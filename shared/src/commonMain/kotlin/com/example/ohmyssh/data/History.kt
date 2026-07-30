package com.example.ohmyssh.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val kMaxCommandLength = 2000

const val kMaxCommandsPerConnection = 2000

const val kMaxConnectionsKept = 300

enum class ConnectionKind {
    SSH,
    SERIAL;

    val wireName: String get() = name.lowercase()

    companion object {
        fun parse(raw: String?): ConnectionKind =
            entries.firstOrNull { it.wireName == raw } ?: SSH
    }
}

enum class ConnectionOutcome {
    OPEN,
    DISCONNECTED,
    FAILED;

    val wireName: String get() = name.lowercase()

    val label: String
        get() = when (this) {
            OPEN -> "Open"
            DISCONNECTED -> "Closed"
            FAILED -> "Failed"
        }

    companion object {
        fun parse(raw: String?): ConnectionOutcome =
            entries.firstOrNull { it.wireName == raw } ?: OPEN
    }
}

class LoggedCommand(
    val text: String,
    val at: Long,
    val cwd: String? = null,
    exitCode: Int? = null,
    durationMs: Long? = null,
) {
    var exitCode: Int? by mutableStateOf(exitCode)
        internal set

    var durationMs: Long? by mutableStateOf(durationMs)
        internal set

    val failed: Boolean get() = (exitCode ?: 0) != 0

    fun toJson(): JsonObject = buildJsonObject {
        put("text", text)
        put("at", at)
        cwd?.let { put("cwd", it) }
        exitCode?.let { put("exit", it) }
        durationMs?.let { put("ms", it) }
    }

    companion object {
        fun fromJson(json: JsonObject): LoggedCommand = LoggedCommand(
            text = json.str("text") ?: "",
            at = json.long("at") ?: 0L,
            cwd = json.str("cwd"),
            exitCode = json.int("exit"),
            durationMs = json.long("ms"),
        )
    }
}

class ConnectionRecord(
    val id: String,
    val kind: ConnectionKind,
    val label: String,
    val target: String,
    val startedAt: Long,
    val username: String? = null,
    val hostId: String? = null,
    osId: String? = null,
) {
    var osId: String? by mutableStateOf(osId)
        internal set

    var endedAt: Long? by mutableStateOf(null)
        internal set

    var outcome: ConnectionOutcome by mutableStateOf(ConnectionOutcome.OPEN)
        internal set

    var error: String? by mutableStateOf(null)
        internal set

    var liveSessionId: String? by mutableStateOf(null)
        internal set

    val commands = mutableStateListOf<LoggedCommand>()

    var droppedCommands: Int by mutableStateOf(0)
        internal set

    val isLive: Boolean get() = liveSessionId != null

    val isConnected: Boolean get() = outcome == ConnectionOutcome.OPEN

    val durationMs: Long? get() = endedAt?.let { it - startedAt }

    fun add(command: LoggedCommand) {
        commands.add(command)
        while (commands.size > kMaxCommandsPerConnection) {
            commands.removeAt(0)
            droppedCommands++
        }
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("kind", kind.wireName)
        put("label", label)
        put("target", target)
        put("startedAt", startedAt)
        username?.let { put("username", it) }
        hostId?.let { put("hostId", it) }
        osId?.let { put("osId", it) }
        endedAt?.let { put("endedAt", it) }
        put("outcome", outcome.wireName)
        error?.let { put("error", it) }
        if (droppedCommands > 0) put("dropped", droppedCommands)
        // Snapshot first: the recorder appends to this while a save runs.
        put("commands", JsonArray(commands.toList().map { it.toJson() }))
    }

    companion object {
        fun fromJson(json: JsonObject): ConnectionRecord {
            val record = ConnectionRecord(
                id = json.str("id") ?: newId(),
                kind = ConnectionKind.parse(json.str("kind")),
                label = json.str("label") ?: "",
                target = json.str("target") ?: "",
                startedAt = json.long("startedAt") ?: 0L,
                username = json.str("username"),
                hostId = json.str("hostId"),
                osId = json.str("osId"),
            )
            record.endedAt = json.long("endedAt")
            // A session that was live when the app went away never got its
            // ending written. It is not open now, whatever the file says.
            record.outcome = ConnectionOutcome.parse(json.str("outcome"))
                .let { if (it == ConnectionOutcome.OPEN) ConnectionOutcome.DISCONNECTED else it }
            record.error = json.str("error")
            record.droppedCommands = json.int("dropped") ?: 0
            json.arr("commands")?.filterIsInstance<JsonObject>()?.forEach {
                record.commands.add(LoggedCommand.fromJson(it))
            }
            return record
        }
    }
}
