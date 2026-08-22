package com.example.ohmyssh

import com.example.ohmyssh.session.SessionState
import com.example.ohmyssh.session.TerminalSession
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A session an agent opened refuses the user's keyboard; one it merely reused
 * must not, or connecting an agent would silently take the shell away from the
 * person already working in it.
 */
class AgentOwnedTest {

    private class FakeSession(id: String) : TerminalSession(id) {
        override val title = "fake"
        override val subtitle = "fake"
        override val checkpoints = emptyList<com.example.ohmyssh.session.Checkpoint>()
        override suspend fun connect() {}
        override suspend fun disconnect() {}
    }

    @Test
    fun defaultsToUserOwned() {
        assertFalse(FakeSession("a").agentOwned)
    }

    @Test
    fun readOnlyRuleMatchesOwnership() {
        val agent = FakeSession("agent").apply { agentOwned = true }
        val human = FakeSession("human")

        // The rule SessionPage applies to TerminalView.
        fun readOnly(session: TerminalSession) =
            session.state != SessionState.CONNECTED || session.agentOwned

        assertTrue(readOnly(agent), "an agent session must never take typing")
        assertTrue(readOnly(human), "a disconnected session takes no typing either")
    }
}
