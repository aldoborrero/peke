# Phase 2 report: assets

Spec 007, User Story 2, tasks T020–T030. Builds on Phase 1 (`f6eb1623`).

## Changes

New:
- `app/src/main/res/drawable/`: 57 vector drawables converted from `design/assets` with AGP's `Svg2Vector`:
  `ic_peke_<name>.xml` (20 two-tone icons), `ic_peke_outline_<name>.xml` (20 outline icons, white, to tint),
  `peke_buddy.xml` and `peke_buddy_{waving,sleeping,puzzled,searching,cheering}.xml`, `avatar_{leo,max,nico,lucy,sara,mia}.xml`,
  `logo_peke.xml`, `ic_banner.xml` (160 × 90 dp), `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`.
- `app/src/main/res/mipmap-anydpi/ic_launcher.xml`: the whole app icon as a vector, for API 23–25.
- `app/src/main/java/dev/aldoborrero/peke/ui/theme/PekeArt.kt`: `PekeIcon` (two-tone and outline per icon),
  `BuddyMood` (six moods, with where each shows), `PekeAvatar` (the plugin's `Avatar` id, drawable, background
  colour; `PekeAvatar.of(id)` accepts only the six lowercase ids).
- `app/src/test/java/dev/aldoborrero/peke/ui/theme/DrawablesTest.kt`.
- `specs/007-peke-interface/assets-conversion.md`: converter, what each SVG feature became, the file map.

Changed:
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`: adaptive icon with the buddy foreground and a monochrome
  layer; `values/ic_launcher_background.xml` is coral `#FF927E`.
- `app/src/main/AndroidManifest.xml`: `android:banner="@drawable/ic_banner"`.
- `ui/components/AppScreensaver.kt`: the floating picture is `R.drawable.peke_buddy`.
- `specs/007-peke-interface/tasks.md` (T020–T030 ticked), `research.md` (R8), `plan.md` (Measured, Phase 2).

Deleted (Wholphin-era launcher art): `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher{,_foreground,_round}.webp`,
`mipmap-anydpi-v26/ic_launcher_round.xml` (no `roundIcon` in the manifest), `mipmap-anydpi-v26/ic_banner.xml`,
`mipmap-xhdpi/ic_banner{,_foreground}.png`, `values/ic_banner_background.xml`, and the debug build's "Wholphin Kids"
banner `app/src/debug/res/mipmap-xhdpi/ic_banner{,_foreground}.png`.

SVG features VectorDrawable can't do: only `currentColor` in the outline icons, replaced with white and tinted at
runtime. The SVGs have no gradients, masks, clip paths, filters, `<use>` or CSS; transforms were baked into the paths
with their stroke widths. The converter printed no warnings.

## Tests

`DrawablesTest` (Robolectric, 6 tests), written before the drawables: the twenty icons in both sets at 48 dp; the
six buddy moods; the six avatars at 200 dp with DESIGN's background colours, and `PekeAvatar.of` taking only the
plugin's lowercase ids; the logo and the 160 × 90 dp banner; the launcher icon adaptive with a vector foreground and
a monochrome layer on API 30; the whole picture as a vector on API 25.

## Validation

- `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`: BUILD
  SUCCESSFUL, 813 tests, 0 failures.
- `nix fmt`: 1 file changed on the first run (`DrawablesTest.kt`), 0 on the second.
- `nix develop -c pre-commit run --all-files`: all hooks passed.

## Screenshots

In the session scratchpad, `…/scratchpad/shots/`:
- `p2-launcher-apps.png`: Google TV's Apps row with Peke's new banner (buddy and "peke for Jellyfin").
- `p2-launcher.png`: Google TV home after install.

The art isn't placed in Peke's screens yet (Phases 3–5).

## APK size

Clean debug builds, Phase 1 → Phase 2:

| ABI | Phase 1 | Phase 2 | Change |
|---|---|---|---|
| arm64-v8a | 39,149,115 | 39,075,997 | −73,118 |
| armeabi-v7a | 37,803,715 | 37,730,593 | −73,122 |
| x86_64 | 39,197,316 | 39,124,198 | −73,118 |

The vectors add about 45 KB (`res/drawable/` +38.6 KB, `resources.arsc` +6 KB); the deleted WebPs and PNGs take off
about 150 KB.

## Commit message

```
Add Peke's art as vector drawables

The PekeTV buddy and its moods, the six kid avatars, the twenty
interface icons in two-tone and outline, and the logo, converted from
design/assets with AGP's SVG converter. The launcher icon is now the
adaptive buddy icon with a monochrome layer, and the TV banner says
"peke for Jellyfin"; Wholphin's launcher art is removed.
```
