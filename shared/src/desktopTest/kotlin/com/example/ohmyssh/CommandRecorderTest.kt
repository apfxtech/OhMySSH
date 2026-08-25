package com.example.ohmyssh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandRecorderTest {
    @Test
    fun capturesWhatWasTyped() {
        val shell = FakeShell()
        shell.run("ls -la /var/log")
        assertEquals(listOf("ls -la /var/log"), shell.texts)
    }

    @Test
    fun readsTheCorrectedLineNotTheKeystrokes() {
        val shell = FakeShell()
        shell.prompt()
        shell.type("ls -lz")
        shell.type("")
        shell.type("a")
        shell.enter()
        assertEquals(listOf("ls -la"), shell.texts)
    }

    @Test
    fun neverRecordsWhatTheShellDidNotEcho() {
        val shell = FakeShell()
        shell.output("[sudo] password for user: ")
        shell.echo = false
        shell.type("correct horse battery staple")
        shell.enter()
        assertEquals(emptyList(), shell.texts)
    }

    @Test
    fun aBareEnterIsNotACommand() {
        val shell = FakeShell()
        shell.prompt()
        shell.enter()
        shell.prompt()
        shell.enter()
        assertEquals(emptyList(), shell.texts)
    }

    @Test
    fun putsAWrappedCommandBackTogether() {
        val shell = FakeShell(cols = 20, prompt = "$ ")
        val long = "grep -rn needle /etc --include=*.conf"
        shell.run(long)
        assertEquals(listOf(long), shell.texts)
    }

    @Test
    fun ignoresKeysTypedIntoAFullScreenProgram() {
        val shell = FakeShell()
        shell.run("vim notes.txt")
        shell.output("[?1049h")
        shell.type("ihello")
        shell.enter()
        shell.type(":wq")
        shell.enter()
        assertEquals(listOf("vim notes.txt"), shell.texts)
    }

    @Test
    fun splitsAMultiLinePasteIntoOneCommandPerLine() {
        val shell = FakeShell()
        shell.prompt()
        shell.terminal.sendKeys("uptime\rwhoami\rid -u\r")
        assertEquals(listOf("uptime", "whoami", "id -u"), shell.texts)
    }

    @Test
    fun neverLearnsAPasswordPromptAsAShellPrompt() {
        val shell = FakeShell()
        shell.run("sudo id")
        shell.echo = false
        shell.output("[sudo] password for user: ")
        shell.type("hunter2")
        shell.enter()

        assertEquals(listOf("sudo id"), shell.texts)
        assertEquals("user@host:~$ ", shell.recorder.lastPrompt)
    }

    @Test
    fun capturesACommandRecalledWithAnArrow() {
        val shell = FakeShell()
        shell.run("systemctl status nginx")
        shell.output("active (running)\r\n")
        shell.prompt()

        shell.terminal.sendKeys("[A")
        shell.output("systemctl status nginx")
        shell.enter()

        assertEquals(
            listOf("systemctl status nginx", "systemctl status nginx"),
            shell.texts,
        )
    }

    @Test
    fun findsThePromptAgainAfterCtrlCAbandonsTheLine() {
        val shell = FakeShell()
        shell.prompt()
        shell.type("rm -rf /import")
        shell.terminal.sendKeys("")
        shell.output("^C\r\n")
        shell.prompt()
        shell.type("pwd")
        shell.enter()

        assertEquals(listOf("pwd"), shell.texts)
    }

    @Test
    fun survivesCtrlLReprintingThePromptElsewhere() {
        val shell = FakeShell()
        shell.output("noise\r\nnoise\r\n")
        shell.prompt()
        shell.type("df -h")
        shell.terminal.sendKeys("")
        shell.output("[2J[H")
        shell.prompt()
        shell.output("df -h")
        shell.enter()

        assertEquals(listOf("df -h"), shell.texts)
    }

    @Test
    fun doesNotMistakeStreamingOutputForACommand() {
        val shell = FakeShell()
        shell.prompt()
        shell.type("t")
        repeat(10) { shell.output("Jul 30 12:0$it host kernel: something happened\r\n") }
        shell.enter()

        assertEquals(emptyList(), shell.texts)
    }

    @Test
    fun takesTheExitCodeAndDurationFromShellIntegration() {
        val shell = FakeShell()
        shell.output("]133;A")
        shell.prompt()
        shell.output("]133;B")
        shell.type("false")
        shell.enter()
        shell.output("]133;C")
        shell.clock += 2500
        shell.output("]133;D;1")

        assertEquals(listOf("false"), shell.texts)
        val command = shell.captured.single()
        assertEquals(1, command.exitCode)
        assertEquals(2500L, command.durationMs)
        assertTrue(command.failed)
    }

    @Test
    fun leavesExitCodeUnknownWithoutShellIntegration() {
        val shell = FakeShell()
        shell.run("true")
        val command = shell.captured.single()
        assertNull(command.exitCode)
        assertNull(command.durationMs)
    }

    @Test
    fun followsTheWorkingDirectoryFromOsc7() {
        val shell = FakeShell()
        shell.output("]7;file://host/home/user/src%20dir")
        shell.run("make")
        assertEquals("/home/user/src dir", shell.captured.single().cwd)
    }

    @Test
    fun stampsEachCommandWithTheTimeItWentOut() {
        val shell = FakeShell()
        shell.run("one")
        shell.clock += 60_000
        shell.output("\r\n")
        shell.run("two")

        assertEquals(listOf("one", "two"), shell.texts)
        assertEquals(60_000L, shell.captured[1].at - shell.captured[0].at)
    }

    @Test
    fun readsAPromptThatHasScrolledIntoTheScrollback() {
        val shell = FakeShell(cols = 12, rows = 3, prompt = "$ ")
        shell.output("a\r\nb\r\n")
        val long = "echo abcdefghijklmnopqrstuvwxyz"
        shell.run(long)
        assertEquals(listOf(long), shell.texts)
    }
}
