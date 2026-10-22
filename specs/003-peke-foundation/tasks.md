---

description: "Tasks for Peke foundation"
---

# Tasks: Peke foundation

**Input**: Design documents from `specs/003-peke-foundation/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md),
[contracts/identity-and-intents.md](contracts/identity-and-intents.md),
[contracts/release-assets.md](contracts/release-assets.md), [contracts/grown-up-shell.md](contracts/grown-up-shell.md)

**Tests**: required by constitution VIII (guard allow-list, Peke settings list, item-type policy, versions, update
assets, device id, kids DB cleaner and migration infrastructure, missing Spanish strings). Tests of deleted code are
deleted with it.

Paths are relative to `app/src/main/java/com/github/damontecres/wholphin/` (abbreviated `main/`) and
`app/src/test/java/com/github/damontecres/wholphin/` (`test/`) unless written in full. Build and test with
`nix develop -c ./gradlew assembleDebug testDebugUnitTest` after each phase. Agents never read or print the
extensions token and never run git write commands; the owner commits.

## Phase 1: Setup (one flavor-less build)

- [X] T001 Remove the `version` flavor dimension and the `default`/`appstore`/`firetv` flavors from
  `app/build.gradle.kts`; in `defaultConfig` add `manifestPlaceholders["leanback"] = false` and
  `buildConfigField("boolean", "UPDATING_ENABLED", "true")` (literal); drop the `DISCOVER_ENABLED` flag; delete
  `app/src/appstore/`
- [X] T002 Set `applicationId = "dev.aldoborrero.peke"` in `app/build.gradle.kts` (keep `namespace` and the debug
  `applicationIdSuffix = ".debug"`); set `rootProject.name = "Peke"` in `settings.gradle.kts`
- [X] T003 In `app/build.gradle.kts`, derive versions from `v*` tags only: `gitTags` = `git tag --list v*`,
  `versionCode = max(1, count)`, `versionName` from `git describe --tags --long --match=v*` without `v`, `0.0.0`
  when there is no tag (contracts/release-assets.md "Tags and versions")
- [X] T004 Rename build outputs in the `androidComponents` block of `app/build.gradle.kts` to
  `Peke-{buildType}-{versionName}-{versionCode}{-abi}.apk` (no flavor segment)
- [X] T005 Replace `BuildConfig.FLAVOR` with `BuildConfig.BUILD_TYPE` in `main/ui/detail/DebugPage.kt` and
  `main/services/ServerReportService.kt`
- [X] T006 Dependency fixes in `app/build.gradle.kts` and `gradle/libs.versions.toml`: remove
  `material3-adaptive-navigation3` and `acra-http`; move `room-testing` and `androidx.test:runner` to
  `androidTestImplementation` (add `androidx.test.ext:junit` there if `TestDbMigrations` needs it)
- [X] T007 Add `androidResources { localeFilters += listOf("en", "es") }` next to `generateLocaleConfig = true` in
  `app/build.gradle.kts`
- [X] T008 Update CI task names to the flavor-less variants in `.github/workflows/pr.yml`
  (`assembleDebug testDebugUnitTest`), `.github/workflows/main.yml` and `.github/workflows/release.yml` (release
  asset details come in US5)

**Checkpoint**: `nix develop -c ./gradlew assembleDebug testDebugUnitTest` passes; `./gradlew tasks` lists only
debug/release variants.

## Phase 2: Foundational (blocks US2, US3 and the data stories)

- [X] T009 Create `main/kids/data/KidsDatabase.kt`: Room database `peke_kids`, version 1, `exportSchema = true`,
  entities `KidProfile` and `KidsServer`, DAOs `kidProfileDao()` and `kidsServerDao()`; add its schema dir under
  `app/schemas/` via the existing `room { schemaDirectory(...) }`
- [X] T010 Remove the foreign keys from `main/kids/data/KidProfile.kt` and `main/kids/server/KidsServer.kt`; add
  `serverId: UUID` (indexed) to `KidProfile` ("`serverId` | UUID (TEXT), indexed | replaces the JOINs on `users`");
  keep `userId: Int` as primary key (the `JellyfinUser.rowId`)
- [X] T011 Rewrite the JOIN queries in `main/kids/data/KidProfileDao.kt` as single-table queries on `serverId`
  (`userIdsForServer`, `observeCountForServer`, `deleteForServer`; add `serverIds()`), and move
  `mostRecentServerId` into code in its caller (order the kids `serverIds()` by `users.lastUsed` from
  `JellyfinServerDao`); set `serverId` everywhere a `KidProfile` is written (`main/kids/data/KidProfileRepository.kt`,
  `main/kids/server/KidsPluginService.kt`, `main/kids/KidsModeService.kt`)
- [X] T012 Provide `KidsDatabase` (`Room.databaseBuilder(..., "peke_kids")`) and its DAOs from
  `main/kids/KidsModule.kt` instead of `AppDatabase`
- [X] T013 Return `main/data/AppDatabase.kt` to version 40: remove the kids entities, `kidProfileDao()`,
  `kidsServerDao()` and `AutoMigration(40, 41)`; delete
  `app/schemas/dev.aldoborrero.peke.data.AppDatabase/41.json`; verify `40.json` is unchanged
  (clarified 2026-10-22: full fork, no installs, database restarts at v1: `AppDatabase` is now version 1 with
  only `1.json`; the Seerr, nav drawer pin and `PlaybackLanguageChoice` entities, Wholphin's migrations and
  `TestDbMigrations` are removed)
- [X] T014 Create `main/kids/data/KidsDataCleaner.kt`: `onUserRemoved(rowId)`, `onServerRemoved(serverId)` and a
  start-up `sweep()` that deletes `kid_profiles` without a `users` row and `kids_servers` without a `servers` row;
  call it from `ServerRepository.removeUser`/`removeServer` in `main/data/ServerRepository.kt` and once at start-up
  (`main/MainActivity.kt` or the kids start-up path)
- [X] T015 [P] Update fakes and tests for the new DAO shape: `test/kids/KidsFakes.kt` (`FakeKidProfileDao` keyed
  by `serverId` instead of the `serverOf` JOIN simulation) and its users; give `KidsDatabase` an in-memory test
  provider next to `test/ui/TestModule.kt`'s `AppDatabase` replacement
- [X] T016 [P] Test `KidsDataCleaner` (user removal, server removal, sweep of orphans) with an in-memory
  `KidsDatabase` and `AppDatabase` in `test/kids/KidsDataCleanerTest.kt`
- [X] T017 Move the kids migration test: delete `migrate40To41AddsKidsTables` from
  `app/src/androidTest/java/com/github/damontecres/wholphin/test/TestDbMigrations.kt` and add a `KidsDatabase` v1
  `MigrationTestHelper` smoke test (creates v1 from the exported schema) in the same directory
  (clarified 2026-10-22: full fork, no installs, database restarts at v1: the remaining Wholphin
  `TestDbMigrations` (2→3 only) is deleted)
- [X] T018 Create the grown-up guard in `main/ui/nav/ApplicationContent.kt`: when `LocalKidsMode` is
  `KidsModeState.Inactive` **and** `KidsModeService.locked` is `false`, render the allowed keys
  (contracts/grown-up-shell.md "Routes allowed") full screen without `NavDrawer`, mapping `Home` and every other key
  to the shell page; when `Inactive` and the lock is `true` or `null`, send the user to the kids-only user list
  (`SetupDestination.UserList`, filtered as today) and never render the shell (fail closed). Extract the decision as
  a pure function `grownUpRoute(destination, locked: Boolean?): GrownUpRoute` in `main/kids/ui/GrownUpRoute.kt`
- [X] T019 [P] Test `grownUpRoute` for every `Destination` subtype (allowed keys pass through; browse, details,
  search, playback, live TV, favorites, `Settings(BASIC/ADVANCED)` map to the shell; `Settings(EXPERIMENTAL)` passes) and for
  `locked = true` and `null` (always the kids user list, never the shell), in `test/kids/GrownUpRoutesTest.kt`

**Checkpoint**: build and tests pass; kids data is in `peke_kids`; a non-kid sees a placeholder shell.

## Phase 3: User Story 1 - Peke installs and runs as its own app (P1) 🎯 MVP

**Goal**: own app id, names, intents and Jellyfin identity.
**Independent test**: quickstart §5 rows 1 and 8.

- [X] T020 [P] [US1] Set `app_name` = "Peke" and `app_name_long` = "Peke for Jellyfin" in
  `app/src/main/res/values/strings.xml`; set `app/src/debug/res/values/strings.xml` `app_name` to "Peke (debug)";
  reword `kids_profile_off` ("Regular Wholphin") to name the grown-up shell
- [X] T021 [US1] Manifest identity in `app/src/main/AndroidManifest.xml`: scheme `peke`, action
  `dev.aldoborrero.peke.PLAYBACK`; update `ACTION_PLAYBACK` in `main/services/IntentService.kt` and any test in
  `test/` that uses the old scheme or action
- [X] T022 [US1] Jellyfin identity in `main/services/hilt/AppModule.kt`: `ClientInfo.name` from `app_name_long`;
  `DeviceModule` returns `DeviceInfo(id = sha256("$androidId:peke") first 32 hex chars, name = androidDevice(context).name)`
  via a helper `pekeDeviceInfo(context)` in `main/services/hilt/PekeDevice.kt`; use the same helper in
  `main/util/CrashReportSenderFactory.kt`
- [X] T023 [P] [US1] Set `DEFAULT_CLIENT` to "Peke" / "Peke (Debug)" in `main/services/DisplayPreferencesService.kt`
- [X] T024 [P] [US1] Test the device id (deterministic, 32 hex chars, differs from the raw `ANDROID_ID`) in
  `test/services/PekeDeviceTest.kt`
- [X] T025 [P] [US1] Update `Intents.md` for the `peke://` scheme and `dev.aldoborrero.peke.PLAYBACK` action
- [X] T026 [US1] Add a credit header above the library list in `main/ui/components/LicenseInfo.kt`: "Peke for
  Jellyfin is a fork of Wholphin by damontecres, licensed GPLv2", with the source URL
  (github.com/aldoborrero/peke) and Wholphin's (github.com/damontecres/Wholphin); strings in `values/` and
  `values-es/` (FR-007)

