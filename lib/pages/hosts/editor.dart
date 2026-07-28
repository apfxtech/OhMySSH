import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../components/appbar.dart';
import '../../components/icon.dart';
import '../../data/models.dart';
import '../../data/store.dart';
import '../../theme/theme.dart';
import '../../widgets/credentials_form.dart';
import '../../widgets/fields.dart';
import '../identities/editor.dart';

enum _UserMode {
  saved('Saved user', 'Reuse a login from the Users list'),
  inline('Only this system', 'A one-off login stored on this system');

  const _UserMode(this.label, this.hint);
  final String label;
  final String hint;
}

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
  late final CredentialsController _credentials;

  late _UserMode _userMode;
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
    _credentials = CredentialsController(host?.inlineIdentity);
    _identityId = host?.identityId;
    _groupId = host?.groupId;
    _knownHostKey = host?.knownHostKey;
    _userMode = (host?.hasInlineIdentity ?? _isNew)
        ? _UserMode.inline
        : _UserMode.saved;
  }

  @override
  void dispose() {
    _label.dispose();
    _hostname.dispose();
    _port.dispose();
    _note.dispose();
    _credentials.dispose();
    super.dispose();
  }

  void _toast(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _save() async {
    final hostname = _hostname.text.trim();
    if (hostname.isEmpty) {
      _toast('Hostname or IP is required');
      return;
    }
    final port = int.tryParse(_port.text.trim()) ?? 22;
    if (port < 1 || port > 65535) {
      _toast('Port must be 1–65535');
      return;
    }

    Identity? inline;
    if (_userMode == _UserMode.inline && !_credentials.isEmpty) {
      // An untouched inline form means "no user yet", not an error: the system
      // saves, it just cannot connect until it has one.
      final problem = _credentials.validate();
      if (problem != null) {
        _toast(problem);
        return;
      }
      inline = _credentials.build(
        id: widget.host?.inlineIdentity?.id ?? newId(),
      );
    }

    final note = _note.text.trim();
    final existing = widget.host;
    final host = Host(
      id: existing?.id ?? newId(),
      label: _label.text.trim(),
      hostname: hostname,
      port: port,
      // The two are mutually exclusive: whichever mode is showing wins, and the
      // other is cleared so a stale reference cannot resurface.
      identityId: _userMode == _UserMode.saved ? _identityId : null,
      inlineIdentity: inline,
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
              icon: Icon(
                Icons.delete_outline,
                color: colors.onAccent,
                size: 20,
              ),
              onPressed: _delete,
            ),
          QPageAppBarAction(
            tooltip: 'Save',
            icon: Icon(Icons.check, color: colors.onAccent, size: 22),
            onPressed: _save,
          ),
        ],
      ),
      body: AnimatedBuilder(
        animation: store,
        builder: (context, _) => ListView(
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
            _ModeToggle(
              mode: _userMode,
              onChanged: (mode) => setState(() => _userMode = mode),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(2, 6, 2, 10),
              child: Text(
                _userMode.hint,
                style: TextStyle(color: colors.textMuted, fontSize: 11.5),
              ),
            ),
            if (_userMode == _UserMode.saved)
              _PickerCard(
                icon: Icons.person_outline,
                title: _identityLabel(store),
                subtitle: store.identities.isEmpty
                    ? 'No saved users yet — tap to create one'
                    : 'Tap to choose or create a user',
                onTap: () => _pickIdentity(store),
              )
            else ...[
              CredentialsEditor(controller: _credentials),
              const SizedBox(height: 6),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  onPressed: _promoteInline,
                  icon: Icon(
                    Icons.bookmark_add_outlined,
                    size: 18,
                    color: colors.accent,
                  ),
                  label: const Text('Also save to Users'),
                ),
              ),
            ],

            const QFormLabel('Group'),
            _PickerCard(
              icon: Icons.folder_outlined,
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
      ),
    );
  }

  String _identityLabel(VaultStore store) {
    if (_identityId == null) return 'No user assigned';
    final identity = store.identityById(_identityId);
    return identity == null
        ? 'Missing user'
        : '${identity.label} (${identity.username})';
  }

  Future<void> _promoteInline() async {
    final problem = _credentials.validate();
    if (problem != null) {
      _toast(problem);
      return;
    }
    final name = await promptForText(
      context,
      title: 'Save as a reusable user',
      label: 'Name',
      initial: _credentials.username.text.trim(),
      actionLabel: 'Save',
    );
    if (name == null || !mounted) return;

    final identity = _credentials.build(id: newId(), label: name);
    await VaultStore.instance.saveIdentity(identity);
    if (!mounted) return;
    setState(() {
      _identityId = identity.id;
      _userMode = _UserMode.saved;
    });
    _toast('${identity.label} added to Users');
  }

  /// Sentinel for the "create one now" row. The leading space keeps it from
  /// colliding with any generated id.
  static const String _createSentinel = ' new';

  Future<void> _pickIdentity(VaultStore store) async {
    final selected = await pickFromList<String?>(
      context,
      title: 'Assign user',
      current: _identityId,
      options: [
        const PickOption(
          _createSentinel,
          'New user…',
          icon: Icons.person_add_alt,
          isAction: true,
        ),
        const PickOption(null, 'No user', icon: Icons.block),
        for (final identity in store.identities)
          PickOption(
            identity.id,
            identity.label,
            subtitle:
                '${identity.username} · '
                '${identity.kind == AuthKind.privateKey ? 'private key' : 'password'}',
            icon: identity.kind == AuthKind.privateKey
                ? Icons.vpn_key_outlined
                : Icons.password,
          ),
      ],
    );
    if (!mounted || selected == null) return;

    if (selected.value == _createSentinel) {
      final created = await Navigator.of(context).push<Identity>(
        MaterialPageRoute(builder: (_) => const IdentityEditorPage()),
      );
      if (!mounted || created == null) return;
      setState(() => _identityId = created.id);
      return;
    }
    setState(() => _identityId = selected.value);
  }

  Future<void> _pickGroup(VaultStore store) async {
    final selected = await pickFromList<String?>(
      context,
      title: 'Assign group',
      current: _groupId,
      options: [
        const PickOption(
          _createSentinel,
          'New group…',
          icon: Icons.create_new_folder_outlined,
          isAction: true,
        ),
        const PickOption(null, 'Ungrouped', icon: Icons.block),
        for (final group in store.groups)
          PickOption(group.id, group.name, icon: Icons.folder_outlined),
      ],
    );
    if (!mounted || selected == null) return;

    if (selected.value == _createSentinel) {
      final name = await promptForText(
        context,
        title: 'New group',
        label: 'Group name',
        actionLabel: 'Create',
      );
      if (name == null || name.isEmpty || !mounted) return;
      final group = HostGroup(id: newId(), name: name);
      await store.saveGroup(group);
      if (!mounted) return;
      setState(() => _groupId = group.id);
      return;
    }
    setState(() => _groupId = selected.value);
  }
}

class _ModeToggle extends StatelessWidget {
  const _ModeToggle({required this.mode, required this.onChanged});

  final _UserMode mode;
  final ValueChanged<_UserMode> onChanged;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return Container(
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: colors.card,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          for (final option in _UserMode.values)
            Expanded(
              child: Material(
                color: mode == option ? colors.accent : Colors.transparent,
                borderRadius: BorderRadius.circular(9),
                clipBehavior: Clip.antiAlias,
                child: InkWell(
                  onTap: () => onChanged(option),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 10),
                    child: Text(
                      option.label,
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: mode == option
                            ? colors.onAccent
                            : colors.textSecondary,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _PickerCard extends StatelessWidget {
  const _PickerCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
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
              QIconBadge(icon: icon, color: colors.info),
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
              Icon(Icons.chevron_right, color: colors.textMuted, size: 20),
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
