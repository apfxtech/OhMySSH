package com.example.ohmyssh

import com.example.ohmyssh.terminal.PasteQueue
import com.example.ohmyssh.terminal.PasteWait
import kotlin.test.Test
import kotlin.test.assertEquals

class PasteQueueTest {
    /// The queue is a state machine over tick(); the session drives it on a
    /// timer, a test drives it by hand so nothing here depends on wall clock.
    private fun queueOn(shell: FakeShell) = PasteQueue(
        terminal = shell.terminal,
        lastPrompt = { shell.recorder.lastPrompt },
        now = { shell.clock },
    )

    private fun PasteQueue.settle(shell: FakeShell, rounds: Int = 3) {
        repeat(rounds) {
            shell.clock += 300
            tick()
        }
    }

    @Test
    fun sendsOneCommandPerPrompt() {
        val shell = FakeShell()
        shell.prompt()
        val paste = queueOn(shell)

        paste.submit("uptime\nwhoami\n")
        paste.settle(shell)
        assertEquals(listOf("uptime\r"), shell.wire)

        // The command is still running: the screen has gone quiet but there is
        // no prompt on it, and quiet alone must never release the next command.
        shell.output("up 3 days\r\n")
        paste.settle(shell)
        assertEquals(listOf("uptime\r"), shell.wire)

        shell.prompt()
        paste.settle(shell)
        assertEquals(listOf("uptime\r", "whoami\r"), shell.wire)
        assertEquals(0, paste.remaining)
    }

    @Test
    fun runsTheLastCommandWithoutWaitingForAnEnter() {
        val shell = FakeShell()
        shell.prompt()
        val paste = queueOn(shell)

        // Selected in an editor, so the block came over without its last
        // newline. It is still a block of commands, and all of them run.
        paste.submit("uptime\nwhoami")
        paste.settle(shell)
        shell.output("up 3 days\r\n")
        shell.prompt()
        paste.settle(shell)

        assertEquals(listOf("uptime\r", "whoami\r"), shell.wire)
    }

    @Test
    fun holdsTheRestOfThePasteAtAPasswordPrompt() {
        val shell = FakeShell()
        shell.prompt()
        val paste = queueOn(shell)

        paste.submit("sudo id\nwhoami\n")
        paste.settle(shell)
        assertEquals(listOf("sudo id\r"), shell.wire)

        // sudo asks, and stops echoing. The second command must not become the
        // password, however long the queue has to wait.
        shell.echo = false
        shell.output("[sudo] password for user: ")
        paste.settle(shell, rounds = 6)
        assertEquals(listOf("sudo id\r"), shell.wire)
        assertEquals(PasteWait.SECRET, paste.waiting)

        // The person types it themselves; the queue picks up where it stopped.
        shell.echo = true
        shell.output("\r\nuid=0(root)\r\n")
        shell.prompt()
        paste.settle(shell)
        assertEquals(listOf("sudo id\r", "whoami\r"), shell.wire)
    }

    @Test
    fun holdsWhileTheFarEndSaysNothingAtAll() {
        val shell = FakeShell()
        shell.prompt()
        val paste = queueOn(shell)

        paste.submit("read -s -p 'PIN ' pin\necho \$pin\n")
        paste.settle(shell)
        assertEquals(1, shell.wire.size)

        // Echo is off and the prompt was printed before the paste, so the only
        // sign left is that nothing came back. That is enough to hold.
        shell.echo = false
        paste.settle(shell, rounds = 6)
        assertEquals(1, shell.wire.size)
    }

    @Test
    fun holdsWhileAFullScreenProgramIsUp() {
        val shell = FakeShell()
        shell.prompt()
        val paste = queueOn(shell)

        paste.submit("uptime\nwhoami\n")
        paste.settle(shell)
        assertEquals(listOf("uptime\r"), shell.wire)

        shell.output("\u001B[?1049h")
        paste.settle(shell, rounds = 6)
        assertEquals(listOf("uptime\r"), shell.wire)
        assertEquals(PasteWait.FULLSCREEN, paste.waiting)

        shell.output("\u001B[?1049l")
        shell.prompt()
        paste.settle(shell)
        assertEquals(listOf("uptime\r", "whoami\r"), shell.wire)
    }

    @Test
    fun sendsAMultiLineCommandInOneWrite() {
        val shell = FakeShell()
        shell.prompt()
        val paste = queueOn(shell)

        paste.submit("if true; then\n  echo hi\nfi\nwhoami\n")
        paste.settle(shell)
        assertEquals(listOf("if true; then\r  echo hi\rfi\r"), shell.wire)
    }

    @Test
    fun dropsCommentsBeforeTheyReachTheShell() {
        val shell = FakeShell()
        shell.prompt()
        val paste = queueOn(shell)

        paste.submit("# check the uptime\nuptime\n")
        assertEquals(listOf("uptime\r"), shell.wire)
    }

    @Test
    fun sendsASingleLineAsTypedAndUnread() {
        val shell = FakeShell()
        shell.output("Password: ")
        val paste = queueOn(shell)

        // A password out of a manager is one line and starts with whatever it
        // starts with. Nothing here may rewrite it, hold it back, or press
        // Enter on it when the clipboard had no newline of its own.
        paste.submit("#hunter2")
        assertEquals(listOf("#hunter2"), shell.wire)
    }

    @Test
    fun wrapsAPasteInTheMarkersWhenTheFarEndAsksForThem() {
        val shell = FakeShell()
        shell.output("\u001B[?2004h")
        val paste = queueOn(shell)

        paste.submit("secret")
        assertEquals(listOf("\u001B[200~secret\u001B[201~"), shell.wire)
    }

    @Test
    fun handsAFullScreenProgramThePasteUnsplit() {
        val shell = FakeShell()
        shell.output("\u001B[?1049h")
        val paste = queueOn(shell)

        paste.submit("# a comment vim should keep\nline two\n")
        assertEquals(listOf("# a comment vim should keep\rline two\r"), shell.wire)
    }

    @Test
    fun cancelDropsWhatIsLeft() {
        val shell = FakeShell()
        shell.prompt()
        val paste = queueOn(shell)

        paste.submit("uptime\nwhoami\nid -u\n")
        paste.settle(shell)
        assertEquals(listOf("uptime\r"), shell.wire)

        paste.cancel()
        assertEquals(0, paste.remaining)
        shell.prompt()
        paste.settle(shell)
        assertEquals(listOf("uptime\r"), shell.wire)
    }

    @Test
    fun writesEachPastedCommandToTheHistoryOnItsOwn() {
        val shell = FakeShell()
        shell.prompt()
        val paste = queueOn(shell)

        paste.submit("uptime\nwhoami\n")
        paste.settle(shell)
        shell.output("up 3 days\r\n")
        shell.prompt()
        paste.settle(shell)

        assertEquals(listOf("uptime", "whoami"), shell.texts)
    }
}
