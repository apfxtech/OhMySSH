package com.example.ohmyssh.ssh

/// iOS has no JVM, so the sshj engine cannot come along. The seam is here and
/// every caller above it is shared; what is missing is a Kotlin/Native SSH
/// transport behind this interface. Until then a connect attempt fails with a
/// clear reason instead of a crash, and the rest of the app (vault, hosts,
/// users, settings) works normally.
actual fun createSshEngine(): SshEngine = UnavailableSshEngine

private object UnavailableSshEngine : SshEngine {
    override suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: String?,
        privateKeyPem: String?,
        passphrase: String?,
        onHandshake: suspend (keyType: String) -> Unit,
        verifyHostKey: suspend (keyType: String, fingerprint: String) -> Boolean,
    ): SshConnection = throw SshEngineUnavailable()
}

class SshEngineUnavailable : Exception(
    "SSH is not available in this iOS build yet — the native transport is still being wired up.",
)

actual fun isEncryptedPem(pem: String): Boolean =
    pem.contains("ENCRYPTED") || pem.contains("Proc-Type: 4,ENCRYPTED")

actual fun describePrivateKey(pem: String, passphrase: String?): String = when {
    !pem.contains("PRIVATE KEY") -> "That does not look like a private key"
    isEncryptedPem(pem) && passphrase.isNullOrEmpty() ->
        "Encrypted key loaded — passphrase required"
    else -> "Key loaded"
}
