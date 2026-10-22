# Feature Specification: Wholphin for Kids (kids mode v1)

> **Superseded in part (2026-10-18)**: kids mode is only offered on servers with the Jellyfin Kids plugin. Kid
> profiles and the parent lock now live on the server, and the device only caches them. The arithmetic challenge
> lock is gone, and daily and sitting limits come from the plugin. See
> [002-kids-server-plugin](../002-kids-server-plugin/spec.md). The kids experience described here is unchanged.

**Feature Branch**: `feature/kids-mode`

**Created**: 2026-10-16

**Status**: Draft

**Input**: User description: "A simple, kid-friendly Wholphin client for Android TV, in the spirit of YouTube Kids. Reuse Wholphin's existing logic and UI as much as possible; adopt selected YouTube Kids interaction patterns (parent gate, single lock entry point, screen-time timer, shallow navigation) without making the UI overly childish. Content restrictions are enforced by the Jellyfin server; the app adds time limits, hidden features and a parent gate. Keep the kids code as separate as possible so it could be proposed upstream."

## Clarifications

### Session 2026-10-16

- Q: Is Wholphin for Kids a separate app or a mode inside Wholphin? → A: A per-profile kids mode
  inside the regular Wholphin app. Adult profiles keep the full UI.
- Q: What is the strong parent verification? → A: A new kids-mode parent passcode (≥4 symbols) set
  during kids setup and stored as a non-reversible verifier, independent of profile PINs.
- Q: Timer scope in v1? → A: Per-session timer only. Daily allowance and bedtime are deferred, and
  allowed hours rely on the server access schedule.
- Q: Which sections does the kids home show? → A: Keep watching, Picked by your grown-ups
  (parent-chosen collections/playlists, in v1), one row per library (alphabetical), New, and My
  favourites. Empty rows are hidden, rows are capped at one page, and there are no algorithmic
  suggestions.

## User Scenarios & Testing *(mandatory)*

Actors:

- **Parent**: the adult who installs and configures the app. Has access to a non-kid Jellyfin account or knows the parent passcode.
- **Child**: watches content using a kid profile and cannot read reliably (ages ~3–10).
- **Jellyfin administrator**: often the same person as the parent. Configures content restrictions for kid accounts on the server.

### User Story 1 - Child watches only from a simplified kids home (Priority: P1)

A child picks up the remote, the TV opens straight into a simplified home for their profile, and
they can browse and play only the content the server allows for their account. They can't reach
settings, other users, server management or any non-kids feature.

**Why this priority**: This is the core value: a safe, simple place for a child to watch. Without
it nothing else matters.

**Independent Test**: Mark a Jellyfin user as a kid profile on the device and launch the app. Verify
that it opens into the kids home, that every reachable screen is within the kids experience, and
that content shown matches what the server returns for that user.

**Acceptance Scenarios**:

1. **Given** a device with a kid profile set up and the app last used by that profile, **When** the
   app is launched, **Then** it opens directly on the kids home for that profile without showing
   the server or user selection screens.
2. **Given** the kids home is shown, **When** the child navigates with the D-pad, **Then** only
   these entries are reachable: Home, Continue/Favourites ("My stuff") and, if enabled by the
   parent, Search. Settings, Discover/requests, Live TV/DVR, server or user switching, and app
   updates are absent.
3. **Given** the child opens any item, **When** they look at its actions, **Then** delete, add to
   playlist, mark as watched or unwatched, play with an external player, download subtitles and
   file-path details are not offered.
4. **Given** a kid account whose server policy hides a library or a rating level, **When** the
   kids home loads, **Then** no item from the restricted content appears in any row, search result
   or detail page.
5. **Given** the kids home, **When** the child selects an item, **Then** playback starts after at
   most two navigation steps (row item → details → play, or row item → play).
6. **Given** a kid profile with watch progress, parent-picked collections, two accessible libraries
   and favourites, **When** the kids home loads, **Then** it shows, in this default order: Keep
   watching, Picked by your grown-ups, one row per library (titles sorted by name), New, and My
   favourites. Each row shows at most one page of items and has no "view more" entry.
