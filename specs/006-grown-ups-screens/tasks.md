---

description: "Tasks for 006 grown-ups screens"
---

# Tasks: Grown-ups screens

**Input**: Design documents from `specs/006-grown-ups-screens/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: included, as constitution VIII asks for unit tests of new logic (timers and limits, gate, preferences).

Paths are relative to `app/src/main/java/dev/aldoborrero/peke/` unless they start with `app/` or `specs/`; tests
are under `app/src/test/java/dev/aldoborrero/peke/`. Strings go in `app/src/main/res/values/strings.xml` and
`app/src/main/res/values-es/strings.xml`, in English and Spanish (DESIGN.md Words).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies)
- **[Story]**: the user story from spec.md

## Phase 1: Setup

- [X] T001 Check whether the plugin contract exists at `/home/aldo/Dev/aldoborrero/jellyfin-kids/.claude/worktrees/kids-bonus/specs/013-bonus-undo-break/contracts/`; if it does, update `specs/006-grown-ups-screens/contracts/kids-api-bonus-undo.md` to match it before T005

## Phase 2: Foundational

- [X] T002 [P] Add `initialFocusIndex: Int? = null` to `DialogPopup` and `DialogPopupContent` in `ui/components/Dialogs.kt` (scroll to it and request focus on open), and pass the selected index from `ui/preferences/ChoicePreference.kt` (R12, FR-026)
- [X] T003 [P] Add `holdStop: MutableStateFlow<Boolean>` to `KidsTimeLimits` in `kids/timer/KidsTimeLimits.kt`, and in `kids/ui/KidsTimerEffects.kt` leave time's up only while it is false (R5)
- [X] T004 [P] Add `reachable: StateFlow<Boolean?>` to `KidsAllowanceService` in `kids/timer/KidsAllowanceService.kt`: true after any HTTP answer to a status request, false after `KidsResult.Unreachable`, null when the kid changes (R4)
- [X] T005 Add `KidsFeatures.BONUS_UNDO = "bonusUndo"` and `BREAK_END = "breakEnd"`, `BonusStatus` (`KidsStatus` plus optional integer `BonusId`) in `kids/server/KidsDtos.kt`; make `KidsApi.bonus` return the status with the bonus id, add `undoBonus(baseUrl, caller, grant, userId, bonusId)` (`DELETE …/WatchTime/Bonus/{bonusId}`) and `endBreak(baseUrl, caller, grant, userId)` (`POST …/WatchTime/Break/End`) in `kids/server/KidsApi.kt`, per `contracts/kids-api-bonus-undo.md` (R6)
- [X] T006 [P] Tests for T005 in `kids/KidsApiTest.kt`: the bonus id is read when present and absent; undo and break end send the grant and parse the status; 403 is `Forbidden`

## Phase 3: User Story 1 - No slip on the TV deletes a kid (P1)

**Goal**: no kid on/off switch and no mark/unmark call on the TV.
**Independent Test**: no grown-up page offers it; `mark`, `unmark` and `deleteProfile` no longer exist.

- [X] T007 [US1] Delete `KidProfileRepository.mark` and `unmark` in `kids/data/KidProfileRepository.kt` and `KidsApi.deleteProfile` in `kids/server/KidsApi.kt`; delete their tests in `kids/KidProfileRepositoryTest.kt` and `kids/KidsApiTest.kt` (FR-001)
- [X] T008 [US1] Remove the "Kid profiles" switches and `setKid` from `kids/ui/settings/KidsSettingsPage.kt` and `KidsSettingsViewModel.kt` (deleted with the page in US3) and the strings `kids_profiles`, `kids_profile_on`, `kids_profile_off`

## Phase 4: User Story 2 - Add time says who, how much and until when (P1)

**Goal**: the stepper, the result line, the confirmation with Undo, errors and reason lines.
**Independent Test**: `AddTimeTest`, the gate tests, and quickstart step 2–3.

- [X] T009 [US2] Create `kids/timer/AddTime.kt`: `ADD_TIME_STEPS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)`, `AddTimeAmount` (stored per TV in `KeyValueService` under `kids_add_time_minutes`, "15 when missing or not a step"), `AddTimePreview.of(status, limits, minutes, now, schedules)` (`until`, `hadLeft`, `bedtimeAt`, `breakAt` per data-model.md) and `AddTimeAvailability.of(...)` (`Offline`, `OnBreak(endsAt, canEnd)`, `Bedtime`, `Unlimited`, `Available`, first match per data-model.md)
- [X] T010 [P] [US2] Tests in `kids/AddTimeTest.kt`: steps and stored amount (missing, not a step), until with and without time left, time's up, bedtime warning only past today's window end, break before the end, availability order and offline
- [X] T011 [US2] In `kids/timer/KidsAllowanceService.kt`: `addBonus` returns `BonusOutcome(result, bonusId)`; add `undoBonus(bonusId)` and `endBreak()` with the grant (403 drops it, `NEEDS_GRANT`), each answer applied to the snapshot like the bonus
- [X] T012 [US2] In `kids/ui/KidsParentGateViewModel.kt`: replace `Minutes` with `GateStage.AddTime(index, sending, error)` and `GateStage.Added(minutes, until, bonusId, undoing, undoFailed)`; `openAddTime()` reads the stored amount; `stepAddTime(delta)` without wrap; `sendAddTime()` ignores repeats while sending, stores the amount on success, keeps the grant until the confirmation closes, sets `holdStop` while the stepper, sending or the confirmation show; `NEEDS_GRANT` goes to `Passcode` with the amount pending and sends it after the code; `undoAddTime()`; `closeAdded()` ends the grant; `backToMenu()`; `keepWatching()` ends the break (R2–R6)
- [X] T013 [US2] Create `kids/ui/AddTimeStepper.kt`: the stepper row (‹ amount ›, dimmed ends, Left/Right on a focused row), kid name, result lines (amber bedtime), busy state, inline error with Retry, and the confirmation with Done (focused) and Undo with a 10 s countdown that closes it
- [X] T014 [US2] In `kids/ui/KidsParentGate.kt`: the menu in fixed order with the header line, Add time or the reason line (and "Keep watching now" with `breakEnd`), the `AddTime` and `Added` stages using T013, Back on the stepper back to the menu, and clear `holdStop` on dispose
- [X] T015 [P] [US2] Tests in `kids/KidsParentGateViewModelTest.kt`: stepper opens on the stored amount, no wrap, one send per OK, success stores the amount and keeps the grant until closed, undo, NEEDS_GRANT then the code sends the pending amount, Back returns to the menu, `holdStop` set and cleared
- [X] T016 [US2] Strings for the stepper, result lines, reasons, confirmation and errors (contracts/grown-up-screens.md), English and Spanish

## Phase 5: User Story 3 - Leo's rules on one screen (P1)

**Goal**: the rules page replaces Kid's settings.
**Independent Test**: `KidRulesViewModelTest`; quickstart step 4.

- [X] T017 [US3] Create `kids/ui/rules/KidRulesViewModel.kt`: current kid's profile, status, policy, channel names (`KidsLiveTvService.channelsFor`), pick names, voice search, dashboard address `{server}/web/#/configurationpage?name=Kids`; `setDailyTime(minutes)` saves one at a time, `NEEDS_GRANT` keeps it pending, `retryPending()` after the gate, `saved` flag for 3 s; options `[5, 10, 15, 30, 45, 60, 90, 120, 180, 240]` plus the current value, Unlimited last (R7)
- [X] T018 [US3] Create `kids/ui/rules/KidRulesPage.kt`: avatar and "{Kid}'s rules", Daily time (`ChoicePreference`, the only focus stop, "Saved"), Today line, dashboard box, and the read-only lines in a second column; `KidsParentGate(onPassed = retryPending)` when a save needs the code
- [X] T019 [US3] Rename `Destination.KidsSettings` to `Destination.KidRules` in `ui/nav/Destination.kt` and its uses (`ui/nav/DestinationContent.kt`, `kids/ui/GrownUpGuard.kt`, `kids/ui/KidsTimerEffects.kt`, `kids/ui/KidsParentGateViewModel.kt`, `preferences/AppPreference.kt`); delete `kids/ui/settings/` and `kids/KidsSettingsEditsTest.kt`, and strings only they used
- [X] T020 [P] [US3] Tests in `kids/KidRulesViewModelTest.kt`: options and order, save, pending after NEEDS_GRANT and retry, saved flag, dashboard address

