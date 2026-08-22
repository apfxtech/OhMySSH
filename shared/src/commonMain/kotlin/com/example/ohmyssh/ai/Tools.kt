package com.example.ohmyssh.ai

import com.example.ohmyssh.data.HistoryStore
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.net.LanScanner
import com.example.ohmyssh.platform.epochMillis
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.ssh.HostSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/// Tool output lands straight in a prompt, so one `cat` of a large log must not
/// spend the whole context window on its own.
const val kMaxToolOutput = 8000


class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

class ToolResult(val ok: Boolean, val content: String) {
    companion object {
        fun ok(content: String) = ToolResult(true, content.ifBlank { "(no output)" })
        fun fail(reason: String) = ToolResult(false, "Error: $reason")
    }
}

private fun prop(type: String, description: String): JsonObject = buildJsonObject {
    put("type", type)
    put("description", description)
}

private fun str(description: String) = prop("string", description)

private fun num(description: String) = prop("integer", description)


private fun schema(
    vararg properties: Pair<String, JsonObject>,
    required: List<String> = emptyList(),
): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", JsonObject(properties.toMap()))
    put("required", JsonArray(required.map(::JsonPrimitive)))
    put("additionalProperties", JsonPrimitive(false))
}

private val noArgs: JsonObject = buildJsonObject { put("type", "object") }

/// Repeated by thirteen tools; spelled out once because every copy of it is
/// paid for on every request.
private val sessionArg = "session" to str("id, label or hostname")

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.need(key: String): String =
    text(key) ?: throw TargetError("Missing required argument '$key'")

