import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

enum QThemeMode {
  system('Match system'),
  dark('Always dark'),
  light('Always light');

  const QThemeMode(this.label);

  final String label;
}

const Color kAccent = Color(0xFF34C7A4);

class QAppThemeController extends ChangeNotifier with WidgetsBindingObserver {
  QAppThemeController._() {
    WidgetsBinding.instance.addObserver(this);
  }

  static const String _prefThemeMode = 'theme.mode';

  static final QAppThemeController instance = QAppThemeController._();

  QThemeMode _themeMode = QThemeMode.system;

  QThemeMode get themeMode => _themeMode;

  Color get accent => kAccent;

  Brightness get brightness {
    switch (_themeMode) {
      case QThemeMode.system:
        return WidgetsBinding.instance.platformDispatcher.platformBrightness;
      case QThemeMode.dark:
        return Brightness.dark;
      case QThemeMode.light:
        return Brightness.light;
    }
  }

  bool get isDark => brightness == Brightness.dark;

  Future<void> loadThemeMode() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_prefThemeMode);
    if (raw == null) return;
    for (final mode in QThemeMode.values) {
      if (mode.name == raw) {
        if (mode != _themeMode) {
          _themeMode = mode;
          notifyListeners();
        }
        return;
      }
    }
  }

  Future<void> setThemeMode(QThemeMode mode) async {
    if (mode == _themeMode) return;
    _themeMode = mode;
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_prefThemeMode, mode.name);
  }

  @override
  void didChangePlatformBrightness() {
    if (_themeMode == QThemeMode.system) notifyListeners();
  }
}

@immutable
class QAppColors extends ThemeExtension<QAppColors> {
  const QAppColors({
    required this.background,
    required this.card,
    required this.accent,
    required this.textPrimary,
    required this.textSecondary,
    required this.textMuted,
    required this.divider,
    required this.info,
    required this.success,
    required this.warning,
    required this.danger,
    required this.dialogBarrier,
    required this.dialogBackground,
    required this.dialogDivider,
    required this.dialogText,
    required this.dialogMuted,
    required this.terminalBackground,
    required this.terminalForeground,
    required this.terminalCursor,
    required this.terminalSelection,
    required this.onAccent,
    required this.transparent,
    required this.isDark,
  });

  final Color background;
  final Color card;
  final Color accent;
  final Color textPrimary;
  final Color textSecondary;
  final Color textMuted;
  final Color divider;
  final Color info;
  final Color success;
  final Color warning;
  final Color danger;
  final Color dialogBarrier;
  final Color dialogBackground;
  final Color dialogDivider;
  final Color dialogText;
  final Color dialogMuted;
  final Color terminalBackground;
  final Color terminalForeground;
  final Color terminalCursor;
  final Color terminalSelection;
  final Color onAccent;
  final Color transparent;
  final bool isDark;

  factory QAppColors.build(Brightness brightness, Color accent) {
    final isDark = brightness == Brightness.dark;
    final onAccent = onColorFor(accent);

    if (isDark) {
      return QAppColors(
        background: const Color(0xFF090909),
        card: const Color(0xFF151515),
        accent: accent,
        textPrimary: const Color(0xFFFFFFFF),
        textSecondary: const Color(0xFFC8C8C8),
        textMuted: const Color(0xFF6F6F6F),
        divider: const Color(0xFF2C2C2C),
        info: const Color(0xFF589DFF),
        success: const Color(0xFF2ED34A),
        warning: const Color(0xFFFF9B34),
        danger: const Color(0xFFE85858),
        dialogBarrier: const Color(0x8A000000),
        dialogBackground: const Color(0xFF1A1A1A),
        dialogDivider: const Color(0xFF2C2C2C),
        dialogText: const Color(0xFFFFFFFF),
        dialogMuted: const Color(0xFF8D8D8D),
        terminalBackground: const Color(0xFF0B0B0B),
        terminalForeground: const Color(0xFFD8D8D8),
        terminalCursor: accent,
        terminalSelection: const Color(0x4034C7A4),
        onAccent: onAccent,
        transparent: Colors.transparent,
        isDark: true,
      );
    }

    return QAppColors(
      background: const Color(0xFFF1F1F1),
      card: const Color(0xFFFFFFFF),
      accent: accent,
      textPrimary: const Color(0xFF000000),
      textSecondary: const Color(0xFF616161),
      textMuted: const Color(0xFFAAAAAA),
      divider: const Color(0xFFDFDFDF),
      info: const Color(0xFF589DFF),
      success: const Color(0xFF2ED34A),
      warning: const Color(0xFFFF9B34),
      danger: const Color(0xFFE85858),
      dialogBarrier: const Color(0x8A000000),
      dialogBackground: const Color(0xFFFFFFFF),
      dialogDivider: const Color(0xFFDFDFDF),
      dialogText: const Color(0xFF000000),
      dialogMuted: const Color(0xFF7A7A7A),
      terminalBackground: const Color(0xFFFAFAFA),
      terminalForeground: const Color(0xFF1E1E1E),
      terminalCursor: accent,
      terminalSelection: const Color(0x3034C7A4),
      onAccent: onAccent,
      transparent: Colors.transparent,
      isDark: false,
    );
  }

