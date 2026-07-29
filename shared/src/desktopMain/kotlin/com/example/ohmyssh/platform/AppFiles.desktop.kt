package com.example.ohmyssh.platform

import java.io.File

/// Kept in step with kVaultFileName; the data layer cannot be reached from
/// here, and this is the file that decides which directory is the real one.
private const val VAULT_FILE = "ohmyssh.vault"

actual object AppFiles {
    actual fun appSupportDirectory(): String {
        val home = System.getProperty("user.home") ?: "."
        val dir = when (appPlatform) {
            AppPlatform.MACOS -> macosSupportDirectory(home)
            AppPlatform.WINDOWS -> {
                val appData = System.getenv("APPDATA") ?: File(home, "AppData/Roaming").path
                File(File(appData, "com.example"), "ohmyssh")
            }
            else -> {
                val dataHome = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotEmpty() }
                    ?: File(home, ".local/share").path
                File(dataHome, "com.example.ohmyssh")
            }
        }
        dir.mkdirs()
        return dir.absolutePath
    }

    /// A macOS Flutter build is sandboxed, so path_provider hands it the app's
    /// container rather than the bare Application Support directory — that is
    /// where the vault written by the Flutter app actually sits. Whichever of
    /// the two holds a vault wins, container first; with neither, the sandbox
    /// path is used when the container exists, so both builds keep writing to
    /// the same file.
    private fun macosSupportDirectory(home: String): File {
        val plain = File(home, "Library/Application Support/com.example.ohmyssh")
        val container = File(
            home,
            "Library/Containers/com.example.ohmyssh/Data/Library/" +
                "Application Support/com.example.ohmyssh",
        )
        return when {
            File(container, VAULT_FILE).isFile -> container
            File(plain, VAULT_FILE).isFile -> plain
            container.isDirectory -> container
            else -> plain
        }
    }

    actual fun exists(path: String): Boolean = File(path).exists()

    actual fun readBytes(path: String): ByteArray = File(path).readBytes()

    actual fun writeBytes(path: String, bytes: ByteArray) {
        File(path).writeBytes(bytes)
    }

    actual fun rename(from: String, to: String) {
        val source = File(from)
        val target = File(to)
        if (!source.renameTo(target)) {
            // Windows will not rename over an existing file; replace explicitly.
            target.delete()
            if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = true)
                source.delete()
            }
        }
    }

    actual fun delete(path: String) {
        File(path).delete()
    }

    actual val pathSeparator: String = File.separator

    actual val userHomeDirectory: String
        get() = System.getProperty("user.home") ?: File.separator
}
