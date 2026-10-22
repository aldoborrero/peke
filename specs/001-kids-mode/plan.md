# Implementation Plan: Wholphin for Kids (kids mode v1)

> **Superseded in part (2026-10-18)**: kids mode is only offered on servers with the Jellyfin Kids plugin. Kid
> profiles and the parent lock now live on the server, and the device only caches them. The arithmetic challenge
> lock is gone, and daily and sitting limits come from the plugin. See
> [002-kids-server-plugin](../002-kids-server-plugin/spec.md). The kids experience described here is unchanged.

**Branch**: `feature/kids-mode` | **Date**: 2026-10-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-kids-mode/spec.md`

## Summary

Kids mode is a per-profile mode of the regular Wholphin app. A device user marked as a kid profile
opens directly into a simplified experience built from Wholphin's own screens:

- the same Home, with a fixed set of kids rows
- a reduced navigation drawer
- detail pages and player with risky actions hidden
- a parent lock as the single way out

New code lives in package `kids`:

- the profile store (Room)
- `KidsModeService`
- the parent gate (arithmetic challenge plus a hashed device passcode)
- a monotonic session timer with a persisted "time's up" state
- the kids home row builder
- the kids settings screen

Shared code is touched only at the choke points listed in [research.md](research.md) (R6–R12),
each behind a `kidsMode` condition. Content restriction stays on the Jellyfin server.

## Technical Context

**Language/Version**: Kotlin 2.4 (JVM target 11), Android Gradle Plugin 9.2, Gradle 9.8

**Primary Dependencies**: Jetpack Compose for TV (androidx.tv material3), Hilt, Navigation 3,
Room, DataStore (proto + preferences), Media3/ExoPlayer (+ MPV extension), Jellyfin Kotlin SDK
1.7. No new dependencies.

**Storage**: Room `AppDatabase` v40 → v41 (new `kid_profiles` table, AutoMigration). Existing
key_value `DataStore<Preferences>` for the device passcode verifier, kids lock flag and timer
checkpoint.

**Testing**: JUnit4, MockK, kotlinx-coroutines-test, Robolectric + Hilt testing (unit), Room
MigrationTestHelper (androidTest). Run with `nix develop -c ./gradlew testDefaultDebugUnitTest`.

**Target Platform**: Android TV / Google TV, minSdk 23, compileSdk 37

**Project Type**: Android TV app, single `:app` module

**Performance Goals**:

- Kids home first rows appear as fast as the regular home (same fetch path).
- Timer expiry stops playback within 5 s (SC-004).
- Gate interactions respond without perceptible delay.

**Constraints**:

- Non-kid profiles unchanged (FR-021).
- D-pad only.
- No new telemetry.
- No admin credentials stored.
- Diff against `main` kept small for upstream.

**Scale/Scope**:

- New code: about 15 files in `kids/`.
- Shared code: about 15 files touched with small conditions.
- 2 new destinations, 1 new Room entity.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|---|---|---|
| I. Reuse Wholphin first | PASS | Home rows via `HomeSettingsService.updateCurrent` + existing row types (R6). Drawer, details, player and favourites reused with conditions (R7). Settings use existing preference composables and `SearchForDialog` (R10). New UI only where nothing exists: gate keypad (R9), time's-up screen, end screen. |
| II. Isolated, upstream-friendly footprint | PASS | All new logic in `…wholphin.kids`. Shared code changes are single conditions at choke points (R7). New DI in a separate `KidsModule`. Non-kid behavior is unchanged when `KidsModeState.Inactive`. |
| III. Server decides what, client decides how much | PASS | All content still fetched with the kid's own token. The client adds only time limits, hidden features and presentation. No admin credentials are stored. The policy is read-only and only for display (R10). |
| IV. Parent-gated and fail-closed | PASS | Every exit is gated: drawer lock, back, switcher filter, leave kids mode (R7, R8). Passcode hashed with PBKDF2 (R3). Unknown or corrupt timer checkpoint → Expired. Unknown kids lock → locked. |
| V. TV-first, D-pad complete | PASS | On-screen keypad plus number keys (R9). Lock never initial focus. Two levels to playback (existing Home → details → play). |
| VI. Private by default | PASS | No new SDKs or network calls besides the existing Jellyfin API. |
| VII. Tested where it matters | PASS | Unit tests for hasher, challenge, timer, row builder, autoplay policy, mode state and switcher filter. Migration test. Robolectric drawer smoke test (R13). |

Post-design re-check (after Phase 1): **PASS**. The data model and contracts introduce no
violations, and the Complexity Tracking table is empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-kids-mode/
├── plan.md              # This file
├── research.md          # Phase 0 decisions R1–R13
├── data-model.md        # Phase 1 entities and state machines
├── quickstart.md        # Phase 1 validation guide
├── contracts/
│   └── kids-mode.md     # Internal service + UI contracts
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/github/damontecres/wholphin/
├── kids/                                  # NEW — all kids-mode logic
│   ├── KidsModeService.kt                 # active profile state, kids lock
│   ├── KidsModule.kt                      # Hilt: DAO + MonotonicClock
│   ├── LocalKidsMode.kt                   # CompositionLocal
│   ├── data/
│   │   ├── KidProfile.kt                  # Room entity
│   │   └── KidProfileDao.kt
│   ├── gate/
│   │   ├── ArithmeticChallenge.kt
│   │   ├── PasscodeHasher.kt
│   │   └── ParentPasscodeStore.kt
│   ├── home/KidsHomeRows.kt
│   ├── playback/KidsAutoplayPolicy.kt
│   ├── timer/
│   │   ├── MonotonicClock.kt
│   │   └── KidsTimerService.kt
│   └── ui/
│       ├── ParentGatePanel.kt             # gate + parent menu
│       ├── KidsTimerEffects.kt            # bar, warnings, expiry navigation
│       ├── KidsTimesUpPage.kt
│       ├── KidsEndScreen.kt
│       └── settings/KidsSettingsPage.kt (+ ViewModel)
├── data/AppDatabase.kt                    # + entity, v41, AutoMigration(40,41), converter
├── MainActivity.kt                        # timer resume/pause, intent guard
├── MainContent.kt                         # provide LocalKidsMode, header customization
├── services/HomeSettingsService.kt        # kids rows on load
├── services/NavDrawerService.kt           # filter library items
├── services/UserSwitchListener.kt         # skip Seerr for kids
├── ui/nav/{Destination,DestinationContent,NavDrawer,ApplicationContent}.kt
├── ui/main/HomePage.kt                    # no view-more / long-press in kids
├── ui/components/{ContextMenu,PlayButtons}.kt
├── ui/playback/{PlaybackPage,PlaybackViewModel}.kt
├── ui/setup/SwitchUserViewModel.kt        # kid-only list while locked
└── preferences/AppPreference.kt (+ PreferencesContent) # "Kids mode" entry

app/schemas/com.github.damontecres.wholphin.data.AppDatabase/41.json   # generated
app/src/test/java/com/github/damontecres/wholphin/kids/…              # unit tests
app/src/androidTest/java/com/github/damontecres/wholphin/test/TestDbMigrations.kt  # + 40→41
app/src/main/res/values/strings.xml                                    # new strings (default only)
```

**Structure Decision**:

- There is a single `:app` module. Kids code is a package inside it, with no product flavor, as
  amended by constitution II (v1.1.0).
- Tests mirror that package under `app/src/test/.../kids/`.

## Complexity Tracking

No violations to justify.
