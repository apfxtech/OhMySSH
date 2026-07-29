package com.example.ohmyssh.services

object Log {
    fun info(scope: String, message: String) = write("INFO", scope, message)

    fun warn(scope: String, message: String) = write("WARN", scope, message)

    fun error(scope: String, error: Any?, throwable: Throwable? = null) {
        write("ERROR", scope, "$error")
        if (throwable != null) write("ERROR", scope, throwable.stackTraceToString())
    }

    private fun write(level: String, scope: String, message: String) {
        writeStderrLine("[$level] [$scope] $message")
    }
}

internal expect fun writeStderrLine(line: String)
