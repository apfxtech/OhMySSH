import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../components/appbar.dart';
import '../../components/cardlist.dart';
import '../../components/icon.dart';
import '../../data/models.dart';
import '../../data/store.dart';
import '../../theme/theme.dart';
import '../../widgets/fields.dart';

class HostEditorPage extends StatefulWidget {
  const HostEditorPage({super.key, this.host});

  final Host? host;

  @override
  State<HostEditorPage> createState() => _HostEditorPageState();
}

class _HostEditorPageState extends State<HostEditorPage> {
  late final TextEditingController _label;
  late final TextEditingController _hostname;
  late final TextEditingController _port;
  late final TextEditingController _note;

  String? _identityId;
  String? _groupId;
  String? _knownHostKey;

  bool get _isNew => widget.host == null;

  @override
  void initState() {
    super.initState();
    final host = widget.host;
    _label = TextEditingController(text: host?.label ?? '');
    _hostname = TextEditingController(text: host?.hostname ?? '');
    _port = TextEditingController(text: '${host?.port ?? 22}');
    _note = TextEditingController(text: host?.note ?? '');
    _identityId = host?.identityId;
    _groupId = host?.groupId;
    _knownHostKey = host?.knownHostKey;
  }

  @override
  void dispose() {
    _label.dispose();
    _hostname.dispose();
    _port.dispose();
    _note.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final hostname = _hostname.text.trim();
    if (hostname.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Hostname or IP is required')),
      );
      return;
    }
    final port = int.tryParse(_port.text.trim()) ?? 22;
    if (port < 1 || port > 65535) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Port must be 1–65535')));
      return;
    }

    final note = _note.text.trim();
    final existing = widget.host;
    final host = Host(
      id: existing?.id ?? newId(),
      label: _label.text.trim(),
      hostname: hostname,
      port: port,
      identityId: _identityId,
      groupId: _groupId,
      note: note.isEmpty ? null : note,
      knownHostKey: _knownHostKey,
      osId: existing?.osId,
      osPretty: existing?.osPretty,
    );

    await VaultStore.instance.saveHost(host);
    if (mounted) Navigator.of(context).pop();
  }

  Future<void> _delete() async {
    final host = widget.host;
    if (host == null) return;
    final confirmed = await confirmDestructive(
      context,
      title: 'Delete system?',
      message: '${host.displayLabel} will be removed from the vault.',
    );
    if (!confirmed || !mounted) return;
    await VaultStore.instance.deleteHost(host.id);
    if (mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final store = VaultStore.instance;

    return Scaffold(
      backgroundColor: colors.background,
      appBar: QPageAppBar(
        title: _isNew ? 'New system' : 'Edit system',
        subtitle: _isNew ? null : widget.host!.endpoint,
        actions: [
          if (!_isNew)
            QPageAppBarAction(
              tooltip: 'Delete',
              icon: QIcon(
                asset: 'assets/ic/action/delete.svg',
                color: colors.onAccent,
                size: 20,
              ),
              onPressed: _delete,
            ),
          QPageAppBarAction(
            tooltip: 'Save',
            icon: QIcon(
              asset: 'assets/ic/state/ok.svg',
              color: colors.onAccent,
              size: 20,
            ),
            onPressed: _save,
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(14, 6, 14, 28),
        children: [
          const QFormLabel('Connection'),
          QTextField(
            controller: _label,
            label: 'Name',
            hint: 'Shown on the card',
            autofocus: _isNew,
          ),
          const SizedBox(height: 10),
          QTextField(
            controller: _hostname,
            label: 'Hostname or IP',
            hint: '10.0.0.5 or box.local',
          ),
          const SizedBox(height: 10),
          QTextField(
            controller: _port,
            label: 'Port',
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          ),

          const QFormLabel('User'),
          _PickerCard(
            icon: 'assets/ic/nav/identities.svg',
            title: _identityLabel(store),
            subtitle: 'Tap to choose which user logs in',
            onTap: () => _pickIdentity(store),
          ),

          const QFormLabel('Group'),
          _PickerCard(
            icon: 'assets/ic/nav/group.svg',
            title: store.groupById(_groupId)?.name ?? 'Ungrouped',
            subtitle: 'Groups become sections on the systems list',
            onTap: () => _pickGroup(store),
          ),

          const QFormLabel('Notes'),
          QTextField(controller: _note, label: 'Notes', maxLines: 3),

          if (_knownHostKey != null) ...[
            const QFormLabel('Host key'),
            _HostKeyCard(
              fingerprint: _knownHostKey!,
              onForget: () => setState(() => _knownHostKey = null),
            ),
          ],
        ],
      ),
    );
  }

  String _identityLabel(VaultStore store) {
    final id = _identityId;
    if (id == null) return 'No user assigned';
    for (final identity in store.identities) {
      if (identity.id == id) {
        return '${identity.label} (${identity.username})';
      }
    }
    return 'Missing user';
  }

  Future<void> _pickIdentity(VaultStore store) async {
    final selected = await _pickFromSheet<String?>(
      title: 'Assign user',
      options: [
        const _Option(null, 'No user'),
        for (final identity in store.identities)
          _Option(identity.id, '${identity.label} (${identity.username})'),
      ],
      current: _identityId,
    );
    if (!mounted) return;
    // Dismissing the sheet returns null; picking "No user" returns an _Option
    // wrapping null. The wrapper is what keeps those apart.
    if (selected == null) return;
    setState(() => _identityId = selected.value);
  }

  Future<void> _pickGroup(VaultStore store) async {
    final selected = await _pickFromSheet<String?>(
      title: 'Assign group',
      options: [
        const _Option(null, 'Ungrouped'),
        for (final group in store.groups) _Option(group.id, group.name),
      ],
      current: _groupId,
    );
    if (!mounted || selected == null) return;
    setState(() => _groupId = selected.value);
  }

  Future<_Option<T>?> _pickFromSheet<T>({
    required String title,
    required List<_Option<T>> options,
    required T current,
  }) {
    final colors = context.appColors;
    return showModalBottomSheet<_Option<T>>(
      context: context,
      backgroundColor: colors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 18, 20, 10),
              child: Row(
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      color: colors.textPrimary,
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ),
            Flexible(
              child: SingleChildScrollView(
                padding: const EdgeInsets.only(bottom: 16),
                child: GroupedCardList<_Option<T>>(
                  items: options,
                  onTap: (option) =>
                      () => Navigator.of(sheetContext).pop(option),
                  itemBuilder: (context, option) => Row(
                    children: [
                      Expanded(
                        child: Text(
                          option.label,
                          style: TextStyle(
                            color: colors.textPrimary,
                            fontSize: 14,
                          ),
                        ),
                      ),
                      if (option.value == current)
                        Icon(
                          Icons.check_circle,
                          size: 20,
                          color: colors.accent,
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Option<T> {
  const _Option(this.value, this.label);
  final T value;
  final String label;
}

class _PickerCard extends StatelessWidget {
  const _PickerCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final String icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return Material(
      color: colors.card,
      borderRadius: BorderRadius.circular(12),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 10, 10, 10),
          child: Row(
            children: [
              QIconBadge(asset: icon, color: colors.info),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: colors.textPrimary,
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(color: colors.textMuted, fontSize: 12),
                    ),
                  ],
                ),
              ),
              QIcon(
                asset: 'assets/ic/nav/navigate.svg',
                color: colors.textMuted,
                size: 16,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _HostKeyCard extends StatelessWidget {
  const _HostKeyCard({required this.fingerprint, required this.onForget});

  final String fingerprint;
  final VoidCallback onForget;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 12, 8, 12),
      decoration: BoxDecoration(
        color: colors.card,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Pinned on first connect',
                  style: TextStyle(color: colors.textMuted, fontSize: 12),
                ),
                const SizedBox(height: 4),
                SelectableText(
                  fingerprint,
                  style: TextStyle(
                    color: colors.textPrimary,
                    fontSize: 12,
                    fontFamily: 'monospace',
                  ),
                ),
              ],
            ),
          ),
          TextButton(
            onPressed: onForget,
            style: TextButton.styleFrom(foregroundColor: colors.danger),
            child: const Text('Forget'),
          ),
        ],
      ),
    );
  }
}
