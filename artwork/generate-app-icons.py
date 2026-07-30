#!/usr/bin/env python3
"""Build the launcher icons for Android, iOS and desktop from app-icon-source.png.

The source is a render, not an asset: the artwork sits on a white page and its
dark panel carries grain, a vignette and a baked long shadow. Handing that file
to a launcher leaves white bars and doubled corners, so this lifts the glyphs off
the panel and recomposes them per platform - full bleed where the OS masks the
icon itself (iOS, Android adaptive), pre-shaped where it does not (macOS, Windows,
Linux).

    python3 artwork/generate-app-icons.py      # needs Pillow
"""

from __future__ import annotations

import io
import json
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "artwork" / "app-icon-source.png"

LUMA = np.array([0.299, 0.587, 0.114])
# Apple's icon grid: an 824x824 body inside 1024, a 9.75% margin per side.
MACOS_BODY = 0.805
# Superellipse exponent that reads closest to Apple's continuous corners.
SQUIRCLE_N = 4.8
DESKTOP_CORNER = 0.235
# Under this size the "SSH" lettering collapses into a smudge, so those entries
# get the wordless variant instead.
WORDLESS_BELOW = 40
WORDLESS_FILL = 0.66
ANDROID_DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
# An adaptive layer is 108dp, but a launcher only ever shows the middle 72dp.
ANDROID_VIEWPORT = 72 / 108


