import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../../components/appbar.dart';
import '../../components/cardlist.dart';
import '../../components/icon.dart';
import '../../data/autologin.dart';
import '../../data/store.dart';
import '../../data/vault.dart';
import '../../services/log.dart';
import '../../ssh/manager.dart';
import '../../theme/theme.dart';
import '../../widgets/app_version.dart';
import '../../widgets/fields.dart';
import '../../widgets/password_prompt.dart';
import 'icon_gallery.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key, required this.onLocked});

  final VoidCallback onLocked;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  static const _tapsForGallery = 7;
  static const _tapWindow = Duration(seconds: 2);

  bool _autoLogin = false;
  bool _autoLoginAvailable = true;
  int _versionTaps = 0;
  DateTime? _lastVersionTap;

  @override
  void initState() {
    super.initState();
    _loadAutoLogin();
  }

  Future<void> _loadAutoLogin() async {
    final available = await AutoLogin.instance.isAvailable();
    final enabled = available && await AutoLogin.instance.isEnabled();
    if (!mounted) return;
    setState(() {
      _autoLoginAvailable = available;
      _autoLogin = enabled;
    });
  }

  /// Seven taps on the version label opens the OS icon gallery. The counter
  /// resets whenever a tap arrives late, so an idle tap never accumulates.
  void _countVersionTap() {
    final now = DateTime.now();
    final last = _lastVersionTap;
    _versionTaps = (last == null || now.difference(last) > _tapWindow)
        ? 1
        : _versionTaps + 1;
    _lastVersionTap = now;
    if (_versionTaps < _tapsForGallery) return;
    _versionTaps = 0;
    _lastVersionTap = null;
    Navigator.of(context).push(
      MaterialPageRoute<void>(builder: (_) => const IconGalleryPage()),
    );
  }

  void _toast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _toggleAutoLogin(bool value) async {
    if (!value) {
      setState(() => _autoLogin = false);
      await AutoLogin.instance.disable();
      return;
    }

    if (!_autoLoginAvailable) {
      final reason =
          AutoLogin.instance.unavailableReason ?? 'no usable keystore';
      Log.warn('settings', 'auto-unlock unavailable: $reason');
      _toast(reason);
      return;
    }

    final password = await promptForPassword(
      context,
      message:
          'Confirm your master password to store it in this device\'s '
          'keystore. The app will then open without asking.',
      actionLabel: 'Enable',
      verify: (candidate) async {
        if (!await VaultStore.instance.verifyPassword(candidate)) {
          return 'Wrong master password';
        }
        try {
          await AutoLogin.instance.enable(candidate);
          return null;
        } on AutoLoginException catch (error) {
          return error.message;
        } catch (error, stackTrace) {
          Log.error('settings', 'enabling auto-unlock failed', stackTrace);
          Log.error('settings', error);
          return '$error';
        }
      },
    );
    if (password == null || !mounted) return;
    setState(() => _autoLogin = true);
  }

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
              onTap: (mode) =>
                  () => controller.setThemeMode(mode),
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
            GroupedCardList<int>(
              title: 'Startup',
              items: const [0],
              wrapItems: true,
              itemBuilder: (context, _) => _AutoLoginRow(
                value: _autoLogin,
                enabled: _autoLoginAvailable,
                unavailableReason: _autoLoginAvailable
                    ? null
                    : AutoLogin.instance.unavailableReason,
                onChanged: _toggleAutoLogin,
              ),
            ),

            const SizedBox(height: 14),
            GroupedCardList<_Action>(
              title: 'Vault',
              items: _vaultActions(),
              onTap: (action) => action.onTap,
              itemBuilder: (context, action) => _ActionRow(action: action),
            ),

            const SizedBox(height: 14),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 26),
              child: Text(
                'The vault file holds every system, user, password and private '
                'key, encrypted with your master password. Exporting copies '
                'that file as-is — the other device only needs the password. '
                'Auto-unlock keeps a copy of the password in the device '
                'keystore; it is never written to the vault or to an export.',
                style: TextStyle(
                  color: colors.textMuted,
                  fontSize: 11.5,
                  height: 1.4,
                ),
              ),
            ),

            const SizedBox(height: 18),
            GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: _countVersionTap,
              child: const AppVersionLabel(),
            ),
            const SizedBox(height: 10),
          ],
        ),
      ),
    );
  }

  List<_Action> _vaultActions() => [
    _Action(
      icon: Icons.file_upload_outlined,
      color: const Color(0xFF589DFF),
      title: 'Export vault',
      subtitle: 'Save an encrypted copy',
      onTap: _export,
    ),
    _Action(
      icon: Icons.file_download_outlined,
      color: const Color(0xFF2ED34A),
      title: 'Import vault',
      subtitle: 'Merge systems and users from a file',
      onTap: _import,
    ),
    _Action(
      icon: Icons.password,
      color: const Color(0xFFFF9B34),
      title: 'Change master password',
      subtitle: 'Re-encrypts the vault in place',
      onTap: _changePassword,
    ),
    _Action(
      icon: Icons.lock_outline,
      color: const Color(0xFFE85858),
      title: 'Lock now',
      subtitle: 'Closes every session and clears the vault from memory',
      onTap: _lock,
    ),
  ];

  Future<void> _export() async {
    try {
      final path = await VaultStore.instance.exportVault();
      if (path == null) return;
      _toast('Exported to $path');
    } catch (error, stackTrace) {
      Log.error('settings', 'export failed', stackTrace);
      Log.error('settings', error);
      _toast('Export failed: $error');
    }
  }

  Future<void> _import() async {
    final picked = await FilePicker.pickFiles(
      dialogTitle: 'Select a vault file',
      // No extension filter: a .vault copied through a chat app or cloud drive
      // often arrives renamed.
    );
    if (picked == null || picked.files.single.path == null) return;
    if (!mounted) return;

    final password = await promptForPassword(
      context,
      message:
          'Enter the master password of the vault file you picked. '
          'Matching entries are updated, new ones are added.',
      actionLabel: 'Import',
    );
    if (password == null || !mounted) return;

    try {
      final summary = await VaultStore.instance.importVault(
        file: File(picked.files.single.path!),
        password: password,
      );
      _toast(
        summary.total == 0
            ? 'Nothing new to import'
            : 'Imported ${summary.hostsAdded} systems, '
                  '${summary.identitiesAdded} users '
                  '(${summary.hostsUpdated + summary.identitiesUpdated} updated)',
      );
    } on WrongPasswordException {
      Log.warn('settings', 'import: wrong password for the chosen file');
      _toast('Wrong password for that file');
    } catch (error, stackTrace) {
      Log.error('settings', 'import failed', stackTrace);
      Log.error('settings', error);
      _toast('Import failed: $error');
    }
  }

  Future<void> _changePassword() async {
    final next = await promptForPassword(
      context,
      message:
          'Pick a new master password. The vault is re-encrypted in '
          'place; existing exports keep their old password.',
      actionLabel: 'Change',
      confirm: true,
      minLength: 8,
      verify: (candidate) async {
        try {
          await VaultStore.instance.changeMasterPassword(candidate);
          return null;
        } catch (error, stackTrace) {
          Log.error('settings', 'changing master password failed', stackTrace);
          Log.error('settings', error);
          return '$error';
        }
      },
    );
    if (next == null || !mounted) return;
    _toast('Master password changed');
  }

  Future<void> _lock() async {
    final confirmed = await confirmDestructive(
      context,
      title: 'Lock the vault?',
      message: 'Open sessions will be closed.',
      actionLabel: 'Lock',
    );
    if (!confirmed) return;
    await SessionManager.instance.closeAll();
    VaultStore.instance.lock();
    widget.onLocked();
  }
}

