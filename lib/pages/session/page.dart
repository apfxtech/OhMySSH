import 'package:flutter/material.dart';

import '../../components/appbar.dart';
import '../../components/icon.dart';
import '../../ssh/manager.dart';
import '../../ssh/session.dart';
import '../../theme/theme.dart';
import '../../widgets/fields.dart';
import 'connect_view.dart';
import 'info_sheet.dart';
import 'sftp_view.dart';
import 'terminal_view.dart';

enum TabMode { terminal, sftp }

class _Tab {
  const _Tab(this.session, this.mode);

  final HostSession session;
  final TabMode mode;

  String get key => '${session.id}:${mode.name}';
}

class SessionPage extends StatefulWidget {
  const SessionPage({super.key, required this.sessionId});

  final String sessionId;

  @override
  State<SessionPage> createState() => _SessionPageState();
}

class _SessionPageState extends State<SessionPage> {
  final _manager = SessionManager.instance;
  late String _activeKey;

  @override
  void initState() {
    super.initState();
    _activeKey = '${widget.sessionId}:${TabMode.terminal.name}';
    _manager.activate(widget.sessionId);
  }

  List<_Tab> get _tabs => [
    for (final session in _manager.sessions) ...[
      _Tab(session, TabMode.terminal),
      if (session.sftpTabOpen) _Tab(session, TabMode.sftp),
    ],
  ];

  _Tab? _resolveActive(List<_Tab> tabs) {
    for (final tab in tabs) {
      if (tab.key == _activeKey) return tab;
    }
    return tabs.isEmpty ? null : tabs.first;
  }

  void _select(_Tab tab) {
    setState(() => _activeKey = tab.key);
    _manager.activate(tab.session.id);
  }

  Future<void> _closeTab(_Tab tab) async {
    if (tab.mode == TabMode.sftp) {
      tab.session.sftpTabOpen = false;
      if (_activeKey == tab.key) {
        setState(() => _activeKey = '${tab.session.id}:${TabMode.terminal.name}');
      }
      return;
    }

    final confirmed = tab.session.isConnected
        ? await confirmDestructive(
            context,
            title: 'Close session?',
            message: 'The connection to ${tab.session.title} will be dropped.',
            actionLabel: 'Close',
          )
        : true;
    if (!confirmed) return;

    await _manager.close(tab.session.id);
    if (!mounted) return;
    if (_manager.sessions.isEmpty) {
      Navigator.of(context).pop();
    } else {
      setState(() => _activeKey = _tabs.first.key);
    }
  }

  void _openSftp(HostSession session) {
    session.sftpTabOpen = true;
    setState(() => _activeKey = '${session.id}:${TabMode.sftp.name}');
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;

    return AnimatedBuilder(
      animation: _manager,
      builder: (context, _) {
        final tabs = _tabs;
        final active = _resolveActive(tabs);

        if (active == null) {
          return Scaffold(
            backgroundColor: colors.background,
            appBar: const QPageAppBar(title: 'Sessions'),
            body: const QEmptyView(
              icon: 'assets/ic/nav/terminal.svg',
              title: 'No open sessions',
              message: 'Tap a system to connect.',
            ),
          );
        }

        final session = active.session;

        return Scaffold(
          backgroundColor: colors.background,
          appBar: QPageAppBar(
            title: session.title,
            subtitle: _subtitleFor(session),
            statusColor: _statusColor(colors, session),
            actions: [
              // A dropped session keeps its scrollback, so it stays on the
              // terminal view rather than reverting to the connect screen.
              if (session.state == SessionState.closed)
                QPageAppBarAction(
                  tooltip: 'Reconnect',
                  icon: QIcon(
                    asset: 'assets/ic/action/connect.svg',
                    color: colors.onAccent,
                    size: 20,
                  ),
                  onPressed: session.connect,
                ),
              if (session.isConnected && active.mode == TabMode.terminal)
                QPageAppBarAction(
                  tooltip: 'Open SFTP',
                  icon: QIcon(
                    asset: 'assets/ic/nav/sftp.svg',
                    color: colors.onAccent,
                    size: 20,
                  ),
                  onPressed: () => _openSftp(session),
                ),
              QPageAppBarAction(
                tooltip: 'System info',
                icon: QIcon(
                  asset: 'assets/ic/state/cpu.svg',
                  color: colors.onAccent,
                  size: 20,
                ),
                onPressed: () => showSessionInfoSheet(context, session),
              ),
            ],
            bottom: _TabStrip(
              tabs: tabs,
              activeKey: active.key,
              onSelect: _select,
              onClose: _closeTab,
            ),
          ),
          // Keyed so switching tabs does not tear down the terminal or reset
          // the SFTP listing.
          body: IndexedStack(
            index: tabs.indexWhere((t) => t.key == active.key),
            children: [
              for (final tab in tabs)
                KeyedSubtree(key: ValueKey(tab.key), child: _body(tab)),
            ],
          ),
        );
      },
    );
  }

