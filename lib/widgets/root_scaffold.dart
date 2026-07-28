import 'package:flutter/material.dart';

import '../components/icon.dart';
import '../theme/theme.dart';

enum RootTab {
  systems('Systems', 'hosts'),
  users('Users', 'identities'),
  sessions('Sessions', 'terminal'),
  settings('Settings', 'settings');

  const RootTab(this.label, this.icon);

  final String label;
  final String icon;

  String asset(bool selected) {
    const hasFilled = {'hosts', 'identities', 'settings'};
    if (selected && hasFilled.contains(icon)) {
      return 'assets/ic/nav/$icon-filled.svg';
    }
    return 'assets/ic/nav/$icon.svg';
  }
}

class RootScaffold extends StatelessWidget {
  const RootScaffold({
    super.key,
    required this.child,
    required this.currentTab,
    required this.onTabSelected,
    this.sessionCount = 0,
  });

  final Widget child;
  final RootTab currentTab;
  final ValueChanged<RootTab> onTabSelected;
  final int sessionCount;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return Scaffold(
      backgroundColor: colors.background,
      body: child,
      bottomNavigationBar: Container(
        color: colors.card,
        child: SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.only(top: 6, bottom: 8),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                for (final tab in RootTab.values)
                  _BottomTab(
                    tab: tab,
                    selected: currentTab == tab,
                    badge: tab == RootTab.sessions ? sessionCount : 0,
                    onTap: () => onTabSelected(tab),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _BottomTab extends StatelessWidget {
  const _BottomTab({
    required this.tab,
    required this.selected,
    required this.badge,
    required this.onTap,
  });

  final RootTab tab;
  final bool selected;
  final int badge;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final color = selected ? colors.accent : colors.textMuted;
    return Material(
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(10),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        borderRadius: BorderRadius.circular(10),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                width: 42,
                height: 24,
                child: Stack(
                  alignment: Alignment.center,
                  clipBehavior: Clip.none,
                  children: [
                    QIcon(asset: tab.asset(selected), color: color, size: 24),
                    if (badge > 0)
                      Positioned(
                        right: 2,
                        top: -2,
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 5,
                            vertical: 1,
                          ),
                          decoration: BoxDecoration(
                            color: colors.accent,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            '$badge',
                            style: TextStyle(
                              fontSize: 9,
                              height: 1.2,
                              fontWeight: FontWeight.w700,
                              color: colors.onAccent,
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 4),
              Text(
                tab.label,
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  color: color,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