class _AutoLoginRow extends StatelessWidget {
  const _AutoLoginRow({
    required this.value,
    required this.enabled,
    required this.unavailableReason,
    required this.onChanged,
  });

  final bool value;
  final bool enabled;
  final String? unavailableReason;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return Row(
      children: [
        QIconBadge(
          icon: value ? Icons.lock_open : Icons.lock_outline,
          color: value ? colors.success : colors.textMuted,
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                'Unlock automatically',
                style: TextStyle(
                  color: colors.textPrimary,
                  fontSize: 14,
                  height: 1.2,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                unavailableReason ??
                    (value
                        ? 'Opens straight to your systems'
                        : 'Ask for the master password on every launch'),
                maxLines: 3,
                style: TextStyle(
                  color: unavailableReason == null
                      ? colors.textMuted
                      : colors.warning,
                  fontSize: 12,
                  height: 1.25,
                ),
              ),
            ],
          ),
        ),
        Switch(
          value: value,
          // Turning it off stays available even when the keystore is missing,
          // so a stale "on" can always be cleared.
          onChanged: (enabled || value) ? onChanged : null,
          activeThumbColor: colors.onAccent,
          activeTrackColor: colors.accent,
        ),
      ],
    );
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

  final IconData icon;
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
        QIconBadge(icon: action.icon, color: action.color),
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
        Icon(Icons.chevron_right, color: colors.textMuted, size: 20),
      ],
    );
  }
}
