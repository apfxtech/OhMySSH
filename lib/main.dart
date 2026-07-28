import 'package:flutter/material.dart';

import 'data/store.dart';
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
  final vaultExists = await VaultStore.vaultExists();
  SessionForegroundService.instance.start();
  runApp(OhMySshApp(vaultExists: vaultExists));
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
