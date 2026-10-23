# Feature Specification: Peke foundation

**Feature Branch**: `003-peke-foundation`

**Created**: 2026-10-22

**Status**: Draft

**Input**: User description: "Feature 003 Peke foundation: turn the Wholphin fork into Peke for Jellyfin, a
kids-only Android TV app (identity, build trim keeping MPV/FFmpeg/AV1/updater, own kids Room DB, leak fixes,
grown-up shell, Spanish kids strings). See GOAL.md plus the updated build scope."

Sources: `GOAL.md` (feature 003 scope) with the updated build scope from the owner (2026-10-22): the updater, MPV
and the Media3 FFmpeg and AV1 decoders are **kept**; only the external player is dropped; there is no 10–11 MB size
goal. Study: `reports/Wholphin Kids version reducida.md` and its notes (`features_and_screens.md`,
`build_deps_size.md`). Constitution 2.0.0.

Builds on [001-kids-mode](../001-kids-mode/spec.md) and [002-kids-server-plugin](../002-kids-server-plugin/spec.md).
Their kids experience (kids home, parent gate, time limits from the plugin, calmer playback) is unchanged. What this
feature supersedes from them: kids mode is no longer a per-profile mode inside a general-purpose app, and non-kid
profiles no longer get the regular Wholphin experience. The screen redesign is a later feature.

## Clarifications

### Session 2026-10-22

- Q: Should kids get the microphone and voice search? → A: Keep voice search, shown only where search is allowed
  for that kid, using the system speech recogniser. `RECORD_AUDIO` and the speech `<queries>` stay and are
  justified in the plan (constitution VI).
- Q: Which artwork goes on the launcher icon and TV banner? → A: Keep Wholphin's current icon and banner art for
  now; only the app label changes to "Peke". This is temporary: constitution VII's own-logo rule is met in a later
  feature that brings the Peke art.
- Q: How should Peke number its versions and tag its releases? → A: Peke's own semantic versioning with plain `v*`
  tags from `v0.1.0`; Wholphin's inherited tags are deleted (locally and on the peke remote) by the owner, not by
  an agent. The first release is 0.1.0 with version code 1, and the updater compares against these versions.
- Q: How will Peke be distributed for now? → A: Sideload only, from GitHub releases with the in-app updater, as one
  build for Google TV, Android TV and Fire TV.
- Q: Should grown-ups be able to change player and other app options? → A: Yes: grown-ups can make all fine app
  settings that make sense in Peke, including the playback switches (backend ExoPlayer / MPV / prefer MPV, FFmpeg
  and AV1 decoders, max bitrate and resolution, refresh-rate switching). The plan evaluates reusing Wholphin's
  Settings screen, shown only in the grown-up shell and pruned of groups that make no sense in Peke, against a new
  "This TV" section, and recommends one.
- Defaults accepted with these answers: the admin may stay signed in on the TV and reaches only the grown-up
  shell; crash reports go only to the household server with no dialog; music videos and home videos stay for kids
  while audio, people and photo items are removed; the app database restarts at version 1 with only the
  tables Peke uses, no Seerr tables (clarified 2026-10-22: full fork, no installs, database restarts at v1); the
  screensaver, kids live TV, Quick Connect sign-in and the media session stay.
- Settings list decisions (owner, 2026-10-22): remove Customize home and the nav drawer pins (the plugin profile
  owns the kids home; Peke owns the nav); keep "Show playback debug info" in the grown-up settings (when on, the
  overlay shows during kid playback, to diagnose direct play vs transcoding) but remove the in-player toggle kids can
  reach; autoplay comes only from the kid profile in the plugin (Wholphin's autoplay, autoplay delay and pass-out
  protection settings are removed; the next-up countdown is a fixed 15 s); keep Quick Connect "authorize another
  device" in the grown-up settings only; keep the Experimental section with all its items.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Peke installs and runs as its own app (Priority: P1)

