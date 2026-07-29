package com.example.ohmyssh.data

import com.example.ohmyssh.platform.SecureStorage
import com.example.ohmyssh.services.Log

object AutoLogin {
    private const val SCOPE = "AutoLogin"
    private const val KEY = "vault.master"

    private var available: Boolean? = null

    var unavailableReason: String? = null
        private set

    var unavailableIsExpected: Boolean = false
        private set

    /// Probes with a write+delete, not a read: on a sandboxed macOS build
    /// reading a missing key succeeds while writing fails with -34018.
    suspend fun isAvailable(): Boolean {
        available?.let { return it }
        val probeKey = "keystore.probe"
        return try {
            SecureStorage.write(probeKey, "ok")
            SecureStorage.delete(probeKey)
            unavailableReason = null
            unavailableIsExpected = false
            Log.info(SCOPE, "keystore available (write probe passed)")
            available = true
            true
        } catch (error: Exception) {
            unavailableIsExpected = isExpectedFailure(error)
            unavailableReason = describeFailure(error)
            if (!unavailableIsExpected) {
                Log.error(SCOPE, "keystore probe failed: $error", error)
            }
            Log.warn(SCOPE, "auto-unlock off: $unavailableReason")
            developerHint(error)?.let { Log.info(SCOPE, it) }
            available = false
            false
        }
    }

    suspend fun isEnabled(): Boolean {
        if (available == false) return false
        return try {
            SecureStorage.containsKey(KEY)
        } catch (error: Exception) {
            report("containsKey failed", error)
            false
        }
    }

    suspend fun readPassword(): String? {
        if (available == false) return null
        return try {
            SecureStorage.read(KEY)
        } catch (error: Exception) {
            report("read failed", error)
            null
        }
    }

    suspend fun enable(password: String) {
        try {
            SecureStorage.write(KEY, password)
            Log.info(SCOPE, "auto-unlock enabled")
        } catch (error: Exception) {
            report("write failed", error)
            throw AutoLoginException(describeFailure(error))
        }
    }

    suspend fun disable() {
        if (available == false) return
        try {
            SecureStorage.delete(KEY)
            Log.info(SCOPE, "auto-unlock disabled")
        } catch (error: Exception) {
            report("delete failed", error)
        }
    }

    private fun report(what: String, error: Exception) {
        if (isExpectedFailure(error)) {
            Log.warn(SCOPE, "$what: ${describeFailure(error)}")
            return
        }
        Log.error(SCOPE, "$what: $error", error)
    }

    fun isExpectedFailure(error: Any): Boolean {
        val text = "$error".lowercase()
        return text.contains("-34018") ||
            text.contains("-25291") ||
            text.contains("-25308") ||
            text.contains("libsecret") ||
            text.contains("secret service") ||
            text.contains("secret-tool") ||
            text.contains("no keystore backend")
    }

    fun describeFailure(error: Any): String {
        val text = "$error"
        if (text.contains("-34018")) {
            return "macOS denies keychain access to this build (-34018) because it " +
                "is signed ad-hoc."
        }
        if (text.contains("-25291")) return "No keychain is available (-25291)."
        if (text.contains("-25308")) return "The keychain is locked (-25308)."
        if (text.contains("-25300")) return "Keychain item not found (-25300)."
        val lower = text.lowercase()
        if (lower.contains("libsecret") || lower.contains("secret service") ||
            lower.contains("secret-tool")
        ) {
            return "No secret service on this Linux session — install libsecret and " +
                "run a keyring daemon."
        }
        if (lower.contains("no keystore backend")) {
            return "This platform has no keystore backend."
        }
        return text
    }

    private fun developerHint(error: Any): String? {
        if (!"$error".contains("-34018")) return null
        return "to enable auto-unlock locally, sign the app with a Team and the " +
            "keychain-access-groups entitlement."
    }
}

class AutoLoginException(override val message: String) : Exception(message) {
    override fun toString(): String = message
}
