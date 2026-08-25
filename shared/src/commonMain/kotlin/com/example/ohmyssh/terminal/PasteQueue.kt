package com.example.ohmyssh.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ohmyssh.platform.epochMillis
import com.example.ohmyssh.services.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/// Why the queue is holding a command back, for the strip the session shows.
enum class PasteWait { NONE, PROMPT, SECRET, FULLSCREEN }

/**
 * Sends a pasted block one command at a time, each only once the far end is
 * back at a prompt.
 *
 * A block written straight to the channel is one buffer of input, and the shell
 * is not the only thing reading it: the moment a command asks a question —
 * `sudo`, `ssh`, `apt`, a `read` in a script — the rest of the paste answers it.
 * The second line becomes the password, the third the retry, and with an `ssh`
 * in the block the remaining commands run on a different machine altogether.
 *
 * So nothing is ever sent on a timer. A command goes out only on a positive
 * sign that a shell is waiting for one, which a password prompt never gives:
 * the queue simply holds until the person has answered and the prompt is back,
 * and then carries on.
 */
class PasteQueue(
    private val terminal: TerminalEmulator,
    private val lastPrompt: () -> String? = { null },
    private val now: () -> Long = ::epochMillis,
    /// Only drives [tick] on a timer. A test keeps its own clock and ticks by
    /// hand, so the queue works without one.
    private val scope: CoroutineScope? = null,
    private val onNotice: (String) -> Unit = {},
) {
    /// Commands still to send.
    var remaining by mutableIntStateOf(0)
        private set

    var waiting by mutableStateOf(PasteWait.NONE)
        private set

    private val queue = ArrayDeque<String>()
    private var sentAny = false
    private var job: Job? = null

    private var lastRevision = -1L
    private var quietSince = 0L
    private var sawDataSinceSend = true

    /**
     * Takes a paste as the person made it and decides what the far end gets.
     *
     * Only a block of several commands is split and paced; a single line goes
     * out as typed, because that is how a password out of a manager, a token or
     * a path is pasted, and rewriting one of those would be its own bug.
     */
    fun submit(raw: String): Int {
        val text = sanitizePasted(raw)
        if (text.isEmpty()) return 0

        // A full-screen program — vim, less, an installer — is not a shell:
        // there are no commands to pace and no comments to drop, and its own
        // paste handling is what the bracketed markers are for.
        if (terminal.usingAltScreen) {
            sendRaw(text)
            return 1
        }

        if (!text.trimEnd('\n').contains('\n')) {
            sendRaw(text)
            return 1
        }

        val script = parsePaste(text)
        if (script.isEmpty) {
            onNotice("Nothing to run — the paste was all comments")
            return 0
        }
        if (script.droppedComments > 0 || script.droppedNoise > 0) {
            Log.info(
                SCOPE,
                "paste: ${script.commands.size} command(s), dropped " +
                    "${script.droppedComments} comment(s) and ${script.droppedNoise} blank line(s)",
            )
        }
        warnAboutLongLines(script)

        if (queue.isEmpty()) sentAny = false
        queue.addAll(script.commands)
        remaining = queue.size

        // One command has nothing to outrun, and holding it back would strand a
        // paste made at a prompt this queue cannot recognise.
        if (!sentAny && queue.size == 1 && readiness() != PasteWait.SECRET) {
            tick(force = true)
            return script.commands.size
        }
        startDriver()
        return script.commands.size
    }

    fun cancel() {
        val dropped = queue.size
        queue.clear()
        remaining = 0
        waiting = PasteWait.NONE
        job?.cancel()
        job = null
        if (dropped > 0) Log.info(SCOPE, "paste cancelled, $dropped command(s) dropped")
    }

    fun dispose() = cancel()

    /// One pass of the state machine. Public to the module so a test can drive
    /// the queue without a scheduler.
    internal fun tick(force: Boolean = false) {
        if (queue.isEmpty()) {
            waiting = PasteWait.NONE
            return
        }
        val hold = if (force) PasteWait.NONE else readiness()
        waiting = hold
        if (hold != PasteWait.NONE) return

        val command = queue.removeFirst()
        remaining = queue.size
        sentAny = true
        write(command)
        if (queue.isEmpty()) waiting = PasteWait.NONE
    }

    private fun startDriver() {
        val host = scope ?: return
        if (job?.isActive == true) return
        job = host.launch {
            while (isActive && queue.isNotEmpty()) {
                tick()
                delay(POLL_MILLIS)
            }
            waiting = PasteWait.NONE
        }
    }

    /**
     * Whether a shell is sitting at a prompt with nothing typed on the line.
     *
     * The order matters: silence first, because a screen mid-command says
     * nothing about what is reading; then the shell's own answer if it emits
     * the OSC 133 marks; then the shape of the line the cursor sits on.
     */
    private fun readiness(): PasteWait {
        if (terminal.usingAltScreen) return PasteWait.FULLSCREEN

        val revision = terminal.revision
        if (revision != lastRevision) {
            lastRevision = revision
            quietSince = now()
            sawDataSinceSend = true
        }
        // Nothing has come back since the last command went out. Either the far
        // end is still working or it is reading with echo off — a password
        // prompt that has not even printed. Neither takes a command.
        if (!sawDataSinceSend) return PasteWait.PROMPT
        if (now() - quietSince < QUIET_MILLIS) return PasteWait.PROMPT

        val cursor = terminal.cursorPoint()
        val line = terminal.logicalLineEnclosing(cursor.row) ?: return PasteWait.PROMPT
        val offset = (cursor.row - line.firstRow) * terminal.viewWidth + cursor.column
        val before = line.text.take(offset.toInt().coerceIn(0, line.text.length))

        if (SECRET_PROMPT.containsMatchIn(before.trimEnd())) return PasteWait.SECRET

        // A shell with OSC 133 integration says outright what it is doing.
        if (terminal.lastShellMark == 'C') return PasteWait.PROMPT
        val marked = terminal.lastShellMark == 'A' || terminal.lastShellMark == 'B'

        val shaped = marked || !sentAny || looksLikePrompt(before, lastPrompt())
        return if (shaped) PasteWait.NONE else PasteWait.PROMPT
    }

    /**
     * Sends one command, Enter and all — including the last one, whether or not
     * the clipboard ended with a newline. Selecting a block of commands is
     * asking for them to run, and leaving the last one sitting on the prompt
     * for an Enter nobody asked for reads as the paste having stalled.
     *
     * A single-line paste never comes through here: that one goes out exactly
     * as it was copied, so a password without a trailing newline still waits.
     */
    private fun write(command: String) {
        // The command and its Enter go in one write: the recorder reads what it
        // logs off the screen and takes the rest of a chunk from the keys
        // themselves, so splitting them here would put a blank line in the
        // history instead of the command.
        val text = command.replace('\n', '\r')
        // Marked before the write, not after: on a fast link the echo can be
        // back by the time sendKeys returns, and a mark taken afterwards would
        // swallow it and leave the queue waiting for an answer already given.
        markSent()
        terminal.sendKeys("$text\r")
    }

    /// Straight through, wrapped in the paste markers when the program on the
    /// far end asked for them (DECSET 2004) so it can tell the text from typing.
    private fun sendRaw(text: String) {
        val body = text.replace('\n', '\r')
        markSent()
        terminal.sendKeys(
            if (terminal.bracketedPaste) "\u001B[200~$body\u001B[201~" else body,
        )
    }

    private fun markSent() {
        lastRevision = terminal.revision
        quietSince = now()
        sawDataSinceSend = false
    }

    /// A terminal line discipline in canonical mode holds 4095 bytes and drops
    /// the rest of the line, so a very long command arrives truncated with no
    /// error anywhere. Better said out loud than found later in a broken file.
    private fun warnAboutLongLines(script: PasteScript) {
        val longest = script.commands
            .flatMap { it.split('\n') }
            .maxOfOrNull { it.length } ?: 0
        if (longest <= MAX_TTY_LINE) return
        Log.warn(SCOPE, "paste holds a ${longest}-character line; the remote tty may cut it")
        onNotice("A pasted line is over 4 KB — the remote shell may truncate it")
    }
}

