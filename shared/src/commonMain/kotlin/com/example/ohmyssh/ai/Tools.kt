package com.example.ohmyssh.ai

import com.example.ohmyssh.data.HistoryStore
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.net.LanScanner
import com.example.ohmyssh.platform.epochMillis
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.session.SessionManager
import com.example.ohmyssh.ssh.ExecResult
import com.example.ohmyssh.ssh.HostSession
import kotlinx.coroutines.CancellationException
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

/// stderr is clipped before stdout and against its own budget: on a failed
/// command it holds the answer, and a chatty stdout must not push it out.
const val kMaxStderrOutput = 2000


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


/// Quoted so a value holding spaces stays one field, and capped so a single
/// enormous command line cannot push the rest of a session out of view.
private fun quoted(value: String?): String =
    "\"" + (value ?: "").take(400).replace("\"", "\\\"") + "\""

private fun clip(text: String, limit: Int = kMaxToolOutput): String =
    if (text.length <= limit) {
        text
    } else {
        text.take(limit) + "\n… truncated, ${text.length - limit} more characters"
    }

/**
 * One command's result as the caller reads it.
 *
 * The exit status leads and stderr is always named, empty or not, because
 * without them an empty answer is ambiguous: a command that printed nothing and
 * one the shell could not find arrive identical, and the second read as the
 * first is a diagnosis pointed at the wrong thing entirely.
 */
