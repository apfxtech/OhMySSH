package com.example.ohmyssh.platform

/// Reentrant lock: the emulator is written from network threads and read from
/// the UI.
expect class PlatformLock() {
    fun lock()
    fun unlock()
}

inline fun <T> PlatformLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
