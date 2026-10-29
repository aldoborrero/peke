"""Generate Peke's logo set into design/assets/logo. Run from anywhere: nix develop -c python3 design/tools/gen.py"""

import math
from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[2]
FONT_DIR = ROOT / "app/src/main/res/font"  # the fonts the app bundles
OUT = ROOT / "design/assets/logo"
O = 12  # buddy outline, on its 200-wide body

NIGHT, TEAL, SUNNY, CORAL, LILAC, WHITE = "#1F2747", "#65D9CE", "#FFDA70", "#FF927E", "#B5A0EF", "#FFFFFF"

BLACK = TTFont(FONT_DIR / "nunito_black.ttf")
XBOLD = TTFont(FONT_DIR / "nunito_extrabold.ttf")


def fmt(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s == "-0" else s


def text_paths(font, text, size, x0, baseline, tracking=0, kern=None):
    """Glyph outlines of `text` as SVG path data, one per glyph, and the ink bounds."""
    gs, cmap, k = font.getGlyphSet(), font.getBestCmap(), size / font["head"].unitsPerEm
    kern = kern or {}
    x, out, box = x0, [], [1e9, 1e9, -1e9, -1e9]
    for i, c in enumerate(text):
        g = cmap[ord(c)]
        if c != " ":
            pen = SVGPathPen(gs, ntos=fmt)
            gs[g].draw(TransformPen(pen, (k, 0, 0, -k, x, baseline)))
            bp = BoundsPen(gs)
            gs[g].draw(TransformPen(bp, (k, 0, 0, -k, x, baseline)))
            b = bp.bounds
            box = [min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3])]
            out.append(pen.getCommands())
        x += font["hmtx"][g][0] * k + tracking * k
        if i + 1 < len(text):
            x += kern.get(c + text[i + 1], 0) * k
    return out, box


# ---- the buddy, in its own frame: body 200 x 160 at (30, 40), as the mascot ------------------------------------
TEAL_BAND = 16  # teal visible between the two outlines, as in the mascot (24 - 8)


def buddy(mono=False):
    """Elements of the buddy mark and its ink bounds (strokes included), in mascot coordinates."""
    o = O
    ink = "currentColor" if mono else NIGHT
    inset = TEAL_BAND + o
    sx, sy, sw, sh = 30 + inset, 40 + inset - 2, 200 - 2 * inset, 160 - 2 * inset + 4
    ear_w = o  # the ears are as thick as the outline
    ears = (
        "M101 40 Q90 14 104 9 Q118 13 117 40 M159 40 Q170 14 156 9 Q142 13 143 40"
    )
    eye_r = 8 + (o - 8) * 0.3
    cy = sy + sh * 0.43
    els = [
        f'<path d="{ears}" fill="none" stroke="{ink if mono else TEAL}" stroke-width="{fmt(ear_w)}"/>',
        f'<rect x="30" y="40" width="200" height="160" rx="52" fill="{"none" if mono else TEAL}" stroke="{ink}" stroke-width="{fmt(o)}"/>',
        f'<rect x="{fmt(sx)}" y="{fmt(sy)}" width="{fmt(sw)}" height="{fmt(sh)}" rx="{fmt(52 - inset + 6)}" fill="{"none" if mono else SUNNY}" stroke="{ink}" stroke-width="{fmt(o)}"/>',
        f'<circle cx="105" cy="{fmt(cy)}" r="{fmt(eye_r)}" fill="{ink}"/>',
        f'<circle cx="155" cy="{fmt(cy)}" r="{fmt(eye_r)}" fill="{ink}"/>',
    ]
    if not mono:
        els += [
            f'<circle cx="{fmt(sx + 20)}" cy="{fmt(cy + 21)}" r="9" fill="{CORAL}" opacity="0.75"/>',
            f'<circle cx="{fmt(sx + sw - 20)}" cy="{fmt(cy + 21)}" r="9" fill="{CORAL}" opacity="0.75"/>',
        ]
    els.append(
        f'<path d="M113 {fmt(cy + 25)} Q130 {fmt(cy + 41)} 147 {fmt(cy + 25)}" fill="none" stroke="{ink}" stroke-width="{fmt(o)}"/>'
    )
    top = 9 - ear_w / 2 - 1  # the ear tips' curve peaks just under the control point
    return els, (30 - o / 2, top, 230 + o / 2, 200 + o / 2)


