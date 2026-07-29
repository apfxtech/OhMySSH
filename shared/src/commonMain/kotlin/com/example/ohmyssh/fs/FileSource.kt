package com.example.ohmyssh.fs

import com.example.ohmyssh.platform.LocalFs
import com.example.ohmyssh.ssh.HostSession
import com.example.ohmyssh.ssh.SftpEntry

class FileEntry(
    val name: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long?,
    val modifyTimeSeconds: Long?,
    val mode: Int?,
)

interface FileSource {
    val id: String

    val label: String

    val isLocal: Boolean

    val separator: String get() = "/"

    suspend fun home(): String

    suspend fun list(path: String): List<FileEntry>
    suspend fun stat(path: String): FileEntry
    suspend fun read(path: String, onProgress: ((Long) -> Unit)? = null): ByteArray
    suspend fun write(path: String, bytes: ByteArray, onProgress: ((Long) -> Unit)? = null)
    suspend fun mkdir(path: String)
    suspend fun remove(path: String)
    suspend fun rmdir(path: String)
    suspend fun rename(from: String, to: String)

    fun join(base: String, name: String): String =
        if (base.endsWith(separator) || base.isEmpty()) "$base$name" else "$base$separator$name"
}

fun FileSource.contains(directory: String, child: String): Boolean {
    if (directory == child) return true
    val prefix = if (directory.endsWith(separator)) directory else "$directory$separator"
    return child.startsWith(prefix)
}

class SftpSource(private val session: HostSession) : FileSource {
    override val id: String = "ssh:${session.id}"
    override val label: String get() = session.title
    override val isLocal: Boolean = false

    private suspend fun channel() = session.sftp()

    private fun adapt(entry: SftpEntry) = FileEntry(
        name = entry.name,
        isDirectory = entry.isDirectory,
        isSymlink = entry.isSymlink,
        size = entry.size,
        modifyTimeSeconds = entry.modifyTimeSeconds,
        mode = entry.mode,
    )

    override suspend fun home(): String = channel().absolute(".")

    override suspend fun list(path: String): List<FileEntry> =
        channel().list(path).map(::adapt)

    override suspend fun stat(path: String): FileEntry = adapt(channel().stat(path))

    override suspend fun read(path: String, onProgress: ((Long) -> Unit)?): ByteArray =
        channel().readBytes(path, onProgress)

    override suspend fun write(path: String, bytes: ByteArray, onProgress: ((Long) -> Unit)?) {
        channel().writeBytes(path, bytes, onProgress)
    }

    override suspend fun mkdir(path: String) = channel().mkdir(path)

    override suspend fun remove(path: String) = channel().remove(path)

    override suspend fun rmdir(path: String) = channel().rmdir(path)

    override suspend fun rename(from: String, to: String) = channel().rename(from, to)
}

class LocalSource(override val id: String) : FileSource {
    override val label: String = "This device"
    override val isLocal: Boolean = true
    override val separator: String = LocalFs.separator

    override suspend fun home(): String = LocalFs.home()

    override suspend fun list(path: String): List<FileEntry> = LocalFs.list(path).map {
        FileEntry(
            name = it.name,
            isDirectory = it.isDirectory,
            isSymlink = it.isSymlink,
            size = it.size,
            modifyTimeSeconds = it.modifiedSeconds,
            mode = it.mode,
        )
    }

    override suspend fun stat(path: String): FileEntry = LocalFs.stat(path).let {
        FileEntry(
            name = it.name,
            isDirectory = it.isDirectory,
            isSymlink = it.isSymlink,
            size = it.size,
            modifyTimeSeconds = it.modifiedSeconds,
            mode = it.mode,
        )
    }

    override suspend fun read(path: String, onProgress: ((Long) -> Unit)?): ByteArray =
        LocalFs.read(path, onProgress)

    override suspend fun write(path: String, bytes: ByteArray, onProgress: ((Long) -> Unit)?) =
        LocalFs.write(path, bytes, onProgress)

    override suspend fun mkdir(path: String) = LocalFs.mkdir(path)

    override suspend fun remove(path: String) = LocalFs.remove(path)

    override suspend fun rmdir(path: String) = LocalFs.rmdir(path)

    override suspend fun rename(from: String, to: String) = LocalFs.rename(from, to)
}