**Checkpoint**: Peke installs beside Wholphin with its own label, intents and Jellyfin device.

## Phase 4: User Story 2 - A kid can't reach anything outside the kids world (P1)

**Goal**: close the study's five leaks.
**Independent test**: quickstart §5 rows 3–6.

- [X] T027 [US2] In `main/ui/playback/PlaybackPage.kt`, keep seeding `showDebugInfo` from the `PlaybackDebugInfo`
  preference (a grown-up setting) and pass it to the overlay; pass `advancedPlayback` to the dialog so it can omit
  rows, and ignore the dialog's debug toggle when advanced playback is not allowed (FR-014, FR-014b)
- [X] T028 [US2] In `main/ui/playback/PlaybackDialog.kt`, omit (not disable) playback speed, video scale, subtitle
  delay and the "Show/Hide debug info" toggle when advanced playback is not allowed
- [X] T029 [US2] Autoplay only from the plugin (research R13): in `main/ui/playback/PlaybackViewModel.kt`
  `shouldAutoPlayNextUp`, use only `KidsAutoplayPolicy.allowNext` for a kid and return false otherwise (no
  `autoPlayNext`, no `passOutProtectionMs`); add `COUNTDOWN_SECONDS = 15L` to `main/kids/playback/KidsAutoplayPolicy.kt`
  and use it in `main/ui/playback/PlaybackPage.kt` instead of `autoPlayNextDelaySeconds`; extend the existing
  autoplay test (or add `test/kids/KidsAutoplayTest.kt`) for cap reached, autoplay off, and a stale interaction time
