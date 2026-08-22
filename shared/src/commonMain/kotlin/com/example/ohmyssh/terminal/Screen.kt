package com.example.ohmyssh.terminal

data class ScreenPoint(val row: Long, val column: Int)

class LogicalLine(val text: String, val firstRow: Long, val lastRow: Long) {
    operator fun contains(row: Long): Boolean = row in firstRow..lastRow
}

sealed class ShellSignal {
    data object PromptStart : ShellSignal()

    data object InputStart : ShellSignal()

    data class Executing(val command: String?) : ShellSignal()

    data class Finished(val exitCode: Int?) : ShellSignal()

    data class WorkingDirectory(val path: String) : ShellSignal()
}

internal fun fileUrlPath(raw: String): String {
    val withoutScheme = raw.removePrefix("file://")
    val path = withoutScheme.substringAfter('/', "").let { if (it.isEmpty()) withoutScheme else "/$it" }
    return percentDecode(path)
}

private fun percentDecode(text: String): String {
    if (!text.contains('%')) return text
    val bytes = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (ch == '%' && i + 2 < text.length) {
            val hex = text.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                bytes.add(hex.toByte())
                i += 3
                continue
            }
        }
        for (byte in ch.toString().encodeToByteArray()) bytes.add(byte)
        i++
    }
    return bytes.toByteArray().decodeToString()
}
