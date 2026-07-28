import 'package:flutter/material.dart';
import 'package:xterm/xterm.dart';

import '../../ssh/session.dart';
import '../../theme/theme.dart';

/// The ANSI palette is fixed; only the chrome follows the app theme.
class SessionTerminalView extends StatelessWidget {
  const SessionTerminalView({super.key, required this.session});

  final HostSession session;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return ColoredBox(
      color: colors.terminalBackground,
      child: TerminalView(
        session.terminal,
        theme: _themeFor(colors),
        textStyle: const TerminalStyle(fontSize: 13, fontFamily: 'monospace'),
        padding: const EdgeInsets.all(8),
        cursorType: TerminalCursorType.block,
        backgroundOpacity: 0,
        autofocus: true,
        readOnly: !session.isConnected,
      ),
    );
  }

  TerminalTheme _themeFor(QAppColors colors) {
    final dark = colors.isDark;
    return TerminalTheme(
      cursor: colors.terminalCursor,
      selection: colors.terminalSelection,
      foreground: colors.terminalForeground,
      background: colors.terminalBackground,
      black: dark ? const Color(0xFF3E3E3E) : const Color(0xFF2E2E2E),
      red: const Color(0xFFCD3131),
      green: const Color(0xFF0DBC79),
      yellow: const Color(0xFFE5E510),
      blue: const Color(0xFF2472C8),
      magenta: const Color(0xFFBC3FBC),
      cyan: const Color(0xFF11A8CD),
      white: const Color(0xFFE5E5E5),
      brightBlack: const Color(0xFF666666),
      brightRed: const Color(0xFFF14C4C),
      brightGreen: const Color(0xFF23D18B),
      brightYellow: const Color(0xFFF5F543),
      brightBlue: const Color(0xFF3B8EEA),
      brightMagenta: const Color(0xFFD670D6),
      brightCyan: const Color(0xFF29B8DB),
      brightWhite: const Color(0xFFFFFFFF),
      searchHitBackground: colors.accent.withValues(alpha: 0.35),
      searchHitBackgroundCurrent: colors.accent,
      searchHitForeground: colors.onAccent,
    );
  }
}