7. **Given** a brand-new kid profile with no progress, favourites or picked collections, **When**
   the kids home loads, **Then** empty sections are not shown and the first visible row is a
   library row.

---

### User Story 2 - Parent gate guards every way out (Priority: P1)

Whenever something would take the child out of the kids experience or change its rules, the app
asks for parent verification first. A light challenge covers everyday actions; a stronger secret
covers changes to the rules.

**Why this priority**: Without the gate the kids home can be escaped with one click, so story 1's
value disappears.

**Independent Test**: From the kids home, try each protected action (open parent menu, leave kids
mode, switch to another profile, change rules, extend time). Verify each is blocked until the
correct verification is provided, and that a wrong answer neither grants access nor locks the
parent out.

**Acceptance Scenarios**:

1. **Given** the kids home, **When** the child presses the system Back key at the top level,
   **Then** the app does not exit to the launcher or another profile without passing the parent
   gate. It either stays on the kids home or shows the gate.
2. **Given** the kids home, **When** the user selects the lock entry, **Then** the parent passcode is
   asked with Wholphin's profile PIN entry (D-pad directions and the remote's number keys).
3. **Given** the passcode entry, **When** a wrong passcode is submitted, **Then** the entry clears, a
   short "wrong passcode" message is shown, and no access is granted.
4. **Given** the passcode is accepted, **When** the parent menu opens, **Then** it offers: set or
   stop the session timer, add time, leave kids mode, and edit kids settings.
5. **Given** kids mode, **When** any parent action is requested, **Then** the same parent passcode
   guards it. It has at least 4 symbols, is created during kids setup and is independent of any
   profile PIN.
6. **Given** a parent profile that is protected by a PIN or login in Wholphin today, **When** the
   user tries to switch to it from kids mode, **Then** that protection is still enforced in
   addition to the kids gate.

---

### User Story 3 - Parent sets up a kid profile (Priority: P1)

A parent marks one or more of the device's Jellyfin users as kid profiles and decides how the app
behaves for them. The content rules themselves stay on the server, and the app explains where to
set them.

**Why this priority**: Stories 1–2 need a kid profile to exist. Setup is the entry point for every
family.

**Independent Test**: Starting from a normal installation with at least two Jellyfin users,
complete kids setup for one user. Verify that user now opens in kids mode and the other keeps the
full Wholphin UI.

**Acceptance Scenarios**:

1. **Given** a parent signed in on the device, **When** they open kids setup, **Then** they can
   choose which signed-in users are kid profiles and set the strong parent secret if none exists.
2. **Given** kids setup, **When** a user is marked as a kid profile, **Then** the app shows a short
   checklist of the server-side restrictions that are recommended for that account: library
   access, maximum parental rating, block unrated items, and allowed hours. It also shows the
   current server values for that account as far as the account's own access can read them.
3. **Given** a kid account whose server policy has no maximum parental rating and allows unrated
   items, **When** setup is completed, **Then** the app clearly warns that the server is not
   restricting content for that account. The setup still completes.
4. **Given** the user switcher, **When** kids mode is active, **Then** only kid profiles are
   listed. Non-kid profiles are reachable only through the parent gate.
5. **Given** kids settings for a kid profile, **When** the parent selects one or more collections
   or playlists for "Picked by your grown-ups" and moves "New" above the library rows, **Then**
   the kids home shows those picks in that section and the new order on next load.

---

### User Story 4 - Session timer with a gentle stop (Priority: P2)

The parent starts a timer for the session. The child sees remaining time ambiently, gets a
friendly warning before the end, and when time is up playback stops on a calm "Time's up" screen
that only a parent can dismiss.

**Why this priority**: Screen time is the main thing Jellyfin cannot do server-side and the
clearest differentiator, but the kids experience is already useful without it.

**Independent Test**: Start a short timer, play content, and verify the remaining-time indicator,
the warning, and the time's-up screen. Then verify that the screen survives an app restart and can
only be dismissed through the parent gate.