private const val SCOPE = "paste"

private const val POLL_MILLIS = 40L

/// How long the screen must stay still before the line under the cursor is
/// read as a prompt rather than as output that happens to have paused.
private const val QUIET_MILLIS = 250L

private const val MAX_TTY_LINE = 4000

/// Prompts end in punctuation and a space; questions and password prompts end
/// in a colon or a bracket, which is what keeps this from matching them.
private val PROMPT_TAIL = Regex("[\\$#%>❯➜›][ \t]?$")

private val SECRET_PROMPT = Regex(
    "(password|passphrase|passcode|pin|token|secret|verification code)\\b[^:]{0,80}:$",
    RegexOption.IGNORE_CASE,
)

/**
 * Whether the text in front of the cursor reads as a prompt waiting for input.
 *
 * The learned prompt is the exact text the last command was typed behind. It is
 * matched from the front as well as the back, because a prompt carrying the
 * working directory changes with every `cd` — its tail moves, its head does not.
 */
private fun looksLikePrompt(before: String, learned: String?): Boolean {
    if (PROMPT_TAIL.containsMatchIn(before)) return true
    if (learned.isNullOrBlank()) return false
    if (before.endsWith(learned)) return true
    val head = learned.take(6)
    return head.isNotBlank() && before.startsWith(head) && before.endsWith(" ")
}
