# Research: Peke foundation

Feature: [spec.md](spec.md) · Plan: [plan.md](plan.md) · Date: 2026-10-22

Paths are relative to `app/src/main/java/com/github/damontecres/wholphin/` (written `W/`) unless they start with
`app/`, `.github/` or `gradle/`. Line numbers are from the tree at `f245846b` and will drift.

Baseline (2026-10-22): `nix develop -c ./gradlew assembleDefaultDebug testDefaultDebugUnitTest` passes in 3 min,
without the extensions credentials (stub MPV, no FFmpeg/AV1 decoders).

## R1. Grown-up settings: reuse Wholphin's Settings screen or build a "This TV" section (owner asked)

**Decision: Option A, reuse Wholphin's Settings screen with a Peke-specific pruned list, reachable only from the
grown-up shell.**

How each works today:
- `Destination.Settings(PreferenceScreenOption)` renders `PreferencesPage` → `PreferencesContent`
  (`W/ui/preferences/PreferencesContent.kt:92,828`), which picks a list of `PreferenceGroup`s (`:152-160`) from
  `W/preferences/AppPreference.kt` (`basicPreferences` `:1180`, `advancedPreferences` `:1313`, and the sub-screens
  EXO_PLAYER, MPV, HDR_FORMATS, SCREENSAVER, SKIP_SEGMENTS, EXPERIMENTAL).
- `PreferencesContent` holds the special handlers that many rows need (`:316-640`): installed version → debug page,
  update check/install, clear image cache, send app logs, screensaver "start now", UI language, the libMPV check.
- Sub-screens navigate with `Destination.Settings(…)` (screensaver, player backend sub-screens, skip segments) or
  `Destination.SubtitleSettings` (subtitle style).

| | A. Reuse Settings, pruned | B. New "This TV" section in kids settings |
|---|---|---|
| Change size | ~60–120 lines: one `pekePreferences` list (or a deny set applied where `PreferencesContent` filters `KidsMode` at `:286`), the external-player option removed, and the grown-up shell allow-list (`Settings`, `SubtitleSettings`, `Debug`, `UpdateApp`) | ~200–350 lines: re-implement the clickable handlers (version, updates, logs, cache, screensaver start, language), DataStore writes in `KidsSettingsViewModel`, the libMPV check, and the backend-dependent rows |
| Coverage of "all fine settings" | Every existing row and sub-screen, including the ExoPlayer/MPV/HDR sub-screens, with no new UI code | Only the rows someone ports; each new upstream setting needs porting |
| Cherry-picks | Upstream changes to `AppPreference`/`PreferencesContent` apply directly | Diverges from upstream's settings code |
| Gate | Reached from the shell, which is itself behind the gate or a grown-up sign-in | Inherits the kids settings grant |
| Risk | A pruned item reappears if a cherry-pick adds it to a shared list; mitigated by a unit test over the Peke list | Duplicated logic drifts |

Rationale: A gives grown-ups every setting that makes sense with the least new code and stays cherry-pickable
(constitution II). The kids-unsafe parts are excluded by the list, and kids can't reach `Destination.Settings` at
all (FR-014, guard in R2).

The Peke list (single top screen plus the existing sub-screens):

| Group | Keep | Drop (why) |
|---|---|---|
| Interface | SubtitleStyle, ThemeColors, ScreensaverSettings, UserInterfaceLanguage, ShowClock, SignInAuto | RememberSelectedTab, PlayThemeMusic (forced off), BackdropStyle, ShowLogos, DisplayToggles, ManageMedia, Slideshow* (grown-up browsing only) |
| Playback | SkipForward, SkipBack, SkipBackOnResume, OneClickPause, ControllerTimeout, DpadSeekMode, SkipSegments, GlobalContentScale, PlaybackDebugInfo (a grown-up turns it on to diagnose direct play vs transcoding; the overlay then shows during kid playback) | ShowNextUpTiming, AutoPlayNextUp, AutoPlayNextDelay, PassOutProtection (autoplay comes only from the kid profile in the plugin), CinemaMode (forced off) |
| Streaming | MaxResolution, MaxBitrate, RefreshRateSwitching, ResolutionSwitching | |
| Player backend | PlayerBackend (ExoPlayer / MPV / prefer MPV), ExoPlayer sub-screen (FFmpeg decoder, AV1 software decoding, downmix, AC3, ASS, PGS, HDR formats), MPV sub-screen | ExternalPlayerApp and the EXTERNAL_PLAYER choice (deleted) |
| About / updates | InstalledVersion, Update (check and install), AutoCheckForUpdates | UpdateUrl (fixed to Peke's feed) |
| More | QuickConnect (authorize another device; grown-up settings only), SendAppLogs, SendCrashReports, DebugLogging, ImageDiskCacheSize, ClearImageCache, Experimental (the toggle and the Experimental sub-screen with all its items), OssLicenseInfo | SeerrIntegration (deleted), CustomizeHome and UserPinnedNavDrawerItems (the plugin profile owns the kids home; Peke owns the nav), ProtectProfile, UserProfileSettings (grown-up viewing), KidsMode entry (the shell links kids settings directly) |

Alternatives considered: B (above); keeping Wholphin's BASIC/ADVANCED split (rejected: two screens for ~35 rows
adds a level without benefit).

