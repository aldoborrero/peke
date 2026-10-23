# Feature Specification: Kids mode backed by the Jellyfin Kids server plugin (client)

> **Superseded in part (2026-10-22, [003-peke-foundation](../003-peke-foundation/spec.md))**: this app is now Peke for
> Jellyfin, a kids-only app (constitution 2.0.0). Kids mode is no longer a per-profile mode inside Wholphin, and
> non-kid profiles no longer get the regular app: they get the grown-up shell. Build commands are flavor-less
> (`assembleDebug testDebugUnitTest` instead of `*DefaultDebug*`).

**Feature Branch**: `feature/kids-mode`

**Created**: 2026-10-17 (study) · **Rewritten**: 2026-10-18 as the client spec

**Status**: Draft

**Input**: User description: "Rewrite specs/002-kids-server-plugin as the client spec for Wholphin's integration
with the Jellyfin Kids server plugin (contracts: kids-api-v1, parent-lock, allowance). Detect plugin via anonymous
GET /Kids/Info cached per server. In plugin mode the server is authoritative for kid profiles, parent lock (Verify,
grants, offline verifier cache) and daily allowance status, with a local cache for offline use. Fail closed if
plugin disappears." Scope change (2026-10-18): "Wholphin will NOT support kids mode without the plugin. There is no
standalone/local mode. Kids mode is only offered when the server answers GET /Kids/Info; without the plugin,
Wholphin behaves as stock Wholphin with no kids mode, and the kids entry points are hidden. Drop the one-time
migration: kids mode v1 was never released, so nobody has local data."

Builds on [001-kids-mode](../001-kids-mode/spec.md), whose experience (kids home, parent gate, session timer, calmer
playback, presentation) is unchanged. This spec replaces 001's device-only storage of kid profiles and of the parent
lock: they now live on the server, and the device only caches them. It also replaces the earlier study in this
folder, which proposed a `/KidsMode` API.

The server side is the Jellyfin Kids plugin (repository `jellyfin-kids`). Its client contract is the source of
truth for every request and response:

- feature 1, profiles: `specs/001-kids-plugin-foundation/contracts/kids-api-v1.md`;
- feature 2, parent lock: `specs/002-kids-parent-lock/contracts/kids-api-v1-parent-lock.md`;
- feature 3, daily allowance: `specs/003-kids-allowance-tracking/contracts/kids-api-v1-allowance.md`;
- feature 4, sitting limit field: `specs/004-kids-session-limit/contracts/kids-api-v1-session-limit.md`;
- feature 5, enforcement and the server sitting (plugin 0.0.5.0):
  `specs/005-kids-enforcement/contracts/kids-api-v1-enforcement.md`;
- feature 6, live TV (plugin 0.0.6.0, optional): `specs/006-kids-live-tv/contracts/kids-api-v1-live-tv.md`.

## Clarifications

### Session 2026-10-18

- Q: How does a TV get the administrator rights needed to set the first household code and mark the first kids? →
  A: Only from an administrator profile already signed in on that TV: kids setup opened from that profile uses its
  own session, and nothing new is stored. Otherwise the TV points to the dashboard. After a lock exists, a grant
  from the code covers changes.
- Q: The server lock is only NUMERIC or REMOTE; what happens to v1's arithmetic challenge lock? → A: It is dropped.
  Kids mode needs a household code.
- Q: Where do the per-sitting session timer, its duration and the timer bar position go? → A: The sitting limit
  becomes a per-kid profile field on the plugin (`SessionLimitMinutes`, 5–120, absent or null means no limit), set
  once for the household. The TV still counts the sitting locally, since a sitting is per device (superseded
  2026-10-19: the server counts it across devices, and the app keeps no time). The timer bar
  position stays a device-only display preference. The indicator shows whichever ends first: the sitting or the
  daily allowance. The plugin field does not exist yet; it will be added after plugin feature 3, so the app treats
  it as optional and older plugins mean no sitting limit.
- Q: How does a grown-up return a server whose plugin disappeared to stock Wholphin? → A: Pass the gate with the
  cached code; the parent menu explains the plugin is gone and offers "turn off kids mode for this server on this
  TV", which clears that server's cache.
  - Follow-up: if the plugin went away before this TV cached the code (no verifier), no code can open the gate.
    The gate then says the plugin is missing, which is not the "can't reach the server" message, and offers a
    sign-in as a Jellyfin administrator instead: user name and password, typed on the TV, sent once to
    `POST /Users/AuthenticateByName` from a one-off device id, checked for administrator rights, then signed out
    and forgotten. It only leads to "turn off kids mode for this server on this TV".
- Q: Can the TV edit a kid's daily allowance? → A: Yes, in kids settings, authorised by the grant. It is a profile
  field sent with every profile save anyway.
