import 'package:flutter/material.dart';

import '../components/icon.dart';
import '../theme/theme.dart';

class PasswordForm extends StatefulWidget {
  const PasswordForm({
    super.key,
    required this.message,
    required this.actionLabel,
    required this.onSubmit,
    this.title,
    this.confirm = false,
    this.minLength = 0,
    this.onCancel,
  });

  final String? title;

  final String message;
  final String actionLabel;

  final bool confirm;
  final int minLength;

  /// Returns an error to display, or null on success. Async so the caller can
  /// verify against the vault before the form dismisses.
  final Future<String?> Function(String password) onSubmit;

  final VoidCallback? onCancel;

  @override
  State<PasswordForm> createState() => _PasswordFormState();
}

class _PasswordFormState extends State<PasswordForm> {
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
    if (_busy) return;
    final password = _password.text;

    if (password.isEmpty) {
      setState(() => _error = 'Enter a master password');
      return;
    }
    if (widget.minLength > 0 && password.length < widget.minLength) {
      setState(() => _error = 'Use at least ${widget.minLength} characters');
      return;
    }
    if (widget.confirm && password != _confirm.text) {
      setState(() => _error = 'Passwords do not match');
      return;
    }

    setState(() {
      _busy = true;
      _error = null;
    });
    final problem = await widget.onSubmit(password);
    if (!mounted) return;
    setState(() {
      _busy = false;
      _error = problem;
    });
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Center(
          child: QIconBadge(
            icon: Icons.lock_outline,
            color: colors.accent,
            size: 60,
            iconSize: 32,
            borderRadius: 16,
          ),
        ),
        const SizedBox(height: 18),
        if (widget.title != null) ...[
          Text(
            widget.title!,
            textAlign: TextAlign.center,
            style: TextStyle(
              color: colors.textPrimary,
              fontSize: 22,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
        ],
        Text(
          widget.message,
          textAlign: TextAlign.center,
          style: TextStyle(color: colors.textMuted, fontSize: 13, height: 1.35),
        ),
        const SizedBox(height: 22),
        _Field(
          controller: _password,
          label: 'Master password',
          autofocus: true,
          onSubmitted: widget.confirm ? null : (_) => _submit(),
        ),
        if (widget.confirm) ...[
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
        Row(
          children: [
            if (widget.onCancel != null) ...[
              Expanded(
                child: SizedBox(
                  height: 46,
                  child: TextButton(
                    onPressed: _busy ? null : widget.onCancel,
                    style: TextButton.styleFrom(
                      foregroundColor: colors.textSecondary,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: const Text('Cancel'),
                  ),
                ),
              ),
              const SizedBox(width: 10),
            ],
            Expanded(
              flex: 2,
              child: SizedBox(
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
                      : Text(widget.actionLabel),
                ),
              ),
            ),
          ],
        ),
      ],
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
    OutlineInputBorder border(Color color, [double width = 1]) =>
        OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: color, width: width),
        );

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
        border: border(colors.divider),
        enabledBorder: border(colors.divider),
        focusedBorder: border(colors.accent, 1.5),
      ),
    );
  }
}

/// [verify] runs before the dialog closes and returns an error to show inline.
Future<String?> promptForPassword(
  BuildContext context, {
  required String message,
  String actionLabel = 'Confirm',
  bool confirm = false,
  int minLength = 0,
  Future<String?> Function(String password)? verify,
}) {
  final colors = context.appColors;
  return showDialog<String>(
    context: context,
    barrierColor: colors.dialogBarrier,
    builder: (dialogContext) => Dialog(
      backgroundColor: colors.background,
      insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 380),
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 28, 24, 20),
          child: PasswordForm(
            message: message,
            actionLabel: actionLabel,
            confirm: confirm,
            minLength: minLength,
            onCancel: () => Navigator.of(dialogContext).pop(),
            onSubmit: (password) async {
              final problem = await verify?.call(password);
              if (problem != null) return problem;
              if (dialogContext.mounted) {
                Navigator.of(dialogContext).pop(password);
              }
              return null;
            },
          ),
        ),
      ),
    ),
  );
}