- [X] T030 [US2] (Done as one kids-only rule, since Peke is kids-only: `main/kids/KidsItemTypes.kt` with the excluded
  types AUDIO, AUDIO_BOOK, MUSIC_ALBUM, MUSIC_ARTIST, PERSON, PHOTO, PHOTO_ALBUM, instead of a `ProfilePolicy` hook.)
  Originally: add `allowedItemTypes` to `ProfilePolicy` for kids: MOVIE, SERIES, SEASON, EPISODE, VIDEO,
  MUSIC_VIDEO, BOX_SET, PLAYLIST, FOLDER, TV_CHANNEL
- [X] T031 [US2] Apply the policy in `main/ui/detail/FavoritesViewModel.kt` (`favoriteOptions`, and stop adding
  PERSON unconditionally) and `main/ui/search/SearchViewModel.kt` (`determineSearchableTypes`)
- [X] T032 [US2] Kids library rows and folders without photos or audio: request `includeItemTypes` from the policy
  for kids `ByParent` rows (`main/services/HomeSettingsService.kt` where kids rows are built, see
  `main/kids/home/KidsHomeRows.kt`) and filter folder views for kids; in `main/ui/nav/DestinationContent.kt`, for a
  kid, route PHOTO, PHOTO_ALBUM, AUDIO, MUSIC_ALBUM, MUSIC_ARTIST and PERSON items to the kids home instead of a
  page or "Unsupported item type"
