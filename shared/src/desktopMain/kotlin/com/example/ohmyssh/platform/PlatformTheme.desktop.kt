package com.example.ohmyssh.platform

import com.example.ohmyssh.theme.QAppThemeController
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.example.ohmyssh.theme.QThemeMode

actual fun applyPlatformTheme(mode: QThemeMode) {
    if (appPlatform != AppPlatform.MACOS) return
    runCatching {
        val appearance = mode.nsAppearanceName?.let { name ->
            ObjC.send(ObjC.cls("NSAppearance"), "appearanceNamed:", ObjC.string(name))
        }
        val app = ObjC.send(ObjC.cls("NSApplication"), "sharedApplication")
        // AppKit only accepts appearance changes on its own thread — and the
        // caller is on the AWT one, so the hop must not block.
        ObjC.send(
            app, "performSelectorOnMainThread:withObject:waitUntilDone:",
            ObjC.sel("setAppearance:"), appearance, false,
        )
    }
}

/// Must run in main() before AWT starts: the property is read once at AppKit
/// init, which gives the very first frame the right title bar instead of a
/// light-to-dark flash.
fun primeWindowAppearance() {
    if (appPlatform != AppPlatform.MACOS) return
    QAppThemeController.loadThemeMode()
    System.setProperty(
        "apple.awt.application.appearance",
        QAppThemeController.themeMode.nsAppearanceName ?: "system",
    )
}

private val QThemeMode.nsAppearanceName: String?
    get() = when (this) {
        QThemeMode.SYSTEM -> null
        QThemeMode.DARK -> "NSAppearanceNameDarkAqua"
        QThemeMode.LIGHT -> "NSAppearanceNameAqua"
    }

actual val platformSupportsDynamicColors: Boolean
    get() = false

@Composable
actual fun platformDynamicColorScheme(isDark: Boolean): ColorScheme? = null
