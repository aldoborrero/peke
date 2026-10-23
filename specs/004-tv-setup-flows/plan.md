# Implementation Plan: TV setup and daily flows

**Branch**: `004-tv-setup-flows` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-tv-setup-flows/spec.md`

## Summary

Replace Wholphin's server list, user list and Peke's "unlocked" state with a kids-only TV:

- **Setup, once per TV**: server (with a plugin 0.0.7.0+ check), administrator sign-in, the household code if
  missing, the household's kids, and each kid signed in by **Quick Connect that the TV approves itself** with the
  in-memory administrator session (research R1). Then the admin session ends on the server and was never stored
  (constitution III, C2).
- **One device id per kid** (R2), so signing one kid or the admin out never drops a sibling's live session.
- **One source of truth**: a `TvSetup` row in the kids database replaces the `kids_lock` key and the two lock flags
  (R3). A set-up TV is always in kids mode; "leave kids mode" becomes "Reset this TV".
- **Launch**: the only kid's home, or a picker of this TV's kids. Picking a sibling while the last kid is stopped,
  or switching from inside a session, needs the parent code (R6).
- **Parent menu**: add time, kid settings, kids on this TV (add needs the admin again, remove doesn't), This TV
  settings, license, reset. Forgot the code? recovers with the administrator (R7).
- **Entry points closed**: intents never switch user, the warm start runs the launch rule, the screensaver shows
  only the current kid, grown-up destinations need a live grant, and backups and device transfer exclude everything
  (R8).

Wholphin's setup screens (`ui/setup/`) and Peke's lock and shell code are deleted rather than adapted
(constitution II).

## Technical Context

- **Language/Version**: Kotlin 2.4 (K2), AGP 9.2.1, Gradle 9.8, JVM 11 target (unchanged from 003).
- **Primary Dependencies**: Compose for TV, Hilt, Navigation 3, Room 2.8.5, Proto DataStore, Jellyfin SDK 1.7.1
  (`QuickConnectApi.authorizeQuickConnect(code, userId)`, `SessionApi.reportSessionEnded`/logout), OkHttp for
  `KidsApi`. No new dependency.
- **Storage**:
  - `peke_kids` 1 → 2: new `tv_setup` table, with a migration and an exported `2.json`.
  - App database unchanged in schema: `users` rows now exist only for kids.
  - The `<pkg>_server` prefs and the `kids_lock` key are removed.
- **Testing**: JUnit4, MockK, Robolectric, coroutines-test, Hilt testing; `MigrationTestHelper` for `peke_kids` 1 → 2.
  Command: `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`.
- **Target Platform**: Android TV, Google TV and Fire TV; minSdk 23, target/compile 37; D-pad.
- **Project Type**: single Android app module (`app/`).
- **Performance Goals**:
  - the first screen of a set-up TV shows in under 2 s, from local data only (network refresh in the background);
  - the setup sign-in of two kids takes under 30 s on the household network.
- **Constraints**:
  - offline launch must work for kids with a stored token;
  - the admin token never touches storage;
  - every new string ships in English and Spanish.
- **Scale/Scope**:
  - one server and up to about 6 kids per TV;
  - about 8 new screens or states (5 setup steps, picker, no kids, reset confirmation);
  - about 12 files deleted and 30 changed.

## Constitution Check

*Gate before Phase 0, re-checked after Phase 1.*

| Principle | Check | Status |
|---|---|---|
| I. Kids-only app | Grown-ups only set the TV up; no grown-up browsing account remains in the app | Pass |
| II. Lean fork, recognisable core | Wholphin's setup screens are deleted; `ServerRepository`, the `users` table, the SDK wiring and the player stay recognisable; `changeUser` keeps its shape for kids | Pass |
| III. The plugin decides; no admin credentials on the device | Kids, code and time stay in the plugin. `AdminSession` is memory-only and logged out after each action (R4) | Pass, and fixes C2 |
| IV. Parent-gated and fail-closed | Every way to grown-up control passes the gate (switch kid, parent menu, grown-up destinations); unknown state → kids mode; leaving the app isn't gated (2.1.0) | Pass |
| V. TV-first, D-pad | All new steps are D-pad lists and keyboards; focus never lands on Grown-ups by default | Pass |
| VI. Private by default | No new permission, data or upload; backups and device transfer disabled for the app's data | Pass |
| VII. GPLv2 and branding | No branding change; license stays reachable from the parent menu | Pass |
| VIII. Tested where it matters | Tests for `TvSetupRepository`, `AdminSession`, the launch rule, the picker rule, the gate's no-kid fallback, the kid sign-in step and intents; migration test for `peke_kids` 1 → 2 | Pass |

**APK size**: no new dependency or asset; deleting `ui/setup/` makes the release about the same size, within ±50 KB
per ABI. The measured size is recorded at the end.

**Permissions**: unchanged. Manifest change: `allowBackup="false"`, `dataExtractionRules` and `fullBackupContent`.

**Re-check after Phase 1**: the design keeps every gate above; no violations, so no complexity tracking.

## Project Structure

### Documentation (this feature)

```text
specs/004-tv-setup-flows/
├── spec.md
├── plan.md              # this file
├── research.md          # R1–R9
├── data-model.md        # TvSetup, KidOnTv, AdminSession, TvState
├── quickstart.md        # validation on the emulator and the household server
├── contracts/
│   ├── tv-flows.md      # first screen, setup steps, picker, parent menu, gate
│   └── server-calls.md  # calls, tokens and device ids per flow, error messages
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source code

