import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:dartssh2/dartssh2.dart';
import 'package:flutter/foundation.dart';
import 'package:xterm/xterm.dart';

import '../data/models.dart';
import 'probe.dart';

enum ConnectStage {
  credentials('Loading credentials'),
  tcp('Connecting'),
  handshake('Key exchange'),
  hostKey('Verifying host key'),
  auth('Authenticating'),
  shell('Opening shell'),
  probe('Reading system info');

  const ConnectStage(this.label);
  final String label;
}

enum StageStatus { waiting, running, done, failed, skipped }

class Checkpoint {
  const Checkpoint(this.stage, this.status, [this.detail]);

  final ConnectStage stage;
  final StageStatus status;
  final String? detail;
}

enum SessionState { idle, connecting, connected, failed, closed }

/// Raised when the pinned host key no longer matches. Clearing the pin is an
/// explicit action in the host editor, never a prompt in the connect flow.
class HostKeyMismatch implements Exception {
  const HostKeyMismatch({required this.expected, required this.actual});

  final String expected;
  final String actual;

  @override
  String toString() =>
      'Host key changed.\nPinned:  $expected\nOffered: $actual';
}

class HostSession extends ChangeNotifier {
  HostSession({required this.host, required this.identity})
    : id = newId(),
      terminal = Terminal(maxLines: 10000, platform: _localPlatform());

  final String id;
  final Host host;
  final Identity? identity;
  final Terminal terminal;

  SSHClient? _client;
  SSHSession? _shell;
  SftpClient? _sftp;
  HostProfile? _profile;
  String? _fingerprint;

  SessionState _state = SessionState.idle;
  Object? _error;

  bool _sftpTabOpen = false;

  bool get sftpTabOpen => _sftpTabOpen;

  set sftpTabOpen(bool value) {
    if (_sftpTabOpen == value) return;
    _sftpTabOpen = value;
    notifyListeners();
  }

  final Map<ConnectStage, Checkpoint> _checkpoints = {
    for (final stage in ConnectStage.values)
      stage: Checkpoint(stage, StageStatus.waiting),
  };

  /// Called when the pinned key differs. Returning true re-pins and continues.
  Future<bool> Function(String expected, String actual)? onHostKeyMismatch;

  Future<void> Function(String fingerprint)? onHostKeyPinned;

  Future<void> Function(HostProfile profile)? onProfiled;

  SessionState get state => _state;
  Object? get error => _error;
  HostProfile? get profile => _profile;
  String? get fingerprint => _fingerprint;
  SSHClient? get client => _client;
  bool get isConnected => _state == SessionState.connected;

  List<Checkpoint> get checkpoints =>
      ConnectStage.values.map((s) => _checkpoints[s]!).toList(growable: false);

  String get title => host.displayLabel;

  void _mark(ConnectStage stage, StageStatus status, [String? detail]) {
    _checkpoints[stage] = Checkpoint(stage, status, detail);
    notifyListeners();
  }

  Future<void> connect() async {
    if (_state == SessionState.connecting || _state == SessionState.connected) {
      return;
    }
    _state = SessionState.connecting;
    _error = null;
    for (final stage in ConnectStage.values) {
      _checkpoints[stage] = Checkpoint(stage, StageStatus.waiting);
    }
    notifyListeners();

    try {
      final auth = await _prepareCredentials();
      final socket = await _openSocket();
      await _startClient(socket, auth);
      await _openShell();
      await _runProbe();

      _state = SessionState.connected;
      notifyListeners();
    } catch (error) {
      _error = error;
      _state = SessionState.failed;
      for (final stage in ConnectStage.values) {
        if (_checkpoints[stage]!.status == StageStatus.running) {
          _checkpoints[stage] = Checkpoint(
            stage,
            StageStatus.failed,
            _describe(error),
          );
        }
      }
      notifyListeners();
      await _teardown();
    }
  }

