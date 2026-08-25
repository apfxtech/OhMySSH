package com.example.ohmyssh

import com.example.ohmyssh.data.LoggedCommand
import com.example.ohmyssh.session.CommandRecorder
import com.example.ohmyssh.terminal.TerminalEmulator

/**
 * A shell on the far end of the terminal: it echoes what is typed the way a
 * line discipline does, prints a prompt when asked, and can be told to stop
 * echoing, which is what a password prompt looks like from this side.
 */
internal class FakeShell(cols: Int = 40, rows: Int = 8, val prompt: String = "user@host:~$ ") {
    var clock = 1_700_000_000_000L
    var echo = true

    val terminal = TerminalEmulator(cols = cols, rows = rows)

    /// Everything the client has written to the far end, in order.
    val wire = mutableListOf<String>()

    val captured = mutableListOf<LoggedCommand>()

    val recorder = CommandRecorder(terminal) { clock }

    init {
        recorder.onCommand = { captured.add(it) }
        recorder.attach()
        terminal.onOutput = { data ->
            wire += data
            if (echo) echoBack(data)
        }
    }

    private fun echoBack(data: String) {
        var inEscape = false
        for (ch in data) {
            if (inEscape) {
                if (ch.isLetter() || ch == '~') inEscape = false
                continue
            }
            when {
                ch == '\u001B' -> inEscape = true
                ch == '\r' || ch == '\n' -> terminal.write("\r\n")
                ch == '\u007F' -> terminal.write("\b \b")
                ch.code >= 0x20 -> terminal.write(ch.toString())
                else -> {}
            }
        }
    }

    fun prompt() = terminal.write(prompt)

    fun output(text: String) = terminal.write(text)

    fun type(text: String) {
        for (ch in text) terminal.sendKeys(ch.toString())
    }

    fun enter() = terminal.sendKeys("\r")

    fun run(command: String) {
        prompt()
        type(command)
        enter()
    }

    val texts: List<String> get() = captured.map { it.text }
}
