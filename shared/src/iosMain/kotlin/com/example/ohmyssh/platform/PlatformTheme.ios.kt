package com.example.ohmyssh.platform

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