A parent installs Peke on a TV that may already have Wholphin or another Jellyfin client. Peke shows up in the
launcher as "Peke" (with Wholphin's icon and banner art until the Peke art lands in a later feature). It installs next to Wholphin without replacing it, and both can be
signed in to the same Jellyfin server at the same time without signing each other out. The Jellyfin dashboard lists
Peke's sessions and devices as Peke.

**Why this priority**: nothing else can ship until Peke has its own identity on the device and on the server.

**Independent Test**: install Peke and Wholphin on one TV, sign both in to one server, check the launcher, the
dashboard's devices and sessions, and that neither app's session is revoked.

**Acceptance Scenarios**:

1. **Given** a TV with Wholphin installed, **When** Peke is installed, **Then** both apps are present and
   launchable and distinguishable by their labels ("Peke" vs "Wholphin"; the art is shared for now).
2. **Given** Peke and Wholphin signed in to one server on one TV, **When** either app signs in or plays, **Then**
   the other stays signed in, and the dashboard shows two devices for that TV: one with client "Peke for
   Jellyfin", one with Wholphin.
3. **Given** a link or intent addressed to Wholphin's scheme or actions, **When** it is opened, **Then** Peke does
   not handle it; Peke answers only its own `peke://` scheme and its own actions.
4. **Given** the launcher shows apps with short labels, **When** Peke is listed, **Then** its label is "Peke", and
   screens with room (about, license, setup welcome) say "Peke for Jellyfin".

---

### User Story 2 - A kid can't reach anything outside the kids world (Priority: P1)

A child using Peke can watch, browse their kids home, favourites, live TV and (when allowed) search. They cannot
reach player debug information or advanced playback options, music, people or photos, and they don't get
trailers before a movie or theme music on a collection.

**Why this priority**: these are the leaks the study found; a kids-only app that leaks is worse than kids mode
inside Wholphin.

**Independent Test**: as a kid profile, try every path listed in the study's reachability audit (player settings
dialog, Favorites tabs, search results, home rows, collections) and confirm each blocked item is unreachable.

**Acceptance Scenarios**:

1. **Given** a kid is watching, **When** they open the player's settings, **Then** only audio and subtitle choices
   (and other kid-safe items) are listed; "Show debug info", video scale, subtitle delay and playback speed are not
   listed at all, and the debug overlay appears only if a grown-up turned on "Show playback debug info" in the
   grown-up settings.
2. **Given** a kid's account has favourite music, artists, people or photos on the server, **When** the kid opens
   Favorites or searches, **Then** only video items (movies, series, episodes, collections and playlists of those,
   and channels for live TV) are shown.
3. **Given** a home-videos library contains photos or photo albums, **When** the kid browses its row, **Then**
   photos and photo albums are not shown, and no item leads to an "unsupported item" screen.
4. **Given** the server has cinema intros or trailers configured, **When** a kid starts a movie, **Then** it starts
   directly, with no pre-roll.
5. **Given** a collection has theme music, **When** a kid opens it, **Then** no theme music plays.

---

### User Story 3 - Grown-ups get a setup shell, not a second app (Priority: P1)

A parent opens the parent gate, or signs in with their own (non-kid) account to set up the TV. Instead of the full
Wholphin experience, they get the grown-up shell: kids settings, the app settings that make sense in Peke (the
"grown-up settings"), the user list (to sign kids in, switch to a kid or sign out) and the license screen. From there they can do everything needed to
set up the TV and nothing else.

**Why this priority**: "Leave" in the gate followed by a grown-up profile currently opens full Wholphin, which a
child who learns the code can use to escape every rule.

**Independent Test**: from the gate choose "Leave" and pick a grown-up profile; from a fresh install sign in an
administrator. In both cases, confirm only the shell is reachable and that every setup task can be completed.

**Acceptance Scenarios**:

1. **Given** a parent in the gate, **When** they choose "Leave" and pick a non-kid profile, **Then** they land in
   the grown-up shell, and no browse, details, search or playback screen is reachable from it.
2. **Given** a fresh install, **When** a grown-up adds the server and signs in an administrator, **Then** they land
   in the grown-up shell and can set the household code, mark kids, sign kids in and switch to a kid.
