package com.example.ohmyssh.data

import androidx.compose.runtime.mutableStateListOf
import com.example.ohmyssh.platform.FilePick
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

    /** Closed connections still in the recent list, the archive aside. */
    val past: List<ConnectionRecord> get() = connections.filter { !it.isLive && !it.archived }

    /** Closed connections the person opened themselves. */
    val clientPast: List<ConnectionRecord> get() = past.filter { !it.agent }

    /** Closed connections an agent opened, kept and capped apart from the above. */
    val agentPast: List<ConnectionRecord> get() = past.filter { it.agent }

    /** Connections put aside to keep, newest first. */
    val archived: List<ConnectionRecord> get() = connections.filter { it.archived }

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

        // Filtered on the way in, not only on the way out: a file written
        // before empty connections were dropped is full of them.
        connections.addAll(loaded.filter { it.worthKeeping }.sortedByDescending { it.startedAt })
        trim()
        Log.info("history", "${connections.size} past connections")
    }

    fun close() {
        persist()
        vault = null
        connections.clear()
    }

    fun wipe() {
        connections.clear()
        vault?.let { runCatching { it.deleteSidecar(kHistoryFileName) } }
        vault = null
    }

    suspend fun exportHistory(fileName: String = "ohmyssh"): String? {
        persist()
        val bytes = vault?.readSidecarFileBytes(kHistoryFileName) ?: return null
        return FilePick.saveFile(name = fileName, extension = "history", bytes = bytes)
    }

    suspend fun importHistory(fileText: String, password: String): Int {
        val clear = Vault.decryptSidecar(fileText, password, kHistoryFormat)
        val parsed = json.parseToJsonElement(clear.decodeToString()) as? JsonObject
            ?: throw VaultException("History file has an unexpected shape")
        val incoming = parsed.arr("connections")
            ?.filterIsInstance<JsonObject>()
            ?.map(ConnectionRecord::fromJson)
            .orEmpty()

        val known = connections.mapTo(HashSet()) { it.id }
        val fresh = incoming.filter { it.id !in known && it.worthKeeping }
        if (fresh.isEmpty()) return 0

        val merged = (connections.toList() + fresh).sortedByDescending { it.startedAt }
        connections.clear()
        connections.addAll(merged)
        trim()
        requestSave()
        return fresh.size
    }

    fun begin(
        sessionId: String,
        kind: ConnectionKind,
        label: String,
        target: String,
        username: String? = null,
        hostId: String? = null,
        agent: Boolean = false,
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
            agent = agent,
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
        // Dropped as the session lets go of it rather than at save time, so the
        // list never shows a row for a connection that recorded nothing.
        if (!record.worthKeeping) connections.remove(record)
        requestSave()
    }

    fun delete(record: ConnectionRecord) {
        connections.remove(record)
        requestSave()
    }

    fun delete(records: Collection<ConnectionRecord>) {
        connections.removeAll(records.toSet())
        requestSave()
    }

    fun archive(records: Collection<ConnectionRecord>, archived: Boolean = true) {
        for (record in records) record.archived = archived
        trim()
        requestSave()
    }

    /** Forgets the recent list. What is archived and what is still open stay. */
    fun clearAll() {
        connections.removeAll { !it.isLive && !it.archived }
        requestSave()
    }

    fun requestSave() {
        saves.trySend(Unit)
    }

    private fun trim() {
        trim(agent = false, keep = kMaxClientConnectionsKept)
        trim(agent = true, keep = kMaxAgentConnectionsKept)
    }

    private fun trim(agent: Boolean, keep: Int) {
        while (connections.count { it.agent == agent && !it.archived } > keep) {
            val oldest = connections.lastOrNull { it.agent == agent && !it.isLive && !it.archived } ?: return
            connections.remove(oldest)
        }
    }

    private fun persist() {
        val target = vault ?: return
        // Snapshot before encoding: these are Compose state lists and the UI
        // thread may be appending to them while this runs.
        val snapshot = connections.filter { it.worthKeeping }

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
