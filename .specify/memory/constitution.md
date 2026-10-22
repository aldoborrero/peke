<!--
Sync Impact Report
- Version change: 1.1.0 → 1.2.0 (2026-10-18): Principle III amended. Kids mode needs the Jellyfin
  Kids server plugin, which owns kid profiles, the household parent lock and the daily allowance;
  the device counts each sitting and caches the rest. Materially expanded guidance, so MINOR.
  Spec 002 (plugin client) and its clarifications.
- Previous: 1.0.0 → 1.1.0 (2026-10-16): Principle II amended. Kids mode is a per-profile
  mode in the regular app instead of a product flavor; isolation is by package. Isolation intent
  unchanged, so MINOR. Spec 001 clarification Q1.
- Previous: (template) → 1.0.0
- Principles defined: I. Reuse Wholphin First; II. Isolated, Upstream-Friendly Footprint;
  III. Server Decides What, Client Decides How Much; IV. Parent-Gated and Fail-Closed;
  V. TV-First, D-Pad Complete; VI. Private by Default; VII. Tested Where It Matters
- Added sections: Technical Constraints, Development Workflow, Governance
- Removed sections: none
- Templates: plan/spec/tasks templates read this file at runtime; no template edits required
- Deferred TODOs: none
-->
# Wholphin for Kids Constitution

## Core Principles

### I. Reuse Wholphin First

Kids mode MUST be built from Wholphin's existing logic and UI: its services (`ServerRepository`,
`NavigationManager`, `HomeSettingsService`, `PlaybackViewModel`, …), its Compose for TV components
(cards, rows, `NavDrawer`, `PinEntry`, dialogs) and its theme system. New code is written only when
no existing piece does the job, and a missing capability is preferably added to the shared component
(e.g. a parameter) rather than duplicated in a kids-only copy.

Rationale: a kids client that forks the UI doubles the maintenance and drifts from upstream.

### II. Isolated, Upstream-Friendly Footprint

- Kids mode is a per-profile mode of the regular app, not a separate product flavor.
  Kids-specific code MUST live in its own package (`…wholphin.kids`), not spread across existing
  packages.
- Shared code is touched only through small, explicit extension points (e.g. a Hilt-injected
  policy interface whose default implementation preserves today's behavior).
- Profiles that are not kid profiles MUST behave exactly as before in every flavor (`default`,
  `appstore`, `firetv`), and the existing test suite stays green.
- Every change to shared code MUST be acceptable to upstream on its own merits (small, readable,
  no kids-only conditionals scattered through screens).

Rationale: the fork may be proposed upstream; the diff against `main` must stay small and reviewable.

### III. Server Decides What, Client Decides How Much

- What a child can see MUST be enforced by the Jellyfin server (user policy: library access,
  maximum parental rating, block-unrated, allowed/blocked tags, access schedules). The client never
  re-implements content authorization as its only line of defense.
- Household rules that the Jellyfin Kids plugin provides (kid profiles, the parent lock, daily
  allowances, per-kid sitting limits) are decided by the server, so they hold on every TV. The
  client caches them for offline use and fails closed when they can't be read.
- The client owns what only the device can do: counting each sitting, autoplay caps, hidden
  features, kid-friendly presentation. Client-side content filtering is allowed only as an additional
  layer.
- The app MUST NOT store administrator credentials on the device.

Rationale: server policy cannot be bypassed by switching clients, and household rules set once
on the server apply to every device; presentation and the sitting itself only exist on the device.

### IV. Parent-Gated and Fail-Closed

- Every way out of the kid context MUST pass a parent gate: settings, switching to a non-kid
  profile, leaving kids mode, extending or disabling time limits, enabling search, blocking or
  unblocking content.
- When state is unknown or an error occurs (corrupt preference, unreadable policy, clock change),
  the app MUST fall back to the more restrictive behavior.
- Parent secrets (PINs/passcodes) introduced by kids mode MUST NOT be stored in plaintext.

### V. TV-First, D-Pad Complete

- Every kids screen MUST be fully operable with a D-pad remote (no touch-only interactions) and
  follow the focus behavior of Wholphin's tv-material3 components.
- Focus MUST never land by default on parent controls.
- Navigation depth from the kids home to playback is at most two levels.
- Text is never the only cue: artwork and icons carry meaning, for children who cannot read yet.

### VI. Private by Default

No analytics, ads or third-party telemetry SDKs are added. Data goes only to the user's own Jellyfin
server. Existing opt-in crash reporting stays as upstream ships it, and no new data collection about
children is introduced.

Rationale: simplest compliance stance for child-directed use (Play Families policy, COPPA, UK
Children's Code).

### VII. Tested Where It Matters

- New non-trivial logic MUST have unit tests in the existing JUnit4 / Robolectric / MockK / Hilt
  test setup. This covers timers and limits, policy decisions, gate verification and preference
  migrations.
- Room schema changes MUST have a migration test.
- The full unit test suite runs green in the Nix devshell before work is considered done.

## Technical Constraints

- Stack as in upstream: Kotlin, Jetpack Compose for TV (androidx.tv), Hilt, Navigation 3, Room,
  Proto DataStore, Media3/ExoPlayer and MPV, Jellyfin Kotlin SDK. Same `minSdk`/`compileSdk` as
  upstream.
- No new third-party dependency unless an existing library or platform API cannot reasonably do
  the job; any addition is justified in the plan.
- Formatting follows ktlint 1.8.0 as configured in `.pre-commit-config.yaml`.
- Builds and tests run through the repository's Nix devshell (`nix develop`).
- Licensing: contributions are GPLv2, like upstream.
- Inspiration from third-party apps (e.g. YouTube Kids) is limited to interaction patterns. No code,
  images, animations, sounds or copy text are copied from them.

## Development Workflow

- Work happens on `feature/kids-mode` (worktree `.worktree/kids-mode`) following the spec-kit flow:
  specify → clarify → plan → tasks → implement. Specs and plans live in `specs/`.
- Commits are small, focused and signed by the author's key.
- Any proposal to upstream follows its contribution policy, including the Jellyfin LLM/AI
  Development Policy:
  - AI/LLM assistance is disclosed.
  - The author understands and can explain every change.
  - The pull request description is written by the author without AI assistance.

## Governance

This constitution takes precedence over other practices for the kids-mode work.

- **Amendments:** a change to this file, recorded with a version bump and a sync impact note, and
  checked against open specs and plans.
- **Versioning:** semantic.
  - MAJOR: a principle is removed or redefined.
  - MINOR: a principle or section is added or materially expanded.
  - PATCH: clarifications.
- **Compliance:** every plan includes a Constitution Check, and every review verifies Principles I–IV
  explicitly. Any deviation is justified in the plan's complexity tracking.

**Version**: 1.2.0 | **Ratified**: 2026-10-16 | **Last Amended**: 2026-10-18
