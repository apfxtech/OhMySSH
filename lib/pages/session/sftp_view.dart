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
import 'file_editor.dart';
import '../../services/log.dart';

class SftpView extends StatefulWidget {
  const SftpView({super.key, required this.session});

  final HostSession session;

  @override
  State<SftpView> createState() => _SftpViewState();
}

class _SftpViewState extends State<SftpView> {
  SftpClient? _sftp;
  String _path = '.';
  List<SftpName> _folders = const [];
  List<SftpName> _files = const [];
  bool _loading = true;
  String? _error;
  _Transfer? _transfer;

  /// Keyed by filename, so it must be cleared on every navigation.
  final Set<String> _selected = <String>{};
  bool _selectionMode = false;

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
    } catch (error, stackTrace) {
      Log.error('sftp', 'could not open SFTP channel: $error', stackTrace);
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
      _selected.clear();
      _selectionMode = false;
    });
    try {
      final names = await sftp.listdir(path);
      names.removeWhere((n) => n.filename == '.' || n.filename == '..');

      int byName(SftpName a, SftpName b) =>
          a.filename.toLowerCase().compareTo(b.filename.toLowerCase());

      final folders = names.where((n) => n.attr.isDirectory).toList()
        ..sort(byName);
      final files = names.where((n) => !n.attr.isDirectory).toList()
        ..sort(byName);

      if (!mounted) return;
      setState(() {
        _path = path;
        _folders = folders;
        _files = files;
        _loading = false;
      });
    } catch (error, stackTrace) {
      Log.error('sftp', 'listdir $path failed: $error', stackTrace);
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = '$error';
      });
    }
  }

  String _join(String base, String name) =>
      base.endsWith('/') ? '$base$name' : '$base/$name';

  void _toggleSelect(SftpName entry) {
    setState(() {
      if (!_selected.add(entry.filename)) _selected.remove(entry.filename);
      _selectionMode = _selected.isNotEmpty;
    });
  }

  void _enterSelection(SftpName entry) {
    setState(() {
      _selectionMode = true;
      _selected
        ..clear()
        ..add(entry.filename);
    });
  }

  void _exitSelection() {
    setState(() {
      _selectionMode = false;
      _selected.clear();
    });
  }

  void _selectAll() {
    final all = [..._folders, ..._files].map((e) => e.filename);
    setState(() {
      if (_selected.length == all.length) {
        _selected.clear();
        _selectionMode = false;
      } else {
        _selected
          ..clear()
          ..addAll(all);
        _selectionMode = true;
      }
    });
  }

  List<SftpName> get _selectedEntries => [
    ..._folders,
    ..._files,
  ].where((e) => _selected.contains(e.filename)).toList();

  /// Directories are skipped: recursive download is not supported.
  Future<void> _downloadSelected() async {
    final entries = _selectedEntries.where((e) => !e.attr.isDirectory).toList();
    final skipped = _selected.length - entries.length;
    if (entries.isEmpty) {
      _toast('Select at least one file');
      return;
    }

    String? directory;
    if (_isDesktop) {
      directory = await FilePicker.getDirectoryPath(
        dialogTitle:
            'Download ${entries.length} file'
            '${entries.length == 1 ? '' : 's'} to…',
      );
      if (directory == null) return;
    }

    var done = 0;
    for (final entry in entries) {
      try {
        await _downloadOne(entry, directory: directory);
        done++;
      } catch (error, stackTrace) {
        _fail(entry.filename, error, stackTrace);
        break;
      }
    }

    if (mounted) setState(() => _transfer = null);
    _exitSelection();
    _toast(
      'Downloaded $done file${done == 1 ? '' : 's'}'
      '${skipped > 0 ? ' · $skipped folder${skipped == 1 ? '' : 's'} skipped' : ''}',
    );
  }

  /// Exactly one of [savePath] / [directory] may be set.
  Future<void> _downloadOne(
    SftpName entry, {
    String? savePath,
    String? directory,
  }) async {
    final sftp = _sftp!;
    setState(
      () =>
          _transfer = _Transfer(entry.filename, entry.attr.size ?? 0, 0, true),
    );

    final target =
        savePath ??
        (directory == null
            ? null
            : '$directory${Platform.pathSeparator}${entry.filename}');

    final file = await sftp.open(_join(_path, entry.filename));
    try {
      if (target != null) {
        await file.downloadTo(
          File(target).openWrite(),
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
  }

  Future<void> _downloadSingle(SftpName entry) async {
    String? savePath;
    if (_isDesktop) {
      savePath = await FilePicker.saveFile(
        dialogTitle: 'Save ${entry.filename}',
        fileName: entry.filename,
      );
      if (savePath == null) return;
    }

    try {
      await _downloadOne(entry, savePath: savePath);
      _toast('Downloaded ${entry.filename}');
    } catch (error, stackTrace) {
      _fail('Download failed', error, stackTrace);
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
      final payload = bytes;

      setState(() => _transfer = _Transfer(name, payload.length, 0, false));
      try {
        final remote = await sftp.open(
          _join(_path, name),
          mode:
              SftpFileOpenMode.create |
              SftpFileOpenMode.write |
              SftpFileOpenMode.truncate,
        );
        try {
          await remote.writeBytes(payload);
        } finally {
          await remote.close();
        }
      } catch (error, stackTrace) {
        _fail('Upload failed', error, stackTrace);
        break;
      }
    }

    if (mounted) setState(() => _transfer = null);
    await _listDir(_path);
  }

  Future<void> _deleteSelected() async {
    final entries = _selectedEntries;
    if (entries.isEmpty) return;
    final confirmed = await confirmDestructive(
      context,
      title: 'Delete ${entries.length} item${entries.length == 1 ? '' : 's'}?',
      message:
          'They will be removed on the remote system. '
          'This cannot be undone.',
    );
    if (!confirmed) return;

    final sftp = _sftp!;
    for (final entry in entries) {
      try {
        final target = _join(_path, entry.filename);
        if (entry.attr.isDirectory) {
          await sftp.rmdir(target);
        } else {
          await sftp.remove(target);
        }
      } catch (error, stackTrace) {
        _fail(entry.filename, error, stackTrace);
        break;
      }
    }
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
    } catch (error, stackTrace) {
      _fail('Delete failed', error, stackTrace);
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
    } catch (error, stackTrace) {
      _fail('Could not create', error, stackTrace);
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
    } catch (error, stackTrace) {
      _fail('Rename failed', error, stackTrace);
    }
  }

  Future<void> _edit(SftpName entry) async {
    final sftp = _sftp;
    if (sftp == null) return;
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => RemoteFileEditor(
          sftp: sftp,
          path: _join(_path, entry.filename),
          name: entry.filename,
        ),
      ),
    );
    if (mounted) await _listDir(_path);
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

  void _fail(String what, Object error, [StackTrace? stackTrace]) {
    Log.error('sftp', '$what: $error', stackTrace);
    _toast('$what: $error');
  }

  static bool get _isDesktop =>
      Platform.isMacOS || Platform.isWindows || Platform.isLinux;

  void _openEntry(SftpName entry) {
    if (_selectionMode) {
      _toggleSelect(entry);
      return;
    }
    if (entry.attr.isDirectory) {
      _listDir(_join(_path, entry.filename));
      return;
    }
    if (isProbablyText(entry)) {
      _edit(entry);
    } else {
      _downloadSingle(entry);
    }
  }

  void _runAction(_EntryAction action, SftpName entry) {
    switch (action) {
      case _EntryAction.edit:
        _edit(entry);
      case _EntryAction.download:
        _downloadSingle(entry);
      case _EntryAction.rename:
        _rename(entry);
      case _EntryAction.delete:
        _delete(entry);
      case _EntryAction.select:
        _enterSelection(entry);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;

    return Stack(
      children: [
        Column(
          children: [
            if (_selectionMode)
              _SelectionBar(
                count: _selected.length,
                onCancel: _exitSelection,
                onSelectAll: _selectAll,
                onDownload: _downloadSelected,
                onDelete: _deleteSelected,
              )
            else
              _Breadcrumbs(path: _path, onNavigate: _listDir),
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
                _ when _folders.isEmpty && _files.isEmpty => const QEmptyView(
                  icon: Icons.folder_open,
                  title: 'Empty directory',
                  message: 'Nothing here yet.',
                ),
                _ => ListView(
                  padding: const EdgeInsets.only(top: 10, bottom: 170),
                  children: [
                    if (_folders.isNotEmpty)
                      _section('Folders (${_folders.length})', _folders),
                    if (_folders.isNotEmpty && _files.isNotEmpty)
                      const SizedBox(height: 14),
                    if (_files.isNotEmpty)
                      _section('Files (${_files.length})', _files),
                  ],
                ),
              },
            ),
          ],
        ),
        if (!_selectionMode)
          Positioned(
            right: 16,
            bottom: 16,
            child: _FloatingActions(
              onNewFolder: _sftp == null ? null : _makeDirectory,
              onUpload: _sftp == null ? null : _upload,
              onRefresh: () => _listDir(_path),
            ),
          ),
      ],
    );
  }

  Widget _section(String title, List<SftpName> entries) =>
      GroupedCardList<SftpName>(
        title: title,
        items: entries,
        onTap: (entry) =>
            () => _openEntry(entry),
        itemBuilder: (context, entry) => _EntryRow(
          entry: entry,
          selected: _selected.contains(entry.filename),
          selectionMode: _selectionMode,
          onAction: (action) => _runAction(action, entry),
          onLongPress: () => _enterSelection(entry),
        ),
      );
}

