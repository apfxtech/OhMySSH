import 'package:flutter/material.dart';

import '../../components/appbar.dart';
import '../../data/models.dart';
import '../../data/store.dart';
import '../../theme/theme.dart';
import '../../widgets/credentials_form.dart';
import '../../widgets/fields.dart';

class IdentityEditorPage extends StatefulWidget {
  const IdentityEditorPage({super.key, this.identity});

  final Identity? identity;

  @override
  State<IdentityEditorPage> createState() => _IdentityEditorPageState();
}

class _IdentityEditorPageState extends State<IdentityEditorPage> {
  late final TextEditingController _label;
  late final CredentialsController _credentials;

  bool get _isNew => widget.identity == null;

  @override
  void initState() {
    super.initState();
    _label = TextEditingController(text: widget.identity?.label ?? '');
    _credentials = CredentialsController(widget.identity);
  }

  @override
  void dispose() {
    _label.dispose();
    _credentials.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final problem = _credentials.validate();
    if (problem != null) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(problem)));
      return;
    }

    final identity = _credentials.build(
      id: widget.identity?.id ?? newId(),
      label: _label.text,
    );
    await VaultStore.instance.saveIdentity(identity);
    if (mounted) Navigator.of(context).pop(identity);
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
          const SizedBox(height: 12),
          CredentialsEditor(controller: _credentials),
        ],
      ),
    );
  }
}