## Phase 6: User Story 4 - A missing channel can be explained on the TV (P2)

- [X] T021 [US4] Live TV line on the rules page: names, "Live TV is off in Jellyfin" from `enableLiveTvAccess`, or "No channels picked" in `kids/ui/rules/KidRulesPage.kt`
- [X] T022 [US4] Add `Destination.KidsChannelRefused` (kid page) in `ui/nav/Destination.kt`, render it in `ui/nav/DestinationContent.kt` with `KidsGrownUpsMessage` and the gate; in `ui/playback/PlaybackViewModel.kt` replace the playback page with it on a refused channel instead of the toast (R8, FR-016)

## Phase 7: User Story 5 - Each thing where its level says (P2)

- [X] T023 [US5] In `kids/ui/KidsParentGateViewModel.kt`: without a kid, a right code or a recovery opens Settings and closes the gate; remove Kids on this TV from the menu (FR-017, FR-018)
- [X] T024 [US5] Add `householdAuth()` in `kids/gate/ParentLockService.kt` and use it in `setCode`; test in `kids/ParentLockServiceTest.kt` (R10)
- [X] T025 [US5] Create `kids/ui/ParentCodeChange.kt` (current code, new code twice, same type, result messages) with its view model (R10)
- [X] T026 [US5] Settings groups in `preferences/AppPreference.kt` (Household: `ChangeParentCode`; This TV: server, `KidsOnThisTv`, voice search, `KidsTimerBar`, screensaver, clock, language, subtitles) and their rendering in `ui/preferences/PreferencesContent.kt` and `PreferencesViewModel.kt` (`openKidsOnTv`, timer bar)
- [X] T027 [US5] Update `design/DESIGN.md` Behaviour "After the parent code" to the new menu and Settings