- [X] T033 [US2] Defaults: `CinemaMode` = false and `PlayThemeMusic` = DISABLED in `main/preferences/AppPreference.kt`
  and `main/preferences/AppPreferencesSerializer.kt`; make sure `main/services/AppUpgradeHandler.kt` doesn't reset
  them to Wholphin's values on first run
  (clarified 2026-10-22: full fork, no installs, database restarts at v1: cinema mode, theme music and their
  preferences are now removed entirely, and `AppUpgradeHandler` has no Wholphin steps left)
- [X] T034 [P] [US2] Tests: kids policy item types and Favorites/Search filtering in
  `test/kids/KidsItemTypesTest.kt`; defaults for cinema mode and theme music in `test/preferences/PekeDefaultsTest.kt`

**Checkpoint**: none of the blocked items is reachable as a kid.

## Phase 5: User Story 3 - Grown-ups get a setup shell, not a second app (P1)

**Goal**: the shell page and the pruned settings.
**Independent test**: quickstart §5 rows 2, 7, 9, 12.

- [X] T035 [US3] Create `main/kids/ui/GrownUpShell.kt` (page + small ViewModel): items Kids settings, Settings,
  Switch user, License (focus on the first), the plugin-missing message when the current server has no usable Kids
  plugin (from `KidsPluginService`); update notices stay in Settings; wire it as the `Home`
  rendering in the guard (T018)
- [X] T036 [US3] Make the gate's `leave()` and `turnOffHere()` in `main/kids/ui/KidsParentGateViewModel.kt` land
  in the shell when a non-kid is picked (they already go to the user list; verify `SwitchUserViewModel` →
  `AppContent` → guard), and make the kids user filter still hide grown-ups while locked
- [X] T037 [US3] Add `PreferenceScreenOption.PEKE` in `main/ui/preferences/PreferenceUtils.kt` and a
  `pekePreferences` list in `main/preferences/AppPreference.kt` with the "Keep" rows of research R1 (including the
  player backend group with its ExoPlayer/MPV conditional rows, FFmpeg, AV1, max bitrate/resolution, refresh-rate
  and resolution switching, `PlaybackDebugInfo`, `QuickConnect` authorize with its existing dialog handler, and
  `ExperimentalPreference.Enable` with the Experimental sub-screen); select it in
  `main/ui/preferences/PreferencesContent.kt`
- [X] T038 [US3] Point the shell's Settings item, `License`, and any Peke settings back-navigation at
  `Settings(PEKE)`; move `OssLicenseInfo`/`InstalledVersion` handlers so they work from the Peke list
- [X] T039 [P] [US3] Test `pekePreferences`: contains the playback switches, `PlaybackDebugInfo`, `QuickConnect`,
  the Experimental entry and the update, language, subtitle, screensaver and license rows; contains none of the excluded rows in contracts/grown-up-shell.md, in
  `test/preferences/PekePreferencesTest.kt`

**Checkpoint**: from the gate's Leave or an admin sign-in, only the shell and its pages are reachable.

## Phase 6: User Story 4 - Peke updates itself from its own releases (P2)

