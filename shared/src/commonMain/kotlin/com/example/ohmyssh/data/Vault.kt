package com.example.ohmyssh.data

import com.example.ohmyssh.platform.AeadAuthenticationException
import com.example.ohmyssh.platform.AppFiles
import com.example.ohmyssh.platform.VaultCrypto
import com.example.ohmyssh.platform.readText
import com.example.ohmyssh.platform.writeTextAtomic
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

const val kVaultFormat = "ohmyssh.vault"
const val kVaultVersion = 1
const val kVaultFileName = "ohmyssh.vault"

private const val FIELD_FORMAT = "format"
private const val FIELD_VERSION = "version"
private const val FIELD_KDF = "kdf"
private const val FIELD_CIPHER = "cipher"
private const val FIELD_NONCE = "nonce"
private const val FIELD_MAC = "mac"
private const val FIELD_DATA = "data"

/** Written into the envelope, so raising it does not orphan existing vaults. */
const val kKdfIterations = 120000

open class VaultException(override val message: String) : Exception(message) {
    override fun toString(): String = message
}

class WrongPasswordException : VaultException("Wrong master password")

private val b64 = Base64.Default

private val json = Json { ignoreUnknownKeys = true }

class Vault private constructor(
    val path: String,
    private var key: ByteArray,
    private var salt: ByteArray,
    private val iterations: Int,
) {
    companion object {
        fun defaultPath(): String {
            val dir = AppFiles.appSupportDirectory()
            return "$dir${AppFiles.pathSeparator}$kVaultFileName"
        }

        fun exists(): Boolean = AppFiles.exists(defaultPath())

        suspend fun create(
            password: String,
            path: String = defaultPath(),
            initial: VaultData = VaultData(),
        ): Vault {
            val salt = VaultCrypto.randomBytes(16)
            val key = VaultCrypto.deriveKey(password, salt, kKdfIterations)
            val vault = Vault(path, key, salt, kKdfIterations)
            vault.save(initial)
            return vault
        }

        suspend fun open(password: String, path: String = defaultPath()): Vault {
            if (!AppFiles.exists(path)) throw VaultException("Vault file not found")
            return openFromText(AppFiles.readText(path), password, path)
        }

        suspend fun openFromText(raw: String, password: String, path: String): Vault {
            val envelope = readEnvelope(raw)
            val (key, salt, iterations) = deriveEnvelopeKey(envelope, password)

            // Force a decrypt now so a bad password fails here and not on first read.
            decrypt(envelope, key)

            return Vault(path, key, salt, iterations)
        }

        suspend fun decryptData(raw: String, password: String): VaultData {
            val envelope = readEnvelope(raw)
            val (key, _, _) = deriveEnvelopeKey(envelope, password)
            val clear = decrypt(envelope, key)
            return parsePayload(clear)
        }

        suspend fun decryptSidecar(raw: String, password: String, format: String): ByteArray {
            val envelope = readEnvelope(raw, format)
            val (key, _, _) = deriveEnvelopeKey(envelope, password)
            return decrypt(envelope, key)
        }

        internal fun parsePayload(clear: ByteArray): VaultData {
            val parsed = json.parseToJsonElement(clear.decodeToString())
            if (parsed !is JsonObject) throw VaultException("Vault payload has an unexpected shape")
            return VaultData.fromJson(parsed)
        }

        private suspend fun deriveEnvelopeKey(
            envelope: JsonObject,
            password: String,
        ): Triple<ByteArray, ByteArray, Int> {
            val kdf = envelope[FIELD_KDF] as? JsonObject
                ?: throw VaultException("Vault file has an unexpected shape")
            val salt = b64.decode(kdf.str("salt") ?: throw VaultException("Vault KDF salt missing"))
            val iterations = kdf.int("iterations") ?: kKdfIterations
            return Triple(VaultCrypto.deriveKey(password, salt, iterations), salt, iterations)
        }

        internal fun readEnvelope(raw: String, format: String = kVaultFormat): JsonObject {
            val decoded = try {
                json.parseToJsonElement(raw)
            } catch (_: Exception) {
                throw VaultException("Vault file is not valid JSON")
            }
            if (decoded !is JsonObject) throw VaultException("Vault file has an unexpected shape")
            if (decoded.str(FIELD_FORMAT) != format) {
                throw VaultException("Not an $format file")
            }
            val version = decoded.int(FIELD_VERSION) ?: 0
            if (version > kVaultVersion) {
                throw VaultException("Vault was written by a newer version of the app (v$version)")
            }
            return decoded
        }

        internal fun decrypt(envelope: JsonObject, key: ByteArray): ByteArray {
            try {
                return VaultCrypto.decrypt(
                    key = key,
                    nonce = b64.decode(envelope.str(FIELD_NONCE) ?: ""),
                    ciphertext = b64.decode(envelope.str(FIELD_DATA) ?: ""),
                    mac = b64.decode(envelope.str(FIELD_MAC) ?: ""),
                )
            } catch (_: AeadAuthenticationException) {
                throw WrongPasswordException()
            }
        }
    }

    fun readFileBytes(): ByteArray = AppFiles.readBytes(path)

    fun read(): VaultData {
        val envelope = readEnvelope(AppFiles.readText(path))
        return parsePayload(decrypt(envelope, key))
    }

    fun save(data: VaultData) {
        val plain = json.encodeToString(JsonObject.serializer(), data.toJson()).encodeToByteArray()
        // Write-then-rename so a crash mid-write cannot leave a truncated vault.
        AppFiles.writeTextAtomic(path, seal(kVaultFormat, plain))
    }

    private fun seal(format: String, clear: ByteArray): String {
        val nonce = VaultCrypto.randomBytes(12)
        val (cipherText, mac) = VaultCrypto.encrypt(key, nonce, clear)

        val envelope = buildJsonObject {
            put(FIELD_FORMAT, format)
            put(FIELD_VERSION, kVaultVersion)
            put(
                FIELD_KDF,
                buildJsonObject {
                    put("algo", "pbkdf2-hmac-sha256")
                    put("iterations", iterations)
                    put("salt", b64.encode(salt))
                },
            )
            put(FIELD_CIPHER, "aes-256-gcm")
            put(FIELD_NONCE, b64.encode(nonce))
            put(FIELD_MAC, b64.encode(mac))
            put(FIELD_DATA, b64.encode(cipherText))
        }
        return json.encodeToString(JsonObject.serializer(), envelope)
    }

    /**
     * State this port keeps beside the vault: same envelope, same key derived
     * from the same master password, its own file.
     *
     * It stays out of the vault itself deliberately. The Flutter app writes
     * `ohmyssh.vault` too, and would drop a key it knows nothing about on its
     * next save — so anything stored in there would survive only until the user
     * next opened the other app. Session history also grows without bound
     * relative to a credential list, and has no business riding along in a file
     * that gets exported and imported.
     */
    fun readSidecar(fileName: String, format: String): ByteArray? {
        val target = siblingPath(fileName)
        if (!AppFiles.exists(target)) return null
        return decrypt(readEnvelope(AppFiles.readText(target), format), key)
    }

    fun writeSidecar(fileName: String, format: String, clear: ByteArray) {
        AppFiles.writeTextAtomic(siblingPath(fileName), seal(format, clear))
    }

    fun deleteSidecar(fileName: String) {
        val target = siblingPath(fileName)
        if (AppFiles.exists(target)) AppFiles.delete(target)
    }

    fun readSidecarFileBytes(fileName: String): ByteArray? {
        val target = siblingPath(fileName)
        if (!AppFiles.exists(target)) return null
        return AppFiles.readBytes(target)
    }

    private fun siblingPath(fileName: String): String {
        val cut = path.lastIndexOf(AppFiles.pathSeparator)
        if (cut < 0) return fileName
        return path.substring(0, cut + AppFiles.pathSeparator.length) + fileName
    }

    /**
     * Reads with the old key first, so this cannot reset a forgotten password.
     * [sidecars] are name-to-format pairs read out under the old key and written
     * back under the new one, since nothing else would ever be able to open
     * them again.
     */
    suspend fun changePassword(newPassword: String, sidecars: List<Pair<String, String>> = emptyList()) {
        val data = read()
        val carried = sidecars.mapNotNull { (fileName, format) ->
            val clear = runCatching { readSidecar(fileName, format) }.getOrNull() ?: return@mapNotNull null
            Triple(fileName, format, clear)
        }

        val newSalt = VaultCrypto.randomBytes(16)
        key = VaultCrypto.deriveKey(newPassword, newSalt, iterations)
        salt = newSalt

        save(data)
        for ((fileName, format, clear) in carried) writeSidecar(fileName, format, clear)
    }
}
