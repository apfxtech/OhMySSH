package com.example.ohmyssh.platform

actual class PlatformLock {
    private val lock = java.util.concurrent.locks.ReentrantLock()

    actual fun lock() = lock.lock()

    actual fun unlock() = lock.unlock()
}