  Future<_Credentials> _prepareCredentials() async {
    _mark(ConnectStage.credentials, StageStatus.running);
    final id = identity;
    if (id == null || id.username.isEmpty) {
      throw const SessionError('No user assigned to this system');
    }

    if (id.kind == AuthKind.privateKey) {
      final pem = id.privateKey;
      if (pem == null || pem.trim().isEmpty) {
        throw const SessionError('Identity has no private key');
      }
      if (SSHKeyPair.isEncryptedPem(pem) &&
          (id.passphrase == null || id.passphrase!.isEmpty)) {
        throw const SessionError('Private key is encrypted but has no passphrase');
      }
      final List<SSHKeyPair> keys;
      try {
        keys = SSHKeyPair.fromPem(pem, id.passphrase);
      } catch (error) {
        throw SessionError('Private key could not be read: ${_describe(error)}');
      }
      _mark(
        ConnectStage.credentials,
        StageStatus.done,
        '${id.username} · key (${keys.length} in file)',
      );
      return _Credentials(username: id.username, keys: keys);
    }

    _mark(ConnectStage.credentials, StageStatus.done, '${id.username} · password');
    return _Credentials(username: id.username, password: id.password ?? '');
  }

  Future<SSHSocket> _openSocket() async {
    _mark(ConnectStage.tcp, StageStatus.running, host.endpoint);
    try {
      final socket = await SSHSocket.connect(
        host.hostname,
        host.port,
        timeout: const Duration(seconds: 15),
      );
      _mark(ConnectStage.tcp, StageStatus.done, host.endpoint);
      return socket;
    } on SocketException catch (error) {
      throw SessionError(error.osError?.message ?? error.message);
    }
  }

  Future<void> _startClient(SSHSocket socket, _Credentials auth) async {
    _mark(ConnectStage.handshake, StageStatus.running);

    // Buffers the rejection reason: throwing out of the verify callback would
    // surface as a generic transport error.
    Object? verifyFailure;

    final client = SSHClient(
      socket,
      username: auth.username,
      identities: auth.keys,
      onPasswordRequest: auth.keys == null ? () => auth.password ?? '' : null,
      keepAliveInterval: const Duration(seconds: 15),
      handshakeTimeout: const Duration(seconds: 20),
      authTimeout: const Duration(seconds: 30),
      onVerifyHostKey: (type, fingerprint) async {
        _mark(ConnectStage.handshake, StageStatus.done, type);
        _mark(ConnectStage.hostKey, StageStatus.running);

        final offered = utf8.decode(fingerprint, allowMalformed: true);
        _fingerprint = offered;
        final pinned = host.knownHostKey;

        if (pinned == null || pinned.isEmpty) {
          await onHostKeyPinned?.call(offered);
          _mark(ConnectStage.hostKey, StageStatus.done, 'pinned $offered');
          return true;
        }
        if (pinned == offered) {
          _mark(ConnectStage.hostKey, StageStatus.done, offered);
          return true;
        }

        final accept =
            await onHostKeyMismatch?.call(pinned, offered) ?? false;
        if (accept) {
          await onHostKeyPinned?.call(offered);
          _mark(ConnectStage.hostKey, StageStatus.done, 're-pinned $offered');
          return true;
        }
        verifyFailure = HostKeyMismatch(expected: pinned, actual: offered);
        _mark(ConnectStage.hostKey, StageStatus.failed, 'key changed');
        return false;
      },
    );
    _client = client;

    _mark(ConnectStage.auth, StageStatus.running, auth.username);
    try {
      await client.authenticated;
    } catch (error) {
      // A rejected host key tears the transport down; report that, not the
      // downstream auth error it produces.
      if (verifyFailure != null) throw verifyFailure!;
      rethrow;
    }
    _mark(ConnectStage.auth, StageStatus.done, auth.username);

    unawaited(
      client.done.then((_) => _handleClosed(), onError: (_) => _handleClosed()),
    );
  }

