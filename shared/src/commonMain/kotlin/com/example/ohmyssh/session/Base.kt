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

    abstract val title: String

    abstract val subtitle: String

    var state: SessionState by mutableStateOf(SessionState.IDLE)
        protected set

    var error: Any? by mutableStateOf(null)
        protected set

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
