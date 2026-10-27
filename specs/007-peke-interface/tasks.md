---

description: "Tasks for 007 Peke's interface"
---

# Tasks: Peke's interface

**Input**: Design documents from `specs/007-peke-interface/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: included, test first: the spec asks for tests of the theme migration, the theme choice and DESIGN's contrast
table (FR-005), and constitution VIII asks for preference migrations and new logic to be tested.

**Organization**: one phase per PR, as the owner asked; each maps to one user story. US6 (measured size) is the last
task of every phase.

Paths are relative to `app/src/main/java/dev/aldoborrero/peke/` unless they start with `app/`, `design/` or
`specs/`; tests are under `app/src/test/java/dev/aldoborrero/peke/`. Strings go in
`app/src/main/res/values/strings.xml` and `app/src/main/res/values-es/strings.xml` (DESIGN Words).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies)
- **[Story]**: the user story from spec.md

## Phase 1: Visual base (US1, PR 1) 🎯 MVP

**Goal**: four themes with PekeTV the default, Wholphin's migrated, Nunito, type, shapes, focus, the Theme choice.
**Independent Test**: quickstart Phase 1; `ThemeMigrationTest`, `ThemeSelectionTest`, `ContrastTest`,
`TypeAndFocusTest`, `PekePreferencesTest`.

### Tests first (they fail until the implementation below)

- [X] T001 [P] [US1] `preferences/ThemeMigrationTest.kt`: for each stored number 0–7 `shouldMigrate` is true and `migrate` gives `PEKE_TV`; for 8–11 `shouldMigrate` is false; for 42 (a newer Peke) `shouldMigrate` is false; the serializer's default is `PEKE_TV` ("stored number 0–7 → 8. Numbers 8–11 and anything above 11 stay", data-model.md)
- [X] T002 [P] [US1] `ui/theme/ThemeSelectionTest.kt`: `pekePalette()` maps `PEKE_TV`, `MANDARINA`, `CARAMELO`, `PAPEL` to their palettes and 0–7, 42 and `UNRECOGNIZED` to PekeTV; `AppPreference.ThemeColors` index ↔ value per data-model.md ("Value → index: the position above, or 0 for anything else"); the default is `PEKE_TV`; Robolectric: `R.array.app_theme_colors` has four names in English and Spanish
- [X] T003 [P] [US1] `ui/theme/ContrastTest.kt`: WCAG 2 contrast of every pair in contracts/design-tokens.md, for the four palettes, equals DESIGN's value ±0.01 and meets its minimum; each palette's eleven role values equal the contract's hex values; Papel's focus border is `#1E2A33`
- [X] T004 [P] [US1] `ui/theme/TypeAndFocusTest.kt`: every `AppTypography` style uses the Nunito family with weight 700, 800 or 900 and size ≥ 11.25 sp; displayLarge 42 sp/900, headlineLarge 36, headlineMedium 27, titleLarge 18, bodyLarge 16.5/800, bodyMedium 13.5, labelMedium 12 (research R4); shapes small 12 dp, medium 18 dp, large 27 dp; `PekeFocus` border 4.5 dp, ring 6 dp, kid scale 1.07, grown-up scale 1.0
- [X] T005 [US1] Update `preferences/PekePreferencesTest.kt`: `AppPreference.ThemeColors` is in This TV after `KidsTimerBar`; drop it from the "left out" list and the `assertFalse` in `settingsKeepEveryItemInItsGroupOrSubScreen`

### Implementation