3. **Given** the grown-up shell, **When** the parent opens the grown-up settings, **Then** they find every app
   setting that makes sense in Peke (see FR-033), including the playback switches, and no Seerr, external-player
   or grown-up viewing customization.
4. **Given** the grown-up shell, **When** the parent opens the license screen, **Then** it lists Peke's and
   Wholphin's credits and every bundled library's license.
5. **Given** a server without the Jellyfin Kids plugin (or with a version too old), **When** a grown-up signs in,
   **Then** the shell explains that Peke needs the plugin, and no kid can use that server.

---

### User Story 4 - Peke updates itself from its own releases (Priority: P2)

A parent who sideloaded Peke is told when a new Peke release is available and can install it from the grown-up
shell. The update always comes from Peke's own releases, never from Wholphin's.

**Why this priority**: sideloaded TVs otherwise never update; pointing at Wholphin would replace the kids app with
a grown-up app.

**Independent Test**: publish (or simulate) a Peke release with a newer version, check from the grown-up settings, install it;
confirm no request reaches Wholphin's release feed.

**Acceptance Scenarios**:

1. **Given** a newer Peke release exists, **When** a grown-up checks for updates in the grown-up settings, **Then** the new
   version and its notes are shown and the matching APK for the device's ABI can be installed.
2. **Given** a kid is using Peke, **When** an update is available, **Then** the kid sees no update prompt or toast.
3. **Given** any build of Peke, **When** its update source is inspected, **Then** it points at Peke's own releases,
   and nothing in the app targets Wholphin's release feed.

---

### User Story 5 - Playback keeps Wholphin's capability (Priority: P2)

Kids content plays directly (no server transcoding) whenever the TV plus Peke's bundled decoders and player
backends can handle it, exactly as Wholphin's full build does. Peke keeps MPV and the FFmpeg and AV1 decoders.

**Why this priority**: direct play over transcoding is a stated household constraint; it outranks APK size.

**Independent Test**: play a set of reference files (H.264/AAC, HEVC/AC3, AV1, DTS or TrueHD audio, ASS subtitles)
on a test TV and compare the play method reported by the Jellyfin dashboard with Wholphin's release build.

**Acceptance Scenarios**:

1. **Given** a reference file that Wholphin's full release direct-plays on a TV, **When** a kid plays it in Peke on
   the same TV, **Then** Peke direct-plays it too.
2. **Given** a release build of Peke, **When** its contents are inspected, **Then** the MPV backend and the FFmpeg
   and AV1 decoders are included, and the external player option is absent.

---

### User Story 6 - A Spanish-speaking household sees Peke in Spanish (Priority: P2)

On a TV set to Spanish, every screen a kid or a grown-up sees in Peke is in Spanish, including the kids screens
and the grown-up shell.

**Why this priority**: the household is Spanish-speaking; kids strings exist only in English today.

