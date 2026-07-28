import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:dartssh2/dartssh2.dart';
import 'package:file_picker/file_picker.dart';
import 'package:file_saver/file_saver.dart';
import 'package:flutter/material.dart';

import '../../components/cardlist.dart';
import '../../components/icon.dart';
import '../../ssh/session.dart';
import '../../theme/theme.dart';
import '../../widgets/fields.dart';

class SftpView extends StatefulWidget {
  const SftpView({super.key, required this.session});

  final HostSession session;

  @override
  State<SftpView> createState() => _SftpViewState();
}

class _SftpViewState extends State<SftpView> {
  SftpClient? _sftp;
  String _path = '.';
  List<SftpName> _entries = const [];
  bool _loading = true;
  String? _error;
  _Transfer? _transfer;

  @override
  void initState() {
    super.initState();
    _bootstrap();
  }

  Future<void> _bootstrap() async {
    try {
      final sftp = await widget.session.sftp();
      final home = await sftp.absolute('.');
      if (!mounted) return;
      _sftp = sftp;
      await _listDir(home);
    } catch (error) {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = '$error';
        });
      }
    }
  }

  Future<void> _listDir(String path) async {
    final sftp = _sftp;
    if (sftp == null) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final names = await sftp.listdir(path);
      names.removeWhere((n) => n.filename == '.' || n.filename == '..');
      names.sort((a, b) {
        final aDir = a.attr.isDirectory;
        final bDir = b.attr.isDirectory;
        if (aDir != bDir) return aDir ? -1 : 1;
        return a.filename.toLowerCase().compareTo(b.filename.toLowerCase());
      });
      if (!mounted) return;
      setState(() {
        _path = path;
        _entries = names;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = '$error';
      });
    }
  }

  String _join(String base, String name) =>
      base.endsWith('/') ? '$base$name' : '$base/$name';

  String get _parentPath {
    final trimmed = _path.endsWith('/') && _path.length > 1
        ? _path.substring(0, _path.length - 1)
        : _path;
    final cut = trimmed.lastIndexOf('/');
    if (cut <= 0) return '/';
    return trimmed.substring(0, cut);
  }

  Future<void> _download(SftpName entry) async {
    final sftp = _sftp;
    if (sftp == null) return;
    final remotePath = _join(_path, entry.filename);
    final size = entry.attr.size ?? 0;

    String? destination;
    if (Platform.isMacOS || Platform.isWindows || Platform.isLinux) {
      destination = await FilePicker.saveFile(
        dialogTitle: 'Save ${entry.filename}',
        fileName: entry.filename,
      );
      if (destination == null) return;
    }

    setState(() => _transfer = _Transfer(entry.filename, size, 0, true));
    try {
      final file = await sftp.open(remotePath);
      try {
        if (destination != null) {
          final sink = File(destination).openWrite();
          await file.downloadTo(
            sink,
            onProgress: _onProgress,
            closeDestination: true,
          );
        } else {
          final bytes = await file.readBytes();
          await FileSaver.instance.saveAs(
            name: entry.filename,
            bytes: Uint8List.fromList(bytes),
            mimeType: MimeType.other,
            includeExtension: false,
          );
        }
      } finally {
        await file.close();
      }
      _toast('Downloaded ${entry.filename}');
    } catch (error) {
      _toast('Download failed: $error');
    } finally {
      if (mounted) setState(() => _transfer = null);
    }
  }

  Future<void> _upload() async {
    final sftp = _sftp;
    if (sftp == null) return;
    final picked = await FilePicker.pickFiles(
      dialogTitle: 'Upload to $_path',
      allowMultiple: true,
      withData: true,
    );
    if (picked == null || picked.files.isEmpty) return;

    for (final platformFile in picked.files) {
      final name = platformFile.name;
      Uint8List? bytes = platformFile.bytes;
      if (bytes == null && platformFile.path != null) {
        bytes = await File(platformFile.path!).readAsBytes();
      }
      if (bytes == null) continue;

      setState(() => _transfer = _Transfer(name, bytes!.length, 0, false));
      try {
        final remote = await sftp.open(
          _join(_path, name),
          mode:
              SftpFileOpenMode.create |
              SftpFileOpenMode.write |
              SftpFileOpenMode.truncate,
        );
        try {
          await remote.writeBytes(bytes);
        } finally {
          await remote.close();
        }
      } catch (error) {
        _toast('Upload failed: $error');
        break;
      }
    }

    if (mounted) setState(() => _transfer = null);
    await _listDir(_path);
  }

  Future<void> _delete(SftpName entry) async {
    final sftp = _sftp;
    if (sftp == null) return;
    final isDir = entry.attr.isDirectory;
    final confirmed = await confirmDestructive(
      context,
      title: isDir ? 'Delete directory?' : 'Delete file?',
      message:
          '${_join(_path, entry.filename)} will be removed on the remote '
          'system. This cannot be undone.',
    );
    if (!confirmed) return;

    try {
      final target = _join(_path, entry.filename);
      if (isDir) {
        await sftp.rmdir(target);
      } else {
        await sftp.remove(target);
      }
      await _listDir(_path);
    } catch (error) {
      _toast('Delete failed: $error');
    }
  }

  Future<void> _makeDirectory() async {
    final sftp = _sftp;
    if (sftp == null) return;
    final name = await promptForText(
      context,
      title: 'New directory',
      label: 'Name',
      actionLabel: 'Create',
    );
    if (name == null || name.isEmpty) return;
    try {
      await sftp.mkdir(_join(_path, name));
      await _listDir(_path);
    } catch (error) {
      _toast('Could not create: $error');
    }
  }

  Future<void> _rename(SftpName entry) async {
    final sftp = _sftp;
    if (sftp == null) return;
    final name = await promptForText(
      context,
      title: 'Rename',
      label: 'New name',
      initial: entry.filename,
      actionLabel: 'Rename',
    );
    if (name == null || name.isEmpty || name == entry.filename) return;
    try {
      await sftp.rename(_join(_path, entry.filename), _join(_path, name));
      await _listDir(_path);
    } catch (error) {
      _toast('Rename failed: $error');
    }
  }

  void _onProgress(int bytes) {
    final transfer = _transfer;
    if (transfer == null || !mounted) return;
    setState(() => _transfer = transfer.withProgress(bytes));
  }

  void _toast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;

    return Column(
      children: [
        _Toolbar(
          path: _path,
          onUp: _path == '/' ? null : () => _listDir(_parentPath),
          onRefresh: () => _listDir(_path),
          onUpload: _sftp == null ? null : _upload,
          onNewFolder: _sftp == null ? null : _makeDirectory,
        ),
        if (_transfer != null) _TransferBar(transfer: _transfer!),
        Expanded(
          child: switch ((_loading, _error)) {
            (true, _) => Center(
              child: CircularProgressIndicator(color: colors.accent),
            ),
            (_, final String error) => QEmptyView(
              icon: Icons.error_outline,
              title: 'Could not read this directory',
              message: error,
            ),
            _ when _entries.isEmpty => const QEmptyView(
              icon: Icons.folder,
              title: 'Empty directory',
              message: 'Nothing here yet.',
            ),
            _ => SingleChildScrollView(
              padding: const EdgeInsets.only(top: 10, bottom: 20),
              child: GroupedCardList<SftpName>(
                items: _entries,
                onTap: (entry) => () {
                  if (entry.attr.isDirectory) {
                    _listDir(_join(_path, entry.filename));
                  } else {
                    _download(entry);
                  }
                },
                itemBuilder: (context, entry) => _EntryRow(
                  entry: entry,
                  onAction: (action) => _runAction(action, entry),
                ),
              ),
            ),
          },
        ),
      ],
    );
  }

  void _runAction(_EntryAction action, SftpName entry) {
    switch (action) {
      case _EntryAction.download:
        _download(entry);
      case _EntryAction.rename:
        _rename(entry);
      case _EntryAction.delete:
        _delete(entry);
    }
  }
}

