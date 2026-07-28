import 'dart:convert';
import 'dart:io';

import 'package:dartssh2/dartssh2.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../components/icon.dart';
import '../data/models.dart';
import '../theme/theme.dart';
import 'fields.dart';

class CredentialsController extends ChangeNotifier {
  CredentialsController([Identity? initial]) {
    load(initial);
  }

  final username = TextEditingController();
  final password = TextEditingController();
  final passphrase = TextEditingController();

  String? _privateKey;
  String? _keyStatus;

  /// Derived rather than chosen: a key in hand means key auth, and without one
  /// the password takes over.
  AuthKind get kind =>
      _privateKey == null ? AuthKind.password : AuthKind.privateKey;

  String? get privateKey => _privateKey;
  String? get keyStatus => _keyStatus;

  bool get isEmpty =>
      username.text.trim().isEmpty &&
      password.text.isEmpty &&
      _privateKey == null;

  void load(Identity? identity) {
    username.text = identity?.username ?? '';
    password.text = identity?.password ?? '';
    passphrase.text = identity?.passphrase ?? '';
    _privateKey = identity?.privateKey;
    _keyStatus = _privateKey == null ? null : describeKey(_privateKey!);
    notifyListeners();
  }

  void setPrivateKey(String? pem) {
    _privateKey = pem;
    _keyStatus = pem == null ? null : describeKey(pem);
    notifyListeners();
  }

  /// Returns an error message, or null when the form is usable.
  String? validate() {
    if (username.text.trim().isEmpty) return 'Username is required';
    return null;
  }

  Identity build({required String id, String? label}) {
    final user = username.text.trim();
    final resolved = (label ?? '').trim();
    final key = _privateKey;
    return Identity(
      id: id,
      label: resolved.isEmpty ? user : resolved,
      username: user,
      kind: kind,
      password: key == null && password.text.isNotEmpty ? password.text : null,
      privateKey: key,
      passphrase: key != null && passphrase.text.isNotEmpty
          ? passphrase.text
          : null,
    );
  }

  static String describeKey(String pem) {
    if (SSHKeyPair.isEncryptedPem(pem)) {
      return 'Encrypted key loaded — passphrase required';
    }
    try {
      final keys = SSHKeyPair.fromPem(pem);
      final types = keys.map((k) => k.type).toSet().join(', ');
      return 'Key loaded${types.isEmpty ? '' : ' · $types'}';
    } catch (error) {
      return 'Key could not be parsed: $error';
    }
  }

  @override
  void dispose() {
    username.dispose();
    password.dispose();
    passphrase.dispose();
    super.dispose();
  }
}

class CredentialsEditor extends StatelessWidget {
  const CredentialsEditor({
    super.key,
    required this.controller,
    this.usernameHint = 'root',
    this.autofocusUsername = false,
  });

  final CredentialsController controller;
  final String usernameHint;
  final bool autofocusUsername;

  Future<void> _importKey(BuildContext context) async {
    final result = await FilePicker.pickFiles(
      dialogTitle: 'Select a private key',
      withData: true,
    );
    if (result == null || result.files.isEmpty) return;

    final picked = result.files.single;
    List<int>? bytes = picked.bytes;
    if (bytes == null && picked.path != null) {
      bytes = await File(picked.path!).readAsBytes();
    }
    if (bytes == null) return;

    final pem = utf8.decode(bytes, allowMalformed: true);
    if (!pem.contains('PRIVATE KEY')) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('That file does not look like a private key'),
        ),
      );
      return;
    }
    controller.setPrivateKey(pem);
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          QTextField(
            controller: controller.username,
            label: 'Username',
            hint: usernameHint,
            autofocus: autofocusUsername,
          ),
          const SizedBox(height: 12),
          _KeyCard(
            status: controller.keyStatus,
            onImport: () => _importKey(context),
            onClear: controller.privateKey == null
                ? null
                : () => controller.setPrivateKey(null),
          ),
          const SizedBox(height: 10),
          // No key means password auth, so the two fields swap places instead
          // of asking for a mode first.
          if (controller.privateKey == null)
            QTextField(
              controller: controller.password,
              label: 'Password',
              obscure: true,
            )
          else
            QTextField(
              controller: controller.passphrase,
              label: 'Key passphrase (if encrypted)',
              obscure: true,
            ),
        ],
      ),
    );
  }
}

class _KeyCard extends StatelessWidget {
  const _KeyCard({required this.status, required this.onImport, this.onClear});

  final String? status;
  final VoidCallback onImport;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final loaded = status != null;
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 12, 8, 12),
      decoration: BoxDecoration(
        color: colors.card,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          QIconBadge(
            icon: Icons.vpn_key_outlined,
            color: loaded ? colors.success : colors.textMuted,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              status ?? 'No private key — the password below is used',
              style: TextStyle(
                color: loaded ? colors.textPrimary : colors.textMuted,
                fontSize: 13,
                height: 1.3,
              ),
            ),
          ),
          if (onClear != null)
            TextButton(
              onPressed: onClear,
              style: TextButton.styleFrom(foregroundColor: colors.danger),
              child: const Text('Clear'),
            ),
          TextButton(onPressed: onImport, child: const Text('Import')),
        ],
      ),
    );
  }
}
