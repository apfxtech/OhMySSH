import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../services/log.dart';

class AutoLogin {
  AutoLogin._();

  static final AutoLogin instance = AutoLogin._();

  static const _scope = 'AutoLogin';
  static const _key = 'vault.master';

  // first_unlock, not unlocked: auto-unlock must work when the app is launched
  // into the background after a reboot.
  static const _storage = FlutterSecureStorage(
    iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
    mOptions: MacOsOptions(accessibility: KeychainAccessibility.first_unlock),
  );

  bool? _available;
  String? _unavailableReason;
  bool _unavailableIsExpected = false;

  String? get unavailableReason => _unavailableReason;

  /// True when the keystore is missing because of how this build was signed or
  /// which desktop session it runs in, not because something went wrong.
  bool get unavailableIsExpected => _unavailableIsExpected;

  /// Probes with a write+delete, not a read: on a sandboxed macOS build reading
  /// a missing key succeeds while writing fails with -34018.
  Future<bool> isAvailable() async {
    final cached = _available;
    if (cached != null) return cached;
    const probeKey = 'keystore.probe';
    try {
      await _storage.write(key: probeKey, value: 'ok');
      await _storage.delete(key: probeKey);
      _unavailableReason = null;
      _unavailableIsExpected = false;
      Log.info(_scope, 'keystore available (write probe passed)');
      return _available = true;
    } catch (error, stackTrace) {
      _unavailableIsExpected = isExpectedFailure(error);
      _unavailableReason = describeFailure(error);
      if (!_unavailableIsExpected) {
        Log.error(_scope, 'keystore probe failed: $error', stackTrace);
      }
      Log.warn(_scope, 'auto-unlock off: $_unavailableReason');
      final hint = _developerHint(error);
      if (hint != null) Log.info(_scope, hint);
      return _available = false;
    }
  }

  Future<bool> isEnabled() async {
    if (_available == false) return false;
    try {
      return await _storage.containsKey(key: _key);
    } catch (error, stackTrace) {
      _report('containsKey failed', error, stackTrace);
      return false;
    }
  }

  Future<String?> readPassword() async {
    if (_available == false) return null;
    try {
      return await _storage.read(key: _key);
    } catch (error, stackTrace) {
      _report('read failed', error, stackTrace);
      return null;
    }
  }

  Future<void> enable(String password) async {
    try {
      await _storage.write(key: _key, value: password);
      Log.info(_scope, 'auto-unlock enabled');
    } catch (error, stackTrace) {
      _report('write failed', error, stackTrace);
      throw AutoLoginException(describeFailure(error));
    }
  }

  /// Never throws: revoking must always succeed from the user's side.
  Future<void> disable() async {
    if (_available == false) return;
    try {
      await _storage.delete(key: _key);
      Log.info(_scope, 'auto-unlock disabled');
    } catch (error, stackTrace) {
      _report('delete failed', error, stackTrace);
    }
  }

  /// Expected failures get one warning line; only genuine faults are logged as
  /// errors with a stack trace.
  static void _report(String what, Object error, StackTrace stackTrace) {
    if (isExpectedFailure(error)) {
      Log.warn(_scope, '$what: ${describeFailure(error)}');
      return;
    }
    Log.error(_scope, '$what: $error', stackTrace);
  }

  /// A keystore the platform simply does not hand out: an ad-hoc signed macOS
  /// build with no keychain entitlement, a locked or absent keychain, a Linux
  /// session with no secret service, or a platform with no plugin at all.
  static bool isExpectedFailure(Object error) {
    final text = '$error'.toLowerCase();
    return text.contains('-34018') ||
        text.contains('-25291') ||
        text.contains('-25308') ||
        text.contains('libsecret') ||
        text.contains('secret service') ||
        text.contains('missingpluginexception');
  }

  static String describeFailure(Object error) {
    final text = '$error';
    if (text.contains('-34018')) {
      return 'macOS denies keychain access to this build (-34018) because it '
          'is signed ad-hoc.';
    }
    if (text.contains('-25291')) {
      return 'No keychain is available (-25291).';
    }
    if (text.contains('-25308')) {
      return 'The keychain is locked (-25308).';
    }
    if (text.contains('-25300')) {
      return 'Keychain item not found (-25300).';
    }
    final lower = text.toLowerCase();
    if (lower.contains('libsecret') || lower.contains('secret service')) {
      return 'No secret service on this Linux session — install libsecret and '
          'run a keyring daemon.';
    }
    if (lower.contains('missingpluginexception')) {
      return 'This platform has no keystore plugin.';
    }
    return text;
  }

  /// Shown in the log only: the user cannot act on it, the developer can.
  static String? _developerHint(Object error) {
    if (!'$error'.contains('-34018')) return null;
    return 'to enable auto-unlock locally, pick a signing Team and add '
        'Keychain Sharing in Xcode — see the comment in '
        'macos/Runner/DebugProfile.entitlements.';
  }
}

class AutoLoginException implements Exception {
  const AutoLoginException(this.message);
  final String message;
  @override
  String toString() => message;
}
