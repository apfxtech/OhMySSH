package com.example.ohmyssh.ai

import com.example.ohmyssh.data.Host
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.session.SessionState
import com.example.ohmyssh.session.TerminalSession
import com.example.ohmyssh.ssh.HostSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class TargetError(override val message: String) : Exception(message) {
    override fun toString(): String = message
}

/**
 * Turns whatever a caller names a machine into a session.
 *
 * A model has no reason to know whether "web-1" is an open session, a saved
 * system or a hostname, so every tool takes one loose string and it is resolved
 * here: live session first, saved system second. Credentials never leave this
 * file — they are read out of the vault locally and never travel in arguments.
 */
object Targets {

    fun findSession(target: String): TerminalSession? {
        val wanted = target.trim()
        if (wanted.isEmpty()) return null

        SessionManager.byId(wanted)?.let { return it }

        return SessionManager.sessions.firstOrNull { session ->
            session is HostSession && (
                session.host.id.equals(wanted, ignoreCase = true) ||
                    session.host.label.equals(wanted, ignoreCase = true) ||
                    session.host.hostname.equals(wanted, ignoreCase = true) ||
                    session.host.endpoint.equals(wanted, ignoreCase = true)
                )
        }
    }

    fun findHost(target: String): Host? {
        val wanted = target.trim()
        if (wanted.isEmpty()) return null

        return VaultStore.hosts.firstOrNull { it.id.equals(wanted, ignoreCase = true) }
            ?: VaultStore.hosts.firstOrNull { it.label.equals(wanted, ignoreCase = true) }
            ?: VaultStore.hosts.firstOrNull { it.hostname.equals(wanted, ignoreCase = true) }
            ?: VaultStore.hosts.firstOrNull { it.endpoint.equals(wanted, ignoreCase = true) }
            ?: VaultStore.hosts.firstOrNull { it.label.contains(wanted, ignoreCase = true) }
            ?: VaultStore.hosts.firstOrNull { it.hostname.contains(wanted, ignoreCase = true) }
    }

    /**
     * Refuses a system whose owner has not switched agent access on.
     *
     * The refusal never names the system. Confirming "that one exists but is
     * off" would hand an agent the inventory it is not allowed to see, one
     * guessed label at a time — so a disabled system is indistinguishable from
     * one that was never there.
     */
    fun assertAllowed(host: Host) {
        if (!host.agentEnabled) throw notAvailable()
    }

    private fun notAvailable(): TargetError {
        val off = VaultStore.hosts.count { !it.agentEnabled }
        return TargetError(
            "No system available to the agent matches that name. " +
                "$off in the vault have agent access switched off; the owner turns one on in " +
                "its settings.",
        )
    }

    /** Opens or focuses a session and returns only once it is actually usable. */
    suspend fun resolve(target: String, connectTimeoutMs: Long = 60_000): HostSession {
        val live = findSession(target)
        if (live is HostSession) {
            assertAllowed(live.host)
            SessionManager.activate(live.id)
            if (live.state == SessionState.CLOSED || live.state == SessionState.FAILED) {
                SessionManager.reconnect(live)
            }
            awaitConnected(live, connectTimeoutMs)
            return live
        }
        if (live != null) throw TargetError("'$target' is a ${live.title} session, not SSH")

        val host = findHost(target) ?: throw notAvailable()
        assertAllowed(host)

        // Reusing a session the person already opened must not take their
        // keyboard away from them, so only a freshly created one is marked.
        SessionManager.liveFor(host)?.let { open ->
            SessionManager.activate(open.id)
            if (open.state == SessionState.CLOSED || open.state == SessionState.FAILED) {
                SessionManager.reconnect(open)
            }
            awaitConnected(open, connectTimeoutMs)
            return open
        }

        val session = SessionManager.open(host)
        session.agentOwned = true
        awaitConnected(session, connectTimeoutMs)
        return session
    }

    /** For tools that must act on a live session rather than dial one themselves. */
    fun require(target: String): HostSession {
        val session = findSession(target) ?: throw notAvailable()
        if (session !is HostSession) throw TargetError("That session is not an SSH session")
        assertAllowed(session.host)
        if (session.state != SessionState.CONNECTED) {
            throw TargetError("That session is ${session.statusLabel.lowercase()}")
        }
        return session
    }

    private suspend fun awaitConnected(session: HostSession, timeoutMs: Long) {
        val settled = withTimeoutOrNull(timeoutMs) {
            while (session.state == SessionState.CONNECTING || session.state == SessionState.IDLE) {
                delay(100)
            }
            session.state
        } ?: throw TargetError("Timed out connecting to ${session.host.endpoint}")

        if (settled != SessionState.CONNECTED) {
            throw TargetError(
                "Could not connect to ${session.host.endpoint}: " +
                    (session.error?.toString() ?: settled.name.lowercase()),
            )
        }
    }
}
