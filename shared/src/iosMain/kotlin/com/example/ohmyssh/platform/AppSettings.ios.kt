package com.example.ohmyssh.platform

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

private val settings: Settings by lazy {
    NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
}

actual fun appSettings(): Settings = settings
