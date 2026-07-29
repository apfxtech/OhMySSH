package com.example.ohmyssh.platform

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFilePosixPermissions
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeSymbolicLink
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.stringByAppendingPathComponent
import platform.Foundation.stringByStandardizingPath
import platform.Foundation.timeIntervalSince1970

@Suppress("CAST_NEVER_SUCCEEDS")
private fun String.asNSString(): NSString = this as NSString

@OptIn(ExperimentalForeignApi::class)
actual object LocalFs {
    actual val separator: String = "/"

    actual fun home(): String = AppFiles.userHomeDirectory

    actual suspend fun absolute(path: String): String = withContext(Dispatchers.Default) {
        path.asNSString().stringByStandardizingPath
    }

    actual suspend fun list(path: String): List<LocalEntry> = withContext(Dispatchers.Default) {
        val manager = NSFileManager.defaultManager
        val names = manager.contentsOfDirectoryAtPath(path, error = null)
            ?: throw IllegalStateException("Cannot read this directory")
        names.mapNotNull { it as? String }
            .map { name -> describe(path.asNSString().stringByAppendingPathComponent(name), name) }
    }

    actual suspend fun stat(path: String): LocalEntry = withContext(Dispatchers.Default) {
        describe(path, path.substringAfterLast('/'))
    }

    actual suspend fun read(path: String, onProgress: ((Long) -> Unit)?): ByteArray =
        withContext(Dispatchers.Default) {
            val bytes = AppFiles.readBytes(path)
            onProgress?.invoke(bytes.size.toLong())
            bytes
        }

    actual suspend fun write(
        path: String,
        bytes: ByteArray,
        onProgress: ((Long) -> Unit)?,
    ): Unit = withContext(Dispatchers.Default) {
        AppFiles.writeBytes(path, bytes)
        onProgress?.invoke(bytes.size.toLong())
    }

    actual suspend fun mkdir(path: String): Unit = withContext(Dispatchers.Default) {
        val created = NSFileManager.defaultManager.createDirectoryAtPath(
            path, withIntermediateDirectories = false, attributes = null, error = null,
        )
        if (!created) throw IllegalStateException("Could not create this directory")
    }

    actual suspend fun remove(path: String): Unit = withContext(Dispatchers.Default) {
        if (!NSFileManager.defaultManager.removeItemAtPath(path, error = null)) {
            throw IllegalStateException("Could not delete this file")
        }
    }

    actual suspend fun rmdir(path: String): Unit = withContext(Dispatchers.Default) {
        val manager = NSFileManager.defaultManager
        val children = manager.contentsOfDirectoryAtPath(path, error = null)
        if (children != null && children.isNotEmpty()) {
            throw IllegalStateException("This directory is not empty")
        }
        if (!manager.removeItemAtPath(path, error = null)) {
            throw IllegalStateException("Could not delete this directory")
        }
    }

    actual suspend fun rename(from: String, to: String): Unit = withContext(Dispatchers.Default) {
        if (!NSFileManager.defaultManager.moveItemAtPath(from, toPath = to, error = null)) {
            throw IllegalStateException("Could not rename")
        }
    }

    private fun describe(path: String, name: String): LocalEntry {
        val manager = NSFileManager.defaultManager
        val attributes = manager.attributesOfItemAtPath(path, error = null)
        val type = attributes?.get(NSFileType) as? String
        val symlink = type == NSFileTypeSymbolicLink

        // fileExistsAtPath follows the link, so a symlink to a directory reads
        // as one — the same thing the SFTP listing reports.
        val directory = memScoped {
            val isDirectory = alloc<BooleanVar>()
            manager.fileExistsAtPath(path, isDirectory = isDirectory.ptr)
            isDirectory.value
        }

        return LocalEntry(
            name = name,
            isDirectory = directory,
            isSymlink = symlink,
            size = if (directory) null else (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue,
            modifiedSeconds = (attributes?.get(NSFileModificationDate) as? NSDate)
                ?.timeIntervalSince1970?.toLong(),
            mode = (attributes?.get(NSFilePosixPermissions) as? NSNumber)?.intValue,
        )
    }
}