## R2. Grown-up shell and the guard

**Decision**: route on the kids state in `W/ui/nav/ApplicationContent.kt` (`:83-110`). When the signed-in user is
not a kid (`KidsModeState.Inactive`) and the device's kids lock is off, render destinations full screen without the nav drawer and allow only:
`Home` (rendered as the new `GrownUpShell` page), `KidsSettings`, `Settings(*)`, `SubtitleSettings`, `License`,
`Debug` and `UpdateApp`. Any other key renders the shell instead.

When the lock is on or unknown (`KidsModeService.locked` is `true` or `null`), a non-kid user is sent to the
kids-only user list instead: this covers a kid whose cached row is gone (kids database lost or not yet refreshed),
who must not land in an ungated shell (constitution IV, fail closed). The lock only turns off through the gate's
"Leave" or a fresh setup with no lock.

Rationale: `Destination.Home` stays the root, which `NavigationManager.goToHome/reloadHome`, `KidsTimerEffects`
and `PlaybackPage` assume. One guard covers intents, restored back stacks, drawer navigation and the gate's
"Leave" and "turn off here" paths, which land on the user list and then `AppContent` with `Home`.

The shell page offers: kids settings, settings, the user list (switch user, sign out, add or switch server), the
license screen, and when the server has no usable Kids plugin, the explanation (FR-013). It uses existing
tv-material3 list items; no new components.

Alternatives: a separate `SetupDestination` for the shell (rejected: duplicates the inner navigation stack and
breaks `reloadHome`); a guard only in `DestinationContent` (rejected: the drawer stays).

## R3. Kids database

**Decision**: a new Room database `KidsDatabase` (file `peke_kids`, version 1, its own schema directory) holding
`kid_profiles` and `kids_servers`. `AppDatabase` restarts at version 1 with only the entities Peke uses (no Seerr,
nav drawer pin or old language-choice tables), without Wholphin's migrations and with a fresh `1.json`
(clarified 2026-10-22: full fork, no installs, database restarts at v1; this replaces "back to upstream's v40").

Facts:
- Kids DAOs are provided by `W/kids/KidsModule.kt:21-27`; four `KidProfileDao` queries JOIN the app's `users`
  table (`userIdsForServer`, `observeCountForServer`, `mostRecentServerId`, `deleteForServer`, `KidProfileDao.kt:23-42`).
- The foreign keys cascade on server and user deletion, which only happens in `ServerRepository.removeUser`
  (`W/data/ServerRepository.kt:269-280`) and `removeServer` (`:282-293`).
- Peke is a new application id that has never been released, so no install has any `AppDatabase` version.

Design:
- Keep `KidProfile.userId` as the local user row id (stable while the row exists; rows are never re-created,
  `JellyfinServerDao.addOrUpdateUser` uses IGNORE + UPDATE) and add a `serverId` column, so the four JOINs become
  single-table queries. `mostRecentServerId` takes the candidate server ids from the kids DB and orders them in
  code by the app DB's `lastUsed`.
- Cascades become code: `ServerRepository.removeUser/removeServer` call a `KidsDataCleaner` (kids package) that
  deletes the kid row(s) and the server row. A startup sweep deletes kid rows whose user row no longer exists
  (covers crashes between the two deletes).
- Changing the key to `(serverId, user UUID)` was rejected: it touches every caller (`kidUserRowIds(): Set<Int>`,
  `profileFor(user)`, filters) for no user-visible gain.
- Tests: `KidsDatabase` migration test infrastructure (schema export for v1, `MigrationTestHelper` ready for v2), a
  Robolectric test of the cleaner and the sweep, `FakeKidProfileDao` updated to the new queries. The androidTest
  `migrate40To41AddsKidsTables` is removed.

## R4. Identity on the device and on the server

**Decision**:
- `applicationId = "dev.aldoborrero.peke"` (debug keeps `.debug`); `namespace = "dev.aldoborrero.peke"` and all packages under it, except the `wholphin-mpv` API.
- `app_name` = "Peke", `app_name_long` = "Peke for Jellyfin"; the debug override (`app/src/debug/res`) says
  "Peke (debug)". The DreamService label follows `app_name`.
