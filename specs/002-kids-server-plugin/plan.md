# Implementation Plan: Kids mode backed by the Jellyfin Kids server plugin (client)

**Branch**: `feature/kids-mode` | **Date**: 2026-10-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-kids-server-plugin/spec.md`

## Summary

Kids mode becomes a client of the Jellyfin Kids plugin. It is only offered when `GET /Kids/Info` advertises
`profiles` and `parentLock` (API version 1).

- **Profiles**: the server owns kid profiles. The v1 `kid_profiles` table becomes their read-through cache, and edits
  go to the server with the cached revision.
- **Parent lock**: the server checks the household code and returns a short-lived grant, which authorises the
  changes that follow. The verifier is cached for offline unlock.
- **Time** (changed 2026-10-19, plugin 0.0.5.0): the server's status is the only source of time: the daily
  allowance, the sitting across the kid's devices and its break, and the denial reason. It is polled; the app keeps
  no timer of its own. Refusals and the plugin's messages lead to Wholphin's own screens.
- **Fail closed**: cached kid rows and the device lock stay when the plugin disappears, and a gated action turns kids
  mode off for that server.
- **Removed**: v1's device-only lock storage and the arithmetic challenge.

## Technical Context

- **Language/Version**: Kotlin (as upstream). Android TV, with the same `minSdk` and `compileSdk`.
- **Primary Dependencies**: Jetpack Compose for TV, Hilt, Room, DataStore and `KeyValueService`, OkHttp 5 (already
  used), kotlinx serialization, Jellyfin SDK 1.7.1 (for `AuthorizationHeaderBuilder` and the models). No new
  dependency.
- **Storage**: Room 41 → 42. `kid_profiles` gains cache columns, and a new `kids_servers` table holds plugin status
  and the lock cache. `KeyValueService` holds the allowance status per kid.
- **Testing**: JUnit4, MockK, coroutines-test and Robolectric, as in the existing `kids` tests. OkHttp calls are
  tested against a fake `Interceptor` (no MockWebServer dependency). There is a Room migration test.
- **Target Platform**: Android TV / Google TV, with a D-pad.
- **Project Type**: mobile app (single Gradle module `app`).
- **Performance Goals**: no extra delay before the kids home (cache first). One anonymous request per connect.
  Allowance polling every 60 s, or every 15 s near the end.
- **Constraints**: works offline from the cache, fails closed, and never stores admin credentials or grants beyond
  memory.
- **Scale/Scope**: a household of a few TVs and kids, with about 15 kids files touched or added and 4 small shared
  touch points.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Reuse Wholphin first | ✅ | The app's OkHttp client, the SDK auth header builder, `PasscodeHasher`, `ChoicePreference`, `SetPinFlow` and the existing gate UI |
| II. Isolated footprint | ✅ | New code is under `kids/`. Shared touch points: `MainActivity` (one observer and one injection), `PreferencesContent` (hide the entry), `SwitchUserViewModel` (refresh before filtering), and `AppDatabase` (version and entity). Users who don't use kids mode see no change |
| III. Server decides what, client decides how much | ✅ (1.2.0) | The household rules come from the plugin (amended principle III; see Complexity Tracking). Content stays on the server policy. No admin credentials are stored: Q1 uses the admin profile already signed in |
| IV. Parent-gated, fail-closed | ✅ | Every write needs the gate's grant (or an admin). Unknown state keeps the cache. The plugin disappearing keeps kids and the lock. Verifiers only, no plaintext |
| V. TV-first | ✅ | Reuses the D-pad gate and preference rows. Focus never lands on parent controls |
| VI. Private by default | ✅ | Data goes only to the user's own server. No telemetry |
| VII. Tested | ✅ | Unit tests for detection, mapping, conflicts, grants, offline verification, polling and combined limits, plus the migration test |

Post-design re-check: unchanged.

## Project Structure

### Documentation (this feature)

```text
specs/002-kids-server-plugin/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/kids-api-client-usage.md
├── checklists/requirements.md
├── study/                     # superseded /KidsMode study
└── tasks.md                   # /speckit-tasks
```

### Source Code

```text
app/src/main/java/com/github/damontecres/wholphin/kids/
├── KidsModeService.kt             # changed: state from the cache; per-server "turn off"
├── KidsModule.kt                  # changed: new DAO
├── server/                        # new
│   ├── KidsApi.kt                 # OkHttp calls and result types
│   ├── KidsDtos.kt                # serializable server shapes and mapping to KidProfile
│   ├── KidsPluginService.kt       # detection, per-server status, lifecycle refresh
│   └── KidsServer.kt              # Room entity and DAO for kids_servers
├── data/
│   ├── KidProfile.kt              # changed: cache columns
│   ├── KidProfileDao.kt           # changed: per-server queries
│   └── KidProfileRepository.kt    # new: refresh and writes through the server
├── gate/
│   ├── ParentLockService.kt       # new: lock type, Verify, grants, verifier cache, set or change code
│   ├── ParentLockType.kt          # changed: CHALLENGE removed
│   ├── ParentPasscodeStore.kt     # removed
│   └── ArithmeticChallenge.kt     # removed
├── timer/
│   ├── KidsAllowanceService.kt    # new: Status polling, cache, bonus, refusals
│   └── KidsTimeLimits.kt          # new: limits, denial and countdown from the server status (no local timer)
└── ui/…                           # gate, settings, timer effects updated

app/src/main/java/com/github/damontecres/wholphin/
├── MainActivity.kt                       # register KidsPluginService and KidsAllowanceService lifecycle
├── data/AppDatabase.kt                   # version 42, KidsServer entity, AutoMigration(41, 42)
├── ui/preferences/PreferencesContent.kt  # hide AppPreference.KidsMode without the plugin
└── ui/setup/SwitchUserViewModel.kt       # refresh kid profiles before the kids lock filter

app/src/test/java/com/github/damontecres/wholphin/kids/   # new and updated unit tests
app/src/androidTest/.../TestDbMigrations.kt              # migrate41To42
```

**Structure Decision**: single Android app module. Everything kids-specific stays under `…wholphin.kids`.

## Design decisions recorded (autonomous defaults)

- **D1**: a raw OkHttp `KidsApi` instead of the SDK request API, because the client needs headers and error bodies
  (R1).
- **D2**: the profile cache keeps the raw server JSON, so edits round-trip unknown fields (R3).
- **D3**: grants live in memory, keyed by server and user row, and expire 15 s early (R4).
- **D4**: an offline check uses the cached verifier and yields no grant (R4).
- **D5**: status polling runs every 60 s, every 10 s near the end or while denied, right after a break should end,
  and once after playback (R5; changed 2026-10-19).
- **D6**: the sitting limit control is shown only when the plugin advertises `sessionLimit` or sends the field
  (R6, an assumption about the plugin).
- **D7**: Room is bumped to 42 instead of rewriting 41 (R9).
- **D8**: the kids settings entry is hidden unless kids mode is available for the current server (R8).
- **D9**: picks keep using the app's own search instead of `Admin/PickCandidates`, which needs an admin.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Principle III (1.1.0): the daily allowance is decided by the server, not the client | The household plugin counts every client, and the user chose server-authoritative kids mode with no local mode | A client-side daily limit is per device and misses non-Wholphin clients. Resolved: principle III amended in constitution 1.2.0 (T036) |
