package com.example.ohmyssh

import com.example.ohmyssh.services.FileLog
import com.example.ohmyssh.services.Log
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileLogTest {

    /// FileLog is one object for the process, so every test here writes to the
    /// same file and looks only for its own lines.
    private companion object {
        val directory: File = File(System.getProperty("java.io.tmpdir"), "ohmyssh-log-test")
            .apply {
                deleteRecursively()
                mkdirs()
                deleteOnExit()
            }
    }

    @BeforeTest
    fun install() {
        FileLog.install(directory = directory.path)
    }

    private fun lines(): List<String> =
        File(directory, "ohmyssh.log").readLines().filter { it.isNotBlank() }

    @Test
    fun `writes one greppable line per event`() {
        Log.info("mcp", "call=7 tool=run_command session=abc")
        Log.warn("ssh:host", "link dropped on call=7")

        val call = lines().single { it.contains("tool=run_command") }
        // Level is padded so the columns line up, so the split is on runs.
        val fields = call.split(Regex("\\s+"), limit = 4)
        assertTrue(fields[0].endsWith("Z"), "timestamp is UTC ISO 8601: ${fields[0]}")
        assertEquals("INFO", fields[1])
        assertEquals("mcp", fields[2])
        assertEquals("call=7 tool=run_command session=abc", fields[3])

        // One id, one grep, both ends of it.
        assertEquals(2, lines().count { it.contains("call=7") })
        assertContains(lines().single { it.contains("link dropped") }, "WARN  ssh:host")
    }

    /// The whole point of the file: an event never spans two lines, so a grep
    /// hit is a whole event and a line count is an event count.
    @Test
    fun `folds a multi-line message onto one line`() {
        Log.error("ssh", "boom", RuntimeException("stack\nframe\nframe"))

        val written = lines()
        assertTrue(written.all { it.first().isDigit() }, "every line starts with a timestamp")
        val trace = written.single { it.contains("RuntimeException") }
        assertContains(trace, "\\n")
        assertTrue('\n' !in trace)
    }

    @Test
    fun `keeps a command verbatim`() {
        Log.info("paste", """sent by=agent cmd=grep -E "a|b" /etc/hosts""")

        assertContains(
            lines().single { it.contains("by=agent") },
            """cmd=grep -E "a|b" /etc/hosts""",
        )
    }
}
