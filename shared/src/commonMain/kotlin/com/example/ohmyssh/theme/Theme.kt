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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.ohmyssh.platform.appSettings
import com.example.ohmyssh.platform.applyPlatformTheme
import com.example.ohmyssh.platform.platformDynamicColorScheme
import com.example.ohmyssh.platform.platformSupportsDynamicColors

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
    private const val PREF_DYNAMIC_COLORS = "theme.dynamicColors"

    var themeMode: QThemeMode by mutableStateOf(QThemeMode.SYSTEM)
        private set

    var dynamicColors: Boolean by mutableStateOf(true)
        private set

    val dynamicColorsSupported: Boolean get() = platformSupportsDynamicColors

    val accent: Color get() = kAccent

    fun loadThemeMode() {
        appSettings().getBooleanOrNull(PREF_DYNAMIC_COLORS)?.let { dynamicColors = it }
        val raw = appSettings().getStringOrNull(PREF_THEME_MODE) ?: return
        QThemeMode.entries.firstOrNull { it.wireName == raw }?.let { themeMode = it }
    }

    fun applyThemeMode(mode: QThemeMode) {
        if (mode == themeMode) return
        themeMode = mode
        appSettings().putString(PREF_THEME_MODE, mode.wireName)
        applyPlatformTheme(mode)
    }

    fun applyDynamicColors(enabled: Boolean) {
        if (enabled == dynamicColors) return
        dynamicColors = enabled
        appSettings().putBoolean(PREF_DYNAMIC_COLORS, enabled)
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
    val terminalAnsi: List<Color>,
    val onAccent: Color,
    val transparent: Color,
    val isDark: Boolean,
) {
    companion object {
        fun onColorFor(color: Color): Color =
            if (color.luminance() > 0.55f) Color(0xFF0A0A0A) else Color(0xFFFFFFFF)

        // Wallpaper-derived scheme mapped onto the same variables every
        // widget already reads; status and terminal colors stay semantic.
        fun fromScheme(scheme: ColorScheme, isDark: Boolean): QAppColors = QAppColors(
            background = if (isDark) scheme.surface else scheme.surfaceContainer,
            card = if (isDark) scheme.surfaceContainer else scheme.surfaceContainerLowest,
            accent = scheme.primary,
            textPrimary = scheme.onSurface,
            textSecondary = scheme.onSurfaceVariant,
            textMuted = scheme.outline,
            divider = scheme.outlineVariant,
            info = Color(0xFF589DFF),
            success = Color(0xFF2ED34A),
            warning = Color(0xFFFF9B34),
            danger = Color(0xFFE85858),
            dialogBarrier = Color(0x8A000000),
            dialogBackground = if (isDark) {
                scheme.surfaceContainerHigh
            } else {
                scheme.surfaceContainerLowest
            },
            dialogDivider = scheme.outlineVariant,
            dialogText = scheme.onSurface,
            dialogMuted = scheme.outline,
            terminalBackground = if (isDark) Color(0xFF0B0B0B) else Color(0xFFFAFAFA),
            terminalForeground = if (isDark) Color(0xFFD8D8D8) else Color(0xFF1E1E1E),
            terminalCursor = scheme.primary,
            terminalSelection = scheme.primary.copy(alpha = if (isDark) 0.25f else 0.19f),
            terminalAnsi = ansiPalette(isDark),
            onAccent = scheme.onPrimary,
            transparent = Color.Transparent,
            isDark = isDark,
        )

        private fun ansiPalette(isDark: Boolean): List<Color> = listOf(
            if (isDark) Color(0xFF3E3E3E) else Color(0xFF2E2E2E),
            Color(0xFFCD3131),
            Color(0xFF0DBC79),
            Color(0xFFE5E510),
            Color(0xFF2472C8),
            Color(0xFFBC3FBC),
            Color(0xFF11A8CD),
            Color(0xFFE5E5E5),
            Color(0xFF666666),
            Color(0xFFF14C4C),
            Color(0xFF23D18B),
            Color(0xFFF5F543),
            Color(0xFF3B8EEA),
            Color(0xFFD670D6),
            Color(0xFF29B8DB),
            Color(0xFFFFFFFF),
        )

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
                    terminalAnsi = ansiPalette(isDark = true),
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
                terminalAnsi = ansiPalette(isDark = false),
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
    val dynamicScheme =
        if (QAppThemeController.dynamicColors) platformDynamicColorScheme(isDark) else null
    val target = dynamicScheme?.let { QAppColors.fromScheme(it, isDark) }
        ?: QAppColors.build(isDark, QAppThemeController.accent)

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
        terminalAnsi = target.terminalAnsi,
        onAccent = animated(target.onAccent).value,
        transparent = Color.Transparent,
        isDark = isDark,
    )

    val scheme: ColorScheme = dynamicScheme?.copy(
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.card,
        onSurface = colors.textPrimary,
        surfaceTint = colors.card,
        scrim = colors.dialogBarrier,
        surfaceContainerHigh = colors.dialogBackground,
        surfaceContainerHighest = colors.dialogBackground,
    ) ?: (if (isDark) darkColorScheme() else lightColorScheme()).copy(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = lerp(colors.card, colors.accent, 0.25f),
            onPrimaryContainer = colors.textPrimary,
            inversePrimary = colors.accent,
            secondary = colors.info,
            onSecondary = QAppColors.onColorFor(colors.info),
            secondaryContainer = lerp(colors.card, colors.info, 0.25f),
            onSecondaryContainer = colors.textPrimary,
            tertiary = colors.success,
            onTertiary = QAppColors.onColorFor(colors.success),
            tertiaryContainer = lerp(colors.card, colors.success, 0.25f),
            onTertiaryContainer = colors.textPrimary,
            error = colors.danger,
            onError = QAppColors.onColorFor(colors.danger),
            errorContainer = lerp(colors.card, colors.danger, 0.25f),
            onErrorContainer = colors.textPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.card,
            onSurface = colors.textPrimary,
            surfaceVariant = lerp(colors.card, colors.divider, 0.5f),
            onSurfaceVariant = colors.textSecondary,
            // Tint == surface makes tonal elevation a no-op, so dialogs and
            // menus keep the exact dialogBackground instead of a primary wash.
            surfaceTint = colors.card,
            inverseSurface = colors.textPrimary,
            inverseOnSurface = colors.background,
            outline = colors.textMuted,
            outlineVariant = colors.divider,
            scrim = colors.dialogBarrier,
            surfaceBright = colors.dialogBackground,
            surfaceDim = colors.background,
            surfaceContainerLowest = colors.background,
            surfaceContainerLow = colors.card,
            surfaceContainer = colors.card,
            surfaceContainerHigh = colors.dialogBackground,
            surfaceContainerHighest = colors.dialogBackground,
        )

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
