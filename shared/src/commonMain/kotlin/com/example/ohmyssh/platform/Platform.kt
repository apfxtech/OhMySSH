package com.example.ohmyssh.platform

enum class AppPlatform { ANDROID, IOS, MACOS, WINDOWS, LINUX }

expect val appPlatform: AppPlatform

val AppPlatform.displayName: String
    get() = when (this) {
        AppPlatform.ANDROID -> "Android"
        AppPlatform.IOS -> "iOS"
        AppPlatform.MACOS -> "macOS"
        AppPlatform.WINDOWS -> "Windows"
        AppPlatform.LINUX -> "Linux"
    }

val AppPlatform.isDesktop: Boolean
    get() = this == AppPlatform.MACOS || this == AppPlatform.WINDOWS || this == AppPlatform.LINUX

expect fun epochMicros(): Long
