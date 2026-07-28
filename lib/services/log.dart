import 'dart:io';

import 'package:flutter/foundation.dart';

/// Writes to stderr so it shows up under `flutter run`, in logcat, and in
/// Console.app, including release builds.
class Log {
  Log._();

  static void info(String scope, String message) =>
      _write('INFO', scope, message);

  static void warn(String scope, String message) =>
      _write('WARN', scope, message);

  static void error(String scope, Object error, [StackTrace? stackTrace]) {
    _write('ERROR', scope, '$error');
    if (stackTrace != null) _write('ERROR', scope, stackTrace.toString());
  }

  static void _write(String level, String scope, String message) {
    final line = '[$level] [$scope] $message';
    try {
      stderr.writeln(line);
    } catch (_) {
      // Some embeddings have no stderr; the debugPrint below still lands.
    }
    if (kDebugMode) debugPrint(line);
  }
}
