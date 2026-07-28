import 'package:flutter/material.dart';

import '../../ssh/session.dart';
import '../../theme/theme.dart';

class ConnectView extends StatelessWidget {
  const ConnectView({super.key, required this.session, required this.onRetry});

  final HostSession session;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final failed = session.state == SessionState.failed;

    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                session.host.displayLabel,
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: colors.textPrimary,
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                session.host.endpoint,
                textAlign: TextAlign.center,
                style: TextStyle(color: colors.textMuted, fontSize: 13),
              ),
              const SizedBox(height: 28),
              for (final checkpoint in session.checkpoints)
                _CheckpointRow(checkpoint: checkpoint),
              if (failed) ...[
                const SizedBox(height: 24),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: colors.danger.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: SelectableText(
                    '${session.error}',
                    style: TextStyle(
                      color: colors.danger,
                      fontSize: 12.5,
                      height: 1.35,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  height: 44,
                  child: FilledButton(
                    onPressed: onRetry,
                    style: FilledButton.styleFrom(
                      backgroundColor: colors.accent,
                      foregroundColor: colors.onAccent,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: const Text('Try again'),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _CheckpointRow extends StatelessWidget {
  const _CheckpointRow({required this.checkpoint});

  final Checkpoint checkpoint;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final (color, label) = switch (checkpoint.status) {
      StageStatus.waiting => (colors.textMuted, colors.textMuted),
      StageStatus.running => (colors.accent, colors.textPrimary),
      StageStatus.done => (colors.success, colors.textSecondary),
      StageStatus.failed => (colors.danger, colors.danger),
      StageStatus.skipped => (colors.warning, colors.textMuted),
    };

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 7),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 22,
            height: 22,
            child: Center(
              child: _StatusMark(status: checkpoint.status, color: color),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  checkpoint.stage.label,
                  style: TextStyle(
                    color: label,
                    fontSize: 14,
                    height: 1.25,
                    fontWeight: checkpoint.status == StageStatus.running
                        ? FontWeight.w700
                        : FontWeight.w500,
                  ),
                ),
                if (checkpoint.detail != null) ...[
                  const SizedBox(height: 2),
                  Text(
                    checkpoint.detail!,
                    style: TextStyle(
                      color: colors.textMuted,
                      fontSize: 11.5,
                      height: 1.3,
                      fontFamily: 'monospace',
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _StatusMark extends StatelessWidget {
  const _StatusMark({required this.status, required this.color});

  final StageStatus status;
  final Color color;

  @override
  Widget build(BuildContext context) {
    switch (status) {
      case StageStatus.running:
        return SizedBox(
          width: 16,
          height: 16,
          child: CircularProgressIndicator(strokeWidth: 2, color: color),
        );
      case StageStatus.waiting:
        return Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(shape: BoxShape.circle, color: color),
        );
      case StageStatus.done:
        return Icon(Icons.check_circle, color: color, size: 18);
      case StageStatus.failed:
        return Icon(Icons.error_outline, color: color, size: 18);
      case StageStatus.skipped:
        return Icon(Icons.warning_amber_rounded, color: color, size: 18);
    }
  }
}
