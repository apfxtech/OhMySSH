import 'dart:convert';
import 'dart:io';

import 'package:dartssh2/dartssh2.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../../components/appbar.dart';
import '../../components/icon.dart';
import '../../data/models.dart';
import '../../data/store.dart';
import '../../theme/theme.dart';
import '../../widgets/fields.dart';

class IdentityEditorPage extends StatefulWidget {
  const IdentityEditorPage({super.key, this.identity});

  final Identity? identity;

  @override
  State<IdentityEditorPage> createState() => _IdentityEditorPageState();
}

class _IdentityEditorPageState extends State<IdentityEditorPage> {
  late final TextEditingController _label;
  late final TextEditingController _username;
  late final TextEditingController _password;
  late final TextEditingController _passphrase;

  late AuthKind _kind;
  String? _privateKey;
  String? _keyStatus;

  bool get _isNew => widget.identity == null;

  @override
  void initState() {
    super.initState();
    final identity = widget.identity;
    _label = TextEditingController(text: identity?.label ?? '');
    _username = TextEditingController(text: identity?.username ?? '');
    _password = TextEditingController(text: identity?.password ?? '');
    _passphrase = TextEditingController(text: identity?.passphrase ?? '');
    _kind = identity?.kind ?? AuthKind.password;
    _privateKey = identity?.privateKey;
    if (_privateKey != null) _keyStatus = _describeKey(_privateKey!);
  }

  @override
  void dispose() {
    _label.dispose();
    _username.dispose();
    _password.dispose();
    _passphrase.dispose();
    super.dispose();
  }

  String _describeKey(String pem) {
    if (SSHKeyPair.isEncryptedPem(pem)) {
      return 'Encrypted key loaded — passphrase required';
    }
    try {
      final keys = SSHKeyPair.fromPem(pem);
      final types = keys.map((k) => k.type).toSet().join(', ');
      return 'Key loaded${types.isEmpty ? '' : ' · $types'}';
    } catch (error) {
      return 'Key could not be parsed: $error';
    }
  }

  Future<void> _importKey() async {
    final result = await FilePicker.pickFiles(
      dialogTitle: 'Select a private key',
      withData: true,
    );
    if (result == null || result.files.isEmpty) return;

    final picked = result.files.single;
    List<int>? bytes = picked.bytes;
    if (bytes == null && picked.path != null) {
      bytes = await File(picked.path!).readAsBytes();
    }
    if (bytes == null) return;

    final pem = utf8.decode(bytes, allowMalformed: true);
    if (!pem.contains('PRIVATE KEY')) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('That file does not look like a private key'),
        ),
      );
      return;
    }

    setState(() {
      _privateKey = pem;
      _kind = AuthKind.privateKey;
      _keyStatus = _describeKey(pem);
    });
  }

  Future<void> _save() async {
    final username = _username.text.trim();
    if (username.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Username is required')));
      return;
    }
    if (_kind == AuthKind.privateKey && (_privateKey?.isEmpty ?? true)) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Import a private key')));
      return;
    }

    final label = _label.text.trim();
    final passphrase = _passphrase.text;
    final identity = Identity(
      id: widget.identity?.id ?? newId(),
      label: label.isEmpty ? username : label,
      username: username,
      kind: _kind,
      password: _kind == AuthKind.password ? _password.text : null,
      privateKey: _kind == AuthKind.privateKey ? _privateKey : null,
      passphrase: _kind == AuthKind.privateKey && passphrase.isNotEmpty
          ? passphrase
          : null,
    );

    await VaultStore.instance.saveIdentity(identity);
    if (mounted) Navigator.of(context).pop();
  }

  Future<void> _delete() async {
    final identity = widget.identity;
    if (identity == null) return;
    final usedBy = VaultStore.instance.hosts
        .where((h) => h.identityId == identity.id)
        .length;
    final confirmed = await confirmDestructive(
      context,
      title: 'Delete user?',
      message: usedBy == 0
          ? '${identity.label} will be removed from the vault.'
          : '${identity.label} is used by $usedBy system'
                '${usedBy == 1 ? '' : 's'}. They will be left without a user.',
    );
    if (!confirmed || !mounted) return;
    await VaultStore.instance.deleteIdentity(identity.id);
    if (mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;

    return Scaffold(
      backgroundColor: colors.background,
      appBar: QPageAppBar(
        title: _isNew ? 'New user' : 'Edit user',
        subtitle: _isNew ? null : widget.identity!.username,
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
          const QFormLabel('Identity'),
          QTextField(
            controller: _label,
            label: 'Name',
            hint: 'Defaults to the username',
            autofocus: _isNew,
          ),
          const SizedBox(height: 10),
          QTextField(controller: _username, label: 'Username', hint: 'root'),

          const QFormLabel('Authentication'),
          _KindToggle(
            kind: _kind,
            onChanged: (kind) => setState(() => _kind = kind),
          ),
          const SizedBox(height: 12),

          if (_kind == AuthKind.password)
            QTextField(
              controller: _password,
              label: 'Password',
              obscure: true,
            )
          else ...[
            _KeyCard(
              status: _keyStatus,
              onImport: _importKey,
              onClear: _privateKey == null
                  ? null
                  : () => setState(() {
                      _privateKey = null;
                      _keyStatus = null;
                    }),
            ),
            const SizedBox(height: 10),
            QTextField(
              controller: _passphrase,
              label: 'Key passphrase (if encrypted)',
              obscure: true,
            ),
          ],
        ],
      ),
    );
  }
}

class _KindToggle extends StatelessWidget {
  const _KindToggle({required this.kind, required this.onChanged});

  final AuthKind kind;
  final ValueChanged<AuthKind> onChanged;

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
          for (final option in AuthKind.values)
            Expanded(
              child: Material(
                color: kind == option ? colors.accent : Colors.transparent,
                borderRadius: BorderRadius.circular(9),
                clipBehavior: Clip.antiAlias,
                child: InkWell(
                  onTap: () => onChanged(option),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 10),
                    child: Text(
                      option == AuthKind.password ? 'Password' : 'Private key',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: kind == option
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

class _KeyCard extends StatelessWidget {
  const _KeyCard({required this.status, required this.onImport, this.onClear});

  final String? status;
  final VoidCallback onImport;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final loaded = status != null;
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 12, 8, 12),
      decoration: BoxDecoration(
        color: colors.card,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          QIconBadge(
            asset: 'assets/ic/action/key.svg',
            color: loaded ? colors.success : colors.textMuted,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              status ?? 'No private key imported',
              style: TextStyle(
                color: loaded ? colors.textPrimary : colors.textMuted,
                fontSize: 13,
                height: 1.3,
              ),
            ),
          ),
          if (onClear != null)
            TextButton(
              onPressed: onClear,
              style: TextButton.styleFrom(foregroundColor: colors.danger),
              child: const Text('Clear'),
            ),
          TextButton(onPressed: onImport, child: const Text('Import')),
        ],
      ),
    );
  }
}
