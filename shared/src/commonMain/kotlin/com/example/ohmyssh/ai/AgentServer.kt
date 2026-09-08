package com.example.ohmyssh.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Where an agent can reach this app, as the settings page reports it.
 *
 * Set by whichever platform hosts the MCP listener; stays null where none does.
 */
object AgentServer {
    var endpoint: String? by mutableStateOf(null)
}
