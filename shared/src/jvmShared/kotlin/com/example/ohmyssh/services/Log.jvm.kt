package com.example.ohmyssh.services

internal actual fun writeStderrLine(line: String) {
    try {
        System.err.println(line)
    } catch (_: Exception) {
        // Some embeddings have no stderr.
    }
}
