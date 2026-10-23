<!--
Sync Impact Report
- Version change: 2.0.0 → 2.1.0 (2026-10-23). MINOR: Principle IV no longer gates leaving the
  app. The Home button leaves any Android TV app and can't be blocked, so Back on the kids home
  quits as in Wholphin; the gate covers every way to grown-up control instead, Peke opens no other
  app for kids, and a relaunch while locked returns to the kid. Decided by the owner after the
  first emulator test.
- Version change: 1.2.0 → 2.0.0 (2026-10-22). MAJOR: the project is no longer "kids mode inside
  Wholphin" but Peke for Jellyfin, an independent kids-only Android TV app (hard fork of Wholphin,
  github.com/aldoborrero/peke). Principles I and II are redefined, a branding/licensing principle
  is added. Spec 003 (Peke foundation).
- Modified principles:
  - I. Reuse Wholphin First → I. Kids-Only App (redefined: the product is kids-only; grown-ups
    only set the TV up)
  - II. Isolated, Upstream-Friendly Footprint → II. Lean Fork, Recognisable Core (redefined:
    deleting Wholphin code is allowed; the player and data/API layers stay recognisable for
    cherry-picks; no upstream merges; the Kotlin namespace becomes dev.aldoborrero.peke)
  - III. Server Decides What, Client Decides How Much → III. The Plugin Decides (redefined: the
    plugin 0.0.7.0+ decides and counts allowance, sitting and break across devices; the client
    only presents and caches)
  - IV. Parent-Gated and Fail-Closed (expanded: grown-up profiles get the grown-up shell only)
  - V. TV-First, D-Pad Complete (unchanged in substance)
  - VI. Private by Default (expanded: permissions minimised; crash reports never ask a child)
  - VII. Tested Where It Matters (renumbered to VIII; Room migration rule scoped per database)
- Added principles: VII. GPLv2 and Jellyfin Branding
- Added sections: none. Technical Constraints rewritten (direct play first; MPV and the Media3
  FFmpeg/AV1 decoders kept; APK size secondary) and Development Workflow rewritten for the fork.
- Removed sections: none (upstream-contribution rules in Development Workflow removed; Peke is
  not proposed upstream).
- Templates: plan/spec/tasks templates read this file at runtime; no template edits required.
- Open specs: specs/001-kids-mode and specs/002-kids-server-plugin were written under 1.x; their
  "per-profile mode, not a flavor" and "non-kid profiles behave exactly as before" requirements
  are superseded by spec 003 and Principles I–II.
- Deferred TODOs: none.
-->
# Peke for Jellyfin Constitution

## Core Principles

### I. Kids-Only App

- Peke for Jellyfin ("Peke") is an Android TV app for children, for households that run the
  Jellyfin Kids server plugin. Every profile used day to day is a kid profile.
- Grown-ups use Peke only to set the TV up: add the server, sign in kids, sign in an administrator
  for first setup or recovery, pass the parent gate, change kids settings and add time. Grown-ups
  watch with another Jellyfin client.
- A feature that serves only grown-up viewing (library management, discovery and requests, app
  settings beyond the TV setup) MUST NOT be added, and existing ones are removed or made
  unreachable.

Rationale: one audience keeps the app small, the attack surface for curious children narrow, and
every screen designed for a child.

### II. Lean Fork, Recognisable Core

- Peke is an independent hard fork of Wholphin. There are no regular upstream merges; playback
  and Jellyfin SDK fixes are cherry-picked when needed.
- Wholphin code that Peke does not need MAY be deleted. Deleting is preferred over hiding behind
  flags when the code is unreachable for kids and costs size, build time or review effort.
- The player (`ui/playback`, `PlayerFactory`, device profiles, stream choice) and the data/API
  layers (Jellyfin SDK wiring, `ServerRepository`, `data/`, Room/DataStore code) MUST stay
  recognisable: keep their file layout and the shape of their classes, so upstream fixes can still
  be ported. The Kotlin namespace and packages are Peke's own (`dev.aldoborrero.peke.*`) and
  classes carry no Wholphin name; porting a fix means moving it to the renamed paths. The one
  exception is the API of the external `wholphin-mpv` library (`com.github.damontecres.wholphin.mpv.*`,
  mirrored by the `wholphin-mpv-stub` module), which its native code binds by name.
- Peke's own data lives apart from Wholphin's: kids tables in their own Room database, kids code
  in the `dev.aldoborrero.peke.kids` package.

Rationale: merging upstream every week costs more than it gives a kids app, but upstream's player
and SDK fixes are the main reason to start from Wholphin.

### III. The Plugin Decides

- The Jellyfin Kids server plugin (0.0.7.0 or later) is the source of truth for household rules:
  kid profiles, the parent lock, the household code, daily allowances, sitting limits, breaks,
  picks and live TV channels. The client caches them for offline use and fails closed when they
  cannot be read.
- The server decides and counts everything time-related: the plugin counts allowance, sitting and
  break per kid across devices. The client presents them and caches the last known state; it MUST
  NOT keep its own authoritative count.
- What a child can see MUST be enforced by the Jellyfin server (user policy: library access,
  maximum parental rating, block-unrated, tags, access schedules). Client-side filtering is
  allowed only as an additional layer.
- The client owns presentation only: showing time left, time's-up and break screens, autoplay
  behavior, hidden features and kid-friendly layout.
- The app MUST NOT store administrator credentials on the device.

Rationale: rules and time set once on the server hold on every TV and cannot be bypassed by
switching TVs or clients; only presentation exists on the device.