- Defaults accepted: kids mode needs both `profiles` and `parentLock`, `allowance` is optional; an unknown API
  version is handled like a missing plugin; the allowance status is polled about every 60 s in kids mode; time's up
  from the allowance is lifted by a server bonus or the next day; removing the lock stays on the dashboard; v1's
  device-only profile and lock storage is removed where cleanly separable, and the profile table becomes the cache;
  001 is marked as superseded for storage.

### Session 2026-10-19 (plugin 0.0.5.0: enforcement and the server sitting)

- Q: Who counts the sitting now? → A: The server, per kid **across devices**, with a household break (default 15
  minutes with nothing playing on any device; after the limit the kid waits out the break). Status reports the
  sitting limit, used and remaining time, and the break left. This supersedes the 2026-10-18 answer that each TV
  counts its own sitting.
- Q: Does the app keep its own time as a fallback for older plugins? → A: No. **Server-only time logic**: the app
  keeps no time of its own. The per-device sitting timer, its saved state, the parent menu's start, change and stop
  timer, and any local countdown that decides time's up are removed. Kids mode needs the plugin features
  `profiles`, `parentLock`, `enforcement` and `sitting` (plugin 0.0.5.0 or later); a plugin without them is treated
  like an unsupported version (fail closed for cached kids), and the gate and kids settings say to update the
  plugin. Kids mode never shipped and the only server runs 0.0.5.0, so there is nothing to fall back for.
- Q: How does the bar move between polls? → A: It counts down from the last status (remaining minus the playing
  time on this TV since then; the break by the time since then), but that countdown never denies. Only a status or
  a refused playback shows the time's up, bedtime or sitting over screens.
- Q: What does the kid see when the server denies? → A: Wholphin's own screen for the reason in `DeniedReason` (or a
  refusal's `X-Kids-Reason`): time's up (`timeUp`), bedtime (`outsideSchedule`, new, the time's up screen with
  another title) or time for a break (`sessionLimit`, with the break left). It is lifted when a status no longer
  denies. Without enforcement the app still shows time's up and the break from the status fields.
- Q: What can a grown-up do on those screens? → A: The same gate as before. "Add time" sends a bonus to today's
  allowance, so it lifts time's up. The break and bedtime can't be overridden from the TV: kids settings (for
  example a longer sitting limit, which the server applies at once) and leaving kids mode stay available.
- Q: The server sends every client a `DisplayMessage` and then `Playstate Stop`. What does Wholphin do? → A: It
  already shows server messages as toasts and obeys Stop. In kids mode, a Kids plugin message is a sign the status
  changed, so the status is read at once; the message is not shown if it repeats what Wholphin already shows (a
  denial screen, or its own warning for the same limit). Stop is always obeyed.
- Q: And a refused playback? → A: A `PlaybackInfo` `NotAllowed` or a stream 403 `PlaybackNotAllowed` in kids mode
  leads to the kids screen for `X-Kids-Reason`, not a playback error. Jellyfin's own 403 outside allowed hours has no
  reason; the status is read at once and decides.
- Q: How often is Status polled? → A: Every 60 s, and every 10 s while denied or with 2 minutes or less left; during
  a break, right after the break should end.
- Q: And offline? → A: As before: the last status holds, so a denial stays (fail closed), and nothing is counted on
  the TV.

### Session 2026-10-21 (plugin 0.0.6.0: live TV)

Contract: feature 6, `specs/006-kids-live-tv/contracts/kids-api-v1-live-tv.md` in `jellyfin-kids`.

- Q: What may a kid do with live TV? → A: Watch the channels a grown-up picked for them, and see the guide for those
  channels (what is on now, next and later). No recordings, no DVR schedule, no timers, no series recordings.
- Q: Is `liveTv` required? → A: No. It is optional: a plugin without it means no kids live TV, and kids mode works
  as before.
- Q: Where does a kid find live TV? → A: A "Live TV" entry in the kids nav drawer, which opens Wholphin's own guide
  limited to the kid's channels, and a "Watch live" row on the kids home (what is on now), right after keep
  watching. Both only when the plugin has `liveTv` and the kid has at least one effective channel.
- Q: Build a kids guide or reuse Wholphin's? → A: Reuse. The guide (`TvGuideGrid`) reads Jellyfin's own
  `/LiveTv/Channels` and `/LiveTv/Programs`, which the plugin filters for kids, and is further limited to the ids
  from `/Kids/Users/{id}/LiveTv/Channels`. In kids mode its record buttons, view options and channel menu are
  hidden, and selecting a channel plays it. A separate "Now / Next" list would duplicate the guide for no gain.
- Q: A kid with no channels? → A: No Live TV entry and no row: the kid sees nothing about it. The grown-ups' kids
  settings show, per kid, how many channels are picked, or that none are, and say to pick them on the plugin's
  dashboard page.