enum _EntryAction { edit, download, rename, delete, select }

bool isProbablyText(SftpName entry) {
  final size = entry.attr.size ?? 0;
  if (size > kMaxEditableBytes) return false;
  final name = entry.filename;
  final dot = name.lastIndexOf('.');
  if (dot <= 0) return true;
  return const {
    'txt',
    'md',
    'log',
    'conf',
    'cfg',
    'ini',
    'yaml',
    'yml',
    'toml',
    'json',
    'xml',
    'sh',
    'bash',
    'zsh',
    'fish',
    'py',
    'pl',
    'rb',
    'lua',
    'js',
    'ts',
    'c',
    'h',
    'cpp',
    'hpp',
    'go',
    'rs',
    'java',
    'kt',
    'php',
    'sql',
    'env',
    'service',
    'socket',
    'timer',
    'rules',
    'list',
    'repo',
    'properties',
    'gitignore',
    'dockerignore',
    'csv',
    'tsv',
  }.contains(name.substring(dot + 1).toLowerCase());
}

class _Breadcrumbs extends StatelessWidget {
  const _Breadcrumbs({required this.path, required this.onNavigate});

  final String path;
  final ValueChanged<String> onNavigate;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final segments = path.split('/').where((s) => s.isNotEmpty).toList();
    final crumbs = <Widget>[];