**Goal**: updater on aldoborrero/peke.
**Independent test**: quickstart §5 row 13.

- [X] T040 [US4] In `main/services/UpdateChecker.kt`: `ASSET_NAME = "Peke"`, release-by-tag URL
  `https://api.github.com/repos/aldoborrero/peke/releases/tags/v…`; in `main/preferences/AppPreference.kt` set the
  `UpdateUrl` default to `https://api.github.com/repos/aldoborrero/peke/releases/latest` and leave `UpdateUrl` out
  of `pekePreferences`; grep `main/` for `damontecres/Wholphin` and remove every release-feed reference
- [X] T041 [US4] Show the start-up update toast (`main/MainActivity.kt` `onStart`) only when the current user is
  not a kid
- [X] T042 [P] [US4] Update `test/test/TestUpdateChecker.kt` to the `Peke*.apk` asset names of
  contracts/release-assets.md (ABI match, universal fallback, debug never matches a release asset) and add version
  parsing cases for `0.1.0`, `0.1.0-3-gabc1234` and the tag `v0.1.0` in `test/test/VersionCompareTests.kt`

**Checkpoint**: an update check hits only aldoborrero/peke and picks the right asset.

## Phase 7: User Story 5 - Playback keeps Wholphin's capability (P2)

**Goal**: MPV and decoders in every release; CI can fetch them.
**Independent test**: quickstart §3 and §5 row 11.

- [X] T043 [US5] In `app/build.gradle.kts`, fail any release-variant build (task graph contains a `*Release*`
  assemble/bundle task) when MPV, FFmpeg or AV1 would resolve to the stub or nothing, with a message pointing to
  quickstart §2; keep the debug fallback and its warnings
- [X] T044 [US5] `.github/workflows/release.yml`: build `assembleRelease` only (no bundles), pass
  `ORG_GRADLE_PROJECT_WholphinExtensionsUsername/Password` from `secrets.EXTENSIONS_USERNAME`/`EXTENSIONS_PASSWORD`,
  falling back to `github.actor`/`secrets.GITHUB_TOKEN` with `permissions: packages: read` when the secrets are
  empty; copy outputs to `Peke-{abi}.apk` and `Peke.apk` (universal); upload only APKs and mapping
- [X] T045 [P] [US5] `.github/workflows/main.yml`: same credentials and naming for the development build; keep its
  pre-release under a non-`v` tag so it never affects `versionCode` or `/releases/latest`
- [X] T046 [P] [US5] Document the credentials (classic PAT with `read:packages`, Gradle property or env var, CI
  secrets) in `README.md`, without any token value

**Checkpoint**: a release build with credentials contains `libmpv.so`, `libffmpegJNI.so`, `libdav1dJNI.so`; without
them it fails.

## Phase 8: User Story 6 - A Spanish-speaking household sees Peke in Spanish (P2)

**Goal**: every reachable string in Spanish.
**Independent test**: quickstart §5 row 10.

- [X] T047 [US6] Translate all 93 `kids_*` strings and the strings added by this feature (shell, plugin missing,
  update line) into `app/src/main/res/values-es/strings.xml`
- [X] T048 [US6] Fill missing `values-es` entries for strings reachable in Peke (kids screens, gate, shell, Peke
  settings list and sub-screens, setup, server and user lists, playback overlay and dialog) in
  `app/src/main/res/values-es/strings.xml`
- [X] T049 [P] [US6] Test that every translatable base string has an `es` entry except an explicit allow-list of
  strings unreachable in Peke, in `test/i18n/SpanishCoverageTest.kt` (parse the two XML files from `app/src/main/res`)

**Checkpoint**: Spanish TV shows no English on kids screens and in the shell.

## Phase 9: User Story 7 - Maintainers build a lean, correct Peke (P3)

**Goal**: delete Seerr, launcher channels and the external player; final manifest.
**Independent test**: quickstart §3–4.

