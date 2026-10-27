# Implementation Plan: Canvas screens

**Branch**: `008-canvas-screens` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-canvas-screens/spec.md`

## Summary

The remaining kid screens move from Wholphin's layouts to the design canvas boards, in six phases that each ship as a
PR. Every phase changes composables only, plus the strings and icons they need; view models, navigation, the player
and data layers keep their behaviour (one read-only state is exposed in Phase 3, R7).

1. **Home** (US1): greeting header with the time left (R4), the three kids card kinds (R3), the rail and the open
   sidebar (R5); no backdrop or focused-item header for kids (R2).
2. **Details and seasons** (US2): the kids details header on the season, series and film pages, wide episode cards, the
   Seasons dialog (R6).
3. **Player and next-up** (US3): round transport buttons, the thick progress bar, the warning pill at the top right,
   the next-up panel and the in-a-row chip (R7).
4. **Live TV** (US4): a list of channel cards instead of the EPG grid for kids (R8).
5. **Search, voice, favourites, library** (US5): the pill search field, Peke's search, voice and new filter icons, the
   listening page, favourites and library grids with kids posters and chips (R9, R11).
6. **Other screens** (US6): the night screensaver, the update page and the microphone consent (R10, R11).

## Technical Context

- **Language/Version**: Kotlin 2.4 (K2), AGP 9.2.1, Gradle 9.8, JVM 11 target (unchanged).
- **Primary Dependencies**: Compose for TV (`androidx.tv:tv-material`), Compose Material 3 for the few M3 parts, Coil 3,
  Hilt. **No new dependency.**
- **Storage**: none changed.
- **Testing**: JUnit4, MockK, Robolectric (constitution VIII). Pure layout decisions get unit tests (card kinds, time
  left, season circle colours, now/next per channel, in-a-row chip); the composables are checked on the emulator, since
  the module has no Compose UI test setup (as in 007). Validation per phase:
  `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`, `nix fmt`
  twice (0 changed on the second), `nix develop -c pre-commit run --all-files`, then `nix develop -c ./gradlew --stop`.
- **Target Platform**: Android TV, Google TV, Fire TV; minSdk 23, target/compile 37; D-pad.
- **Project Type**: single Android app module (`app/`).
- **Performance Goals**: no extra recomposition per frame: focus stays in 007's `pekeFocus` node; the countdown ring and
  progress bars draw from state already there.
- **Constraints**: 1 canvas px = 0.75 dp (R1). Colours from theme roles only. MPV, FFmpeg and AV1 untouched. Every
  string in English and Spanish. No text below DESIGN's Small (15 px) even where a board uses 14 px.
- **Scale/Scope**: about 6–12 files per phase; 2 new icons (filter in Phase 5, download in Phase 6) and one drawable
  (the night scene, Phase 6).

## Constitution Check

*Gate before Phase 0, re-checked after Phase 1.*

| Principle | Check | Status |
|---|---|---|
| I. Kids-only app | Only kid screens change, plus two grown-up pages the owner named (update, microphone consent); nothing for grown-up viewing is added | Pass |
| II. Lean fork, recognisable core | The player's view model gains one read-only state (Phase 3); `ui/playback` layout files change only in kids branches; data and API layers untouched | Pass |
| III. The plugin decides | The time left shown on the home is the server's count (KidsLimits); no client-side count | Pass |
| IV. Parent-gated and fail-closed | The drawer's Grown-ups entry and avatar keep the gate; focus never lands on them first; Back unchanged; update and mic pages stay behind the gate | Pass |
| V. TV-first, D-pad | Every new layout is a D-pad focus order of existing focusables; the seasons dialog opens on the current season | Pass |
| VI. Private by default | No permission, network call or data collection added | Pass |
| VII. GPLv2 and branding | Icons and art are Peke's own; no Jellyfin logo | Pass |
| VIII. Tested where it matters | Unit tests for the new pure decisions; 004–006 tests unchanged | Pass |

**Permissions (VI)**: none added or removed.

**APK size**: each phase compares clean debug builds of the previous phase and the phase (same toolchain, per ABI).

| Phase | Expected change per ABI |
|---|---|
| 1 | code and strings: +10 to +40 KB |
| 2 | code: +10 to +40 KB |
| 3 | code: +5 to +30 KB |
| 4 | code: +5 to +25 KB |
| 5 | code and one icon pair: +10 to +40 KB |
| 6 | code, one icon pair and the night scene: +10 to +40 KB |

## Project Structure

### Documentation (this feature)

```text
specs/008-canvas-screens/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── boards.md            # each board → the composables that draw it, and the board values used
├── checklists/
│   └── requirements.md
├── reports/
│   └── phase-1.md … phase-6.md
└── tasks.md
```

### Source Code (repository root)

Paths under `app/src/main/java/dev/aldoborrero/peke/` unless shown in full.

```text
# Phase 1
ui/theme/Canvas.kt                    # new: cpx/csp, the rail colour (R1, R5)
kids/home/KidsHomeLayout.kt           # new: kidsCardKind, homeTimeLeft (R3, R4)
kids/home/KidsHomeUi.kt               # new: header, time-left pill, home cards (R2–R4)
kids/ui/KidsCards.kt                  # new: shared kids cards, progress, badges (used again in Phases 2 and 5)
ui/main/HomePage.kt                   # kids branch: header, cards, spacing, no backdrop
ui/cards/ItemRow.kt                   # optional title gap and bleed for rows aligned with their title
kids/ui/KidsTimerEffects.kt           # no bar on the home
ui/nav/NavDrawer.kt, ui/nav/NavigationDrawerAndroid.kt   # rail and sidebar (R5)
app/src/main/res/values{,-es}/strings.xml
test: kids/KidsHomeLayoutTest.kt

