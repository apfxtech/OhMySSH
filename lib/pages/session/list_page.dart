import 'package:flutter/material.dart';

import '../../components/appbar.dart';
import '../../components/cardlist.dart';
import '../../components/icon.dart';
import '../../ssh/manager.dart';
import '../../ssh/probe.dart';
import '../../ssh/session.dart';
import '../../theme/theme.dart';
import '../../widgets/fields.dart';
import 'page.dart';

class SessionsListPage extends StatelessWidget {
  const SessionsListPage({super.key});

  @override
  Widget build(BuildContext context) {
    final manager = SessionManager.instance;
    final colors = context.appColors;

    return AnimatedBuilder(
      animation: manager,
      builder: (context, _) {
        final sessions = manager.sessions;

        return Scaffold(
          backgroundColor: colors.background,
          appBar: QPageAppBar(
            title: 'Sessions',
            subtitle: sessions.isEmpty ? null : '${sessions.length} open',
            actions: [
              if (sessions.isNotEmpty)
                QPageAppBarAction(
                  tooltip: 'Close all',
                  icon: QIcon(
                    asset: 'assets/ic/action/disconnect.svg',
                    color: colors.onAccent,
                    size: 20,
                  ),
                  onPressed: () async {
                    final confirmed = await confirmDestructive(
                      context,
                      title: 'Close all sessions?',
                      message: 'Every open connection will be dropped.',
                      actionLabel: 'Close all',
                    );
                    if (confirmed) await manager.closeAll();
                  },
                ),
            ],
          ),
          body: sessions.isEmpty
              ? const QEmptyView(
                  icon: 'assets/ic/nav/terminal.svg',
                  title: 'Nothing open',
                  message: 'Connect to a system to start a session.',
                )
              : SingleChildScrollView(
                  padding: const EdgeInsets.only(top: 14, bottom: 20),
                  child: GroupedCardList<HostSession>(
                    items: sessions,
                    onTap: (session) => () => Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => SessionPage(sessionId: session.id),
                      ),
                    ),
                    itemBuilder: (context, session) =>
                        _SessionRow(session: session),
                  ),
                ),
        );
      },
    );
  }
}

class _SessionRow extends StatelessWidget {
  const _SessionRow({required this.session});

  final HostSession session;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final osId = session.profile?.osId ?? session.host.osId;

    final (statusColor, statusText) = switch (session.state) {
      SessionState.connected => (colors.success, 'Connected'),
      SessionState.connecting => (colors.warning, 'Connecting…'),
      SessionState.failed => (colors.danger, 'Failed'),
      SessionState.closed => (colors.textMuted, 'Disconnected'),
      SessionState.idle => (colors.textMuted, 'Idle'),
    };

    return Row(
      children: [
        QIconBadge(asset: osIconAsset(osId), color: Color(osColorValue(osId))),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                session.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: colors.textPrimary,
                  fontSize: 14.5,
                  height: 1.2,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 2),
              Row(
                children: [
                  Container(
                    width: 6,
                    height: 6,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: statusColor,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Flexible(
                    child: Text(
                      '$statusText · ${session.host.endpoint}'
                      '${session.sftpTabOpen ? ' · SFTP' : ''}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: colors.textMuted,
                        fontSize: 12,
                        height: 1.2,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        IconButton(
          tooltip: 'Close',
          onPressed: () => SessionManager.instance.close(session.id),
          icon: QIcon(
            asset: 'assets/ic/action/close.svg',
            color: colors.textMuted,
            size: 16,
          ),
        ),
      ],
    );
  }
}