**Independent Test**: set the TV (or Peke's language) to Spanish and walk through the kids home, the gate, time's
up, the grown-up shell and kids settings; no English text appears.

**Acceptance Scenarios**:

1. **Given** a TV in Spanish, **When** any kids screen or the grown-up shell is shown, **Then** all its text is in
   Spanish.
2. **Given** Peke's language list, **When** a grown-up picks a language, **Then** only English and Spanish are
   offered.

---

### User Story 7 - Maintainers build a lean, correct Peke (Priority: P3)

The maintainer builds Peke with one product variant, gets a release that contains only what a kids TV app needs
(no Seerr/discover, no launcher channels, no unused permissions, no test libraries), and knows its size.

**Why this priority**: it reduces what can leak, what must be reviewed and what each cherry-pick must touch.

**Independent Test**: build debug and release, run unit tests, inspect the merged manifest, the dependency list and
the APK contents, and record the size per ABI.

**Acceptance Scenarios**:

1. **Given** the source tree, **When** the maintainer lists the build variants, **Then** there is a single product
   (debug and release), not default/appstore/firetv.
2. **Given** a release APK, **When** its merged manifest is inspected, **Then** it contains none of the removed
   permissions, components or queries listed in FR-020.
3. **Given** a release APK, **When** its classes are inspected, **Then** no Seerr/discover, launcher-channel or
   test-library code is present.
4. **Given** CI without the maintainer's personal credentials, **When** it builds a release, **Then** it fetches
   MPV and the decoders and the APK includes them.

---

### Edge Cases

- **Extension credentials missing locally**: a build without the extensions credentials can't include MPV and the
  decoders. A debug build may fall back (stub, no decoders) with a clear warning; a release build MUST fail rather
  than silently ship without them.
- **Same signing key as Wholphin**: if Peke and Wholphin share the device-level identifier the Jellyfin SDK derives
  per signing key, they must still not collide on the server (FR-006).
- **Server without the plugin, or plugin older than required**: no kid can use it; grown-ups see why in the shell.
- **Plugin removed after setup**: fail closed as in 002 (kids stay locked; grown-ups see the shell and the reason).
- **User or server removed**: their kids data is removed too, even though it now lives in a separate database.
- **Kids database unreadable or corrupt**: treated as unknown state; Peke fails closed and rebuilds the cache from
  the plugin. While the device's kids lock is on, a user without a cached kid row gets the kids-only user list
  (the gate is the only way out), never the grown-up shell.
- **Upgrade from a dev build that had kids tables in the main database**: not supported (new applicationId; no
  released data exists). Debug builds may need a reinstall.
- **Deep link or launcher intent while a kid is locked in**: ignored, as today.
- **Update check while offline or rate-limited**: fails quietly for the grown-up with a retry; never shows anything
  to a kid.
- **A grown-up picks MPV, or a file needs MPV**: playback works, since MPV ships (unlike the current stub builds).
- **A kid favourite of a now-hidden type** (song, person, photo): silently omitted, not an error.

## Requirements *(mandatory)*

### Functional Requirements

**Identity**

- **FR-001**: Peke MUST install as its own application (`dev.aldoborrero.peke`), so it can coexist with Wholphin
  and any other client on the same device. Debug builds MUST also coexist with release builds.
- **FR-002**: The launcher label MUST be "Peke"; screens with room MUST use "Peke for Jellyfin".
- **FR-003**: In this feature Peke keeps Wholphin's current launcher icon and TV banner art; only the label
  changes. This is a recorded, temporary deviation from constitution VII's own-logo rule, closed by a later feature
  that brings the Peke art. Peke MUST NOT use Jellyfin's logo and MUST follow Jellyfin's naming rules.
- **FR-004**: Peke MUST answer only its own deep-link scheme (`peke://`) and its own custom intent actions; it MUST
  NOT register Wholphin's scheme or actions.
- **FR-005**: Peke MUST identify itself to Jellyfin with the client name "Peke for Jellyfin"; the device name stays
  the TV's own name, so the dashboard shows "Peke for Jellyfin" on that TV.
- **FR-006**: Peke's device identifier sent to Jellyfin MUST differ from Wholphin's on the same TV, so neither app's
  sign-in revokes the other's sessions.
- **FR-007**: Wholphin's credits, the git history and the open-source license screen MUST be kept; the license
  screen MUST name Peke as a fork of Wholphin and be reachable from the grown-up shell.

**Kids-only experience and grown-up shell**

- **FR-010**: Every everyday profile is a kid profile; Peke MUST NOT offer the regular browsing experience to any
  profile.
- **FR-011**: A non-kid profile (including an administrator signed in for setup or recovery, and the target of
  "Leave" in the gate) MUST land in the grown-up shell, unless the device's kids lock is on or unknown, in which
  case it MUST get the kids-only user list instead. The shell offers only: kids settings, the grown-up settings
  (FR-033), the user list (sign in, switch user, sign out, add server) and the license screen.
- **FR-012**: From the grown-up shell, no browsing, details, search, playback, Seerr/discover, music, photos or
  app-wide settings screen MUST be reachable.
- **FR-013**: When the server lacks a usable Jellyfin Kids plugin, meaning it doesn't answer or doesn't advertise
  the required features (profiles, parent lock, enforcement, sitting; plugin 0.0.7.0 is the supported baseline),
  Peke MUST NOT let any kid use that server and MUST tell a grown-up why in the shell.
- **FR-014**: Kids MUST NOT reach: the player debug-info toggle, video scale, subtitle delay, playback speed or
  any other advanced playback option; music (albums, artists, songs, music playback started from items), people
  pages, photos and photo albums or slideshows; external players; app settings.
- **FR-014b**: The player debug overlay MUST show during kid playback only when a grown-up has turned on "Show
  playback debug info" in the grown-up settings; kids MUST NOT be able to switch it.
- **FR-017a**: Autoplay of the next episode MUST follow only the kid profile from the plugin (autoplay on/off and
  the cap); Wholphin's own autoplay, autoplay-delay and pass-out-protection settings MUST NOT apply, and the
  next-up countdown is a fixed 15 seconds.
- **FR-014a**: Voice search MUST be offered to a kid only where search is allowed for that kid, and MUST use the
  system speech recogniser; the microphone permission and the speech-recogniser query are kept for this purpose
  only.
- **FR-015**: Favorites and search for kids MUST list only video item types and containers of them (movies, series,
  episodes, videos, music videos, collections, playlists, live TV channels when the plugin allows them).
- **FR-016**: Cinema mode (pre-roll intros and trailers) MUST be off for Peke, and theme music MUST be off.
- **FR-017**: All existing parent-gate, lock and time-limit behavior from specs 001/002 MUST keep working
  unchanged, with time counted by the plugin (constitution III).

**Build and distribution**

- **FR-020**: The release build MUST NOT contain: the `POST_NOTIFICATIONS` permission, launcher-channel (EPG)
  permissions and code, the external-player `<queries>` entry and the external player itself, Seerr/discover code
  and its generated client. The DASH module stays, because the direct-play device profile uses it (research R8). The permissions and components the updater
  needs (install packages, its file provider, and storage only where the updater needs it on old Android versions)
  stay, justified in the plan.
- **FR-021**: The release build MUST include the MPV backend and the Media3 FFmpeg and AV1 decoders, fetched from
  the Wholphin extensions package feed with credentials supplied by Gradle properties or `ORG_GRADLE_PROJECT_*`
  environment variables. A release build without them MUST fail.
- **FR-022**: The updater MUST check and download only Peke's own releases (github.com/aldoborrero/peke), match
  Peke's own release asset names, and nothing in the app MUST target Wholphin's release feed or repository.
- **FR-023**: Update notices and the install flow MUST only appear in the grown-up shell, never to a kid.
- **FR-024**: Peke MUST be built as a single product (debug and release), without the default/appstore/firetv
  split, installable by sideloading on Google TV, Android TV and Fire TV (leanback not required).
- **FR-024a**: Peke MUST use its own semantic versions from plain `v*` tags starting at `v0.1.0`: the version name
  comes from the latest such tag and the version code counts them (0.1.0 → code 1). Wholphin's inherited tags are
  deleted by the owner (locally and on the peke remote) before the first release, and fetching from Wholphin MUST
  NOT bring them back. The updater compares release versions on this scheme.
- **FR-025**: The shipped UI languages MUST be English and Spanish only.
- **FR-026**: Test-only libraries MUST NOT be packaged in the app, and dependencies with no use MUST be removed.
- **FR-027**: CI MUST build Peke with MPV and the decoders without anyone's personal token, and MUST publish
  release assets named as the updater expects.
- **FR-028**: The release APK size per ABI MUST be measured and recorded (estimated ~44–45 MB arm64-v8a and
  ~40–41 MB armeabi-v7a with MPV, research R12); there is no size target beyond recording it.

**Data**

- **FR-030**: Kids data (kid profiles, per-server plugin state) MUST live in its own local database, separate from
  the app's other data, with its own schema history and migration tests.
- **FR-031**: Removing a user or a server MUST remove its kids data, as the shared-database cascade does today.
- **FR-032**: No migration from a Wholphin install or from earlier dev builds is required.

**Grown-up settings**

- **FR-033**: The grown-up settings, reachable only from the grown-up shell, MUST offer every app setting that makes
  sense in Peke, at least: interface language (English/Spanish), subtitle style, screensaver, the playback switches
  (player backend ExoPlayer / MPV / prefer MPV, FFmpeg decoder, AV1 software decoding, maximum bitrate and
  resolution, refresh-rate and resolution switching), "Show playback debug info", Quick Connect "authorize another
  device", the Experimental section, check for updates, installed version and the license screen. They MUST NOT
  offer Seerr/discover, the external player, home customization or nav drawer pins, Wholphin's autoplay, autoplay
  delay or pass-out protection, or other settings that only serve grown-up viewing. Whether this reuses Wholphin's Settings screen or is a new
  section is decided in the plan.

**Translations**

- **FR-040**: Every kids string and every string new to Peke (grown-up shell, plugin-missing message, update
  notices) MUST have a Spanish translation.

### Key Entities

- **Kids database**: the device's cache of kids state from the plugin: kid profiles (per signed-in user: search,
  autoplay, picks, sections, limits, server revision) and per-server plugin state (plugin info, features, lock
  data). Owned by Peke, independent of the app database; keyed by the same user and server identifiers.
