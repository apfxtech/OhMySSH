import 'package:flutter/material.dart';

import '../../components/cardlist.dart';
import '../../components/icon.dart';
import '../../ssh/probe.dart';
import '../../ssh/session.dart';
import '../../theme/theme.dart';
import 'sftp_view.dart' show formatBytes;

Future<void> showSessionInfoDialog(BuildContext context, HostSession session) {
  final colors = context.appColors;
  return showDialog<void>(
    context: context,
    barrierColor: colors.dialogBarrier,
    builder: (_) => _InfoDialog(session: session),
  );
}

class _InfoDialog extends StatefulWidget {
  const _InfoDialog({required this.session});

  final HostSession session;

  @override
  State<_InfoDialog> createState() => _InfoDialogState();
}

class _InfoDialogState extends State<_InfoDialog> {
  bool _refreshing = false;

  Future<void> _refresh() async {
    setState(() => _refreshing = true);
    try {
      await widget.session.refreshProfile();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Probe failed: $error')));
      }
    } finally {
      if (mounted) setState(() => _refreshing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final session = widget.session;
    final media = MediaQuery.of(context).size;

    return AnimatedBuilder(
      animation: session,
      builder: (context, _) {
        final profile = session.profile;
        final metrics = profile?.metrics;

        return Dialog(
          backgroundColor: colors.background,
          insetPadding: const EdgeInsets.symmetric(
            horizontal: 20,
            vertical: 40,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxWidth: 460,
              maxHeight: media.height * 0.8,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(18, 16, 8, 8),
                  child: Row(
                    children: [
                      QIconBadge.svg(
                        asset: osIconAsset(profile?.osId),
                        color: Color(osColorValue(profile?.osId)),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              profile?.osPretty ?? 'Unknown system',
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                color: colors.textPrimary,
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              session.host.endpoint,
                              style: TextStyle(
                                color: colors.textMuted,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        tooltip: 'Refresh',
                        onPressed: _refreshing ? null : _refresh,
                        icon: _refreshing
                            ? SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: colors.accent,
                                ),
                              )
                            : Icon(
                                Icons.refresh,
                                color: colors.textSecondary,
                                size: 18,
                              ),
                      ),
                      IconButton(
                        tooltip: 'Close',
                        onPressed: () => Navigator.of(context).pop(),
                        icon: Icon(
                          Icons.close,
                          color: colors.textMuted,
                          size: 18,
                        ),
                      ),
                    ],
                  ),
                ),
                Flexible(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.only(bottom: 18),
                    child: Column(
                      children: [
                        if (metrics != null) ...[
                          _MetricGrid(metrics: metrics),
                          const SizedBox(height: 14),
                        ],
                        GroupedCardList<_Row>(
                          title: 'Details',
                          items: _details(session, profile),
                          itemBuilder: (context, row) => Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              SizedBox(
                                width: 92,
                                child: Text(
                                  row.label,
                                  style: TextStyle(
                                    color: colors.textMuted,
                                    fontSize: 12.5,
                                  ),
                                ),
                              ),
                              Expanded(
                                child: SelectableText(
                                  row.value,
                                  style: TextStyle(
                                    color: colors.textPrimary,
                                    fontSize: 12.5,
                                    fontFamily: row.mono ? 'monospace' : null,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  List<_Row> _details(HostSession session, HostProfile? profile) {
    final metrics = profile?.metrics;
    return [
      if (profile?.hostname != null) _Row('Hostname', profile!.hostname!),
      _Row('Address', session.host.endpoint),
      if (session.identity != null) _Row('User', session.identity!.username),
      if (profile?.kernel != null) _Row('Kernel', profile!.kernel!),
      if (profile?.arch != null) _Row('Architecture', profile!.arch!),
      if (metrics?.cpuCount != null) _Row('CPUs', '${metrics!.cpuCount}'),
      if (metrics?.uptime != null) _Row('Uptime', _uptime(metrics!.uptime!)),
      if (session.fingerprint != null)
        _Row('Host key', session.fingerprint!, mono: true),
    ];
  }

  static String _uptime(Duration duration) {
    final days = duration.inDays;
    final hours = duration.inHours % 24;
    final minutes = duration.inMinutes % 60;
    if (days > 0) return '${days}d ${hours}h';
    if (hours > 0) return '${hours}h ${minutes}m';
    return '${minutes}m';
  }
}

class _Row {
  const _Row(this.label, this.value, {this.mono = false});
  final String label;
  final String value;
  final bool mono;
}

class _MetricGrid extends StatelessWidget {
  const _MetricGrid({required this.metrics});

  final HostMetrics metrics;

  @override
  Widget build(BuildContext context) {
    final tiles = <Widget>[
      if (metrics.load1 != null)
        _MetricTile(
          icon: Icons.speed,
          label: 'Load avg',
          value: metrics.load1!.toStringAsFixed(2),
          caption:
              '${metrics.load5?.toStringAsFixed(2) ?? '—'} · '
              '${metrics.load15?.toStringAsFixed(2) ?? '—'}',
          ratio: metrics.cpuCount == null
              ? null
              : (metrics.load1! / metrics.cpuCount!).clamp(0.0, 1.0),
        )
      else if (metrics.cpuPercent != null)
        _MetricTile(
          icon: Icons.speed,
          label: 'CPU',
          value: '${metrics.cpuPercent!.toStringAsFixed(0)}%',
          ratio: (metrics.cpuPercent! / 100).clamp(0.0, 1.0),
        ),
      if (metrics.memTotalKb != null)
        _MetricTile(
          icon: Icons.memory,
          label: 'Memory',
          value: metrics.memUsedRatio == null
              ? formatBytes(metrics.memTotalKb! * 1024)
              : '${(metrics.memUsedRatio! * 100).toStringAsFixed(0)}%',
          caption: formatBytes(metrics.memTotalKb! * 1024),
          ratio: metrics.memUsedRatio,
        ),
      if (metrics.diskTotalKb != null)
        _MetricTile(
          icon: Icons.storage,
          label: 'Disk /',
          value: metrics.diskUsedRatio == null
              ? formatBytes(metrics.diskTotalKb! * 1024)
              : '${(metrics.diskUsedRatio! * 100).toStringAsFixed(0)}%',
          caption: '${formatBytes((metrics.diskFreeKb ?? 0) * 1024)} free',
          ratio: metrics.diskUsedRatio,
        ),
    ];

    if (tiles.isEmpty) return const SizedBox.shrink();

    return GroupedCardGrid<Widget>(
      title: 'Live',
      items: tiles,
      crossAxisCount: tiles.length.clamp(1, 3),
      mainAxisExtent: null,
      cardPadding: const EdgeInsets.all(12),
      itemBuilder: (context, tile) => tile,
    );
  }
}

class _MetricTile extends StatelessWidget {
  const _MetricTile({
    required this.icon,
    required this.label,
    required this.value,
    this.caption,
    this.ratio,
  });

  final IconData icon;
  final String label;
  final String value;
  final String? caption;
  final double? ratio;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final level = ratio ?? 0;
    final tint = level > 0.9
        ? colors.danger
        : level > 0.7
        ? colors.warning
        : colors.accent;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          children: [
            Icon(icon, color: colors.textMuted, size: 14),
            const SizedBox(width: 6),
            Text(
              label,
              style: TextStyle(color: colors.textMuted, fontSize: 11),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          value,
          style: TextStyle(
            color: colors.textPrimary,
            fontSize: 19,
            height: 1.1,
            fontWeight: FontWeight.w700,
          ),
        ),
        if (caption != null) ...[
          const SizedBox(height: 2),
          Text(
            caption!,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(color: colors.textMuted, fontSize: 10.5),
          ),
        ],
        if (ratio != null) ...[
          const SizedBox(height: 8),
          ClipRRect(
            borderRadius: BorderRadius.circular(3),
            child: LinearProgressIndicator(
              value: ratio,
              minHeight: 3,
              color: tint,
              backgroundColor: colors.divider,
            ),
          ),
        ],
      ],
    );
  }
}
