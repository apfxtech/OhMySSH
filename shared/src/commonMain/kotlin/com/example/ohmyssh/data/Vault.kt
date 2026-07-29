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

        internal fun readEnvelope(raw: String): JsonObject {
            val decoded = try {
                json.parseToJsonElement(raw)
            } catch (_: Exception) {
                throw VaultException("Vault file is not valid JSON")
            }
            if (decoded !is JsonObject) throw VaultException("Vault file has an unexpected shape")
            if (decoded.str(FIELD_FORMAT) != kVaultFormat) {
                throw VaultException("Not an ohmyssh vault file")
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
        val nonce = VaultCrypto.randomBytes(12)
        val (cipherText, mac) = VaultCrypto.encrypt(key, nonce, plain)

        val envelope = buildJsonObject {
            put(FIELD_FORMAT, kVaultFormat)
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

        // Write-then-rename so a crash mid-write cannot leave a truncated vault.
        AppFiles.writeTextAtomic(path, json.encodeToString(JsonObject.serializer(), envelope))
    }

    suspend fun changePassword(newPassword: String) {
        val data = read()
        val newSalt = VaultCrypto.randomBytes(16)
        key = VaultCrypto.deriveKey(newPassword, newSalt, iterations)
        salt = newSalt
        save(data)
    }
}