- [X] T050 [US7] Delete Seerr/discover sources: `main/api/seerr/`, `main/ui/discover/`, `main/ui/detail/discover/`,
  `main/ui/setup/seerr/`, `main/data/SeerrServerDao.kt`, `main/util/DiscoverRequestPager.kt`,
  `main/services/SeerrApi.kt`, `main/services/SeerrServerRepository.kt`, `main/services/SeerrService.kt`,
  `main/data/model/SeerrPermission.kt`, `main/data/model/DiscoverItem.kt`, `main/data/filter/DiscoverFilter.kt`,
  `main/ui/cards/DiscoverItemCard.kt`, `app/src/main/seerr/`, `test/test/TestSeerr.kt`,
  `test/test/TestSeerrPermission.kt`; keep `main/data/model/SeerrServer.kt` (entities) (clarified
  2026-10-22: full fork, no installs, database restarts at v1: `SeerrServer.kt` is deleted too)
- [X] T051 [US7] Remove the OpenAPI generator: plugin alias, `openApiGenerate` block, generated source dir and the
  `preBuild` dependency in `app/build.gradle.kts`; the plugin entry in `gradle/libs.versions.toml`
- [X] T052 [US7] Fix Seerr callers in navigation and data: `main/ui/nav/Destination.kt`, `DestinationContent.kt`,
  `NavDrawer.kt`, `main/services/NavDrawerService.kt`, `main/ui/preferences/NavDrawerPreference.kt`,
  `main/data/AppDatabase.kt` (`seerrServerDao()` only), `main/services/hilt/DatabaseModule.kt`,
  `main/services/hilt/AppModule.kt` (`seerrApi`), `test/ui/TestModule.kt`
- [X] T053 [US7] Fix Seerr callers in services and policy: `main/services/UserSwitchListener.kt`,
  `main/services/AppUpgradeHandler.kt`, `main/services/BackdropService.kt`, `main/services/FilterOptionCache.kt`,
  `main/services/ProfilePolicy.kt` and `main/kids/KidsProfilePolicy.kt` (`allowsDiscover` removed)
- [X] T054 [US7] Fix Seerr callers in UI: `main/ui/components/FilterByButton.kt`, `SortByButton.kt`,
  `main/ui/cards/PersonRow.kt`, `main/ui/UiConstants.kt` (`AppColors.Discover`), person page, movie and
  series details/view models, `main/ui/search/` (SearchViewModel, SearchPage, SearchForDialog,
  SearchTypeOptionsDialog), home settings (`HomeSettingsViewModel`, `HomeSettingsPage`, `HomeSettingsDestination`,
  `HomeSettingsAddRow`), preferences (`PreferencesViewModel`, `PreferencesContent`, `AppPreference.SeerrIntegration`);
  remove the Seerr/discover strings from `values/` (other locales may keep orphans)
- [X] T055 [US7] Delete launcher channels: `main/services/tvprovider/` (2 files), the `MainActivity` field and
  `onStop` refresh, the `androidx.tvprovider` dependency
- [X] T056 [US7] Delete the external player: `main/ui/playback/PlayExternalPage.kt`; its uses in
  `main/MainActivity.kt`, `main/ui/nav/DestinationContent.kt`, `main/ui/preferences/PreferencesViewModel.kt`
  (external player query), `PreferencesContent.kt`, `main/preferences/AppPreference.kt` (`ExternalPlayerApp`, the
  conditional rows), `main/ui/util/InterfaceCustomization.kt` (`RestrictedFeature.EXTERNAL_PLAYER`); drop the 4th
  item of `player_backend_options` in `values/strings.xml` (and matching arrays)
- [X] T057 [US7] Proto and playback: in `app/src/main/proto/WholphinDataStore.proto` replace `EXTERNAL_PLAYER = 3`
  with `reserved 3; reserved "EXTERNAL_PLAYER";` and field `external_player = 24` with `reserved 24;`; update the
  exhaustive `when`s in `main/services/PlayerFactory.kt` and `main/ui/playback/PlaybackViewModel.kt`; map unknown backend values
  to EXO_PLAYER in `AppPreference.PlayerBackendPref`
  (clarified 2026-10-22: full fork, no installs, database restarts at v1: the `reserved` markers are dropped as well)
