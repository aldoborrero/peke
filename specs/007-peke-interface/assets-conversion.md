# Asset conversion log (spec 007, Phase 2)

## Converter

AGP's own SVG converter, `com.android.ide.common.vectordrawable.Svg2Vector` from `com.android.tools:sdk-common`
32.2.1 (the version AGP 9.2 resolves; it is the code behind Android Studio's Vector Asset tool), run from the Gradle
cache with the devshell's `java` and a ten-line driver that calls `Svg2Vector.parseSvgToXml(input, output)` for each
file. Nothing was installed. To redo a file:

```sh
C=~/.gradle/caches/modules-2/files-2.1
CP=$(fd -a '^(sdk-common|common)-32.2.1.jar$' $C/com.android.tools | paste -sd:):$(fd -a 'guava-32.1.3-jre.jar' $C | head -1):$(fd -a 'kotlin-stdlib-2.1.20.jar' $C | head -1)
nix develop -c java -cp "$CP" Svg2Vd.java pairs.txt   # pairs.txt: "<svg> <xml>" per line
```

## What the SVGs use

`<path>`, `<rect rx>`, `<circle>`, `<ellipse>`, `<g transform="translate(…) scale(…)">`, `opacity`, `stroke-linecap`,
`stroke-linejoin`, `fill="none"`. All of it maps to VectorDrawable:

| SVG feature | Result |
|---|---|
| `rect`, `circle`, `ellipse` | converted to path data |
| `g transform` (mascot moods, app icon, banner) | baked into the path coordinates, stroke widths scaled with them (e.g. the app icon's 9 → 1.85 at 108 dp) |
| `opacity` on a shape | `fillAlpha` / `strokeAlpha` |
| `currentColor` (outline icons) | not a VectorDrawable colour: replaced with `#FFFFFF` before converting, and tinted at runtime (Compose `Icon(tint = …)`) |
| `<text>` (only in `logo-peke-text.svg`) | not converted: that file is the editable source; `logo-peke.svg` has the same wordmark as paths |
| `stroke-linecap`, `stroke-linejoin` on the root `<svg>` (icons) | inherited: every converted path carries `strokeLineCap="round"` and `strokeLineJoin="round"` |

None of the files uses gradients, masks, clip paths, filters, `<use>`, `<style>` or CSS, so nothing needed a
workaround beyond `currentColor`. The converter printed no warnings for any of the 57 files.

## Files

| Source (design/assets) | Drawable (app/src/main/res) | Size |
|---|---|---|
| `icons/*.svg` (20) | `drawable/ic_peke_<name>.xml` (hyphens → underscores) | 48 dp |
| `icons/outline/*.svg` (20) | `drawable/ic_peke_outline_<name>.xml`, white, to tint | 48 dp |
| `mascot/peke-tv-buddy*.svg` (6) | `drawable/peke_buddy.xml`, `peke_buddy_{waving,sleeping,puzzled,searching,cheering}.xml` | 260 × 220 dp viewport, as the source |
| `avatars/*.svg` (6) | `drawable/avatar_{leo,max,nico,lucy,sara,mia}.xml`, background circle included | 200 dp |
| `logo-peke.svg` | `drawable/logo_peke.xml` | 640 × 220 |
| `tv-banner.svg` | `drawable/ic_banner.xml`, the manifest's `android:banner` | 160 × 90 dp (320 × 180 px at xhdpi) |
| `app-icon.svg` | `mipmap-anydpi/ic_launcher.xml`, the whole picture for API 23–25 | 48 dp |
| `app-icon.svg`, split | `mipmap-anydpi-v26/ic_launcher.xml` (adaptive): background `@color/ic_launcher_background` `#FF927E`; foreground `drawable/ic_launcher_foreground.xml`, the buddy placed as in the source over the 72 dp visible area of the 108 dp layer, inside the 66 dp safe zone; `drawable/ic_launcher_monochrome.xml`, the buddy's outlines, eyes and smile in one colour, for themed icons (API 33+) | 108 dp |

The Kotlin names are in `ui/theme/PekeArt.kt`: `PekeIcon` (two-tone and outline), `BuddyMood`, `PekeAvatar` (the
plugin's `Avatar` id, drawable and background colour). `DrawablesTest` inflates every one.

## Removed

Wholphin-era launcher art: `mipmap-*dpi/ic_launcher*.webp`, `mipmap-anydpi-v26/ic_launcher_round.xml` (no
`roundIcon` in the manifest), `mipmap-anydpi-v26/ic_banner.xml`, `mipmap-xhdpi/ic_banner*.png`,
`values/ic_banner_background.xml`, and the debug build's "Wholphin Kids" banner in `app/src/debug/res/mipmap-xhdpi/`.
The screensaver's floating picture is now the buddy.

## Icon consistency pass

The twenty icons were redrawn in both sets to DESIGN.md's Icons rules (report: `reports/icons.md`). Each icon was
drawn once, as shared geometry, and written out as its two-tone and outline SVG, so the two sets match; the SVGs in
`design/assets/icons` are the source from then on. The 40 drawables `ic_peke_*.xml` and `ic_peke_outline_*.xml` were
converted again with the same `Svg2Vector` driver and the same `currentColor` → `#FFFFFF` step for the outline set;
the converter printed no warnings. File names and sizes didn't change, so no Kotlin changed.

`IconSpecTest` (JVM, no Robolectric) checks the SVG sources: the 48 grid and live area, stroke widths, round caps and
joins on the root, explicit fills, corner radii from the scale, where the highlight is, that both sets of an icon have
the same bounds, and that no code outside `PekeArt.kt` draws a two-tone icon.
