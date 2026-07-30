package com.example.ohmyssh.data

import androidx.compose.runtime.mutableStateListOf
import com.example.ohmyssh.platform.epochMillis
import com.example.ohmyssh.services.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val kHistoryFileName = "ohmyssh.history"
const val kHistoryFormat = "ohmyssh.history"

private const val kSaveDebounceMs = 1500L

private val json = Json { ignoreUnknownKeys = true }

object HistoryStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val saves = Channel<Unit>(Channel.CONFLATED)

    private var vault: Vault? = null

    val connections = mutableStateListOf<ConnectionRecord>()

    init {
        scope.launch {
            for (ignored in saves) {
                delay(kSaveDebounceMs)
                persist()
            }
        }
    }

    fun byId(id: String): ConnectionRecord? = connections.firstOrNull { it.id == id }

    fun forSession(sessionId: String): ConnectionRecord? =
        connections.firstOrNull { it.liveSessionId == sessionId }

    val past: List<ConnectionRecord> get() = connections.filter { !it.isLive }

    fun open(vault: Vault) {
        this.vault = vault
        connections.clear()

        val raw = try {
            vault.readSidecar(kHistoryFileName, kHistoryFormat)
        } catch (failure: Exception) {
            // A corrupt or half-written history must not keep the app locked
            // out of its own vault.
            Log.warn("history", "unreadable, starting empty: $failure")
            null
        } ?: return

        val loaded = try {
            val parsed = json.parseToJsonElement(raw.decodeToString()) as? JsonObject
            parsed?.arr("connections")
                ?.filterIsInstance<JsonObject>()
                ?.map(ConnectionRecord::fromJson)
                .orEmpty()
        } catch (failure: Exception) {
            Log.warn("history", "malformed, starting empty: $failure")
            emptyList()
        }

        connections.addAll(loaded.sortedByDescending { it.startedAt })
        Log.info("history", "${connections.size} past connections")
    }

    fun close() {
        persist()
        vault = null
        connections.clear()
    }

    fun begin(
        sessionId: String,
        kind: ConnectionKind,
        label: String,
        target: String,
        username: String? = null,
        hostId: String? = null,
        osId: String? = null,
    ): ConnectionRecord {
        val record = ConnectionRecord(
            id = newId(),
            kind = kind,
            label = label,
            target = target,
            startedAt = epochMillis(),
            username = username,
            hostId = hostId,
            osId = osId,
        )
        record.liveSessionId = sessionId
        connections.add(0, record)
        trim()
        requestSave()
        return record
    }

    fun end(record: ConnectionRecord, outcome: ConnectionOutcome, error: String? = null) {
        if (record.endedAt == null) record.endedAt = epochMillis()
        if (record.outcome != ConnectionOutcome.FAILED) record.outcome = outcome
        if (error != null) record.error = error
        requestSave()
    }

    fun reopen(record: ConnectionRecord) {
        record.endedAt = null
        record.outcome = ConnectionOutcome.OPEN
        record.error = null
        requestSave()
    }

    fun release(record: ConnectionRecord) {
        record.liveSessionId = null
        if (record.endedAt == null) {
            record.endedAt = epochMillis()
            record.outcome = ConnectionOutcome.DISCONNECTED
        }
        requestSave()
    }

    fun delete(record: ConnectionRecord) {
        connections.remove(record)
        requestSave()
    }

    fun clearAll() {
        connections.removeAll { !it.isLive }
        requestSave()
    }

    fun requestSave() {
        saves.trySend(Unit)
    }

    private fun trim() {
        while (connections.size > kMaxConnectionsKept) {
            val oldest = connections.lastOrNull { !it.isLive } ?: return
            connections.remove(oldest)
        }
    }

    private fun persist() {
        val target = vault ?: return
        // Snapshot before encoding: these are Compose state lists and the UI
        // thread may be appending to them while this runs.
        val snapshot = connections.toList()

        if (snapshot.isEmpty()) {
            runCatching { target.deleteSidecar(kHistoryFileName) }
                .onFailure { Log.error("history", "could not delete: $it") }
            return
        }

        val payload = buildJsonObject {
            put("connections", JsonArray(snapshot.map { it.toJson() }))
        }

        runCatching {
            target.writeSidecar(
                fileName = kHistoryFileName,
                format = kHistoryFormat,
                clear = json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray(),
            )
        }.onFailure { Log.error("history", "save failed: $it") }
    }
}
