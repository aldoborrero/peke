# Implementation Plan: Peke foundation

**Branch**: `003-peke-foundation` | **Date**: 2026-10-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-peke-foundation/spec.md`

## Summary

Turn the Wholphin fork into Peke for Jellyfin, a kids-only Android TV app, without the screen redesign:

- **Identity**: application id `dev.aldoborrero.peke`, label "Peke" ("Peke for Jellyfin" where there's room),
  `peke://` and `dev.aldoborrero.peke.*` intents, a Jellyfin client name and a device id of its own (hashed from
  `ANDROID_ID`), and Peke's own `v*` versions from `v0.1.0`. Wholphin's icon and banner art stay for now.
- **Kids-only routing**: a guard in `ApplicationContent` gives every non-kid profile the **grown-up shell** (kids
  settings, Peke settings, user list, license) and nothing else.
- **Grown-up settings**: Wholphin's Settings screen, reused with a Peke list that keeps every setting that makes
  sense (playback switches included) and drops the rest (research R1, option A).
- **Leak fixes**: player debug toggle and advanced rows hidden for kids (the debug overlay shows only when a
  grown-up enables it in settings); autoplay only from the plugin's kid profile, fixed 15 s countdown; Favorites/Search/home rows limited to video types;
  cinema mode and theme music off by default.
- **Build**: one product (no flavors); MPV, FFmpeg and AV1 kept and required for release; updater kept and pointed
  at aldoborrero/peke; Seerr/discover, launcher channels and the external player deleted; locales en/es;
  unused or misplaced dependencies fixed; CI builds with a `read:packages` secret.
- **Data**: kids tables move to their own `KidsDatabase` (v1); `AppDatabase` restarts at v1 with only the tables Peke
  uses (clarified 2026-10-22: full fork, no installs, database restarts at v1).
- **Spanish** for every string reachable in Peke.

## Technical Context

- **Language/Version**: Kotlin 2.4 (K2), AGP 9.2.1, Gradle 9.8, JVM 11 target.
- **Primary Dependencies**: Compose for TV, Hilt, Navigation 3, Room 2.8.5, Proto DataStore, Media3/ExoPlayer,
  MPV + Media3 FFmpeg/AV1 decoders from `damontecres/wholphin-extensions` 0.2.4, Jellyfin SDK 1.7.1, ACRA,
  aboutlibraries. No new dependency; three removed (`material3-adaptive-navigation3`, `acra-http`, `acra-dialog`)
  plus tvprovider and the OpenAPI generator plugin.
- **Storage**: `AppDatabase` 41 → 1 (new app id, never released, so no install to migrate); new `KidsDatabase` v1
  (`kid_profiles` + `serverId`, `kids_servers`); DataStore proto fields of removed features dropped (clarified
  2026-10-22: full fork, no installs, database restarts at v1).
- **Testing**: JUnit4, MockK, Robolectric, coroutines-test, Hilt testing (unit); `MigrationTestHelper` in
  androidTest. Command: `nix develop -c ./gradlew assembleDebug testDebugUnitTest` (flavor-less after this feature).
- **Target Platform**: Android TV / Google TV / Fire TV, minSdk 23, target/compile 37, D-pad.
- **Project Type**: mobile app, single Gradle module `app` plus `wholphin-mpv-stub`.
- **Performance Goals**: no regression in start-up or time to playback; the guard adds no network call.
- **Constraints**: direct play first (MPV and decoders required in release); agents never read the extensions
  token; nothing committed by agents.
- **Scale/Scope**: ~45 files deleted (Seerr, tvprovider, external player), ~70 files edited, ~6 new files
  (`KidsDatabase`, cleaner, `GrownUpShell`, Peke settings list, tests), ~120 Spanish strings.
- **Release APK size**: expected ~44–45 MB arm64-v8a and ~40–41 MB armeabi-v7a (research R12); measured and
  recorded, no target.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.* Constitution 2.0.0.

