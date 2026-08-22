package com.example.ohmyssh.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ohmyssh.terminal.TerminalEmulator

enum class SessionState { IDLE, CONNECTING, CONNECTED, FAILED, CLOSED }

enum class StageStatus { WAITING, RUNNING, DONE, FAILED, SKIPPED }

data class Checkpoint(
    val label: String,
    val status: StageStatus,
    val detail: String? = null,
)

abstract class TerminalSession(val id: String) {
    val terminal = TerminalEmulator(maxScrollback = 10000)

    val commands = CommandRecorder(terminal)

    abstract val title: String

    abstract val subtitle: String

    private var stateValue: SessionState by mutableStateOf(SessionState.IDLE)

    var state: SessionState
        get() = stateValue
        protected set(value) {
            if (stateValue == value) return
            stateValue = value
            onStateChanged?.invoke(value)
        }

    var onStateChanged: ((SessionState) -> Unit)? = null

    var error: Any? by mutableStateOf(null)
        protected set

    /**
     * Whether an agent opened this session, which makes it read-only to the
     * person at the keyboard.
     *
     * Two writers on one PTY interleave into a line neither of them meant to
     * run, so a session an agent is driving takes no typing and no paste.
     * Selection and copy stay live, and a human who wants a shell of their own
     * on the same host opens one.
     */
    var agentOwned: Boolean = false
        internal set

    abstract val checkpoints: List<Checkpoint>

    val isConnected: Boolean get() = state == SessionState.CONNECTED

    val statusLabel: String
        get() = when (state) {
            SessionState.IDLE -> "Idle"
            SessionState.CONNECTING -> "Connecting…"
            SessionState.CONNECTED -> "Connected"
            SessionState.FAILED -> "Failed"
            SessionState.CLOSED -> "Disconnected"
        }

    abstract suspend fun connect()

    abstract suspend fun disconnect()

    open fun dispose() {}
}

class SessionError(override val message: String) : Exception(message) {
    override fun toString(): String = message
}
