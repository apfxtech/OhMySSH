package com.example.ohmyssh.ssh

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.Identity
import com.example.ohmyssh.data.AuthKind
import com.example.ohmyssh.data.newId
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.session.Checkpoint
import com.example.ohmyssh.session.SessionError
import com.example.ohmyssh.session.SessionState
import com.example.ohmyssh.session.StageStatus
import com.example.ohmyssh.session.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

enum class ConnectStage(val label: String) {
    CREDENTIALS("Loading credentials"),
    TCP("Connecting"),
    HANDSHAKE("Key exchange"),
    HOST_KEY("Verifying host key"),
    AUTH("Authenticating"),
    SHELL("Opening shell"),
    PROBE("Reading system info"),
}

/// Raised when the pinned host key no longer matches. Clearing the pin is an
/// explicit action in the host editor, never a prompt in the connect flow.
class HostKeyMismatch(val expected: String, val actual: String) : Exception() {
    override val message: String
        get() = "Host key changed.\nPinned:  $expected\nOffered: $actual"

    override fun toString(): String = message
}

class HostSession(
    val host: Host,
    val identity: Identity?,
) : TerminalSession(newId()) {
    private val engine = createSshEngine()
    private val scope = CoroutineScope(SupervisorJob())

    private var connection: SshConnection? = null
    private var shell: SshShell? = null
    private var sftpChannel: SftpChannel? = null

    var profile: HostProfile? by mutableStateOf(null)
        private set

    var fingerprint: String? by mutableStateOf(null)
        private set

    private val stageStatus = mutableStateMapOf<ConnectStage, Checkpoint>().apply {
        for (stage in ConnectStage.entries) put(stage, Checkpoint(stage.label, StageStatus.WAITING))
    }

    var onHostKeyMismatch: (suspend (expected: String, actual: String) -> Boolean)? = null

    var onHostKeyPinned: (suspend (fingerprint: String) -> Unit)? = null

    var onProfiled: (suspend (profile: HostProfile) -> Unit)? = null

    override val checkpoints: List<Checkpoint>
        get() = ConnectStage.entries.map { stageStatus.getValue(it) }

    override val title: String get() = host.displayLabel

    override val subtitle: String
        get() = when (state) {
            SessionState.CONNECTED -> profile?.osPretty ?: host.endpoint
            SessionState.CONNECTING -> "Connecting…"
            SessionState.FAILED -> "Failed"
            SessionState.CLOSED -> "Disconnected"
            SessionState.IDLE -> host.endpoint
        }

    private fun mark(stage: ConnectStage, status: StageStatus, detail: String? = null) {
        stageStatus[stage] = Checkpoint(stage.label, status, detail)
    }

    override suspend fun connect() {
        if (state == SessionState.CONNECTING || state == SessionState.CONNECTED) return
        state = SessionState.CONNECTING
        error = null
        for (stage in ConnectStage.entries) {
            stageStatus[stage] = Checkpoint(stage.label, StageStatus.WAITING)
        }

        try {
            val auth = prepareCredentials()
            openConnection(auth)
            openShell()
            runProbe()

            state = SessionState.CONNECTED
        } catch (error: Exception) {
            Log.error("ssh:${host.endpoint}", "connect failed: ${describe(error)}", error)
            this.error = describe(error)
            state = SessionState.FAILED
            for (stage in ConnectStage.entries) {
                if (stageStatus.getValue(stage).status == StageStatus.RUNNING) {
                    stageStatus[stage] = Checkpoint(stage.label, StageStatus.FAILED, describe(error))
                }
            }
            teardown()
        }
    }

    private class Credentials(
        val username: String,
        val password: String? = null,
        val privateKey: String? = null,
        val passphrase: String? = null,
    )

    private fun prepareCredentials(): Credentials {
        mark(ConnectStage.CREDENTIALS, StageStatus.RUNNING)
        val id = identity
        if (id == null || id.username.isEmpty()) {
            throw SessionError("No user assigned to this system")
        }

        if (id.kind == AuthKind.PRIVATE_KEY) {
            val pem = id.privateKey
            if (pem.isNullOrBlank()) throw SessionError("Identity has no private key")
            if (isEncryptedPem(pem) && id.passphrase.isNullOrEmpty()) {
                throw SessionError("Private key is encrypted but has no passphrase")
            }
            mark(ConnectStage.CREDENTIALS, StageStatus.DONE, "${id.username} · key")
            return Credentials(
                username = id.username,
                privateKey = pem,
                passphrase = id.passphrase,
            )
        }

        mark(ConnectStage.CREDENTIALS, StageStatus.DONE, "${id.username} · password")
        return Credentials(username = id.username, password = id.password ?: "")
    }

    private suspend fun openConnection(auth: Credentials) {
        mark(ConnectStage.TCP, StageStatus.RUNNING, host.endpoint)
        var mismatch: HostKeyMismatch? = null

        val opened = try {
            engine.connect(
                host = host.hostname,
                port = host.port,
                username = auth.username,
                password = auth.password,
                privateKeyPem = auth.privateKey,
                passphrase = auth.passphrase,
                onHandshake = { keyType ->
                    mark(ConnectStage.TCP, StageStatus.DONE, host.endpoint)
                    mark(ConnectStage.HANDSHAKE, StageStatus.DONE, keyType)
                    mark(ConnectStage.HOST_KEY, StageStatus.RUNNING)
                },
                verifyHostKey = { _, offered ->
                    fingerprint = offered
                    val pinned = host.knownHostKey

                    when {
                        pinned.isNullOrEmpty() -> {
                            onHostKeyPinned?.invoke(offered)
                            mark(ConnectStage.HOST_KEY, StageStatus.DONE, "pinned")
                            true
                        }
                        pinned == offered -> {
                            mark(ConnectStage.HOST_KEY, StageStatus.DONE)
                            true
                        }
                        onHostKeyMismatch?.invoke(pinned, offered) == true -> {
                            onHostKeyPinned?.invoke(offered)
                            mark(ConnectStage.HOST_KEY, StageStatus.DONE, "re-pinned")
                            true
                        }
                        else -> {
                            mismatch = HostKeyMismatch(expected = pinned, actual = offered)
                            mark(ConnectStage.HOST_KEY, StageStatus.FAILED, "key changed")
                            false
                        }
                    }
                },
            )
        } catch (error: Exception) {
            mismatch?.let { throw it }
            throw error
        }

        connection = opened
        mark(ConnectStage.AUTH, StageStatus.DONE, auth.username)
    }

    private suspend fun openShell() {
        mark(ConnectStage.SHELL, StageStatus.RUNNING)
        val active = connection ?: throw SessionError("Not connected")

        // The PTY size is fixed at allocation, so a shell opened before the
        // view has measured would make the remote wrap its first output — the
        // login banner especially — to a width the screen does not have.
        withTimeoutOrNull(1000) {
            while (!terminal.isMeasured) delay(16)
        }

        val opened = active.openShell(
            cols = terminal.viewWidth,
            rows = terminal.viewHeight,
            onData = { terminal.write(it) },
            onClosed = { handleClosed() },
        )
        shell = opened

        terminal.onOutput = { data -> opened.write(data) }
        terminal.onResize = { cols, rows, pixelWidth, pixelHeight ->
            opened.resize(cols, rows, pixelWidth, pixelHeight)
        }

        mark(ConnectStage.SHELL, StageStatus.DONE)
    }

    private suspend fun runProbe() {
        mark(ConnectStage.PROBE, StageStatus.RUNNING)
        try {
            val active = connection ?: throw SessionError("Not connected")
            val result = withTimeout(20_000) { probeHost(active) }
            profile = result
            onProfiled?.invoke(result)
            mark(ConnectStage.PROBE, StageStatus.DONE, result.osPretty)
        } catch (error: Exception) {
            Log.warn("ssh:${host.endpoint}", "probe skipped: ${describe(error)}")
            mark(ConnectStage.PROBE, StageStatus.SKIPPED, describe(error))
        }
    }

    suspend fun refreshProfile() {
        val active = connection ?: return
        if (state != SessionState.CONNECTED) return
        val result = withTimeout(20_000) { probeHost(active) }
        profile = result
        onProfiled?.invoke(result)
    }

    suspend fun sftp(): SftpChannel {
        sftpChannel?.let { return it }
        val active = connection ?: throw SessionError("Not connected")
        val opened = active.openSftp()
        sftpChannel = opened
        return opened
    }

    private fun handleClosed() {
        if (state == SessionState.CLOSED || state == SessionState.FAILED) return
        state = SessionState.CLOSED
    }

    private suspend fun teardown() {
        runCatching { shell?.close() }
        runCatching { sftpChannel?.close() }
        runCatching { connection?.close() }
        shell = null
        sftpChannel = null
        connection = null
    }

    override suspend fun disconnect() {
        teardown()
        if (state != SessionState.FAILED) state = SessionState.CLOSED
    }

    override fun dispose() {
        // Detach terminal callbacks first: a late resize event would otherwise
        // write to a shell that is already gone.
        terminal.onOutput = null
        terminal.onResize = null
        scope.launch { teardown() }
        scope.cancel()
    }
}

internal fun describe(error: Any?): String = when (error) {
    is SessionError -> error.message
    is HostKeyMismatch -> error.message
    is SshAuthException -> error.message
    is SshKeyException -> error.message
    is TimeoutCancellationException -> "Timed out"
    is Throwable -> error.message ?: error.toString()
    else -> error.toString()
}