| Principle | Status | Notes |
|---|---|---|
| I. Kids-only app | ✅ | Non-kids get only the grown-up shell (R2); grown-up-viewing features are deleted (Seerr, external player, launcher channels) or unreachable (browsing, details) |
| II. Lean fork, recognisable core | ✅ | Deletes unreachable code; player, data and API layers keep layout and names; `AppDatabase` restarts at v1 (clarified 2026-10-22); kids data in `KidsDatabase` and `kids/` |
| III. The plugin decides | ✅ | No change to time handling: the plugin counts allowance, sitting and break; FR-017 keeps 001/002 behavior |
| IV. Parent-gated, fail-closed | ✅ | The shell is reached only via the gate or a grown-up sign-in; unknown kids state renders the loading/shell, never browsing; kid-safe defaults (cinema mode, theme music off; debug rows hidden) |
| V. TV-first | ✅ | The shell uses existing tv-material3 list items and focus; focus starts on "Kids settings", not on destructive actions |
| VI. Private by default | ✅ | Permissions justified below; crash dialog removed, reports only to the household server |
| VII. GPLv2 and branding | ⚠️ justified | Credits, history and license screen kept; name follows "X for Jellyfin". **Deviation**: Wholphin's icon/banner art kept until a later feature (owner, clarification Q2) — see Complexity Tracking |
| VIII. Tested | ✅ | Tests for the guard's allow-list, the Peke settings list, item-type policy, version parsing, update asset choice, device id, kids DB cleaner/sweep, missing Spanish strings; tests of deleted code deleted |
| Tech: direct play first | ✅ | MPV, FFmpeg, AV1, DASH and libass kept; release fails without the extensions |
| Tech: APK size tracked | ✅ | Expected size stated above; measured in the polish phase |

Merged-manifest permissions after this feature (constitution VI):