Paths under `app/src/main/java/dev/aldoborrero/peke/`.

```text
kids/setup/                       # new
├── TvSetup.kt                    # entity, DAO, TvState, TvSetupRepository
├── AdminSession.kt               # in-memory admin client, close() → Sessions/Logout
├── KidSignIn.kt                  # Quick Connect approve-and-redeem, password fallback, Users/Me check
├── SetupFlow.kt                  # step screens and view models (server, admin, code, kids, sign-in)
├── KidsPicker.kt                 # picker, FR-012a rule, "no kids" state
└── ResetTv.kt                    # confirmation and erase
kids/gate/ParentLockService.kt    # drop admin-as-grant; offline verifier when no kid token; recovery
kids/ui/KidsParentGate*.kt        # parent menu items; no Leave / Turn off here
kids/KidsModeService.kt           # reads TvSetup; locked/lockEngaged/kids_lock removed
kids/data/KidsDatabase.kt         # v2 + migration
kids/server/KidsApi.kt            # per-kid device id; Users listing for setup
services/hilt/PekeDevice.kt       # pekeDeviceId per kid
data/ServerRepository.kt          # per-kid device id on the ApiClient; logout on remove
services/IntentService.kt         # current kid only
MainActivity.kt                   # one launch rule for cold and warm starts
ui/nav/ApplicationContent.kt, DestinationContent.kt, NavDrawer.kt   # routing from TvState, grant guard, avatar → gate
PekeDreamService.kt, services/ScreensaverService.kt                 # current kid only
preferences/AppPreference.kt and the settings screens               # drop Quick Connect authorize, Sign in automatically
AndroidManifest.xml, res/xml/backup_rules.xml, res/xml/data_extraction_rules.xml
res/values/strings.xml, res/values-es/strings.xml

Deleted:
ui/setup/ (SwitchUserViewModel, SwitchUserContent, UserList, SwitchServerContent, ServerList, PinEntry, …)
data/MostRecentServer.kt
kids/KidsLockedLaunch.kt, kids/KidsUserFilter.kt
kids/ui/KidsLockGuard.kt, kids/ui/GrownUpRoute.kt, kids/ui/GrownUpShell.kt
```

Tests (`app/src/test/...`):
- **new:** `TvSetupRepositoryTest`, `AdminSessionTest`, `KidSignInTest`, `KidsPickerTest`, `LaunchRuleTest`;
- **changed:** `KidsModeServiceTest`, `ParentLockServiceTest`, `KidProfileRepositoryTest`, `IntentServiceTest`,
  `ServerRepositoryTest`, `KidsDataCleanerTest`, `KidsApiTest`;
- **deleted:** `KidsLockedLaunchTest`, `GrownUpRoutesTest`, `KidsUserFilterTest`, `SwitchUserViewModelTest`;
- **instrumented:** `TestKidsDbMigrations` gains 1 → 2.

**Structure Decision**: one Android module as today. New code goes in `kids/setup/`, next to the other kids
packages (constitution II).

## Phasing (for tasks)

1. `TvSetup`, `TvState`, the migration and the launch rule, wired behind the existing screens. It reads the current
   data so the app keeps working.
2. Per-kid device ids, and `AdminSession`.
3. The setup steps, including the kid sign-in via Quick Connect, replacing `ui/setup/`.
4. The picker with FR-012/FR-012a, and the parent menu changes (kids on this TV, reset, recovery).
5. Removing the lock flags, the grown-up shell, `MostRecentServer` and the user list. Closing the entry points
   (intents, warm start, screensaver, grant guard, backups).
6. Strings in Spanish, the quickstart run on the emulator and the household server, and APK size.

## Review findings closed

C2, C3, C4, C5, C6, C8, C10, C12 (grown-up destinations behind a grant), C17 and C18.

Not in this feature: C7 (cleartext HTTP; owner decision pending), C11 (microphone prompt), C13–C16, P1, P2, P4 and P5.

## Complexity Tracking

No constitution violations to justify.
