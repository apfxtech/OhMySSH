import 'dart:convert';
import 'dart:typed_data';

import 'package:dartssh2/dartssh2.dart';
import 'package:flutter/material.dart';

import '../../components/appbar.dart';
import '../../theme/theme.dart';
import '../../widgets/fields.dart';
import '../../services/log.dart';

const int kMaxEditableBytes = 1 << 20; // 1 MiB

class RemoteFileEditor extends StatefulWidget {
  const RemoteFileEditor({
    super.key,
    required this.sftp,
    required this.path,
    required this.name,
  });

  final SftpClient sftp;
  final String path;
  final String name;

  @override
  State<RemoteFileEditor> createState() => _RemoteFileEditorState();
}

class _RemoteFileEditorState extends State<RemoteFileEditor> {
  final _controller = TextEditingController();
  bool _loading = true;
  bool _saving = false;
  String? _error;
  String _original = '';

  /// Preserved so saving a CRLF file does not rewrite every line ending.
  bool _crlf = false;

  bool get _dirty => _controller.text != _original;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final file = await widget.sftp.open(widget.path);
      Uint8List bytes;
      try {
        final attrs = await file.stat();
        final size = attrs.size ?? 0;
        if (size > kMaxEditableBytes) {
          throw const FormatException('File is too large to edit here');
        }
        bytes = await file.readBytes();
      } finally {
        await file.close();
      }

      if (bytes.contains(0)) {
        throw const FormatException('This looks like a binary file');
      }

      var text = utf8.decode(bytes, allowMalformed: true);
      _crlf = text.contains('\r\n');
      if (_crlf) text = text.replaceAll('\r\n', '\n');

      if (!mounted) return;
      setState(() {
        _controller.text = text;
        _original = text;
        _loading = false;
      });
    } on FormatException catch (error) {
      Log.warn('editor', '${widget.path}: ${error.message}');
      if (mounted) {
        setState(() {
          _loading = false;
          _error = error.message;
        });
      }
    } catch (error, stackTrace) {
      Log.error('editor', 'open ${widget.path} failed', stackTrace);
      Log.error('editor', error);
      if (mounted) {
        setState(() {
          _loading = false;
          _error = '$error';
        });
      }
    }
  }

  Future<void> _save() async {
    if (_saving) return;
    setState(() => _saving = true);
    final text = _controller.text;
    try {
      final payload = utf8.encode(_crlf ? text.replaceAll('\n', '\r\n') : text);
      final file = await widget.sftp.open(
        widget.path,
        mode:
            SftpFileOpenMode.write |
            SftpFileOpenMode.create |
            SftpFileOpenMode.truncate,
      );
      try {
        await file.writeBytes(Uint8List.fromList(payload));
      } finally {
        await file.close();
      }
      if (!mounted) return;
      setState(() => _original = text);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Saved ${widget.name}')));
    } catch (error, stackTrace) {
      Log.error('editor', 'save ${widget.path} failed', stackTrace);
      Log.error('editor', error);
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Save failed: $error')));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<bool> _confirmDiscard() async {
    if (!_dirty) return true;
    return confirmDestructive(
      context,
      title: 'Discard changes?',
      message: '${widget.name} has unsaved edits.',
      actionLabel: 'Discard',
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        if (await _confirmDiscard() && mounted) {
          if (context.mounted) Navigator.of(context).pop();
        }
      },
      child: Scaffold(
        backgroundColor: colors.background,
        appBar: QPageAppBar(
          title: widget.name,
          subtitle: _dirty ? 'Modified' : widget.path,
          statusColor: _dirty ? colors.warning : null,
          actions: [
            if (!_loading && _error == null)
              QPageAppBarAction.native(
                tooltip: 'Save to server',
                icon: _saving
                    ? SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: colors.onAccent,
                        ),
                      )
                    : Icon(
                        Icons.cloud_upload_outlined,
                        color: colors.onAccent,
                        size: 21,
                      ),
                onPressed: _dirty && !_saving ? _save : null,
              ),
          ],
        ),
        body: switch ((_loading, _error)) {
          (true, _) => Center(
            child: CircularProgressIndicator(color: colors.accent),
          ),
          (_, final String error) => QEmptyView(
            icon: Icons.error_outline,
            title: 'Cannot edit this file',
            message: error,
          ),
          _ => Container(
            color: colors.terminalBackground,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: TextField(
              controller: _controller,
              onChanged: (_) => setState(() {}),
              maxLines: null,
              expands: true,
              keyboardType: TextInputType.multiline,
              textAlignVertical: TextAlignVertical.top,
              style: TextStyle(
                color: colors.terminalForeground,
                fontFamily: 'monospace',
                fontSize: 13,
                height: 1.4,
              ),
              cursorColor: colors.accent,
              decoration: const InputDecoration(
                border: InputBorder.none,
                isDense: true,
                contentPadding: EdgeInsets.zero,
              ),
            ),
          ),
        },
      ),
    );
  }
}