def load_panel() -> np.ndarray:
    """Crop the source to its dark panel, dropping the page and outer shadow."""
    rgb = np.asarray(Image.open(SOURCE).convert("RGB")).astype(float)
    ys, xs = np.where(rgb @ LUMA < 140)
    return rgb[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def panel_interior(lum: np.ndarray) -> np.ndarray:
    """Everything inside the panel, glyph holes filled.

    The panel is convex, so intersecting each row's with each column's dark span
    fills the holes - no flood fill, no scipy.
    """
    dark = lum < 140
    rows = np.zeros_like(dark)
    for y in range(dark.shape[0]):
        idx = np.flatnonzero(dark[y])
        if idx.size:
            rows[y, idx[0]:idx[-1] + 1] = True
    cols = np.zeros_like(dark)
    for x in range(dark.shape[1]):
        idx = np.flatnonzero(dark[:, x])
        if idx.size:
            cols[idx[0]:idx[-1] + 1, x] = True
    return rows & cols


def erode(mask: np.ndarray, px: int) -> np.ndarray:
    # Pad first: Pillow's rank filters clip their kernel at the image border, so
    # a mask that runs to the edge would not erode there at all.
    padded = np.pad(mask, px, constant_values=False)
    img = Image.fromarray((padded * 255).astype(np.uint8), "L")
    return np.asarray(img.filter(ImageFilter.MinFilter(2 * px + 1)))[px:-px, px:-px] > 127


def background_field(lum: np.ndarray, interior: np.ndarray) -> np.ndarray:
    """Block-average the panel tone over non-glyph pixels.

    Tracking the baked shadow and vignette keeps glyph alpha correct where a
    stroke crosses out of the lit area; a single flat estimate would eat the
    strokes in shadow and halo the ones outside it.
    """
    h, w = lum.shape
    plain = interior & (lum < 70)
    block = 24
    ph, pw = -(-h // block), -(-w // block)
    weighted = np.zeros((ph * block, pw * block))
    weighted[:h, :w] = lum * plain
    counted = np.zeros_like(weighted)
    counted[:h, :w] = plain
    sums = weighted.reshape(ph, block, pw, block).sum(axis=(1, 3))
    counts = counted.reshape(ph, block, pw, block).sum(axis=(1, 3))
    coarse = grow_into_gaps(np.where(counts > 0, sums / np.maximum(counts, 1), np.nan))
    return np.asarray(Image.fromarray(coarse.astype(np.float32), mode="F")
                      .resize((w, h), Image.BICUBIC)).astype(float)


def grow_into_gaps(a: np.ndarray) -> np.ndarray:
    """Fill blocks that held no panel pixel at all from their neighbours."""
    out = a.copy()
    while np.isnan(out).any():
        known = ~np.isnan(out)
        filled = np.nan_to_num(out)
        acc = np.zeros_like(out)
        cnt = np.zeros_like(out)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            acc += np.roll(filled, (dy, dx), (0, 1))
            cnt += np.roll(known, (dy, dx), (0, 1)).astype(float)
        out = np.where(known, out, np.where(cnt > 0, acc / np.maximum(cnt, 1), np.nan))
    return out


def box_sum(a: np.ndarray, r: int) -> np.ndarray:
    rows = np.cumsum(np.pad(a, ((r + 1, r), (0, 0))), axis=0)
    band = rows[2 * r + 1:] - rows[:-(2 * r + 1)]
    cols = np.cumsum(np.pad(band, ((0, 0), (r + 1, r))), axis=1)
    return cols[:, 2 * r + 1:] - cols[:, :-(2 * r + 1)]


def local_tone(rgb: np.ndarray, core: np.ndarray, r: int = 7) -> np.ndarray:
    """Mean colour of the core pixels around each pixel, background excluded.

    Averaging only within the glyph gives every pixel - edges included - the tone
    of the glyph it belongs to, and washes out the render's grain. A local maximum
    would track the grain instead, and one global average would flatten the grey
    rule and the white lettering into the same tone.
    """
    den = box_sum(core.astype(float), r)
    num = np.stack([box_sum(rgb[..., c] * core, r) for c in range(3)], axis=-1)
    return np.divide(num, np.maximum(den, 1e-9)[..., None],
                     where=(den > 0)[..., None], out=np.zeros_like(num))


def claim_edges(green_core: np.ndarray, light_core: np.ndarray, rounds: int) -> np.ndarray:
    """Label each antialiased edge pixel with the nearest glyph's colour class.

    Dilating both classes at once, first claimer wins, stops a green edge from
    picking up the white of a glyph a few pixels away.
    """
    label = np.where(green_core, 1, 0) + np.where(light_core, 2, 0)
    for _ in range(rounds):
        free = label == 0
        if not free.any():
            break
        claim = np.zeros_like(label)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
            claim = np.where((claim == 0) & free, np.roll(label, (dy, dx), (0, 1)), claim)
        label = np.where(free, claim, label)
    return label


class Artwork:
    """The glyph cluster on transparency, plus the geometry it was drawn at."""

    def __init__(self, glyphs: Image.Image, panel: tuple[int, int, int],
                 panel_side: float, report: dict):
        self.glyphs = glyphs
        self.panel = panel
        self.panel_side = panel_side
        self.report = report


def extract_glyphs() -> Artwork:
    rgb = load_panel()
    lum = rgb @ LUMA
    interior = erode(panel_interior(lum), 9)
    bg = background_field(lum, interior)

    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    lifted = lum > bg + 45
    green_core = interior & lifted & (g - np.maximum(r, b) > 30)
    light_core = interior & lifted & ~green_core & (np.ptp(rgb, axis=-1) < 18)

    green = np.median(rgb[green_core & (lum > bg + 80)], axis=0)
    green_lum = float(green @ LUMA)
    # Sample the tone from interior pixels only. Including the antialiased ramp
    # drags each glyph's local mean down and leaves a darker rim inside the fill.
    light = local_tone(rgb, light_core & (lum > bg + 120))
    light_lum = light @ LUMA

    label = claim_edges(green_core, light_core, rounds=5)
    target = np.where(label == 1, green_lum, light_lum)
    # A pixel claimed beyond local_tone's radius has no tone to aim at; without
    # this guard it resolves to opaque black and rings every glyph.
    solid = (label > 0) & (target > bg + 20)

    alpha = np.where(solid, np.clip((lum - bg) / np.maximum(target - bg, 1.0), 0.0, 1.0), 0.0)
    alpha = np.where(alpha < 0.05, 0.0, alpha)  # the render's faint outer glow
    # Pin interiors opaque: the grain otherwise rides through the alpha ramp and
    # mottles every fill. Pinning only two pixels deep keeps the outline soft.
    alpha = np.where(erode(alpha > 0.75, 2), 1.0, alpha)

    colour = np.where((label == 1)[..., None], green, light)
    layer = Image.fromarray(np.dstack([colour, alpha * 255.0]).clip(0, 255).astype(np.uint8),
                            "RGBA")
    panel = np.percentile(rgb[interior & (lum < 70)], 85, axis=0).round()
    box = layer.getbbox()
    report = {
        "panel_px": (rgb.shape[1], rgb.shape[0]),
        "panel": tuple(int(v) for v in panel),
        "green": tuple(int(v) for v in green.round()),
        "neutrals": sorted({int(v) for v in np.round(light_lum[light_core] / 5) * 5}),
        "cluster_of_panel": ((box[2] - box[0]) / rgb.shape[1], (box[3] - box[1]) / rgb.shape[0]),
    }
    return Artwork(layer.crop(box), tuple(int(v) for v in panel),
                   (rgb.shape[0] + rgb.shape[1]) / 2, report)


def split_wordmark(glyphs: Image.Image) -> Image.Image:
    """Drop the bottom block - the "SSH" lettering - at the widest empty band."""
    alpha = np.asarray(glyphs)[..., 3]
    empty = alpha.max(axis=1) == 0
    best = start = None
    for y in range(len(empty) + 1):
        if y < len(empty) and empty[y]:
            start = y if start is None else start
        elif start is not None:
            if best is None or y - start > best[1] - best[0]:
                best = (start, y)
            start = None
    if best is None:
        return glyphs
    head = glyphs.crop((0, 0, glyphs.width, best[0]))
    return head.crop(head.getbbox())


def resample(layer: Image.Image, w: int, h: int) -> Image.Image:
    """Resize through premultiplied alpha.

    Pillow resamples channels independently, so on a straight-alpha layer the
    colour behind fully transparent pixels bleeds in and rings the glyphs.
    """
    arr = np.asarray(layer).astype(float)
    a = arr[..., 3:4] / 255.0
    premul = Image.fromarray(np.dstack([arr[..., :3] * a, arr[..., 3]])
                             .clip(0, 255).astype(np.uint8), "RGBA")
    out = np.asarray(premul.resize((w, h), Image.LANCZOS)).astype(float)
    a_out = out[..., 3:4].clip(0, 255)
    rgb = np.divide(out[..., :3] * 255.0, np.maximum(a_out, 1e-6),
                    where=a_out > 0, out=np.zeros_like(out[..., :3]))
    return Image.fromarray(np.dstack([rgb, a_out]).clip(0, 255).astype(np.uint8), "RGBA")


def place(art: Artwork, glyphs: Image.Image, size: int, body: float = 1.0,
          fill: float | None = None) -> Image.Image:
    """Centre a glyph cluster on a transparent square of `size`.

    By default the cluster keeps the proportion it has on the source panel, so
    every platform shows the same drawing at a different scale. `fill` sizes it to
    a fraction of the body instead, for the wordless variant.
    """
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    if fill is None:
        scale = size * body / art.panel_side
    else:
        scale = size * body * fill / max(glyphs.width, glyphs.height)
    w, h = max(1, round(glyphs.width * scale)), max(1, round(glyphs.height * scale))
    canvas.alpha_composite(resample(glyphs, w, h), ((size - w) // 2, (size - h) // 2))
    return canvas


def squircle(size: int, exponent: float, inset: float = 0.0) -> Image.Image:
    ss = 4
    n = size * ss
    half = (n - 2 * inset * n) / 2
    d = np.abs((np.arange(n) + 0.5 - n / 2) / half) ** exponent
    inside = (d[None, :] + d[:, None]) <= 1.0
    # BOX averages the supersampled coverage; Lanczos would ring the shape edge.
    return Image.fromarray((inside * 255).astype(np.uint8), "L").resize((size, size), Image.BOX)


def rounded_square(size: int, radius: float) -> Image.Image:
    ss = 4
    n = size * ss
    r = radius * n
    y, x = np.mgrid[0:n, 0:n] + 0.5
    dx = np.maximum(np.maximum(r - x, x - (n - r)), 0)
    dy = np.maximum(np.maximum(r - y, y - (n - r)), 0)
    inside = (dx ** 2 + dy ** 2) <= r ** 2
    return Image.fromarray((inside * 255).astype(np.uint8), "L").resize((size, size), Image.BOX)


def compose(art: Artwork, size: int, mask: Image.Image | None = None,
            body: float = 1.0, wordless: bool = False) -> Image.Image:
    base = Image.new("RGBA", (size, size), art.panel + (255,))
    if mask is not None:
        base.putalpha(mask)
    glyphs = split_wordmark(art.glyphs) if wordless else art.glyphs
    base.alpha_composite(place(art, glyphs, size, body,
                               fill=WORDLESS_FILL if wordless else None))
    return base


def monochrome(layer: Image.Image) -> Image.Image:
    out = Image.new("RGBA", layer.size, (255, 255, 255, 0))
    out.putalpha(layer.split()[3])
    return out


def tinted(layer: Image.Image) -> Image.Image:
    """Grey ink for iOS' tinted appearance, mids lifted so the green stays read."""
    arr = np.asarray(layer).astype(float)
    lifted = 255.0 * np.clip((arr[..., :3] @ LUMA) / 255.0, 0, 1) ** 0.55
    return Image.fromarray(np.dstack([lifted, lifted, lifted, arr[..., 3]])
                           .clip(0, 255).astype(np.uint8), "RGBA")


def write_png(img: Image.Image, path: Path, opaque: bool = False) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    (img.convert("RGB") if opaque else img).save(path, "PNG", optimize=True)
    print(f"  {path.relative_to(ROOT)}  {img.width}x{img.height}{' opaque' if opaque else ''}")


def write_text(path: Path, body: str, note: str = "") -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(body)
    print(f"  {path.relative_to(ROOT)}  {note}".rstrip())


def write_icns(members: dict[str, Image.Image], path: Path) -> None:
    """Assemble the .icns from PNG members, so regenerating needs no macOS tools."""
    chunks = b""
    for kind, img in members.items():
        buf = io.BytesIO()
        img.save(buf, "PNG", optimize=True)
        chunks += kind.encode("ascii") + struct.pack(">I", len(buf.getvalue()) + 8) + buf.getvalue()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"icns" + struct.pack(">I", len(chunks) + 8) + chunks)
    print(f"  {path.relative_to(ROOT)}  {len(members)} sizes, {(len(chunks) + 8) // 1024}KB")


def build_android(art: Artwork) -> None:
    res = ROOT / "androidApp" / "src" / "main" / "res"
    print("Android adaptive icon")
    for density, factor in ANDROID_DENSITIES.items():
        layer = place(art, art.glyphs, round(108 * factor), body=ANDROID_VIEWPORT)
        write_png(layer, res / f"mipmap-{density}" / "ic_launcher_foreground.png")
        write_png(monochrome(layer), res / f"mipmap-{density}" / "ic_launcher_monochrome.png")

    hex_panel = "#%02X%02X%02X" % art.panel
    write_text(res / "values" / "ic_launcher_background.xml",
               '<?xml version="1.0" encoding="utf-8"?>\n'
               "<resources>\n"
               f'    <color name="ic_launcher_background">{hex_panel}</color>\n'
               "</resources>\n", hex_panel)
    write_text(res / "mipmap-anydpi-v26" / "ic_launcher.xml",
               '<?xml version="1.0" encoding="utf-8"?>\n'
               '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
               '    <background android:drawable="@color/ic_launcher_background" />\n'
               '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
               '    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />\n'
               "</adaptive-icon>\n")


def build_ios(art: Artwork) -> None:
    out = ROOT / "iosApp" / "iosApp" / "Assets.xcassets" / "AppIcon.appiconset"
    print("iOS app icon")
    # Full bleed and no alpha: iOS applies its own squircle, and App Store review
    # rejects an icon that carries an alpha channel.
    write_png(compose(art, 1024), out / "app-icon-1024.png", opaque=True)
    # The dark and tinted variants ship without a background; iOS draws its own.
    write_png(place(art, art.glyphs, 1024), out / "app-icon-1024-dark.png")
    write_png(tinted(place(art, art.glyphs, 1024)), out / "app-icon-1024-tinted.png")

    shared = {"idiom": "universal", "platform": "ios", "size": "1024x1024"}
    write_text(out / "Contents.json", json.dumps({
        "images": [
            {"filename": "app-icon-1024.png", **shared},
            {"appearances": [{"appearance": "luminosity", "value": "dark"}],
             "filename": "app-icon-1024-dark.png", **shared},
            {"appearances": [{"appearance": "luminosity", "value": "tinted"}],
             "filename": "app-icon-1024-tinted.png", **shared},
        ],
        "info": {"author": "xcode", "version": 1},
    }, indent=2) + "\n", "light + dark + tinted")


def macos_tile(art: Artwork, size: int) -> Image.Image:
    # macOS never masks an app icon, so draw Apple's inset squircle here.
    return compose(art, size, mask=squircle(size, SQUIRCLE_N, inset=(1 - MACOS_BODY) / 2),
                   body=MACOS_BODY, wordless=size < WORDLESS_BELOW)


def desktop_tile(art: Artwork, size: int) -> Image.Image:
    # Windows and Linux draw the icon as given, and full bleed survives a 16px
    # taskbar entry better than Apple's margin would.
    return compose(art, size, mask=rounded_square(size, DESKTOP_CORNER),
                   wordless=size < WORDLESS_BELOW)


def build_desktop(art: Artwork) -> None:
    print("Desktop icons")
    out = ROOT / "artwork" / "desktop"
    write_icns({kind: macos_tile(art, px) for kind, px in (
        ("icp4", 16), ("icp5", 32), ("ic11", 32), ("ic12", 64), ("ic07", 128),
        ("ic13", 256), ("ic08", 256), ("ic14", 512), ("ic09", 512), ("ic10", 1024),
    )}, out / "app-icon-macos.icns")
    write_png(macos_tile(art, 1024), out / "app-icon-macos.png")

    sizes = [16, 24, 32, 48, 64, 128, 256]
    frames = [desktop_tile(art, s) for s in sizes]
    out.mkdir(parents=True, exist_ok=True)
    frames[-1].save(out / "app-icon-windows.ico", format="ICO",
                    sizes=[(s, s) for s in sizes], append_images=frames[:-1])
    print(f"  {(out / 'app-icon-windows.ico').relative_to(ROOT)}  {sizes}")

    write_png(desktop_tile(art, 512), out / "app-icon-linux.png")
    # Read at runtime for the window and taskbar, so it lives on the desktop
    # classpath rather than next to the packaging icons.
    write_png(desktop_tile(art, 512),
              ROOT / "shared" / "src" / "desktopMain" / "resources" / "app-icon.png")


def android_tile(art: Artwork, size: int, shape: str) -> Image.Image:
    """Render the adaptive layers the way a launcher does: 108dp layer, 72dp window."""
    layer_px = round(size / ANDROID_VIEWPORT)
    base = Image.new("RGBA", (layer_px, layer_px), art.panel + (255,))
    base.alpha_composite(place(art, art.glyphs, layer_px, body=ANDROID_VIEWPORT))
    off = (layer_px - size) // 2
    view = base.crop((off, off, off + size, off + size))
    view.putalpha({"circle": lambda: squircle(size, 2.0),
                   "squircle": lambda: squircle(size, SQUIRCLE_N),
                   "rounded": lambda: rounded_square(size, 0.14)}[shape]())
    return view


def sheet(tiles: list[Image.Image], pad: int) -> Image.Image:
    w = tiles[0].width
    out = Image.new("RGBA", (len(tiles) * (w + pad) + pad, w + 2 * pad), (124, 126, 132, 255))
    for i, tile in enumerate(tiles):
        out.alpha_composite(tile, (pad + i * (w + pad), pad))
    return out


def build_previews(art: Artwork) -> None:
    print("Previews")
    size = 224
    write_png(sheet([
        compose(art, size, mask=squircle(size, SQUIRCLE_N)),
        android_tile(art, size, "circle"),
        android_tile(art, size, "squircle"),
        android_tile(art, size, "rounded"),
        macos_tile(art, size),
        desktop_tile(art, size),
    ], 16), ROOT / "artwork" / "preview-masks.png")

    small = [desktop_tile(art, px) for px in (16, 24, 32, 48)]
    small += [macos_tile(art, px) for px in (16, 32)]
    small += [place(art, art.glyphs, px, body=ANDROID_VIEWPORT) for px in (48, 72)]
    write_png(sheet([t.resize((96, 96), Image.NEAREST) for t in small], 12),
              ROOT / "artwork" / "preview-small-sizes.png")


def main() -> None:
    art = extract_glyphs()
    r = art.report
    print("Source panel %dx%d  panel #%02X%02X%02X  green #%02X%02X%02X  neutrals %s"
          % (*r["panel_px"], *r["panel"], *r["green"], r["neutrals"]))
    print("Glyph cluster %dx%dpx, %.1f%% x %.1f%% of the panel"
          % (art.glyphs.width, art.glyphs.height, *[v * 100 for v in r["cluster_of_panel"]]))
    write_png(art.glyphs, ROOT / "artwork" / "app-icon-glyphs.png")

    build_android(art)
    build_ios(art)
    build_desktop(art)
    build_previews(art)


if __name__ == "__main__":
    main()
