import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

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
    this.onSubmitted,
  });

  final TextEditingController controller;
  final String label;
  final String? hint;
  final bool obscure;
  final int maxLines;
  final TextInputType? keyboardType;
  final List<TextInputFormatter>? inputFormatters;
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
      obscureText: obscure,
      maxLines: obscure ? 1 : maxLines,
      keyboardType: keyboardType,
      inputFormatters: inputFormatters,
      autofocus: autofocus,
      onSubmitted: onSubmitted,
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

  final IconData icon;
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
            Icon(icon, color: colors.textMuted, size: 44),
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

class PickOption<T> {
  const PickOption(
    this.value,
    this.label, {
    this.subtitle,
    this.icon,
    this.isAction = false,
  });

  final T value;
  final String label;
  final String? subtitle;
  final IconData? icon;

  final bool isAction;
}

Future<PickOption<T>?> pickFromList<T>(
  BuildContext context, {
  required String title,
  required List<PickOption<T>> options,
  required T current,
}) {
  final colors = context.appColors;
  return showDialog<PickOption<T>>(
    context: context,
    barrierColor: colors.dialogBarrier,
    builder: (dialogContext) => AlertDialog(
      backgroundColor: colors.dialogBackground,
      title: Text(title, style: TextStyle(color: colors.dialogText)),
      contentPadding: const EdgeInsets.fromLTRB(0, 12, 0, 8),
      content: SizedBox(
        width: 340,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.of(dialogContext).size.height * 0.5,
          ),
          child: ListView.builder(
            shrinkWrap: true,
            itemCount: options.length,
            itemBuilder: (context, index) {
              final option = options[index];
              final selected = !option.isAction && option.value == current;
              final tint = option.isAction ? colors.accent : colors.dialogText;
              return ListTile(
                dense: true,
                leading: option.icon == null
                    ? null
                    : Icon(
                        option.icon,
                        size: 20,
                        color: option.isAction
                            ? colors.accent
                            : colors.textMuted,
                      ),
                title: Text(
                  option.label,
                  style: TextStyle(
                    color: tint,
                    fontSize: 14,
                    fontWeight: option.isAction
                        ? FontWeight.w600
                        : FontWeight.w400,
                  ),
                ),
                subtitle: option.subtitle == null
                    ? null
                    : Text(
                        option.subtitle!,
                        style: TextStyle(
                          color: colors.dialogMuted,
                          fontSize: 12,
                        ),
                      ),
                trailing: selected
                    ? Icon(Icons.check, size: 18, color: colors.accent)
                    : null,
                onTap: () => Navigator.of(dialogContext).pop(option),
              );
            },
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(dialogContext).pop(),
          child: const Text('Cancel'),
        ),
      ],
    ),
  );
}

Future<String?> promptForText(
  BuildContext context, {
  required String title,
  required String label,
  String initial = '',
  bool obscure = false,
  String actionLabel = 'Save',
}) {
  return showDialog<String>(
    context: context,
    barrierColor: context.appColors.dialogBarrier,
    builder: (_) => _TextPrompt(
      title: title,
      label: label,
      initial: initial,
      obscure: obscure,
      actionLabel: actionLabel,
    ),
  );
}

/// The controller lives on the [State]: `showDialog`'s future completes on
/// pop(), but the dialog keeps rebuilding through its dismissal animation, so
/// disposing it in the caller uses it after disposal.
class _TextPrompt extends StatefulWidget {
  const _TextPrompt({
    required this.title,
    required this.label,
    required this.initial,
    required this.obscure,
    required this.actionLabel,
  });

  final String title;
  final String label;
  final String initial;
  final bool obscure;
  final String actionLabel;

  @override
  State<_TextPrompt> createState() => _TextPromptState();
}

class _TextPromptState extends State<_TextPrompt> {
  late final TextEditingController _controller = TextEditingController(
    text: widget.initial,
  );

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() => Navigator.of(context).pop(_controller.text.trim());

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return AlertDialog(
      backgroundColor: colors.dialogBackground,
      title: Text(widget.title, style: TextStyle(color: colors.dialogText)),
      content: QTextField(
        controller: _controller,
        label: widget.label,
        obscure: widget.obscure,
        autofocus: true,
        onSubmitted: (_) => _submit(),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        TextButton(onPressed: _submit, child: Text(widget.actionLabel)),
      ],
    );
  }
}
