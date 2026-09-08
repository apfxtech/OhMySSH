package com.example.ohmyssh

import com.example.ohmyssh.data.ConnectionKind
import com.example.ohmyssh.data.ConnectionOutcome
import com.example.ohmyssh.data.ConnectionRecord
import com.example.ohmyssh.data.HistoryStore
import com.example.ohmyssh.data.LoggedCommand
import com.example.ohmyssh.data.Vault
import com.example.ohmyssh.data.VaultData
import com.example.ohmyssh.data.kHistoryFileName
import com.example.ohmyssh.data.kHistoryFormat
import com.example.ohmyssh.data.kMaxAgentConnectionsKept
import com.example.ohmyssh.data.kMaxClientConnectionsKept
import com.example.ohmyssh.data.kMaxCommandsPerConnection
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryTest {
    private lateinit var temp: File

    @BeforeTest
    fun setUp() {
        temp = File.createTempFile("ohmyssh_history_test", "").apply {
            delete()
            mkdirs()
        }
    }

    @AfterTest
    fun tearDown() {
        temp.deleteRecursively()
    }

    private fun vaultPath(): String = File(temp, "test.vault").path

    private fun historyFile(): File = File(temp, kHistoryFileName)

    private fun sampleRecord(): ConnectionRecord {
        val record = ConnectionRecord(
            id = "c1",
            kind = ConnectionKind.SSH,
            label = "web-01",
            target = "10.0.0.5:2222",
            startedAt = 1_700_000_000_000L,
            username = "root",
            hostId = "h1",
            osId = "ubuntu",
        )
        record.add(LoggedCommand(text = "systemctl restart nginx", at = 1_700_000_001_000L, cwd = "/etc"))
        record.add(
            LoggedCommand(
                text = "journalctl -u nginx -n 50",
                at = 1_700_000_050_000L,
                exitCode = 1,
                durationMs = 320,
            ),
        )
        record.endedAt = 1_700_000_100_000L
        record.outcome = ConnectionOutcome.DISCONNECTED
        return record
    }

    private fun encode(records: List<ConnectionRecord>): ByteArray =
        """{"connections":[${records.joinToString(",") { it.toJson().toString() }}]}"""
            .encodeToByteArray()

    @Test
    fun roundTripsARecordThroughTheSidecar() = runBlocking {
        val vault = Vault.create("master", vaultPath(), VaultData())
        vault.writeSidecar(kHistoryFileName, kHistoryFormat, encode(listOf(sampleRecord())))

        val raw = vault.readSidecar(kHistoryFileName, kHistoryFormat)!!
        val parsed = Json.parseToJsonElement(raw.decodeToString()) as JsonObject
        val records = (parsed["connections"] as kotlinx.serialization.json.JsonArray)
            .filterIsInstance<JsonObject>()
            .map(ConnectionRecord::fromJson)

        val record = records.single()
        assertEquals("web-01", record.label)
        assertEquals("10.0.0.5:2222", record.target)
        assertEquals("root", record.username)
        assertEquals("ubuntu", record.osId)
        assertEquals(ConnectionOutcome.DISCONNECTED, record.outcome)
        assertEquals(100_000L, record.durationMs)

        assertEquals(2, record.commands.size)
        assertEquals("systemctl restart nginx", record.commands[0].text)
        assertEquals("/etc", record.commands[0].cwd)
        assertNull(record.commands[0].exitCode)
        assertEquals(1, record.commands[1].exitCode)
        assertEquals(320L, record.commands[1].durationMs)
        assertTrue(record.commands[1].failed)
    }

    @Test
    fun theHistoryFileGivesNothingAwayInTheClear() = runBlocking {
        val vault = Vault.create("master", vaultPath(), VaultData())
        vault.writeSidecar(kHistoryFileName, kHistoryFormat, encode(listOf(sampleRecord())))

        val raw = historyFile().readText()
        assertFalse(raw.contains("systemctl"))
        assertFalse(raw.contains("web-01"))
        assertFalse(raw.contains("10.0.0.5"))
    }

    @Test
    fun itSitsBesideTheVaultRatherThanInsideIt() = runBlocking {
        val path = vaultPath()
        val vault = Vault.create("master", path, VaultData())
        vault.writeSidecar(kHistoryFileName, kHistoryFormat, encode(listOf(sampleRecord())))

        assertFalse(File(path).readText().contains(kHistoryFormat))
        assertTrue(historyFile().exists())
        assertEquals(0, vault.read().hosts.size)
    }

    @Test
    fun changingTheMasterPasswordCarriesTheHistoryAcross() = runBlocking {
        val path = vaultPath()
        val vault = Vault.create("old-password", path, VaultData())
        vault.writeSidecar(kHistoryFileName, kHistoryFormat, encode(listOf(sampleRecord())))

        vault.changePassword("new-password", listOf(kHistoryFileName to kHistoryFormat))

        val reopened = Vault.open("new-password", path)
        val raw = reopened.readSidecar(kHistoryFileName, kHistoryFormat)!!
        assertTrue(raw.decodeToString().contains("systemctl restart nginx"))
    }

    @Test
    fun aHistoryLeftBehindByTheOldPasswordIsNotReadable() = runBlocking {
        val path = vaultPath()
        val vault = Vault.create("old-password", path, VaultData())
        vault.writeSidecar(kHistoryFileName, kHistoryFormat, encode(listOf(sampleRecord())))

        vault.changePassword("new-password")

        val reopened = Vault.open("new-password", path)
        val failed = runCatching { reopened.readSidecar(kHistoryFileName, kHistoryFormat) }
        assertTrue(failed.isFailure)
    }

    @Test
    fun aSessionStillOpenWhenTheAppWentAwayReadsBackAsClosed() {
        val record = ConnectionRecord(
            id = "c2",
            kind = ConnectionKind.SSH,
            label = "db-01",
            target = "10.0.0.9",
            startedAt = 1L,
        )
        record.liveSessionId = "s1"
        assertTrue(record.isLive)
        assertEquals(ConnectionOutcome.OPEN, record.outcome)

        val reloaded = ConnectionRecord.fromJson(record.toJson())
        assertFalse(reloaded.isLive)
        assertEquals(ConnectionOutcome.DISCONNECTED, reloaded.outcome)
    }

    private fun freshStore(): HistoryStore {
        HistoryStore.close()
        return HistoryStore
    }

    @Test
    fun lockingTheVaultWritesWhatTheDebounceHasNotGotToYet() = runBlocking {
        val vault = Vault.create("master", vaultPath(), VaultData())
        val store = freshStore()
        store.open(vault)

        val record = store.begin("s1", ConnectionKind.SSH, "web-01", "10.0.0.5")
        record.add(LoggedCommand(text = "make deploy", at = 1L))
        store.release(record)

        store.close()

        val reopened = Vault.open("master", vaultPath())
        val raw = reopened.readSidecar(kHistoryFileName, kHistoryFormat)!!
        assertTrue(raw.decodeToString().contains("make deploy"))
    }

    @Test
    fun aRecordIsHistoryOnlyOnceItsSessionIsGone() {
        val store = freshStore()
        val record = store.begin(
            sessionId = "s1",
            kind = ConnectionKind.SSH,
            label = "web-01",
            target = "10.0.0.5",
        )
        record.add(LoggedCommand(text = "uptime", at = 1L))

        assertTrue(record.isLive)
        assertEquals(emptyList(), store.past)
        assertEquals(record, store.forSession("s1"))

        store.end(record, ConnectionOutcome.DISCONNECTED)
        assertTrue(record.isLive)
        assertFalse(record.isConnected)
        assertEquals(emptyList(), store.past)

        store.release(record)
        assertFalse(record.isLive)
        assertEquals(listOf(record), store.past)
        assertNull(store.forSession("s1"))
    }

    @Test
    fun aLaterCloseDoesNotPaperOverAFailure() {
        val store = freshStore()
        val record = store.begin("s1", ConnectionKind.SSH, "web-01", "10.0.0.5")

        store.end(record, ConnectionOutcome.FAILED, error = "Connection refused")
        store.release(record)

        assertEquals(ConnectionOutcome.FAILED, record.outcome)
        assertEquals("Connection refused", record.error)
    }

    @Test
    fun reopeningARecordClearsTheFailedAttemptOffIt() {
        val store = freshStore()
        val record = store.begin("s1", ConnectionKind.SSH, "web-01", "10.0.0.5")
        store.end(record, ConnectionOutcome.FAILED, error = "Timed out")

        store.reopen(record)

        assertTrue(record.isConnected)
        assertNull(record.error)
        assertNull(record.endedAt)
    }

    @Test
    fun clearingHistoryLeavesAnythingStillOpenAlone() {
        val store = freshStore()
        val open = store.begin("s1", ConnectionKind.SSH, "live", "10.0.0.1")
        val closed = store.begin("s2", ConnectionKind.SSH, "done", "10.0.0.2")
        closed.add(LoggedCommand(text = "uptime", at = 1L))
        store.release(closed)

        assertEquals(listOf(closed), store.past)

        store.clearAll()

        assertEquals(listOf(open), store.connections.toList())
        assertEquals(emptyList(), store.past)
    }

    @Test
    fun aConnectionThatRecordedNothingIsNotKept() {
        val store = freshStore()
        val record = store.begin("s1", ConnectionKind.SSH, "web-01", "10.0.0.5")

        store.end(record, ConnectionOutcome.DISCONNECTED)
        store.release(record)

        assertEquals(emptyList(), store.past)
        assertEquals(emptyList(), store.connections.toList())
    }

    @Test
    fun aFailedAttemptIsKeptThoughNothingRanOverIt() {
        val store = freshStore()
        val record = store.begin("s1", ConnectionKind.SSH, "web-01", "10.0.0.5")

        store.end(record, ConnectionOutcome.FAILED, error = "Connection refused")
        store.release(record)

        assertEquals(listOf(record), store.past)
    }

    @Test
    fun anEmptyConnectionNeverReachesTheFile() = runBlocking {
        val vault = Vault.create("master", vaultPath(), VaultData())
        val store = freshStore()
        store.open(vault)

        val quiet = store.begin("s1", ConnectionKind.SSH, "quiet-one", "10.0.0.5")
        val busy = store.begin("s2", ConnectionKind.SSH, "busy-one", "10.0.0.6")
        busy.add(LoggedCommand(text = "make deploy", at = 1L))
        store.release(quiet)
        store.release(busy)
        store.close()

        val reopened = Vault.open("master", vaultPath())
        val raw = reopened.readSidecar(kHistoryFileName, kHistoryFormat)!!.decodeToString()
        assertTrue(raw.contains("busy-one"))
        assertFalse(raw.contains("quiet-one"))
    }

    @Test
    fun whoOpenedTheConnectionSurvivesAReload() {
        val record = ConnectionRecord(
            id = "c4",
            kind = ConnectionKind.SSH,
            label = "web-01",
            target = "10.0.0.5",
            startedAt = 1L,
            agent = true,
        )

        assertTrue(ConnectionRecord.fromJson(record.toJson()).agent)
    }

    @Test
    fun anAgentsSessionsCannotPushOutTheOnesTheOwnerRan() {
        val store = freshStore()
        repeat(kMaxClientConnectionsKept + 10) { closed(store, "c$it", agent = false) }
        repeat(kMaxAgentConnectionsKept + 10) { closed(store, "a$it", agent = true) }

        assertEquals(kMaxClientConnectionsKept, store.clientPast.size)
        assertEquals(kMaxAgentConnectionsKept, store.agentPast.size)
        assertEquals("c${kMaxClientConnectionsKept + 9}", store.clientPast.first().label)
        assertEquals("a${kMaxAgentConnectionsKept + 9}", store.agentPast.first().label)
    }

    @Test
    fun anArchivedConnectionOutlivesTheCapAndTheClear() {
        val store = freshStore()
        closed(store, "keeper", agent = false)
        val keeper = store.past.single()
        store.archive(listOf(keeper))
        repeat(kMaxClientConnectionsKept + 10) { closed(store, "c$it", agent = false) }

        assertTrue(keeper in store.connections)
        assertEquals(listOf(keeper), store.archived)
        assertFalse(keeper in store.past)

        store.clearAll()

        assertEquals(listOf(keeper), store.connections.toList())
        assertTrue(ConnectionRecord.fromJson(keeper.toJson()).archived)
    }

    @Test
    fun unarchivingPutsAConnectionBackInTheRecentList() {
        val store = freshStore()
        closed(store, "keeper", agent = false)
        val keeper = store.past.single()
        store.archive(listOf(keeper))
        store.archive(listOf(keeper), archived = false)

        assertEquals(listOf(keeper), store.past)
        assertEquals(emptyList(), store.archived)
    }

    private fun closed(store: HistoryStore, label: String, agent: Boolean) {
        val record = store.begin(
            sessionId = label,
            kind = ConnectionKind.SSH,
            label = label,
            target = "10.0.0.1",
            agent = agent,
        )
        record.add(LoggedCommand(text = "uptime", at = 0L))
        store.release(record)
    }

    @Test
    fun aRunawaySessionDropsItsOldestCommandsRatherThanGrowingForever() {
        val record = ConnectionRecord(
            id = "c3",
            kind = ConnectionKind.SSH,
            label = "busy",
            target = "host",
            startedAt = 0L,
        )
        repeat(kMaxCommandsPerConnection + 25) {
            record.add(LoggedCommand(text = "command $it", at = it.toLong()))
        }

        assertEquals(kMaxCommandsPerConnection, record.commands.size)
        assertEquals(25, record.droppedCommands)
        assertEquals("command 25", record.commands.first().text)
    }
}