# Phase 2
kids/ui/KidsDetails.kt                # new: KidsDetailsHeader, KidsSeasonsDialog, seasonCircle (R6)
ui/detail/series/SeriesOverviewContent.kt, SeriesOverview.kt, SeriesDetails.kt, SeriesViewModel.kt
ui/detail/movie/MovieDetails.kt, MovieViewModel.kt
test: kids/KidsDetailsTest.kt

# Phase 3
ui/playback/overlay/PlaybackControls.kt, PlaybackController.kt, SeekBar.kt
ui/playback/NextUpEpisode.kt, PlaybackPage.kt, PlaybackViewModel.kt (read-only in-a-row count)
kids/ui/KidsTimerEffects.kt           # the warning's player form (shared with Phase 1: a different part of the file)
test: kids/KidsPlayerTest.kt

# Phase 4
kids/livetv/KidsLiveGuide.kt          # new: the list and now/next helpers (R8)
ui/nav/DestinationContent.kt          # KidsLiveTv → KidsLiveGuide
test: kids/KidsLiveGuideTest.kt

# Phase 5
ui/search/SearchPage.kt, SearchPageCard.kt, ui/components/EditTextBox.kt, VoiceSearchButton.kt
ui/detail/FavoritesPage.kt, ui/components/CollectionFolderGrid.kt, CollectionFolderView.kt
design/assets/icons/filter.svg, outline/filter.svg, res/drawable/ic_peke_{,outline_}filter.xml, ui/theme/PekeArt.kt
test: IconSpecTest/DrawablesTest cover the icon

# Phase 6
ui/components/AppScreensaver.kt, res/drawable/peke_night.xml
ui/preferences/InstallUpdatePage.kt, ui/preferences/VoiceSearchConsent.kt
design/assets/icons/download.svg, outline/download.svg, res/drawable/ic_peke_{,outline_}download.xml, ui/theme/PekeArt.kt
```

**Shared files between phases** (each phase edits its own part; the reports say so): `strings.xml` (a block per
phase), `kids/ui/KidsCards.kt` (Phase 1 creates it; 2 and 5 use it, adding a parameter at most),
`kids/ui/KidsTimerEffects.kt` (Phases 1 and 3), `ui/theme/PekeArt.kt` and the icon folders (Phases 5 and 6).

**Structure Decision**: the single `app/` module; kids layouts under `kids/`, small kids branches in the shared pages.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| A kids branch inside shared Wholphin pages (home, details, player, search) | The pages hold 004–005's click, Play-key and focus rules; restyling them in place keeps those rules and their tests | Separate kids pages would duplicate the hardened handlers |
| `PlaybackViewModel` exposes its in-a-row count (Phase 3) | KNextUpPekeTV shows it; the count already exists | Counting again in the UI would be a second, possibly different count |

## Measured

Filled per phase from the reports.

### Phase 1 (2026-10-27, T011)

Clean debug builds (`clean :app:assembleDebug`) of the base (`1ab9994c`) and Phase 1, same toolchain:

| ABI | Base (bytes) | Phase 1 (bytes) | Change |
|---|---|---|---|
| arm64-v8a | 39,093,776 | 39,143,649 | +49,873 |
| armeabi-v7a | 37,748,378 | 37,798,245 | +49,867 |
| x86_64 | 39,141,987 | 39,191,851 | +49,864 |

`apkanalyzer apk compare` (x86_64): dex +48.4 KB (two files take +17.6 and +17.3 KB: the new kids home, cards and drawer
code, unminified in debug), `resources.arsc` +0.7 KB (strings). A little over the +40 KB estimate; R8 shrinks the dex
in release.

### Phase 2 (2026-10-27, T019)

Clean debug builds, Phase 1 → Phase 2 (Phase 2 built alone in a scratch clone of the base with Phase 2's tree):
arm64-v8a 39,143,649 → 39,193,428 (+49,779), armeabi-v7a 37,798,245 → 37,848,029 (+49,784), x86_64 39,191,851 →
39,241,640 (+49,789). Dex for the details header, seasons list and season page; strings. Over the +40 KB estimate, as
in Phase 1 (unminified debug dex).

### Phase 3 (2026-10-27, T026)

Clean debug builds, Phase 2 → Phase 3: arm64-v8a 39,193,428 → 39,226,602 (+33,174), armeabi-v7a 37,848,029 →
37,881,204 (+33,175), x86_64 39,241,640 → 39,274,807 (+33,167). Dex and strings, within the +5 to +30 KB estimate
plus 10%.

### Phase 4 (2026-10-27, T031)

Clean debug builds, Phase 3 → Phase 4: arm64-v8a 39,226,602 → 39,243,099 (+16,497), armeabi-v7a 37,881,204 →
37,897,704 (+16,500), x86_64 39,274,807 → 39,291,310 (+16,503). Within the +5 to +25 KB estimate.