    Widget chip(Widget child, String target, {required bool isLast}) => InkWell(
      onTap: isLast ? null : () => onNavigate(target),
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
        child: child,
      ),
    );

    crumbs.add(
      chip(
        Icon(
          Icons.home_filled,
          size: 18,
          color: segments.isEmpty ? colors.accent : colors.textSecondary,
        ),
        '/',
        isLast: segments.isEmpty,
      ),
    );

    var cumulative = '';
    for (var i = 0; i < segments.length; i++) {
      cumulative += '/${segments[i]}';
      final isLast = i == segments.length - 1;
      crumbs.add(Icon(Icons.chevron_right, size: 16, color: colors.textMuted));
      crumbs.add(
        chip(
          Text(
            segments[i],
            style: TextStyle(
              color: isLast ? colors.accent : colors.textSecondary,
              fontWeight: isLast ? FontWeight.w700 : FontWeight.w500,
              fontSize: 13.5,
            ),
          ),
          cumulative,
          isLast: isLast,
        ),
      );
    }

    return Container(
      height: 40,
      width: double.infinity,
      alignment: Alignment.centerLeft,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        reverse: true,
        padding: const EdgeInsets.symmetric(horizontal: 8),
        child: Row(mainAxisSize: MainAxisSize.min, children: crumbs),
      ),
    );
  }
}

class _SelectionBar extends StatelessWidget {
  const _SelectionBar({
    required this.count,
    required this.onCancel,
    required this.onSelectAll,
    required this.onDownload,
    required this.onDelete,
  });

