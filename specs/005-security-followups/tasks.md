---

description: "Tasks for feature 005: security follow-ups"
---

# Tasks: Security follow-ups

**Input**: Design documents from `specs/005-security-followups/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md),
[contracts/kid-surfaces.md](contracts/kid-surfaces.md), [quickstart.md](quickstart.md)

**Tests**: included. Constitution VIII requires unit tests for policy decisions and gate rules; each rule here is
extracted into a small function so it can be tested on the JVM.

**Organization**: by user story (US1–US6 from spec.md). P5 (FR-019) is in the Polish phase. P2 is cut (research
R12) and has no task.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US6 from spec.md

## Path conventions

- `main/` = `app/src/main/java/dev/aldoborrero/peke/`
- `test/` = `app/src/test/java/dev/aldoborrero/peke/`
- `res/` = `app/src/main/res/`

Every task description below uses these prefixes literally. Strings go to `res/values/strings.xml` (EN) and
`res/values-es/strings.xml` (ES) in the same task; two stories editing those files at once must merge by hand, so
string tasks are never marked [P] against each other.

Rules for whoever implements: build and test only with `nix develop -c ./gradlew …`, stop the daemons with
`nix develop -c ./gradlew --stop` when idle, no git writes, no credentials, no emulator unless the coordinator says
so. Format with `nix fmt` before handing over.

---

## Phase 1: Setup

**Purpose**: a known-green baseline.

- [X] T001 Run `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest` on the untouched branch, record failures (if any) in the hand-over notes as pre-existing, then `nix develop -c ./gradlew --stop`

---

## Phase 2: Foundational

**Purpose**: none needed. The stories touch disjoint code; the only shared files are the string resources (see
Path conventions). Every story can start after T001.

---

## Phase 3: User Story 1 - Videos open the way the design says, and Back means Back (Priority: P1) 🎯 MVP

**Goal**: the Play key goes where OK goes (direct play only from "Continue watching"), the home decides from its
current rows, and direct play swaps only its own loading page (FR-001 to FR-004; research R4, R5).

**Independent Test**: quickstart scenarios 1 and 2.

### Tests for User Story 1

- [X] T002 [P] [US1] Create `test/services/NavigationManagerTest.kt`: `replaceTop(expected, with)` replaces and returns true when `expected` is the top entry; returns false and leaves the stack unchanged when another entry is on top (for example `Destination.KidsTimesUp` pushed after the loading page) or when `expected` is only deeper in the stack; with `with = null` it only pops
- [X] T003 [P] [US1] Create `test/kids/KidsPlayKeyTest.kt`: in kids mode the Play key destination equals `HomeRowLoadingState?.clickDestination(item, clickToPlay, kidsMode = true)` for a "Continue watching", a "Next up", a combined row and a Picks row; for grids `kidsPlayKeyDestination(item)` is `item.destination()` (details page) for a movie, an episode and a series
- [X] T004 [P] [US1] Extend `test/kids/KidsDirectPlayTest.kt`: `KidsDirectPlayViewModel.play()` calls `replaceTop(destination, Destination.Playback(item))` when the item resolves; when `replaceTop` returns false no toast is shown and nothing else is navigated; when the item is null, `replaceTop(destination, null)` is called and the "nothing to play" toast shows only if it returned true

### Implementation for User Story 1

- [X] T005 [US1] Add `fun replaceTop(expected: Destination, with: Destination?): Boolean` to `main/services/NavigationManager.kt`, synchronized like `goBack()`: only when `backStack.lastOrNull() == expected` remove it and add `with` (if not null), log, return true; otherwise change nothing and return false
- [X] T006 [US1] In `main/kids/playback/KidsDirectPlay.kt` `play()`, replace `goBack()` + `navigateTo(Playback)` with `navigationManager.replaceTop(destination, item?.let { Destination.Playback(it) })`, and show `R.string.kids_nothing_to_play` only when the item is null and `replaceTop` returned true (depends on T005)
- [X] T007 [P] [US1] Create `main/kids/KidsPlayKey.kt` with `fun kidsPlayKeyDestination(item: BaseItem): Destination = item.destination()` and a KDoc citing DESIGN "Opening videos" and spec 005 FR-001
- [X] T008 [US1] In `main/ui/main/HomePage.kt`: add `val currentRows by rememberUpdatedState(homeRows)` and use it in `onClickItem` and `onLongClickItem` instead of the captured `homeRows`; make `onClickPlay` navigate, when `kidsMode` is true, to `currentRows.getOrNull(position.row).clickDestination(item, currentHomePrefs.clickToPlay, true)` and otherwise to `Destination.Playback(item)` as today (FR-002)
- [X] T009 [US1] Route the grid Play key in kids mode through `kidsPlayKeyDestination(item)` (read `LocalKidsMode.current.isActive` in the composable that builds the lambda) in `main/ui/components/ItemGrid.kt` (`:231`), `main/ui/components/RecommendedContent.kt` (`:428`), `main/ui/components/CollectionFolderView.kt` (`gridActions.onClickPlayRemoteButton`, `:1002` and `:1023`), `main/ui/search/SearchPage.kt` (`:506`) and `main/ui/detail/collection/CollectionDetails.kt` (`:140`); music-only pages stay as they are (depends on T007)
- [X] T010 [US1] Run `nix develop -c ./gradlew --console=plain testDebugUnitTest --tests '*NavigationManagerTest' --tests '*KidsPlayKeyTest' --tests '*KidsDirectPlayTest' --tests '*KidsHomeRowsTest'`, then `nix develop -c ./gradlew --stop`

**Checkpoint**: C13 and C14 closed; the Play key and Back behave as the contract's "Opening videos" table.

---

## Phase 4: User Story 2 - A grown-up decides about the microphone, never the kid (Priority: P1)

**Goal**: voice search is a per-TV setting, off by default, turned on by a grown-up behind the gate after an
explanation; the system prompt goes to the grown-up; kids never see it (FR-005 to FR-011; research R8).

**Independent Test**: quickstart scenario 3.

### Tests for User Story 2

- [X] T011 [P] [US2] Create `test/ui/components/VoiceSearchShownTest.kt` for `voiceSearchShown(enabled, granted, available)`: true only when all three are true; false for each single false input
- [X] T012 [P] [US2] Extend `test/kids/setup/SetupFlowViewModelTest.kt`: on a first setup, `finish()` shows `SetupScreen.Voice(server)` when `kids_voice_search` is false and a signed-in kid's cached profile has `searchAllowed = true`; it goes straight to the launcher when no kid has search allowed, when voice search is already on, when re-signing a kid in (`againKid`) and when adding kids; "Not now" on the Voice step launches without changing the pref
- [X] T013 [P] [US2] Add a test to `test/test/TestVoiceInputManager.kt` (or a new `test/ui/components/VoiceInputIntentTest.kt`) that the recognition intent carries `RecognizerIntent.EXTRA_PREFER_OFFLINE = true`

### Implementation for User Story 2

- [X] T014 [US2] Add `bool kids_voice_search = 3;` to `message SearchPreferences` in `app/src/main/proto/PekeDataStore.proto` ("Default: false (proto3 default). `AppPreferencesSerializer` doesn't set it"); do not set it in `main/preferences/AppPreferencesSerializer.kt`
- [X] T015 [US2] In `main/ui/components/VoiceSearchButton.kt`: delete the `RECORD_AUDIO` permission launcher and the request branch in `onClick`; add `fun voiceSearchShown(enabled: Boolean, granted: Boolean, available: Boolean): Boolean`; the button composable re-reads `voiceInputManager.hasPermission` on `Lifecycle.Event.ON_RESUME` and renders nothing when `voiceSearchShown(...)` is false; keep `onPermissionGranted/Denied` only if still used elsewhere, otherwise remove them from `main/ui/components/VoiceInputManager.kt`
- [X] T016 [US2] In `main/ui/components/VoiceInputManager.kt`, add `putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)` to `recognitionIntent` (FR-010)
- [X] T017 [US2] In `main/ui/search/SearchPage.kt` and `main/ui/search/SearchForDialog.kt`, pass `enabled = prefs.searchPreferences.kidsVoiceSearch && prefs.searchPreferences.showVoiceSearchButton` to the voice button's visibility (SearchPage's `AnimatedVisibility` uses `voiceSearchShown(...)`), so the mic shows only per the data-model rule "`kids_voice_search && show_voice_search_button && RECORD_AUDIO granted && speech service available`" (depends on T014, T015)
- [X] T018 [US2] Create `main/ui/preferences/VoiceSearchConsent.kt`: a composable that shows the explanation dialog (contract text), then `rememberLauncherForActivityResult(RequestPermission())` for `RECORD_AUDIO`; on grant it calls `onEnabled()`; on denial it checks `shouldShowRequestPermissionRationale` and, when false, shows the "Settings → Apps → Peke → Permissions → Microphone" message; it never opens system settings; D-pad buttons only, default focus on the dialog's first button (not a gate control)
- [X] T019 [US2] Add `AppPreference.KidsVoiceSearch` (an `AppClickablePreference<AppPreferences>` with an on/off summary from `kidsVoiceSearch`) to `main/preferences/AppPreference.kt` and list it in `pekePreferences` (a "Kids" or the interface group); handle its click in `main/ui/preferences/PreferencesContent.kt`: when off, open `VoiceSearchConsent` and set `kids_voice_search = true` only from `onEnabled`; when on, set it to false directly (depends on T014, T018)
- [X] T020 [US2] Add `SetupScreen.Voice(server)` and its step composable to `main/kids/setup/SetupFlow.kt`: `finish()` shows it per T012's rule (read the signed-in kids' cached profiles through the existing kids profile repository/`KidsModeService`), the step reuses `VoiceSearchConsent`, and both its outcomes and "Not now" continue to `launcher.launch { … }` as `finish()` does today; Back on the step acts like "Not now" (depends on T018)
- [X] T021 [US2] In `main/ui/detail/music/NowPlayingPage.kt`, delete the visualiser's `RECORD_AUDIO` request, the rationale dialog state and `RecordAudioRationaleDialog`; `onEnableVisualizer` starts the visualiser only when `state.visualizerPermissions` is already true (FR-011)
- [X] T022 [US2] Add the strings (EN and ES) for the explanation, the denied-for-good message, the This TV row title and summaries, and the setup Voice step (title, "Turn on voice search", "Not now") to `res/values/strings.xml` and `res/values-es/strings.xml`, using the contract's texts
- [X] T023 [US2] Run `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest --tests '*VoiceSearchShownTest' --tests '*SetupFlowViewModelTest' --tests '*VoiceInput*'`, then `nix develop -c ./gradlew --stop`; check with `rg -n "RECORD_AUDIO" app/src/main/java` that only `VoiceInputManager.hasPermission`, `VoiceSearchConsent` and the visualiser's permission check remain

**Checkpoint**: C11 closed; no kid screen can raise the microphone prompt.

---

## Phase 5: User Story 3 - The kid's player always has its controls (Priority: P2)

**Goal**: the grown-up's "Playback debug info" has no effect for kids (FR-012; research R6).

**Independent Test**: quickstart scenario 4.

- [X] T024 [US3] In `main/ui/playback/PlaybackPage.kt`, seed `showDebugInfo` with `prefs.showDebugInfo && advancedPlayback` (`:183`) and pass `showDebugInfo = showDebugInfo && advancedPlayback` to the overlay (`:499`); leave `:299` and `:772` as they are
- [X] T025 [US3] Run `nix develop -c ./gradlew --console=plain assembleDebug`, then `nix develop -c ./gradlew --stop` (the change is a guard on composable parameters, checked on a device in quickstart scenario 4)

**Checkpoint**: C15 closed.

---

## Phase 6: User Story 4 - Focus never starts on a grown-up control (Priority: P2)

**Goal**: stop screens focus their title, and Grown-ups is one D-pad move away (FR-013; research R7).

**Independent Test**: quickstart scenario 5.

- [X] T026 [US4] In `main/kids/ui/KidsTimesUpPage.kt` `KidsGrownUpsMessage`: move the `FocusRequester` from the Grown-ups button to a focusable, non-clickable container around the title (`Modifier.focusRequester(focus).focusable()` with the shared focus border from DESIGN's Focus row), give it `focusProperties { down = grownUpsFocus }`, and keep OK on it a no-op; update the KDoc ("Focus starts on the message, never on Grown-ups (constitution V)")
- [X] T027 [US4] Check every other kid-reachable screen for a default focus on a gate control with `rg -n "focusRequester|requestFocus|tryRequestFocus" main/kids/ui main/kids/setup/KidsPicker.kt main/ui/nav/NavDrawer.kt` and record in the hand-over notes that only `KidsGrownUpsMessage` did; fix any other hit the same way
- [X] T028 [US4] Run `nix develop -c ./gradlew --console=plain assembleDebug`, then `nix develop -c ./gradlew --stop`

**Checkpoint**: C16 closed.

---

## Phase 7: User Story 5 - Other devices can't steer a kid's TV (Priority: P2)

**Goal**: the media session accepts only Peke and trusted controllers with transport commands; only the Kids
plugin's session messages show (FR-017, FR-018; research R9, R10).

**Independent Test**: quickstart scenarios 6 and 7.

### Tests for User Story 5

- [X] T029 [P] [US5] Create `test/services/KidsSessionCallbackTest.kt` (Robolectric, MockK for `MediaSession` and `MediaSession.ControllerInfo`): `onConnect` accepts a controller whose `packageName` is the app's and one with `isTrusted = true`, and rejects an untrusted third-party package; an accepted result's available player commands equal `KidsSessionCallback.PLAYER_COMMANDS` and contain none of `COMMAND_SET_MEDIA_ITEM`, `COMMAND_CHANGE_MEDIA_ITEMS`, `COMMAND_SET_SPEED_AND_PITCH`, `COMMAND_SET_SHUFFLE_MODE`, `COMMAND_SET_REPEAT_MODE`, `COMMAND_SET_TRACK_SELECTION_PARAMETERS`, `COMMAND_SET_VOLUME`, `COMMAND_SET_PLAYLIST_METADATA`; `onAddMediaItems` and `onSetMediaItems` return failed futures
- [X] T030 [P] [US5] Create `test/kids/KidsServerMessagesTest.kt` for `KidsServerMessages.shouldShow(header, hidden)`: a non-Kids header (and a null header) is never shown; a Kids header is shown unless `hidden`

### Implementation for User Story 5

- [X] T031 [US5] Create `main/services/KidsSessionCallback.kt`: a `MediaSession.Callback` with `PLAYER_COMMANDS` exactly as listed in data-model.md "Media session commands", `onConnect` per research R9 (accept own package or `isTrusted`, else `ConnectionResult.reject()`; accepted with `AcceptedResultBuilder(session).setAvailablePlayerCommands(PLAYER_COMMANDS)` and the default session commands), and `onAddMediaItems`/`onSetMediaItems` failing with `SessionError.ERROR_PERMISSION_DENIED`; KDoc cites P1 and the API < 28 risk
- [X] T032 [US5] Use it in `main/services/PlayerFactory.kt` `createMediaSession` (`.setCallback(KidsSessionCallback(context.packageName))`) and in `main/services/MusicService.kt` (`:125`) (depends on T031)
- [X] T033 [US5] Add `fun shouldShow(header: String?, hidden: Boolean): Boolean = isKids(header) && !hidden` to `main/kids/KidsServerMessages.kt`, and in `main/services/ServerEventListener.kt` show the toast only when `KidsServerMessages.shouldShow(header, hideForKids(header))`; update `hideForKids`' KDoc to say every other message is dropped (FR-018); keep the `DISPLAY_MESSAGE`/`SEND_STRING` capabilities (the plugin uses them)
- [X] T034 [US5] Run `nix develop -c ./gradlew --console=plain testDebugUnitTest --tests '*KidsSessionCallbackTest' --tests '*KidsServerMessagesTest' --tests '*KidsEnforcementTest'`, then `nix develop -c ./gradlew --stop`

**Checkpoint**: P1 and P4 closed in code; the API < 28 media-key check stays open until quickstart scenario 6 runs on such a device.

---

## Phase 8: User Story 6 - The TV's link to the server can't easily be forged (Priority: P2)

**Goal**: HTTPS first for a bare host, a warning before any `http://` server, "Not encrypted" in This TV, trust
anchors unchanged, and no kid removed on an empty list without a per-kid answer (FR-014 to FR-016; research R1–R3).

