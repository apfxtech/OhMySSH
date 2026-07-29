package com.example.ohmyssh.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.fs.FileBrowsers
import com.example.ohmyssh.serial.SerialDeviceEntry
import com.example.ohmyssh.serial.SerialRegistry
import com.example.ohmyssh.serial.SerialSession
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.ssh.HostSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SessionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessions = mutableStateListOf<TerminalSession>()

    var activeId: String? by mutableStateOf(null)
        private set

    var onSessionsChanged: (() -> Unit)? = null

    val hasLiveSessions: Boolean
        get() = sessions.any { it.state == SessionState.CONNECTED }

    val active: TerminalSession?
        get() = activeId?.let { id -> sessions.firstOrNull { it.id == id } }

    fun byId(id: String): TerminalSession? = sessions.firstOrNull { it.id == id }

    fun open(host: Host): HostSession {
        val session = HostSession(host = host, identity = VaultStore.identityFor(host))

        session.onHostKeyPinned = { fingerprint ->
            currentHost(host.id)?.let { VaultStore.saveHost(it.copy(knownHostKey = fingerprint)) }
        }
        session.onProfiled = { profile ->
            currentHost(host.id)?.let {
                VaultStore.saveHost(it.copy(osId = profile.osId, osPretty = profile.osPretty))
            }
        }

        Log.info("sessions", "opening ${host.endpoint}")
        return adopt(session)
    }

    fun openSerial(entry: SerialDeviceEntry): SerialSession {
        val existing = byId(entry.device.id)
        if (existing is SerialSession) {
            activate(existing.id)
            if (existing.state == SessionState.CLOSED || existing.state == SessionState.FAILED) {
                scope.launch { existing.connect() }
            }
            return existing
        }

        val session = SerialSession(device = entry.device, port = entry.port)
        session.onResolvePort = {
            SerialRegistry.refresh()
            SerialRegistry.entryForDevice(entry.device.id)?.port
        }
        session.onSettingsChanged = { device -> VaultStore.saveSerialDevice(device) }

        if (!entry.saved) {
            scope.launch { VaultStore.saveSerialDevice(entry.device) }
        }

        Log.info("sessions", "opening serial ${entry.device.path}")
        return adopt(session)
    }

    private fun <T : TerminalSession> adopt(session: T): T {
        sessions.add(session)
        activeId = session.id
        onSessionsChanged?.invoke()

        scope.launch { session.connect() }
        return session
    }

    private fun currentHost(id: String): Host? = VaultStore.hosts.firstOrNull { it.id == id }

    fun activate(id: String) {
        if (activeId == id) return
        activeId = id
    }

    suspend fun close(id: String) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return
        val session = sessions.removeAt(index)
        FileBrowsers.forgetGroup("$id:")

        if (activeId == id) {
            activeId = sessions.getOrNull(index.coerceAtMost(sessions.size - 1))?.id
        }
        onSessionsChanged?.invoke()

        session.disconnect()
        session.dispose()
    }

    suspend fun closeAll() {
        Workspace.reset()
        val open = sessions.toList()
        for (session in open) FileBrowsers.forgetGroup("${session.id}:")
        sessions.clear()
        activeId = null
        onSessionsChanged?.invoke()
        for (session in open) {
            session.disconnect()
            session.dispose()
        }
    }

    internal fun notifyChanged() {
        onSessionsChanged?.invoke()
    }
}