- Q: Can the TV pick channels? → A: Not in this version. The plugin's channel list
  (`GET /Kids/Admin/LiveTvChannels`) needs an administrator, which a parent grant is not, so picking stays on the
  dashboard. Every profile save sends `LiveTvChannelIds` back unchanged, since `PUT` is a full replacement.
- Q: A channel the server refuses? → A: "This channel isn't available", and back to where the kid was: no retry, no
  transcode fallback, no playback error. A recording refusal (`liveTvRecording`) is treated the same way with
  "This isn't available", since recordings are hidden. Time refusals keep their kids screens.

## Context

- Kids mode v1 (unreleased) kept everything on the device: kid profiles per signed-in user, one device-wide parent
  lock, a device lock that keeps the TV in kids mode, and a per-session timer.
- With the plugin, the household-wide parts live on the server: which users are kids and their kids settings, one
  household parent lock that the server checks and that authorises changes with short-lived grants, and (feature 3)
  a daily allowance counted by the server across every client.
- A server without the plugin has no kids mode. Wholphin then behaves as stock Wholphin.
- The device lock ("this TV stays in kids mode") stays on the device. The per-sitting limit is set per kid on the
  server, and the server counts the sitting across the kid's devices (plugin feature 5). The app keeps no time of
  its own.

## User Scenarios & Testing *(mandatory)*

Actors are the same as in 001: **parent** (also called grown-up), **child**, and **Jellyfin administrator**
(often the parent). A **household** is the set of devices and users of one Jellyfin server.

### User Story 1 - Kids set up once for the whole house (Priority: P1)

A parent marks a Jellyfin user as a kid and chooses their kids settings once, from any Wholphin TV or from the
plugin's dashboard page. Every Wholphin TV signed in to that server opens that user in kids mode with the same
sections, picks, search and autoplay options.

**Why this priority**: it is the reason for kids mode on the server. Without it there is no kid to apply anything
to.

**Independent Test**: on a server with the plugin, mark a kid from TV A (or the dashboard), then sign the same user
in on TV B. TV B opens in kids mode with the same settings, without any setup on TV B.

**Acceptance Scenarios**:

1. **Given** a server with the plugin and a kid configured on the dashboard, **When** a TV signs that user in,
   **Then** the TV opens the user in kids mode with the server's sections, picks, search and autoplay settings.
2. **Given** a parent changes a kid's settings in kids settings on TV A, **When** the change is saved, **Then** it
   is stored on the server, and TV B shows it the next time it loads that kid.
3. **Given** two TVs editing the same kid, **When** the second one saves after the first, **Then** the second TV is
   told the settings changed elsewhere, shows the current values, and nothing is overwritten silently.
4. **Given** a parent unmarks a kid on one TV, **When** another TV next loads that user's profile, **Then** it opens
   the user with the full Wholphin UI.
5. **Given** a kid with cached settings, **When** the server cannot be reached, **Then** the TV still opens the kid
   in kids mode with the cached settings.

---

### User Story 2 - Parent lock checked by the server (Priority: P1)

The household parent code is held and checked by the server, so it is the same code on every TV and can be changed
in one place. Changes made from a TV are authorised by a short-lived grant the server issues for the right code, so
a kid's own account can never change kids settings, the lock or time.

**Why this priority**: server-side profiles must not be editable by a kid's token. The grant is what lets a TV
signed in as the kid make parent changes at all.

**Independent Test**: set a household code on the dashboard. On a TV signed in as a kid, open the parent gate: the
wrong code is refused, the right one opens the menu, and a settings change is accepted by the server. Then
disconnect the network: the right code still opens the gate.

**Acceptance Scenarios**:

1. **Given** a household code, **When** a grown-up enters it at the parent gate, **Then** the server checks it, and
   the changes made in the following minutes are authorised by the grant it returned.
2. **Given** repeated wrong codes, **When** the server starts throttling, **Then** the gate tells the grown-up how
   long to wait, does not send codes while waiting, and never signs the user out.
3. **Given** a TV that has passed the server gate once, **When** the server cannot be reached later, **Then** the
   right code still opens the gate, checked against a copy of the household verifier cached on the device.
4. **Given** the household code is changed elsewhere, **When** this TV next reaches the server, **Then** it accepts
   only the new code, and its cached verifier is replaced at the next successful unlock.
5. **Given** a grant has expired, **When** a parent change is saved, **Then** the gate asks for the code again
   instead of failing silently.
6. **Given** a grown-up changes the household code from a TV, **When** they confirm the current code and a new one,
   **Then** the server stores the new code, and every TV accepts it.

---

### User Story 3 - First setup on a household (Priority: P1)

On a server that has the plugin but no kids yet, a grown-up opens kids setup from a regular profile, sets the
household lock if there is none, and marks one or more signed-in users as kids. Kids setup is not offered at all on
servers without the plugin.

**Why this priority**: without it, a household can only be set up from the dashboard.

