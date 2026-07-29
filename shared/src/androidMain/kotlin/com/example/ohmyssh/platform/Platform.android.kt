package com.example.ohmyssh.platform

actual val appPlatform: AppPlatform = AppPlatform.ANDROID

actual fun epochMicros(): Long =
    System.currentTimeMillis() * 1000 + (System.nanoTime() / 1000) % 1000

actual fun releaseTag(): String = System.getenv("OHMYSSH_RELEASE_TAG") ?: ""