**Acceptance Scenarios**:

1. **Given** the parent menu, **When** the parent sets a duration (adjustable in 5-minute steps,
   default 30 minutes) and starts the timer, **Then** a thin remaining-time indicator is visible
   on kids screens and during playback controls.
2. **Given** a running timer, **When** 5 minutes and 1 minute remain, **Then** a short visual
   warning with remaining minutes is shown. If a sound plays, playback audio is lowered briefly and
   not paused.
3. **Given** a running timer, **When** it reaches zero, **Then** playback stops, the player is
   released and a full-screen "Time's up" screen is shown whose only control is the parent lock.
4. **Given** the time's-up screen, **When** the app is restarted or the device reboots, **Then**
   the app opens on the time's-up screen again until a parent passes the gate.
5. **Given** the time's-up screen, **When** the parent passes the gate, **Then** they can add time
   or end the timer, and the child returns to where they were.
6. **Given** v1, **When** a timer is used, **Then** it is a per-session timer only. A daily
   allowance and a bedtime window are out of scope; allowed hours are enforced by the server's
   access schedule (see Assumptions).

---

### User Story 5 - Calmer playback: no endless autoplay (Priority: P2)

Episodes don't play back-to-back forever. The parent decides whether next episodes autoplay and
how many in a row. At the end, the child gets a simple choice instead of an infinite chain.

**Why this priority**: Avoiding autoplay rabbit holes is a key kids-app principle and builds on
Wholphin's existing autoplay and "pass-out protection". It is valuable but secondary to safety and
time.

**Independent Test**: With autoplay enabled and capped at N, play N+1 consecutive episodes. Verify
autoplay stops after N and an end screen offers replay or back to home.

**Acceptance Scenarios**:

1. **Given** kids mode, **When** no parent preference is set, **Then** autoplay of next episodes
   is off by default.
2. **Given** autoplay enabled with a cap of N consecutive episodes, **When** the Nth autoplayed
   episode ends, **Then** playback stops and an end screen offers "watch again" and "back to home".
3. **Given** kids playback, **When** the child opens player options, **Then** only basic controls
   are offered: play/pause, seek, previous/next, and audio and subtitle track selection among the
   tracks available. Subtitle download and advanced playback settings are not offered.

---

### User Story 6 - Kid-friendly presentation reusing Wholphin's UI (Priority: P3)

The kids experience looks like Wholphin, not like a different app. It uses the same rows, cards,
drawer and player, but with larger artwork, fewer and clearer entries, titles that don't rely on
reading, and a calm color theme.

**Why this priority**: It improves usability for young children, but the functional value is
already delivered by stories 1–5 with the stock look.

**Independent Test**: Compare kids screens with their regular Wholphin equivalents. They use the
same components with larger cards and fewer items per row. Every action is identifiable by
icon/artwork, and the kids theme applies only to kid profiles.

**Acceptance Scenarios**:

1. **Given** kids mode, **When** the home is shown, **Then** cards are visibly larger than in
   regular Wholphin and at most 5 cards are visible per row on a 1080p screen.
2. **Given** kids mode, **When** any navigation entry is focused, **Then** it is identifiable by an
   icon or artwork, not by text alone.
3. **Given** kids mode, **When** focus moves to a new screen, **Then** initial focus is on content
   (first row item), never on the parent lock.

---

### Edge Cases

- **Server unreachable or kid's session token expired:** a kid-voiced "ask a grown-up for help"
  screen is shown with no path to server or login screens except through the parent gate.
- **Kid account policy can't be read** (the account has insufficient permissions): setup still
  completes and shows the recommendations without current values. It never blocks.
- **Device clock changes during a running timer:** remaining time must not increase. Elapsed time
  is measured monotonically, and when in doubt the stricter outcome applies.
- **Timer expires mid-playback:** playback progress up to the stop is reported to the server like a
  normal stop, so "continue watching" resumes correctly.
- **Deep links / Android TV home-screen channels / voice assistant intents** that target non-kid
  content or screens are ignored in kids mode, or routed to the kids home.