  Future<void> _openShell() async {
    _mark(ConnectStage.shell, StageStatus.running);
    final client = _client!;
    final shell = await client.shell(
      pty: SSHPtyConfig(
        type: 'xterm-256color',
        width: terminal.viewWidth,
        height: terminal.viewHeight,
      ),
    );
    _shell = shell;

    terminal.onOutput = (data) {
      shell.write(Uint8List.fromList(utf8.encode(data)));
    };
    terminal.onResize = (width, height, pixelWidth, pixelHeight) {
      shell.resizeTerminal(width, height, pixelWidth, pixelHeight);
    };

    shell.stdout
        .cast<List<int>>()
        .transform(const Utf8Decoder(allowMalformed: true))
        .listen(terminal.write);
    shell.stderr
        .cast<List<int>>()
        .transform(const Utf8Decoder(allowMalformed: true))
        .listen(terminal.write);

    _mark(ConnectStage.shell, StageStatus.done);
  }

  /// Non-fatal: a failed probe downgrades to "skipped" rather than killing the
  /// session.
  Future<void> _runProbe() async {
    _mark(ConnectStage.probe, StageStatus.running);
    try {
      final profile = await probeHost(_client!).timeout(
        const Duration(seconds: 20),
      );
      _profile = profile;
      await onProfiled?.call(profile);
      _mark(ConnectStage.probe, StageStatus.done, profile.osPretty);
    } catch (error) {
      _mark(ConnectStage.probe, StageStatus.skipped, _describe(error));
    }
  }

  Future<void> refreshProfile() async {
    final client = _client;
    if (client == null || _state != SessionState.connected) return;
    final profile = await probeHost(client).timeout(const Duration(seconds: 20));
    _profile = profile;
    await onProfiled?.call(profile);
    notifyListeners();
  }

  Future<SftpClient> sftp() async {
    final existing = _sftp;
    if (existing != null) return existing;
    final client = _client;
    if (client == null) throw const SessionError('Not connected');
    final sftp = await client.sftp();
    _sftp = sftp;
    return sftp;
  }

  void _handleClosed() {
    if (_state == SessionState.closed || _state == SessionState.failed) return;
    _state = SessionState.closed;
    notifyListeners();
  }

  Future<void> _teardown() async {
    try {
      _shell?.close();
    } catch (_) {}
    try {
      await _sftp?.close();
    } catch (_) {}
    try {
      _client?.close();
    } catch (_) {}
    _shell = null;
    _sftp = null;
    _client = null;
  }

  Future<void> disconnect() async {
    await _teardown();
    if (_state != SessionState.failed) _state = SessionState.closed;
    notifyListeners();
  }

  @override
  void dispose() {
    // Detach terminal callbacks first: a late resize event would otherwise
    // write to a shell that is already gone.
    terminal.onOutput = null;
    terminal.onResize = null;
    _teardown();
    super.dispose();
  }

  static TerminalTargetPlatform _localPlatform() {
    if (Platform.isAndroid) return TerminalTargetPlatform.android;
    if (Platform.isIOS) return TerminalTargetPlatform.ios;
    if (Platform.isMacOS) return TerminalTargetPlatform.macos;
    if (Platform.isWindows) return TerminalTargetPlatform.windows;
    if (Platform.isLinux) return TerminalTargetPlatform.linux;
    return TerminalTargetPlatform.unknown;
  }
}

class _Credentials {
  const _Credentials({required this.username, this.password, this.keys});

  final String username;
  final String? password;
  final List<SSHKeyPair>? keys;
}

class SessionError implements Exception {
  const SessionError(this.message);
  final String message;
  @override
  String toString() => message;
}

String _describe(Object error) {
  if (error is SessionError) return error.message;
  if (error is HostKeyMismatch) return error.toString();
  if (error is SSHAuthFailError) return 'Authentication failed';
  if (error is SSHAuthAbortError) return 'Authentication aborted';
  if (error is SocketException) {
    return error.osError?.message ?? error.message;
  }
  if (error is TimeoutException) return 'Timed out';
  return error.toString();
}
