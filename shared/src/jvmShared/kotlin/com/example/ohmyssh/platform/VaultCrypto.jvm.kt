package com.example.ohmyssh.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val TAG_BITS = 128
private const val TAG_BYTES = TAG_BITS / 8

private val secureRandom = SecureRandom()

actual object VaultCrypto {
    actual suspend fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray =
        withContext(Dispatchers.Default) {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
            factory.generateSecret(spec).encoded
        }

    actual fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
    ): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val combined = cipher.doFinal(plaintext)
        val split = combined.size - TAG_BYTES
        return combined.copyOfRange(0, split) to combined.copyOfRange(split, combined.size)
    }

    actual fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        mac: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return try {
            cipher.doFinal(ciphertext + mac)
        } catch (_: AEADBadTagException) {
            throw AeadAuthenticationException()
        }
    }

    actual fun randomBytes(length: Int): ByteArray =
        ByteArray(length).also { secureRandom.nextBytes(it) }
}