**Independent Test**: on a fresh plugin server, from an administrator's profile on the TV, set a code and mark a
user as a kid; that user then opens in kids mode. On a server without the plugin, no kids entry point is visible.

**Acceptance Scenarios**:

1. **Given** a server with the plugin and no household lock, **When** an administrator opens kids setup on the TV,
   **Then** they can set the household code and mark kids.
2. **Given** a server with the plugin and a household lock, **When** a grown-up opens kids setup, **Then** they pass
   the parent gate first, and their changes are authorised by the grant.
3. **Given** a server without the plugin, **When** any profile is used, **Then** no kids settings, kids menu entry or
   kids mode is shown, and Wholphin behaves as stock.
4. **Given** kids setup, **When** a user is marked as a kid, **Then** the server-side restriction checklist from 001
   is shown as before.

---

### User Story 4 - Daily allowance shared across devices (Priority: P2)

When the server counts a kid's daily allowance, the TV shows the time left today, warns before it runs out, and
shows the time's up screen when it does, whichever device used the time. A grown-up can add time from the parent
menu.

**Why this priority**: it is the plugin's main new capability for families, but profiles and the lock must work
first.

**Independent Test**: give a kid a 30-minute allowance on the server, watch 20 minutes on another client, then open
the kid on the TV: about 10 minutes are shown, warnings appear near the end, and the time's up screen appears when
the server reports no time left. Adding 15 minutes from the parent menu lets the kid watch again.

**Acceptance Scenarios**:

1. **Given** a kid with a daily allowance, **When** the kids home is shown, **Then** the time left today is shown
   with the same indicator as the session timer.
2. **Given** a kid watching, **When** the time left reaches the server's warning points, **Then** the same gentle
   warnings as the session timer are shown.
3. **Given** the server reports no time left, **When** the kid is watching or browsing, **Then** playback stops and
   the time's up screen is shown, and it survives an app restart.
4. **Given** the time's up screen because of the allowance, **When** a grown-up passes the gate and adds minutes,
   **Then** the server records the bonus for today and the kid can watch again.
5. **Given** the next day starts on the server, **When** the TV refreshes the status, **Then** the time's up screen
   is lifted without a grown-up.