private fun JsonObject.number(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull


private fun clip(text: String): String =
    if (text.length <= kMaxToolOutput) {
        text
    } else {
        text.take(kMaxToolOutput) + "\n… truncated, ${text.length - kMaxToolOutput} more characters"
    }

/**
 * Everything a model is allowed to do to this app.
 *
 * [specs] is the wire description handed to a provider or an MCP client;
 * [call] is the single entry point that runs one.
 */
object AppTools {

    val specs: List<ToolSpec> = listOf(
        ToolSpec(
            "list_sessions",
            "Every system you may use, open or not, plus a count of those you may not.",
            noArgs,
        ),
        ToolSpec(
            "connect",
            "Open a session, or focus the one already open. Returns when connected.",
            schema("target" to str("id, label, hostname or endpoint"), required = listOf("target")),
        ),
        ToolSpec("disconnect", "Close a session.", schema(sessionArg, required = listOf("session"))),
        ToolSpec(
            "activate_session",
            "Show this session in the UI, so the user sees what you are working on.",
            schema(sessionArg, required = listOf("session")),
        ),
        ToolSpec(
            "run_command",
            "Run a shell command and return its output. Own channel; the user's shell is untouched.",
            schema(
                sessionArg,
                "command" to str("the command line"),
                "timeout_seconds" to num("default 30"),
                required = listOf("session", "command"),
            ),
        ),
        ToolSpec(
            "terminal_input",
            "Type into the user's terminal, blind. Only for interactive programs run_command cannot drive.",
            schema(
                sessionArg,
                "text" to str("raw text; end with CR for Enter"),
                required = listOf("session", "text"),
            ),
        ),
        ToolSpec(
            "send_password",
            "Have the app type this login's password at a waiting sudo prompt. You never see it.",
            schema(sessionArg, required = listOf("session")),
        ),
        ToolSpec(
            "scan_network",
            "Sweep the LAN: IP, MAC, hostname, ping, and the system that matches.",
            schema("timeout_seconds" to num("default 30")),
        ),
        ToolSpec(
            "search_history",
            "Commands run on these systems before, newest first.",
            schema(
                "query" to str("substring of the command"),
                "system" to str("id, label or hostname"),
                "limit" to num("default 40"),
            ),
        ),
    )

    val byName: Map<String, ToolSpec> = specs.associateBy { it.name }

    suspend fun call(name: String, args: JsonObject): ToolResult = try {
        Log.info("ai", "tool $name(${args.keys.joinToString(", ")})")
        dispatch(name, args)
    } catch (error: TargetError) {
        ToolResult.fail(error.message)
    } catch (error: Exception) {
        Log.warn("ai", "tool $name failed: $error")
        ToolResult.fail(error.message ?: error.toString())
    }

    private suspend fun dispatch(name: String, args: JsonObject): ToolResult = when (name) {

        "list_sessions" -> listSessions()

        "scan_network" -> scanNetwork(args.number("timeout_seconds") ?: 30)

        "connect" -> {
            val session = Targets.resolve(args.need("target"))
            ToolResult.ok(
                "Connected. session=${session.id} host=${session.host.endpoint} " +
                    "os=${session.profile?.osPretty ?: "unknown"}",
            )
        }

        "disconnect" -> {
            val session = Targets.require(args.need("session"))
            SessionManager.close(session.id)
            ToolResult.ok("Closed ${session.title}.")
        }

        "activate_session" -> {
            val session = Targets.require(args.need("session"))
            SessionManager.activate(session.id)
            ToolResult.ok("Now showing ${session.title}.")
        }

        "run_command" -> {
            val session = Targets.require(args.need("session"))
            val command = args.need("command")
            val timeout = (args.number("timeout_seconds") ?: 30).coerceIn(1, 600) * 1000L

            // The exec channel is invisible to both the screen and the recorder,
            // so the user would watch an idle terminal while an agent worked the
            // box and the history would show the connection with no commands in
            // it. Echoing here puts the agent's work where a person can see it.
            session.terminal.write("\r\n\u001B[36m[agent]\u001B[0m $command\r\n")
            val started = epochMillis()
            val output = try {
                session.exec(command, timeout)
            } catch (error: Exception) {
                session.commands.note(command, durationMs = epochMillis() - started)
                session.terminal.write("\u001B[31m${error.message}\u001B[0m\r\n")
                throw error
            }
            session.commands.note(command, durationMs = epochMillis() - started)
            session.terminal.write(output.replace("\n", "\r\n"))
            ToolResult.ok(clip(output))
        }

        "terminal_input" -> {
            val session = Targets.require(args.need("session"))
            session.terminal.sendKeys(args.need("text"))
            ToolResult.ok("Sent.")
        }

        "send_password" -> {
            val session = Targets.require(args.need("session"))
            if (!session.host.agentMayAuthenticate) {
                throw TargetError(
                    "'${session.host.displayLabel}' does not allow the agent to request its " +
                        "password. Turn that on in the system's settings.",
                )
            }
            val identity = VaultStore.identityFor(session.host)
                ?: throw TargetError("No user is attached to ${session.host.displayLabel}")
            val password = identity.password?.takeIf { it.isNotEmpty() }
                ?: throw TargetError("The saved login for ${session.host.displayLabel} has no password")

            // Straight to the PTY, never through terminal.write: a sudo prompt
            // does not echo, so the recorder sees a blank line and writes
            // nothing down — which is what keeps the secret out of the history.
            session.terminal.sendKeys(password + "\r")
            ToolResult.ok("Password sent.")
        }

        "search_history" -> searchHistory(
            query = args.text("query"),
            system = args.text("system"),
            limit = (args.number("limit") ?: 40).coerceIn(1, 200),
        )

        else -> ToolResult.fail("Unknown tool '$name'")
    }

    /**
     * One list for every reachable machine, open or not.
     *
     * An agent has no use for the split between a saved system and a live
     * session: it says where it wants to be and [Targets] works out whether that
     * means dialling. Systems the owner has not enabled are counted, never
     * named — telling an agent which machines exist but are off-limits only
     * invites it to keep asking.
     */
    private fun listSessions(): ToolResult {
        val allowed = VaultStore.hosts.filter { it.agentEnabled }
        val hidden = VaultStore.hosts.size - allowed.size

        if (allowed.isEmpty()) {
            return ToolResult.ok(
                "No systems are available to you" +
                    if (hidden > 0) " ($hidden in the vault have agent access switched off)." else ".",
            )
        }

        return ToolResult.ok(
            buildString {
                for (host in allowed) {
                    val live = SessionManager.sessions
                        .filterIsInstance<HostSession>()
                        .firstOrNull { it.host.id == host.id }
                    append(live?.id ?: "-")
                    append("  ${host.displayLabel}  ${host.endpoint}")
                    append("  ${live?.statusLabel?.lowercase() ?: "not open"}")
                    if (live != null && live.id == SessionManager.activeId) append("  on-screen")
                    if (host.agentMayAuthenticate) append("  password-on-request")
                    appendLine()
                }
                if (hidden > 0) append("($hidden more in the vault are not available to you.)")
            }.let(::clip),
        )
    }

    private suspend fun scanNetwork(timeoutSeconds: Int): ToolResult {
        LanScanner.refresh()
        // refresh() returns as soon as the sweep is launched; without waiting for
        // it to settle the caller would read whatever the previous sweep left.
        withTimeoutOrNull(timeoutSeconds.coerceIn(1, 300) * 1000L) {
            while (LanScanner.scanning) delay(200)
        }

        val devices = LanScanner.devices
        if (devices.isEmpty()) return ToolResult.ok(LanScanner.lastError ?: "No devices answered.")

        return ToolResult.ok(
            buildString {
                appendLine(
                    "subnet=${LanScanner.subnet ?: "?"} interface=${LanScanner.interfaceName ?: "?"}",
                )
                if (LanScanner.scanning) appendLine("(still sweeping, partial results)")
                for (device in devices) {
                    val saved = (
                        Targets.findHost(device.ipv4)
                            ?: device.hostname?.let { Targets.findHost(it) }
                            ?: device.shortName?.let { Targets.findHost(it) }
                        )?.takeIf { it.agentEnabled }
                    append(device.ipv4)
                    append("  mac=${device.mac ?: "-"}")
                    append("  name=${device.hostname ?: "-"}")
                    append("  ping=${device.rttMs?.let { "${it}ms" } ?: "-"}")
                    if (device.self) append("  (this device)")
                    if (saved != null) append("  saved=${saved.displayLabel} id=${saved.id}")
                    appendLine()
                }
            }.let(::clip),
        )
    }

    private fun searchHistory(query: String?, system: String?, limit: Int): ToolResult {
        val hostId = system?.let { name -> Targets.findHost(name)?.takeIf { it.agentEnabled }?.id }
        if (system != null && hostId == null) {
            throw TargetError("No system available to you matches that name.")
        }

        val rows = mutableListOf<String>()
        // HistoryStore keeps connections newest-first (it inserts at 0), so
        // reversing here would have answered "most recent" with the oldest rows.
        outer@ for (record in HistoryStore.connections) {
            if (hostId != null && record.hostId != hostId) continue
            // A machine the owner has not opened up must not leak through its
            // past commands either.
            if (hostId == null &&
                VaultStore.hosts.none { it.id == record.hostId && it.agentEnabled }
            ) {
                continue
            }
            for (command in record.commands.asReversed()) {
                if (query != null && !command.text.contains(query, ignoreCase = true)) continue
                val exit = command.exitCode?.let { " exit=$it" } ?: ""
                val cwd = command.cwd?.let { " cwd=$it" } ?: ""
                rows.add("${record.label}  ${command.text}$exit$cwd")
                if (rows.size >= limit) break@outer
            }
        }

        return ToolResult.ok(rows.joinToString("\n").ifEmpty { "No matching commands." })
    }
}
