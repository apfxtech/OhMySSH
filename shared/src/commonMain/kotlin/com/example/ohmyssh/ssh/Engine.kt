package com.example.ohmyssh.ssh

interface SshEngine {
    suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: String?,
        privateKeyPem: String?,
        passphrase: String?,
        onHandshake: suspend (keyType: String) -> Unit,
        verifyHostKey: suspend (keyType: String, fingerprint: String) -> Boolean,
    ): SshConnection
}

interface SshConnection {
    val fingerprint: String?

    val isConnected: Boolean

    suspend fun openShell(
        cols: Int,
        rows: Int,
        onData: (String) -> Unit,
        onClosed: () -> Unit,
    ): SshShell

    suspend fun exec(command: String, timeoutMillis: Long): String

    suspend fun openSftp(): SftpChannel

    fun close()
}

interface SshShell {
    fun write(data: String)
    fun resize(cols: Int, rows: Int, pixelWidth: Int, pixelHeight: Int)
    fun close()
}

class SftpEntry(
    val name: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long?,
    val modifyTimeSeconds: Long?,
    val mode: Int?,
)

interface SftpChannel {
    suspend fun absolute(path: String): String
    suspend fun list(path: String): List<SftpEntry>
    suspend fun readBytes(path: String, onProgress: ((Long) -> Unit)? = null): ByteArray
    suspend fun writeBytes(path: String, bytes: ByteArray, onProgress: ((Long) -> Unit)? = null)
    suspend fun stat(path: String): SftpEntry
    suspend fun mkdir(path: String)
    suspend fun remove(path: String)
    suspend fun rmdir(path: String)
    suspend fun rename(from: String, to: String)
    suspend fun close()
}

class SshKeyException(override val message: String) : Exception(message)

class SshAuthException(override val message: String = "Authentication failed") : Exception(message)

expect fun createSshEngine(): SshEngine

expect fun describePrivateKey(pem: String, passphrase: String?): String

expect fun isEncryptedPem(pem: String): Boolean
