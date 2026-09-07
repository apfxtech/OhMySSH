package com.example.ohmyssh.net

/**
 * Enough of the binary property list format to read one archive macOS will not
 * hand over any other way. Values come back as plain Kotlin types — [Map],
 * [List], [String], [Long], [ByteArray] — with [Uid] for the cross references a
 * keyed archive is built out of.
 *
 * Deliberately total: every malformed length, bad offset or truncated tail
 * returns null rather than throwing, because the input is an OS blob that owes
 * this parser nothing.
 */
internal class Uid(val value: Int)

internal object BinaryPlist {
    private val MAGIC = "bplist00".encodeToByteArray()
    private const val TRAILER = 32

    fun parse(bytes: ByteArray): Any? {
        if (bytes.size < MAGIC.size + TRAILER) return null
        if (!MAGIC.indices.all { bytes[it] == MAGIC[it] }) return null

        val trailer = bytes.size - TRAILER
        val offsetSize = bytes[trailer + 6].toInt() and 0xFF
        val refSize = bytes[trailer + 7].toInt() and 0xFF
        val count = readBigEndian(bytes, trailer + 8, 8).toInt()
        val top = readBigEndian(bytes, trailer + 16, 8).toInt()
        val tableAt = readBigEndian(bytes, trailer + 24, 8).toInt()
        if (offsetSize !in 1..8 || refSize !in 1..8 || count <= 0) return null
        if (tableAt < 0 || tableAt + count * offsetSize > bytes.size) return null

        val offsets = IntArray(count) { readBigEndian(bytes, tableAt + it * offsetSize, offsetSize).toInt() }
        return Reader(bytes, offsets, refSize).read(top, depth = 0)
    }

    private class Reader(val bytes: ByteArray, val offsets: IntArray, val refSize: Int) {
        fun read(index: Int, depth: Int): Any? {
            // A ring of references would otherwise recurse until the stack goes.
            if (depth > 32) return null
            val start = offsets.getOrNull(index) ?: return null
            if (start < 0 || start >= bytes.size) return null

            val marker = bytes[start].toInt() and 0xFF
            val low = marker and 0x0F
            return when (marker and 0xF0) {
                0x00 -> when (low) {
                    0x08 -> false
                    0x09 -> true
                    else -> null
                }
                0x10 -> readBigEndian(bytes, start + 1, 1 shl low)
                0x40 -> sized(start, low)?.let { (at, length) -> bytes.copyOfRange(at, at + length) }
                0x50 -> sized(start, low)?.let { (at, length) ->
                    bytes.decodeToString(at, at + length)
                }
                0x60 -> sized(start, low)?.let { (at, length) ->
                    val chars = CharArray(length) {
                        readBigEndian(bytes, at + it * 2, 2).toInt().toChar()
                    }
                    chars.concatToString()
                }
                0x80 -> Uid(readBigEndian(bytes, start + 1, low + 1).toInt())
                0xA0, 0xC0 -> sized(start, low)?.let { (at, length) ->
                    List(length) { read(refAt(at, it), depth + 1) }
                }
                0xD0 -> sized(start, low)?.let { (at, length) ->
                    val entries = LinkedHashMap<Any?, Any?>(length)
                    for (slot in 0 until length) {
                        val key = read(refAt(at, slot), depth + 1)
                        entries[key] = read(refAt(at, length + slot), depth + 1)
                    }
                    entries
                }
                else -> null
            }
        }

        private fun refAt(base: Int, slot: Int): Int =
            readBigEndian(bytes, base + slot * refSize, refSize).toInt()

        /** Where an object's payload starts and how long it is, 0xF marker included. */
        private fun sized(start: Int, low: Int): Pair<Int, Int>? {
            if (low != 0x0F) return start + 1 to low
            val header = bytes.getOrNull(start + 1)?.toInt()?.and(0xFF) ?: return null
            if (header and 0xF0 != 0x10) return null
            val width = 1 shl (header and 0x0F)
            val length = readBigEndian(bytes, start + 2, width).toInt()
            return start + 2 + width to length
        }
    }

    private fun readBigEndian(bytes: ByteArray, at: Int, width: Int): Long {
        if (at < 0 || width <= 0 || at + width > bytes.size) return 0
        var value = 0L
        for (step in 0 until width) value = (value shl 8) or (bytes[at + step].toLong() and 0xFF)
        return value
    }
}

/**
 * Pulls [key] out of an NSKeyedArchiver payload. The archive stores every string
 * once in `$objects` and refers to it by index, so a key is found by its own
 * index turning up in some dictionary's parallel key and value arrays.
 */
internal fun archivedString(root: Any?, key: String): String? {
    val objects = (root as? Map<*, *>)?.get("\$objects") as? List<*> ?: return null
    val keyIndex = objects.indexOfFirst { it == key }
    if (keyIndex < 0) return null

    for (entry in objects) {
        val dictionary = entry as? Map<*, *> ?: continue
        val keys = dictionary["NS.keys"] as? List<*> ?: continue
        val values = dictionary["NS.objects"] as? List<*> ?: continue
        val slot = keys.indexOfFirst { (it as? Uid)?.value == keyIndex }
        if (slot < 0 || slot >= values.size) continue
        val reference = (values[slot] as? Uid)?.value ?: continue
        val text = objects.getOrNull(reference) as? String ?: continue
        if (text.isNotEmpty()) return text
    }
    return null
}