private fun report(result: ExecResult): String = buildString {
    result.trouble?.let { appendLine("interrupted: $it") }
    appendLine("exit=${result.exitCode ?: "unknown"}")

    val stderr = clip(result.stderr.trimEnd(), kMaxStderrOutput)
    val stdout = clip(result.stdout.trimEnd(), kMaxToolOutput - stderr.length)
    appendLine("stdout:")
    appendLine(stdout.ifEmpty { "(empty)" })
    appendLine("stderr:")
    append(stderr.ifEmpty { "(empty)" })
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
            "Run a shell command on its own channel; the user's shell is untouched. Answers with " +
                "exit=, stdout and stderr apart, and keeps partial output if the link drops.",
            schema(
                sessionArg,
                "command" to str("the command line"),
                "timeout_seconds" to num("default 30"),
                required = listOf("session", "command"),
            ),
        ),
        ToolSpec(
            "terminal_input",
            "Type into the user's terminal, blind. Only for interactive programs run_command cannot " +
                "drive. Several lines are run one per prompt, comments dropped, and the call is " +
                "held until they have gone out — cancel it and the rest is dropped.",
            schema(
                sessionArg,
                "text" to str("raw text; end with CR for Enter"),
                "timeout_seconds" to num("how long to wait for the block to go out; default 120"),
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
        // The call itself is logged by whoever dispatched it, with the id that
        // ties it to its answer. What is added here is the half that would
        // otherwise go unwritten: the calls that were turned down, and why.
        dispatch(name, args)
    } catch (error: TargetError) {
        Log.info("ai", "tool=$name refused: ${error.message}")
        ToolResult.fail(error.message)
    } catch (error: Exception) {
        Log.warn("ai", "tool=$name failed: $error")
        ToolResult.fail(error.message ?: error.toString())
    }

    /**
     * What one call may be written down as.
     *
     * The log outlives the session, so an argument is only quoted where the
     * same text is kept in the history anyway. terminal_input is the exception
     * that matters: a single line of it is usually the answer to whatever
     * prompt is on screen, and that prompt is often asking for a password —
     * the one thing the recorder deliberately never writes down. Its shape is
     * logged; the commands it queues are logged by the queue as they go out.
     */
    fun audit(name: String, args: JsonObject): String = buildString {
        args.text("session")?.let { append(" session=$it") }
        when (name) {
            "run_command" -> append(" cmd=${quoted(args.text("command"))}")

            "terminal_input" -> {
                val text = args.text("text").orEmpty()
                append(" bytes=${text.length} lines=${text.count { it == '\n' } + 1}")
            }

            "connect" -> append(" target=${quoted(args.text("target"))}")

            "search_history" -> {
                args.text("query")?.let { append(" query=${quoted(it)}") }
                args.text("system")?.let { append(" system=$it") }
            }
        }
    }

    private suspend fun dispatch(name: String, args: JsonObject): ToolResult = when (name) {

        "list_sessions" -> listSessions()

        "scan_network" -> scanNetwork(args.number("timeout_seconds") ?: 30)

        "connect" -> {
            val session = Targets.resolve(args.need("target"))
            val profile = session.profile
            ToolResult.ok(
                buildString {
                    append("Connected. session=${session.id} host=${session.host.endpoint}")
                    append(" os=${profile?.osPretty ?: "unknown"}")
                    // Who the shell actually runs as, not the label and not
                    // always the vault's login: everything relative — ~, sudo,
                    // where a file lands — is read against this and nothing else
                    // in the connect result says it.
                    append(" user=${profile?.user ?: session.identity?.username ?: "unknown"}")
                    profile?.home?.let { append(" home=$it") }
                    // Named here because a tool nobody calls may as well not
                    // exist, and this host's own past commands are the shortest
                    // path to how the operator runs it.
                    commandsRecorded(session.host.id).takeIf { it > 0 }
                        ?.let { append(" history=$it commands recorded (search_history)") }
                },
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
            val result = try {
                session.exec(command, timeout)
            } catch (error: Exception) {
                session.commands.note(command, durationMs = epochMillis() - started)
                session.terminal.write("\u001B[31m${error.message}\u001B[0m\r\n")
                throw error
            }
            session.commands.note(
                command,
                exitCode = result.exitCode,
                durationMs = epochMillis() - started,
            )

            session.terminal.write(result.stdout.replace("\n", "\r\n"))
            if (result.stderr.isNotEmpty()) {
                session.terminal.write("\u001B[31m${result.stderr.replace("\n", "\r\n")}\u001B[0m")
            }
            result.trouble?.let { session.terminal.write("\r\n\u001B[31m$it\u001B[0m\r\n") }

            // A dropped link is a failed call even though the output survives:
            // handing it back as a clean result would have the caller read five
            // partial minutes as the whole answer.
            ToolResult(result.trouble == null, report(result))
        }

        "terminal_input" -> {
            val session = Targets.require(args.need("session"))
            val text = args.need("text")
            val timeout = (args.number("timeout_seconds") ?: 120).coerceIn(1, 600) * 1000L
            // Keystrokes go straight through — arrows, Ctrl-C and a bare answer
            // to a prompt are what this tool is for. A block of commands does
            // not: it is paced through the paste queue, one command per prompt,
            // so a sudo question in the middle of it is not answered by line 3.
            if (text.trimEnd('\r', '\n').any { it == '\n' || it == '\r' }) {
                // Stacking a block behind one still going out would run both
                // with nothing here able to tell them apart or call either
                // back, which is how a block outlives the turn that sent it.
                session.paste.remaining.takeIf { it > 0 }?.let { pending ->
                    throw TargetError(
                        "$pending command(s) from an earlier block are still queued on this " +
                            "session and have not been sent yet. Wait for them, or have the " +
                            "user cancel the paste from the strip in the terminal.",
                    )
                }
                val queued = session.paste.submit(text, byAgent = true)
                ToolResult.ok(drain(session, queued, timeout))
            } else {
                // Claimed for the agent across the write itself: the shell
                // echoes this exactly like typing, so the recorder has no other
                // way to know whose keystroke it was.
                session.commands.attributeNextInput(true)
                try {
                    session.terminal.sendKeys(text)
                } finally {
                    session.commands.attributeNextInput(false)
                }
                ToolResult.ok("Sent.")
            }
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
                    // Probed for an open session, configured for the rest: the
                    // login the vault dials with and the one the shell turned
                    // out to run as are not always the same name.
                    val login = live?.profile?.user ?: VaultStore.identityFor(host)?.username
                    if (!login.isNullOrBlank()) append("  user=$login")
                    live?.profile?.home?.let { append("  home=$it") }
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

    /**
     * Holds the call open while a queued block actually goes out.
     *
     * The queue sends one command per prompt, which used to happen long after
     * the tool had answered: nobody on this side could then call it back, and a
     * block could land minutes later, after the caller had decided against it.
     * Waiting here puts the sending inside the request, so cancelling the
     * request cancels the rest of the block.
     */
    private suspend fun drain(session: HostSession, queued: Int, timeoutMillis: Long): String {
        val sent = try {
            withTimeoutOrNull(timeoutMillis) {
                while (session.paste.remaining > 0) delay(100)
                true
            } != null
        } catch (cancelled: CancellationException) {
            session.paste.cancel()
            throw cancelled
        }

        if (sent) return "Sent $queued command(s), one per prompt."

        // One reading, not three: the queue is being drained by its own
        // coroutine, and a count that moved between two of them would report a
        // block that never existed.
        val left = session.paste.remaining
        val held = session.paste.waiting

        // Not cancelled, so the block is half run, and dropping the rest would
        // leave the host in a state nobody asked for. Said out loud instead,
        // because from here on only the user can stop it.
        return "Sent ${queued - left} of $queued command(s); $left still queued, held by " +
            "${held.name.lowercase()}. They go out on their own from here — cancelling this " +
            "call no longer reaches them, only the user can, from the paste strip."
    }

    private fun commandsRecorded(hostId: String): Int =
        HistoryStore.connections.sumOf { if (it.hostId == hostId) it.commands.size else 0 }

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
                val who = if (command.agent) " [agent]" else ""
                rows.add("${record.label}  ${command.text}$exit$cwd$who")
                if (rows.size >= limit) break@outer
            }
        }

        return ToolResult.ok(rows.joinToString("\n").ifEmpty { "No matching commands." })
    }
}
