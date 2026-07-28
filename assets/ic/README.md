# Icons

Only **OS and distro logos** live here. Everything functional — actions, nav,
status, file types — uses Material `Icons` directly from the Flutter SDK, so
there is nothing to draw for those.

Every SVG in `os/` is currently the **same placeholder glyph**. The filenames
are final; only the artwork is pending. Replace them one at a time — no code
change needed.

## Rules for replacement art

`QIcon` rasterises each SVG once per (asset, colour, pixel size) and tints the
result with `BlendMode.srcIn`. That means:

- **Only the alpha channel survives.** Whatever colour is in the file is thrown
  away, so a multi-colour brand logo flattens to a single silhouette. Draw for
  that.
- **24×24 viewBox**, solid fills, no strokes, no gradients, no `<text>`.
- Keep the glyph inside the box with a little padding — `QIconBadge.svg` puts it
  on a 36×36 pill at 24px.

Tint comes from the call site and flips with the theme via `QIconBadgeStyle`:
light theme paints a solid colour pill behind a white glyph, dark theme a
translucent pill behind a coloured glyph.

## Adding a distro

1. Drop `assets/ic/os/<id>.svg`, where `<id>` is the `ID=` field from that
   distro's `/etc/os-release`.
2. Add `<id>` to `kKnownOsIds` in `lib/ssh/probe.dart`.
3. Optionally give it a tint in `osColorValue()` in the same file.

Nothing else — `pubspec.yaml` ships the whole directory. Unknown derivatives
already fall back to their family icon via `ID_LIKE`.

## Licensing

These placeholders are trivial shapes with no provenance. Real distro logos are
trademarks: check each project's brand guidelines before shipping, especially
for anything published to an app store.