- **App database**: Wholphin's existing local store (servers, users, playback state, preferences tables). Left
  structurally recognisable for cherry-picks.
- **Peke release**: a GitHub release of aldoborrero/peke with a version and one APK per ABI plus a universal APK,
  named so the updater can pick the right one.
- **Peke identity**: application id, launcher label, icon/banner, URI scheme, intent actions, Jellyfin client name,
  device name and device identifier.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Peke and Wholphin both stay signed in to the same server on the same TV through 10 alternating
  sign-ins and playbacks, with zero forced sign-outs.
- **SC-002**: In a walk-through of every path in the study's reachability audit, a kid reaches 0 of the blocked
  items (the debug-info toggle, advanced playback options, music, people, photos, cinema pre-rolls, theme music,
  external player).
- **SC-003**: From the grown-up shell, 0 browse, details, search or playback screens are reachable, and every setup
  task (add server, set household code, mark a kid, sign a kid in, add time, change kids settings) can be completed
  without another client, except where the plugin requires the dashboard.
- **SC-004**: Every reference file that Wholphin's full release direct-plays on the test TV is also direct-played by
  Peke (100% parity).
- **SC-005**: With the TV in Spanish, 0 English strings appear on kids screens and in the grown-up shell.
- **SC-006**: The build, the unit test suite and ktlint on changed files pass; the release APK size per ABI is
  recorded.
