package com.example.ohmyssh.platform

class LocalEntry(
    val name: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long?,
    val modifiedSeconds: Long?,
    val mode: Int?,
)

expect object LocalFs {
    val separator: String

    fun home(): String

    suspend fun absolute(path: String): String
    suspend fun list(path: String): List<LocalEntry>
    suspend fun stat(path: String): LocalEntry
    suspend fun read(path: String, onProgress: ((Long) -> Unit)?): ByteArray
    suspend fun write(path: String, bytes: ByteArray, onProgress: ((Long) -> Unit)?)
    suspend fun mkdir(path: String)

    suspend fun remove(path: String)

    suspend fun rmdir(path: String)

    suspend fun rename(from: String, to: String)
}
