package com.example.ohmyssh.platform

private val osName = System.getProperty("os.name")?.lowercase() ?: ""

actual val appPlatform: AppPlatform = when {
    osName.contains("mac") || osName.contains("darwin") -> AppPlatform.MACOS
    osName.contains("win") -> AppPlatform.WINDOWS
    else -> AppPlatform.LINUX
}

actual fun epochMicros(): Long =
    System.currentTimeMillis() * 1000 + (System.nanoTime() / 1000) % 1000
