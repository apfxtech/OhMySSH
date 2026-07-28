# Icons

Every SVG here is currently the **same placeholder glyph**. The filenames and
the call sites are final — only the artwork is pending. Replace files one at a
time; nothing in the code needs to change.

## Rules for replacement art

`QIcon` rasterises each SVG once per (asset, colour, pixel size) and tints the
result with `BlendMode.srcIn`. That means:

- **Only the alpha channel survives.** Whatever colour is in the file is thrown
  away, so multi-colour brand logos flatten to a single silhouette. Draw for
  that.
- **24×24 viewBox**, solid fills, no strokes, no gradients, no `<text>`.
- Keep the glyph inside the box with a little padding — `QIconBadge` puts it on
  a 36×36 pill at 24px.

Tint comes from the call site and flips with the theme via `QIconBadgeStyle`:
light theme paints a solid colour pill behind a white glyph, dark theme a
translucent pill behind a coloured glyph.

## Directories

| Directory | Used for |
|---|---|
| `os/` | Distro and OS badges. Basename must match the `ID=` field of `/etc/os-release` — see `kKnownOsIds` in `lib/ssh/probe.dart`. Unknown distros fall back to `linux.svg` via `ID_LIKE`. |
| `action/` | Verbs: add, edit, delete, upload, download, import/export, … |
| `nav/` | Bottom bar and row chevrons. `hosts`, `identities` and `settings` also need a `-filled` variant for the selected state. |
| `state/` | Connect checkpoints and the metrics tiles. |
| `file/` | SFTP browser rows, picked by extension in `sftp_view.dart`. |

## Adding a new distro

1. Drop `assets/ic/os/<id>.svg` where `<id>` is the os-release `ID`.
2. Add `<id>` to `kKnownOsIds` in `lib/ssh/probe.dart`.
3. Optionally give it a tint in `osColorValue()` in the same file.

Nothing else — `pubspec.yaml` includes the whole directory.

## Licensing

These placeholders are trivial shapes with no provenance. Real distro logos are
trademarks: check each project's brand guidelines before shipping, especially
for anything published to an app store.
