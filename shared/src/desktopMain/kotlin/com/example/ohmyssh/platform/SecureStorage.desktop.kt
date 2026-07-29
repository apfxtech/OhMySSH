package com.example.ohmyssh.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64

private const val SERVICE = "com.example.ohmyssh"

actual object SecureStorage {
    actual suspend fun write(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        when (appPlatform) {
            AppPlatform.MACOS -> {
                // -U updates in place instead of failing on a duplicate item.
                run(
                    "security", "add-generic-password", "-U",
                    "-s", SERVICE, "-a", key, "-w", value,
                )
            }
            AppPlatform.LINUX -> {
                val process = ProcessBuilder(
                    "secret-tool", "store", "--label", "ohmyssh",
                    "service", SERVICE, "account", key,
                ).redirectErrorStream(true).start()
                process.outputStream.use { it.write(value.toByteArray()) }
                val out = process.inputStream.readBytes().decodeToString()
                if (process.waitFor() != 0) {
                    throw SecureStorageException("secret-tool store failed: ${out.trim()}")
                }
            }
            else -> {
                val encrypted = powershell(
                    "[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect(" +
                        "[Text.Encoding]::UTF8.GetBytes(\$env:OHMYSSH_SECRET)," +
                        "\$null,'CurrentUser'))",
                    mapOf("OHMYSSH_SECRET" to value),
                )
                dpapiFile(key).writeText(encrypted.trim())
            }
        }
    }

    actual suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        when (appPlatform) {
            AppPlatform.MACOS -> runOrNull(
                "security", "find-generic-password",
                "-s", SERVICE, "-a", key, "-w",
            )?.trimEnd('\n')
            AppPlatform.LINUX -> runOrNull(
                "secret-tool", "lookup", "service", SERVICE, "account", key,
            )
            else -> {
                val file = dpapiFile(key)
                if (!file.exists()) return@withContext null
                powershell(
                    "[Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect(" +
                        "[Convert]::FromBase64String(\$env:OHMYSSH_BLOB)," +
                        "\$null,'CurrentUser'))",
                    mapOf("OHMYSSH_BLOB" to file.readText().trim()),
                ).trimEnd('\r', '\n')
            }
        }
    }

    actual suspend fun delete(key: String): Unit = withContext(Dispatchers.IO) {
        when (appPlatform) {
            AppPlatform.MACOS -> {
                runOrNull("security", "delete-generic-password", "-s", SERVICE, "-a", key)
                Unit
            }
            AppPlatform.LINUX -> {
                runOrNull("secret-tool", "clear", "service", SERVICE, "account", key)
                Unit
            }
            else -> {
                dpapiFile(key).delete()
                Unit
            }
        }
    }

    actual suspend fun containsKey(key: String): Boolean = read(key) != null

    private fun dpapiFile(key: String): File {
        val safe = Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray())
        return File(AppFiles.appSupportDirectory(), "secure_$safe.dpapi")
    }

    private fun run(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val out = process.inputStream.readBytes().decodeToString()
        if (process.waitFor() != 0) {
            throw SecureStorageException("${command.joinToString(" ")} failed: ${out.trim()}")
        }
        return out
    }

    private fun runOrNull(vararg command: String): String? = try {
        val process = ProcessBuilder(*command).start()
        val out = process.inputStream.readBytes().decodeToString()
        if (process.waitFor() == 0) out else null
    } catch (_: Exception) {
        null
    }

    private fun powershell(script: String, env: Map<String, String>): String {
        val builder = ProcessBuilder(
            "powershell", "-NoProfile", "-NonInteractive", "-Command",
            "Add-Type -AssemblyName System.Security; $script",
        ).redirectErrorStream(true)
        builder.environment().putAll(env)
        val process = builder.start()
        val out = process.inputStream.readBytes().decodeToString()
        if (process.waitFor() != 0) {
            throw SecureStorageException("DPAPI call failed: ${out.trim()}")
        }
        return out
    }
}

class SecureStorageException(message: String) : Exception(message)