| Permission / entry | Why |
|---|---|
| `INTERNET`, `ACCESS_LOCAL_NETWORK` | The household's Jellyfin server; local server discovery |
| `RECORD_AUDIO`, `RECOGNIZE_SPEECH` query, `android.hardware.microphone` (not required) | Voice search, only where search is allowed for the kid, via the system recogniser (Q1) |
| `REQUEST_INSTALL_PACKAGES`, `FileProvider` | In-app updater installs Peke releases (Q4) |
| `WRITE/READ_EXTERNAL_STORAGE` (`maxSdkVersion=28`) | Updater download on Android ≤ 9 |
| `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, AndroidX dynamic-receiver permission (library-merged) | WorkManager home-row refresh workers and AndroidX internals |
| Removed | `POST_NOTIFICATIONS`, `WRITE_EPG_DATA` (+ `READ_EPG_DATA` from tvprovider), external-player `VIEW video/*` query |

Post-design re-check: unchanged; the only deviation remains VII (art).

## Project Structure

### Documentation (this feature)

```text
specs/003-peke-foundation/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/
│   ├── identity-and-intents.md   # app id, names, scheme, actions, Jellyfin client/device
│   ├── release-assets.md         # tags, versions, release asset names the updater expects
│   └── grown-up-shell.md         # routes allowed for non-kids, Peke settings list
├── checklists/requirements.md
└── tasks.md                      # /speckit-tasks
```

### Source Code

```text
app/build.gradle.kts                 # no flavors; app id; versions from v* tags; localeFilters; deps; release check
settings.gradle.kts                  # rootProject.name = "Peke"; extensions feed unchanged
app/src/main/AndroidManifest.xml     # permissions, peke scheme/actions, queries
app/src/appstore/                    # deleted
app/src/main/seerr/                  # deleted
app/src/main/res/values/strings.xml, values-es/strings.xml, app/src/debug/res/values/strings.xml
app/src/main/proto/WholphinDataStore.proto   # reserved external player value/field
app/schemas/…AppDatabase/41.json     # deleted; …kids.data.KidsDatabase/1.json new
app/src/main/java/com/github/damontecres/wholphin/
├── MainActivity.kt                  # tvprovider and external player removed; update toast only for grown-ups
├── WholphinApplication.kt           # ACRA without dialog
├── data/AppDatabase.kt              # v40, kids entities removed
├── data/ServerRepository.kt         # calls KidsDataCleaner on user/server removal
├── kids/data/KidsDatabase.kt        # new
├── kids/data/KidsDataCleaner.kt     # new: cascade + startup sweep
├── kids/data/KidProfile(.kt|Dao.kt) # serverId column; single-table queries
├── kids/KidsModule.kt               # provides KidsDatabase and DAOs
├── kids/ui/GrownUpShell.kt          # new: the shell page
├── kids/KidsProfilePolicy.kt, services/ProfilePolicy.kt  # allowedItemTypes; allowsDiscover removed
├── preferences/AppPreference.kt     # pekePreferences list; defaults; external player and Seerr rows removed
├── services/UpdateChecker.kt        # Peke feed and asset names
├── services/hilt/AppModule.kt       # client name, device id; seerrApi removed
├── services/DisplayPreferencesService.kt  # client key "Peke"
├── ui/nav/ApplicationContent.kt     # grown-up guard
├── ui/nav/Destination.kt, DestinationContent.kt, NavDrawer.kt  # Seerr/external player removed; kid-safe item routing
├── ui/playback/PlaybackPage.kt, PlaybackDialog.kt  # debug/advanced rows gated
├── ui/detail/FavoritesViewModel.kt, ui/search/SearchViewModel.kt  # allowed item types
└── (deleted) api/seerr, ui/discover, ui/detail/discover, ui/setup/seerr, services/Seerr*, services/tvprovider,
    ui/playback/PlayExternalPage.kt, …
app/src/test/…                       # new tests; TestSeerr* deleted; TestUpdateChecker updated
app/src/androidTest/…                # TestDbMigrations: kids migration test moved to KidsDatabase
.github/workflows/{pr,main,release}.yml   # flavor-less tasks, Peke asset names, extensions secrets
README.md, Intents.md                # Peke identity, scheme, credentials note
```

**Structure Decision**: single Android module, existing layout. New kids code goes under `kids/`; shared files are
edited in place (constitution II allows deletion and edits; the player and data layers keep their names).

## Phases (for /speckit-tasks)

1. **Setup**: flavor removal, app id, versions, rename of build outputs, dependency fixes (one build that
   compiles).
2. **Foundational**: the grown-up guard and shell skeleton (blocks US3 and the settings work); `KidsDatabase`
   split (blocks nothing user-visible but touches many kids files, so done early).
3. **US1 Identity** (P1), **US2 Kid leaks** (P1), **US3 Grown-up shell and settings** (P1).
4. **US4 Updater** (P2), **US5 Playback extensions and CI** (P2), **US6 Spanish** (P2).
5. **US7 Lean build** (P3): Seerr, tvprovider, external player deletions; manifest; locales.
6. **Polish**: ktlint, full test run, release build and APK size, docs (README, Intents.md), the owner's tag
   runbook.

## Risks

| Risk | Mitigation |
|---|---|
| Seerr deletion touches ~40 files incl. details/search/person pages; compile breakage cascades | Do it as its own phase after the guard, compile after each group; keep `SeerrServer.kt` entities so the DB schema doesn't move |
| `GITHUB_TOKEN` can't read the extensions feed cross-repo (R7) | CI uses a classic `read:packages` PAT secret; `GITHUB_TOKEN` tried only as fallback; vendoring AARs as plan B |
| Release silently ships without MPV/decoders | Release-variant check fails the build |
| Wholphin tags come back on `git fetch upstream`, breaking versionName | Runbook sets `remote.upstream.tagOpt --no-tags`; owner deletes tags locally and on origin before the first release |
| Kids data orphaned after the FK cascade is gone | `KidsDataCleaner` on removal + startup sweep, unit-tested |
| A cherry-pick re-adds a dropped setting to a shared list | Peke list is its own list; unit test asserts the excluded rows |
| Grown-up guard misses a destination opened by an intent or restored stack | Guard is at the `ApplicationContent` level (all keys), allow-list unit test |
| APK larger than the owner's estimate (~44–45 MB arm64) | Stated up front; libass/subfont trims remain possible later |
| Wholphin art kept (VII deviation) | Recorded; closed by the later art feature |

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| VII: Peke ships with Wholphin's launcher icon and banner art | Owner decision (clarification Q2): the Peke art arrives in a later feature; the label already says "Peke" | Using the unfinished PekeTV artwork would show a "PekeTV" wordmark next to the "Peke" label |
