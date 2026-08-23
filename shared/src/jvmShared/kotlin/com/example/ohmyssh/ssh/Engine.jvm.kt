package com.example.ohmyssh.ssh

import com.example.ohmyssh.services.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.keyprovider.KeyFormat
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.keyprovider.PuTTYKeyFile
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.InputStream
import java.io.StringReader
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.EnumSet
import java.util.concurrent.TimeUnit

actual fun createSshEngine(): SshEngine = SshjEngine

/// OpenSSH-style pin: SHA256 over the SSH wire encoding of the key, base64
/// without padding — byte-identical with what dartssh2 produced, so existing
/// `knownHostKey` values keep matching.
internal fun openSshFingerprint(key: PublicKey): String {
    val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
    val digest = MessageDigest.getInstance("SHA-256").digest(blob)
    return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
}

private object SshjEngine : SshEngine {
    override suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: String?,
        privateKeyPem: String?,
        passphrase: String?,
        onHandshake: suspend (keyType: String) -> Unit,
        verifyHostKey: suspend (keyType: String, fingerprint: String) -> Boolean,
    ): SshConnection = withContext(Dispatchers.IO) {
        val client = SSHClient(DefaultConfig())
        client.connectTimeout = 15_000
        client.timeout = 30_000
        // sshj starts the keep-alive thread inside connect(), and only if the
        // interval is already non-zero: set after connecting, heartbeats never
        // run and an idle session dies whenever the first NAT or server idle
        // timer on the path fires.
        client.connection.keepAlive.keepAliveInterval = 15

        var fingerprint: String? = null
        var rejected = false

        client.addHostKeyVerifier(
            object : HostKeyVerifier {
                override fun verify(hostname: String?, p: Int, key: PublicKey): Boolean {
                    val type = KeyType.fromKey(key).toString()
                    val offered = openSshFingerprint(key)
                    fingerprint = offered
                    // The verifier runs on sshj's transport thread; the
                    // suspending callbacks are bridged here deliberately.
                    return runBlocking {
                        onHandshake(type)
                        val accepted = verifyHostKey(type, offered)
                        if (!accepted) rejected = true
                        accepted
                    }
                }

                override fun findExistingAlgorithms(hostname: String?, p: Int): List<String> =
                    emptyList()
            },
        )

        try {
            client.connect(host, port)
        } catch (error: Exception) {
            runCatching { client.close() }
            throw error
        }

        try {
            client.useCompression()
        } catch (_: Exception) {
        }

        try {
            if (privateKeyPem != null) {
                client.authPublickey(username, loadKeyProvider(privateKeyPem, passphrase))
            } else {
                client.authPassword(username, password ?: "")
            }
        } catch (error: Exception) {
            runCatching { client.close() }
            // A rejected host key tears the transport down; report that, not
            // the downstream auth error it produces.
            if (rejected) throw HostKeyRejected()
            throw if (error is UserAuthException) SshAuthException() else error
        }

        SshjConnection(client, fingerprint)
    }
}

internal class HostKeyRejected : Exception("Host key rejected")

private fun loadKeyProvider(pem: String, passphrase: String?): KeyProvider {
    val format = try {
        KeyProviderUtil.detectKeyFileFormat(StringReader(pem), true)
    } catch (error: Exception) {
        throw SshKeyException("Private key could not be read: ${error.message ?: error}")
    }

    val provider = when (format) {
        KeyFormat.OpenSSHv1 -> OpenSSHKeyV1KeyFile()
        KeyFormat.PuTTY -> PuTTYKeyFile()
        KeyFormat.PKCS8, KeyFormat.OpenSSH -> PKCS8KeyFile()
        else -> throw SshKeyException("Unsupported private key format")
    }

    if (passphrase.isNullOrEmpty()) {
        provider.init(StringReader(pem))
    } else {
        provider.init(StringReader(pem), PasswordUtils.createOneOff(passphrase.toCharArray()))
    }
    return provider
}