**Independent Test**: quickstart scenarios 8 and 9.

### Tests for User Story 6

- [X] T035 [P] [US6] Create `test/kids/setup/SetupServerLookupTest.kt` for `SetupServerLookup.pick(results)`: an `https://` GOOD result wins over an `http://` GREAT one; with only `http://` results the best score wins and `insecure = true`; `BAD` results and results whose system info isn't a set-up server are ignored; an empty list gives `NotFound`
- [X] T036 [P] [US6] Extend `test/kids/setup/SetupFlowViewModelTest.kt`: `addServer` with an insecure lookup shows `SetupScreen.Server(httpWarning = server)` and makes no plugin call; confirming runs the plugin check and moves to `SetupScreen.Admin(server)`; Back clears `httpWarning` and stays on the server step; a secure lookup never shows the warning
- [X] T037 [P] [US6] Extend `test/kids/KidProfileRepositoryTest.kt`: `refreshServer` with an empty `/Kids/Profiles` list deletes no profile and removes no row when each kid's own `profile()` answers `Ok` or fails; with a partial list, only the kid whose `profile()` answers `NotAKid` while `confirmAvailable` is true is signed out and removed; a row without a token is kept

### Implementation for User Story 6

- [X] T038 [US6] In `main/kids/setup/SetupFlow.kt` `SetupServerLookup`: add `internal fun pick(results: List<RecommendedServerInfo>): ServerLookup` per research R1 (https before http, best score inside a scheme, non-`BAD`, set-up server only), use it in `find()`, and add `insecure: Boolean` to `ServerLookup.Found`
- [X] T039 [US6] In `main/kids/setup/SetupFlow.kt`: add `httpWarning: JellyfinServer? = null` to `SetupScreen.Server`; `addServer()` stops after `find()` when `insecure` and sets it; add `confirmHttp()` (continues the plugin check with that server) and handle Back on the warning in `back()` (clears it); a discovered server is added through `addServer(address)` so it gets the same lookup; render the warning dialog in `ServerStep` with the contract's texts, buttons "Use it anyway" and "Back", default focus on "Back" (depends on T038)
- [X] T040 [US6] Add `AppPreference.ServerAddress` (read-only `AppClickablePreference<AppPreferences>` with no action) to `main/preferences/AppPreference.kt` in `pekePreferences`, rendered in `main/ui/preferences/PreferencesContent.kt` with the current server's URL as summary and "Not encrypted" appended when it starts with `http://`
- [X] T041 [US6] In `main/kids/data/KidProfileRepository.kt` `refreshServer()`: for a row missing from the list, call `refresh(server, user)` instead of deleting, marking not-a-kid and `forgetUser()` directly; update the KDoc ("removed only on the plugin's per-kid NotAKid", spec 005 FR-016)
- [X] T042 [P] [US6] Add an XML comment to `app/src/main/res/xml/network_security_config.xml` saying cleartext and user CAs are kept on purpose (spec 005, owner decision 2026-10-24: warn on HTTP, keep user CAs); no attribute change
- [X] T043 [US6] Add the strings (EN and ES) for the HTTP warning title, body and buttons, the This TV server row title and "Not encrypted" to `res/values/strings.xml` and `res/values-es/strings.xml`, using the contract's texts
- [X] T044 [US6] Run `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest --tests '*SetupServerLookupTest' --tests '*SetupFlowViewModelTest' --tests '*KidProfileRepositoryTest'`, then `nix develop -c ./gradlew --stop`