enum _EntryAction { download, rename, delete }

class _Transfer {
  const _Transfer(this.name, this.total, this.done, this.isDownload);

  final String name;
  final int total;
  final int done;
  final bool isDownload;

  double? get ratio => total <= 0 ? null : (done / total).clamp(0.0, 1.0);

  _Transfer withProgress(int bytes) =>
      _Transfer(name, total, bytes, isDownload);
}

class _TransferBar extends StatelessWidget {
  const _TransferBar({required this.transfer});

  final _Transfer transfer;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              SizedBox(
                width: 14,
                height: 14,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: colors.accent,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  '${transfer.isDownload ? 'Downloading' : 'Uploading'} '
                  '${transfer.name}  ${formatBytes(transfer.done)}'
                  '${transfer.total > 0 ? ' / ${formatBytes(transfer.total)}' : ''}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(color: colors.textSecondary, fontSize: 12.5),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: transfer.ratio,
              minHeight: 4,
              color: colors.accent,
              backgroundColor: colors.divider,
            ),
          ),
        ],
      ),
    );
  }
}

class _Toolbar extends StatelessWidget {
  const _Toolbar({
    required this.path,
    required this.onUp,
    required this.onRefresh,
    required this.onUpload,
    required this.onNewFolder,
  });

  final String path;
  final VoidCallback? onUp;
  final VoidCallback onRefresh;
  final VoidCallback? onUpload;
  final VoidCallback? onNewFolder;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return Container(
      color: colors.card,
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
      child: Row(
        children: [
          IconButton(
            tooltip: 'Up',
            onPressed: onUp,
            icon: Icon(
              Icons.arrow_upward,
              color: onUp == null ? colors.textMuted : colors.textSecondary,
              size: 18,
            ),
          ),
          Expanded(
            child: Text(
              path,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: colors.textSecondary,
                fontSize: 12.5,
                fontFamily: 'monospace',
              ),
            ),
          ),
          IconButton(
            tooltip: 'New folder',
            onPressed: onNewFolder,
            icon: Icon(
              Icons.create_new_folder_outlined,
              color: colors.textSecondary,
              size: 18,
            ),
          ),
          IconButton(
            tooltip: 'Upload',
            onPressed: onUpload,
            icon: Icon(Icons.upload, color: colors.accent, size: 18),
          ),
          IconButton(
            tooltip: 'Refresh',
            onPressed: onRefresh,
            icon: Icon(Icons.refresh, color: colors.textSecondary, size: 18),
          ),
        ],
      ),
    );
  }
}

