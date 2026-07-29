package com.example.ohmyssh.platform

import java.io.File

actual object AppFiles {
    actual fun appSupportDirectory(): String = AndroidApp.context.filesDir.absolutePath

    actual fun exists(path: String): Boolean = File(path).exists()

    actual fun readBytes(path: String): ByteArray = File(path).readBytes()

    actual fun writeBytes(path: String, bytes: ByteArray) {
        File(path).writeBytes(bytes)
    }

    actual fun rename(from: String, to: String) {
        val source = File(from)
        val target = File(to)
        if (!source.renameTo(target)) {
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
        get() = AndroidApp.context.filesDir.absolutePath
}
