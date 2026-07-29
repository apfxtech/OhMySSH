package com.example.ohmyssh.terminal

/// Decodes a byte stream that arrives in arbitrary chunks: a multi-byte
/// sequence split across two reads would otherwise land as replacement
/// characters.
class Utf8StreamDecoder {
    private var carry = ByteArray(0)

    fun decode(chunk: ByteArray): String {
        val combined = if (carry.isEmpty()) chunk else carry + chunk
        val complete = completeLength(combined)
        carry = if (complete == combined.size) {
            ByteArray(0)
        } else {
            combined.copyOfRange(complete, combined.size)
        }
        if (complete == 0) return ""
        return combined.decodeToString(0, complete)
    }

    private fun completeLength(bytes: ByteArray): Int {
        var index = bytes.size
        var scanned = 0
        while (index > 0 && scanned < 4) {
            val b = bytes[index - 1].toInt() and 0xFF
            if (b and 0x80 == 0) return index
            if (b and 0xC0 == 0xC0) {
                val needed = when {
                    b and 0xF8 == 0xF0 -> 4
                    b and 0xF0 == 0xE0 -> 3
                    else -> 2
                }
                return if (bytes.size - (index - 1) >= needed) bytes.size else index - 1
            }
            index--
            scanned++
        }
        return bytes.size
    }
}
