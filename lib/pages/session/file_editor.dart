import 'dart:convert';
import 'dart:math';

import 'package:dartssh2/dartssh2.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

import '../../components/appbar.dart';
import '../../theme/theme.dart';
import '../../widgets/fields.dart';
import '../../services/log.dart';

const int kMaxEditableBytes = 1 << 20; // 1 MiB

const double _kFontSize = 13;
const double _kLineHeight = _kFontSize * 1.4;
const double _kTopPadding = 10;
const double _kLeftPadding = 8;
const double _kRightPadding = 12;
const double _kBottomPadding = 12;

/// Above this the O(n·m) LCS is skipped and the whole changed region is marked.
const int _kMaxDiffLines = 1500;

RenderEditable? findRenderEditable(RenderObject? object) {
  if (object is RenderEditable) return object;
  RenderEditable? found;
  object?.visitChildren((child) => found ??= findRenderEditable(child));
  return found;
}

/// Indices into [current] that are new or edited relative to [saved].
Set<int> computeModifiedLines(List<String> saved, List<String> current) {
  var lo = 0;
  final shortest = min(saved.length, current.length);
  while (lo < shortest && saved[lo] == current[lo]) {
    lo++;
  }
  if (lo == saved.length && lo == current.length) return {};

  var hiSaved = saved.length;
  var hiCurrent = current.length;
  while (hiSaved > lo &&
      hiCurrent > lo &&
      saved[hiSaved - 1] == current[hiCurrent - 1]) {
    hiSaved--;
    hiCurrent--;
  }

  final before = saved.sublist(lo, hiSaved);
  final after = current.sublist(lo, hiCurrent);
  final n = before.length;
  final m = after.length;
  // Pure deletion: no line left to mark.
  if (m == 0) return {};
  if (n == 0 || n > _kMaxDiffLines || m > _kMaxDiffLines) {
    return {for (var i = lo; i < lo + m; i++) i};
  }

  final dp = List.generate(n + 1, (_) => List.filled(m + 1, 0));
  for (var i = 1; i <= n; i++) {
    for (var j = 1; j <= m; j++) {
      dp[i][j] = before[i - 1] == after[j - 1]
          ? dp[i - 1][j - 1] + 1
          : max(dp[i - 1][j], dp[i][j - 1]);
    }
  }

  final modified = <int>{};
  var i = n;
  var j = m;
  while (j > 0) {
    if (i > 0 && before[i - 1] == after[j - 1]) {
      i--;
      j--;
    } else if (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) {
      modified.add(lo + j - 1);
      j--;
    } else {
      i--;
    }
  }
  return modified;
}

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
  final _scroll = ScrollController();
  final _fieldKey = GlobalKey();

  bool _loading = true;
  bool _saving = false;
  String? _error;
  String _original = '';

  /// Preserved so saving a CRLF file does not rewrite every line ending.
  bool _crlf = false;

  List<String> _savedLines = [];
  Set<int> _modifiedLines = {};
  int _lineCount = 1;

  /// Y of each logical line's first visual row, in content coordinates.
  /// Measured rather than computed because soft-wrapped lines span several
  /// rows.
  List<double> _lineY = [];

  double _lastWidth = 0;

  bool get _dirty => _controller.text != _original;

  double get _gutterWidth => max(34, _lineCount.toString().length * 7.5 + 14);

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onTextChanged);
    _load();
  }

  @override
  void dispose() {
    _controller.removeListener(_onTextChanged);
    _controller.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _onTextChanged() {
    final lines = _controller.text.split('\n');
    final modified = computeModifiedLines(_savedLines, lines);
    if (lines.length != _lineCount || !setEquals(modified, _modifiedLines)) {
      setState(() {
        _lineCount = lines.length;
        _modifiedLines = modified;
      });
    } else {
      // Still needs a rebuild: the appbar shows the dirty marker.
      setState(() {});
    }
    _readLinePositions();
  }

  /// `getLocalRectForCaret` returns viewport coordinates (scroll already
  /// subtracted), so the offset is added back to get content coordinates.
  void _readLinePositions() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final editable = findRenderEditable(
        _fieldKey.currentContext?.findRenderObject(),
      );
      if (editable == null) return;

      final scroll = _scroll.hasClients ? _scroll.offset : 0.0;
      final lines = _controller.text.split('\n');
      final ys = <double>[];
      var offset = 0;
      for (final line in lines) {
        final rect = editable.getLocalRectForCaret(
          TextPosition(offset: offset),
        );
        ys.add(rect.top + scroll);
        offset += line.length + 1;
      }

      if (!listEquals(ys, _lineY)) setState(() => _lineY = ys);
    });
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
      _original = text;
      _savedLines = text.split('\n');
      _controller.text = text;
      setState(() {
        _loading = false;
        _lineCount = _savedLines.length;
        _modifiedLines = {};
      });
      _readLinePositions();
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
      setState(() {
        _original = text;
        _savedLines = text.split('\n');
        _modifiedLines = {};
      });
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
          _ => _buildEditor(colors),
        },
      ),
    );
  }

  Widget _buildEditor(QAppColors colors) {
    return ColoredBox(
      color: colors.terminalBackground,
      child: LayoutBuilder(
        builder: (context, constraints) {
          // A width change re-wraps long lines, invalidating _lineY.
          if (constraints.maxWidth != _lastWidth) {
            _lastWidth = constraints.maxWidth;
            _readLinePositions();
          }
          return Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              AnimatedBuilder(
                animation: _scroll,
                builder: (context, _) => SizedBox(
                  width: _gutterWidth,
                  child: CustomPaint(
                    painter: _GutterPainter(
                      lineCount: _lineCount,
                      lineY: _lineY,
                      modifiedLines: _modifiedLines,
                      scrollOffset: _scroll.hasClients ? _scroll.offset : 0,
                      background: colors.background,
                      divider: colors.divider,
                      foreground: colors.textMuted,
                      modified: colors.warning,
                    ),
                  ),
                ),
              ),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(
                    _kLeftPadding,
                    _kTopPadding,
                    _kRightPadding,
                    _kBottomPadding,
                  ),
                  child: TextField(
                    key: _fieldKey,
                    controller: _controller,
                    scrollController: _scroll,
                    maxLines: null,
                    expands: true,
                    keyboardType: TextInputType.multiline,
                    textAlignVertical: TextAlignVertical.top,
                    cursorColor: colors.accent,
                    style: TextStyle(
                      color: colors.terminalForeground,
                      fontFamily: 'monospace',
                      fontSize: _kFontSize,
                      height: _kLineHeight / _kFontSize,
                    ),
                    decoration: const InputDecoration(
                      border: InputBorder.none,
                      focusedBorder: InputBorder.none,
                      enabledBorder: InputBorder.none,
                      isCollapsed: true,
                      contentPadding: EdgeInsets.zero,
                    ),
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _GutterPainter extends CustomPainter {
  const _GutterPainter({
    required this.lineCount,
    required this.lineY,
    required this.modifiedLines,
    required this.scrollOffset,
    required this.background,
    required this.divider,
    required this.foreground,
    required this.modified,
  });

  final int lineCount;
  final List<double> lineY;
  final Set<int> modifiedLines;
  final double scrollOffset;
  final Color background;
  final Color divider;
  final Color foreground;
  final Color modified;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(Offset.zero & size, Paint()..color = background);
    canvas.drawRect(
      Rect.fromLTWH(size.width - 1, 0, 1, size.height),
      Paint()..color = divider,
    );

    final modifiedTint = modified.withValues(alpha: 0.10);

    for (var i = 0; i < lineCount; i++) {
      // Arithmetic fallback for the first frame, before lineY is measured.
      final contentY = i < lineY.length ? lineY[i] : i * _kLineHeight;
      final top = _kTopPadding + contentY - scrollOffset;
      if (top + _kLineHeight < 0 || top > size.height) continue;

      final isModified = modifiedLines.contains(i);
      if (isModified) {
        canvas.drawRect(
          Rect.fromLTWH(2, top, size.width - 3, _kLineHeight),
          Paint()..color = modifiedTint,
        );
        canvas.drawRect(
          Rect.fromLTWH(0, top, 2, _kLineHeight),
          Paint()..color = modified,
        );
      }

      final painter = TextPainter(
        text: TextSpan(
          text: '${i + 1}',
          style: TextStyle(
            fontFamily: 'monospace',
            fontSize: 11,
            color: isModified ? modified : foreground,
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      painter.paint(
        canvas,
        Offset(
          size.width - painter.width - 7,
          top + (_kLineHeight - painter.height) / 2,
        ),
      );
    }
  }

  @override
  bool shouldRepaint(_GutterPainter old) =>
      old.scrollOffset != scrollOffset ||
      old.lineCount != lineCount ||
      old.foreground != foreground ||
      old.background != background ||
      !setEquals(old.modifiedLines, modifiedLines) ||
      !listEquals(old.lineY, lineY);
}
