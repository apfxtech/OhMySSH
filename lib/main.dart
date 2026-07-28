import 'package:flutter/material.dart';

import 'data/autologin.dart';
import 'data/store.dart';
import 'services/log.dart';
import 'pages/hosts/page.dart';
import 'pages/identities/page.dart';
import 'pages/lock/page.dart';
import 'pages/session/list_page.dart';
import 'pages/settings/page.dart';
import 'services/session_foreground_service.dart';
import 'ssh/manager.dart';
import 'theme/theme.dart';
import 'widgets/root_scaffold.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await QAppThemeController.instance.loadThemeMode();

  await AutoLogin.instance.isAvailable();

  final vaultExists = await VaultStore.vaultExists();
  Log.info('startup', 'vault ${vaultExists ? 'found' : 'not created yet'}');
  if (vaultExists) await _tryAutoUnlock();

  SessionForegroundService.instance.start();
  runApp(OhMySshApp(vaultExists: vaultExists));
}

Future<void> _tryAutoUnlock() async {
  final password = await AutoLogin.instance.readPassword();
  if (password == null) return;
  try {
    await VaultStore.instance.unlock(password);
    Log.info('startup', 'vault unlocked automatically');
  } catch (error, stackTrace) {
    Log.error('startup', 'auto-unlock failed, clearing it: $error', stackTrace);
    await AutoLogin.instance.disable();
  }
}

class OhMySshApp extends StatelessWidget {
  const OhMySshApp({super.key, required this.vaultExists});

  final bool vaultExists;

  @override
  Widget build(BuildContext context) {
    final controller = QAppThemeController.instance;
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) => MaterialApp(
        title: 'ohmyssh',
        debugShowCheckedModeBanner: false,
        theme: buildAppTheme(controller.brightness, controller.accent),
        home: _Root(vaultExists: vaultExists),
      ),
    );
  }
}

class _Root extends StatefulWidget {
  const _Root({required this.vaultExists});

  final bool vaultExists;

  @override
  State<_Root> createState() => _RootState();
}

class _RootState extends State<_Root> {
  late bool _vaultExists = widget.vaultExists;
  RootTab _tab = RootTab.systems;

  @override
  Widget build(BuildContext context) {
    final store = VaultStore.instance;

    return AnimatedBuilder(
      animation: store,
      builder: (context, _) {
        if (!store.isUnlocked) {
          return LockPage(
            // Once a vault exists the lock screen must stop offering to create
            // another one, or unlocking would overwrite it.
            vaultExists: _vaultExists,
            onUnlocked: () => setState(() => _vaultExists = true),
          );
        }

        return AnimatedBuilder(
          animation: SessionManager.instance,
          builder: (context, _) => RootScaffold(
            currentTab: _tab,
            sessionCount: SessionManager.instance.sessions.length,
            onTabSelected: (tab) => setState(() => _tab = tab),
            child: switch (_tab) {
              RootTab.systems => const HostsPage(),
              RootTab.users => const IdentitiesPage(),
              RootTab.sessions => const SessionsListPage(),
              RootTab.settings => SettingsPage(
                onLocked: () => setState(() => _tab = RootTab.systems),
              ),
            },
          ),
        );
      },
    );
  }
}
