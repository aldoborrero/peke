"""Banners and icons from the logo set. Run from anywhere: nix develop -c python3 design/tools/compose.py

Writes docs/assets/banner.svg and social-preview.png, design/assets/tv-banner.svg and app-icon.svg, and the 108 dp
adaptive icon layers to convert with Svg2Vector in build/peke-logo/ (see design/tools/README.md)."""

import subprocess

import gen
from gen import CORAL, LILAC, ROOT, SUNNY, WHITE, XBOLD, fmt, text_paths, translate

BUILD = ROOT / "build/peke-logo"

MIST = "#C8CDE6"
TAG = "A kids-only Android TV app for your Jellyfin server"


def place(els, box, x, y, h):
    """els scaled so the ink box is h tall, its top-left at (x, y)."""
    s = h / (box[3] - box[1])
    return translate(els, x - box[0] * s, y - box[1] * s, s), (box[2] - box[0]) * s


def sparkle(cx, cy, r, colour):
    k = r * 0.2
    return (
        f'<path d="M{fmt(cx)} {fmt(cy - r)} Q{fmt(cx + k)} {fmt(cy - k)} {fmt(cx + r)} {fmt(cy)} '
        f"Q{fmt(cx + k)} {fmt(cy + k)} {fmt(cx)} {fmt(cy + r)} Q{fmt(cx - k)} {fmt(cy + k)} {fmt(cx - r)} {fmt(cy)} "
        f'Q{fmt(cx - k)} {fmt(cy - k)} {fmt(cx)} {fmt(cy - r)} Z" fill="{colour}"/>'
    )


def dot(cx, cy, r):
    return f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{MIST}"/>'


def tagline(cx, baseline, size):
    paths, box = text_paths(XBOLD, TAG, size, 0, baseline)
    dx = cx - (box[0] + box[2]) / 2
    paths, box = text_paths(XBOLD, TAG, size, dx, baseline)
    return f'<path d="{"".join(paths)}" fill="{MIST}"/>'


def write(path, w, h, comment, els):
    body = "\n  ".join(els)
    path.write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" '
        f'stroke-linecap="round" stroke-linejoin="round">\n  <!-- {comment} -->\n  {body}\n</svg>\n'
    )


BUILD.mkdir(parents=True, exist_ok=True)
hz, _ = gen.lockups(False, WHITE)

# README banner, 1280 x 400: a night card with rounded corners, so it sits on GitHub light and dark alike.
lock, lw = place(*hz, 0, 0, 226)
lock, lw = place(*hz, (1280 - lw) / 2, 50, 226)
write(
    ROOT / "docs/assets/banner.svg",
    1280,
    400,
    "README banner: the horizontal \"Peke for Jellyfin\" lockup (design/assets/logo) on a rounded night card, "
    "transparent outside it, and the tagline. Text is Nunito as outlines.",
    ['<rect width="1280" height="400" rx="40" fill="#1F2747"/>']
    + [dot(1150, 70, 5), dot(1212, 318, 4), dot(70, 330, 4), dot(300, 46, 3), sparkle(1172, 170, 24, SUNNY)]
    + [sparkle(1090, 276, 15, LILAC), sparkle(110, 96, 19, CORAL)]
    + lock
    + [tagline(640, 346, 30)],
)

# Social preview, 1280 x 640: full bleed (GitHub rounds and crops it), content inside the 40 px safe edge.
lock, lw = place(*hz, 0, 0, 300)
lock, lw = place(*hz, (1280 - lw) / 2, 120, 300)
write(
    BUILD / "social-preview.svg",
    1280,
    640,
    "GitHub social preview source (rendered to social-preview.png).",
    ['<rect width="1280" height="640" fill="#1F2747"/>']
    + [dot(1160, 96, 6), dot(1196, 520, 5), dot(96, 540, 5), dot(380, 70, 4), sparkle(1150, 260, 32, SUNNY)]
    + [sparkle(1060, 470, 20, LILAC), sparkle(130, 140, 26, CORAL)]
    + lock
    + [tagline(640, 536, 38)],
)
subprocess.run(
    ["rsvg-convert", BUILD / "social-preview.svg", "-o", ROOT / "docs/assets/social-preview.png"], check=True
)

# Android TV launcher banner, 320 x 180, full bleed: the launcher rounds the corners.
lock, lw = place(*hz, 0, 0, 104)
lock, lw = place(*hz, (320 - lw) / 2, (180 - 104) / 2, 104)
write(
    ROOT / "design/assets/tv-banner.svg",
    320,
    180,
    "Android TV launcher banner (xhdpi, 320x180 px): the horizontal \"Peke for Jellyfin\" lockup from "
    "design/assets/logo, text as outlines. The launcher rounds the corners itself.",
    ['<rect width="320" height="180" fill="#1F2747"/>'] + lock,
)

# App icon, 512: the buddy mark on coral, placed as before (body 30..230 -> 109.8..402.2).
b_els, b_box = gen.buddy(False)
S, TX, TY = 1.46, 66, 86
write(
    ROOT / "design/assets/app-icon.svg",
    512,
    512,
    "App icon: the buddy mark (design/assets/logo/peke-mark.svg) on coral. Never text.",
    ['<rect width="512" height="512" rx="116" fill="#FF927E"/>'] + translate(b_els, TX, TY, S),
)

# Adaptive layers (108 dp), for the drawable conversion only: the buddy where the
# Wholphin-era foreground had it, inside the 66 dp safe zone.
F, FX, FY = 0.2053, 27.28, 30.1
write(BUILD / "ic_launcher_foreground.svg", 108, 108, "foreground", translate(b_els, FX, FY, F))
m_els, _ = gen.buddy(True)
mono = [e.replace("currentColor", "#000000") for e in translate(m_els, FX, FY, F)]
write(BUILD / "ic_launcher_monochrome.svg", 108, 108, "monochrome", mono)
