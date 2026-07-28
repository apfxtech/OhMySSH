import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../theme/theme.dart';

class _RasterIconCache {
  _RasterIconCache._();
  static final _RasterIconCache instance = _RasterIconCache._();

  static const int _maxEntries = 128;

  final Map<String, ui.Image> _ready = <String, ui.Image>{};
  final Map<String, Future<ui.Image>> _pending = <String, Future<ui.Image>>{};

  static String keyFor(String asset, Color color, int pixelSize) =>
      '$asset|${color.toARGB32()}|$pixelSize';

  ui.Image? ready(String key) => _ready[key];

  Future<ui.Image> resolve({
    required String key,
    required String label,
    required int pixelSize,
    required Future<ui.Image> Function() rasterize,
  }) {
    final existing = _ready[key];
    if (existing != null) return Future<ui.Image>.value(existing);

    var wasAdded = false;
    final pending = _pending.putIfAbsent(key, () async {
      wasAdded = true;
      try {
        final image = await rasterize();
        _store(key, image);
        return image;
      } catch (error) {
        _debugLog('failed', label: label, pixelSize: pixelSize, error: error);
        rethrow;
      } finally {
        _pending.remove(key);
        _debugLog('finished', label: label, pixelSize: pixelSize);
      }
    });

    _debugLog(
      wasAdded ? 'queued' : 'joined',
      label: label,
      pixelSize: pixelSize,
    );
    return pending;
  }

  void _store(String key, ui.Image image) {
    if (_ready.length >= _maxEntries) {
      final oldestKey = _ready.keys.first;
      _ready.remove(oldestKey)?.dispose();
      _debugLog('evicted');
    }
    _ready[key] = image;
  }

  void _debugLog(
    String action, {
    String? label,
    int? pixelSize,
    Object? error,
  }) {
    if (!kDebugMode) return;

    final details = <String>[
      'pending=${_pending.length}',
      'cached=${_ready.length}/$_maxEntries',
      if (label != null) 'asset=$label',
      if (pixelSize != null) 'pixelSize=$pixelSize',
      if (error != null) 'error=$error',
    ];
    debugPrint('[QIconCache] $action; ${details.join('; ')}');
  }

  static Future<ui.Image> _rasterize({
    required String asset,
    required Color color,
    required int pixelSize,
  }) async {
    final info = await vg.loadPicture(SvgAssetLoader(asset), null);
    try {
      final src = info.size;
      final scale = (src.width <= 0 || src.height <= 0)
          ? 1.0
          : pixelSize / (src.width > src.height ? src.width : src.height);
      final dx = (pixelSize - src.width * scale) / 2;
      final dy = (pixelSize - src.height * scale) / 2;

      final recorder = ui.PictureRecorder();
      final canvas = Canvas(recorder);
      final paint = Paint()
        ..colorFilter = ui.ColorFilter.mode(color, ui.BlendMode.srcIn);
      canvas.saveLayer(
        Rect.fromLTWH(0, 0, pixelSize.toDouble(), pixelSize.toDouble()),
        paint,
      );
      canvas.translate(dx, dy);
      canvas.scale(scale);
      canvas.drawPicture(info.picture);
      canvas.restore();

      final picture = recorder.endRecording();
      try {
        return await picture.toImage(pixelSize, pixelSize);
      } finally {
        picture.dispose();
      }
    } finally {
      info.picture.dispose();
    }
  }
}

class QIcon extends StatefulWidget {
  const QIcon({super.key, required this.asset, required this.color, this.size});

  final String asset;
  final Color color;
  final double? size;

  @override
  State<QIcon> createState() => _QIconState();
}

class _QIconState extends State<QIcon> {
  String? _key;
  ui.Image? _image;

  void _adopt(ui.Image? cacheImage) {
    _image?.dispose();
    _image = cacheImage?.clone();
  }

  @override
  void dispose() {
    _image?.dispose();
    _image = null;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final size = widget.size;
    if (size == null) {
      return SvgPicture.asset(
        widget.asset,
        colorFilter: ColorFilter.mode(widget.color, BlendMode.srcIn),
      );
    }

    final dpr = MediaQuery.devicePixelRatioOf(context);
    final pixelSize = (size * dpr).ceil();
    final key = _RasterIconCache.keyFor(widget.asset, widget.color, pixelSize);

    if (key != _key) {
      _key = key;
      final cached = _RasterIconCache.instance.ready(key);
      if (cached != null) {
        _adopt(cached);
      } else {
        _adopt(null);
        _RasterIconCache.instance
            .resolve(
              key: key,
              label: widget.asset,
              pixelSize: pixelSize,
              rasterize: () => _RasterIconCache._rasterize(
                asset: widget.asset,
                color: widget.color,
                pixelSize: pixelSize,
              ),
            )
            .then((image) {
              if (mounted && _key == key) setState(() => _adopt(image));
            });
      }
    }

    final image = _image;
    if (image == null) {
      return SizedBox(width: size, height: size);
    }
    return RawImage(
      image: image.clone(),
      width: size,
      height: size,
      fit: BoxFit.contain,
    );
  }
}

@immutable
class QIconBadgeStyle {
  const QIconBadgeStyle({required this.background, required this.foreground});

  final Color background;
  final Color foreground;

  factory QIconBadgeStyle.of(
    BuildContext context,
    Color color, {
    double darkOpacity = 0.18,
  }) => QIconBadgeStyle.forColors(
    context.appColors,
    color,
    darkOpacity: darkOpacity,
  );

  factory QIconBadgeStyle.forColors(
    QAppColors colors,
    Color color, {
    double darkOpacity = 0.18,
  }) {
    if (colors.isDark) {
      return QIconBadgeStyle(
        background: color.withValues(alpha: darkOpacity),
        foreground: color,
      );
    }
    final isNearWhite = color.computeLuminance() > 0.9;
    return QIconBadgeStyle(
      background: isNearWhite ? const Color(0xFF9E9E9E) : color,
      foreground: const Color(0xFFFFFFFF),
    );
  }
}

class QIconBadge extends StatelessWidget {
  const QIconBadge({
    super.key,
    required IconData this.icon,
    required this.color,
    this.size = 36,
    this.iconSize = 22,
    this.backgroundOpacity = 0.18,
    this.borderRadius = 8,
  }) : asset = null;

  const QIconBadge.svg({
    super.key,
    required String this.asset,
    required this.color,
    this.size = 36,
    this.iconSize = 24,
    this.backgroundOpacity = 0.18,
    this.borderRadius = 8,
  }) : icon = null;

  final IconData? icon;
  final String? asset;
  final Color color;
  final double size;
  final double iconSize;
  final double backgroundOpacity;
  final double borderRadius;

  @override
  Widget build(BuildContext context) {
    final style = QIconBadgeStyle.of(
      context,
      color,
      darkOpacity: backgroundOpacity,
    );

    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: style.background,
        borderRadius: BorderRadius.circular(borderRadius),
      ),
      child: asset != null
          ? QIcon(asset: asset!, color: style.foreground, size: iconSize)
          : Icon(icon, color: style.foreground, size: iconSize),
    );
  }
}
