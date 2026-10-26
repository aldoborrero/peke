---

description: "Tasks for feature 004: TV setup and daily flows"
---

# Tasks: TV setup and daily flows

**Input**: Design documents from `specs/004-tv-setup-flows/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md),
[contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: included. Constitution VIII requires unit tests for policy decisions, gate verification and migrations.

**Organization**: by user story. Setup and Foundational follow plan phases 1 and 2. US1 and US2 cover phase 3 and
the launch half of phase 4. US3 to US5 cover the rest of phase 4. Polish covers phases 5 and 6.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US5 from spec.md

## Path conventions

- `main/` = `app/src/main/java/dev/aldoborrero/peke/`
- `test/` = `app/src/test/java/dev/aldoborrero/peke/`
- `res/` = `app/src/main/res/`

Every task description below uses these prefixes literally.

---

## Phase 1: Setup

**Purpose**: the database change and package that everything else builds on.

- [x] T001 Create the package `main/kids/setup/` and add `TvSetupEntity` (table `tv_setup`; fields from data-model.md: `id` Int "Always 1", `serverId` UUID, `serverUrl` String, `step` enum `SERVER`, `ADMIN`, `CODE`, `KIDS`, `SIGN_IN`, `DONE`, `lastKidRowId` Int?, `completedAt` Instant?) and `TvSetupDao` in `main/kids/setup/TvSetup.kt`
- [x] T002 Bump `peke_kids` to version 2 in `main/kids/data/KidsDatabase.kt`, with `MIGRATION_1_2`: create `tv_setup`, and insert a row with `step = DONE` when a server and kids are stored. Export `app/schemas/dev.aldoborrero.peke.kids.data.KidsDatabase/2.json`
- [x] T003 [P] Add the 1 → 2 case to `app/src/androidTest/java/dev/aldoborrero/peke/test/TestKidsDbMigrations.kt` (with and without a stored server)

---

## Phase 2: Foundational

**Purpose**: the single source of truth, the launch rule, per-kid device ids and the admin session (plan phases 1–2).

**⚠️ No story work starts before this phase is done.**

- [x] T004 Write `TvSetupRepositoryTest` in `test/kids/setup/TvSetupRepositoryTest.kt`. It must cover:
  - `NotSetUp` only with no server stored;
  - `InSetup(step)`;
  - `SetUp` with 0, 1 and several kids;
  - a missing row with a stored server gives `SetUp` (FR-018);
  - an unreadable row;
  - `lastKidRowId` updates.

  Write it first, so it fails until T005.
- [x] T005 Implement `TvState` (`Loading`, `NotSetUp`, `InSetup(step)`, `SetUp(server, kids)`) and `TvSetupRepository` (a `state` flow from `tv_setup` plus the `users` and `servers` rows, `markStep`, `markDone`, `setLastKid`, `erase`) in `main/kids/setup/TvSetup.kt`. Provide it from `main/kids/KidsModule.kt`.
- [x] T006 [P] Write `LaunchRuleTest` in `test/kids/setup/LaunchRuleTest.kt` with one case for each row of the first-screen table in `contracts/tv-flows.md`.
- [x] T007 Implement `LaunchRule` in `main/kids/setup/LaunchRule.kt`. It maps `TvState` to the first destination. Call it from `main/MainActivity.kt` `handleAppStart` for cold and warm starts and on resume, replacing the warm-start branch at `:596-601` and the `KidsLockedLaunch` call. A `SetUp` kid opens from the stored row without waiting for `getCurrentUser`; the user is refreshed in the background.
- [x] T008 [P] Write `PekeDeviceTest` in `test/services/hilt/PekeDeviceTest.kt`. The per-kid id must be stable for the same install and user, differ between users, and differ from the install id.
- [x] T009 Add `pekeKidDeviceId(baseId, userId)` (`pekeDeviceId("$baseId:$userId")`) in `main/services/hilt/PekeDevice.kt`. Correct the comment about shared ids, which only matters for the same user.
- [x] T010 Use the kid's device id in `main/data/ServerRepository.kt`, in `changeUser` (SDK `ApiClient` `deviceInfo`), and in every `KidsApi` call made with that kid's token, in `main/kids/server/KidsApi.kt`. Extend `test/services/ServerRepositoryTest.kt` and `test/kids/KidsApiTest.kt` to check that the header sends the kid's device id.
- [x] T011 [P] Write `AdminSessionTest` in `test/kids/setup/AdminSessionTest.kt`. It must check:
  - the token is never passed to `ServerRepository`, the DAOs, DataStore or prefs (verify with MockK `confirmVerified`);
  - `close()` calls `Sessions/Logout` once;
  - `close()` runs on cancel;
  - the device id is random for each session.
- [x] T012 Implement `AdminSession` in `main/kids/setup/AdminSession.kt`. It holds its own `ApiClient` with a random device id, an `open(server, username, password)`, an `openWithQuickConnect`, the `IsAdministrator` check and `close()`. It is scoped to the screen that opened it, and nothing is persisted (research R4).

**Checkpoint**: `TvState` drives the first screen; the existing screens still work behind it.

---

## Phase 3: User Story 1 - A grown-up sets the TV up once (P1) 🎯 MVP

**Goal**: a fresh install goes server → admin → code → kids → kid sign-in, and ends with kid sessions only.

**Independent Test**: quickstart scenario 1 (setup timing, dashboard devices, no admin data on the TV).

### Tests

- [x] T013 [P] [US1] Write `KidSignInTest` in `test/kids/setup/KidSignInTest.kt`. Cover each case in `contracts/server-calls.md`:
  - **Calls:** Initiate goes out with the kid's device id; Authorize goes out with the admin token and `userId`; the secret is redeemed; `Users/Me` is checked.
  - **Errors:** 403 for device access, 403 for sessions, 404 retries with a new code, 400, and `Users/Me` refused.
  - **Quick Connect off:** the password fallback.
  - **Secret:** never logged or stored.
- [x] T014 [P] [US1] Write `SetupFlowViewModelTest` in `test/kids/setup/SetupFlowViewModelTest.kt`. Cover:
  - the step order;
  - the `CODE` step skipped when a code exists;
  - a non-admin refused;
  - a plugin older than 0.0.7.0 refused;
  - no kids, then Check again;
  - finishing with at least one kid;
  - `AdminSession.close()` on finish and on abandon.

### Implementation

- [x] T015 [US1] Add `kidsInfo` version gating (0.0.7.0 or later) and `householdKids(adminToken)` to `main/kids/server/KidsApi.kt`. `householdKids` joins `GET /Kids/Profiles` with `GET /Users` for names and image tags.
- [x] T016 [US1] Implement `KidSignIn` in `main/kids/setup/KidSignIn.kt`, following research R1 and `contracts/server-calls.md`:
  - check `QuickConnect/Enabled`;
  - `initiateQuickConnect` with the kid's device id;
  - `authorizeQuickConnect(code, userId)` with `AdminSession`;
  - redeem with `authenticateWithQuickConnect`;
  - check `Users/Me`;
  - save through `ServerRepository.changeUser`.

  Also add the password fallback, with the password never stored. Keep the secret in memory only.
- [x] T017 [US1] Implement the setup view model and step screens (`SERVER`, `ADMIN`, `CODE`, `KIDS`, `SIGN_IN`) in `main/kids/setup/SetupFlow.kt`. Reuse the server discovery and address entry from `main/ui/setup/` where it's only UI, the Quick Connect code display for the admin sign-in, and the existing code entry from `main/kids/ui/` for `CODE`.
- [x] T018 [US1] Route `NotSetUp` and `InSetup` to the setup flow. Add the destinations in `main/ui/nav/Destination.kt`; route them in `main/ui/nav/ApplicationContent.kt` and `main/services/SetupNavigationManager.kt`.
- [x] T019 [P] [US1] Add the setup strings (step titles, hints and every error message in `contracts/server-calls.md`) to `res/values/strings.xml` and `res/values-es/strings.xml`.

**Checkpoint**: a fresh install can be set up and opens the kids' side; no administrator data is stored.

---

## Phase 4: User Story 2 - Kids open Peke and land on their own things (P1)

**Goal**: with one kid, their home opens directly; with several, a picker of this TV's kids, with FR-012 and FR-012a.

**Independent Test**: quickstart scenarios 2–4.

### Tests

- [x] T020 [P] [US2] Write `KidsPickerTest` in `test/kids/setup/KidsPickerTest.kt`. It must cover:
  - only this TV's kids are listed;
  - picking the last kid opens their home or stop screen;
  - picking a sibling while the last kid is `Stopped` requires the gate first;
  - a sibling is free otherwise;
  - a kid marked `NeedsGrownUp` requires the gate and then the admin;
  - an unknown or unparseable denial counts as stopped (research R6).
- [x] T021 [P] [US2] Extend `test/kids/KidsTimeLimitsTest.kt` with `isStopped(status, now)`: time's up, break, outside allowed hours, a denial past its end, and an unknown denial.

### Implementation

- [x] T022 [US2] Add `isStopped(status, now)` to `main/kids/timer/KidsTimeLimits.kt`. It reads the cached `kids_status_<rowId>` (`main/kids/timer/KidsAllowanceService.kt`).
- [x] T023 [US2] Implement the picker and the "This TV has no kids" state in `main/kids/setup/KidsPicker.kt`: avatars from Jellyfin images or initials, and Grown-ups last, which is never focused by default. Call `TvSetupRepository.setLastKid` when a kid becomes current, from `main/kids/KidsModeService.kt`.
- [x] T024 [US2] Make the sidebar avatar open the parent gate and then the picker (FR-012), in `main/ui/nav/NavDrawer.kt`.
- [x] T025 [P] [US2] Add the picker and no-kids strings to `res/values/strings.xml` and `res/values-es/strings.xml`.

**Checkpoint**: every launch lands on a kid's home or the picker (SC-003).

---

## Phase 5: User Story 3 - Grown-ups reach their menu with the parent code (P2)

**Goal**: a parent menu with the kids on this TV (add, remove), and no path to an unlocked TV.

**Independent Test**: quickstart scenario 5.

### Tests

- [x] T026 [P] [US3] Extend `test/kids/ParentLockServiceTest.kt`:
  - an admin token is no longer a grant;
  - with no kid token, the gate uses the offline verifier;
  - with no verifier, only recovery is offered.
- [x] T027 [P] [US3] Write `KidsOnTvViewModelTest` in `test/kids/setup/KidsOnTvViewModelTest.kt`:
  - removing a kid logs out and deletes only that row;
  - adding a kid opens `AdminSession`, lists only household kids not yet on the TV, and closes the session afterwards.

### Implementation

- [x] T028 [US3] In `main/kids/gate/ParentLockService.kt`, remove `isAdmin()` from `auth()` (`:355-367`) and add the offline-verifier path for when no kid token works (research R7).
- [x] T029 [US3] Rebuild the parent menu in `main/kids/ui/KidsParentGate.kt` and `main/kids/ui/KidsParentGateViewModel.kt`. Items, as in `contracts/tv-flows.md`: add time, this kid's settings, kids on this TV, This TV, license, reset. Remove Leave and Turn off here.
- [x] T030 [US3] Implement the kids on this TV screen (list, remove, add through the `KIDS` and `SIGN_IN` steps with `AdminSession`) in `main/kids/setup/KidsOnTv.kt`.
- [x] T031 [US3] Guard the grown-up destinations (settings, debug, kids settings, license) with a live parent grant in `main/ui/nav/DestinationContent.kt` (`:107-130`, `372-391`). Without a grant, redirect to the gate (C12).

**Checkpoint**: everything grown-ups do goes through the gate; no step leaves the TV unlocked.

---

## Phase 6: User Story 4 - Resetting the TV instead of unlocking it (P2)

**Goal**: Reset this TV erases everything and returns to setup.

**Independent Test**: quickstart scenario 6.

- [x] T032 [P] [US4] Write `ResetTvTest` in `test/kids/setup/ResetTvTest.kt`:
  - every kid is logged out with their own device id;
  - offline, everything is still erased and a message names the dashboard;
  - `TvSetup`, the users, the servers and the kids data (`main/kids/data/KidsDataCleaner.kt`) are erased;
  - a warm resume after a reset shows setup.
- [x] T033 [US4] Implement `ResetTv` and its confirmation screen in `main/kids/setup/ResetTv.kt`, using `KidsDataCleaner` and `TvSetupRepository.erase`.
- [x] T034 [P] [US4] Add the reset strings to `res/values/strings.xml` and `res/values-es/strings.xml`.

---

## Phase 7: User Story 5 - Recovering a forgotten parent code (P3)

**Goal**: Forgot the code? → administrator → new code.

**Independent Test**: quickstart scenario 7.

- [x] T035 [P] [US5] Add the recovery cases to `test/kids/ParentLockServiceTest.kt`:
  - `PUT /Kids/Parent/Lock` is sent without the current code, using the admin token;
  - a non-admin is refused;
  - the session closes.
- [x] T036 [US5] Add Forgot the code? to the gate in `main/kids/ui/KidsParentGate.kt`, using `AdminSession` and `ParentLockService.setCode`.
- [x] T037 [P] [US5] Add the recovery strings to `res/values/strings.xml` and `res/values-es/strings.xml`.

---

## Phase 8: Polish & cross-cutting (plan phases 5–6)

- [x] T038 Remove the lock flags. In `main/kids/KidsModeService.kt`, delete `locked`, `isLockedNow`, `lockEngaged` and the `kids_lock` key. Move every reader to `TvSetupRepository.state`: `main/kids/LocalKidsMode.kt`, `main/ui/nav/ApplicationContent.kt`, the update toast and the deep-link check (C17).
- [x] T039 Delete the old flows:
  - `main/ui/setup/` (`SwitchUserViewModel`, `SwitchUserContent`, `UserList`, `SwitchServerContent`, `ServerList`, `PinEntry`; keep only what T017 reused, moved into `main/kids/setup/`);
  - `main/data/MostRecentServer.kt`;
  - `main/kids/KidsLockedLaunch.kt` and `main/kids/KidsUserFilter.kt`;
  - `main/kids/ui/KidsLockGuard.kt`, `main/kids/ui/GrownUpRoute.kt` and `main/kids/ui/GrownUpShell.kt`.

  Delete their tests too: `KidsLockedLaunchTest`, `GrownUpRoutesTest`, `KidsUserFilterTest` and `SwitchUserViewModelTest`.
- [x] T040 [P] Make intents act only as the current kid in `main/services/IntentService.kt` (`:120-155`) and `main/MainActivity.kt` (`:527-538`). User ids in intents are ignored, and on error the intent is dropped, never sent to a user list (C6). Update `test/services/IntentServiceTest.kt`.
- [x] T041 [P] Make the screensaver show only the current kid in `main/PekeDreamService.kt` (`:75-81`) and `main/services/ScreensaverService.kt`. With no kid current, it shows Peke's neutral art (C10).
- [x] T042 [P] Disable backups and device transfer:
  - set `android:allowBackup="false"` in `app/src/main/AndroidManifest.xml`;
  - point `android:dataExtractionRules` at `res/xml/data_extraction_rules.xml` and `android:fullBackupContent` at `res/xml/backup_rules.xml`;
  - both files exclude every domain (C8).
- [x] T043 [P] Remove the Quick Connect "authorize another device" and "Sign in automatically" settings from `main/preferences/AppPreference.kt` and the settings screens (`main/ui/preferences/PreferencesContent.kt`, `main/ui/preferences/PreferencesViewModel.kt`), along with their strings.
- [x] T044 Run `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`, then `nix fmt` (0 changed) and `nix develop -c pre-commit run --all-files`.
- [ ] T045 Run quickstart.md scenarios 1–9 on the Windows emulator against the household server. The owner types the credentials. Record the results in `specs/004-tv-setup-flows/quickstart.md`.
- [x] T046 Record the release APK size per ABI in `specs/004-tv-setup-flows/plan.md`, and update `design/DESIGN.md`'s Behaviour section: launch, picker, switch kid and reset.

---

## Dependencies & execution order

### Phase dependencies

- **Phase 1 → Phase 2 → stories.** T002 needs T001, and T005 needs T002.
- **US1 (Phase 3)** needs T005, T009, T010 and T012.
- **US2 (Phase 4)** needs T005 and T007. Setting up kids for real needs US1, but its tests run without it.
- **US3, US4 and US5** need US1, for `AdminSession` and `KidSignIn`, and US2, for the picker.
- **Polish:**
  - T038 and T039 need US1–US5;
  - T040–T043 only need Phase 2;
  - T044–T046 come last.

### Within each story

The tests come first and must fail. The order after that is: API and service, then view model and screen, then routing, then strings.

### Parallel opportunities

- T003 runs alongside T004–T012.
- Within Phase 2, the tests T006, T008 and T011 run in parallel.
- US1: T013, T014 and T019 run in parallel.
- US2: T020, T021 and T025 run in parallel.
- US3: T026 and T027 run in parallel.
- US4: T032 and T034 run in parallel.
- US5: T035 and T037 run in parallel.
- Polish: T040–T043 run in parallel with each other, and with US3–US5.

## Parallel example: User Story 1

```text
Task: "KidSignInTest in test/kids/setup/KidSignInTest.kt"
Task: "SetupFlowViewModelTest in test/kids/setup/SetupFlowViewModelTest.kt"
Task: "Setup strings in res/values/strings.xml and res/values-es/strings.xml"
```

## Implementation strategy

### MVP

Phases 1–2, then US1 and US2. The result is a TV that sets itself up with kid sessions only and opens to the kid or
the picker. The old screens can stay until Polish, but nothing routes to them.

### Incremental delivery

1. Foundation, with the launch rule behind the current screens.
2. US1 setup.
3. US2 picker.
4. US3 parent menu.
5. US4 reset.
6. US5 recovery.
7. Polish: deletions and entry points.

Each step builds, passes the tests and runs on the emulator.

## Notes

- [P] = different files, with no dependency on an unfinished task.
- Agents never run git write commands; the owner commits, signed.
- Agents never handle credentials. Every admin password and Quick Connect approval in T045 is the owner's.
- Before each story's implementation, verify that its tests fail.
