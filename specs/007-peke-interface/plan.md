# Implementation Plan: Peke's interface

**Branch**: `007-peke-interface` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/007-peke-interface/spec.md`

## Summary

DESIGN.md built into the app, in six phases that each ship as their own PR:

1. **Visual base** (US1): the four themes as one palette type with DESIGN's values, read through the tv-material
   `ColorScheme` plus a `PekeColors` local for the extra roles (time, outline, focus border, text secondary); PekeTV
   the default; the `AppThemeColors` proto keeps 0–7 reserved and gains 8–11, with a DataStore migration that rewrites
   a saved Wholphin value to PekeTV once (R1, R2). Nunito 700/800/900 as static font resources (R3), the type scale and
   shapes at 0.75 dp per canvas px (R4, R5), one focus modifier for border, ring and scale used by the shared
   components (R6), and the Theme choice in Settings › This TV (R7). The eight Wholphin palettes are deleted.
2. **Assets** (US2): SVG → VectorDrawable for the buddy and moods, avatars, both icon sets, logo, adaptive app icon and
   banner, with a conversion log (R8).
3. **Kid screens** (US3): avatars from the plugin (`avatar`, Kids API 1.1) with fallbacks (R9), the picker, home with
   the sidebar, details, player and the timer bar (R10).
4. **Stops and states** (US4): the mascot stop screens, loading, empty and error states.
5. **First setup** (US5): the setup steps in the canvas look.
6. **Grown-ups full-screen** (US7): specified, **blocked** on the owner's review of the 006 flows.

## Technical Context

- **Language/Version**: Kotlin 2.4 (K2), AGP 9.2.1, Gradle 9.8, JVM 11 target (unchanged).
- **Primary Dependencies**: Compose for TV (`androidx.tv:tv-material` 1.1.0-alpha01), Compose Material 3 (for the few
  M3 components still used), Proto DataStore, Hilt, AboutLibraries (license screen). **No new dependency**: Nunito is a
  font resource and the pictures are vector drawables.
- **Storage**: `AppThemeColors` in `PekeDataStore.proto` (values 8–11 added, 0–7 reserved); one `DataMigration` on the
  app preferences DataStore. Phase 3 caches the avatar with the kid profile (Room, kids database: a migration and its
  test, Principle VIII).
- **Testing**: JUnit4, MockK, Robolectric. `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest
  compileDebugAndroidTestKotlin`, `nix fmt` (twice), `nix develop -c pre-commit run --all-files`, then
  `nix develop -c ./gradlew --stop`.
- **Target Platform**: Android TV, Google TV, Fire TV; minSdk 23, target/compile 37; D-pad.
- **Project Type**: single Android app module (`app/`).
- **Performance Goals**: focus motion is scale and opacity only, animated in the layer (no recomposition per frame);
  no blur. Vector drawables for pictures under 500 path nodes each, rendered once to a cached bitmap by the platform.
- **Constraints**: minSdk 23 can't apply font variation axes, so static instances are bundled (R3). MPV, FFmpeg and AV1
  untouched. Every string in English and Spanish.
- **Scale/Scope (Phase 1)**: about 20 files changed, 8 palette files deleted, 4 new source files, 3 font files, 2
  license config files, 4 test files.

## Constitution Check

*Gate before Phase 0, re-checked after Phase 1.*

| Principle | Check | Status |
|---|---|---|
| I. Kids-only app | Only presentation changes; the theme choice is a TV setup setting in grown-up Settings | Pass |
| II. Lean fork, recognisable core | Wholphin's palettes and their enum switches are deleted (allowed). The player, data and API layers are untouched; the proto keeps Wholphin's numbers reserved | Pass |
| III. The plugin decides | Avatars come from the plugin profile (Phase 3), never chosen on the TV; the timer bar only presents the server's time | Pass |
| IV. Parent-gated and fail-closed | The theme choice lives in Settings behind the gate; an unknown theme value falls back to PekeTV. Phase 3–4 keep every gate, Grown-ups never first focus | Pass |
| V. TV-first, D-pad | A stronger focus (border, ring, scale) on every shared component; no touch-only parts | Pass |
| VI. Private by default | No permission, network call or data collection added. Nunito is bundled, never downloaded | Pass |
| VII. GPLv2 and branding | Nunito (OFL 1.1) is listed on the license screen with its license; the app icon and banner are Peke's buddy, never the Jellyfin logo | Pass |
| VIII. Tested where it matters | Tests for the theme migration, theme resolution and the Settings choice, DESIGN's contrast table, the type scale; Phase 3's Room change gets a migration test | Pass |

**Permissions (VI)**: none added or removed.

**APK size**: release builds need the owner's extensions token, which agents don't handle, so each phase compares
clean debug builds of the base and the phase (same toolchain, per ABI) and lists what the phase added with
`apkanalyzer`. The owner may record release sizes when building a release.

| Phase | Expected change per ABI |
|---|---|
| 1 | Nunito, three static TTFs of 125 KB each (Latin, Latin Extended, Cyrillic, Vietnamese): +250 to +380 KB (TTFs compress about 35% in the APK); minus the eight Wholphin palettes, a few KB of dex. Measured: +153 KB |
| 2 | 6 mascot + 6 avatar + 40 icon + logo + icon + banner vector drawables: +60 to +150 KB (binary XML); the Wholphin launcher PNGs they replace, if any, come off |
| 3–5 | code and strings: within ±50 KB |

**Measured**: see the "Measured" section at the end, filled per phase.

**Post-design re-check (Phase 1 design)**: the contracts keep every rule above. Pass.

## Project Structure

### Documentation (this feature)

```text
specs/007-peke-interface/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── design-tokens.md        # themes, type, shape, focus as the app reads them
│   └── kids-api-avatar.md      # proposed plugin field for Phase 3
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

