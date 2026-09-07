package com.example.ohmyssh.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ohmyssh.data.ConnectionKind
import com.example.ohmyssh.data.ConnectionOutcome
import com.example.ohmyssh.data.ConnectionRecord
import com.example.ohmyssh.data.HistoryStore
import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.net.NetworkWatcher
import com.example.ohmyssh.serial.SerialDeviceEntry
import com.example.ohmyssh.serial.SerialRegistry
import com.example.ohmyssh.serial.SerialSession
import com.example.ohmyssh.serial.serialPortName
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.ssh.HostSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SessionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessions = mutableStateListOf<TerminalSession>()

    var activeId: String? by mutableStateOf(null)
        private set

    var onSessionsChanged: (() -> Unit)? = null

    val hasLiveSessions: Boolean
        get() = sessions.any { it.state == SessionState.CONNECTED }

    val active: TerminalSession?
        get() = activeId?.let { id -> sessions.firstOrNull { it.id == id } }

    fun byId(id: String): TerminalSession? = sessions.firstOrNull { it.id == id }

    /**
     * What a session id used to be, for a while after it is gone.
     *
     * An agent holding an id from a session that has since closed would
     * otherwise be answered as if it had named a machine that does not exist,
     * which reads as "your access was revoked" and gets reported as one.
     */
    private var retired: Map<String, String> = emptyMap()

    fun retiredLabel(id: String): String? = retired[id]

    /**
     * A session already open on this host, for callers that must not dial a
     * second one. A serial port takes one connection, so openSerial reuses; SSH
     * takes many, and a person opening the same system twice wants two shells.
     */
    fun liveFor(host: Host): HostSession? =
        sessions.filterIsInstance<HostSession>().firstOrNull { it.host.id == host.id }

    fun open(host: Host): HostSession {
        val session = HostSession(host = host, identity = VaultStore.identityFor(host))

        session.onHostKeyPinned = { fingerprint ->
            currentHost(host.id)?.let { VaultStore.saveHost(it.copy(knownHostKey = fingerprint)) }
        }

        val record = beginRecord(session)

        session.onProfiled = { profile ->
            record.osId = profile.osId
            currentHost(host.id)?.let {
                VaultStore.saveHost(it.copy(osId = profile.osId, osPretty = profile.osPretty))
            }
        }

        Log.info("sessions", "opening ${host.endpoint}")
        return adopt(session, record)
    }

    fun openSerial(entry: SerialDeviceEntry): SerialSession {
        val existing = byId(entry.device.id)
        if (existing is SerialSession) {
            activate(existing.id)
            if (existing.state == SessionState.CLOSED || existing.state == SessionState.FAILED) {
                scope.launch { reconnect(existing) }
            }
            return existing
        }

        val session = SerialSession(device = entry.device, port = entry.port)
        session.onResolvePort = {
            SerialRegistry.refresh()
            SerialRegistry.entryForDevice(entry.device.id)?.port
        }
        session.onSettingsChanged = { device -> VaultStore.saveSerialDevice(device) }

        if (!entry.saved) {
            scope.launch { VaultStore.saveSerialDevice(entry.device) }
        }

        Log.info("sessions", "opening serial ${entry.device.path}")
        return adopt(session, beginRecord(session))
    }

    private fun <T : TerminalSession> adopt(session: T, record: ConnectionRecord): T {
        follow(session, record)
        session.commands.attach()

        sessions.add(session)
        activeId = session.id
        onSessionsChanged?.invoke()

        scope.launch { session.connect() }
        return session
    }

    private fun follow(session: TerminalSession, record: ConnectionRecord) {
        session.commands.onCommand = { command ->
            record.add(command)
            HistoryStore.requestSave()
        }

        session.onStateChanged = { state ->
            when (state) {
                SessionState.CONNECTED -> scope.launch { noteNetwork(session, record) }
                SessionState.FAILED -> HistoryStore.end(
                    record,
                    ConnectionOutcome.FAILED,
                    error = session.error?.toString(),
                )
                SessionState.CLOSED -> HistoryStore.end(record, ConnectionOutcome.DISCONNECTED)
                else -> {}
            }
        }
    }

    /**
     * Files the connection under the network it was made on, and files the
     * network under the system. Done on connect rather than on open: a system
     * that would not answer says nothing about where it can be reached from.
     */
    private suspend fun noteNetwork(session: TerminalSession, record: ConnectionRecord) {
        if (session !is HostSession) return
        try {
            val tag = NetworkWatcher.currentTag() ?: return

            record.networkId = tag.id
            record.networkLabel = tag.name
            HistoryStore.requestSave()
            VaultStore.rememberNetwork(session.host.id, tag.id)
        } catch (failure: Exception) {
            // Which Wi-Fi this was is a nicety. A vault locked between the
            // connect and the write must not take the session down with it.
            Log.warn("sessions", "network not recorded: $failure")
        }
    }

    private fun beginRecord(session: TerminalSession): ConnectionRecord = when (session) {
        is HostSession -> HistoryStore.begin(
            sessionId = session.id,
            kind = ConnectionKind.SSH,
            label = session.host.displayLabel,
            target = session.host.endpoint,
            username = session.identity?.username,
            hostId = session.host.id,
            osId = session.profile?.osId ?: session.host.osId,
        )
        is SerialSession -> HistoryStore.begin(
            sessionId = session.id,
            kind = ConnectionKind.SERIAL,
            label = session.device.displayLabel,
            target = "${serialPortName(session.device.path)} · ${session.device.lineSettings}",
        )
        else -> HistoryStore.begin(
            sessionId = session.id,
            kind = ConnectionKind.SSH,
            label = session.title,
            target = session.subtitle,
        )
    }

    suspend fun reconnect(session: TerminalSession) {
        val previous = HistoryStore.forSession(session.id)
        if (previous != null && previous.commands.isEmpty()) {
            HistoryStore.reopen(previous)
        } else {
            previous?.let { HistoryStore.release(it) }
            follow(session, beginRecord(session))
        }
        session.connect()
    }

    private fun currentHost(id: String): Host? = VaultStore.hosts.firstOrNull { it.id == id }

    fun activate(id: String) {
        if (activeId == id) return
        activeId = id
    }

    suspend fun close(id: String) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return
        val session = sessions.removeAt(index)
        Workspace.dropSession(id)

        if (activeId == id) {
            activeId = sessions.getOrNull(index.coerceAtMost(sessions.size - 1))?.id
        }
        onSessionsChanged?.invoke()

        retire(session)
    }

    suspend fun closeAll() {
        Workspace.reset()
        val open = sessions.toList()
        sessions.clear()
        activeId = null
        onSessionsChanged?.invoke()
        for (session in open) retire(session)
    }

    private suspend fun retire(session: TerminalSession) {
        // Replaced rather than mutated: a tool call reads this map on its own
        // thread while a session is being closed on this one.
        val remembered = retired + (session.id to session.title)
        retired = if (remembered.size <= 32) {
            remembered
        } else {
            remembered.entries.drop(remembered.size - 32).associate { it.toPair() }
        }

        session.disconnect()
        session.dispose()
        session.commands.detach()
        HistoryStore.forSession(session.id)?.let { HistoryStore.release(it) }
    }

    internal fun notifyChanged() {
        onSessionsChanged?.invoke()
    }
}
