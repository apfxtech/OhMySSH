package com.example.ohmyssh.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.ohmyssh.platform.appSettings

enum class QThemeMode(val label: String) {
    SYSTEM("Match system"),
    DARK("Always dark"),
    LIGHT("Always light");

    val wireName: String
        get() = when (this) {
            SYSTEM -> "system"
            DARK -> "dark"
            LIGHT -> "light"
        }
}

val kAccent = Color(0xFF34C7A4)

object QAppThemeController {
    private const val PREF_THEME_MODE = "theme.mode"

    var themeMode: QThemeMode by mutableStateOf(QThemeMode.SYSTEM)
        private set

    val accent: Color get() = kAccent

    fun loadThemeMode() {
        val raw = appSettings().getStringOrNull(PREF_THEME_MODE) ?: return
        QThemeMode.entries.firstOrNull { it.wireName == raw }?.let { themeMode = it }
    }

    fun applyThemeMode(mode: QThemeMode) {
        if (mode == themeMode) return
        themeMode = mode
        appSettings().putString(PREF_THEME_MODE, mode.wireName)
    }
}

class QAppColors(
    val background: Color,
    val card: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color,
    val info: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val dialogBarrier: Color,
    val dialogBackground: Color,
    val dialogDivider: Color,
    val dialogText: Color,
    val dialogMuted: Color,
    val terminalBackground: Color,
    val terminalForeground: Color,
    val terminalCursor: Color,
    val terminalSelection: Color,
    val onAccent: Color,
    val transparent: Color,
    val isDark: Boolean,
) {
    companion object {
        fun onColorFor(color: Color): Color =
            if (color.luminance() > 0.55f) Color(0xFF0A0A0A) else Color(0xFFFFFFFF)

        fun build(isDark: Boolean, accent: Color): QAppColors {
            val onAccent = onColorFor(accent)

            if (isDark) {
                return QAppColors(
                    background = Color(0xFF090909),
                    card = Color(0xFF151515),
                    accent = accent,
                    textPrimary = Color(0xFFFFFFFF),
                    textSecondary = Color(0xFFC8C8C8),
                    textMuted = Color(0xFF6F6F6F),
                    divider = Color(0xFF2C2C2C),
                    info = Color(0xFF589DFF),
                    success = Color(0xFF2ED34A),
                    warning = Color(0xFFFF9B34),
                    danger = Color(0xFFE85858),
                    dialogBarrier = Color(0x8A000000),
                    dialogBackground = Color(0xFF1A1A1A),
                    dialogDivider = Color(0xFF2C2C2C),
                    dialogText = Color(0xFFFFFFFF),
                    dialogMuted = Color(0xFF8D8D8D),
                    terminalBackground = Color(0xFF0B0B0B),
                    terminalForeground = Color(0xFFD8D8D8),
                    terminalCursor = accent,
                    terminalSelection = Color(0x4034C7A4),
                    onAccent = onAccent,
                    transparent = Color.Transparent,
                    isDark = true,
                )
            }

            return QAppColors(
                background = Color(0xFFF1F1F1),
                card = Color(0xFFFFFFFF),
                accent = accent,
                textPrimary = Color(0xFF000000),
                textSecondary = Color(0xFF616161),
                textMuted = Color(0xFFAAAAAA),
                divider = Color(0xFFDFDFDF),
                info = Color(0xFF589DFF),
                success = Color(0xFF2ED34A),
                warning = Color(0xFFFF9B34),
                danger = Color(0xFFE85858),
                dialogBarrier = Color(0x8A000000),
                dialogBackground = Color(0xFFFFFFFF),
                dialogDivider = Color(0xFFDFDFDF),
                dialogText = Color(0xFF000000),
                dialogMuted = Color(0xFF7A7A7A),
                terminalBackground = Color(0xFFFAFAFA),
                terminalForeground = Color(0xFF1E1E1E),
                terminalCursor = accent,
                terminalSelection = Color(0x3034C7A4),
                onAccent = onAccent,
                transparent = Color.Transparent,
                isDark = false,
            )
        }
    }
}

val LocalAppColors = staticCompositionLocalOf { QAppColors.build(isDark = true, accent = kAccent) }

val appColors: QAppColors
    @Composable get() = LocalAppColors.current

@Composable
private fun animated(target: Color): State<Color> =
    animateColorAsState(target, animationSpec = tween(durationMillis = 260))

@Composable
fun OhMySshTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (QAppThemeController.themeMode) {
        QThemeMode.SYSTEM -> systemDark
        QThemeMode.DARK -> true
        QThemeMode.LIGHT -> false
    }
    val target = QAppColors.build(isDark, QAppThemeController.accent)

    val colors = QAppColors(
        background = animated(target.background).value,
        card = animated(target.card).value,
        accent = animated(target.accent).value,
        textPrimary = animated(target.textPrimary).value,
        textSecondary = animated(target.textSecondary).value,
        textMuted = animated(target.textMuted).value,
        divider = animated(target.divider).value,
        info = target.info,
        success = target.success,
        warning = target.warning,
        danger = target.danger,
        dialogBarrier = target.dialogBarrier,
        dialogBackground = animated(target.dialogBackground).value,
        dialogDivider = animated(target.dialogDivider).value,
        dialogText = animated(target.dialogText).value,
        dialogMuted = animated(target.dialogMuted).value,
        terminalBackground = animated(target.terminalBackground).value,
        terminalForeground = animated(target.terminalForeground).value,
        terminalCursor = target.terminalCursor,
        terminalSelection = target.terminalSelection,
        onAccent = animated(target.onAccent).value,
        transparent = Color.Transparent,
        isDark = isDark,
    )

    val scheme: ColorScheme =
        (if (isDark) darkColorScheme() else lightColorScheme()).copy(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.info,
            onSecondary = colors.onAccent,
            error = colors.danger,
            onError = colors.onAccent,
            surface = colors.card,
            onSurface = colors.textPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            outlineVariant = colors.divider,
        )

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
