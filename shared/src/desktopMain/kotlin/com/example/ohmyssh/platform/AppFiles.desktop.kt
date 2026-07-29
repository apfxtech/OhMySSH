package com.example.ohmyssh.platform

import java.io.File

actual object AppFiles {
    actual fun appSupportDirectory(): String {
        val home = System.getProperty("user.home") ?: "."
        val dir = when (appPlatform) {
            AppPlatform.MACOS -> File(home, "Library/Application Support/com.example.ohmyssh")
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
}
