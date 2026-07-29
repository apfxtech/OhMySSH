package com.example.ohmyssh.platform

expect object AppFiles {
    fun appSupportDirectory(): String

    fun exists(path: String): Boolean
    fun readBytes(path: String): ByteArray
    fun writeBytes(path: String, bytes: ByteArray)

    /// Write-then-rename so a crash mid-write cannot leave a truncated file.
    fun rename(from: String, to: String)

    fun delete(path: String)

    val pathSeparator: String
}

fun AppFiles.readText(path: String): String = readBytes(path).decodeToString()

fun AppFiles.writeTextAtomic(path: String, text: String) {
    val temp = "$path.tmp"
    writeBytes(temp, text.encodeToByteArray())
    rename(temp, path)
}
