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

  String? get unavailableReason => _unavailableReason;

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
      Log.info(_scope, 'keystore available (write probe passed)');
      return _available = true;
    } catch (error, stackTrace) {
      _unavailableReason = describeFailure(error);
      Log.error(_scope, 'keystore probe failed: $error', stackTrace);
      Log.warn(_scope, 'auto-unlock disabled: $_unavailableReason');
      return _available = false;
    }
  }

  Future<bool> isEnabled() async {
    try {
      return await _storage.containsKey(key: _key);
    } catch (error, stackTrace) {
      Log.error(_scope, 'containsKey failed', stackTrace);
      Log.error(_scope, error);
      return false;
    }
  }

  Future<String?> readPassword() async {
    try {
      return await _storage.read(key: _key);
    } catch (error, stackTrace) {
      Log.error(_scope, 'read failed: $error', stackTrace);
      return null;
    }
  }

  Future<void> enable(String password) async {
    try {
      await _storage.write(key: _key, value: password);
      Log.info(_scope, 'auto-unlock enabled');
    } catch (error, stackTrace) {
      Log.error(_scope, 'write failed: $error', stackTrace);
      throw AutoLoginException(describeFailure(error));
    }
  }

  /// Never throws: revoking must always succeed from the user's side.
  Future<void> disable() async {
    try {
      await _storage.delete(key: _key);
      Log.info(_scope, 'auto-unlock disabled');
    } catch (error, stackTrace) {
      Log.error(_scope, 'delete failed: $error', stackTrace);
    }
  }

  static String describeFailure(Object error) {
    final text = '$error';
    if (text.contains('-34018')) {
      return 'macOS keychain refused access (-34018). This build is signed '
          'ad-hoc; pick a signing Team and add Keychain Sharing in Xcode — '
          'see the comment in macos/Runner/DebugProfile.entitlements.';
    }
    if (text.contains('-25291')) {
      return 'No keychain is available (-25291).';
    }
    if (text.contains('-25300')) {
      return 'Keychain item not found (-25300).';
    }
    if (text.toLowerCase().contains('libsecret') ||
        text.toLowerCase().contains('secret service')) {
      return 'No secret service on this Linux session — install libsecret and '
          'run a keyring daemon.';
    }
    return text;
  }
}

class AutoLoginException implements Exception {
  const AutoLoginException(this.message);
  final String message;
  @override
  String toString() => message;
}
