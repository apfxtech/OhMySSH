package com.example.ohmyssh.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual object AppFiles {
    actual fun appSupportDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory, NSUserDomainMask, true,
        )
        val dir = paths.firstOrNull() as? String ?: NSFileManager.defaultManager.currentDirectoryPath
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir, withIntermediateDirectories = true, attributes = null, error = null,
        )
        return dir
    }

    actual fun exists(path: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(path)

    actual fun readBytes(path: String): ByteArray {
        val data = NSData.dataWithContentsOfFile(path)
            ?: throw IllegalStateException("Cannot read $path")
        val length = data.length.toInt()
        if (length == 0) return ByteArray(0)
        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
        return bytes
    }

    actual fun writeBytes(path: String, bytes: ByteArray) {
        val data = if (bytes.isEmpty()) {
            NSData()
        } else {
            bytes.usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
            }
        }
        if (!data.writeToFile(path, atomically = false)) {
            throw IllegalStateException("Cannot write $path")
        }
    }

    actual fun rename(from: String, to: String) {
        val manager = NSFileManager.defaultManager
        if (manager.fileExistsAtPath(to)) manager.removeItemAtPath(to, error = null)
        manager.moveItemAtPath(from, toPath = to, error = null)
    }

    actual fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    actual val pathSeparator: String = "/"
}