- [X] T006 [US1] `app/src/main/proto/PekeDataStore.proto`: `AppThemeColors` = `APP_THEME_COLORS_UNSPECIFIED = 0` (comment: was PURPLE), `reserved 1 to 7;`, `reserved "PURPLE", "BLUE", "GREEN", "ORANGE", "BOLD_BLUE", "OLED_BLACK", "RED", "BROWN";`, `PEKE_TV = 8; MANDARINA = 9; CARAMELO = 10; PAPEL = 11;` (R1)
- [X] T007 [US1] Create `preferences/ThemeMigration.kt` (`DataMigration<AppPreferences>`: stored number in 0..7 → `PEKE_TV`) and add it to the app preferences `DataStoreFactory.create(migrations = …)` in `services/hilt/DatabaseModule.kt`; default `PEKE_TV` in `preferences/AppPreferencesSerializer.kt` (R1)
- [X] T008 [US1] Create `ui/theme/PekePalette.kt`: `PekePalette` (eleven roles + `isLight`), `PekeTvPalette`, `MandarinaPalette`, `CarameloPalette`, `PapelPalette` with contracts/design-tokens.md values, `fun AppThemeColors.pekePalette()` (unknown → PekeTV), `PekeColors` and `LocalPekeColors` (R2)
- [X] T009 [US1] Bundle Nunito: `app/src/main/res/font/nunito_bold.ttf`, `nunito_extrabold.ttf`, `nunito_black.ttf` (Nunito 3.602 static, OFL), `app/config/libraries/lib_nunito.json` and `app/config/licenses/lic_ofl_1_1.json` for the license screen (R3)
- [X] T010 [US1] `ui/theme/Type.kt`: `NunitoFamily` and `AppTypography` per research R4 (line height 1.25×), and `AppMaterialTypography` with the same styles (R4)
- [X] T011 [P] [US1] Create `ui/theme/Shape.kt`: `AppShapes` (small 12 dp, medium 18 dp, large 27 dp) (R5)
- [X] T012 [US1] Create `ui/theme/Focus.kt`: `PekeFocus` values, `LocalPekeFocusScale`, `Modifier.pekeFocus(shape)` node (focus listener, layer scale animated 150 ms, z-index while focused, border then ring drawn outside the outline), and `PekeFocus.surfaceScale()/cardScale()/noBorder` defaults for tv-material components (R6)
- [X] T013 [US1] `ui/theme/Theme.kt`: `PekeTheme(appThemeColors)` builds the tv-material and Material 3 schemes from the palette (mapping in research R2), provides `LocalPekeColors`, `AppTypography`, `AppShapes`; `LocalTheme` default `PEKE_TV`; delete `ui/theme/ThemeColors.kt` and `ui/theme/colors/*` (R2)
- [X] T014 [US1] Remove the Wholphin theme switches: `ui/nav/NavDrawer.kt` (`navItemColor`), `ui/preferences/SwitchPreference.kt` (checked track in secondary), `ui/components/SliderBar.kt`, `ui/cards/WatchedIcon.kt`, `ui/playback/overlay/PlaybackControls.kt`, previews in `ui/components/VideoStreamDetails.kt`, `ui/components/PlayButtons.kt`, `ui/playback/NextUpEpisode.kt`, `ui/theme/ThemePreview.kt` (four themes), `MainActivity.kt` (loading scheme from PekeTV), `PekeDreamService.kt`
- [X] T015 [US1] Apply the shared focus: `ui/components/Button.kt`, `ui/components/ListItemWrapper.kt` and the cards in `ui/cards/` (`BannerCard`, `GridCard`, `SeasonCard`, `ViewMoreCard`, `ChapterCard`, `GenreCard`, `StudioCard`, `PersonCard`) use `Modifier.pekeFocus` with tv-material scale 1 and no focused border; `ui/nav/DestinationContent.kt` provides `LocalPekeFocusScale provides false` around grown-up destinations (R6)
- [X] T016 [US1] Theme choice: `preferences/AppPreference.kt` `ThemeColors` over `[PEKE_TV, MANDARINA, CARAMELO, PAPEL]`, default `PEKE_TV`, in `pekePreferences` This TV after `KidsTimerBar`; `R.array.app_theme_colors` in `app/src/main/res/values/arrays.xml` and `values-es` ("PekeTV", "Mandarina", "Caramelo", "Papel (daytime)" / "Papel (de día)"), title "Theme" / "Tema" (R7)
- [X] T017 [US1] Validation: `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`, `nix fmt` twice (0 changed), `nix develop -c pre-commit run --all-files`
- [X] T018 [US1] Screenshots on the emulator: kid home and Settings in PekeTV, and one other theme if reachable without the parent code; store in the session scratchpad. Done for the kid home, the sidebar and the parent gate; Settings and a second theme are behind the parent code, which agents never type, so they wait for the owner
- [X] T019 [US1] [US6] Measure clean debug APKs of the base and Phase 1 per ABI with `apkanalyzer`; record in plan.md "Measured"; stop the Gradle daemons