**Checkpoint**: C7 closed within the owner's rule.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T045 [P] Create `test/util/ExceptionHandlerTest.kt` (Robolectric): calling `ExceptionHandler(autoToast = true).handleException(...)` on the main looper returns without blocking, and after `shadowOf(getMainLooper()).idle()` `ShadowToast` shows the friendly text, never the exception message
- [X] T046 In `main/util/ExceptionHandler.kt`, replace `runBlocking { showToast(...) }` with `Handler(Looper.getMainLooper()).post { Toast.makeText(PekeApplication.instance, R.string.peke_something_went_wrong, Toast.LENGTH_LONG).show() }`; add `peke_something_went_wrong` ("Oops, something went wrong", ES alongside) to `res/values/strings.xml` and `res/values-es/strings.xml` (FR-019, research R11)
- [X] T047 Run `nix fmt`, then `nix develop -c pre-commit run --files` on every changed file, and `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`; then `nix develop -c ./gradlew --stop`
- [X] T048 Check that every new string key exists in both `res/values/strings.xml` and `res/values-es/strings.xml` (`rg -o 'name="[^"]+"'` diff of the new keys)
- [X] T049 Record the APK size (release per ABI if `assembleRelease` works without the owner's credentials, else a clean `assembleDebug` per ABI compared with the base of this branch, built in a separate clone) in `specs/005-security-followups/plan.md` under the APK size paragraph
- [X] T050 Write the hand-over notes for the coordinator: which quickstart scenarios still need a device (1–8), the API < 28 media-key risk (R9), and the pre-existing test failures from T001, if any

---

## Dependencies & Execution Order

- **Setup (T001)** first.
- **User stories** are independent of each other and can run in any order or in parallel after T001. Only the string
  tasks (T022, T043, T046) and the two `SetupFlow.kt` stories (US2's T020 and US6's T038/T039) touch the same files:
  run those in sequence, or merge by hand.
- **Inside a story**: tests first (they must fail before the change), then the implementation tasks in ID order,
  then the story's run task.
- **Polish** after the stories it checks; T045/T046 (P5) can go any time after T001.

## Parallel examples

- **US1**: T002, T003, T004 together; then T005 and T007 together; then T006, T008, T009.
- **US2**: T011, T012, T013 together; T014 then T015/T016/T018 together; then T017, T019, T020, T021.
- **US5**: T029 and T030 together; T031 and T033 together; then T032.
- **US6**: T035, T036, T037 together; T038, T040, T041 and T042 together; then T039.
- **Across stories**: US3 (T024) and US4 (T026) are one-file changes that can run beside any other story.

## Implementation Strategy

- **MVP**: US1 (the daily "Back means Back" and Play key fixes), then US2 (the consent fix). Both are P1.
- **Then** US3 and US4 (one-file fixes), US5, US6, and the Polish phase.
- Each story ends with its own green run, so the coordinator can commit story by story.