# ---- the wordmark -------------------------------------------------------------------------------------------------
X = 80  # x-height of "peke" in lockup units
SIZE = X / (BLACK["OS/2"].sxHeight / 1000)
TRACK, KERN = -10, {"ke": -40}
COLOURS = [SUNNY, CORAL, TEAL, LILAC]
RIM = 10  # night stroke under the letters: 5 shows outside them


def wordmark(x0, baseline, mono=False):
    paths, box = text_paths(BLACK, "peke", SIZE, x0, baseline, TRACK, KERN)
    r = RIM / 2  # the box keeps the rim in every version, so the one-colour files share the colour files' boxes
    box = [box[0] - r, box[1] - r, box[2] + r, box[3] + r]
    els = []
    if mono:
        els.append(f'<path d="{"".join(paths)}" fill="currentColor"/>')
        return els, box
    els.append(
        f'<path d="{"".join(paths)}" fill="none" stroke="{NIGHT}" stroke-width="{fmt(RIM)}" stroke-linejoin="round"/>'
    )
    els += [f'<path d="{p}" fill="{c}"/>' for p, c in zip(paths, COLOURS)]
    return els, box


def subline(x0, baseline, colour, size, anchor_center=None):
    paths, box = text_paths(BLACK, "for Jellyfin", size, 0, baseline, 0)
    dx = (anchor_center - (box[0] + box[2]) / 2) if anchor_center is not None else x0 - box[0]
    paths, box = text_paths(BLACK, "for Jellyfin", size, dx, baseline, 0)
    return [f'<path d="{"".join(paths)}" fill="{colour}"/>'], box


def svg(name, els, box, comment):
    """Write els with the ink box moved to the origin; the viewBox is the ink box rounded out to whole units."""
    x0, y0 = math.floor(box[0]), math.floor(box[1])
    w, h = math.ceil(box[2]) - x0, math.ceil(box[3]) - y0
    body = "\n    ".join(els)
    (OUT / f"{name}.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" '
        f'stroke-linecap="round" stroke-linejoin="round">\n'
        f"  <!-- {comment} -->\n  <g transform=\"translate({fmt(-x0)} {fmt(-y0)})\">\n    {body}\n  </g>\n</svg>\n"
    )
    return w, h


def union(*bs):
    return (min(b[0] for b in bs), min(b[1] for b in bs), max(b[2] for b in bs), max(b[3] for b in bs))


def translate(els, dx, dy, s=1):
    t = f"translate({fmt(dx)} {fmt(dy)})" + (f" scale({fmt(s)})" if s != 1 else "")
    return [f'<g transform="{t}">'] + ["  " + e for e in els] + ["</g>"]


def tb(b, dx, dy, s=1):
    return (b[0] * s + dx, b[1] * s + dy, b[2] * s + dx, b[3] * s + dy)


# Shared layout: the buddy's body is centred on the x-height band, 2.1 x-heights tall plus its outline.
BODY_H = 2.1 * X + O  # body height in lockup units, outline included
BS = BODY_H / (160 + O)  # buddy scale
GAP = 0.5 * X  # buddy to wordmark (ink to ink)
SUB = 0.36  # sub-line x-height as a share of X
SUB_SIZE = SUB * X / (BLACK["OS/2"].sxHeight / 1000)


def lockups(mono, sub=None):
    """sub: None, or the sub-line colour."""
    b_els, b_box = buddy(mono)
    # buddy: body centre (130, 120) placed on the x-height band centre
    baseline = 0
    dx, dy = -b_box[2] * BS, -X / 2 - 120 * BS
    h_buddy, hb = translate(b_els, dx, dy, BS), tb(b_box, dx, dy, BS)
    w_els, w_box = wordmark(0, baseline, mono)
    shift = hb[2] + GAP - w_box[0]
    w_els, w_box = wordmark(shift, baseline, mono)
    els, box = h_buddy + w_els, union(hb, w_box)
    if sub is not None:
        s_els, s_box = subline(w_box[0] + RIM / 2, 0, sub, SUB_SIZE)
        dy2 = w_box[3] + 0.42 * X - s_box[1]
        s_els, s_box = subline(w_box[0] + RIM / 2, dy2, sub, SUB_SIZE)
        # with a sub-line, the buddy's body centres on the whole text block: x-height top to sub-line baseline
        dy = (-X + dy2) / 2 - 120 * BS
        h_buddy, hb = translate(b_els, dx, dy, BS), tb(b_box, dx, dy, BS)
        els, box = h_buddy + w_els + s_els, union(hb, w_box, s_box)
    hz = (els, box)

    # stacked: buddy above, wordmark centred under it
    w_els, w_box = wordmark(0, 0, mono)
    cx = (w_box[0] + w_box[2]) / 2
    s = BS * 1.25
    dx, dy = cx - 130 * s, w_box[1] - 0.5 * X - b_box[3] * s
    st_b, sb = translate(b_els, dx, dy, s), tb(b_box, dx, dy, s)
    els, box = st_b + w_els, union(sb, w_box)
    if sub is not None:
        s_els, s_box = subline(0, 0, sub, SUB_SIZE, anchor_center=cx)
        dy2 = w_box[3] + 0.42 * X - s_box[1]
        s_els, s_box = subline(0, dy2, sub, SUB_SIZE, anchor_center=cx)
        els, box = els + s_els, union(box, s_box)
    return hz, (els, box)