- **Screensaver** in kids mode shows only content the kid profile is allowed to see.
- **The only signed-in user is marked as kid** and the parent forgets the strong secret: a
  documented recovery path exists (re-adding the server / clearing app data). It is not available
  from the kid UI.
- **Theme music, trailers and extras** play only if they belong to items the kid account can
  already access.

## Requirements *(mandatory)*

### Functional Requirements

**Kids mode & profiles**

- **FR-001**: Kids mode MUST be a per-profile mode inside the regular Wholphin app (no separate
  app). Device users marked as kid profiles open directly into the kids experience. Non-kid
  profiles keep the full Wholphin UI unchanged.
- **FR-002**: In kids mode the app MUST hide these features:
  - settings
  - discover/requests
  - live TV and DVR
  - server and user management
  - app updates
  - item deletion
  - playlist editing
  - watched-state editing
  - external player selection
  - subtitle download
  - file-path and debug information
- **FR-003**: In kids mode navigation MUST offer at most three top-level entries: Home, My stuff
  (continue watching and favourites), and Search (only when enabled by a parent; disabled by
  default).
- **FR-003a**: The kids home MUST consist only of these sections, in this default order:
  1. **Keep watching**: in-progress items and next episodes, combined.
  2. **Picked by your grown-ups**: the contents of the collections or playlists chosen by a parent
     for this kid profile.
  3. **One row per library** the kid account can access, with titles in a stable alphabetical
     order.
  4. **New**: recently added items from accessible libraries, marked as new.
  5. **My favourites**: series and movies.

  Empty sections MUST be hidden. Each row MUST show at most one page of items (about 20) with no
  "view more" or endless loading.
- **FR-003b**: The kids home MUST NOT include algorithmic suggestions, recently released,
  studios, genres, live TV or recordings rows.
- **FR-003c**: In kids settings a parent MUST be able to choose which collections or playlists
  feed "Picked by your grown-ups", and reorder or hide the home sections, for each kid profile.
  The choice is limited to items the kid account can access.
- **FR-003d**: "My stuff" MUST show the kid's full favourites and full "keep watching" list as
  grids. It MUST NOT show watch history.
- **FR-004**: The kids user switcher MUST list only kid profiles. Reaching any other profile MUST
  require the parent gate and any protection that profile already has.
- **FR-005**: Content shown in kids mode MUST come only from what the Jellyfin server returns for
  the kid account. Every view (home rows, search, details, extras, screensaver, home-screen
  channels) MUST use that account's own access.

**Parent gate**

