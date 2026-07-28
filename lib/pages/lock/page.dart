import 'package:flutter/material.dart';

import '../../components/icon.dart';
import '../../data/store.dart';
import '../../data/vault.dart';
import '../../theme/theme.dart';

class LockPage extends StatefulWidget {
  const LockPage({
    super.key,
    required this.vaultExists,
    required this.onUnlocked,
  });

  final bool vaultExists;
  final VoidCallback onUnlocked;

  @override
  State<LockPage> createState() => _LockPageState();
}

class _LockPageState extends State<LockPage> {
  final _password = TextEditingController();
  final _confirm = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _password.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final password = _password.text;
    if (password.isEmpty) {
      setState(() => _error = 'Enter a master password');
      return;
    }
    if (!widget.vaultExists) {
      if (password.length < 8) {
        setState(() => _error = 'Use at least 8 characters');
        return;
      }
      if (password != _confirm.text) {
        setState(() => _error = 'Passwords do not match');
        return;
      }
    }

    setState(() {
      _busy = true;
      _error = null;
    });

    try {
      if (widget.vaultExists) {
        await VaultStore.instance.unlock(password);
      } else {
        await VaultStore.instance.create(password);
      }
      if (mounted) widget.onUnlocked();
    } on VaultException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (error) {
      if (mounted) setState(() => _error = '$error');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final creating = !widget.vaultExists;

    return Scaffold(
      backgroundColor: colors.background,
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 40),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 360),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Center(
                  child: QIconBadge(
                    icon: Icons.lock_outline,
                    color: colors.accent,
                    size: 64,
                    iconSize: 36,
                    borderRadius: 16,
                  ),
                ),
                const SizedBox(height: 20),
                Text(
                  creating ? 'Create your vault' : 'ohmyssh',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: colors.textPrimary,
                    fontSize: 22,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  creating
                      ? 'Everything — systems, users, keys — is encrypted with '
                            'this password. There is no recovery if you lose it.'
                      : 'Enter your master password to unlock.',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: colors.textMuted,
                    fontSize: 13,
                    height: 1.35,
                  ),
                ),
                const SizedBox(height: 24),
                _Field(
                  controller: _password,
                  label: 'Master password',
                  autofocus: true,
                  onSubmitted: creating ? null : (_) => _submit(),
                ),
                if (creating) ...[
                  const SizedBox(height: 10),
                  _Field(
                    controller: _confirm,
                    label: 'Confirm password',
                    onSubmitted: (_) => _submit(),
                  ),
                ],
                if (_error != null) ...[
                  const SizedBox(height: 12),
                  Text(
                    _error!,
                    textAlign: TextAlign.center,
                    style: TextStyle(color: colors.danger, fontSize: 12.5),
                  ),
                ],
                const SizedBox(height: 20),
                SizedBox(
                  height: 46,
                  child: FilledButton(
                    onPressed: _busy ? null : _submit,
                    style: FilledButton.styleFrom(
                      backgroundColor: colors.accent,
                      foregroundColor: colors.onAccent,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: _busy
                        ? SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: colors.onAccent,
                            ),
                          )
                        : Text(creating ? 'Create vault' : 'Unlock'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Field extends StatelessWidget {
  const _Field({
    required this.controller,
    required this.label,
    this.autofocus = false,
    this.onSubmitted,
  });

  final TextEditingController controller;
  final String label;
  final bool autofocus;
  final ValueChanged<String>? onSubmitted;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return TextField(
      controller: controller,
      autofocus: autofocus,
      obscureText: true,
      onSubmitted: onSubmitted,
      style: TextStyle(color: colors.textPrimary, fontSize: 15),
      decoration: InputDecoration(
        labelText: label,
        labelStyle: TextStyle(color: colors.textMuted, fontSize: 14),
        filled: true,
        fillColor: colors.card,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: colors.divider),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: colors.accent, width: 1.5),
        ),
      ),
    );
  }
}
