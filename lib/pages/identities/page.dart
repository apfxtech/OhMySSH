import 'package:flutter/material.dart';

import '../../components/appbar.dart';
import '../../components/cardlist.dart';
import '../../components/icon.dart';
import '../../data/models.dart';
import '../../data/store.dart';
import '../../theme/theme.dart';
import '../../widgets/fields.dart';
import 'editor.dart';

class IdentitiesPage extends StatelessWidget {
  const IdentitiesPage({super.key});

  @override
  Widget build(BuildContext context) {
    final store = VaultStore.instance;
    final colors = context.appColors;

    return AnimatedBuilder(
      animation: store,
      builder: (context, _) {
        final identities = store.identities;

        return Scaffold(
          backgroundColor: colors.background,
          appBar: QPageAppBar(
            title: 'Users',
            subtitle: '${identities.length} saved',
            actions: [
              QPageAppBarAction(
                tooltip: 'New user',
                icon: QIcon(
                  asset: 'assets/ic/action/add.svg',
                  color: colors.onAccent,
                  size: 20,
                ),
                onPressed: () => _openEditor(context, null),
              ),
            ],
          ),
          body: identities.isEmpty
              ? QEmptyView(
                  icon: 'assets/ic/nav/identities.svg',
                  title: 'No users yet',
                  message:
                      'A user holds a login and its password or private key. '
                      'Systems point at one.',
                  action: FilledButton(
                    onPressed: () => _openEditor(context, null),
                    style: FilledButton.styleFrom(
                      backgroundColor: colors.accent,
                      foregroundColor: colors.onAccent,
                    ),
                    child: const Text('Add user'),
                  ),
                )
              : SingleChildScrollView(
                  padding: const EdgeInsets.only(top: 14, bottom: 20),
                  child: GroupedCardList<Identity>(
                    items: identities,
                    onTap: (identity) => () => _openEditor(context, identity),
                    itemBuilder: (context, identity) =>
                        _IdentityRow(identity: identity),
                  ),
                ),
        );
      },
    );
  }

  Future<void> _openEditor(BuildContext context, Identity? identity) =>
      Navigator.of(context).push<void>(
        MaterialPageRoute(builder: (_) => IdentityEditorPage(identity: identity)),
      );
}

class _IdentityRow extends StatelessWidget {
  const _IdentityRow({required this.identity});

  final Identity identity;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final isKey = identity.kind == AuthKind.privateKey;
    final usedBy = VaultStore.instance.hosts
        .where((h) => h.identityId == identity.id)
        .length;

    return Row(
      children: [
        QIconBadge(
          asset: isKey
              ? 'assets/ic/action/key.svg'
              : 'assets/ic/action/password.svg',
          color: isKey ? colors.accent : colors.info,
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                identity.label.isEmpty ? identity.username : identity.label,
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
                '${identity.username} · ${isKey ? 'private key' : 'password'}'
                '${usedBy == 0 ? '' : ' · $usedBy system${usedBy == 1 ? '' : 's'}'}',
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
        QIcon(
          asset: 'assets/ic/nav/navigate.svg',
          color: colors.textMuted,
          size: 16,
        ),
      ],
    );
  }
}
