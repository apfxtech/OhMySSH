package com.example.ohmyssh.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual val appPlatform: AppPlatform = AppPlatform.IOS

@OptIn(ExperimentalForeignApi::class)
actual fun epochMicros(): Long = (NSDate().timeIntervalSince1970 * 1_000_000).toLong()

actual fun releaseTag(): String = ""
