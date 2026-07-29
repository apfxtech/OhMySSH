package com.example.ohmyssh

import com.example.ohmyssh.terminal.TerminalEmulator
import com.example.ohmyssh.terminal.Utf8StreamDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalTest {
    private fun TerminalEmulator.lineText(row: Int): String =
        snapshot(0) { lines, _ -> lines[row].textRange(0, lines[row].size).trimEnd() }

    @Test
    fun writesPlainTextAndWraps() {
        val terminal = TerminalEmulator(cols = 10, rows = 4)
        terminal.write("hello")
        assertEquals("hello", terminal.lineText(0))

        terminal.write("world!!")
        assertEquals("helloworld", terminal.lineText(0))
        assertEquals("!!", terminal.lineText(1))
    }

    @Test
    fun cursorAddressingAndErase() {
        val terminal = TerminalEmulator(cols = 20, rows = 5)
        terminal.write("first\r\nsecond\r\nthird")
        terminal.write("[2;1H")
        terminal.write("X")
        assertEquals("Xecond", terminal.lineText(1))

        terminal.write("[2J")
        assertEquals("", terminal.lineText(0))
        assertEquals("", terminal.lineText(1))
    }

    @Test
    fun scrollRegionScrollsAndFillsScrollback() {
        val terminal = TerminalEmulator(cols = 10, rows = 3)
        terminal.write("a\r\nb\r\nc\r\nd")
        assertEquals("b", terminal.lineText(0))
        assertEquals("d", terminal.lineText(2))
        assertEquals(1, terminal.scrollbackSize)
        assertTrue(terminal.allText().startsWith("a"))
    }

    @Test
    fun sgrSetsColorsAndAttributes() {
        val terminal = TerminalEmulator(cols = 20, rows = 2)
        terminal.write("[1;31mred[0m plain")
        terminal.snapshot(0) { lines, _ ->
            val line = lines[0]
            assertEquals(1, line.attrs[0])
            assertEquals(1, line.fg[0])
            assertEquals(0, line.attrs[4])
            assertEquals(-1, line.fg[4])
        }
    }

    @Test
    fun altScreenSwitchesAndRestores() {
        val terminal = TerminalEmulator(cols = 10, rows = 3)
        terminal.write("main")
        terminal.write("[?1049h")
        assertEquals("", terminal.lineText(0))
        terminal.write("alt")
        assertEquals("alt", terminal.lineText(0))
        terminal.write("[?1049l")
        assertEquals("main", terminal.lineText(0))
    }

    @Test
    fun titleComesFromOsc() {
        val terminal = TerminalEmulator(cols = 10, rows = 2)
        terminal.write("]0;my-host")
        assertEquals("my-host", terminal.title)
    }

    @Test
    fun decodesUtf8SplitAcrossChunks() {
        val decoder = Utf8StreamDecoder()
        val bytes = "héllo→".encodeToByteArray()
        val out = StringBuilder()
        for (byte in bytes) out.append(decoder.decode(byteArrayOf(byte)))
        assertEquals("héllo→", out.toString())
    }
}