6. **Given** a kid with a sitting limit and a daily allowance, **When** the server reports either has run out,
   **Then** the screen for it is shown (time's up, or time for a break); the indicator shows whichever ends first,
   by the server's figures.
7. **Given** the server cannot be reached, **When** the last known status said time was up, **Then** the time's up
   screen stays.

---

### User Story 7 - The server enforces the limits (Priority: P2)

With server enforcement on, the server stops and refuses a denied kid's playback on every client. Wholphin shows
its own screen for the reason, with the server's sitting and break, and doesn't show the server's duplicate
messages on top of it.

**Why this priority**: it is what makes the limits hold on every client, and the sitting is now one per kid
across devices.

**Independent Test**: on a 0.0.5.0 server with enforcement on and a 20-minute sitting limit, watch 15 minutes on
another client, then on the TV: the bar shows about 5 minutes, the TV warns, and at the limit the time for a break
screen shows the break left, without the server's toast on top. When the break has passed, the screen goes away on
its own. Outside allowed hours, the bedtime screen shows.

**Acceptance Scenarios**:

1. **Given** a status with `DeniedReason`, **When** it arrives, **Then** the screen for the reason replaces what is
   on screen: time's up, bedtime or time for a break.
2. **Given** the time for a break screen, **When** the break left reaches zero, **Then** the status is asked for
   at once, and the screen is lifted as soon as the server no longer denies.
3. **Given** a playback refused with `NotAllowed` or a 403 `PlaybackNotAllowed`, **When** it happens in kids mode,
   **Then** the kids screen for `X-Kids-Reason` is shown, not a playback error.
4. **Given** the server's warning or denial message, **When** Wholphin already shows the same, **Then** the server's
   message is not shown; the server's Stop is still obeyed.
5. **Given** a server whose plugin is older than 0.0.5.0, **When** a grown-up opens kids settings or the gate,
   **Then** they are told to update the plugin, and cached kids stay in kids mode.

---

### User Story 5 - The plugin goes away (Priority: P2)

If a server that had kids stops advertising the plugin, or stops answering, the TV does not quietly turn kids into
regular users. Kids stay in kids mode with their cached settings, the cached lock still guards the gate, and
grown-ups are told behind the gate what happened.

**Why this priority**: a failure or uninstall must never open the kids experience. It is rare, but the cost of
failing open is high.

**Independent Test**: with kids set up, disable the plugin on the server and restart the app. The kid still opens
in kids mode, the cached code still opens the gate, and the parent menu explains that the server no longer has the
plugin.

**Acceptance Scenarios**:

1. **Given** a server that had kids, **When** it stops advertising the plugin, **Then** cached kids stay kids, the
   cached lock still guards the gate, and the device lock stays.
2. **Given** that state, **When** a grown-up passes the gate, **Then** the parent menu says the server no longer has
   the kids plugin and offers to turn kids mode off for this server on this TV.
3. **Given** a network error while checking for the plugin, **When** the app starts, **Then** the last known result
   is kept.
4. **Given** the plugin comes back, **When** the TV detects it, **Then** the server's data replaces the cache.
5. **Given** the plugin went away before this TV cached the code, **When** a grown-up opens the gate, **Then** it
   says the server no longer has the plugin (not that it can't be reached) and offers an administrator sign-in;
   **When** a server administrator signs in, **Then** the only action offered is to turn kids mode off for this
   server on this TV, and neither the password nor the session is kept. A wrong password or a non-administrator is
   refused.

---

### User Story 6 - Stock Wholphin without the plugin (Priority: P1)

On a server without the plugin, including every Jellyfin 10.11 server, Wholphin behaves as stock Wholphin.

**Why this priority**: most installs will not have the plugin, and they must see no change.

**Independent Test**: against a server without the plugin, the existing Wholphin tests pass, no kids entry point is
visible, and the only extra network request is the anonymous plugin check.

**Acceptance Scenarios**:

1. **Given** a server without the plugin, **When** the app connects, **Then** no kids-related request other than the
   anonymous plugin check is sent.
2. **Given** a server without the plugin, **When** any screen is used, **Then** it looks and behaves as stock
   Wholphin.

### Edge Cases

- **Several servers on one TV**: kids mode and the household lock are per server. The device lock engages whenever a
  kid on any server is current, and leaving it uses the lock of the current server or, with no current user, of the server of the most recently used kid.
- **Plugin with only some features**: kids mode needs the `profiles`, `parentLock`, `enforcement` and `sitting`
  features. A server missing any is treated as having no kids mode, unless it had kids before (then fail closed). A
  plugin with `profiles` and `parentLock` but not the rest is outdated: the gate and kids settings say to update it.
- **Bedtime without enforcement**: Jellyfin itself refuses playback outside allowed hours with a 403 and no
  `X-Kids-Reason`; the app reads the status, and without a denial in it shows the usual playback error.
- **Unknown API version**: treated like a server whose plugin went away: fail closed for cached kids, no kids mode
  otherwise. Grown-ups are told behind the gate.
- **Cache missing or unreadable, server unreachable**: users the TV knew as kids open in kids mode with default
  settings, and the device lock stays.
- **Kid unmarked on the server while the TV is offline**: the TV keeps kids mode from the cache until it reaches the
  server.
- **Server reports a user as a kid who was never a kid on this TV**: kids mode applies as soon as the TV learns it,
  and the device lock engages.
- **Allowed hours closed (bedtime)**: the plugin's calls work outside allowed hours, so the gate, status and
  settings still load.
- **No verifier cached yet and the server is unreachable**: the gate cannot check the code; it says so and stays
  closed. If the server is reachable but the plugin is gone, the gate says that instead (FR-026a).
- **Grant scope**: a grant works only for the sign-in that obtained it. After the kid's sign-in is replaced, the
  gate is passed again.
- **Picks the kid cannot see or that were deleted**: shown as missing in kids settings, as today; the server keeps
  them.
- **Server store failure** (server error on a read): treated as unknown, never as "not a kid", "no lock" or "no time
  used"; the cache applies.

## Requirements *(mandatory)*

### Functional Requirements

**Detection**

- **FR-001**: The app MUST check each server for the plugin with an anonymous request, and remember the result
  (API version and advertised features) per server.
- **FR-002**: Kids mode MUST be offered for a server only when its last known result advertises `profiles`,
  `parentLock`, `enforcement` and `sitting` with a supported API version.
- **FR-002a**: A version-1 plugin advertising `profiles` and `parentLock` but not the rest MUST be treated as
  outdated: no kids mode (fail closed for cached kids, FR-025), and the gate and kids settings MUST say to update
  the plugin to 0.0.5.0 or later.
- **FR-003**: A "not found" answer MUST mean no plugin. A network error MUST keep the last known result.
- **FR-004**: The check MUST run when the app connects to a server and when it returns to the foreground after a
  while, and MUST NOT delay showing the kids home from the cache.
- **FR-005**: Without the plugin, the app MUST behave as stock Wholphin: no kids entry points, and no kids request
  other than the plugin check.
- **FR-005a**: A server that never had the plugin MUST leave nothing about kids on the TV (no cached plugin
  status), and the user list MUST NOT wait for the kids check unless the TV is locked to kids mode.

**Kid profiles**

- **FR-006**: The server MUST be the only source of truth for which signed-in users are kids and for their kids
  settings (sections and their order and visibility, picks, search, autoplay and its cap).
- **FR-007**: The app MUST cache the server's profiles per user, with the revision they were read at, and use the
  cache whenever the server cannot be reached or returns an error.
- **FR-008**: The app MUST refresh a user's profile from the server when that user becomes current, and the list of
  kids when the user switcher is shown.
- **FR-009**: Every profile change from a TV MUST go through the server, authorised by a grant or an administrator,
  and be sent with the revision it was based on. On a conflict the app MUST show the current server values and not
  overwrite them.
- **FR-009a**: Changes made quickly one after another MUST be saved one at a time, each applied to the profile
  as last saved, so none is sent with an old revision or undoes another.
- **FR-009b**: The kids home MUST be rebuilt when the current kid's profile arrives or its revision changes, and
  the current kid's profile MUST be read again when the app returns to the foreground after a while.
- **FR-010**: Profile changes MUST send every server field, including ones the app does not edit, so a change on the
  TV never clears them.
- **FR-011**: The timer bar position MUST stay a device-only display preference.
- **FR-011a**: Kids settings MUST let a grown-up edit a kid's daily allowance (minutes per day, or unlimited) and
  sitting limit (5–120 minutes in 5-minute steps, or none). The sitting limit control is shown only when the server
  profile carries the field.

**Parent lock**

- **FR-012**: The parent gate MUST check the code with the server when it can be reached, using the household lock
  type for code entry.
- **FR-013**: A right code MUST yield a grant that the app attaches to the parent changes that follow, until it
  expires; the app MUST ask again when it has expired.
- **FR-013a**: A grant MUST be timed from when it was received on the monotonic clock: the plugin's 5 minutes
  less a 15 s margin, shortened by `ExpiresAt` only when the TV's wall clock agrees with it.
- **FR-014**: When the server throttles, the gate MUST show the wait and not send codes until it is over.
  This holds for the parent gate and for kids settings alike, for a `RetryAfterSeconds` answer and a 429.
- **FR-014a**: Codes checked against the cached verifier MUST be limited like the server's per device: after 5
  wrong codes in a row, codes are refused unchecked for 30 s, doubled by each wrong code after a wait, up to 15
  min, and cleared by a right code. The count and the wait MUST be kept on the device across restarts.
- **FR-015**: After a successful server unlock, the app MUST fetch and cache the household verifier and lock type,
  and use them to check codes when the server cannot be reached.
- **FR-015a**: The cached verifier MUST be stored with the lock's `ChangedAt` read before it, and dropped as soon
  as the server reports another lock type or `ChangedAt`, so an old code never opens the gate.
- **FR-016**: A wrong code or a throttle answer MUST never sign the user out or show a server error.
- **FR-017**: A grown-up MUST be able to change the household code from the TV by entering the current code and a
  new one. Setting the first code, and marking kids before a code exists, MUST be done from an administrator profile
  signed in on the TV (its own session); otherwise the TV points to the dashboard.
- **FR-017a**: The household lock MUST be a code (numeric or remote buttons); the v1 arithmetic challenge is not
  offered.
- **FR-018**: Removing the household lock MUST be left to the dashboard (it needs an administrator).

**Daily allowance**

- **FR-019**: For a kid with a daily allowance, the app MUST show the time left today, poll the server's status
  about once a minute while kids mode is in the foreground, and refresh it right after playback stops.
- **FR-019a**: A status MUST NOT replace a newer one, such as a poll answered after a bonus: the later `ServerTime`
  wins, or without it the later request. Plugin status and the cached lock MUST be written separately, so neither
  check overwrites the other.
- **FR-020**: The app MUST show the same warnings as the session timer at the server's warning points.
- **FR-021**: When the server reports no time left, the app MUST stop playback and show the time's up screen, which
  persists across restarts until the server reports time again or a grown-up adds time.
- **FR-021a**: Time's up MUST also stop music, and kids mode MUST pause music when the app goes to the background.
- **FR-021b**: Time's up MUST be left whenever it is on screen and time is available again, also after a
  grown-up added time from kids settings opened on top of it. Time running out while a grown-up is in kids
  settings MUST NOT interrupt them: time's up shows when they leave.
- **FR-022**: From the time's up screen or the parent menu, a grown-up MUST be able to add minutes to today's
  allowance through the server.
- **FR-023**: When the server cannot be reached, the app MUST keep the last known status; if it said time was up,
  the time's up screen stays.
- **FR-024** (changed 2026-10-19): The app MUST NOT keep time of its own: no sitting timer, no saved countdown, and
  no local decision that time is up. The sitting is the server's (`SittingLimitSeconds`, `SittingRemainingSeconds`,
  across the kid's devices), and the indicator shows whichever ends first, the server's sitting or the daily
  allowance.
- **FR-024a**: Between polls, the indicator MUST count down from the last status: the allowance and the sitting by
  the playing time on this TV since it, the break by the time since it. This countdown MUST NOT deny on its own.
- **FR-024b**: The parent menu MUST show the time left today and in this sitting, or the break left, and offer "add
  time" (a bonus to today's allowance) when the kid has a daily allowance and is not denied for another reason.

**Server enforcement and denial screens**

- **FR-032**: When the status denies (`DeniedReason`, or without enforcement `TimeUp` or a break left), the app MUST
  show its own screen for the reason: time's up (`timeUp`), bedtime (`outsideSchedule`) or time for a break
  (`sessionLimit`), with the parent gate as on time's up. An unknown reason MUST be shown as time's up.
- **FR-033**: The time for a break screen MUST show the server's break left, and MUST be left without a grown-up
  when the break ends: the server reports `BreakRemainingSeconds` 0, or the next status no longer denies.
- **FR-034**: Every denial screen MUST be lifted when a status no longer denies (a bonus, the next day, the break
  over, allowed hours again, a changed limit).
- **FR-035**: In kids mode, a refused playback (`PlaybackInfo` `ErrorCode` `NotAllowed`, or a stream 403 with
  `X-Application-Error-Code: PlaybackNotAllowed`) MUST show the kids screen for `X-Kids-Reason` instead of a
  playback error, and the status MUST be read again. A 403 without a reason MUST read the status at once and show
  its screen if it denies.
- **FR-036**: In kids mode, a Kids plugin `DisplayMessage` MUST make the app read the status at once, and MUST NOT
  be shown when it repeats what the app shows: any of them while a denial screen is up, a "Screen time" or "Break
  time" warning when the app's own warning for that limit is due. The server's `Playstate Stop` MUST still be
  obeyed. Other server messages are shown as before.
- **FR-037**: The status MUST be polled every 10 s while denied or with 2 minutes or less left of the allowance or
  the sitting, and right after a break should end; otherwise about once a minute (FR-019).

**Live TV (plugin 0.0.6.0, optional)**

- **FR-038**: `liveTv` MUST be optional: it is not part of the features kids mode needs (FR-002), and without it a
  kid has no live TV and the app makes no live TV request to `/Kids`.
- **FR-039**: With `liveTv`, the app MUST read the current kid's effective channels from
  `GET /Kids/Users/{id}/LiveTv/Channels` with the kid's token when the kid becomes current and when their profile
  revision changes. An empty list, not a kid, a missing route or a refusal means no live TV; any other error keeps
  what was known for that kid (FR-027).
- **FR-040**: Kids mode MUST show live TV only when the plugin has `liveTv` and the kid has at least one effective
  channel: a "Live TV" entry in the kids nav drawer and a "Watch live" row of what is on now on the kids home, right
  after keep watching (first if keep watching is hidden). Otherwise neither is shown.
- **FR-041**: The Live TV entry MUST open Wholphin's guide limited to the kid's channels (those from FR-039, within
  what Jellyfin's filtered `/LiveTv/Channels` returns), with now, next and later programs. Selecting a channel or a
  program on now MUST play the channel through Wholphin's normal live TV playback.
- **FR-042**: In kids mode, recordings MUST be hidden everywhere (`RestrictedFeature.LIVE_TV_RECORDINGS`): record
  and series record buttons, the DVR schedule, recording folders and their pages. The guide's view options (app
  settings) and channel menu (favourites) MUST be hidden too.
- **FR-043**: In kids mode, a refusal with reason `liveTvChannel` (`PlaybackInfo` `NotAllowed` or a stream 403
  `PlaybackNotAllowed`) MUST show "This channel isn't available" and leave playback, with no retry, no fallback and
  no playback error. `liveTvRecording` MUST do the same with "This isn't available". Neither is a time refusal: the
  status is not read for them, and time refusals keep FR-035.
- **FR-044**: Every profile save MUST send `LiveTvChannelIds` back exactly as the server last sent it; a profile from
  a plugin without the field sends none.
- **FR-045**: The grown-ups' kids settings MUST show, per kid, how many live TV channels are picked, or that none
  are, and say channels are picked on the plugin's dashboard page. The TV does not pick channels (the plugin's
  channel list needs an administrator).

**Fail closed**

- **FR-025**: If a server that had kids stops advertising the plugin or a required feature, the app MUST keep the
  cached kids, the cached lock and the device lock, and MUST NOT turn cached kids into regular users.
- **FR-026**: In that state, the parent menu MUST tell grown-ups that the server no longer has the plugin and offer
  to turn kids mode off for that server on this TV, which clears its cache.
- **FR-026a**: If the server no longer has the plugin and no verifier is cached, the gate MUST say the plugin is
  missing and offer a one-off sign-in as a server administrator. A successful sign-in MUST only offer the FR-026
  action. The credentials and the resulting session MUST NOT be kept: the session is signed out right away, from a
  device id of its own so the TV's own sessions are untouched.
- **FR-027**: A server error on any kids read MUST be treated as unknown, never as "not a kid", "no lock" or "no
  time used".
- **FR-027a**: A 404 MUST only remove a kid when it is the plugin's own "This user is not a kid." answer and a
  new `/Kids/Info` check confirms the plugin is there. A 404 without that body, as from a plugin that went
  away, MUST keep the kid (FR-025).

**Security and privacy**

- **FR-028**: The app MUST NOT store administrator passwords, and MUST NOT keep grants beyond memory.
- **FR-029**: The cached verifier MUST be stored no less protected than the v1 device verifier.
- **FR-030**: No data goes anywhere but the user's own Jellyfin server.
- **FR-030a**: While the TV is locked to kids mode, signing in again (password or Quick Connect) MUST only renew
  a kid's row as that same Jellyfin user on that server; any other account is refused with a message, changes
  nothing, and its new session is ended. Adding users stays blocked.
- **FR-030b**: The device lock MUST be set when a kid becomes the current profile (also at app start) or is
  picked in the user list, and MUST NOT be set again by a profile refresh, so "Leave kids mode" lasts until
  then.
- **FR-030c**: Until it is known whether the current user is a kid and whether the TV is locked, the app MUST
  show neither the regular drawer and settings nor the kids pages.

**Removal of device-only storage**

- **FR-031**: The v1 device-only storage of kid profiles and of the parent lock MUST be replaced by the server and
  its cache; code that exists only for it is removed where it is cleanly separable.

### Key Entities

- **Plugin status (per server)**: the API version and features last advertised, when they were checked, and whether
  the server ever had kids on this TV.
- **Cached kid profile (per user)**: the server's kids settings for that user and the revision they were read at.
- **Household lock cache (per server)**: the lock type and verifier last fetched from the server, the lock's
  `ChangedAt` they belong to, and the offline wrong-code count and wait.
- **Grant (memory only)**: the token from a successful server unlock and when it expires, for the current sign-in.
- **Kids status (per user)**: today's date, used, allowance, bonus and remaining time, whether time is up, the
  warning points, when the next day starts, whether enforcement is on, the denial reason and until when, the
  sitting limit, used and remaining time, and the break left and its length, as last reported by the server.
- **Refusal (memory only)**: the reason of a refused playback, until a newer status.
- **Kid's live TV channels (memory only)**: the current kid's effective channels (id, name, number) and the profile
  revision they were read at. The picked ids themselves are the profile's `LiveTvChannelIds`, kept in the cached
  server object.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A kid configured once is in kids mode on a second TV, with the same settings, within 1 minute of
  signing in, with no setup on that TV.
- **SC-002**: With the plugin gone or the server unreachable, 0 cached kids open in the regular UI in scripted
  tests.
- **SC-003**: A kid's token alone never changes kids settings, the lock or time: every change the TV makes is
  authorised by a grant or an administrator.
- **SC-004**: When the server reports no time left, playback stops and the time's up screen is shown within 70
  seconds (within 10 s when it was 2 minutes or less away at the last poll), and immediately after a stop or
  refused playback. The same holds for bedtime and the sitting.
- **SC-005**: Against a server without the plugin, every existing Wholphin test passes and no kids entry point is
  visible.
- **SC-006**: The parent gate opens with the right code, offline, on a TV that has passed it once online.

## Assumptions

- The plugin targets Jellyfin 12.x. Servers on 10.11 have no plugin, so they have no kids mode.
- The plugin contracts above are authoritative. The allowance contract is still being implemented; the app relies
  only on what it states, and ignores unknown fields.
- Kids mode v1 was never released, so there is no device data to migrate.
- Wholphin has no offline downloads, so the app never reports watch time itself; the server counts playback from
  its own events.
- Content restrictions and allowed hours stay on the server's user policy (unchanged from 001).
- Server-side playback stopping and refusal is the plugin's (feature 5). The app already obeys the server's
  `Playstate Stop` (`PlaybackViewModel.handlePlaystateRequest`, `MusicService`) and shows `DisplayMessage` as a toast
  (`ServerEventListener`); in kids mode it hides the plugin's duplicates (FR-036) and turns refusals into its own
  screens (FR-035).
- The device lock, the timer bar position and the kids presentation remain device features. Time is the server's.
- Editing allowed hours and recommended policies is done on the plugin's dashboard page.
- **Dependency**: kids mode needs plugin 0.0.5.0 (features `enforcement` and `sitting`). The sitting limit is the
  `SessionLimitMinutes` profile field (feature 4), counted by the server (feature 5).
- Kids live TV needs plugin 0.0.6.0 (feature `liveTv`, feature 6), and is optional. The plugin filters Jellyfin's
  own `/LiveTv/Channels` and `/LiveTv/Programs` for kids and refuses other channels and every recording route, so
  the app hiding them is presentation, not the guard.
