package com.example.ohmyssh.platform

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

private val settings: Settings by lazy {
    PreferencesSettings(Preferences.userRoot().node("com/example/ohmyssh"))
}

actual fun appSettings(): Settings = settings
