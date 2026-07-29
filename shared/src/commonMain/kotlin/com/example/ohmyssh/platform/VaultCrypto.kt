package com.example.ohmyssh.platform

/// AES-256-GCM + PBKDF2-HMAC-SHA256, split so the ciphertext and the 16-byte
/// tag travel separately — exactly how the Flutter app's `cryptography`
/// package laid the envelope out.
expect object VaultCrypto {
    suspend fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray

    fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): Pair<ByteArray, ByteArray>

    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, mac: ByteArray): ByteArray

    fun randomBytes(length: Int): ByteArray
}

class AeadAuthenticationException : Exception("Authentication tag mismatch")