## Phase 8: User Story 6 - The parent gate explains itself (P2)

- [X] T028 [US6] `KidsPasscodeDialog` forgot hint in `kids/ui/KidsPasscodeDialog.kt`; empty OK opens the recovery without `verify`, Back returns to the entry, in `kids/ui/KidsParentGateViewModel.kt` and `KidsParentGate.kt` (FR-020)
- [X] T029 [US6] Quick Connect in `kids/ui/KidsAdminSignIn.kt` and the view model for recovery and plugin-missing (FR-021)
- [X] T030 [US6] Dead lines to `DialogItemText` and stage titles in `kids/ui/KidsParentGate.kt` (FR-023)
- [X] T031 [US6] "Parent code" in every visible string, Spanish "código de padres" (FR-022)
- [X] T032 [P] [US6] Tests in `kids/KidsParentGateViewModelTest.kt`: empty OK never calls `verify`; Back from recovery; Quick Connect recovery opens the new-code stage

## Phase 9: User Story 7 - Small fixes (P3)

- [X] T033 [US7] `kids_plugin_outdated_summary` names 0.0.7.0; `kids_gate_code_changed` says only "Parent code changed" (FR-024, FR-025)

## Phase 10: Polish

- [X] T034 `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`; `nix fmt` twice; `nix develop -c pre-commit run --all-files`; stop the daemons
- [X] T035 Unused strings and imports removed (lint `UnusedResources` for the deleted pages); every new string in both languages
- [X] T036 Emulator screenshots of the menu, the stepper and the rules page if reachable without typing the code; record which weren't checked (code entry with the hint, and Forgot the code? from an empty OK, checked; the menu, the stepper and the rules page need the code typed and weren't)
- [X] T037 Record lines removed and added, and the APK size note in `specs/006-grown-ups-screens/plan.md`
- [X] T038 Follow the final plugin contract (`kids-api-v1-bonus-undo-break.md`): keep `BonusUndoUntil` so Undo never outlives the server's window, tell the undo 404 `No such bonus.` ("already taken back") from a failure, never count a break-end 404 as ended; tests in `kids/KidsTodayChangesTest.kt`, `kids/KidsApiTest.kt`, `kids/KidsParentGateViewModelTest.kt`

## Dependencies

- Phase 2 before US2 (T003–T006) and US3 (T002).
- US1 before US3 (the page US1 trims is deleted in US3).
- US4's T021 needs US3; T022 is independent.
- US5's T023 and US6 touch the gate after US2.

## Parallel examples

- T002, T003, T004 together; T006 with T009.
- Tests T010, T015, T020, T032 alongside the code they cover once its API exists.

## Implementation strategy

MVP is US1 (removes the severity-4 slip). Then US2 and US3 (the daily tasks), then US4–US7. Each phase builds and
keeps the suite green.
