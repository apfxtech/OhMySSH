package com.example.ohmyssh.session

import com.example.ohmyssh.data.LoggedCommand
import com.example.ohmyssh.data.kMaxCommandLength
import com.example.ohmyssh.platform.epochMillis
import com.example.ohmyssh.terminal.LogicalLine
import com.example.ohmyssh.terminal.ScreenPoint
import com.example.ohmyssh.terminal.ShellSignal
import com.example.ohmyssh.terminal.TerminalEmulator

/**
 * Watches one terminal and reports the commands the user submits.
 *
 * The text is read off the *screen*, not off the keystrokes: when Enter is
 * pressed the row under the cursor already holds exactly what the shell will
 * run, with line editing, history recall, completion and pastes all resolved
 * there by the remote.
 *
 * Anything the shell chose not to echo — a sudo password, an SSH passphrase, a
 * `read -s` answer — leaves the row blank after the prompt, and a blank row is
 * never recorded. The line is anchored by position *and* by the prompt text
 * before it; once the screen stops matching the anchor the capture is abandoned
 * rather than guessed at.
 *
 * A shell emitting the OSC 133 prompt marks short-circuits all of it and hands
 * over the prompt boundary, the exit code and the duration directly.
 */
class CommandRecorder(
    private val terminal: TerminalEmulator,
    private val now: () -> Long = ::epochMillis,
) {
    var onCommand: ((LoggedCommand) -> Unit)? = null

    private class Anchor(val row: Long, val column: Int, val prompt: String)

    private var anchor: Anchor? = null

    /// Whether the current line has been anchored yet. Kept apart from
    /// [anchor] being null: a line that failed to anchor must not be anchored
    /// again halfway through, which would fold what was already typed into the
    /// prompt.
    private var lineAnchored = false

    private var cwd: String? = null

    private var running: LoggedCommand? = null
    private var runningSince = 0L

    private var submitted = false

    fun attach() {
        terminal.onInput = ::onKeys
        terminal.onShellSignal = ::onShellSignal
    }

    fun detach() {
        terminal.onInput = null
        terminal.onShellSignal = null
        resetLine()
        running = null
    }

    /// Records a command that never touched the terminal — one an agent ran on
    /// its own exec channel. Without this the agent's connection shows up in the
    /// history with no commands in it at all.
    fun note(text: String, exitCode: Int? = null, durationMs: Long? = null) {
        val clean = sanitize(text)
        if (clean.isEmpty()) return
        val command = LoggedCommand(
            text = clean.take(kMaxCommandLength),
            at = now(),
            cwd = cwd,
            exitCode = exitCode,
            durationMs = durationMs,
        )
        onCommand?.invoke(command)
    }

    private fun onKeys(data: String) {
        if (terminal.usingAltScreen) {
            resetLine()
            return
        }

        // Anchor before the key reaches the host: right now the cursor sits
        // where the prompt left it, and nothing typed has been echoed back yet.
        if (!lineAnchored) {
            anchor = anchorAt(terminal.cursorPoint())
            lineAnchored = true
        }

        var index = 0
        // The first line of a chunk can still be read off the screen. Anything
        // past a submit inside the same chunk cannot — the host has not echoed
        // it yet — so the remaining lines of a multi-line paste are taken from
        // the paste itself.
        var readScreen = true

        while (index < data.length) {
            var cut = index
            while (cut < data.length && data[cut] != '\r' && data[cut] != '\n') cut++
            if (cut >= data.length) break

            val typed = data.substring(index, cut)
            submit(if (readScreen) screenInput().orEmpty() + typed else typed)

            index = cut + 1
            // A pasted CRLF is one submit, not two.
            if (data[cut] == '\r' && data.getOrNull(index) == '\n') index++
            readScreen = false
        }
    }

    private fun onShellSignal(event: ShellSignal) {
        when (event) {
            is ShellSignal.PromptStart -> resetLine()

            is ShellSignal.InputStart -> {
                anchor = anchorAt(terminal.cursorPoint())
                lineAnchored = true
                submitted = false
            }

            is ShellSignal.Executing -> {
                if (!submitted) {
                    event.command?.trim()?.takeIf { it.isNotEmpty() }?.let(::record)
                }
            }

            is ShellSignal.Finished -> {
                finish(event.exitCode)
                submitted = false
            }

            is ShellSignal.WorkingDirectory -> cwd = event.path
        }
    }

    private fun anchorAt(point: ScreenPoint): Anchor? {
        val line = terminal.logicalLineAt(point.row) ?: return null
        if (point.column > line.text.length) return null
        return Anchor(point.row, point.column, line.text.take(point.column))
    }

    private fun screenInput(): String? {
        val at = anchor ?: return null
        val cursor = terminal.cursorPoint()

        val line = terminal.logicalLineAt(at.row)
        if (line != null && matches(line, cursor, at)) {
            return line.text.substring(at.prompt.length)
        }

        // The prompt moved: Ctrl+L redrew the screen, or Ctrl+C abandoned the
        // line and the shell printed a fresh prompt lower down. The column is
        // stale but the prompt text is not, so the line can be found again
        // wherever it went. A prompt with nothing in it would match anything,
        // so that case gives up instead.
        if (at.prompt.isBlank()) return null
        val redrawn = terminal.logicalLineAt(cursor.row) ?: return null
        if (!redrawn.text.startsWith(at.prompt)) return null
        return redrawn.text.substring(at.prompt.length)
    }

    private fun matches(line: LogicalLine, cursor: ScreenPoint, at: Anchor): Boolean =
        cursor.row in line && line.text.startsWith(at.prompt)

    private fun submit(raw: String) {
        resetLine()

        // A blank line after the prompt means either that nothing was typed, or
        // that the shell chose not to echo it — a password, a passphrase, a
        // `read -s` answer. Neither is a command, and the second must never be
        // written down.
        val text = sanitize(raw)
        if (text.isEmpty()) return
        record(text)
    }

    private fun record(text: String) {
        val at = now()
        val command = LoggedCommand(
            text = text.take(kMaxCommandLength),
            at = at,
            cwd = cwd,
        )
        running = command
        runningSince = at
        submitted = true
        onCommand?.invoke(command)
    }

    private fun finish(exitCode: Int?) {
        val command = running ?: return
        running = null
        command.exitCode = exitCode
        command.durationMs = (now() - runningSince).coerceAtLeast(0)
    }

    private fun resetLine() {
        anchor = null
        lineAnchored = false
    }

    private fun sanitize(raw: String): String =
        raw.filter { it.code >= 0x20 && it.code != 0x7F }.trim()
}