NOTES = {
    "peke-mark": "The buddy mark: the PekeTV buddy drawn for the logo (outline 12 on a 200-wide body).",
    "peke-wordmark": "The wordmark: \"peke\" in Nunito Black as outlines, tracked -10/1000 and k-e kerned -40, one PekeTV colour per letter on a night rim.",
    "peke-horizontal": "Horizontal lockup: the buddy mark, its body centred on the wordmark's x-height, then \"peke\".",
    "peke-stacked": "Stacked lockup: the buddy mark over the wordmark, centred.",
    "peke-for-jellyfin-horizontal": "Horizontal lockup with the \"for Jellyfin\" sub-line (Nunito Black as outlines).",
    "peke-for-jellyfin-stacked": "Stacked lockup with the \"for Jellyfin\" sub-line (Nunito Black as outlines).",
}
VARIANT = {
    "": " Full colour, for any background.",
    "-mono": " One colour: currentColor.",
    "-on-dark": " Full colour; the sub-line is white, for dark backgrounds.",
    "-on-light": " Full colour; the sub-line is night, for light backgrounds.",
}


def note(base, sfx):
    return NOTES[base] + VARIANT[sfx] + " Generated; see design/DESIGN.md, Logo."


def wordmark_text():
    """peke-wordmark-text.svg: the wordmark as live text, each letter at the x the outlines use."""
    gs, cmap, k = BLACK.getGlyphSet(), BLACK.getBestCmap(), SIZE / 1000
    _, box = wordmark(0, 0)
    x0, y0 = math.floor(box[0]), math.floor(box[1])
    w, h = math.ceil(box[2]) - x0, math.ceil(box[3]) - y0
    x, spans = 0, []
    for i, (c, colour) in enumerate(zip("peke", COLOURS)):
        spans.append(f'<tspan x="{fmt(x - x0)}" fill="{colour}">{c}</tspan>')
        x += (BLACK["hmtx"][cmap[ord(c)]][0] + TRACK + KERN.get("peke"[i : i + 2], 0)) * k
    (OUT / "peke-wordmark-text.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">\n'
        "  <!-- Editable source of peke-wordmark.svg: live text in Nunito Black (weight 900), tracked -10/1000 and k-e"
        " kerned\n       -40/1000, as one x per letter. The outlined files are generated from these values; see"
        " design/DESIGN.md, Logo. -->\n"
        f'  <text y="{fmt(-y0)}" font-family="Nunito" font-size="{fmt(SIZE)}" font-weight="900" stroke="{NIGHT}" '
        f'stroke-width="{fmt(RIM)}" stroke-linejoin="round" paint-order="stroke">{"".join(spans)}</text>\n</svg>\n'
    )


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    wordmark_text()
    for mono, sfx in ((False, ""), (True, "-mono")):
        svg("peke-mark" + sfx, *buddy(mono), note("peke-mark", sfx))
        svg("peke-wordmark" + sfx, *wordmark(0, 0, mono), note("peke-wordmark", sfx))
        hz, st = lockups(mono)
        svg("peke-horizontal" + sfx, *hz, note("peke-horizontal", sfx))
        svg("peke-stacked" + sfx, *st, note("peke-stacked", sfx))
    for sfx, colour, mono in (("-on-dark", WHITE, False), ("-on-light", NIGHT, False), ("-mono", "currentColor", True)):
        hz, st = lockups(mono, colour)
        svg("peke-for-jellyfin-horizontal" + sfx, *hz, note("peke-for-jellyfin-horizontal", sfx))
        svg("peke-for-jellyfin-stacked" + sfx, *st, note("peke-for-jellyfin-stacked", sfx))