**Checkpoint**: PR 1 ready.

## Phase 2: Assets (US2, PR 2)

**Goal**: every SVG as a drawable, launcher icon and banner replaced.
**Independent Test**: `DrawablesTest`; the launcher on the emulator.

- [X] T020 [US2] Find the SVG converter available in the devshell (AGP `vd-tool`, the `android` CLI) and record it in research R8; if none, ask to add one to `nix/devshell.nix`
- [X] T021 [P] [US2] `ui/theme/DrawablesTest.kt` (Robolectric): every drawable named in data-model's asset list inflates and has the source's viewport (48×48 icons; buddy, avatars per source)
- [X] T022 [P] [US2] Convert `design/assets/mascot/*.svg` to `app/src/main/res/drawable/peke_buddy*.xml` (six moods)
- [X] T023 [P] [US2] Convert `design/assets/avatars/*.svg` to `app/src/main/res/drawable/avatar_{leo,max,nico,lucy,sara,mia}.xml`
- [X] T024 [P] [US2] Convert `design/assets/icons/*.svg` to `app/src/main/res/drawable/ic_peke_*.xml` (two-tone, colours baked)
- [X] T025 [P] [US2] Convert `design/assets/icons/outline/*.svg` to `app/src/main/res/drawable/ic_peke_outline_*.xml` with a single tintable colour (`currentColor` → `@android:color/white` filled, tinted at runtime)
- [X] T026 [P] [US2] Convert `design/assets/logo-peke.svg` to `app/src/main/res/drawable/logo_peke.xml`
- [X] T027 [US2] Adaptive launcher icon from `design/assets/app-icon.svg`: `app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`; PNG fallbacks for API 23–25 in `mipmap-*dpi`, rendered from the SVG
- [X] T028 [US2] TV banner from `design/assets/tv-banner.svg` as `@mipmap/ic_banner` (vector, with an xhdpi PNG fallback if API 23 needs it) referenced by `app/src/main/AndroidManifest.xml`
- [X] T029 [US2] Write `specs/007-peke-interface/assets-conversion.md`: per file, converter result, unsupported features (`<style>`, `<use>`, masks, filters, gradients on API 23, `clip-path`) and workaround
- [X] T030 [US2] [US6] Validation as T017, launcher screenshot, size measured and recorded as T019

## Phase 3: Kid screens (US3, PR 3)

**Goal**: avatars on the picker and sidebar, the home, details, player, timer bar.
**Independent Test**: quickstart Phase 3.

