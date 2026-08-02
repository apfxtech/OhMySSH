package com.example.ohmyssh.platform

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.ohmyssh.theme.QThemeMode

actual fun applyPlatformTheme(mode: QThemeMode) = Unit

actual val platformSupportsDynamicColors: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
actual fun platformDynamicColorScheme(isDark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