Paths under `app/src/main/java/dev/aldoborrero/peke/` unless shown in full.

```text
# Phase 1
app/src/main/proto/PekeDataStore.proto       # AppThemeColors: 0 unspecified, 1–7 reserved, PEKE_TV 8 … PAPEL 11 (R1)
app/src/main/res/font/nunito_bold.ttf, nunito_extrabold.ttf, nunito_black.ttf   # new (R3)
app/config/libraries/lib_nunito.json, app/config/licenses/lic_ofl_1_1.json     # new: license screen (R3)
app/src/main/res/values{,-es}/arrays.xml, strings.xml   # four theme names (R7)
ui/theme/PekePalette.kt                      # new: the four palettes and AppThemeColors → palette (R2)
ui/theme/Theme.kt                            # PekeTheme: schemes from the palette, LocalPekeColors, shapes, type (R2, R4, R5)
ui/theme/Type.kt                             # Nunito family and the type scale (R4)
ui/theme/Shape.kt                            # new: Shapes (R5)
ui/theme/Focus.kt                            # new: PekeFocus defaults and Modifier.pekeFocus (R6)
ui/theme/ThemeColors.kt, ui/theme/colors/*   # deleted
ui/theme/ThemePreview.kt                     # previews of the four themes
preferences/AppPreference.kt                 # ThemeColors choice over the four; in This TV (R7)
preferences/AppPreferencesSerializer.kt      # default PEKE_TV
preferences/ThemeMigration.kt                # new: DataMigration (R1)
services/hilt/DatabaseModule.kt              # the migration on the DataStore
ui/components/Button.kt, ListItemWrapper.kt, ui/cards/*.kt   # the shared focus (R6)
ui/nav/DestinationContent.kt                 # grown-up pages: no focus scale (R6)
ui/nav/NavDrawer.kt, ui/preferences/SwitchPreference.kt, ui/components/SliderBar.kt,
ui/cards/WatchedIcon.kt, ui/playback/overlay/PlaybackControls.kt, ui/components/VideoStreamDetails.kt,
ui/components/PlayButtons.kt, ui/playback/NextUpEpisode.kt, MainActivity.kt, PekeDreamService.kt   # Wholphin theme switches removed

app/src/test/java/dev/aldoborrero/peke/
├── preferences/ThemeMigrationTest.kt        # new
├── preferences/PekePreferencesTest.kt       # Theme in This TV
├── ui/theme/ThemeSelectionTest.kt           # new: resolution, choice index mapping, names
├── ui/theme/ContrastTest.kt                 # new: DESIGN's contrast table
└── ui/theme/TypeAndFocusTest.kt             # new: type scale, shapes, focus constants

# Phase 2
app/src/main/res/drawable/peke_buddy*.xml, avatar_*.xml, ic_peke_*.xml, ic_peke_outline_*.xml, logo_peke.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml, drawable/ic_launcher_{background,foreground,monochrome}.xml
app/src/main/res/drawable-xhdpi/banner.xml (or drawable/banner.xml)
specs/007-peke-interface/assets-conversion.md   # conversion log (FR-014)

# Phase 3
kids/server/KidsDtos.kt, KidsApi.kt           # avatar feature and field (contracts/kids-api-avatar.md)
kids/data/*                                   # avatar cached with the kid (Room migration + test)
kids/setup/KidAvatar.kt, KidsPicker*          # avatars and fallbacks
ui/nav/NavDrawer.kt, ui/main/*, ui/detail/*   # home, sidebar, details
kids/timer/KidsTimerBar*.kt, kids/ui/KidsTimerEffects.kt   # timer bar

# Phase 4
kids/ui/KidsTimesUpPage.kt, KidsEndScreen.kt, ui/components/LoadingPage, ErrorMessage, empty states

# Phase 5
kids/setup/SetupFlow*.kt and its pages
```

**Structure Decision**: the single `app/` module. Theme code stays in `ui/theme/`; kids pictures as resources.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| A custom focus modifier instead of tv-material's `Border` and scale | DESIGN needs two strokes (border and ring) that scale with the item; tv-material draws one border per state, and its scale sits inside the component, so a ring added from outside wouldn't scale with it | `Glow` blurs (DESIGN: no blur); a ring drawn inside each card's content is clipped and repeated per component |
| Phase 3 depends on a plugin field not yet published | The avatar is a household rule set in the dashboard (Principle III) | Picking on the TV would diverge between TVs; until the plugin ships it, the fallbacks apply |

## Measured

### Phase 1 (2026-10-27, T019)

Clean debug builds (`clean :app:assembleDebug`) of the base (`0d9d65c9`) and Phase 1, same toolchain:

| ABI | Base (bytes) | Phase 1 (bytes) | Change |
|---|---|---|---|
| arm64-v8a | 38,992,266 | 39,149,115 | +156,849 (+153 KB) |
| armeabi-v7a | 37,646,858 | 37,803,715 | +156,857 (+153 KB) |
| x86_64 | 39,040,457 | 39,197,316 | +156,859 (+153 KB) |

`apkanalyzer apk compare` (x86_64): `res/font/` +171,002 (Nunito Bold 56,270, ExtraBold 57,260, Black 57,472
compressed, from 376 KB raw: the TTFs compress about 55%, better than the 35% expected), the license list +1,468,
dex +15.6 KB in one file and a few hundred bytes less in others (the eight Wholphin palettes deleted, the new theme
code added). Inside the plan's estimate (+250 to +380 KB). Release builds weren't measured (they need the owner's
extensions token); the fonts cost the same there, and R8 shrinks the dex change.
