---

description: "Tasks for kids mode backed by the Jellyfin Kids server plugin (client)"
---

# Tasks: Kids mode backed by the Jellyfin Kids server plugin (client)

**Input**: Design documents from `specs/002-kids-server-plugin/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md),
[contracts/kids-api-client-usage.md](contracts/kids-api-client-usage.md)

**Tests**: required by constitution VII (unit tests for detection, mapping, grants, offline verification, polling,
combined limits; a Room migration test).

Paths are relative to `app/src/main/java/com/github/damontecres/wholphin/` (abbreviated `main/`) and
`app/src/test/java/com/github/damontecres/wholphin/` (`test/`) unless written in full.

## Phase 1: Setup

- [X] T001 Mark 001's device-only storage as superseded: add a note at the top of `specs/001-kids-mode/spec.md`
  and `specs/001-kids-mode/plan.md` pointing to `specs/002-kids-server-plugin/spec.md`

## Phase 2: Foundational (blocks every story)

- [X] T002 [P] Create `main/kids/server/KidsDtos.kt`: `@Serializable` PascalCase DTOs for `KidsInfo`
  (`PluginVersion`, `ApiVersion`, `Features`), `KidProfileRef` (`UserId`, `Revision`), `LockStatus` (`LockType?`,
  `ChangedAt?`), `VerifyResult` (`Valid`, `Grant?`, `ExpiresAt?`, `RetryAfterSeconds?`), `VerifierDto`
  (`LockType`, `Verifier`), `KidsStatusDto` (`Date`, `UsedSeconds`, `AllowanceSeconds?`, `BonusSeconds`,
  `RemainingSeconds?`, `TimeUp`, `ServerTime?`, `NextDayStartsAt?`, `WarnAtSeconds`), and the profile mapping
  `JsonObject` ↔ `KidProfile` (section names `Picked`, `KeepWatching`, `Libraries`, `New`, `Favourites`; unknown
  names dropped then `KidsHomeSectionEntry.normalize`; `DailyAllowanceMinutes` 0–1440 or null; `SessionLimitMinutes`
  5–120 or null) that replaces the app fields on the cached object (research R3)
- [X] T003 [P] Create `main/kids/server/KidsApi.kt`: OkHttp calls on `@StandardOkHttpClient` for every endpoint in
  the contract usage table, building `Authorization` with `AuthorizationHeaderBuilder` from an optional token and
  adding `X-Kids-Grant` when given; return a sealed `KidsResult<T>` (`Ok`, `NotFound`, `Conflict(body)`,
  `Forbidden`, `Throttled(seconds)`, `Invalid(errors)`, `Failed(status)`, `Unreachable`)
- [X] T004 [P] Create `main/kids/server/KidsServer.kt`: Room entity `kids_servers` (`serverId` UUID PK, FK
  `servers.id` cascade; `apiVersion` Int?; `features` String; `checkedAt` Long?; `lockType` String?; `verifier`
  String?) and `KidsServerDao` (get, observe, upsert, delete)
- [X] T005 Extend `main/kids/data/KidProfile.kt` with `revision` (Long, default 0), `dailyAllowanceMinutes` (Int?),
  `sessionLimitMinutes` (Int?), `serverJson` (String?), all with `@ColumnInfo(defaultValue)` for auto-migration; add
  per-server queries to `main/kids/data/KidProfileDao.kt` (row ids for a server, delete for a server, count for a
  server)
- [X] T006 Bump `main/data/AppDatabase.kt` to version 42 with `KidsServer` and `AutoMigration(41, 42)`, expose
  `kidsServerDao()`, provide it in `main/kids/KidsModule.kt`, and add `migrate41To42` to
  `app/src/androidTest/java/com/github/damontecres/wholphin/test/TestDbMigrations.kt`
- [X] T007 [P] Tests for DTO mapping and `KidsApi` result handling (fake `Interceptor`): 200/404/409 body/403/429/
  network error, PascalCase bodies, unknown fields kept on round trip, in `test/kids/KidsApiTest.kt`

**Checkpoint**: the app builds with schema 42 and the API layer is tested.

## Phase 3: User Story 6 - Stock Wholphin without the plugin (P1)

**Goal**: detection per server; no kids entry point without the plugin.
**Independent test**: against a server without the plugin, only `/Kids/Info` is called and Settings has no Kids
mode entry.

- [X] T008 [US6] Create `main/kids/server/KidsPluginService.kt`: check `/Kids/Info` when the current server changes
  and on foreground if the last check is older than 5 minutes; store 200 (version, features) and 404 (no plugin),
  keep the row otherwise; expose `KidsServerStatus` (`AVAILABLE`, `GONE`, `ABSENT`) per server and for the current
  server, plus `allowance` and `sessionLimit` feature flags (data-model)
- [X] T009 [US6] Register `KidsPluginService` as a lifecycle observer in `main/MainActivity.kt`
- [X] T010 [US6] Hide `AppPreference.KidsMode` in `main/ui/preferences/PreferencesContent.kt` unless the current
  server's status is `AVAILABLE` (via a `kidsAvailable` flow on `PreferencesViewModel`)
- [X] T011 [P] [US6] Tests for detection states (200 v1, 404, 500 keeps, network error keeps, unknown version,
  GONE with cached kids) in `test/kids/KidsPluginServiceTest.kt`

## Phase 4: User Story 1 - Kids set up once for the whole house (P1)

**Goal**: profiles come from the server; edits go to the server with revisions.
**Independent test**: a kid marked on the dashboard opens in kids mode on the TV; a TV edit reaches the server; a
conflict shows current values.

- [X] T012 [US1] Create `main/kids/data/KidProfileRepository.kt`: `refresh(user)` (self token: 200 upsert, 404
  delete, else keep), `refreshServer(server)` (`GET /Kids/Profiles` with any stored token, then changed kids),
  `mark(user)`, `unmark(user)`, `save(profile)` (PUT with cached revision and `serverJson` base; 409 replaces cache
  and returns `Conflict`; 403 returns `NeedsGrant`), using the grant or the admin token from `ParentLockService`
- [X] T013 [US1] Change `main/kids/KidsModeService.kt`: drop the local `setKidProfile`/`updateProfile`, refresh the
  current user's profile through `KidProfileRepository` when the current user changes and kids mode is available,
  and add `turnOffForServer(serverId)` (delete cached rows and the server row)
- [X] T014 [US1] Refresh kid profiles for the server before applying `filterUsersForKidsLock` in
  `main/ui/setup/SwitchUserViewModel.kt` (best effort, cache on failure)
- [X] T015 [US1] Update `main/kids/ui/settings/KidsSettingsViewModel.kt` and `KidsSettingsPage.kt` to mark, unmark
  and edit kids through `KidProfileRepository`, show "changed elsewhere" on conflict and "can't reach the server"
  on failure, and ask for the code again on `NeedsGrant`
- [X] T016 [P] [US1] Tests for refresh (200/404/error), list refresh deletes non-kids only on 200, save sends
  revision and keeps unknown fields, conflict replaces cache, in `test/kids/KidProfileRepositoryTest.kt`; update
  `test/kids/KidsModeServiceTest.kt`

## Phase 5: User Story 2 - Parent lock checked by the server (P1)

**Goal**: Verify with grants, throttling, offline verifier; change the code from the TV.
**Independent test**: wrong/right/throttled codes online; right code offline after one online unlock.

- [X] T017 [US2] Create `main/kids/gate/ParentLockService.kt`: resolve server and token (current, else the most
  recent kid's server and a stored token), `lockType()` (server, cache fallback), `verify(code)` →
  `Valid`/`Wrong`/`Throttled(seconds)`/`ValidOffline`/`CannotCheck`, in-memory grant per (server, user row) valid
  until `ExpiresAt` − 15 s on the monotonic clock, verifier fetch and cache after a valid check, `setCode(type,
  code, current?)` (admin or grant; re-verify the new code afterwards), `isAdmin`, `grantSettings`/
  `consumeSettingsGrant` kept from v1
- [X] T018 [US2] Remove `CHALLENGE` from `main/kids/gate/ParentLockType.kt` and delete
  `main/kids/gate/ArithmeticChallenge.kt` and `main/kids/gate/ParentPasscodeStore.kt`; update `ParentLockKeys`
- [X] T019 [US2] Update `main/kids/ui/KidsParentGateViewModel.kt`, `KidsParentGate.kt` and `KidsPasscodeDialog.kt`
  to use `ParentLockService`: throttle wait message, "can't check the code now" offline without a cache, no
  challenge
- [X] T020 [US2] Update the lock section of `main/kids/ui/settings/KidsSettingsPage.kt` and its view model: lock
  type choice (numeric or remote) and "change code" (current code then new code for grant callers; new code only
  for admins); settings verify through `ParentLockService`
- [X] T021 [P] [US2] Tests for verify results, grant expiry and drop on 403, offline verifier check, no-cache
  offline, throttle, code change re-verify, in `test/kids/ParentLockServiceTest.kt`; update
  `test/kids/ParentLockTest.kt` (no challenge)

## Phase 6: User Story 3 - First setup on a household (P1)

**Goal**: an admin profile sets the first code and marks kids; others are pointed to an admin or the dashboard.
**Independent test**: on a fresh plugin server, an admin sets the code and marks a kid; a non-admin sees the
message.

- [X] T022 [US3] In `main/kids/ui/settings/KidsSettingsViewModel.kt` and `KidsSettingsPage.kt`: when the server
  has no lock, admins get the "set a household code" dialog first, others get a message pointing to an
  administrator profile or the dashboard and nothing else
- [X] T023 [P] [US3] Strings for the new messages in `app/src/main/res/values/strings.xml`

## Phase 7: User Story 4 - Daily allowance shared across devices (P2)

**Goal**: show and enforce the server's daily allowance; add bonus time; per-kid sitting limit.
**Independent test**: a 10-minute allowance shows, warns, ends on time's up, and a bonus resumes.

- [X] T024 [US4] Create `main/kids/timer/KidsAllowanceService.kt`: poll `Status` every 60 s (15 s when ≤ 120 s
  left) while a kid is current, in the foreground and `allowance` is advertised; refresh on demand; cache per user
  row in `KeyValueService`; warnings when crossing `WarnAtSeconds`; `addBonus(minutes)` with the grant
- [X] T025 [US4] Create `main/kids/timer/KidsTimeLimits.kt`: combine `KidsTimerService.state` and the allowance into
  remaining, total, expired and cause; merge warnings
- [X] T026 [US4] Start the sitting timer with the kid's `sessionLimitMinutes` when kids mode becomes active and the
  timer is idle, in `main/kids/KidsModeService.kt` (or a small observer in `main/kids/timer/`)
- [X] T027 [US4] Use `KidsTimeLimits` in `main/kids/ui/KidsTimerEffects.kt` (bar, warnings, time's up, refresh
  after leaving playback) and `main/kids/ui/KidsParentGate.kt` (time left today; "add time" adds to each expired cause: the sitting locally, the allowance as a server bonus)
- [X] T028 [US4] Add daily allowance (unlimited, 15–240 min) and sitting limit (none, 5–120 in 5 min steps, shown
  only when `sessionLimit` is advertised or the field is present) choices to `main/kids/ui/settings/KidsSettingsPage.kt`
- [X] T029 [US4] Register `KidsAllowanceService` as a lifecycle observer in `main/MainActivity.kt`
- [X] T030 [P] [US4] Tests for polling cadence, cache restore of time up, warnings crossing, bonus, combined limits
  and sitting auto-start, in `test/kids/KidsAllowanceServiceTest.kt` and `test/kids/KidsTimeLimitsTest.kt`

## Phase 8: User Story 5 - The plugin goes away (P2)

**Goal**: fail closed and a gated way back to stock Wholphin.
**Independent test**: with the plugin disabled the kid stays in kids mode; the menu offers "turn off kids mode
here".

- [X] T031 [US5] In `main/kids/ui/KidsParentGate.kt` and its view model: when the current server is `GONE`, show the
  notice and a "turn off kids mode for this server on this TV" item that calls `KidsModeService.turnOffForServer`
- [X] T032 [P] [US5] Tests for `turnOffForServer` and GONE handling in `test/kids/KidsModeServiceTest.kt`

## Phase 9: Polish

- [X] T033 Remove now-unused v1 strings (challenge) from `app/src/main/res/values/strings.xml` and unused imports
- [X] T034 Run `nix develop -c ./gradlew assembleDefaultDebug testDefaultDebugUnitTest` and ktlint on changed files
- [X] T035 Update the kids pending-work memory note with the `sessionLimit` feature assumption (research R6)
- [X] T036 Amend constitution principle III (MINOR, 1.1.0 → 1.2.0) in `.specify/memory/constitution.md`: time limits may be decided by the household plugin when the server has it; the device still counts each sitting; record the sync impact note

## Phase 10: Fixes from the end-to-end test

- [X] T037 [US5] Plugin gone with no cached verifier (FR-026a): `ParentLockService.lockState` returns
  `LockState.PluginMissing` (and re-checks `/Kids/Info` when the lock route answers 404); `checkAdmin` signs in with
  `POST /Users/AuthenticateByName` from a one-off device id, checks `Policy.IsAdministrator`, signs out with
  `POST /Sessions/Logout` and keeps nothing. The gate shows the plugin-missing message, an administrator sign-in
  (`main/kids/ui/KidsAdminSignIn.kt`, same form as Wholphin's sign-in) and then only "turn off kids mode here"
- [X] T038 [P] [US5] Tests for `PluginMissing` versus unreachable, a cached verifier still asking for the code, and
  administrator sign-in results and sign-out in `test/kids/ParentLockServiceTest.kt`
- [X] T039 Remove v1's DataStore keys `kids_passcode_verifier` and `kids_lock_type` at startup in
  `KidsModeService`, tested in `test/kids/KidsModeServiceTest.kt` (removed in T060)
- [X] T040 [US4] "Add time" picker gets its own title; daily allowance choices add 5 and 10 minutes
- [X] T041 [US4] Adding time from the time's up screen resumes the item that was playing, at its position
  (`KidsInterruptedPlayback` in `main/kids/timer/KidsTimeLimits.kt`, tested in `test/kids/KidsTimeLimitsTest.kt`)

## Phase 11: Fixes from the adversarial review

Security:

- [X] T042 [US2] S1 (FR-030a): while locked, a sign-in again (password or Quick Connect) must yield the row's own
  Jellyfin user and server, else it is refused with a message, the row is unchanged and the new session is ended
  (`kidsLockAllowsSignIn` in `main/kids/KidsUserFilter.kt`, used by `main/ui/setup/SwitchUserViewModel.kt`; tested
  in `test/kids/KidsUserFilterTest.kt`)
- [X] T043 [US2] S2 (FR-014, FR-014a): offline wrong-code limit in `ParentLockService.verifyOffline` (5 wrong, then
  30 s doubling to 15 min, cleared by a right code), kept in `kids_servers.offlineFailures`/`offlineBlockedAt` on
  the monotonic clock; after a server `RetryAfterSeconds` or 429, `verify` and `setCode` send nothing until the wait
  is over, which covers the gate and kids settings (tested in `test/kids/ParentLockServiceTest.kt`)
- [X] T044 [US2] S3 (FR-015a): `kids_servers.lockChangedAt` holds the `ChangedAt` read before the verifier;
  `lockState` drops the verifier when the type or `ChangedAt` differs (tested in `test/kids/ParentLockServiceTest.kt`)
- [X] T045 [US5] S4 (FR-027a): `KidsResult.NotAKid` for the plugin's ProblemDetails "This user is not a kid.";
  `KidProfileRepository` removes a kid on a 404 only for `NotAKid` and after `KidsPluginService.confirmAvailable`
  (refresh, save, unmark); a bare 404 keeps the kid (tested in `test/kids/KidProfileRepositoryTest.kt` and
  `test/kids/KidsApiTest.kt`)
- [X] T046 [US4] S5 (FR-021a): `main/kids/timer/KidsMusicGuard.kt` stops music on time's up and pauses it when the
  app goes to the background in kids mode (`MusicService.stopIfStarted`/`pauseIfStarted`), registered in
  `main/MainActivity.kt`

Correctness:

- [X] T047 [US4] C1 (FR-021b): `KidsTimerEffects` reacts to the page on screen: time's up is left whenever it is on
  top and time is available; it is not shown over kids settings until the grown-up leaves; coming back to time's
  up asks for the allowance again
- [X] T048 [US2] C2 (FR-030b): the lock is set when the current kid's row id changes (also at app start) and by
  `KidsModeService.lockIfKid` after a pick in the user list, not on profile refreshes (tested in
  `test/kids/KidsModeServiceTest.kt`)
- [X] T049 [US1] C3 (FR-009b): `UserSwitchListener` rebuilds the home when the current kid's profile appears or its
  revision changes; `KidsModeService.onResume` reads the current kid's profile again after 5 minutes
- [X] T050 [US2] C4 (FR-013a): `ParentLockService.grantMillis` takes the wall clock as a parameter; the grant is
  timed from receipt on the monotonic clock, `ExpiresAt` only shortens it when 0–5 minutes away (tested). The code
  already timed grants from receipt; this makes it explicit and tested
- [X] T051 [US1] C5 (FR-009a): `KidsSettingsViewModel.update(userId, change)` saves under a mutex, applying each
  change to the latest cached profile; sections are moved and toggled by name (tested in
  `test/kids/KidsSettingsEditsTest.kt`)
- [X] T052 [US6] C6 (FR-019a): `KidsServerDao.updateInfo`/`updateLock`/`updateOfflineThrottle` targeted updates, so
  `KidsPluginService.check` and `cacheLock` can't overwrite each other (tested in
  `test/kids/KidsPluginServiceTest.kt`); `KidsAllowanceService` applies a status only if newer by `ServerTime`, or
  by request order without it, and only for the current kid (tested in `test/kids/KidsTimeLimitsTest.kt`)
- [X] T053 C7 (FR-030c): `KidsModeState.Loading` until the current user's profile is read (also on each switch),
  `KidsModeService.locked` starts null; `ApplicationContent` shows a loading page while loading, and
  `KidsLockGuard` reads `LocalKidsLocked` (tested in `test/kids/KidsModeServiceTest.kt`)

Stock users:

- [X] T054 [US6] R1 (FR-005a): the user list awaits the kids refresh only when locked; otherwise it refreshes in the
  background and narrows the list if that locks the TV
- [X] T055 [US6] R2 (FR-005a): no `kids_servers` row is created for a server that never had the plugin (tested in
  `test/kids/KidsPluginServiceTest.kt`)
- [X] T056 [US6] R3: `KidsTimerEffects` returns before creating its view model outside kids mode; `KidsLockGuard`
  has no view model and reads `LocalKidsLocked`, provided in `main/MainActivity.kt`

Tests and database:

- [X] T057 Room version 43 (`AutoMigration(42, 43)`: `lockChangedAt`, `offlineFailures`, `offlineBlockedAt`),
  migration test in `app/src/androidTest/.../TestDbMigrations.kt` (superseded by T059)
- [X] T058 Fix tests that didn't test their claim: the sitting "in progress" case in
  `test/kids/KidsModeServiceTest.kt` now starts one; `test/kids/KidsApiTest.kt` and
  `test/kids/ParentLockServiceTest.kt` use the wire format's camelCase `errors`

## Phase 12: Cleanup for upstream

Kids code lives under `main/kids/`, reuses Wholphin's own code, and stock Wholphin is unchanged on a server without
the Kids plugin. Kids mode never shipped, so nothing installed needs migrating.

- [X] T059 Room: one kids migration, version 41 with `AutoMigration(40, 41)`; `41.json` regenerated, `42.json` and
  `43.json` deleted; the `defaultValue` on `KidProfile.revision` and `KidsServer.offlineFailures` dropped (only the
  intermediate migrations needed it); one `migrate40To41AddsKidsTables` test in `TestDbMigrations.kt`
- [X] T060 Dead code: `KidsServerDao.all()`, `KidsResult.valueOrNull()`, the v1 DataStore key cleanup (T039) and its
  test, and the strings `kids_parent_lock`, `kids_add_time_title` (one `kids_add_time`), `kids_section_picked` (uses
  `kids_picked`), `kids_no_limit` (uses `no_limit`) and `kids_timer_bar_hidden` (uses `hidden_ui`); plural
  placeholders are `%1$d`
- [X] T061 `ui/setup/CredentialsForm.kt`: the username and password fields and Login button, shared by
  `SwitchUserContent` and `kids/ui/KidsAdminSignIn.kt`
- [X] T062 The administrator check uses the Jellyfin SDK (`KidsApi.isAdministrator`: an `ApiClient` with a one-off
  device id, `authenticateUserByName`, then `reportSessionEnded`, retried once); `SignInRequest`/`SignInResult`
  removed; same `AdminCheck` mapping
- [X] T063 `KidsModeService` keeps `kids_lock` in `KeyValueService`, like the other kids settings
- [X] T064 Generic hooks in shared code: `RestrictedFeature` gains `SETTINGS`, `NOW_PLAYING`, `SEARCH` and
  `LIBRARIES`, and `InterfaceCustomization.navDrawerItems`; `kids/ui/KidsUi.kt` builds the kids customization per
  profile (search from `searchAllowed`) and provides `LocalProfileGate` (`ui/nav/ProfileGate.kt`), so `NavDrawer`
  imports nothing from kids; `services/ProfilePolicy.kt` (with `ProfilePolicy.None`) is bound to
  `kids/KidsProfilePolicy.kt` and used by `HomeSettingsService`, `UserSwitchListener` and `PreferencesViewModel`
- [X] T065 `PlaybackController` uses `OverlayStateLabel` for the chapters and queue labels too (the bottom padding
  moved to the callers, so every label keeps its layout)
- [X] T066 Hygiene: unused imports, the shadowed `it` in `PlaybackViewModel.shouldAutoPlayNextUp`
- [ ] T067 Still importing kids in shared code: `MainActivity` (lifecycle observers, `LocalKidsMode`, intent guard),
  `MainContent` (`ProvideKidsUi`, `KidsLockGuard`), `AppDatabase` (entities), `ApplicationContent`,
  `DestinationContent` and `PlaybackPage` (kids pages and the end screen), `PlaybackViewModel` (autoplay cap, end
  screen, kids refusals), `ServerEventListener` (hiding the plugin's messages) and `SwitchUserViewModel` (kids lock
  at sign-in)

## Phase 13: Server enforcement and the server sitting (plugin 0.0.5.0)

The server keeps all time: the allowance, the sitting across the kid's devices and its household break, and the
denial reason. The app shows them and reacts (clarifications 2026-10-19).

Server-only time:

- [X] T068 FR-002, FR-002a: `KidsFeatures.ENFORCEMENT`, `SITTING` and `REQUIRED`; `KidsServer.available` needs all
  of `REQUIRED`, and `KidsServer.outdated` / `KidsServerState.outdated` mark a version-1 plugin with `profiles` and
  `parentLock` but not the rest. The gate (`pluginGoneTitle`/`pluginGoneSummary`) and kids settings
  (`KidsSettingsState.pluginOutdated`, entry shown by `KidsProfilePolicy.settingsAvailable`) say to update the
  plugin (tested in `test/kids/KidsPluginServiceTest.kt`)
- [X] T069 FR-024: `KidsTimerService`, its `kids_timer` checkpoint and its test removed; `KidsModeService` no longer
  starts a sitting; `MainActivity` no longer observes it; the gate's start, change and stop timer and the strings
  `kids_time_limit`, `kids_start_timer`, `kids_update_timer`, `kids_stop_timer` removed. `KidsModeServiceTest`'s two
  sitting tests removed
- [X] T070 `KidsStatus` gains `Enforced`, `DeniedReason`, `DeniedUntil`, `SittingLimitSeconds`, `SittingUsedSeconds`,
  `SittingRemainingSeconds`, `BreakRemainingSeconds` and `BreakSeconds`, and `denial`; `KidsDenial` maps
  `timeUp`, `outsideSchedule`, `sessionLimit` (unknown reasons as time's up)
- [X] T071 FR-024, FR-024a: `KidsLimits.of(status, refusal, played, elapsed)` picks whichever ends first of the
  server's sitting and the allowance, counts down between polls (`KidsCountdown`: playing time on this TV, and time
  since the status for the break) and takes the denial only from the status or a refusal; `KidsTimeLimits` ticks
  every second in the foreground, and `KidsTimerEffects` tells it when something plays. Warnings come from
  `KidsWarnings` (once per point, again after time is added)

Denial screens:

- [X] T072 FR-032: `KidsTimesUpPage` (with `KidsTimesUpViewModel`) shows time's up, bedtime (new strings
  `kids_bedtime`, `kids_bedtime_subtitle`) or time for a break (`kids_sitting_over`, `kids_break_left`) for the
  current denial; `KidsTimerEffects` shows and leaves it as before (FR-021b, FR-034)
- [X] T073 FR-033: the break left is shown from the server's `BreakRemainingSeconds`; the screen is left when a
  status no longer denies or reports the break at 0; polling asks again 1 s after the break should end
- [X] T074 FR-024b: the parent menu shows the time left today, in this sitting, or the break left
  (`kids_minutes_left_sitting`), and offers "add time" (a server bonus) when the kid has an allowance and is not
  denied for another reason; resuming playback after time's up is kept

Server messages and refusals:

- [X] T075 FR-036: `ServerEventListener` (which already toasted `DisplayMessage`/`SendString`, and
  `PlaybackViewModel`/`MusicService`, which already obey `Playstate Stop`) asks `KidsAllowanceService.check()` on a
  Kids plugin message in kids mode, and hides it when `KidsServerMessages.shouldHide` says Wholphin shows the same
- [X] T076 FR-035: `PlaybackViewModel` sends a `PlaybackInfo` `NotAllowed` (with `X-Kids-Reason`), a `PlaybackInfo`
  403 (no reason) and a stream 403 `PlaybackNotAllowed` (`KidsPlaybackRefusal.of` on the player error) to
  `KidsAllowanceService.refused`, which sets the refusal (cleared by the next status asked for after it) or reads
  the status at once; no playback error or transcode fallback then
- [X] T077 FR-037: `KidsAllowanceService.interval(limits)`: 60 s, 10 s while denied or with 2 minutes or less of
  either limit, and break left + 1 s (at most 10 s) on a break

Tests and docs:

- [X] T078 `test/kids/KidsTimeLimitsTest.kt`: the status JSON, the denial mapping, the sitting source (sitting
  present or absent), the countdown, the break and its lift, the polling cadence and the warnings;
  `test/kids/KidsEnforcementTest.kt` (Robolectric): refusals from player errors and the message hiding
- [X] T079 spec (clarifications 2026-10-19, User Story 7, FR-002a, FR-024–024b, FR-032–037), data model, plan
- [ ] T080 Manual check on a 0.0.5.0 server with enforcement on: the break screen and its lift, bedtime, a refused
  stream, no duplicate toasts (no emulator run yet)

## Phase 14: Kids live TV (plugin 0.0.6.0, optional)

A kid watches the channels a grown-up picked and sees their guide; no recordings or DVR (clarifications
2026-10-21). `liveTv` is optional: older plugins mean no kids live TV.

Server shapes:

- [X] T081 FR-038: `KidsFeatures.LIVE_TV` (not in `REQUIRED`); `KidsInfo.liveTv` (`KidsLiveTvGate`: `Gate`,
  `Missing`), logged by `KidsPluginService` when it is not `ok`
- [X] T082 FR-039: `KidsApi.liveTvChannels` (`GET /Kids/Users/{id}/LiveTv/Channels`) and `KidsItemsResult` /
  `KidsLiveTvItem`; `KidsLiveTv.channels` maps them to `KidsChannel` (only `TvChannel` items with an id),
  `KidsLiveTv.channelsAfter` says which answers mean no live TV and which keep what was known
- [X] T083 FR-044: `KidProfileMapper.LIVE_TV_CHANNEL_IDS` and `liveTvChannelIds(profile)`, read from the cached
  server object (no Room column, no migration); `toUpdate` sends it back as the server sent it

Kids UI:

- [X] T084 FR-039, FR-040: `kids/livetv/KidsLiveTvService` reads the current kid's channels once per profile
  revision, exposes `available` (`KidsLiveTv.shown`), provided to the UI as `LocalKidsLiveTv` from `MainActivity`
- [X] T085 FR-040: `kidsNavDrawerItems(liveTv)` adds `NavDrawerItem.LiveTv` (to `Destination.KidsLiveTv`) to the
  kids drawer; `KidsHomeRows.build(profile, libraries, liveTv)` adds `HomeRowConfig.TvPrograms` ("Watch live")
  after keep watching; `KidsProfilePolicy` passes it and rebuilds the home when it changes
- [X] T086 FR-041: `Destination.KidsLiveTv` shows `TvGuideGrid` (reused, not a new kids guide);
  `ProfilePolicy.liveTvChannelIds` limits `LiveTvViewModel`'s channels to the kid's. In kids mode a channel plays on
  select, and the view options are hidden (`RestrictedFeature.SETTINGS`)
- [X] T087 FR-042: `RestrictedFeature.LIVE_TV_RECORDINGS` (restricted for kids like every entry):
  `ProgramDialog` drops its record buttons (guide, search, DVR schedule), `CollectionFolderLiveTv` shows only the
  guide, `Destination.Recordings` shows "This isn't available"
- [X] T088 FR-043: `KidsPlaybackRefusal.LIVE_TV_CHANNEL`, `LIVE_TV_RECORDING` and `kindOf`; `PlaybackViewModel`
  leaves playback with `kids_channel_not_available` or `kids_not_available` instead of asking the time limits
- [X] T089 FR-045: kids settings (behind the grant) show the picked channel count per kid
  (`kids_live_tv_channels`, `kids_live_tv_count`, `kids_live_tv_none`) when the plugin has `liveTv`; picking stays
  on the dashboard

Tests and docs:

- [X] T090 `test/kids/KidsLiveTvTest.kt`: channel mapping, the gate, answers that mean no live TV, when live TV
  shows, the service (read once with the kid's token, no channels, an older plugin, errors keep what was known),
  the profile field round trip, the drawer entry; `KidsHomeRowsTest` (the live TV row), `KidProfileRepositoryTest`
  (a save sends `LiveTvChannelIds`), `KidsEnforcementTest` (live TV refusals are not time refusals)
- [X] T091 spec (clarifications 2026-10-21, FR-038–045), data model
- [ ] T092 Manual check on a 0.0.6.0 server: the drawer entry and home row appear after picking channels in the
  dashboard (within a profile refresh), the guide shows only them, a removed channel says "This channel isn't
  available", no record buttons anywhere (no emulator run yet)

## Dependencies

- Phase 2 blocks everything. US6 (detection) blocks US1–US5 (they read the server status).
- US2's `ParentLockService` is needed by US1's writes (grant); implement T017 before T012's write path.
- US3 depends on US1 and US2. US4 depends on US2 (bonus grant). US5 depends on US1 (`turnOffForServer`).

## Parallel opportunities

- T002, T003, T004 in parallel; T007, T011, T016, T021, T030 (tests) alongside their stories.

## Implementation strategy

MVP = Phases 1–6 (detection, profiles, lock, first setup): kids mode works end to end on a plugin server. Then the
allowance (US4) and fail-closed exit (US5).