### IV. Parent-Gated and Fail-Closed

- Every way from the kid context to grown-up control MUST pass a parent gate: settings, switching
  to a non-kid profile or another user, extending or disabling time limits, enabling search,
  blocking or unblocking content, leaving kids mode.
- Leaving the app is not gated. The remote's Home button leaves any Android TV app and cannot be
  blocked, so Back on the kids home quits as in Wholphin. What MUST hold instead: Peke opens no
  other app itself (no external links or remote trailers for kids), and relaunching Peke while
  the TV is locked returns to the kid. Whatever lies outside Peke is the TV's job (for example a
  Google TV kids profile).
- A grown-up profile gets only the grown-up shell: kids settings, a short "This TV" settings group,
  the user list and the license screen. It MUST NOT reach browsing, playback or any other
  Wholphin screen.
- When state is unknown or an error occurs (corrupt preference, unreadable policy, clock change,
  missing plugin), the app MUST fall back to the more restrictive behavior.
- Defaults are the kid-safe value: no cinema-mode pre-rolls, no theme music, no debug or advanced
  playback options, no external apps.
- Parent secrets (PINs, passcodes) MUST NOT be stored in plaintext.

### V. TV-First, D-Pad Complete

- Every screen MUST be fully operable with a D-pad remote (no touch-only interactions) and follow
  the focus behavior of the tv-material3 components.
- Focus MUST never land by default on parent controls.
- Navigation depth from the kids home to playback is at most two levels.
- Text is never the only cue: artwork and icons carry meaning, for children who cannot read yet.

### VI. Private by Default

- No analytics, ads or third-party telemetry SDKs. Data goes only to the household's own
  Jellyfin server.
- The app requests only the permissions a kids TV app needs; every permission in the merged
  manifest is justified in the plan that keeps or adds it.
- Crash reports go only to the household's Jellyfin server and never ask a child to decide.
- No new data collection about children is introduced.

Rationale: simplest compliance stance for child-directed use (Play Families policy, COPPA, UK
Children's Code).

### VII. GPLv2 and Jellyfin Branding

- Peke is licensed GPLv2-only, like Wholphin. Wholphin's credits, the git history and the
  open-source license screen MUST be kept, and the license screen MUST stay reachable from the
  grown-up shell.
- The product name is "Peke for Jellyfin" (short "Peke"), following Jellyfin's branding rules:
  "X for Jellyfin" only, Peke's own logo, never the Jellyfin logo as the app icon, and nothing
  implying an official Jellyfin app.
- Peke has its own identity on the device: its own `applicationId`, deep-link scheme, intent
  actions, and Jellyfin client and device names, so it never collides with Wholphin or another
  client.
- Inspiration from third-party apps (e.g. YouTube Kids) is limited to interaction patterns. No
  code, images, animations, sounds or copy text are copied from them.

### VIII. Tested Where It Matters

- New non-trivial logic MUST have unit tests in the existing JUnit4 / Robolectric / MockK / Hilt
  setup: timers and limits, policy decisions, gate verification, preference defaults and
  migrations.
- A schema change to either Room database MUST have a migration test, and each database keeps its
  own exported schema history.
- When Wholphin code is deleted, its tests are deleted with it; the remaining suite stays green.
- Build, unit tests and ktlint on changed files run green in the Nix devshell before work is
  considered done.

## Technical Constraints

- Stack: Kotlin, Jetpack Compose for TV (androidx.tv), Hilt, Navigation 3, Room, Proto DataStore,
  Media3/ExoPlayer with the Media3 FFmpeg and AV1 decoders, MPV, Jellyfin Kotlin SDK. Same
  `minSdk`/`compileSdk` as the Wholphin base unless a plan justifies a change.
- Direct play first: playback MUST prefer direct play and avoid server transcoding wherever the
  device can decode the media, using the bundled decoders and backends to do so. Playback
  capability outranks APK size.
- No new third-party dependency unless an existing library or platform API cannot reasonably do
  the job; any addition is justified in the plan. Unused dependencies are removed, and test
  libraries never sit on `implementation`.
- APK size is tracked but secondary to playback capability: every plan that touches dependencies, assets or native libraries
  states the expected release APK size per ABI, and the measured size is recorded when the work
  is done.
- Formatting follows ktlint 1.8.0 as configured in `.pre-commit-config.yaml`.
- Builds and tests run through the repository's Nix devshell (`nix develop -c ./gradlew …`).
- Supported UI languages are chosen deliberately; every user-facing string ships in each of them.

## Development Workflow

- Work follows the spec-kit flow: specify → clarify → plan → tasks → implement. Specs and plans
  live in `specs/`.
- Commits are small, focused and signed by the author's key. Agents prepare changes; the author
  commits them.
- Upstream fixes are brought in by cherry-pick, with the upstream commit referenced in the message.

## Governance

This constitution takes precedence over other practices for Peke.

- **Amendments:** a change to this file, recorded with a version bump and a sync impact note, and
  checked against open specs and plans.
- **Versioning:** semantic.
  - MAJOR: a principle is removed or redefined.
  - MINOR: a principle or section is added or materially expanded.
  - PATCH: clarifications.
- **Compliance:** every plan includes a Constitution Check, and every review verifies Principles
  III, IV and VII explicitly. Any deviation is justified in the plan's complexity tracking.

**Version**: 2.1.0 | **Ratified**: 2026-10-16 | **Last Amended**: 2026-10-23