- **SC-007**: An update published to Peke's releases is found and installed from the grown-up shell, and 0 network
  requests from Peke go to Wholphin's release feed.

## Assumptions

- Plugin 0.0.7.0 or later is the supported baseline; the client checks the plugin's advertised features (profiles,
  parent lock, enforcement, sitting) as it does today.
- Distribution is by sideloading GitHub releases; no app store listing is planned in this feature (clarified).
- Music videos and home videos stay available to kids as video; only audio, people and photo items are removed.
- Live TV for kids, the screensaver, Quick Connect sign-in and the media session (remote control from a phone) stay.
- Crash reports keep going only to the household's own server; the crash dialog is never shown to a kid.
- Wholphin's database is left as is; Seerr tables may stay as unused tables rather than adding a schema change.
- The Kotlin namespace and packages become `dev.aldoborrero.peke.*` and Wholphin-named classes are renamed (`PekeApplication`, `PekeTheme`, …). Only the external `wholphin-mpv` API (`com.github.damontecres.wholphin.mpv.*`) and the extensions' Maven coordinates keep their names (clarified 2026-10-22: Peke is a full fork).
- The screen redesign of the kids UI is out of scope; screens keep their current look.
- The constitution's Development Workflow applies: nothing is committed by agents; the owner signs commits.
