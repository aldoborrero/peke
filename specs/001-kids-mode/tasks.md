---
description: "Task list for Wholphin for Kids (kids mode v1)"
---

# Tasks: Wholphin for Kids (kids mode v1)

**Input**: Design documents from `specs/001-kids-mode/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/kids-mode.md

**Tests**: Required by constitution VII for non-trivial logic (hasher, challenge, timer, row
builder, autoplay policy, mode state, switcher filter, migration).

**Organization**: Tasks are grouped by user story.

## Format: `[ID] [P?] [Story] Description`

- `M/` = `app/src/main/java/com/github/damontecres/wholphin/`
- `T/` = `app/src/test/java/com/github/damontecres/wholphin/`
- `[P]`: the task can run in parallel with others (different files, no dependencies).

---

## Phase 1: Setup

- [x] T001 Create package `M/kids/` with `KidsModule.kt` (Hilt `@InstallIn(SingletonComponent)`, provides `MonotonicClock` and `KidProfileDao`), so `TestModule` only needs the DAO added
- [x] T002 [P] Add the kids strings (defaults only) to `app/src/main/res/values/strings.xml`

---

## Phase 2: Foundational (blocks all stories)

- [x] T003 Create the `KidProfile` entity and `KidsHomeSection` enum (+ JSON list serialization) in `M/kids/data/KidProfile.kt`, per data-model.md
- [x] T004 Create `KidProfileDao` (observe by userId, get, upsert, delete, all ids) in `M/kids/data/KidProfileDao.kt`
- [x] T005 Register the entity, add the `kidProfileDao()` accessor, bump to version 41 with `AutoMigration(40, 41)`, and add the list converters in `M/data/AppDatabase.kt`. Build to generate `app/schemas/.../41.json`
- [x] T006 [P] Create `PasscodeHasher` (PBKDF2WithHmacSHA256, salt, constant-time verify, encoded format) in `M/kids/gate/PasscodeHasher.kt`, with tests in `T/kids/PasscodeHasherTest.kt`
- [x] T007 Create `ParentPasscodeStore` over the key_value DataStore in `M/kids/gate/ParentPasscodeStore.kt`
- [x] T008 Create `KidsModeService` (state, kids lock fail-closed, setKidProfile, updateProfile, unlock) in `M/kids/KidsModeService.kt`, with tests in `T/kids/KidsModeServiceTest.kt`
- [x] T009 Create `LocalKidsMode` in `M/kids/LocalKidsMode.kt` and provide it in `M/MainContent.kt`. In kids mode, override `LocalInterfaceCustomization` with no rating toggles (R12)
- [x] T010 Add the `KidProfileDao` provider to the test Hilt module `T/ui/TestModule.kt` (in-memory DB)

**Checkpoint**: the app builds, all existing tests pass, and kids mode is inert.

---

## Phase 3: User Story 1 — Simplified kids home (P1) 🎯 MVP

**Goal**: A kid profile opens on a kids home built from Wholphin's rows, with every non-kids
feature hidden.

**Independent test**: mark a user as kid (DAO or settings) → relaunch → quickstart scenarios 2–3.

- [x] T011 [P] [US1] Create `KidsHomeRows.build(profile, libraries)` in `M/kids/home/KidsHomeRows.kt` (R6 section mapping, kids view options), with tests in `T/kids/KidsHomeRowsTest.kt`
- [x] T012 [US1] In `HomeSettingsService.loadCurrentSettings`, when kids mode is active, use `updateCurrent(userId, KidsHomeRows.build(...))` and skip the file/server load (`M/services/HomeSettingsService.kt`)
- [x] T013 [US1] In `HomePage`, set `showViewMore = !kids` and make long-press a no-op in kids mode (`M/ui/main/HomePage.kt`)
- [x] T014 [US1] Drawer: keep only Home, My stuff (Favorites) and Search (if `searchAllowed`), and hide ProfileIcon, NowPlaying, libraries, More and Settings (`M/ui/nav/NavDrawer.kt`, `M/services/NavDrawerService.kt`)
- [x] T015 [P] [US1] Return early from `ContextMenuDialog` in kids mode (`M/ui/components/ContextMenu.kt`)
- [x] T016 [P] [US1] In `ExpandablePlayButtons`, hide watched, delete, more and remote trailers in kids mode (`M/ui/components/PlayButtons.kt`)
- [x] T017 [P] [US1] Playback: no subtitle download, speed or debug in kids mode (`M/ui/playback/PlaybackPage.kt`, `PlaybackDialog.kt`). Never use the external player (`M/ui/nav/DestinationContent.kt`)
- [x] T018 [P] [US1] Ignore non-MAIN intents in kids mode (`M/MainActivity.kt` `handleIntent`)
- [x] T019 [P] [US1] Skip Seerr login for kid profiles (`M/services/UserSwitchListener.kt`)

**Checkpoint**: the kids home and its restrictions work. Adult behavior is unchanged.

---

## Phase 4: User Story 2 — Parent gate guards every way out (P1)

**Goal**: A single lock, a light arithmetic gate, a strong passcode for rule changes, and no
escape through Back or the switcher.

**Independent test**: quickstart scenario 4.

- [x] T020 [P] [US2] Create `ArithmeticChallenge` in `M/kids/gate/ArithmeticChallenge.kt`, with tests in `T/kids/ArithmeticChallengeTest.kt`
- [x] T021 [US2] Create `ParentGatePanel` (end panel, scrim, 3×4 keypad, number keys, light/strong modes, retry message) and `KidsParentMenu` in `M/kids/ui/ParentGatePanel.kt`, with a ViewModel in `M/kids/ui/ParentGateViewModel.kt`
- [x] T022 [US2] Add the Grown-ups lock item last in the drawer (never initial focus). Back on Home with the drawer open opens the gate (`M/ui/nav/NavDrawer.kt`)
- [x] T023 [US2] Leave kids mode: strong passcode → `KidsModeService.unlock()` → user list (`M/kids/ui/ParentGateViewModel.kt`)
- [x] T024 [US2] While locked, the switcher lists only kid profiles (`M/ui/setup/SwitchUserViewModel.kt`), with filter tests in `T/kids/KidsSwitcherFilterTest.kt`
- [x] T024a [US2] While locked, guard the setup escape routes: hide add server/user in the user list, and require the gate before reaching the server list, e.g. when the server is unreachable at startup (`M/ui/setup/*`, `M/MainActivity.kt`)

---

## Phase 5: User Story 3 — Parent sets up kid profiles (P1)

**Goal**: The parent creates the passcode, marks kid profiles and configures them.

**Independent test**: quickstart scenario 1.

- [x] T025 [US3] Add `Destination.KidsSettings` (full screen) and route it (`M/ui/nav/Destination.kt`, `M/ui/nav/DestinationContent.kt`)
- [x] T026 [US3] Create `KidsSettingsPage` + `KidsSettingsViewModel` with:
  - passcode create/change
  - device users with a kid toggle
  - per kid: search, autoplay and cap, picks via `SearchForDialog`, section order/visibility
  - recommendations and the readable policy summary

  Files: `M/kids/ui/settings/`
- [x] T027 [US3] Add the "Kids mode" entry in adult Settings (`M/preferences/AppPreference.kt`, `M/ui/preferences/PreferencesContent.kt`) and the strong-gated entry from the parent menu

---

## Phase 6: User Story 4 — Session timer (P2)

**Goal**: Start a timer, see the remaining time and warnings, and land on a persistent "Time's up"
screen.

**Independent test**: quickstart scenario 5.

- [x] T028 [P] [US4] Create `MonotonicClock` in `M/kids/timer/MonotonicClock.kt`
- [x] T029 [US4] Create `KidsTimerService` (state machine, foreground-only counting, 15 s checkpoints, warnings, fail-closed restore) in `M/kids/timer/KidsTimerService.kt`, with tests in `T/kids/KidsTimerServiceTest.kt`
- [x] T030 [US4] Call timer `onForeground`/`onBackground` from `MainActivity` onResume/onPause (`M/MainActivity.kt`)
- [x] T031 [US4] Add `Destination.KidsTimesUp` and `KidsTimesUpPage` (`M/kids/ui/KidsTimesUpPage.kt`, Destination/DestinationContent)
- [x] T032 [US4] Create `KidsTimerEffects` (top bar, warning toast and beep with volume duck, expiry → `replace(KidsTimesUp)`) in `M/kids/ui/KidsTimerEffects.kt`, composed in `M/ui/nav/ApplicationContent.kt`
- [x] T033 [US4] Add timer controls to `KidsParentMenu` (start/update/stop/add)

---

## Phase 7: User Story 5 — Calmer playback (P2)

**Goal**: Autoplay is off by default and capped when enabled, followed by a "Watch again / Home"
end screen.

**Independent test**: quickstart scenario 6.

- [x] T034 [P] [US5] Create `KidsAutoplayPolicy` in `M/kids/playback/KidsAutoplayPolicy.kt`, with tests in `T/kids/KidsAutoplayPolicyTest.kt`
- [x] T035 [US5] In `PlaybackViewModel`, add an autoplay counter, `autoPlayNextUp()`, apply the kids policy in `shouldAutoPlayNextUp()`, and expose a kids end-screen state on `STATE_ENDED` (`M/ui/playback/PlaybackViewModel.kt`)
- [x] T036 [US5] Create `KidsEndScreen` and wire it in `PlaybackPage` (countdown calls `autoPlayNextUp()`) (`M/kids/ui/KidsEndScreen.kt`, `M/ui/playback/PlaybackPage.kt`)

---

## Phase 8: User Story 6 — Kid-friendly presentation (P3)

- [x] T037 [US6] Kids row view options: wide thumbs, larger height, titles shown, so that at most 5 cards per row fit on 1080p (in `KidsHomeRows`). Verify drawer icons and initial focus on content

---

## Phase 9: Polish

- [x] T038 [P] Add a Room migration test for 40→41 in `app/src/androidTest/.../TestDbMigrations.kt`
- [x] T039 Run ktlint 1.8.0 formatting (pre-commit config) on the changed files
- [x] T040 Run the full unit suite and `assembleDefaultDebug` in the devshell, and fix any regressions
- [x] T041 Update `specs/001-kids-mode/quickstart.md` if behavior diverged

## Dependencies & execution order

- Setup → Foundational → US1 → US2 → US3, then US4 and US5 (independent of each other) → US6 →
  Polish.
- US2's drawer lock (T022) touches the same file as T014, so do them sequentially.
- US4's parent-menu controls (T033) need T021.
- Tasks marked [P] in a phase touch different files and can run in parallel after that phase's
  first non-[P] task.

## Implementation strategy

The MVP is Phases 1–5 (US1–US3: a safe kids home with a gate and setup). Then add the timer
(US4) and autoplay (US5). Keep the full suite green after each phase.

## Implementation notes (2026-10-16)

- **T010:** not needed. `KidsModule` provides the DAO from `AppDatabase`, which the test database module already supplies.
- **T014:** `NavDrawerService` is untouched. The drawer hides library and "More" items locally in kids mode.
- **T016:** the filtering lives in the shared `DeleteButton` and `TrailerButton`, so the series, collection and music pages are covered too. Mark watched is also hidden in `SeriesDetails`.
- **T027:** no `PreferencesContent` change was needed, because `AppDestinationPreference` is handled generically.
- **Not done:** the Robolectric drawer smoke test (R13).
- **Not run:** the androidTest migration test T038, which needs a device or emulator. Room validated the schema at build time and `41.json` was generated.
- **Known limitation:** on a cold start, `LocalKidsMode` can be `Inactive` for a moment before Room answers. The kids home itself reads the profile directly and is unaffected.
- **Verification:** `testDefaultDebugUnitTest` passes (458 tests, 0 failures). `assembleDefaultDebug` succeeds. ktlint 1.8.0 reports no violations on the changed files.

## Emulator review (2026-10-17)

- **Kids home load:** the "New" row was a recursive query across the whole server that took about 30 s on a large library. It now matches the regular default home: one `RecentlyAdded` row per kids library (`/Items/Latest`). Load time is about 2.5 s.
- **Wholphin UI reuse:**
  - Kids rows use `viewOptionsForCollectionType`, the regular home defaults, instead of enlarged cards.
  - The arithmetic light gate, the on-screen keypad and the custom passcode panels are gone. The parent gate and the kids settings passcode use Wholphin's `PinEntryCreate` in a `BasicDialog`, so the passcode accepts digits and D-pad directions, as in the profile PIN.
  - The parent menu and the timer length are `DialogPopup` lists.
  - This supersedes the light-gate parts of R9, T020 and T021 in research.md, plan.md and data-model.md.
- **Direct play:** in kids mode, opening a movie, episode or video plays it from its resume point instead of showing the details page. A series plays its next-up episode, falling back to the first. Collections, playlists and folders still open for browsing. `KidsDirectPlay` is hooked once in `DestinationContent`, so every entry point (home, Favorites, search, suggestions) behaves the same.
- **More like this:** the player overlay has a new `MORE` state below Chapters and Queue. It shows Jellyfin's similar items (based on the series for episodes) in the same row component as the queue (`ItemRowOverlay`). Picking one plays it directly. In adult mode the list is empty, so the row does not appear.
- **End of playback:**
  - When there is a next item, kids mode always shows Wholphin's `NextUpEpisode` panel. Without autoplay, or once the autoplay cap is reached, the panel has no countdown and the child chooses.
  - When nothing comes next, a panel in the same style offers Watch again and Home as `ExpandablePlayButton`s, plus the More like this cards.
- **Time's up and Ask a grown-up:** both use one screen, `KidsGrownUpsMessage`, with Wholphin's `TimeDisplay` clock and an `ExpandablePlayButton` lock that opens the parent gate.
- **Parent lock types:** grown-ups choose one in Kids settings → Parent lock (`ParentLockType`):
  - **Sum or multiplication** (`CHALLENGE`): nothing to remember. A new question replaces a wrong answer, so guessing does not help.
  - **Number code** (`NUMERIC`): digits only, at least 4.
  - **Remote buttons code** (`REMOTE`): arrows and digits, plus the colour, media, channel and info buttons the connected remote has (`InputDevice.hasKeys`). Codes set before lock types existed are treated as this type.
  - `PinEntryCreate` and `SetPinFlow` gained optional `symbolFor` and `hint` parameters (and `isValid` for `SetPinFlow`); their defaults keep the profile PIN behaviour.
- **Kids settings follow Wholphin's preference screens:** a right-hand panel like `UserProfilePreferencesPage`, group titles, `AppSwitchPreference`/`AppSliderPreference` on `KidProfile` through `ComposablePreference`, `ChoicePreference` for the lock and timer bar, `NavDrawerPreferenceListItem` to show and reorder home sections, and `SetPinFlow` to create codes. Initial focus is on the first item.
- **Timer bar:** Kids settings → Timer bar: at the top, at the bottom or hidden (device-wide, `KidsTimerBarSetting` in `KeyValueService`).
- **Review fixes:**
  - Opening Kids settings from the parent gate uses an in-memory grant instead of a `verified` flag in the destination, so settings restored after the app was killed ask for the lock again.
  - The nav drawer shows Wholphin's Favorites item (`KidsNavDrawerItems`) instead of a custom "My stuff" item, so the selected item survives going back.
  - Collections hide More, playlists hide More and the reorder buttons. The kids checks in movie, episode and series details were removed, since kids mode never opens those pages.
- **Upstream-friendly restrictions:** shared screens no longer check for kids mode to hide features. `InterfaceCustomization` has a generic `RestrictedFeature` set (item menus, delete, playlist editing, view more, advanced playback controls, external player) and asks `allows(...)`. Kids mode turns them all on in one place (`KidsInterfaceCustomization`). The remaining kids hooks in shared code are wiring: providers, the direct-play hook, the drawer items, the player end screen and suggestions, and the services.
- **More review fixes:**
  - Direct play of a series uses `PlaylistCreator.nextUpEpisode`, the same lookup as the series Play button; the next episodes come from Wholphin's playlist.
  - More like this opens videos straight in the player; series still go through direct play.
  - The suggestions view model is only created in kids mode, picked item names are fetched in one request, and the end screen rows restore focus.
  - The kids timer observes the activity lifecycle (`DefaultLifecycleObserver`) instead of being called from `MainActivity`.
  - `PinEntryCreate` keeps the original key handling for arrows and digits; only extra keys consume their key down.
  - The lock guard shows `LoadingPage` while it checks, and the kids strings reuse Wholphin's "Home" and "Favorites".
  - The parent lock and timer stay on raw DataStore keys: moving them to `KeyValueService` would change the stored format and invalidate codes already set.
- **Server plugin:** studied in [002-kids-server-plugin](../002-kids-server-plugin/spec.md), not started.
