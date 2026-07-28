import 'package:flutter/material.dart';

import '../../components/appbar.dart';
import '../../components/cardlist.dart';
import '../../components/icon.dart';
import '../../data/models.dart';
import '../../data/store.dart';
import '../../ssh/manager.dart';
import '../../ssh/probe.dart';
import '../../theme/theme.dart';
import '../../widgets/fields.dart';
import '../session/page.dart';
import 'editor.dart';

class HostsPage extends StatelessWidget {
  const HostsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final store = VaultStore.instance;
    final colors = context.appColors;

    return AnimatedBuilder(
      animation: store,
      builder: (context, _) {
        final buckets = store.hostsByGroup();

        return Scaffold(
          backgroundColor: colors.background,
          appBar: QPageAppBar(
            title: 'Systems',
            subtitle: '${store.hosts.length} saved',
            actions: [
              QPageAppBarAction(
                tooltip: 'New group',
                icon: Icon(
                  Icons.create_new_folder_outlined,
                  color: colors.onAccent,
                  size: 20,
                ),
                onPressed: () => _createGroup(context),
              ),
              QPageAppBarAction(
                tooltip: 'New system',
                icon: Icon(Icons.add, color: colors.onAccent, size: 20),
                onPressed: () => _openEditor(context, null),
              ),
            ],
          ),
          body: buckets.isEmpty
              ? QEmptyView(
                  icon: Icons.dns_outlined,
                  title: 'No systems yet',
                  message:
                      'Add a system to connect over SSH and browse it over SFTP.',
                  action: FilledButton(
                    onPressed: () => _openEditor(context, null),
                    style: FilledButton.styleFrom(
                      backgroundColor: colors.accent,
                      foregroundColor: colors.onAccent,
                    ),
                    child: const Text('Add system'),
                  ),
                )
              : SingleChildScrollView(
                  padding: const EdgeInsets.only(top: 9, bottom: 20),
                  child: Column(
                    children: [
                      for (final entry in buckets.entries)
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 5),
                          child: GroupedCardList<Host>(
                            title: entry.key?.name ?? 'Ungrouped',
                            items: entry.value,
                            onTap: (host) =>
                                () => _connect(context, host),
                            itemBuilder: (context, host) =>
                                _HostRow(host: host),
                          ),
                        ),
                    ],
                  ),
                ),
        );
      },
    );
  }

  void _connect(BuildContext context, Host host) {
    if (VaultStore.instance.identityFor(host) == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Assign a user to this system first'),
          action: SnackBarAction(
            label: 'Edit',
            onPressed: () => _openEditor(context, host),
          ),
        ),
      );
      return;
    }
    final session = SessionManager.instance.open(host);
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => SessionPage(sessionId: session.id)),
    );
  }

  Future<void> _openEditor(BuildContext context, Host? host) => Navigator.of(
    context,
  ).push<void>(MaterialPageRoute(builder: (_) => HostEditorPage(host: host)));

  Future<void> _createGroup(BuildContext context) async {
    final name = await promptForText(
      context,
      title: 'New group',
      label: 'Group name',
      actionLabel: 'Create',
    );
    if (name == null || name.isEmpty) return;
    await VaultStore.instance.saveGroup(HostGroup(id: newId(), name: name));
  }
}

class _HostRow extends StatelessWidget {
  const _HostRow({required this.host});

  final Host host;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final identity = VaultStore.instance.identityFor(host);
    final subtitle = identity == null
        ? host.endpoint
        : '${identity.username}@${host.endpoint}';

    return Row(
      children: [
        QIconBadge.svg(
          asset: osIconAsset(host.osId),
          color: Color(osColorValue(host.osId)),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                host.displayLabel,
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
              Text(
                subtitle,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: colors.textMuted,
                  fontSize: 12,
                  height: 1.2,
                ),
              ),
            ],
          ),
        ),
        _EditButton(host: host),
        Padding(
          padding: const EdgeInsets.only(left: 2),
          child: Icon(Icons.chevron_right, color: colors.textMuted, size: 16),
        ),
      ],
    );
  }
}

class _EditButton extends StatelessWidget {
  const _EditButton({required this.host});

  final Host host;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return IconButton(
      tooltip: 'Edit',
      onPressed: () => Navigator.of(context).push<void>(
        MaterialPageRoute(builder: (_) => HostEditorPage(host: host)),
      ),
      icon: Icon(Icons.edit_outlined, color: colors.textMuted, size: 18),
    );
  }
}