  Widget _body(_Tab tab) {
    final session = tab.session;
    return AnimatedBuilder(
      animation: session,
      builder: (context, _) {
        if (session.state == SessionState.connecting ||
            session.state == SessionState.failed ||
            session.state == SessionState.idle) {
          return ConnectView(session: session, onRetry: session.connect);
        }
        return switch (tab.mode) {
          TabMode.terminal => SessionTerminalView(session: session),
          TabMode.sftp => SftpView(session: session),
        };
      },
    );
  }

  String _subtitleFor(HostSession session) => switch (session.state) {
    SessionState.idle => session.host.endpoint,
    SessionState.connecting => 'Connecting…',
    SessionState.connected =>
      session.profile?.osPretty ?? session.host.endpoint,
    SessionState.failed => 'Failed',
    SessionState.closed => 'Disconnected',
  };

  Color _statusColor(QAppColors colors, HostSession session) =>
      switch (session.state) {
        SessionState.connected => colors.success,
        SessionState.connecting => colors.warning,
        SessionState.failed => colors.danger,
        _ => colors.textMuted,
      };
}

class _TabStrip extends StatelessWidget implements PreferredSizeWidget {
  const _TabStrip({
    required this.tabs,
    required this.activeKey,
    required this.onSelect,
    required this.onClose,
  });

  final List<_Tab> tabs;
  final String activeKey;
  final ValueChanged<_Tab> onSelect;
  final ValueChanged<_Tab> onClose;

  @override
  Size get preferredSize => const Size.fromHeight(42);

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return Container(
      height: 42,
      color: colors.card,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 6),
        child: Row(
          children: [
            for (final tab in tabs)
              _TabChip(
                tab: tab,
                selected: tab.key == activeKey,
                onTap: () => onSelect(tab),
                onClose: () => onClose(tab),
              ),
            IconButton(
              tooltip: 'New session',
              onPressed: () => Navigator.of(context).pop(),
              icon: QIcon(
                asset: 'assets/ic/action/add.svg',
                color: colors.textMuted,
                size: 18,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TabChip extends StatelessWidget {
  const _TabChip({
    required this.tab,
    required this.selected,
    required this.onTap,
    required this.onClose,
  });

  final _Tab tab;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final session = tab.session;
    final foreground = selected ? colors.textPrimary : colors.textMuted;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 3, vertical: 5),
      child: Material(
        color: selected ? colors.background : Colors.transparent,
        borderRadius: BorderRadius.circular(9),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(10, 0, 4, 0),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                QIcon(
                  asset: tab.mode == TabMode.sftp
                      ? 'assets/ic/nav/sftp.svg'
                      : 'assets/ic/nav/terminal.svg',
                  color: foreground,
                  size: 15,
                ),
                const SizedBox(width: 7),
                ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 130),
                  child: Text(
                    session.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: foreground,
                      fontSize: 12.5,
                      fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                    ),
                  ),
                ),
                const SizedBox(width: 5),
                Container(
                  width: 6,
                  height: 6,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: session.isConnected
                        ? colors.success
                        : session.state == SessionState.connecting
                        ? colors.warning
                        : colors.textMuted,
                  ),
                ),
                IconButton(
                  tooltip: 'Close',
                  onPressed: onClose,
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints.tightFor(
                    width: 28,
                    height: 28,
                  ),
                  icon: QIcon(
                    asset: 'assets/ic/action/close.svg',
                    color: colors.textMuted,
                    size: 13,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
