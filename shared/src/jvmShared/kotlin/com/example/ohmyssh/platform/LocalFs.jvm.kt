package com.example.ohmyssh.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

private const val CHUNK = 1 shl 16

actual object LocalFs {
    actual val separator: String = File.separator

    actual fun home(): String = AppFiles.userHomeDirectory

    actual suspend fun absolute(path: String): String = withContext(Dispatchers.IO) {
        File(path).absoluteFile.toPath().normalize().toString()
    }

    actual suspend fun list(path: String): List<LocalEntry> = withContext(Dispatchers.IO) {
        val dir = File(path)
        // listFiles returns null for "not a directory" and for "not allowed to
        // look", and the browser needs to say which.
        val children = dir.listFiles()
            ?: throw IOException(
                if (!dir.exists()) {
                    "No such directory"
                } else if (!dir.isDirectory) {
                    "Not a directory"
                } else {
                    "Permission denied"
                },
            )
        children.map { describe(it) }
    }

    actual suspend fun stat(path: String): LocalEntry = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) throw IOException("No such file")
        describe(file)
    }

    actual suspend fun read(
        path: String,
        onProgress: ((Long) -> Unit)?,
    ): ByteArray = withContext(Dispatchers.IO) {
        val file = File(path)
        val total = file.length()
        if (total > Int.MAX_VALUE) throw IOException("File is too large to open")

        val buffer = ByteArray(total.toInt())
        var done = 0
        file.inputStream().use { stream ->
            while (done < buffer.size) {
                val read = stream.read(buffer, done, minOf(CHUNK, buffer.size - done))
                if (read <= 0) break
                done += read
                onProgress?.invoke(done.toLong())
            }
        }
        // A file that shrank between the length() call and the read.
        if (done == buffer.size) buffer else buffer.copyOf(done)
    }

    actual suspend fun write(
        path: String,
        bytes: ByteArray,
        onProgress: ((Long) -> Unit)?,
    ): Unit = withContext(Dispatchers.IO) {
        File(path).outputStream().use { stream ->
            var done = 0
            while (done < bytes.size) {
                val length = minOf(CHUNK, bytes.size - done)
                stream.write(bytes, done, length)
                done += length
                onProgress?.invoke(done.toLong())
            }
        }
    }

    actual suspend fun mkdir(path: String): Unit = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (dir.isDirectory) return@withContext
        if (!dir.mkdir()) throw IOException("Could not create ${dir.name}")
    }

    actual suspend fun remove(path: String): Unit = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.isDirectory) throw IOException("${file.name} is a directory")
        if (!file.delete()) throw IOException("Could not delete ${file.name}")
    }

    actual suspend fun rmdir(path: String): Unit = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (dir.isDirectory && (dir.list()?.isNotEmpty() == true)) {
            throw IOException("${dir.name} is not empty")
        }
        if (!dir.delete()) throw IOException("Could not delete ${dir.name}")
    }

    actual suspend fun rename(from: String, to: String): Unit = withContext(Dispatchers.IO) {
        if (!File(from).renameTo(File(to))) throw IOException("Could not rename")
    }

    private fun describe(file: File): LocalEntry {
        val path = file.toPath()
        val symlink = runCatching { Files.isSymbolicLink(path) }.getOrDefault(false)
        val directory = file.isDirectory
        return LocalEntry(
            name = file.name,
            isDirectory = directory,
            isSymlink = symlink,
            size = if (directory) null else file.length(),
            modifiedSeconds = file.lastModified().takeIf { it > 0L }?.div(1000),
            mode = runCatching { modeBits(Files.getPosixFilePermissions(path)) }.getOrNull(),
        )
    }

    private fun modeBits(permissions: Set<PosixFilePermission>): Int {
        var mode = 0
        for (permission in permissions) {
            mode = mode or when (permission) {
                PosixFilePermission.OWNER_READ -> 0x100
                PosixFilePermission.OWNER_WRITE -> 0x080
                PosixFilePermission.OWNER_EXECUTE -> 0x040
                PosixFilePermission.GROUP_READ -> 0x020
                PosixFilePermission.GROUP_WRITE -> 0x010
                PosixFilePermission.GROUP_EXECUTE -> 0x008
                PosixFilePermission.OTHERS_READ -> 0x004
                PosixFilePermission.OTHERS_WRITE -> 0x002
                PosixFilePermission.OTHERS_EXECUTE -> 0x001
            }
        }
        return mode
    }
}
