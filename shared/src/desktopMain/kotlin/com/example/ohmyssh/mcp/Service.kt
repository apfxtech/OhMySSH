package com.example.ohmyssh.mcp

import com.example.ohmyssh.services.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

const val kMcpDefaultPort = 8722

/**
 * Serves MCP from inside the running app.
 *
 * The point of living in the GUI process rather than a headless one is that an
 * agent then drives the *same* [com.example.ohmyssh.session.SessionManager] the
 * person is looking at: its connections appear in the session list, its commands
 * scroll past in the terminal, and its work lands in the same history file. A
 * second process would share none of that and would race the app for the vault.
 */
object McpService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var socket: ServerSocket? = null

    val port: Int? get() = socket?.localPort

    fun start(port: Int = kMcpDefaultPort) {
        if (socket != null) return

        val listener = try {
            // Loopback only: this socket drives SSH sessions with credentials
            // already unlocked, so it must never be reachable off the machine.
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
            }
        } catch (error: Exception) {
            Log.error("mcp", "cannot listen on 127.0.0.1:$port: $error", error)
            return
        }

        socket = listener
        Log.info("mcp", "listening on 127.0.0.1:${listener.localPort}")

        scope.launch {
            while (!listener.isClosed) {
                val client = try {
                    listener.accept()
                } catch (error: Exception) {
                    if (!listener.isClosed) Log.warn("mcp", "accept failed: $error")
                    break
                }
                scope.launch { handle(client) }
            }
        }
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private suspend fun handle(client: Socket) {
        Log.info("mcp", "client connected")
        try {
            client.use {
                McpServer(
                    input = it.getInputStream().bufferedReader(),
                    output = it.getOutputStream().bufferedWriter(),
                    ownsSessions = false,
                ).serve()
            }
        } catch (error: Exception) {
            Log.warn("mcp", "client failed: $error")
        }
    }
}