actual fun isEncryptedPem(pem: String): Boolean {
    if (pem.contains("ENCRYPTED")) return true
    if (pem.contains("Proc-Type: 4,ENCRYPTED")) return true
    // OpenSSH v1: the cipher name sits in the base64 body right after the
    // "openssh-key-v1" magic; "none" means unencrypted.
    if (pem.contains("BEGIN OPENSSH PRIVATE KEY")) {
        return runCatching {
            val body = pem.substringAfter("-----BEGIN OPENSSH PRIVATE KEY-----")
                .substringBefore("-----END OPENSSH PRIVATE KEY-----")
                .filterNot { it.isWhitespace() }
            val bytes = Base64.getDecoder().decode(body)
            val buffer = Buffer.PlainBuffer(bytes)
            buffer.readRawBytes(ByteArray(15))
            buffer.readString() != "none"
        }.getOrDefault(false)
    }
    return false
}

actual fun describePrivateKey(pem: String, passphrase: String?): String {
    if (isEncryptedPem(pem) && passphrase.isNullOrEmpty()) {
        return "Encrypted key loaded — passphrase required"
    }
    return try {
        val provider = loadKeyProvider(pem, passphrase)
        val type = provider.type?.toString().orEmpty()
        "Key loaded" + if (type.isEmpty()) "" else " · $type"
    } catch (error: Exception) {
        "Key could not be parsed: ${error.message ?: error}"
    }
}

private class SshjConnection(
    private val client: SSHClient,
    override val fingerprint: String?,
) : SshConnection {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sftp: SFTPClient? = null

    override val isConnected: Boolean get() = client.isConnected

    override suspend fun openShell(
        cols: Int,
        rows: Int,
        onData: (String) -> Unit,
        onClosed: () -> Unit,
    ): SshShell = withContext(Dispatchers.IO) {
        val session = client.startSession()
        session.allocatePTY(
            "xterm-256color",
            cols,
            rows,
            0,
            0,
            emptyMap<PTYMode, Int>(),
        )
        val shell = session.startShell()

        scope.launch { pump(shell.inputStream, onData, onClosed) }
        scope.launch { pump(shell.errorStream, onData, null) }

        SshjShell(session, shell)
    }

    private fun pump(stream: InputStream, onData: (String) -> Unit, onClosed: (() -> Unit)?) {
        val buffer = ByteArray(8192)
        val carry = ByteArray(4)
        var carrySize = 0
        try {
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (read == 0) continue

                val combined = if (carrySize == 0) {
                    buffer.copyOf(read)
                } else {
                    carry.copyOf(carrySize) + buffer.copyOf(read)
                }
                val complete = completeUtf8Length(combined)
                carrySize = combined.size - complete
                if (carrySize > 0) combined.copyInto(carry, 0, complete, combined.size)
                if (complete > 0) {
                    onData(String(combined, 0, complete, Charsets.UTF_8))
                }
            }
        } catch (error: Exception) {
            Log.info("ssh", "stream ended: ${error.message ?: error}")
        } finally {
            onClosed?.invoke()
        }
    }

    override suspend fun exec(command: String, timeoutMillis: Long): String =
        withContext(Dispatchers.IO) {
            val session = client.startSession()
            try {
                val cmd = session.exec(command)
                val output = cmd.inputStream.readBytes().toString(Charsets.UTF_8)
                cmd.join(timeoutMillis, TimeUnit.MILLISECONDS)
                output
            } finally {
                runCatching { session.close() }
            }
        }

    override suspend fun openSftp(): SftpChannel = withContext(Dispatchers.IO) {
        sftp?.let { return@withContext SshjSftp(it) }
        val opened = client.newSFTPClient()
        sftp = opened
        SshjSftp(opened)
    }

    override fun close() {
        scope.cancel()
        runCatching { sftp?.close() }
        sftp = null
        runCatching { client.disconnect() }
        runCatching { client.close() }
    }
}