- [X] T058 [US7] Manifest in `app/src/main/AndroidManifest.xml`: remove `POST_NOTIFICATIONS`, `WRITE_EPG_DATA` and
  the `VIEW video/*` `<queries>` entry; keep `RECORD_AUDIO`, the `RECOGNIZE_SPEECH` query, the microphone feature
  (not required), `REQUEST_INSTALL_PACKAGES`, the `FileProvider` and the storage permissions with
  `maxSdkVersion="28"`
- [X] T059 [US7] ACRA without dialog in `main/WholphinApplication.kt`: remove the `dialog {}` block, the two toasts
  and `ReportField.USER_COMMENT`; remove `acra-dialog` from `app/build.gradle.kts` and `gradle/libs.versions.toml`

**Checkpoint**: build and tests pass; the merged manifest matches plan.md's permission table.

## Phase 10: Polish & cross-cutting

- [X] T060 Run `pre-commit run --files <changed files>` (ktlint 1.8.0) through `nix develop` and fix findings
- [X] T061 Full run: `nix develop -c ./gradlew assembleDebug testDebugUnitTest`; fix failures
- [ ] T062 Release build with the owner's credentials in place (`nix develop -c ./gradlew assembleRelease`), then
  record the size of each APK (armeabi-v7a, arm64-v8a, x86_64, universal) and the merged-manifest permissions in
  `specs/003-peke-foundation/quickstart.md` §4 (if credentials are not available to the agent, record the debug
  sizes and leave the release measurement to the owner, saying so)
  Status 2026-10-22: release build blocked on the owner's extensions token (guard works); debug sizes recorded in
  quickstart §6. Finish by re-running the release build once the token is set.
- [X] T063 [P] Update `README.md` for Peke (name, "for Jellyfin" wording, fork of Wholphin with credit, plugin
  0.0.7.0+ requirement, sideload install, GPLv2) and mark the superseded parts at the top of
  `specs/001-kids-mode/spec.md` and `specs/002-kids-server-plugin/spec.md` (per-profile mode, non-kid profiles
  unchanged, `*DefaultDebug*` commands)
- [X] T064 [P] Check no user- or server-visible "Wholphin" remains except credits, theme names and log tags:
  `rg -n "Wholphin|wholphin://|damontecres/Wholphin" app/src/main/res app/src/main/AndroidManifest.xml main/`
  and fix leftovers
- [X] T065 Hand the owner the tag runbook (quickstart §1) and the CI secrets list (quickstart §2) in the final
  report; do not run them

## Dependencies & execution order

- Phase 1 → Phase 2 → stories. Phase 2's guard (T018) blocks US3; the kids DB (T009–T017) blocks nothing
  user-visible but touches kids files that US2/US3 also touch, so it goes first.
- US1 (Phase 3) is independent of US2/US3 and is the MVP.
- US2 depends on Phase 2 only. US3 depends on T018.
- US4 depends on T037 (the Peke settings list) for the Update row placement; the checker change itself is
  independent.
- US5 depends on Phase 1 (flavor-less tasks) and US4's asset names (T040) for the workflow naming.
- US6 is best after US3 (new strings exist) and US7 (Seerr strings gone).
- US7's deletions are independent of US1–US6 but touch shared files (`DestinationContent`, `PreferencesContent`,
  `AppPreference`, `MainActivity`); do them after US3 to avoid conflicting edits in one session.
- Polish last.

## Parallel examples

- Phase 2: T015, T016 and T019 (different test files) once T009–T014 and T018 are in.
- US1: T020, T023, T024 and T025 together; T021 and T022 touch different files and can run alongside.
- US2: T034 after T030–T033.
- US5: T045 and T046 alongside T044.
- US7: T055 (tvprovider) and T059 (ACRA) alongside the Seerr tasks T050–T054, which are sequential.

## Implementation strategy

1. MVP: Phases 1–3 (flavor-less build, kids DB and guard, identity). Peke installs beside Wholphin.
2. Safety: US2 and US3 (leaks closed, grown-up shell). This is the first build worth putting on a kid's TV.
3. Distribution: US4 and US5 (updater, MPV/decoders, CI), then the owner's tag runbook and `v0.1.0`.
4. US6 Spanish and US7 trim; Polish with the size measurement.

Stop before `/speckit-implement`: the owner reviews this plan first.
