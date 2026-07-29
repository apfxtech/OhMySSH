package com.example.ohmyssh.serial

import androidx.compose.runtime.mutableStateMapOf
import com.example.ohmyssh.data.SerialDevice
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.session.Checkpoint
import com.example.ohmyssh.session.SessionState
import com.example.ohmyssh.session.StageStatus
import com.example.ohmyssh.session.TerminalSession
import com.example.ohmyssh.terminal.Utf8StreamDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

enum class SerialStage(val label: String) {
    OPEN("Opening port"),
    CONFIGURE("Applying line settings"),
    ATTACH("Attaching console"),
}

class SerialSession(
    device: SerialDevice,
    port: SerialPortInfo,
) : TerminalSession(device.id) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var device: SerialDevice = device
        private set

    var port: SerialPortInfo = port
        private set

    private var link: SerialLink? = null
    private var reader: Job? = null

    private val stageStatus = mutableStateMapOf<SerialStage, Checkpoint>().apply {
        for (stage in SerialStage.entries) put(stage, Checkpoint(stage.label, StageStatus.WAITING))
    }

    /// Android renumbers a device on every attach, so a reconnect has to ask
    /// detection for the current handle before it can open anything.
    var onResolvePort: (suspend () -> SerialPortInfo?)? = null

    var onSettingsChanged: (suspend (device: SerialDevice) -> Unit)? = null

    override val checkpoints: List<Checkpoint>
        get() = SerialStage.entries.map { stageStatus.getValue(it) }

    override val title: String get() = device.displayLabel

    override val subtitle: String
        get() = when (state) {
            SessionState.CONNECTING -> "Opening…"
            SessionState.FAILED -> "Failed"
            SessionState.CLOSED -> "Closed"
            else -> "${serialPortName(device.path)} · ${device.lineSettings}"
        }

    private fun mark(stage: SerialStage, status: StageStatus, detail: String? = null) {
        stageStatus[stage] = Checkpoint(stage.label, status, detail)
    }

    override suspend fun connect() {
        if (state == SessionState.CONNECTING || state == SessionState.CONNECTED) return
        state = SessionState.CONNECTING
        error = null
        for (stage in SerialStage.entries) {
            stageStatus[stage] = Checkpoint(stage.label, StageStatus.WAITING)
        }

        try {
            mark(SerialStage.OPEN, StageStatus.RUNNING, device.path)
            onResolvePort?.invoke()?.let { port = it }

            val opened = openSerialLink(device = device, port = port)
            link = opened
            mark(SerialStage.OPEN, StageStatus.DONE, device.path)

            mark(
                SerialStage.CONFIGURE,
                StageStatus.DONE,
                "${device.lineSettings} · flow ${device.flowControl.label}",
            )

            mark(SerialStage.ATTACH, StageStatus.RUNNING)
            terminal.onOutput = { data -> opened.write(data.encodeToByteArray()) }

            val decoder = Utf8StreamDecoder()
            reader = scope.launch {
                opened.input
                    .catch { cause -> fail(cause) }
                    .onCompletion { cause -> if (cause == null) handleClosed() }
                    .collect { bytes -> terminal.write(decoder.decode(bytes)) }
            }
            mark(SerialStage.ATTACH, StageStatus.DONE)

            state = SessionState.CONNECTED
        } catch (error: Exception) {
            Log.error("serial:${device.path}", "open failed: $error", error)
            this.error = error.message ?: "$error"
            state = SessionState.FAILED
            for (stage in SerialStage.entries) {
                if (stageStatus.getValue(stage).status == StageStatus.RUNNING) {
                    stageStatus[stage] = Checkpoint(
                        stage.label,
                        StageStatus.FAILED,
                        error.message ?: "$error",
                    )
                }
            }
            teardown()
        }
    }

    suspend fun applySettings(device: SerialDevice) {
        val wasConnected = state == SessionState.CONNECTED
        this.device = device
        onSettingsChanged?.invoke(device)
        if (!wasConnected) return
        disconnect()
        connect()
    }

    private suspend fun fail(cause: Throwable) {
        Log.warn("serial:${device.path}", "read failed: $cause")
        error = cause.message ?: "$cause"
        state = SessionState.FAILED
        teardown()
    }

    private suspend fun handleClosed() {
        if (state == SessionState.CLOSED || state == SessionState.FAILED) return
        state = SessionState.CLOSED
        teardown()
    }

    private suspend fun teardown() {
        terminal.onOutput = null
        reader?.cancel()
        reader = null
        runCatching { link?.close() }
        link = null
    }

    override suspend fun disconnect() {
        teardown()
        if (state != SessionState.FAILED) state = SessionState.CLOSED
    }

    override fun dispose() {
        terminal.onOutput = null
        scope.cancel()
    }
}