- **FR-006**: The parent gate MUST ask for the kids-mode parent passcode, entered with Wholphin's
  profile PIN entry (D-pad directions and the remote's number keys), before showing the parent menu.
- **FR-007**: A wrong passcode MUST clear the entry and show a short retry message. It MUST NOT lock
  the parent out.
- **FR-008**: The parent passcode guards every parent action: the timer, leaving kids mode, editing
  kids settings, changing which profiles are kids and enabling search. It has at least 4 symbols
  (digits or D-pad directions), is set during kids setup and is stored only as a non-reversible
  verifier. Changing it requires the current passcode.
- **FR-009**: The parent lock MUST be the single entry point to parent features from kid screens.
  It MUST be reachable from the kids navigation (home and other kids screens) and the time's-up
  screen, and MUST never receive initial focus. From the player, Back returns to kids screens.
- **FR-010**: Pressing Back at the top level of kids mode MUST NOT leave kids mode or the app
  without the parent gate.

**Setup**

- **FR-011**: Kids setup MUST let a parent choose kid profiles among signed-in users and set the
  strong secret if it doesn't exist yet.
- **FR-012**: Kids setup MUST show recommended server restrictions per kid account, and their
  current values where readable. It MUST show a prominent warning when the account has no rating
  limit and allows unrated items.

**Screen time**

- **FR-013**: A parent MUST be able to start, update and stop a session timer from the parent menu
  (5-minute steps, default 30 minutes, last value remembered).
- **FR-014**: While a timer runs, kids screens MUST show an unobtrusive remaining-time indicator,
  and warnings MUST appear at 5 and 1 minutes remaining.
- **FR-015**: When the timer expires the app MUST stop and release playback and show a time's-up
  screen. That screen persists across app restarts and device reboots until dismissed through the
  parent gate, where the parent can add time or end the timer.
- **FR-016**: Remaining time MUST be computed so that changing the device clock cannot extend it.

**Playback**

- **FR-017**: In kids mode autoplay of next episodes MUST be off by default. When a parent enables
  it, it is capped at a parent-chosen number of consecutive episodes (default 3), after which an
  end screen offers "watch again" and "back to home".
- **FR-018**: Kids playback MUST offer only basic controls (play/pause, seek, previous/next,
  existing audio/subtitle tracks) and MUST keep Wholphin's existing inactivity ("pass-out")
  protection.

**Presentation**

- **FR-019**: Kids screens MUST reuse Wholphin's existing screens and components, with a kids
  presentation (larger cards, at most 5 per row on 1080p, a calm color theme). Every navigation
  entry MUST have an icon or artwork.
- **FR-020**: Every kids screen MUST be fully operable with a D-pad. Initial focus MUST land on
  content.

**Isolation**

- **FR-021**: Users who don't use kids mode MUST see no behavioral or visual change compared with
  the current Wholphin release.

### Key Entities

- **Kid profile**: a device user (Jellyfin account on a server) flagged as kid. Attributes:
  - kids presentation on/off
  - search allowed (default no)
  - autoplay enabled (default no) and autoplay cap
  - last timer duration
  - picked collections/playlists
  - home section order and visibility
- **Parent secret**: the strong verification for rule-changing actions. Stored only as a
  non-reversible verifier.
- **Session timer**: one per device. It applies to whichever kid profile is active, so switching
  between kid profiles does not reset it. Attributes: total duration, remaining time
  (monotonic), state (idle / running / expired). The expired state persists.
- **Server restriction summary**: read-only view of the kid account's server policy (accessible
  libraries, max parental rating, block unrated, access schedule), used only for display in setup.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A parent with an already-restricted Jellyfin kid account completes kids setup on the
  TV in under 3 minutes.
- **SC-002**: In a scripted walkthrough of every kids screen, 0 paths lead to settings, other
  profiles, server management or a non-kids screen without passing the parent gate.
- **SC-003**: A child reaches playback of a home-row item in at most 3 remote presses after
  selecting it (open → play), and in at most 2 navigation levels from the kids home.
- **SC-004**: With a running timer, playback stops within 5 seconds of expiry, and the time's-up
  screen is still shown after an app restart in 100% of test runs.
- **SC-005**: Changing the device clock forward or backward during a running timer never increases
  remaining time.
- **SC-006**: Every existing automated test of the regular Wholphin builds still passes, and users
  who never enable kids mode see no change (FR-021).
- **SC-007**: An adult passes the parent gate in under 10 seconds in typical use, and a wrong
  passcode never locks the parent out.

## Assumptions

- Content restrictions (libraries, max rating, unrated items, tags, allowed hours) are configured
  by the parent on the Jellyfin server (web dashboard). In-app editing of server policy is out of
  scope for v1 because it needs administrator credentials, which the app does not store
  (constitution III).
- The target is Android TV / Google TV with a D-pad remote. Phone and tablet layouts are out of
  scope for v1.
- The kids experience reuses Wholphin's playback engines, home rows, cards, details screens and
  navigation drawer. No new playback engine or content source is introduced.
- Interaction patterns are inspired by YouTube Kids; no code, imagery, sounds or text from it are
  used.
- Out of scope for v1 (candidates for later specs):
  - an "approved content only" allow-list
  - blocking or reporting items from the kid UI
  - illustrated avatar sets and a kid secret code
  - per-kid watch history review
  - voice search UX
  - device kiosk / lock-task mode
  - "learn first" goals
  - a daily allowance and an in-app bedtime (allowed hours rely on the server access schedule)
- Offline use is not required. A server is needed to browse and play, as in regular Wholphin.