  final int count;
  final VoidCallback onCancel;
  final VoidCallback onSelectAll;
  final VoidCallback onDownload;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return Container(
      height: 40,
      color: colors.accent.withValues(alpha: 0.14),
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: Row(
        children: [
          IconButton(
            tooltip: 'Cancel',
            onPressed: onCancel,
            icon: Icon(Icons.close, size: 18, color: colors.textSecondary),
          ),
          Expanded(
            child: Text(
              '$count selected',
              style: TextStyle(
                color: colors.textPrimary,
                fontSize: 13.5,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          IconButton(
            tooltip: 'Select all',
            onPressed: onSelectAll,
            icon: Icon(Icons.select_all, size: 19, color: colors.textSecondary),
          ),
          IconButton(
            tooltip: 'Download',
            onPressed: onDownload,
            icon: Icon(Icons.download, size: 19, color: colors.accent),
          ),
          IconButton(
            tooltip: 'Delete',
            onPressed: onDelete,
            icon: Icon(Icons.delete_outline, size: 19, color: colors.danger),
          ),
        ],
      ),
    );
  }
}

class _FloatingActions extends StatelessWidget {
  const _FloatingActions({
    required this.onNewFolder,
    required this.onUpload,
    required this.onRefresh,
  });

  final VoidCallback? onNewFolder;
  final VoidCallback? onUpload;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _FloatingAction(
          tooltip: 'New folder',
          icon: Icons.create_new_folder_outlined,
          onPressed: onNewFolder,
          background: colors.card,
          foreground: colors.textSecondary,
        ),
        const SizedBox(height: 10),
        _FloatingAction(
          tooltip: 'Refresh',
          icon: Icons.refresh,
          onPressed: onRefresh,
          background: colors.card,
          foreground: colors.textSecondary,
        ),
        const SizedBox(height: 10),
        _FloatingAction(
          tooltip: 'Upload',
          icon: Icons.upload,
          onPressed: onUpload,
          background: colors.accent,
          foreground: colors.onAccent,
        ),
      ],
    );
  }
}

class _FloatingAction extends StatelessWidget {
  const _FloatingAction({
    required this.tooltip,
    required this.icon,
    required this.onPressed,
    required this.background,
    required this.foreground,
  });

  final String tooltip;
  final IconData icon;
  final VoidCallback? onPressed;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) {
    final disabled = onPressed == null;
    return FloatingActionButton.small(
      // Several of these share one route and would all fly to the same hero on
      // a page transition.
      heroTag: null,
      tooltip: tooltip,
      elevation: 3,
      backgroundColor: disabled
          ? background.withValues(alpha: 0.5)
          : background,
      foregroundColor: disabled
          ? foreground.withValues(alpha: 0.4)
          : foreground,
      onPressed: onPressed,
      child: Icon(icon, size: 20),
    );
  }
}

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

class _EntryRow extends StatelessWidget {
  const _EntryRow({
    required this.entry,
    required this.selected,
    required this.selectionMode,
    required this.onAction,
    required this.onLongPress,
  });

  final SftpName entry;
  final bool selected;
  final bool selectionMode;
  final ValueChanged<_EntryAction> onAction;
  final VoidCallback onLongPress;

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
      if (attr.mode != null) _formatMode(attr.mode!),
    ];

    return GestureDetector(
      onLongPress: onLongPress,
      behavior: HitTestBehavior.opaque,
      child: Row(
        children: [
          if (selectionMode)
            Padding(
              padding: const EdgeInsets.only(right: 6),
              child: Icon(
                selected ? Icons.check_circle : Icons.circle_outlined,
                size: 22,
                color: selected ? colors.accent : colors.textMuted,
              ),
            ),
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
                    fontWeight: isDir ? FontWeight.w600 : FontWeight.w400,
                  ),
                ),
                if (parts.isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text(
                    parts.join(' · '),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
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
          if (isDir && !selectionMode)
            Icon(Icons.chevron_right, color: colors.textMuted, size: 18),
          if (!selectionMode)
            PopupMenuButton<_EntryAction>(
              tooltip: 'Actions',
              onSelected: onAction,
              color: colors.dialogBackground,
              position: PopupMenuPosition.under,
              icon: Icon(Icons.more_vert, color: colors.textMuted, size: 18),
              itemBuilder: (context) => [
                if (!isDir && isProbablyText(entry))
                  _menuItem(
                    colors,
                    _EntryAction.edit,
                    Icons.edit_note,
                    'Edit',
                    colors.accent,
                  ),
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
                  _EntryAction.select,
                  Icons.checklist,
                  'Select',
                  colors.textSecondary,
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
      ),
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

  static String _formatMode(SftpFileMode mode) {
    String bit(bool on, String ch) => on ? ch : '-';
    return '${bit(mode.userRead, 'r')}${bit(mode.userWrite, 'w')}'
        '${bit(mode.userExecute, 'x')}'
        '${bit(mode.groupRead, 'r')}${bit(mode.groupWrite, 'w')}'
        '${bit(mode.groupExecute, 'x')}'
        '${bit(mode.otherRead, 'r')}${bit(mode.otherWrite, 'w')}'
        '${bit(mode.otherExecute, 'x')}';
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