class _EntryRow extends StatelessWidget {
  const _EntryRow({required this.entry, required this.onAction});

  final SftpName entry;
  final ValueChanged<_EntryAction> onAction;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final attr = entry.attr;
    final isDir = attr.isDirectory;
    final isLink = attr.isSymbolicLink;

    final icon = isDir
        ? Icons.folder
        : isLink
        ? Icons.link
        : _iconForName(entry.filename);

    final parts = <String>[
      if (!isDir && attr.size != null) formatBytes(attr.size!),
      if (attr.modifyTime != null) _formatTime(attr.modifyTime!),
    ];

    return Row(
      children: [
        QIconBadge(
          icon: icon,
          color: isDir ? colors.info : colors.textMuted,
          size: 32,
          iconSize: 20,
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                entry.filename,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: colors.textPrimary,
                  fontSize: 14,
                  height: 1.2,
                ),
              ),
              if (parts.isNotEmpty) ...[
                const SizedBox(height: 2),
                Text(
                  parts.join(' · '),
                  style: TextStyle(
                    color: colors.textMuted,
                    fontSize: 11.5,
                    height: 1.2,
                  ),
                ),
              ],
            ],
          ),
        ),
        PopupMenuButton<_EntryAction>(
          tooltip: 'Actions',
          onSelected: onAction,
          color: colors.dialogBackground,
          position: PopupMenuPosition.under,
          icon: Icon(Icons.more_vert, color: colors.textMuted, size: 18),
          itemBuilder: (context) => [
            if (!isDir)
              _menuItem(
                colors,
                _EntryAction.download,
                Icons.download,
                'Download',
                colors.accent,
              ),
            _menuItem(
              colors,
              _EntryAction.rename,
              Icons.drive_file_rename_outline,
              'Rename',
              colors.info,
            ),
            _menuItem(
              colors,
              _EntryAction.delete,
              Icons.delete_outline,
              'Delete',
              colors.danger,
              textColor: colors.danger,
            ),
          ],
        ),
      ],
    );
  }

  PopupMenuItem<_EntryAction> _menuItem(
    QAppColors colors,
    _EntryAction action,
    IconData icon,
    String label,
    Color iconColor, {
    Color? textColor,
  }) => PopupMenuItem<_EntryAction>(
    value: action,
    height: 42,
    child: Row(
      children: [
        Icon(icon, size: 18, color: iconColor),
        const SizedBox(width: 12),
        Text(
          label,
          style: TextStyle(color: textColor ?? colors.dialogText, fontSize: 14),
        ),
      ],
    ),
  );

  static IconData _iconForName(String name) {
    final dot = name.lastIndexOf('.');
    final ext = dot < 0 ? '' : name.substring(dot + 1).toLowerCase();
    return switch (ext) {
      'zip' ||
      'gz' ||
      'tar' ||
      'xz' ||
      'bz2' ||
      '7z' => Icons.folder_zip_outlined,
      'png' ||
      'jpg' ||
      'jpeg' ||
      'gif' ||
      'webp' ||
      'svg' => Icons.image_outlined,
      'sh' || 'bash' || 'zsh' || 'py' || 'pl' || 'rb' => Icons.code,
      'conf' ||
      'cfg' ||
      'ini' ||
      'yaml' ||
      'yml' ||
      'toml' ||
      'json' => Icons.tune,
      'log' || 'txt' || 'md' => Icons.description_outlined,
      'bin' || 'so' || 'o' || 'exe' || 'dll' => Icons.data_object,
      _ => Icons.insert_drive_file_outlined,
    };
  }

  static String _formatTime(int epochSeconds) {
    final time = DateTime.fromMillisecondsSinceEpoch(epochSeconds * 1000);
    String two(int value) => value.toString().padLeft(2, '0');
    return '${time.year}-${two(time.month)}-${two(time.day)} '
        '${two(time.hour)}:${two(time.minute)}';
  }
}

String formatBytes(int bytes) {
  if (bytes < 1024) return '$bytes B';
  const units = ['KB', 'MB', 'GB', 'TB'];
  var value = bytes / 1024;
  var unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return '${value.toStringAsFixed(value >= 10 ? 0 : 1)} ${units[unit]}';
}
