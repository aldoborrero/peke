# Feature Specification: Security follow-ups

**Feature Branch**: `005-security-followups`

**Created**: 2026-10-24

**Status**: Draft

**Input**: User description: "Close the leftovers of the Peke adversarial review that spec 004 didn't close: C7
(cleartext HTTP and user CAs), C11 (the child answers the microphone prompt), C13 (the Play key on a home card skips
the details page), C14 (direct play still starts after Back), C15 (the grown-up debug setting hides the kid's player
controls), C16 (default focus on Grown-ups), P1 (media-session controllers), and decide P2, P4 and P5."

Sources: `reports/Peke adversarial review.md` (findings C7, C11, C13–C16, P1, P2, P4, P5), `reports/Peke 004
review.md` for context, constitution 2.1.0 and `design/DESIGN.md` (Behaviour: "Opening videos", "Launch"; Words;
board 29, microphone permission).

Builds on [004-tv-setup-flows](../004-tv-setup-flows/spec.md), which isn't merged yet. 004 closed C2–C6, C8, C10,
C17 and C18. The 004 review's own findings (F1–F12) are not part of this feature, except where noted below.

## Clarifications

### Session 2026-10-24

- Q: How is an `http://` server address handled (C7)? → A: Warn and continue. The grown-up must confirm a warning
  that plain HTTP can be intercepted on the home network before Peke uses an `http://` address, and HTTP keeps
  working afterwards. When the grown-up types a bare host without a scheme, Peke tries `https://` first. HTTPS-only
  is not required: households that run Jellyfin on plain HTTP at home stay supported, while HTTPS servers (such as
  the owner's own, on a public certificate) are preferred whenever they answer.
- Q: Which certificate authorities does Peke trust for an `https://` server (C7)? → A: Keep trusting the system's
  and those a grown-up installed on the TV. Installing one needs the TV's system Settings, which is the TV's job
  under constitution 2.1.0, and households with a home CA keep working over HTTPS.

## Review items and what this feature does with them

Each item was checked against the code on `005-security-followups` (based on 004) before writing this spec.

| Item | Still open? | Decision |
|---|---|---|
| C7 | Yes: cleartext is allowed everywhere and user-installed CAs are trusted; an empty kids list from the server still erases every kid on the TV | In scope (User Story 6): warn on HTTP, prefer HTTPS, keep user CAs (Clarifications) |
| C11 | Yes: the mic button asks the system for the permission when a kid presses it; the music visualiser asks too | In scope (User Story 2) |
| C13 | Yes: the Play key plays directly from every home row and every library grid; the home's click handler reads the rows of its first composition | In scope (User Story 1) |
| C14 | Yes: the direct-play loading page goes back and starts the player when the item arrives, whatever is on screen by then | In scope (User Story 1) |
| C15 | Yes: the overlay gets the raw debug setting; only the dialog checks that advanced playback is allowed | In scope (User Story 3) |
| C16 | Yes: the stop screens (time's up, break, bedtime) focus Grown-ups; the picker and "no kids" screens already don't | In scope (User Story 4) |
| P1 | Yes: the player's media session has no rules, so any controller gets every default command | In scope (User Story 5) |
| P2 | No: the Kids plugin 0.0.7.0 refuses media delivery to a stopped kid, cuts deliveries in flight and sends Stop to the session, so a direct stream doesn't outlive time's up | Cut: nothing to do on the client |
| P4 | Yes: any account allowed to remote-control sessions can show any text on a kid's TV; only the Kids plugin's own messages are filtered | In scope (User Story 5) |
| P5 | Yes, latent: the error handler that shows a toast blocks the main thread waiting for the main thread when it fires there; no kid-reachable path triggers it today | In scope, low priority (FR-019) |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Videos open the way the design says, and Back means Back (Priority: P1)

A kid on the home presses the remote's Play key on a film in Picks. Today the film starts without its details page.
After this feature, only "Continue watching" starts playback straight away, whether the kid presses OK or Play;
everywhere else, OK and Play both open the details page. If the kid opens a "Continue watching" item and presses Back
while it's loading, they stay where they were and nothing starts behind them.

**Why this priority**: kids use these paths dozens of times a day; a video that starts by itself after Back, or that
skips the page a parent expects, is the most visible break of the rules.

**Independent Test**: on a kid's home, press Play on an item in each row and in a library grid; then open a
"Continue watching" item and press Back before it plays, on a slow network.

**Acceptance Scenarios**:

1. **Given** a kid's home, **When** the kid presses Play on an item in any row other than "Continue watching",
   **Then** the item's details page opens, exactly as OK does.
2. **Given** a kid's home, **When** the kid presses Play or OK on an item in "Continue watching", **Then** it plays
   straight away, resuming.
3. **Given** a library or favourites grid in kids mode, **When** the kid presses Play on an item, **Then** its
   details page opens.
4. **Given** the home's rows load or reorder after the page first showed (for example after a profile refresh),
   **When** the kid presses OK or Play on an item, **Then** the decision uses the row the item is in now, never the
   row that used to be at that position.
5. **Given** a "Continue watching" item is loading, **When** the kid presses Back before playback starts, **Then**
   the kid stays on the page they went back to and no playback starts afterwards.
6. **Given** a "Continue watching" item is loading, **When** the kid's time runs out before it plays, **Then** the
   time's-up screen stays on top and no video plays, not even for a moment.

---

### User Story 2 - A grown-up decides about the microphone, never the kid (Priority: P1)

A kid with search allowed presses the microphone on the search page. Today Android asks "Allow Peke to record
audio?" and the kid answers. After this feature, the microphone shows for kids only after a grown-up has turned
voice search on behind the parent gate, read where the voice goes, and answered the system prompt themselves. A kid
never sees the permission prompt.

**Why this priority**: a consent decision about a child's voice is a grown-up's (constitution VI, DESIGN board 29),
and the voice goes to the TV's speech service, not to the household server.

**Independent Test**: on a fresh TV, open Search as a kid: no microphone. Turn voice search on behind the gate and
grant it; the kid now sees the microphone and can use it without any prompt. Deny it, or turn voice search off: the
microphone is gone again and no prompt ever appears for the kid.

**Acceptance Scenarios**:

1. **Given** voice search has never been turned on for this TV, **When** a kid opens Search, **Then** there is no
   microphone, and nothing on any kid screen can bring up the system's permission prompt.
2. **Given** the parent menu (after the code), **When** the grown-up turns voice search on, **Then** Peke first
   explains in plain words that the kid's voice is sent to the TV's speech service and not to the household
   server, and only after the grown-up confirms shows the system prompt to them.
3. **Given** the grown-up grants the permission, **When** a kid with search allowed opens Search, **Then** the
   microphone shows and works with no prompt.
4. **Given** the grown-up denies the permission, or denied it earlier for good, **When** they come back to the
   setting, **Then** Peke says voice search is off and how to allow it in the TV's own settings; kids still see no
   microphone.
5. **Given** voice search is on, **When** the grown-up turns it off, **Then** the microphone disappears for every kid
   on this TV, even though the system permission stays granted.
6. **Given** the permission was withdrawn in the TV's system settings, **When** a kid opens Search, **Then** the
   microphone is hidden and no prompt appears.
7. **Given** first setup, **When** a picked kid has search allowed, **Then** the last setup step offers turning
   voice search on, with the same explanation; skipping it leaves voice search off.

---

### User Story 3 - The kid's player always has its controls (Priority: P2)

A parent turns on "Playback debug info" in This TV to look at a stream problem. Today every kid on the TV then gets a
player with no seek bar or buttons and a debug overlay. After this feature, the setting has no effect on a kid's
player: kids always get their normal controls and never the debug overlay.

**Why this priority**: it breaks the player for kids, but only after a grown-up changes a setting.

**Independent Test**: turn the setting on in This TV, play something as a kid: the controls show and the overlay
doesn't.

**Acceptance Scenarios**:

1. **Given** "Playback debug info" is on, **When** a kid plays a video and opens the controls, **Then** the seek bar
   and buttons show, and no debug overlay appears.
2. **Given** "Playback debug info" is on, **When** a kid presses any remote key in the player, **Then** no debug
   view opens.

---

### User Story 4 - Focus never starts on a grown-up control (Priority: P2)

A kid's time runs out and the time's-up screen appears with Grown-ups focused; pressing OK opens the code entry and
invites guessing. After this feature, a stop screen (time's up, break, outside allowed hours) opens with focus on its
picture or message, and Grown-ups is reached only by moving the D-pad to it on purpose.

**Why this priority**: a literal breach of constitution V with small impact.

**Independent Test**: reach each stop screen and press OK straight away: nothing opens. Press Down (or the direction
the layout gives) and Grown-ups gets focus.

**Acceptance Scenarios**:

1. **Given** a stop screen opens, **When** the kid presses OK without moving, **Then** nothing happens and the code
   entry doesn't open.
2. **Given** a stop screen, **When** a grown-up moves the D-pad to Grown-ups and presses OK, **Then** the parent gate
   opens as before.
3. **Given** any screen a kid can reach, **When** it opens, **Then** its default focus is never Grown-ups, the
   avatar switch or another control that leads to the parent gate.

---

### User Story 5 - Other devices can't steer a kid's TV (Priority: P2)

A phone remote app or another app on the TV connects to Peke's player and can skip, seek or try to queue other
items; any account allowed to remote-control sessions can pop arbitrary text on the kid's screen. After this feature,
only the TV's own system controllers (the remote's media keys, the system's media controls) can drive the kid's
player, and only with play, pause, seek, previous, next and stop; nothing can change what is queued. Only the Kids
plugin's messages show on a kid's screen.

**Why this priority**: the server still enforces time and content, so this is about strangers and siblings steering
or messaging a child, not about rules being bypassed.

**Independent Test**: during kids playback, connect a test media controller app and try play, seek, next and setting
a new item; send a message to the kid's session from a grown-up's Jellyfin client.

**Acceptance Scenarios**:

1. **Given** a kid is watching, **When** the remote's media keys or the system's media controls are used, **Then**
   play, pause, seek and stop work as today.
2. **Given** a kid is watching, **When** an ordinary app on the TV or on a paired device tries to connect to the
   player, **Then** it is refused.
3. **Given** a controller is accepted, **When** it tries to set, add, replace or remove items, **Then** the request is
   refused and playback continues unchanged.
4. **Given** a kid is current, **When** another account sends a message to the kid's session, **Then** nothing shows,
   unless it is one of the Kids plugin's own messages, which follow the existing rules.

---

### User Story 6 - The TV's link to the server can't easily be forged (Priority: P2)

Someone on the home network (a sibling with a laptop) intercepts the TV's traffic and answers the parent-code check
with "valid", or answers the kids list with an empty list. Today that opens the parent menu, or signs every kid out
of the TV. After this feature, the connection rules below make that interception harder or visible to the grown-up
who sets the TV up (HTTPS first, a warning before plain HTTP), and an empty answer can never erase the TV's kids on
its own.

**Why this priority**: the parent code and the server's rules hold only as far as the connection does, but the
attack needs a capable person on the home network.

**Independent Test**: set the TV up with an `http://` server and with an `https://` one, check what setup and This
TV say; then make the server's kids list answer empty and check that the kids stay on the TV.

**Acceptance Scenarios**:

1. **Given** setup's server step, **When** the grown-up enters an `http://` address or picks a server that answers
   only on HTTP, **Then** Peke shows the HTTP warning, and goes on only after the grown-up confirms it; declining
   returns to the server step.
2. **Given** a set-up TV, **When** the server's kids list comes back empty, or without some of the TV's kids,
   **Then** no kid is removed from the TV until the server confirms, kid by kid, that each one is no longer a kid.
3. **Given** a set-up TV on an `https://` server, **When** a certificate isn't trusted by the rules of FR-015,
   **Then** the connection fails as "can't reach the server", and nothing falls back to plain HTTP.
4. **Given** setup's server step, **When** the grown-up types a host without a scheme (for example
   `jellyfin.example.org`), **Then** Peke tries `https://` first and uses it when it answers, with no warning; only
   if HTTPS doesn't answer does it try `http://`, which then needs the warning.
5. **Given** a TV set up on an `http://` server, **When** a grown-up opens This TV, **Then** it shows that the
   connection to the server isn't encrypted.

---

### Edge Cases

- **Play on a "Continue watching" item whose details are needed** (a series with no next episode): the existing
  direct-play rule decides, as for OK; Play never does more than OK.
- **Back pressed twice during a direct-play load**: the first Back leaves the loading page; the late result is
  dropped and the second Back acts on the page underneath as usual.
- **The direct-play load fails**: the loading page goes away and the kid gets the friendly "nothing to play" message
  only if they are still on it; a kid who already went back sees nothing.
- **Voice search on, but the TV has no speech service**: the microphone stays hidden, as today.
- **Voice search when the TV can't reach the internet**: the speech service is asked to work offline when it can;
  otherwise the kid gets the existing friendly error.
- **The permission was granted before this feature** (on a test TV): voice search stays off until a grown-up turns it
  on; the old grant alone doesn't show the microphone.
- **The music visualiser**: kids never reach music playback, so its microphone request is removed rather than gated.
- **A grown-up's Jellyfin client remote-controls the kid's session** (pause, stop from the dashboard or a phone): the
  server's remote-control permission already decides this; it keeps working as today.
- **A stop screen that is the only thing on screen**: the D-pad still reaches Grown-ups from the focused picture or
  message in one move.
- **An empty kids list from a real server** (all kids unmarked in the dashboard): each kid is confirmed one by one,
  then removed as in 004; the TV ends on "This TV has no kids".
- **A server that moves from `http://` to `https://`** (the household adds a reverse proxy): the grown-up resets the
  TV and sets it up again; changing the stored address in place is out of scope.

## Requirements *(mandatory)*

### Functional Requirements

**Opening videos (C13, C14)**

- **FR-001**: In kids mode, the Play key on any home row item or library grid item MUST lead to the same destination
  as OK on that item: direct play for "Continue watching", the details page for everything else.
- **FR-002**: The home's click and Play handling MUST decide from the rows as they are when the key is pressed.
- **FR-003**: A direct-play load MUST start playback only if its loading page is still the screen on top when the
  item arrives; otherwise the result is dropped, and nothing else on the screen stack is removed or replaced.
- **FR-004**: When a direct-play load ends without an item, the "nothing to play" message MUST show only if the
  kid is still on the loading page.

**Microphone (C11)**

- **FR-005**: No kid screen MUST ever request the microphone permission or show the system's permission prompt.
- **FR-006**: The microphone on the search page MUST show for a kid only when voice search is turned on for this TV
  by a grown-up, the permission is granted, the TV has a speech service and the kid has search allowed.
- **FR-007**: Voice search MUST be a This TV setting, reachable only after the parent gate, off by default. Turning
  it on MUST first show a plain explanation that the kid's voice goes to the TV's speech service and not to the
  household server, and MUST show the system prompt to the grown-up only after they confirm.
- **FR-008**: If the permission is denied or can no longer be requested, the setting MUST stay off and say how to
  allow it in the TV's system settings. Peke doesn't open system settings itself.
- **FR-009**: Setup's last step MUST offer voice search, with the same explanation, when at least one picked kid has
  search allowed; skipping it leaves voice search off.
- **FR-010**: Voice recognition MUST ask the speech service to work on the device when the service supports it.
- **FR-011**: The music visualiser's microphone request MUST be removed, since kids never reach music playback.

**Player (C15)**

- **FR-012**: In kids mode, the grown-up's "Playback debug info" setting MUST have no effect: the kid's player shows
  its normal controls and never the debug overlay or debug views.

**Focus (C16)**

- **FR-013**: Stop screens (time's up, break, outside allowed hours) MUST open with focus on a non-actionable picture
  or message, and Grown-ups MUST be reachable from it with a single D-pad move. No kid-reachable screen focuses a
  control that leads to the parent gate by default.

**Connection to the server (C7)**

- **FR-014**: Peke MUST use an `http://` server address only after the grown-up confirms a warning that plain HTTP
  can be intercepted on the home network and that HTTPS is safer. This applies to an address typed with
  `http://`, to a server found on the local network that answers only on HTTP, and to a bare host that answered
  only on HTTP. Declining the warning returns to the server step. Once confirmed, HTTP keeps working, and This TV
  shows that the connection isn't encrypted. When the grown-up types a host without a scheme, Peke MUST try
  `https://` first and use `http://` only if HTTPS doesn't answer.
- **FR-015**: For an `https://` server, Peke MUST trust the system's certificate authorities and those a grown-up
  installed on the TV, and nothing else; a certificate that fails that check MUST fail the connection, with no
  fallback to plain HTTP.
- **FR-016**: An answer from the server that leaves out some or all of this TV's kids MUST NOT remove any kid by
  itself; a kid is removed only after the server confirms, for that kid, that they are no longer a kid.

**Remote control (P1, P4)**

- **FR-017**: During playback, only the TV's own system controllers and Peke itself MUST be able to control the
  player, and only with play, pause, seek, previous and next within the queue Peke built, and stop. Every request to
  set, add, replace or remove items MUST be refused, from any controller.
- **FR-018**: While a kid is current, text messages sent to the TV's session MUST show only when they are the Kids
  plugin's own messages; every other message is dropped silently.

**Errors (P5)**

- **FR-019**: Showing an error message from a background error MUST never block the screen; on kid screens such
  messages use the friendly wording of DESIGN's Words, never technical details.

**Words**

- **FR-020**: Every new message and setting MUST ship in English and Spanish and follow `design/DESIGN.md`.

### Key Entities

- **Voice search setting**: per TV, off by default, changed only behind the parent gate; with the system's
  microphone permission it decides whether kids see the microphone.
- **Server connection rule**: whether this TV's server may be plain HTTP and which certificates it trusts; fixed at
  setup.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: None of the review's findings C7, C11, C13, C14, C15, C16, P1 and P4 can be reproduced with their
  original steps; for C7, an `http://` server is never used without the grown-up confirming the warning.
- **SC-002**: In 100% of attempts, a kid pressing Play outside "Continue watching" lands on a details page, and a kid
  pressing Back during a direct-play load never gets playback afterwards.
- **SC-003**: Across every kid screen, the system microphone prompt appears zero times; it appears only after a
  grown-up confirms the explanation behind the parent gate.
- **SC-004**: Every stop screen can be dismissed into the gate only by a deliberate D-pad move followed by OK; OK
  pressed straight away opens nothing.
- **SC-005**: An empty or partial kids list from the server removes zero kids from the TV without per-kid
  confirmation.
- **SC-006**: A test controller app gets no control over a kid's playback, and messages from other accounts never
  show on a kid's screen.

## Assumptions

- 004 is merged first or together; this feature uses its parent menu, This TV settings, setup's last step and the
  launch rule as they are.
- Peke hasn't been released, so there are no installs to migrate; test TVs keep working after an update, and voice
  search starts off on them.
- The kids' search permission (search allowed or not) stays a per-kid plugin setting; the voice search setting is
  per TV, because Android's microphone permission is per app on the device.
- The TV's speech service is the system one Peke uses today (the spec 001 choice); a household-server speech
  service is out of scope.
- The Kids plugin 0.0.7.0 enforces time on media delivery, which is why P2 is cut; if a later plugin drops that, P2
  comes back as a plugin issue.
- The Kids plugin's own messages can be told apart from others by their header, as the existing filter does.
- The 004 review's F1–F12 are handled separately; F2's logged Quick Connect secret is a transport-adjacent risk but
  isn't part of this feature.
