import 'package:flutter/material.dart';

import '../../data/store.dart';
import '../../data/vault.dart';
import '../../services/log.dart';
import '../../theme/theme.dart';
import '../../widgets/password_prompt.dart';

class LockPage extends StatelessWidget {
  const LockPage({
    super.key,
    required this.vaultExists,
    required this.onUnlocked,
  });

  final bool vaultExists;
  final VoidCallback onUnlocked;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final creating = !vaultExists;

    return Scaffold(
      backgroundColor: colors.background,
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 40),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 360),
            child: PasswordForm(
              title: creating ? 'Create your vault' : 'ohmyssh',
              message: creating
                  ? 'Everything — systems, users, keys — is encrypted with '
                        'this password. There is no recovery if you lose it.'
                  : 'Enter your master password to unlock.',
              actionLabel: creating ? 'Create vault' : 'Unlock',
              confirm: creating,
              minLength: creating ? 8 : 0,
              onSubmit: (password) => _submit(password, creating),
            ),
          ),
        ),
      ),
    );
  }

  Future<String?> _submit(String password, bool creating) async {
    try {
      if (creating) {
        await VaultStore.instance.create(password);
        Log.info('vault', 'created');
      } else {
        await VaultStore.instance.unlock(password);
        Log.info('vault', 'unlocked');
      }
      onUnlocked();
      return null;
    } on VaultException catch (error, stackTrace) {
      Log.error('vault', error.message, stackTrace);
      return error.message;
    } catch (error, stackTrace) {
      Log.error('vault', error, stackTrace);
      return '$error';
    }
  }
}