- Jellyfin `ClientInfo.name` uses `app_name_long`, "Peke for Jellyfin": the dashboard shows it next to the device
  name, where there is room for the full name. `DisplayPreferencesService.DEFAULT_CLIENT`
  (`W/services/DisplayPreferencesService.kt:57`) becomes "Peke" so Peke's server-side display preferences don't
  mix with Wholphin's.
- Device id: `androidDevice(context)` (jellyfin-sdk 1.7.1) returns the raw `ANDROID_ID`, which on Android 8+ is per
  signing key. Peke uses `sha256("$androidId:peke")` truncated to 32 hex chars, with the SDK's device name, in
  `DeviceModule` (`W/services/hilt/AppModule.kt:201-209`) and `W/util/CrashReportSenderFactory.kt:84-85`. This
  holds even if Peke and Wholphin are ever signed with the same key. Jellyfin revokes other tokens with the same
  device id on sign-in, which is the collision SC-001 tests.
- Deep links: scheme `peke`, action `dev.aldoborrero.peke.PLAYBACK` (manifest `:65,:68`,
  `W/services/IntentService.kt:203`, `Intents.md`). Still ignored while a kid is locked in.
- Other visible "Wholphin" strings: ACRA dialog text (removed, R8), `kids_profile_off` "Regular Wholphin" (reworded),
  the "Wholphin Default/Compact" theme names (kept: they name Wholphin's themes, credit is fine), `updated_toast`
  (uses `app_name`). Log tags and the DB file name `wholphin` stay (not visible; keeps cherry-picks simple).
- Launcher icon and banner: unchanged Wholphin art (clarification Q2), recorded as a temporary deviation from
  constitution VII.

## R5. Versions and tags

**Decision**: plain `v*` tags from `v0.1.0`; `versionCode` = number of `v*` tags (at least 1); `versionName` =
`git describe --tags --long --match=v*` without the `v`, `0.0.0` when there's no tag.

- `W/util/Version.kt:80` already parses `0.1.0` and `0.1.0-3-gabc123`; `UpdateChecker.getRelease` builds
  `…/releases/tags/v{major}.{minor}.{patch}`, which matches.
- `p*` is dropped from the tag count (unused).
- The repo carries 53 Wholphin `v*` tags (`v0.0.1` … `v1.0.9`, including a Wholphin `v0.1.0`) and a `develop` tag,
  locally and on `origin` (aldoborrero/peke); `gh release list -R aldoborrero/peke` shows no releases. The owner
  deletes them (commands in [quickstart.md](quickstart.md) §1) and sets `remote.upstream.tagOpt=--no-tags`,
  otherwise the next `git fetch upstream` brings them back and `versionName` jumps to 1.0.9.
- Until the first Peke tag exists, local builds are `0.0.0-…` with code 1.
- `AppUpgradeHandler` keeps only the version bookkeeping, the subtitle font copy and an empty hook for steps keyed
  to Peke versions; Wholphin's steps and `WHOLPHIN_FORK_VERSION` are gone, and a fresh install takes all its defaults
  from `AppPreferencesSerializer` (clarified 2026-10-22: full fork, no installs, database restarts at v1).

## R6. Updater

**Decision**: keep `UpdateChecker` and `InstallUpdatePage`, re-pointed:
- `UpdateUrl` default `https://api.github.com/repos/aldoborrero/peke/releases/latest`; release-by-tag URL
  `https://api.github.com/repos/aldoborrero/peke/releases/tags/v…`; `ASSET_NAME = "Peke"`. The `UpdateUrl`
  preference row is removed from the Peke settings list.
- `UPDATING_ENABLED` becomes a `defaultConfig` literal `true`.
- The start-up auto check (`MainActivity.onStart`) shows its toast only when the current user is not a kid
  (FR-023). Kids never see update UI: `UpdateApp` is outside the kids routes and inside the shell allow-list.
- Asset names: see [contracts/release-assets.md](contracts/release-assets.md). `TestUpdateChecker` moves to the
  `Peke*.apk` names.

Permissions it needs (justified per constitution VI): `REQUEST_INSTALL_PACKAGES` (install the APK), the
`FileProvider` (hand the APK to the installer), `WRITE/READ_EXTERNAL_STORAGE` with `maxSdkVersion=28` (download to
Downloads on Android ≤ 9; minSdk is 23).

## R7. MPV, FFmpeg and AV1 from the extensions feed

**Decision**: keep the existing wiring (`app/build.gradle.kts:369-397`, `settings.gradle.kts`): local
`app/libs/*.aar` first, else the GitHub Packages feed when `WholphinExtensionsUsername` is set (Gradle property or
`ORG_GRADLE_PROJECT_WholphinExtensionsUsername`/`…Password`), else the stub. Add a check: any `release` variant
build fails with a clear message when MPV or a decoder would come from the stub/nothing. Debug keeps the fallback
with its warning.

Agents never read or handle the token; the owner sets it in `~/.gradle/gradle.properties` or the environment.

**CI and `GITHUB_TOKEN` (owner asked)**: it almost certainly does **not** work. The Maven registry on GitHub
Packages only supports repository-scoped permissions, and a workflow's `GITHUB_TOKEN` is limited to its own
repository (the fork exception doesn't apply: aldoborrero/peke is not a GitHub fork). Every Maven download needs
authentication even for public packages, and fine-grained PATs still don't support Packages. No report shows
`GITHUB_TOKEN` reading another owner's Maven package. Sources:
docs.github.com/en/packages/learn-github-packages/about-permissions-for-github-packages,
docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry,
github.com/orgs/community/discussions/26634, github.com/orgs/community/discussions/25513.

CI design:
- Release and main workflows read `secrets.EXTENSIONS_USERNAME`/`EXTENSIONS_PASSWORD` (a **classic PAT with only
  `read:packages`** created by the owner), as Wholphin's workflows already do. Fallback when the secret is empty:
  try `github.actor` + `GITHUB_TOKEN` (cheap, and settles the question in one run); the release check (above)
  fails the build if neither works.
- PR builds stay debug-only and need no secret.
- Alternative kept open: vendor the three AARs into `app/libs/` (the build already prefers them; GPL-2.0 permits
  it with source pointers). It removes the secret entirely but adds ~35 MB of binaries to the repo history;
  recommended only if the PAT becomes a burden.

## R8. Build trim

| Item | Decision | Notes |
|---|---|---|
| Seerr/discover | **Delete** 33 files (~7.7k lines), `app/src/main/seerr/`, the OpenAPI plugin and codegen, `TestSeerr*`. `SeerrServer.kt` and its tables go too (clarified 2026-10-22: full fork, no installs, database restarts at v1). ~40 callers edited (navigation, drawer, search, person/details, filters, preferences, home settings, `UserSwitchListener`, `ProfilePolicy.allowsDiscover`, `AppModule`, `DatabaseModule`, `TestModule`) | Constitution II: unreachable for kids, costs build time |
| tvprovider / EPG channels | **Delete** `W/services/tvprovider/*` (2 files), `MainActivity` field and `onStop` call, `WRITE_EPG_DATA`, the dependency | `IntentService` stays (suggestions use it) |
| External player | **Delete** `W/ui/playback/PlayExternalPage.kt` and its uses (`MainActivity`, `DestinationContent`, `PreferencesViewModel`, `AppPreference.ExternalPlayerApp`, `RestrictedFeature.EXTERNAL_PLAYER`, the 4th backend option). Proto value 3 and field 24 are dropped without `reserved` markers (no installs); an unknown stored value maps to ExoPlayer | Kids would leave the app |
| DASH | **Keep** | Referenced by the direct-play device profile (`DeviceProfileUtils:188`) and `PlaybackViewModel:811`; dropping it would turn DASH direct play into a failure, against "direct play first", for ~150 KB |
| Updater, markdown renderer, MPV, FFmpeg, AV1, libass, subfont/fonts | **Keep** | Owner's build scope and direct play |
| `POST_NOTIFICATIONS` | **Remove** | No code uses notifications |
| External-player `<queries>` (`VIEW video/*`) | **Remove** | Only the external player used it |
| `RECORD_AUDIO`, `RECOGNIZE_SPEECH` query, microphone feature (not required) | **Keep** | Voice search for kids where search is allowed (clarification Q1) |
| `material3-adaptive-navigation3`, `acra-http` | **Remove** | No imports |
| `room-testing`, `androidx.test:runner` | Move to `androidTestImplementation` (+ `androidx.test.ext:junit` if not present) | Only `TestDbMigrations` and `WholphinTestRunner` use them |
| `acra-dialog` | **Remove** with the dialog | R9 |
| Flavors | **Remove** `default/appstore/firetv`, `src/appstore/`; `leanback=false` placeholder and `UPDATING_ENABLED=true` in `defaultConfig`; `BuildConfig.FLAVOR` uses (`DebugPage:144`, `ServerReportService:95`) show the build type instead; APK name `Peke-{buildType}-{versionName}-{versionCode}{-abi}.apk` | One sideload build for Google TV, Android TV, Fire TV |
| Locales | `androidResources.localeFilters += listOf("en", "es")` (AGP 9.2.1 supports it); `values-*` dirs stay (cherry-picks of translations stay clean) | `generateLocaleConfig` then offers only en/es |

## R9. Crash reports

**Decision**: remove ACRA's dialog block, its two toasts and `ReportField.USER_COMMENT`
(`W/WholphinApplication.kt:93-105`) and the `acra-dialog` dependency. Reports go silently to the household's own
server only when `SendCrashReports` is on (it writes `acra.enable`, which ACRA honours). The default stays as
upstream ships it.

## R10. Kid leaks

| Leak | Fix |
|---|---|
| Player debug info, video scale, subtitle delay, speed | `PlaybackDialog` omits speed, video scale, subtitle delay and the debug-info toggle when advanced playback is not allowed (`:137-171`), instead of showing them disabled, so a kid can neither switch debug info nor change those options. The overlay follows the `PlaybackDebugInfo` preference, which only a grown-up can set in the grown-up settings (owner decision 2026-10-22: diagnosing direct play vs transcoding during kid playback) |
| Music, people, photos in Favorites and Search | Implemented as one kids-only rule (`kids/KidsItemTypes.kt`): `favoriteOptions` and `allSearchableTypes` list only video types (no PERSON, music, photos, or channels and programs outside the kid's guide); home `ByParent` rows and folder views send `excludeItemTypes`; a kid opening an excluded item or a slideshow goes back with a message |
| Photos and photo albums in home-video rows | Kids `ByParent` rows request `includeItemTypes` = the video types plus FOLDER; folder views for kids exclude PHOTO/PHOTO_ALBUM; `DestinationContent` sends PHOTO/PHOTO_ALBUM/AUDIO/MUSIC_* /PERSON to the kids home instead of a page when the profile is a kid |
| Cinema mode | Default `false` in `AppPreference.CinemaMode` and the serializer; row removed from Peke settings. Later removed entirely with its proto field (clarified 2026-10-22: full fork, no installs, database restarts at v1) |
| Theme music | Default `DISABLED` in `AppPreference.PlayThemeMusic` and the serializer; row removed. Later removed entirely with `ThemeSongPlayer` and its proto field (clarified 2026-10-22: full fork, no installs, database restarts at v1) |

Global defaults are enough: grown-ups don't browse in Peke (R2).

## R11. Spanish

93 `kids_*` strings exist only in `values/`; `values-es` has 631 of the 773 base strings. Translate all `kids_*`
strings and the new Peke strings (shell, plugin missing, update notices), and fill the missing `values-es`
entries for every string reachable from Peke's screens (kids screens, gate, shell, Peke settings list, setup and
user list). A unit test lists base strings without an `es` entry, minus an explicit allow-list of strings not
reachable in Peke.

## R12. Expected APK size

Upstream Wholphin 1.0.9 release with MPV, FFmpeg and AV1: 46.1 MB arm64-v8a, 41.8 MB armeabi-v7a. Peke removes
~0.8–1.0 MB of translations (locale filter) and ~0.3–0.6 MB of dex (Seerr client, tvprovider, external player).
**Expected: ~44–45 MB arm64-v8a, ~40–41 MB armeabi-v7a.** This is above the owner's 35–40 MB estimate, because
MPV's native chain alone is ~29 MB plus the 2.9 MB subtitle font. Recorded, not a target (FR-028).

## R13. Autoplay and the next-up countdown (owner decision 2026-10-22)

**Decision**: autoplay comes only from the kid profile in the plugin (`AutoplayEnabled` and the cap, already
cached in `KidProfile` and applied by `KidsAutoplayPolicy`). In `PlaybackViewModel.shouldAutoPlayNextUp`, a kid
uses only `KidsAutoplayPolicy.allowNext`; Wholphin's `autoPlayNext` and `passOutProtectionMs` preferences no longer
take part (a non-kid never plays in Peke, so they return false). The rows `AutoPlayNextUp`, `AutoPlayNextDelay`,
`PassOutProtection` and `ShowNextUpTiming` are not in the Peke settings list; parents change autoplay in kids
settings (behind the gate) or on the dashboard.

The next-up countdown uses a **fixed 15 s** (`KidsAutoplayPolicy.COUNTDOWN_SECONDS`, Wholphin's default) instead
of `autoPlayNextDelaySeconds`. Making it a plugin profile field is a possible later plugin feature, not part of 003.
The cap replaces pass-out protection: it stops endless autoplay without a time-based heuristic.