- [X] T031 [US3] Test in `kids/KidProfileRepositoryTest.kt`: a daily-time save sends the cached `Avatar` and an unknown field back unchanged. The plugin's `PUT` is a full replacement; `KidProfileMapper.toUpdate` already starts from the cached server JSON, so nothing is cleared (contracts/kids-api-avatar.md, "Writing the profile")
- [X] T032 [P] [US3] Tests in `kids/KidAvatarsTest.kt`: `Avatar` read from the cached profile; values `leo`, `max`, `nico`, `lucy`, `sara`, `mia` matched lowercase, "Omitted when the kid has none", anything else treated as none; ignored without the `avatar` feature; another server's kid excluded. `ApiMinorVersion` isn't read: the plugin's contract says to check `Features`, which Peke does
- [X] T033 [P] [US3] (changed) No Room migration: the avatar is read from the profile JSON the server last sent (`KidProfile.serverJson`), like the live TV channels, so it is cached with the profile and needs no column
- [X] T034 [US3] `KidsFeatures.AVATAR = "avatar"` and `KidProfileMapper.avatarId` in `kids/server/KidsDtos.kt`; `KidProfileMapper.toUpdate` already sends it back (T031); the enum is `PekeAvatar` in `ui/theme/PekeArt.kt` (Phase 2). `KidProfileRef` isn't changed: profiles are refreshed when their revision changes, which an avatar change does
- [X] T035 [US3] `kids/data/KidAvatars.kt`: each kid's avatar by row id from the cached profiles, empty while the cached plugin row lacks `avatar`; `KidProfileDao.observeAll()` (a query, no schema change)
- [X] T036 [P] [US3] Tests in `kids/KidAvatarsTest.kt`: avatar → profile image → initial order; image error falls back to the initial; `kids/setup/KidsPickerTest.kt`: the picker shows each kid's avatar
- [X] T037 [US3] `kids/setup/KidAvatar.kt`: draw the avatar on its colour, else the image, else the initial (FR-017, FR-018)
- [X] T038 [US3] Picker ("Who's watching?") per canvas 1b: avatar tiles with `pekeFocus(CircleShape)`, names in Headline, Grown-ups last and never first focus, in `kids/setup/KidsPicker.kt`
- [X] T039 [US3] Sidebar per DESIGN Behaviour with outline icons, avatar at the top, Grown-ups at the bottom, selected in primary and others in text secondary while closed: `ui/nav/NavDrawer.kt`, `ui/nav/ProfileGate.kt`, `kids/ui/KidsUi.kt`
- [X] T040 [US3] Kids home rows and cards: an 18 dp minimum gap and room for the focus ring in `ui/cards/ItemRow.kt`, row spacing in `ui/main/HomePage.kt`; Section titles were already titleLarge
- [X] T041 [US3] Details pages: Peke's play icon and spacing for the focus ring in `ui/components/PlayButtons.kt`; the pills, focus and type come from Phase 1. 004's kid rules unchanged
- [X] T042 [US3] Player overlay: Peke's play, pause, subtitles and next-episode outline icons in `ui/playback/overlay/PlaybackControls.kt`
- [X] T043 [P] [US3] Tests: `kids/KidsTimeLimitsTest.kt` (prominent from the first `WarnAtSeconds` point, counted down while playing, a sitting's own points, nothing when unlimited, stopped or without points) and `kids/TimeLeftBarTest.kt` (minutes rounded up, never 0)
- [X] T044 [US3] Timer bar: `kids/ui/TimeLeftBar.kt` (time colour, outline time-left icon, minutes label; taller with a Section-size label from the first warning, no flashing), used by `kids/ui/KidsTimerEffects.kt`, whose warning now matches it; `KidsLimits.nearEnd` in `kids/timer/KidsTimeLimits.kt` (R10)
- [X] T045 [US3] Strings: `kids_time_bar_minutes`, English and Spanish
- [X] T046 [US3] [US6] Validation as T017, screenshots, size measured and recorded

## Phase 4: Stops and states (US4, PR 4)

**Goal**: the mascot stop screens and the loading, empty and error states.
**Independent Test**: quickstart Phase 4.

- [X] T047 [P] [US4] Tests in `kids/KidsStopScreenTest.kt`: each stop reason maps to its mood (time's up → waving, outside hours → sleeping, break → buddy) and each kid error to offline (searching) or oops (puzzled) by its cause; Grown-ups never first focus is kept from 005 (focus starts on the message)
- [X] T048 [US4] Shared stop layout in `kids/ui/KidsTimesUpPage.kt` (`KidsGrownUpsMessage`: buddy in its mood, Display title, Supporting line, Grown-ups small in the bottom corner); `kids/ui/KidsStop.kt` (new): `stopMood`, `KidsTrouble`, `KidsTroubleMessage`, `KidsEmptyMessage`, `isKidScreen`
- [X] T049 [US4] Used by time's up, bedtime and the break (`KidsTimesUpPage`), the refused channel (puzzled), can't play (`ui/playback/PlaybackPage.kt` through `ErrorMessage`) and offline (any kid error caused by a network error); `kids/ui/KidsEndScreen.kt` gets the cheering buddy
- [X] T050 [US4] `ui/components/ErrorMessage.kt`: kids get the buddy page, without the error or the log button (grown-up pages, marked by `LocalGrownUpPage` from `GrownUpGuard`, keep them); `ui/components/CircularProgress.kt`: the buddy above the spinner on kid screens and at start-up (`MainActivity`); empty grids (`ui/detail/CardGrid.kt`) and searches (`ui/search/SearchPage.kt`) show the buddy and a line; search errors no longer show kids the exception. Fixed on the way: the search box and the text input dialog drew text in a container's "on" colour, navy on navy in PekeTV (`ui/components/EditTextBox.kt`, `ui/preferences/StringInputDialog.kt`)
- [X] T051 [US4] Strings `kids_oops_title`, `kids_oops_wont_play`, `kids_oops_hint`, `kids_offline_title`, `kids_offline_hint`, `kids_empty`, `kids_search_empty`, English and Spanish
- [X] T052 [US4] [US6] Validation as T017, screenshots, size measured and recorded

## Phase 5: First setup (US5, PR 5)

**Goal**: setup steps in the canvas look, behaviour unchanged.
**Independent Test**: 004's setup tests unchanged and green; the owner walks setup after a reset.

- [X] T053 [US5] Every setup step (select and add server, the administrator, the parent code, picking kids, their sign-in with Quick Connect, voice search) shares `SetupFlow`'s root in `kids/setup/SetupFlow.kt`: the Peke logo (buddy and wordmark) as the header, step titles in Title style, grown-up focus (border and ring, no scale) and errors with their details (`LocalPekeFocusScale` false, `LocalGrownUpPage` true); the shared spinner in `ui/components/CircularProgress.kt` is primary. 004's behaviour unchanged
- [X] T054 [US5] Strings: none changed (the header reuses `app_name`)
- [X] T055 [US5] [US6] Validation as T017, size measured and recorded; setup screenshots need a reset or the parent code, so the owner takes them

## Phase 6: Grown-ups screens full-screen (US7, PR 6) ⛔ Blocked

**Blocked** on the owner's review of the 006 grown-up flows. No task here starts until then; the review may change
this phase.

- [ ] T056 [US7] (blocked) Re-specify US7 after the owner's review of the 006 flows
- [ ] T057 [US7] (blocked) Parent menu, Add time, a kid's rules, grown-up setup and Settings as full-screen pages per canvas 8–12 in `kids/ui/` and `ui/preferences/`
- [ ] T058 [US7] (blocked) Validation as T017, size measured and recorded

## Dependencies & Execution Order

- Phase 1 first: every later phase draws with its palette, type, shapes and focus.
- Phase 2 needs nothing from Phase 1 and can be reviewed in parallel; Phases 3–4 need both.
- Phase 3: the plugin side is done (Jellyfin Kids 0.0.8.0, released in 0.0.9.0: `avatar`, `ApiMinorVersion`). Saves on the
  TV already keep the avatar (T031).
- Phase 4 after Phase 3 (shares the stop pages' Grown-ups corner and the buddy drawables); Phase 5 after Phase 2.
- Phase 6 blocked on the owner.
- Within Phase 1: T001–T005 first (failing), then T006 → T007; T008 → T013 → T014; T009 → T010; T011, T012 → T015;
  T016; then T017–T019.

### Parallel opportunities

- Phase 1: T001–T004 together; T009 and T011 alongside T006–T008.
- Phase 2: T021–T026 together.
- Phase 3: T032, T033, T036, T043 together.

## Implementation Strategy

MVP is Phase 1: once merged, every screen already uses Peke's colours, font, shapes and focus. Each later phase is
its own PR, measured for size, and leaves 004–006's behaviour and tests unchanged.