  static Color onColorFor(Color color) => color.computeLuminance() > 0.55
      ? const Color(0xFF0A0A0A)
      : const Color(0xFFFFFFFF);

  @override
  QAppColors copyWith({
    Color? background,
    Color? card,
    Color? accent,
    Color? textPrimary,
    Color? textSecondary,
    Color? textMuted,
    Color? divider,
    Color? info,
    Color? success,
    Color? warning,
    Color? danger,
    Color? dialogBarrier,
    Color? dialogBackground,
    Color? dialogDivider,
    Color? dialogText,
    Color? dialogMuted,
    Color? terminalBackground,
    Color? terminalForeground,
    Color? terminalCursor,
    Color? terminalSelection,
    Color? onAccent,
    Color? transparent,
    bool? isDark,
  }) {
    return QAppColors(
      background: background ?? this.background,
      card: card ?? this.card,
      accent: accent ?? this.accent,
      textPrimary: textPrimary ?? this.textPrimary,
      textSecondary: textSecondary ?? this.textSecondary,
      textMuted: textMuted ?? this.textMuted,
      divider: divider ?? this.divider,
      info: info ?? this.info,
      success: success ?? this.success,
      warning: warning ?? this.warning,
      danger: danger ?? this.danger,
      dialogBarrier: dialogBarrier ?? this.dialogBarrier,
      dialogBackground: dialogBackground ?? this.dialogBackground,
      dialogDivider: dialogDivider ?? this.dialogDivider,
      dialogText: dialogText ?? this.dialogText,
      dialogMuted: dialogMuted ?? this.dialogMuted,
      terminalBackground: terminalBackground ?? this.terminalBackground,
      terminalForeground: terminalForeground ?? this.terminalForeground,
      terminalCursor: terminalCursor ?? this.terminalCursor,
      terminalSelection: terminalSelection ?? this.terminalSelection,
      onAccent: onAccent ?? this.onAccent,
      transparent: transparent ?? this.transparent,
      isDark: isDark ?? this.isDark,
    );
  }

  @override
  QAppColors lerp(ThemeExtension<QAppColors>? other, double t) {
    if (other is! QAppColors) return this;
    Color mix(Color a, Color b) => Color.lerp(a, b, t) ?? a;
    return QAppColors(
      background: mix(background, other.background),
      card: mix(card, other.card),
      accent: mix(accent, other.accent),
      textPrimary: mix(textPrimary, other.textPrimary),
      textSecondary: mix(textSecondary, other.textSecondary),
      textMuted: mix(textMuted, other.textMuted),
      divider: mix(divider, other.divider),
      info: mix(info, other.info),
      success: mix(success, other.success),
      warning: mix(warning, other.warning),
      danger: mix(danger, other.danger),
      dialogBarrier: mix(dialogBarrier, other.dialogBarrier),
      dialogBackground: mix(dialogBackground, other.dialogBackground),
      dialogDivider: mix(dialogDivider, other.dialogDivider),
      dialogText: mix(dialogText, other.dialogText),
      dialogMuted: mix(dialogMuted, other.dialogMuted),
      terminalBackground: mix(terminalBackground, other.terminalBackground),
      terminalForeground: mix(terminalForeground, other.terminalForeground),
      terminalCursor: mix(terminalCursor, other.terminalCursor),
      terminalSelection: mix(terminalSelection, other.terminalSelection),
      onAccent: mix(onAccent, other.onAccent),
      transparent: mix(transparent, other.transparent),
      isDark: t < 0.5 ? isDark : other.isDark,
    );
  }
}

ThemeData buildAppTheme(Brightness brightness, Color accent) {
  final colors = QAppColors.build(brightness, accent);
  final colorScheme =
      (colors.isDark ? const ColorScheme.dark() : const ColorScheme.light())
          .copyWith(
            primary: colors.accent,
            onPrimary: colors.onAccent,
            secondary: colors.info,
            onSecondary: colors.onAccent,
            error: colors.danger,
            onError: colors.onAccent,
            surface: colors.card,
            onSurface: colors.textPrimary,
          );
  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    scaffoldBackgroundColor: colors.background,
    colorScheme: colorScheme,
    dividerColor: colors.divider,
    dialogTheme: DialogThemeData(backgroundColor: colors.dialogBackground),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(foregroundColor: colors.accent),
    ),
    iconButtonTheme: IconButtonThemeData(
      style: IconButton.styleFrom(
        iconSize: 20,
        minimumSize: const Size.square(40),
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      ),
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(color: colors.accent),
    extensions: [colors],
  );
}

extension QThemeContext on BuildContext {
  QAppColors get appColors => Theme.of(this).extension<QAppColors>()!;
}