internal fun completeUtf8Length(bytes: ByteArray): Int {
    var index = bytes.size
    var scanned = 0
    while (index > 0 && scanned < 4) {
        val b = bytes[index - 1].toInt() and 0xFF
        if (b and 0x80 == 0) return index
        if (b and 0xC0 == 0xC0) {
            val needed = when {
                b and 0xF8 == 0xF0 -> 4
                b and 0xF0 == 0xE0 -> 3
                else -> 2
            }
            return if (bytes.size - (index - 1) >= needed) bytes.size else index - 1
        }
        index--
        scanned++
    }
    return bytes.size
}

private class SshjShell(
    private val session: Session,
    private val shell: Session.Shell,
) : SshShell {
    override fun write(data: String) {
        try {
            shell.outputStream.write(data.toByteArray(Charsets.UTF_8))
            shell.outputStream.flush()
        } catch (error: Exception) {
            Log.warn("ssh", "write failed: ${error.message ?: error}")
        }
    }

    override fun resize(cols: Int, rows: Int, pixelWidth: Int, pixelHeight: Int) {
        runCatching { shell.changeWindowDimensions(cols, rows, pixelWidth, pixelHeight) }
    }

    override fun close() {
        runCatching { shell.close() }
        runCatching { session.close() }
    }
}

private class SshjSftp(private val sftp: SFTPClient) : SftpChannel {
    override suspend fun absolute(path: String): String = withContext(Dispatchers.IO) {
        sftp.canonicalize(path)
    }

    override suspend fun list(path: String): List<SftpEntry> = withContext(Dispatchers.IO) {
        sftp.ls(path).map { info ->
            val attrs = info.attributes
            SftpEntry(
                name = info.name,
                isDirectory = info.isDirectory,
                isSymlink = attrs.type == FileMode.Type.SYMLINK,
                size = attrs.size,
                modifyTimeSeconds = attrs.mtime,
                mode = attrs.mode?.permissionsMask,
            )
        }
    }

    override suspend fun readBytes(path: String, onProgress: ((Long) -> Unit)?): ByteArray =
        withContext(Dispatchers.IO) {
            val file = sftp.open(path)
            try {
                val total = file.length()
                val out = java.io.ByteArrayOutputStream(total.coerceAtMost(1 shl 20).toInt())
                val buffer = ByteArray(32768)
                var offset = 0L
                while (true) {
                    val read = file.read(offset, buffer, 0, buffer.size)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    offset += read
                    onProgress?.invoke(offset)
                }
                out.toByteArray()
            } finally {
                runCatching { file.close() }
            }
        }

    override suspend fun writeBytes(path: String, bytes: ByteArray, onProgress: ((Long) -> Unit)?) {
        withContext(Dispatchers.IO) {
            val file = sftp.open(
                path,
                EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC),
            )
            try {
                var offset = 0L
                val chunk = 32768
                while (offset < bytes.size) {
                    val length = minOf(chunk.toLong(), bytes.size - offset).toInt()
                    file.write(offset, bytes, offset.toInt(), length)
                    offset += length
                    onProgress?.invoke(offset)
                }
                if (bytes.isEmpty()) file.setLength(0)
            } finally {
                runCatching { file.close() }
            }
        }
    }

    override suspend fun stat(path: String): SftpEntry = withContext(Dispatchers.IO) {
        val attrs = sftp.stat(path)
        SftpEntry(
            name = path.substringAfterLast('/'),
            isDirectory = attrs.type == FileMode.Type.DIRECTORY,
            isSymlink = attrs.type == FileMode.Type.SYMLINK,
            size = attrs.size,
            modifyTimeSeconds = attrs.mtime,
            mode = attrs.mode?.permissionsMask,
        )
    }

    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) { sftp.mkdir(path) }

    override suspend fun remove(path: String) = withContext(Dispatchers.IO) { sftp.rm(path) }

    override suspend fun rmdir(path: String) = withContext(Dispatchers.IO) { sftp.rmdir(path) }

    override suspend fun rename(from: String, to: String) =
        withContext(Dispatchers.IO) { sftp.rename(from, to) }

    override suspend fun close() {
        withContext(Dispatchers.IO) { runCatching { sftp.close() } }
    }
}
