---

description: "Tasks for 008 Canvas screens"
---

# Tasks: Canvas screens

**Input**: Design documents from `specs/008-canvas-screens/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/boards.md

**Tests**: test first for the pure layout decisions (data-model.md); constitution VIII. Composables are checked on the
emulator against their boards (no Compose UI test setup in the module, as in 007).

**Organization**: one phase per PR, as the owner asked; each maps to one user story. US7 (measured, unchanged
behaviour) is the last task of every phase, with its report in `specs/008-canvas-screens/reports/phase-N.md`.

Paths are relative to `app/src/main/java/dev/aldoborrero/peke/` unless they start with `app/`, `design/` or `specs/`;
tests are under `app/src/test/java/dev/aldoborrero/peke/`. Strings go in `app/src/main/res/values/strings.xml` and
`values-es/strings.xml`, one block per phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies)
- **[Story]**: the user story from spec.md

## Phase 1: Kids home (US1, PR 1) 🎯 MVP

**Goal**: the home and sidebar of HomeRailPekeTV and HomeDrawerPekeTV.
**Independent Test**: quickstart 1; `KidsHomeLayoutTest`; 004–005's home tests unchanged.

- [X] T001 [P] [US1] `kids/KidsHomeLayoutTest.kt`: `kidsCardKind` ("keep watching, next up, their combined row → WIDE; TV programmes and channels → CHANNEL; the rest → POSTER") and `homeTimeLeft` ("null without a daily limit, while unknown, or when the bar is Hidden; minutes rounded up and never 0; `today` when the allowance runs out no later than the sitting"; nearEnd as the bar)
- [X] T002 [P] [US1] `ui/theme/Canvas.kt`: `cpx`, `csp` (0.75 per px) and `PekePalette.rail` (background 16% darker)
- [X] T003 [US1] `kids/home/KidsHomeLayout.kt`: `KidsCardKind`, `kidsCardKind`, `HomeTimeLeft`, `homeTimeLeft` (T001 green)
- [X] T004 [US1] `kids/ui/KidsCards.kt`: `KidsPictureCard` (picture or theme-coloured tile with the title, title under the card, focused title in text colour), `KidsCardSurface` (pekeFocus, no tv-material scale/border), `KidsProgress`, `KidsWatchedBadge`, `KidsFavouriteBadge`, `KidsLiveBadge`, `tileColor`
- [X] T005 [US1] `kids/home/KidsHomeUi.kt`: `KidsHomeHeader` (greeting 28 px, question 18 px, time-left pill, the clock when on), `KidsTimeLeftPill`, `KidsHomeCard` (WIDE 320×180, CHANNEL 240×112, POSTER 140×200), `KidsTimeLeftViewModel`
- [X] T006 [US1] `ui/main/HomePage.kt`: kids branch: `KidsHomeHeader` instead of `HomePageHeader`, `KidsHomeCard` per `kidsCardKind(row.rowType)`, page padding 40/48 px, 26 px between rows, 14 px title to cards, no backdrop updates and the backdrop cleared; clicks, Play key and the context menu unchanged
- [X] T007 [US1] `ui/cards/ItemRow.kt`: optional `titleGap` and `bleed` (cards aligned with the title, room for the ring) used by the kids home
- [X] T008 [US1] `kids/ui/KidsTimerEffects.kt`: the bar doesn't draw while the top destination is the home (the header shows the time instead); the warning still shows
- [X] T009 [US1] `ui/nav/NavDrawer.kt`, `ui/nav/NavigationDrawerAndroid.kt`: closed rail (104 px, rail colour, avatar 64 px, 36 px icons, current page on a 64 px surface tile in primary, Grown-ups at the bottom) and the open sheet (380 px over the page, page at 35%, avatar 72 px with name and time left, rows with 24 px names, focused row primary with "on primary", Grown-ups 20 px); the clock not drawn on the kids home (the header has it); focus rules unchanged (open → current page, never the avatar)
- [X] T010 [US1] Strings: `kids_live_badge`, `kids_home_greeting`, `kids_home_question`, `kids_home_time_left_today`, `kids_home_time_left`, English and Spanish
- [X] T011 [US1] [US7] Validation (plan Technical Context), screenshots of the home, a lower row and the open sidebar, size against the base, `reports/phase-1.md`

**Checkpoint**: PR 1 ready.

## Phase 2: Details and seasons (US2, PR 2)

**Goal**: DetailsPekeTV and KSeasonsPekeTV.
**Independent Test**: quickstart 2; `KidsDetailsTest`; 004's details rules unchanged.

- [X] T012 [P] [US2] `kids/KidsDetailsTest.kt`: season circle colours ("not focused: secondary, tertiary, time, primary in turn with 'on primary'; focused: the background colour with the number in 'time'"), the episode label "{n}. {name}" (name alone without a number), the chips of a season, a series and a film
- [X] T013 [US2] `kids/ui/KidsDetails.kt`: `KidsDetailsHeader` (poster 260×380, Display title, chips, overview, Play pill 72 px, round favourite 72 px, extra round buttons for what the page already offers), `KidsSeasonsDialog`, `seasonCircle`, `episodeLabel`, chips helpers (T012 green)
- [X] T014 [US2] `ui/detail/series/SeriesOverviewContent.kt`, `SeriesOverview.kt`: kids branch with `KidsDetailsHeader` for the season (Play episode N on the focused episode, resume as today), the season chip opening `KidsSeasonsDialog` (more than one season), no TabRow, wide episode cards 260×146 with labels, watched badge and progress
- [X] T015 [US2] `ui/detail/series/SeriesDetails.kt`: kids branch with `KidsDetailsHeader` (Play next up, favourite, watched, shuffle as today) and season cards as kids posters
- [X] T016 [US2] `ui/detail/movie/MovieDetails.kt`: kids branch with `KidsDetailsHeader` (year and runtime chips, Play/Resume, favourite, watched, restart as today)
- [X] T017 [US2] No backdrop on kids details: `SeriesViewModel`, `MovieViewModel` skip `backdropService.submit` in kids mode and the pages clear it
- [X] T018 [US2] Strings for the chips, the seasons title and Play episode N, English and Spanish
- [X] T019 [US2] [US7] Validation, screenshots (series page, season page, seasons dialog, a film), size, `reports/phase-2.md`

## Phase 3: Player and next-up (US3, PR 3)

**Goal**: PlayerPekeTV and KNextUpPekeTV.
**Independent Test**: `KidsPlayerTest`; player tests unchanged; code review against the boards (playback isn't started on
purpose).

- [X] T020 [P] [US3] `kids/KidsPlayerTest.kt`: the in-a-row chip ("shown only with a cap; n is the count of episodes played in a row including the one ending, capped at the cap"), the kids title "{Series} · {episode}" or the film's name
- [X] T021 [US3] `ui/playback/PlaybackViewModel.kt`: expose the existing `autoPlayedInARow` read-only (a getter, read when next-up shows; no logic change); `kids/playback/KidsPlayerUi.kt` (new): `inARow`, `kidsPlayerTitle` (T020 green)
- [X] T022 [US3] `ui/playback/overlay/PlaybackController.kt`, `PlaybackControls.kt`, `SeekBar.kt`: kids look: 26 px title, 12 px bar in primary on outline 50%, round buttons (76 px play/pause in primary, 62 px others on outline 92%), Peke's audio icon
- [X] T023 [US3] `kids/ui/KidsTimerEffects.kt`: on the player, the warning is the dark pill at the top right with the buddy and the minutes in "time"; elsewhere unchanged
- [X] T024 [US3] `ui/playback/NextUpEpisode.kt`, `ui/playback/PlaybackPage.kt`: kids next-up panel at the bottom right (image, Next Up, "{n}. {name}", Play now with the countdown ring, Stop = Back's cancel) and the in-a-row chip at the bottom left
- [X] T025 [US3] Strings: in a row, Play now, Stop, English and Spanish
- [X] T026 [US3] [US7] Validation, screenshots where reachable, size, `reports/phase-3.md`

## Phase 4: Live TV (US4, PR 4)

**Goal**: LiveGuidePekeTV.
**Independent Test**: `KidsLiveGuideTest`; quickstart 4.

- [X] T027 [P] [US4] `kids/KidsLiveGuideTest.kt`: now/next per channel ("now: the programme whose start ≤ now < end; next: the first that starts at or after now's end; progress = elapsed / length, 0–1"), no guide → no now
- [X] T028 [US4] `kids/livetv/KidsLiveGuide.kt`: `channelNowNext`, `KidsLiveGuide` (title, channel cards with tile, LIVE, name, programme, progress, next) on `LiveTvViewModel`; a click plays as the grid's channel cell does
- [X] T029 [US4] `ui/nav/DestinationContent.kt`: `KidsLiveTv` → `KidsLiveGuide`
- [X] T030 [US4] Strings: "Next: %s", English and Spanish
- [X] T031 [US4] [US7] Validation, screenshot of Live TV, size, `reports/phase-4.md`

## Phase 5: Search, voice, favourites, library (US5, PR 5)

**Goal**: KSearchPekeTV, KVoicePekeTV, KFavoritesPekeTV, KLibraryPekeTV; no Material search, mic or filter icons.
**Independent Test**: quickstart 5; `IconSpecTest`, `DrawablesTest` with the new icon.

- [X] T032 [P] [US5] Draw `design/assets/icons/filter.svg` and `outline/filter.svg` (sliders) under DESIGN Icons; convert to `app/src/main/res/drawable/ic_peke_filter.xml`, `ic_peke_outline_filter.xml`; add `PekeIcon.FILTER` in `ui/theme/PekeArt.kt`; `IconSpecTest`, `DrawablesTest` green
- [X] T033 [US5] Search: `ui/components/EditTextBox.kt` (kids: 72 px pill, Peke's search icon), `ui/components/VoiceSearchButton.kt` (72 px tertiary circle with Peke's voice icon; listening page per KVoicePekeTV), `ui/search/SearchPage.kt` (view options with Peke's filter icon; rows with kids cards), `ui/search/SearchPageCard.kt`
- [X] T034 [US5] Favourites: `ui/detail/FavoritesPage.kt` and the grid it uses: the kid's title with the heart, tabs as chips, kids posters with the heart badge, no grid header for kids, the hint pill
- [X] T035 [US5] Library grids: `ui/components/CollectionFolderGrid.kt` header for kids: title 44 px, sort and filter as chips, Peke icons; kids posters in the grid
- [X] T036 [US5] Strings: favourites title and hint, Speak to search, Press Back to stop, English and Spanish
- [X] T037 [US5] [US7] Validation, screenshots (search, results, favourites, a folder), size, `reports/phase-5.md`

## Phase 6: Other screens (US6, PR 6)

**Goal**: OtherScreensaverPekeTV, OtherUpdatePekeTV, OtherMicPekeTV.
**Independent Test**: quickstart 6.

- [ ] T038 [P] [US6] Draw `design/assets/icons/download.svg` and `outline/download.svg`; convert; `PekeIcon.DOWNLOAD`; icon tests green
- [ ] T039 [US6] `ui/components/AppScreensaver.kt`: the night scene for a current kid (sky, stars, moon, hill, sleeping buddy moving every `duration`, clock when on); settings kept
- [ ] T040 [US6] `ui/preferences/InstallUpdatePage.kt`: the board's layout; download and install unchanged
- [ ] T041 [US6] `ui/preferences/VoiceSearchConsent.kt`: the board's dialog for every stage; the permission flow unchanged
- [ ] T042 [US6] Strings: What's new, Later, Download & Update, versions, Allow, Not now, English and Spanish
- [ ] T043 [US6] [US7] Validation, screenshots where reachable, size, `reports/phase-6.md`

## Dependencies & Execution Order

- Phase 1 first: it adds `Canvas.kt` and `KidsCards.kt`, which Phases 2 and 5 use.
- Phases 2–6 each depend on Phase 1 only, and are done in order so each PR applies on the previous one.
- Within a phase: the test task first (failing), then the code, then validation and the report.

### Parallel opportunities

- T001 and T002; T012 with nothing else open; T032 and T038 (icons) are independent of the screen code.

## Implementation Strategy

MVP is Phase 1: the home is where every session starts. Each later phase is its own PR, measured, and leaves 004–006's
behaviour and tests unchanged.
