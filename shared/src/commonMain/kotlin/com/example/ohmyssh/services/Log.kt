package com.example.ohmyssh.services

object Log {
    /**
     * An extra place every line also goes, installed at start-up by whoever can
     * write files.
     *
     * Added to stderr rather than replacing it: for the stdio MCP server stderr
     * is how a client surfaces problems at all, and trading that away for a
     * file would lose more than it gained.
     */
    var sink: ((level: String, scope: String, message: String) -> Unit)? = null

    fun info(scope: String, message: String) = write("INFO", scope, message)

    fun warn(scope: String, message: String) = write("WARN", scope, message)

    fun error(scope: String, error: Any?, throwable: Throwable? = null) {
        write("ERROR", scope, "$error")
        if (throwable != null) write("ERROR", scope, throwable.stackTraceToString())
    }

    private fun write(level: String, scope: String, message: String) {
        writeStderrLine("[$level] [$scope] $message")
        // A logger that throws would take the app down from inside whatever it
        // was reporting on, which is the worst possible moment for it.
        try {
            sink?.invoke(level, scope, message)
        } catch (_: Exception) {
        }
    }
}

internal expect fun writeStderrLine(line: String)
