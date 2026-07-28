import 'package:flutter/foundation.dart';

import '../data/models.dart';
import '../data/store.dart';
import 'session.dart';

class SessionManager extends ChangeNotifier {
  SessionManager._();

  static final SessionManager instance = SessionManager._();

  final List<HostSession> _sessions = [];
  String? _activeId;

  List<HostSession> get sessions => List.unmodifiable(_sessions);

  bool get hasLiveSessions =>
      _sessions.any((s) => s.state == SessionState.connected);

  HostSession? get active {
    final id = _activeId;
    if (id == null) return null;
    for (final session in _sessions) {
      if (session.id == id) return session;
    }
    return null;
  }

  HostSession? byId(String id) {
    for (final session in _sessions) {
      if (session.id == id) return session;
    }
    return null;
  }

  HostSession open(Host host) {
    final store = VaultStore.instance;
    final session = HostSession(host: host, identity: store.identityFor(host));

    session.onHostKeyPinned = (fingerprint) async {
      final current = _currentHost(host.id);
      if (current == null) return;
      await store.saveHost(current.copyWith(knownHostKey: fingerprint));
    };
    session.onProfiled = (profile) async {
      final current = _currentHost(host.id);
      if (current == null) return;
      await store.saveHost(
        current.copyWith(osId: profile.osId, osPretty: profile.osPretty),
      );
    };

    session.addListener(_onSessionChanged);
    _sessions.add(session);
    _activeId = session.id;
    notifyListeners();

    // Fire and forget: the UI follows the session's own checkpoint stream.
    session.connect();
    return session;
  }

  Host? _currentHost(String id) {
    for (final host in VaultStore.instance.hosts) {
      if (host.id == id) return host;
    }
    return null;
  }

  void activate(String id) {
    if (_activeId == id) return;
    _activeId = id;
    notifyListeners();
  }

  Future<void> close(String id) async {
    final index = _sessions.indexWhere((s) => s.id == id);
    if (index < 0) return;
    final session = _sessions.removeAt(index);
    session.removeListener(_onSessionChanged);

    if (_activeId == id) {
      _activeId = _sessions.isEmpty
          ? null
          : _sessions[index.clamp(0, _sessions.length - 1)].id;
    }
    notifyListeners();

    await session.disconnect();
    session.dispose();
  }

  Future<void> closeAll() async {
    final open = [..._sessions];
    _sessions.clear();
    _activeId = null;
    notifyListeners();
    for (final session in open) {
      session.removeListener(_onSessionChanged);
      await session.disconnect();
      session.dispose();
    }
  }

  void _onSessionChanged() => notifyListeners();
}
