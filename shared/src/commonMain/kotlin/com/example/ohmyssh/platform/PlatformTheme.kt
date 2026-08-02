package com.example.ohmyssh.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.example.ohmyssh.theme.QThemeMode

expect fun applyPlatformTheme(mode: QThemeMode)

expect val platformSupportsDynamicColors: Boolean

@Composable
expect fun platformDynamicColorScheme(isDark: Boolean): ColorScheme?
