package com.example.ohmyssh.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.example.ohmyssh.theme.QThemeMode
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow

actual fun applyPlatformTheme(mode: QThemeMode) {
    val style = when (mode) {
        QThemeMode.SYSTEM -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
        QThemeMode.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        QThemeMode.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
    }
    UIApplication.sharedApplication.windows.forEach { window ->
        (window as? UIWindow)?.overrideUserInterfaceStyle = style
    }
}

actual val platformSupportsDynamicColors: Boolean
    get() = false

@Composable
actual fun platformDynamicColorScheme(isDark: Boolean): ColorScheme? = null
