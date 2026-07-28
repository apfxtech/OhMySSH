import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../components/icon.dart';
import '../theme/theme.dart';

class QTextField extends StatelessWidget {
  const QTextField({
    super.key,
    required this.controller,
    required this.label,
    this.hint,
    this.obscure = false,
    this.maxLines = 1,
    this.keyboardType,
    this.inputFormatters,
    this.autofocus = false,
  });

  final TextEditingController controller;
  final String label;
  final String? hint;
  final bool obscure;
  final int maxLines;
  final TextInputType? keyboardType;
  final List<TextInputFormatter>? inputFormatters;
  final bool autofocus;

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
      obscureText: obscure,
      maxLines: obscure ? 1 : maxLines,
      keyboardType: keyboardType,
      inputFormatters: inputFormatters,
      autofocus: autofocus,
      style: TextStyle(color: colors.textPrimary, fontSize: 15),
      decoration: InputDecoration(
        labelText: label,
        hintText: hint,
        hintStyle: TextStyle(color: colors.textMuted, fontSize: 14),
        labelStyle: TextStyle(color: colors.textMuted, fontSize: 14),
        filled: true,
        fillColor: colors.card,
        isDense: true,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 14,
          vertical: 14,
        ),
        border: border(colors.divider),
        enabledBorder: border(colors.divider),
        focusedBorder: border(colors.accent, 1.5),
      ),
    );
  }
}

class QFormLabel extends StatelessWidget {
  const QFormLabel(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(2, 18, 2, 8),
      child: Text(
        text.toUpperCase(),
        style: TextStyle(
          color: context.appColors.textMuted,
          fontSize: 11,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.6,
        ),
      ),
    );
  }
}

class QEmptyView extends StatelessWidget {
  const QEmptyView({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.action,
  });

  final String icon;
  final String title;
  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 40),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            QIcon(asset: icon, color: colors.textMuted, size: 44),
            const SizedBox(height: 16),
            Text(
              title,
              textAlign: TextAlign.center,
              style: TextStyle(
                color: colors.textSecondary,
                fontSize: 16,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              message,
              textAlign: TextAlign.center,
              style: TextStyle(
                color: colors.textMuted,
                fontSize: 13,
                height: 1.35,
              ),
            ),
            if (action != null) ...[const SizedBox(height: 18), action!],
          ],
        ),
      ),
    );
  }
}

Future<bool> confirmDestructive(
  BuildContext context, {
  required String title,
  required String message,
  String actionLabel = 'Delete',
}) async {
  final colors = context.appColors;
  final result = await showDialog<bool>(
    context: context,
    barrierColor: colors.dialogBarrier,
    builder: (dialogContext) => AlertDialog(
      backgroundColor: colors.dialogBackground,
      title: Text(title, style: TextStyle(color: colors.dialogText)),
      content: Text(message, style: TextStyle(color: colors.dialogMuted)),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(dialogContext).pop(false),
          child: const Text('Cancel'),
        ),
        TextButton(
          onPressed: () => Navigator.of(dialogContext).pop(true),
          style: TextButton.styleFrom(foregroundColor: colors.danger),
          child: Text(actionLabel),
        ),
      ],
    ),
  );
  return result ?? false;
}

Future<String?> promptForText(
  BuildContext context, {
  required String title,
  required String label,
  String initial = '',
  bool obscure = false,
  String actionLabel = 'Save',
}) async {
  final colors = context.appColors;
  final controller = TextEditingController(text: initial);
  try {
    return await showDialog<String>(
      context: context,
      barrierColor: colors.dialogBarrier,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: colors.dialogBackground,
        title: Text(title, style: TextStyle(color: colors.dialogText)),
        content: QTextField(
          controller: controller,
          label: label,
          obscure: obscure,
          autofocus: true,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () =>
                Navigator.of(dialogContext).pop(controller.text.trim()),
            child: Text(actionLabel),
          ),
        ],
      ),
    );
  } finally {
    controller.dispose();
  }
}
