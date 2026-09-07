package com.example.ohmyssh.mcp

import com.example.ohmyssh.ai.AppTools
import com.example.ohmyssh.data.AutoLogin
import com.example.ohmyssh.data.VaultStore
import com.example.ohmyssh.platform.appVersion
import com.example.ohmyssh.platform.epochMillis
import com.example.ohmyssh.services.FileLog
import com.example.ohmyssh.services.Log
import com.example.ohmyssh.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.ConcurrentHashMap

private const val SERVER_NAME = "ohmyssh"

private const val DEFAULT_PROTOCOL = "2025-06-18"

private val KNOWN_PROTOCOLS = setOf("2026-07-28", "2025-06-18", "2025-03-26", "2024-11-05")

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Serves [AppTools] to any MCP client over stdio.
 *
 * stdout carries the protocol and nothing else — every diagnostic goes through
 * [Log], which writes to stderr. A stray println here corrupts the stream and
 * the client drops the connection with a parse error.
 */
class McpServer(
    private val input: BufferedReader = System.`in`.bufferedReader(),
    private val output: BufferedWriter = System.out.bufferedWriter(),
    /**
     * Whether losing the client tears the sessions down. False when the server
     * runs inside the GUI: a disconnecting agent must not close the terminals
     * the person at the keyboard is working in.
     */
    private val ownsSessions: Boolean = true,
) {

    /// Requests still running, by the id the client gave them, so a
    /// cancellation can find the one it names.
    private val inFlight = ConcurrentHashMap<String, Job>()

    private val writeLock = Any()

    suspend fun serve(): Unit = coroutineScope {
        if (ownsSessions) unlockVault()

        while (true) {
            val line = input.readLine() ?: break
            if (line.isBlank()) continue

            val request = try {
                json.parseToJsonElement(line) as? JsonObject
            } catch (error: Exception) {
                Log.warn("mcp", "unparseable frame: $error")
                null
            }
            if (request == null) {
                send(errorFrame(JsonNull, -32700, "Parse error"))
                continue
            }

            val id = request["id"]
            val method = (request["method"] as? JsonPrimitive)?.contentOrNull
            if (method == null) continue
            val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())

            // A notification carries no id and must never draw a response — a
            // client that gets one for notifications/initialized aborts the
            // handshake.
            if (id == null || id is JsonNull) {
                notification(method, params)
                continue
            }

            // Each call on its own coroutine, and never on the thread that
            // reads the socket. Handled in turn, a cancellation could not be
            // read until the call it cancels had already finished — which is
            // the same as not honouring it at all.
            val key = id.toString()
            // call= ties the start, the end and everything the tool logged in
            // between into one grep.
            val label = "call=$key ${describe(method, params)}"
            val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
                Log.info("mcp", "$label start")
                val started = epochMillis()
                val response = try {
                    resultFrame(id, handle(method, params))
                } catch (taken: CancellationException) {
                    // The client took the call back. Answering it anyway is
                    // forbidden, and the tool has already undone what it could.
                    Log.info("mcp", "$label cancelled ms=${epochMillis() - started}")
                    throw taken
                } catch (error: UnknownMethod) {
                    Log.warn("mcp", "$label unknown-method")
                    errorFrame(id, -32601, "Method not found: ${error.method}")
                } catch (error: Exception) {
                    Log.warn("mcp", "$label failed: $error")
                    errorFrame(id, -32603, error.message ?: error.toString())
                }
                Log.info("mcp", "$label done ms=${epochMillis() - started}")
                send(response)
            }
            // Registered before it can run, or a call that finishes at once
            // would clear an entry that has not been made yet.
            inFlight[key] = job
            job.invokeOnCompletion { inFlight.remove(key) }
            job.start()
        }

        Log.info("mcp", "client gone")
        // A dropped client leaves nobody to answer, and a half-sent block must
        // not carry on typing into someone's shell after it.
        inFlight.values.forEach { it.cancel() }
        if (ownsSessions) SessionManager.closeAll()
    }

    /**
     * Handles the frames that carry no id and take no answer.
     *
     * `notifications/cancelled` is the only way a client can say the user took
     * a call back. Dropping it — which is what logging and moving on amounts to
     * — leaves a tool running work nobody wants any more.
     */
    private fun notification(method: String, params: JsonObject) {
        if (method != "notifications/cancelled") {
            Log.info("mcp", "notification $method")
            return
        }

        val target = params["requestId"]?.toString()
        val job = target?.let { inFlight.remove(it) }
        if (job == null) {
            Log.info("mcp", "cancellation for request $target, which is not running")
            return
        }
        Log.info("mcp", "cancelling request $target")
        job.cancel()
    }

    /// What a call is written down as. A tools/call says which tool and, from
    /// the tool's own policy, as much of its arguments as may be kept.
    private fun describe(method: String, params: JsonObject): String {
        if (method != "tools/call") return "method=$method"
        val name = (params["name"] as? JsonPrimitive)?.contentOrNull ?: "?"
        val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        return "tool=$name${AppTools.audit(name, arguments)}"
    }

    private class UnknownMethod(val method: String) : Exception(method)

    private suspend fun handle(method: String, params: JsonObject): JsonObject = when (method) {

        "initialize" -> {
            val asked = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
            val agreed = asked?.takeIf { it in KNOWN_PROTOCOLS } ?: DEFAULT_PROTOCOL
            Log.info("mcp", "initialize: client asked $asked, agreed $agreed")
            buildJsonObject {
                put("protocolVersion", agreed)
                put("capabilities", buildJsonObject { put("tools", buildJsonObject { }) })
                // Clients inject this into the system prompt ahead of the tool
                // schemas; the ones that ignore it still get every rule from the
                // tool descriptions and from the code that enforces them.
                put("instructions", instructions())
                put(
                    "serverInfo",
                    buildJsonObject {
                        put("name", SERVER_NAME)
                        put("version", appVersion)
                    },
                )
            }
        }

        "ping" -> JsonObject(emptyMap())

        "tools/list" -> buildJsonObject {
            put(
                "tools",
                JsonArray(
                    AppTools.specs.map { spec ->
                        buildJsonObject {
                            put("name", spec.name)
                            put("description", spec.description)
                            put("inputSchema", spec.parameters)
                        }
                    },
                ),
            )
        }

        "tools/call" -> {
            val name = (params["name"] as? JsonPrimitive)?.contentOrNull
                ?: throw IllegalArgumentException("tools/call needs a name")
            val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
            val result = AppTools.call(name, arguments)
            buildJsonObject {
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", result.content)
                            },
                        )
                    },
                )
                put("isError", !result.ok)
            }
        }

        // Probed by clients during discovery; answering empty is cheaper than
        // letting them retry a method-not-found.
        "resources/list" -> buildJsonObject { put("resources", JsonArray(emptyList())) }
        "resources/templates/list" -> buildJsonObject { put("resourceTemplates", JsonArray(emptyList())) }
        "prompts/list" -> buildJsonObject { put("prompts", JsonArray(emptyList())) }

        else -> throw UnknownMethod(method)
    }

    /// The log is named here because a client that can read it can answer
    /// "what actually ran, and when" without asking anyone.
    private fun instructions(): String {
        val path = FileLog.path ?: return kServerInstructions
        return kServerInstructions + "\n\nEvery call, and every command that reaches a shell, " +
            "is logged one event per line at $path — grep it rather than guessing about what " +
            "happened earlier."
    }

    private suspend fun unlockVault() {
        if (VaultStore.isUnlocked) return
        val password = AutoLogin.readPassword()
        if (password == null) {
            Log.warn("mcp", "no auto-unlock password in the keystore; tools will fail until unlocked")
            return
        }
        try {
            VaultStore.unlock(password)
            Log.info("mcp", "vault unlocked, ${VaultStore.hosts.size} systems")
        } catch (error: Exception) {
            Log.error("mcp", "vault unlock failed: $error", error)
        }
    }

    /// Called from every request's own coroutine, so one frame must finish
    /// before the next starts: interleaved writes would reach the client as a
    /// line neither response can be parsed out of.
    private fun send(frame: JsonObject) {
        synchronized(writeLock) {
            output.write(json.encodeToString(JsonObject.serializer(), frame))
            output.write("\n")
            output.flush()
        }
    }

    private fun resultFrame(id: JsonElement, result: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    private fun errorFrame(id: JsonElement, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put(
            "error",
            buildJsonObject {
                put("code", code)
                put("message", message)
            },
        )
    }
}

fun main() {
    // No window is ever opened here, and AWT probing a display on a headless
    // box would abort the process before the first frame is read.
    System.setProperty("java.awt.headless", "true")
    FileLog.install("mcp stdio $appVersion")
    runBlocking { McpServer().serve() }
}
