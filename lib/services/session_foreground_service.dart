import 'dart:async';
import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';

import '../ssh/manager.dart';

/// Keeps SSH sessions alive on Android while the app is backgrounded.
/// Android-only; a no-op elsewhere.
///
/// Android 12+ forbids starting a foreground service from the background, so a
/// start requested while backgrounded is deferred until the app next resumes.
///
/// Android 15 caps `dataSync` services at 6 cumulative hours per 24h; the
/// budget resets when the app returns to the foreground.
class SessionForegroundService with WidgetsBindingObserver {
  SessionForegroundService._();

  static final SessionForegroundService instance = SessionForegroundService._();

  static const int _serviceId = 3001;
  static const String _channelId = 'ssh_sessions';

  bool _started = false;
  bool _initialized = false;
  bool _wantRunning = false;
  bool _foreground = true;
  bool _serviceRunning = false;
  int _sessionCount = 0;
  bool _askedBatteryExemption = false;

  /// Serializes start/stop so overlapping session events cannot interleave two
  /// platform service requests.
  Future<void> _pending = Future<void>.value();

  void start() {
    if (!Platform.isAndroid || _started) return;
    _started = true;
    WidgetsBinding.instance.addObserver(this);
    SessionManager.instance.addListener(_onSessionsChanged);
  }

  void stop() {
    if (!_started) return;
    _started = false;
    WidgetsBinding.instance.removeObserver(this);
    SessionManager.instance.removeListener(_onSessionsChanged);
    _wantRunning = false;
    _sync();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final wasForeground = _foreground;
    _foreground = state == AppLifecycleState.resumed;
    if (_foreground && !wasForeground) _sync();
  }

  void _onSessionsChanged() {
    final manager = SessionManager.instance;
    _sessionCount = manager.sessions.length;
    _wantRunning = manager.hasLiveSessions;
    _sync();
  }

  void _sync() {
    if (_wantRunning && _foreground && !_serviceRunning) {
      _enqueue(_startService);
    } else if (!_wantRunning && _serviceRunning) {
      _enqueue(_stopService);
    } else if (_wantRunning && _serviceRunning) {
      _enqueue(_updateNotification);
    }
  }

  void _enqueue(Future<void> Function() op) {
    _pending = _pending.then((_) => op()).catchError((Object error) {
      debugPrint('[SessionForegroundService] op failed: $error');
    });
  }

  void _ensureInitialized() {
    if (_initialized) return;
    _initialized = true;
    FlutterForegroundTask.init(
      androidNotificationOptions: AndroidNotificationOptions(
        channelId: _channelId,
        channelName: 'SSH sessions',
        channelDescription: 'Keeps open SSH sessions alive in the background',
        channelImportance: NotificationChannelImportance.LOW,
        priority: NotificationPriority.LOW,
        onlyAlertOnce: true,
      ),
      iosNotificationOptions: const IOSNotificationOptions(),
      foregroundTaskOptions: ForegroundTaskOptions(
        eventAction: ForegroundTaskEventAction.nothing(),
        allowWakeLock: true,
        allowWifiLock: true,
        autoRunOnBoot: false,
      ),
    );
  }

  String get _notificationText =>
      _sessionCount == 1 ? '1 session open' : '$_sessionCount sessions open';

  Future<void> _startService() async {
    // Re-check under the serialized op: state may have moved while queued.
    if (_serviceRunning || !_wantRunning || !_foreground) return;
    _ensureInitialized();

    // Android 13+ requires runtime notification permission for an FGS.
    final permission =
        await FlutterForegroundTask.checkNotificationPermission();
    if (permission != NotificationPermission.granted) {
      await FlutterForegroundTask.requestNotificationPermission();
    }

    if (!_askedBatteryExemption) {
      _askedBatteryExemption = true;
      if (!await FlutterForegroundTask.isIgnoringBatteryOptimizations) {
        await FlutterForegroundTask.requestIgnoreBatteryOptimization();
      }
    }

    final result = await FlutterForegroundTask.startService(
      serviceId: _serviceId,
      serviceTypes: const [ForegroundServiceTypes.dataSync],
      notificationTitle: 'ohmyssh',
      notificationText: _notificationText,
    );
    if (result is ServiceRequestSuccess) {
      _serviceRunning = true;
    } else if (result is ServiceRequestFailure) {
      debugPrint('[SessionForegroundService] start failed: ${result.error}');
    }
  }

  Future<void> _updateNotification() async {
    if (!_serviceRunning) return;
    try {
      await FlutterForegroundTask.updateService(
        notificationTitle: 'ohmyssh',
        notificationText: _notificationText,
      );
    } catch (error) {
      debugPrint('[SessionForegroundService] update failed: $error');
    }
  }

  Future<void> _stopService() async {
    if (!_serviceRunning) return;
    _serviceRunning = false;
    final result = await FlutterForegroundTask.stopService();
    if (result is ServiceRequestFailure) {
      debugPrint('[SessionForegroundService] stop failed: ${result.error}');
    }
  }
}
