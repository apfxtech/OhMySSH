package com.example.ohmyssh.platform

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.providers.openssl3.Openssl3
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.arc4random_buf

private const val TAG_BYTES = 16

/// OpenSSL 3 (statically linked) — CommonCrypto has no public GCM, and this
/// keeps the math byte-identical with the JVM targets.
@OptIn(DelicateCryptographyApi::class)
actual object VaultCrypto {
    private val provider = CryptographyProvider.Openssl3

    actual suspend fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val pbkdf2 = provider.get(PBKDF2)
        val derivation = pbkdf2.secretDerivation(
            digest = SHA256,
            iterations = iterations,
            outputSize = 32.bytes,
            salt = salt,
        )
        return derivation.deriveSecretToByteArray(password.encodeToByteArray())
    }

    actual fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
    ): Pair<ByteArray, ByteArray> {
        val cipher = decodeKey(key).cipher()
        val combined = cipher.encryptWithIvBlocking(nonce, plaintext)
        val split = combined.size - TAG_BYTES
        return combined.copyOfRange(0, split) to combined.copyOfRange(split, combined.size)
    }

    actual fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        mac: ByteArray,
    ): ByteArray = try {
        decodeKey(key).cipher().decryptWithIvBlocking(nonce, ciphertext + mac)
    } catch (_: Exception) {
        throw AeadAuthenticationException()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun randomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        if (length > 0) {
            bytes.usePinned { pinned ->
                arc4random_buf(pinned.addressOf(0), length.toULong())
            }
        }
        return bytes
    }

    private fun decodeKey(key: ByteArray): AES.GCM.Key =
        provider.get(AES.GCM).keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
}
