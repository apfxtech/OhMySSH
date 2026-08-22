package com.example.ohmyssh

import com.example.ohmyssh.terminal.CellColor
import com.example.ohmyssh.terminal.ScreenPoint
import com.example.ohmyssh.terminal.TerminalEmulator
import com.example.ohmyssh.terminal.Utf8StreamDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalTest {
    private fun TerminalEmulator.lineText(row: Int): String =
        snapshot(0) { lines, _, _ -> lines[row].textRange(0, lines[row].size).trimEnd() }

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
        terminal.snapshot(0) { lines, _, _ ->
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
    fun growingTheGridLeavesBlankDefaultCells() {
        val terminal = TerminalEmulator(cols = 20, rows = 4)
        terminal.write("hello")
        terminal.resize(60, 8)

        terminal.snapshot(0) { lines, _, _ ->
            val line = lines[0]
            assertEquals(60, line.size)
            for (x in 20 until 60) {
                assertEquals(' ', line.chars[x])
                assertEquals(CellColor.DEFAULT, line.bg[x])
                assertEquals(CellColor.DEFAULT, line.fg[x])
            }
            assertEquals("hello", line.textRange(0, line.size).trimEnd())
        }
    }

    @Test
    fun titleComesFromOsc() {
        val terminal = TerminalEmulator(cols = 10, rows = 2)
        terminal.write("]0;my-host")
        assertEquals("my-host", terminal.title)
    }

    @Test
    fun selectionSpansRowsAndDropsPadding() {
        val terminal = TerminalEmulator(cols = 10, rows = 3)
        terminal.write("first\r\nsecond\r\nthird")
        val top = terminal.topRow(0)

        assertEquals("irst", terminal.textBetween(ScreenPoint(top, 1), ScreenPoint(top, 4)))
        // Padding goes only when the selection runs to the end of the row.
        assertEquals("first", terminal.textBetween(ScreenPoint(top, 0), ScreenPoint(top, 9)))
        assertEquals("st  ", terminal.textBetween(ScreenPoint(top, 3), ScreenPoint(top, 6)))
        assertEquals(
            "first\nsecond\nthi",
            terminal.textBetween(ScreenPoint(top, 0), ScreenPoint(top + 2, 2)),
        )
        // Anchor and head swapped: dragging upwards reads the same text.
        assertEquals(
            "first\nsecond\nthi",
            terminal.textBetween(ScreenPoint(top + 2, 2), ScreenPoint(top, 0)),
        )
    }

    @Test
    fun selectionReachesIntoScrollbackAndKeepsWrappedLinesWhole() {
        val terminal = TerminalEmulator(cols = 6, rows = 2)
        terminal.write("longcommand\r\nnext\r\ntail")
        assertEquals(2, terminal.scrollbackSize)

        val top = terminal.topRow(terminal.scrollbackSize)
        assertEquals("longcommand", terminal.textBetween(ScreenPoint(top, 0), ScreenPoint(top + 1, 5)))
    }

    @Test
    fun wordAtStopsAtWhitespace() {
        val terminal = TerminalEmulator(cols = 40, rows = 2)
        terminal.write("cat /etc/hosts here")
        val row = terminal.topRow(0)

        assertEquals(4..13, terminal.wordAt(ScreenPoint(row, 8)))
        assertEquals(null, terminal.wordAt(ScreenPoint(row, 3)))
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
