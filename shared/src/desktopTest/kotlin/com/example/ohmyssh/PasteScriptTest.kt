package com.example.ohmyssh

import com.example.ohmyssh.terminal.parsePaste
import com.example.ohmyssh.terminal.sanitizePasted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasteScriptTest {
    private fun commands(text: String): List<String> = parsePaste(text).commands

    @Test
    fun splitsABlockIntoOneCommandPerLine() {
        assertEquals(
            listOf("uptime", "whoami", "id -u"),
            commands("uptime\nwhoami\nid -u\n"),
        )
    }

    @Test
    fun dropsWholeLineComments() {
        val script = parsePaste(
            """
            # Update the package index first
            apt update
            # Then upgrade
            apt upgrade -y
            """.trimIndent() + "\n",
        )
        assertEquals(listOf("apt update", "apt upgrade -y"), script.commands)
        assertEquals(2, script.droppedComments)
    }

    @Test
    fun keepsATrailingComment() {
        assertEquals(listOf("apt update # index"), commands("apt update # index\n"))
    }

    @Test
    fun keepsAHashInsideAWord() {
        assertEquals(
            listOf("curl https://example.com/page#anchor"),
            commands("curl https://example.com/page#anchor\n"),
        )
    }

    @Test
    fun keepsAHereDocWholeAndUntouched() {
        val body = """
            cat > /etc/motd <<'EOF'
            # this line is the file, not a comment

            welcome
            EOF
            echo written
        """.trimIndent() + "\n"

        val script = parsePaste(body)
        assertEquals(2, script.commands.size)
        assertEquals(
            "cat > /etc/motd <<'EOF'\n# this line is the file, not a comment\n\nwelcome\nEOF",
            script.commands[0],
        )
        assertEquals("echo written", script.commands[1])
        assertEquals(0, script.droppedComments)
    }

    @Test
    fun stripsLeadingTabsOnlyForADashHereDoc() {
        val script = parsePaste("cat <<-EOF\n\tbody\n\tEOF\necho after\n")
        assertEquals(listOf("cat <<-EOF\n\tbody\n\tEOF", "echo after"), script.commands)
    }

    @Test
    fun keepsAMultiLineBlockAsOneCommand() {
        val script = parsePaste(
            """
            if [ -f /etc/os-release ]; then
              cat /etc/os-release
            fi
            echo after
            """.trimIndent() + "\n",
        )
        assertEquals(2, script.commands.size)
        assertTrue(script.commands[0].startsWith("if [ -f /etc/os-release ]; then"))
        assertTrue(script.commands[0].endsWith("fi"))
    }

    @Test
    fun doesNotCloseALoopOnTheWordDoneInAnArgument() {
        val script = parsePaste("for f in a b; do\n  echo done\ndone\necho after\n")
        assertEquals(listOf("for f in a b; do\n  echo done\ndone", "echo after"), script.commands)
    }

    @Test
    fun joinsBackslashAndPipeContinuations() {
        assertEquals(listOf("echo one \\\ntwo"), commands("echo one \\\ntwo\n"))
        assertEquals(listOf("cat /etc/passwd |\ngrep root"), commands("cat /etc/passwd |\ngrep root\n"))
        assertEquals(listOf("true &&\nfalse"), commands("true &&\nfalse\n"))
    }

    @Test
    fun keepsANewlineInsideQuotes() {
        assertEquals(listOf("echo \"line one\nline two\""), commands("echo \"line one\nline two\"\n"))
    }

    @Test
    fun dropsMarkdownFencesAndBlankLines() {
        val script = parsePaste("```bash\nuptime\n\nwhoami\n```\n")
        assertEquals(listOf("uptime", "whoami"), script.commands)
        assertEquals(3, script.droppedNoise)
    }

    @Test
    fun stripsDocumentationPromptsOnlyWhenEveryLineHasOne() {
        assertEquals(listOf("apt update", "apt upgrade"), commands("$ apt update\n$ apt upgrade\n"))
        assertEquals(listOf("$ apt update", "echo hi"), commands("$ apt update\necho hi\n"))
    }

    @Test
    fun repairsInvisibleCharactersFromCopiedText() {
        assertEquals(listOf("echo hi", "ls"), commands("echo\u00A0hi\nls\n"))
        assertEquals(listOf("echo hi", "ls"), commands("ec\u200Bho hi\nls\n"))
    }

    @Test
    fun dropsEscapeSequencesFromCopiedOutput() {
        assertEquals("red", sanitizePasted("\u001B[31mred\u001B[0m"))
        assertEquals("ls", sanitizePasted("\u001B]0;a window title\u0007ls"))
        assertEquals(listOf("ls"), commands("\u001B[31mls\u001B[0m\n"))
    }

    @Test
    fun readsTheSameCommandsWithOrWithoutATrailingNewline() {
        assertEquals(listOf("ls", "whoami"), commands("ls\nwhoami\n"))
        assertEquals(listOf("ls", "whoami"), commands("ls\nwhoami"))
    }

    @Test
    fun keepsAnUnterminatedConstructRatherThanDroppingIt() {
        val script = parsePaste("cat <<EOF\nline\n")
        assertEquals(listOf("cat <<EOF\nline"), script.commands)
    }
}
