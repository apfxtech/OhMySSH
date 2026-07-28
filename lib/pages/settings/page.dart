import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../../components/appbar.dart';
import '../../components/cardlist.dart';
import '../../components/icon.dart';
import '../../data/store.dart';
import '../../data/vault.dart';
import '../../ssh/manager.dart';
import '../../theme/theme.dart';
import '../../widgets/fields.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key, required this.onLocked});

  final VoidCallback onLocked;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final controller = QAppThemeController.instance;

    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) => Scaffold(
        backgroundColor: colors.background,
        appBar: const QPageAppBar(title: 'Settings'),
        body: ListView(
          padding: const EdgeInsets.symmetric(vertical: 10),
          children: [
            GroupedCardList<QThemeMode>(
              title: 'Theme',
              items: QThemeMode.values,
              onTap: (mode) => () => controller.setThemeMode(mode),
              itemBuilder: (context, mode) => Row(
                children: [
                  Expanded(
                    child: Text(
                      mode.label,
                      style: TextStyle(
                        color: colors.textPrimary,
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                  Icon(
                    controller.themeMode == mode
                        ? Icons.check_circle
                        : Icons.circle_outlined,
                    size: 21,
                    color: controller.themeMode == mode
                        ? colors.accent
                        : colors.textMuted,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 14),
            GroupedCardList<_Action>(
              title: 'Vault',
              items: _vaultActions(context, onLocked),
              onTap: (action) => action.onTap,
              itemBuilder: (context, action) => _ActionRow(action: action),
            ),
            const SizedBox(height: 14),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 26),
              child: Text(
                'The vault file holds every system, user, password and private '
                'key, encrypted with your master password. Exporting copies '
                'that file as-is — the other device only needs the password.',
                style: TextStyle(
                  color: colors.textMuted,
                  fontSize: 11.5,
                  height: 1.4,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  List<_Action> _vaultActions(BuildContext context, VoidCallback onLocked) => [
    _Action(
      icon: 'assets/ic/action/export.svg',
      color: const Color(0xFF589DFF),
      title: 'Export vault',
      subtitle: 'Save an encrypted copy',
      onTap: () => _export(context),
    ),
    _Action(
      icon: 'assets/ic/action/import.svg',
      color: const Color(0xFF2ED34A),
      title: 'Import vault',
      subtitle: 'Merge systems and users from a file',
      onTap: () => _import(context),
    ),
    _Action(
      icon: 'assets/ic/action/password.svg',
      color: const Color(0xFFFF9B34),
      title: 'Change master password',
      subtitle: 'Re-encrypts the vault in place',
      onTap: () => _changePassword(context),
    ),
    _Action(
      icon: 'assets/ic/state/lock.svg',
      color: const Color(0xFFE85858),
      title: 'Lock now',
      subtitle: 'Closes every session and clears the vault from memory',
      onTap: () => _lock(context, onLocked),
    ),
  ];

  Future<void> _export(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      final path = await VaultStore.instance.exportVault();
      if (path == null) return;
      messenger.showSnackBar(SnackBar(content: Text('Exported to $path')));
    } catch (error) {
      messenger.showSnackBar(SnackBar(content: Text('Export failed: $error')));
    }
  }

  Future<void> _import(BuildContext context) async {
    final picked = await FilePicker.pickFiles(
      dialogTitle: 'Select a vault file',
      // No extension filter: a .vault copied through a chat app or cloud drive
      // often arrives renamed.
    );
    if (picked == null || picked.files.single.path == null) return;
    if (!context.mounted) return;

    final password = await promptForText(
      context,
      title: 'Import vault',
      label: 'Master password of that file',
      obscure: true,
      actionLabel: 'Import',
    );
    if (password == null || password.isEmpty || !context.mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    try {
      final summary = await VaultStore.instance.importVault(
        file: File(picked.files.single.path!),
        password: password,
      );
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            summary.total == 0
                ? 'Nothing new to import'
                : 'Imported ${summary.hostsAdded} systems, '
                      '${summary.identitiesAdded} users '
                      '(${summary.hostsUpdated + summary.identitiesUpdated} updated)',
          ),
        ),
      );
    } on WrongPasswordException {
      messenger.showSnackBar(
        const SnackBar(content: Text('Wrong password for that file')),
      );
    } catch (error) {
      messenger.showSnackBar(SnackBar(content: Text('Import failed: $error')));
    }
  }

  Future<void> _changePassword(BuildContext context) async {
    final next = await promptForText(
      context,
      title: 'Change master password',
      label: 'New password',
      obscure: true,
      actionLabel: 'Change',
    );
    if (next == null || !context.mounted) return;
    if (next.length < 8) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Use at least 8 characters')),
      );
      return;
    }

    final confirm = await promptForText(
      context,
      title: 'Confirm',
      label: 'Repeat new password',
      obscure: true,
      actionLabel: 'Confirm',
    );
    if (confirm == null || !context.mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    if (confirm != next) {
      messenger.showSnackBar(
        const SnackBar(content: Text('Passwords do not match')),
      );
      return;
    }

    try {
      await VaultStore.instance.changeMasterPassword(next);
      messenger.showSnackBar(
        const SnackBar(content: Text('Master password changed')),
      );
    } catch (error) {
      messenger.showSnackBar(SnackBar(content: Text('Failed: $error')));
    }
  }

  Future<void> _lock(BuildContext context, VoidCallback onLocked) async {
    final confirmed = await confirmDestructive(
      context,
      title: 'Lock the vault?',
      message: 'Open sessions will be closed.',
      actionLabel: 'Lock',
    );
    if (!confirmed) return;
    await SessionManager.instance.closeAll();
    VaultStore.instance.lock();
    onLocked();
  }
}

class _Action {
  const _Action({
    required this.icon,
    required this.color,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final String icon;
  final Color color;
  final String title;
  final String subtitle;
  final VoidCallback onTap;
}

class _ActionRow extends StatelessWidget {
  const _ActionRow({required this.action});

  final _Action action;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return Row(
      children: [
        QIconBadge(asset: action.icon, color: action.color),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                action.title,
                style: TextStyle(
                  color: colors.textPrimary,
                  fontSize: 14,
                  height: 1.2,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                action.subtitle,
                style: TextStyle(
                  color: colors.textMuted,
                  fontSize: 12,
                  height: 1.2,
                ),
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
    );
  }
}
