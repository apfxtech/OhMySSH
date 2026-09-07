package com.example.ohmyssh

import com.example.ohmyssh.net.BinaryPlist
import com.example.ohmyssh.net.archivedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The blob macOS caches the last Wi-Fi scan in is an NSKeyedArchiver payload,
 * and it is the one place the network name survives the redaction. This fixture
 * is shaped like a real one and invented down to the last byte.
 */
class BinaryPlistTest {
    private val archive =
        "62706c6973743030d4010203040506191c5924617263686976657258246f626a656374735424746f70582476657273696f6e5f100f4e534b657965644172636869766572a80708090a0b16171855246e756c6c574348414e4e454c58535349445f535452554253534944d20c0d0e12574e532e6b6579735a4e532e6f626a65637473a30f1011800180028003a313141580058006800710285854657374204e65745f101161613a62623a63633a64643a65653a6666d11a1b54726f6f74800412000186a008111b242932444d535b646a6f778286888a8c9092949698a1b5b8bdbf0000000000000101000000000000001d000000000000000000000000000000c4"

    private fun bytes(hex: String) = ByteArray(hex.length / 2) {
        ((hex[it * 2].digitToInt(16) shl 4) or hex[it * 2 + 1].digitToInt(16)).toByte()
    }

    @Test
    fun `reads a name out of a keyed archive`() {
        assertEquals("Test Net", archivedString(BinaryPlist.parse(bytes(archive)), "SSID_STR"))
        assertEquals("aa:bb:cc:dd:ee:ff", archivedString(BinaryPlist.parse(bytes(archive)), "BSSID"))
    }

    @Test
    fun `has nothing to say about a key that is not there`() {
        assertNull(archivedString(BinaryPlist.parse(bytes(archive)), "PASSWORD"))
    }

    @Test
    fun `refuses anything that is not a binary plist`() {
        assertNull(BinaryPlist.parse(ByteArray(0)))
        assertNull(BinaryPlist.parse("not a plist, not even close".encodeToByteArray()))
        // Truncated: the trailer promises an offset table that is not there.
        assertNull(archivedString(BinaryPlist.parse(bytes(archive).copyOfRange(0, 40)), "SSID_STR"))
    }
}
