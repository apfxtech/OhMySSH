import 'package:flutter/material.dart';

import '../../components/appbar.dart';
import '../../components/cardlist.dart';
import '../../components/icon.dart';
import '../../ssh/probe.dart';
import '../../theme/theme.dart';

/// Every OS glyph side by side, drawn exactly as the host lists draw them.
/// Reached by tapping the version label in Settings; nothing links to it.
class IconGalleryPage extends StatelessWidget {
  const IconGalleryPage({super.key});

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    final ids = kKnownOsIds.toList()..sort();

    return Scaffold(
      backgroundColor: colors.background,
      appBar: QPageAppBar(
        title: 'OS icons',
        subtitle: '${ids.length} glyphs, ${colors.isDark ? 'dark' : 'light'}',
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 10),
        children: [
          GroupedCardGrid<String>(
            items: ids,
            maxCrossAxisExtent: 118,
            mainAxisExtent: 86,
            itemBuilder: (context, id) => _GlyphTile(id: id),
          ),
        ],
      ),
    );
  }
}

class _GlyphTile extends StatelessWidget {
  const _GlyphTile({required this.id});

  final String id;

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            QIconBadge.svg(
              asset: osIconAsset(id),
              color: Color(osColorValue(id)),
            ),
            const SizedBox(width: 8),
            QIcon(
              asset: osIconAsset(id),
              color: colors.textPrimary,
              size: 24,
            ),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          id,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            color: colors.textSecondary,
            fontSize: 11,
            height: 1.1,
          ),
        ),
      ],
    );
  }
}
