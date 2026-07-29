package com.example.ohmyssh.platform

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.Settings

private val settings: Settings by lazy {
    SharedPreferencesSettings(
        AndroidApp.context.getSharedPreferences("ohmyssh_prefs", Context.MODE_PRIVATE),
    )
}

actual fun appSettings(): Settings = settings
