# Feature Specification: TV setup and daily flows

**Feature Branch**: `004-tv-setup-flows`

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "TV setup and daily flows for Peke. Replace Wholphin's server/user-list flows with a
kids-only flow: add the server, set up the parent account, fetch the household's kids from the plugin and
authenticate them (Quick Connect preferred), then discard the administrator session. Launch opens the only kid
directly, or the kids picker when there are several. The Grown-ups button asks for the parent code. No unlocked
state: leaving kids mode becomes resetting the TV."

Sources: the owner's decisions of 2026-10-23 after the first emulator test and the adversarial review
(`reports/Peke adversarial review.md`, findings C2, C3, C4, C5, C6, C10, C17, C18), constitution 2.1.0, and
`design/DESIGN.md` (boards 21–26 first setup, 1b who's watching, 7 parent gate, 8 parent menu).

Builds on [003-peke-foundation](../003-peke-foundation/spec.md). What this feature supersedes from 003: the default
"the admin may stay signed in on the TV" (it contradicts constitution III), Wholphin's server list and user list as
screens a household sees, and the "leave kids mode" choice that unlocks the TV.

## Clarifications

### Session 2026-10-23

- Q: When Quick Connect is disabled on the server, how is a kid signed in? → A: Both: Peke first suggests turning
  Quick Connect on in the dashboard and checking again, and also offers typing the kid's password once on this TV.
- Q: Can a kid switch to a sibling from inside their session? → A: No: switching to another kid needs the parent
  code, so a kid whose time is up can't carry on as a sibling.
- Q: With several kids, the launch picker is free; how does it stop a kid whose time is up from relaunching and
  picking a sibling? → A: The picker stays free unless the last active kid is stopped (time's up, break or outside
  allowed hours): then picking a different kid needs the parent code; picking the same kid is always free and shows
  their stop screen. Kids never type a code themselves.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A grown-up sets the TV up once (Priority: P1)

A parent installs Peke on the living-room TV. Peke walks them through four steps: add the household's Jellyfin
server, sign in as the server's administrator, set the household parent code if there isn't one yet, and pick which
of the household's kids use this TV. Each picked kid is signed in on the TV without anyone typing the kid's password.
When the parent finishes, the administrator is signed out and nothing of that account stays on the TV. The TV now
opens straight into the kids' side.

**Why this priority**: every other flow depends on a TV that knows its server and its kids, and this is where the
administrator account is used and must be let go.

**Independent Test**: on a fresh install with a server running the Kids plugin and two kid profiles, complete the
setup with the remote only; check that the kids' side opens, that the TV holds sessions only for the two kids, and
that the administrator's session is gone from the server's device list and from the TV's storage.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** the parent adds a server address that answers, **Then** Peke checks that the
   Kids plugin is present and goes on to the administrator sign-in; a server without the plugin gets a clear
   message and can't be set up.
2. **Given** the server step is done, **When** the parent signs in with an account that isn't an administrator,
   **Then** Peke refuses with a clear message and stays on the sign-in step.
3. **Given** the administrator is signed in and the household has no parent code, **When** the parent continues,
   **Then** Peke asks them to set the code (entered twice) before going on; if a code exists, this step is skipped.
4. **Given** the household has kid profiles in the plugin, **When** the kids step opens, **Then** it lists every
   kid with their avatar and name, and the parent picks one or more.
5. **Given** the parent picked kids and confirms, **When** setup finishes, **Then** each picked kid is signed in on
   this TV, the administrator session is ended on the server and erased on the TV, and the TV opens the kids' side.
6. **Given** the household has no kid profiles yet, **When** the kids step opens, **Then** Peke explains how to mark
   kids in the plugin's dashboard and offers to check again; setup can't finish without at least one kid.

---

### User Story 2 - Kids open Peke and land on their own things (Priority: P1)

A kid turns the TV on and opens Peke. If the TV has one kid, their home opens at once. If it has several, Peke shows
"Who's watching?" with only this TV's kids and their avatars; the kid picks themself and their home opens. No
server, user list or grown-up account ever appears.

**Why this priority**: this is what happens every day, and it's where kids could stray into grown-up accounts today.

**Independent Test**: with a set-up TV, relaunch Peke cold and warm, with one kid and with three; the first screen
is the kid's home or the kids picker, never anything else.

**Acceptance Scenarios**:

1. **Given** a TV set up with one kid, **When** Peke is launched, **Then** that kid's home opens directly.
2. **Given** a TV set up with several kids, **When** Peke is launched, **Then** the kids picker shows only those kids,
   and picking one opens their home.
3. **Given** several kids and the last kid's time is up, **When** Peke is relaunched and another kid is picked,
   **Then** the parent gate opens first; picking the kid whose time is up shows their time's-up screen.
4. **Given** a kid is watching, **When** the TV is turned off and on, Peke is killed, or the remote's Home button is
   pressed and Peke is opened again, **Then** Peke follows the same rule (the only kid, or the picker) and never shows
   a server list, a user list or a grown-up.
5. **Given** the kids picker, **When** a kid looks for a way to add a user or a server, **Then** there is none; the
   only other choice is Grown-ups, which asks for the parent code.

---

### User Story 3 - Grown-ups reach their menu with the parent code (Priority: P2)

A parent presses Grown-ups (in a kid's sidebar, on a stop screen or on the kids picker), types the parent code with
the remote and gets the parent menu: add time, the current kid's settings, add or remove kids on this TV, and reset
this TV. Nothing in it needs the administrator, except adding a kid, which asks for the administrator sign-in once
more to sign the new kid in.

**Why this priority**: the parent menu already exists; this story removes its ways into an unlocked TV and adds
managing the TV's kids.

**Independent Test**: from a kid's home, open the parent menu with the code; add time, change a setting, remove a kid
and add one back; check that no step leaves the kids' side unlocked.

**Acceptance Scenarios**:

1. **Given** a kid's home, **When** a grown-up opens Grown-ups and types the right code, **Then** the parent menu
   opens; a wrong code is refused with the existing retry limits.
2. **Given** the parent menu, **When** the parent removes a kid from this TV, **Then** that kid's session is signed
   out and forgotten on this TV only; the kid stays in the household and on other TVs.
3. **Given** the parent menu, **When** the parent adds a kid, **Then** Peke asks for the administrator sign-in, lists
   the household's kids not yet on this TV, signs the picked ones in and discards the administrator session again.
4. **Given** the parent menu, **When** the parent closes it, **Then** Peke returns to the kid (or the picker); there
   is no path from the menu to browsing as a grown-up.

---

### User Story 4 - Resetting the TV instead of unlocking it (Priority: P2)

A parent wants to hand the TV to another household or start over. In the parent menu they pick "Reset this TV" and
confirm. Peke signs out every kid on this TV, forgets the server and its cached kids data, and returns to the first
setup step. There is no longer a state where the TV is set up but unlocked.

**Why this priority**: the old "leave kids mode" choice is the root of findings C3, C4 and C5; removing the unlocked
state closes them together.

**Independent Test**: reset a set-up TV and check that the first screen is server setup, that no kid or server data
remains, and that relaunching or deep links can't reach any previous kid.

**Acceptance Scenarios**:

1. **Given** the parent menu, **When** the parent picks Reset this TV and confirms, **Then** every kid session on this
   TV is ended on the server and erased on the TV, and the server setup step opens.
2. **Given** a reset is confirmed and the server is unreachable, **When** the reset runs, **Then** the TV still
   erases everything locally and opens setup; the server-side sessions are left to expire or be removed from the
   dashboard, and the parent is told so.
3. **Given** a TV reset while Peke was running, **When** Peke is brought back from the background, **Then** it shows
   setup, not a kid.

---

### User Story 5 - Recovering a forgotten parent code (Priority: P3)

A parent forgot the code. On the parent gate they pick "Forgot the code?", sign in as the administrator and set a new
code. The administrator session is discarded right after.

**Why this priority**: rare, but without it the only way out is clearing the app's data.

**Independent Test**: on the gate, recover with the administrator account; the new code works, the old one doesn't,
and no administrator session remains.

**Acceptance Scenarios**:

1. **Given** the parent gate, **When** the parent picks Forgot the code? and signs in as an administrator, **Then**
   they set a new code and the gate accepts it.
2. **Given** the recovery sign-in, **When** a non-administrator account is used, **Then** it is refused.

---

### Edge Cases

- **A kid is unmarked in the plugin's dashboard:** that kid disappears from this TV the next time Peke hears from the
  server; their session on this TV is signed out. If no kids remain, Peke shows "This TV has no kids" with only
  Grown-ups (code) leading to add a kid or reset; it never unlocks.
- **A kid's session is revoked on the server** (signed out from the dashboard, password change): the kid's tile shows
  that a grown-up is needed; picking it opens the parent gate and then the administrator sign-in to sign the kid in
  again.
- **The server is unreachable at launch:** kids with a stored session open with their last known rules and the
  "can't reach the server" states of 003; nothing falls back to a grown-up or to setup.
- **The plugin is uninstalled or outdated:** the TV stays set up and in kids mode (fail closed); kids see the "a
  grown-up needs to fix this" state; the parent menu and reset still work with the cached code check.
- **Lost or corrupt local data** (kids cache, preferences): if the TV can't tell which kids it has, it treats itself
  as set up and locked, and asks for a grown-up; it never shows a user list or an unlocked home. Only if the stored
  server is also gone does it start setup.
- **An intent or deep link from another app or a phone** (play this, open this item, search): it acts only within
  the current kid and their rules; it never switches the user, opens setup or reaches the parent menu (C6).
- **The system screensaver** starts while a kid is active or the picker is shown: it shows only artwork the current
  kid may see, or Peke's own neutral art; never a grown-up's library (C10).
- **Quick Connect is disabled on the server:** the kid sign-in step suggests turning it on and offers the kid's
  password once (FR-006).
- **Two TVs**: setting up a second TV with the same kids is independent; each TV has its own sessions and removing a
  kid on one doesn't affect the other.
- **Setup is abandoned halfway** (the TV is turned off, Back on the first step): an administrator session that was
  opened is never written to storage, so it's gone after the restart; setup restarts from the first unfinished step,
  and any kid already signed in stays.
- **The kids picker and time limits:** picking another kid uses that kid's own allowance; switching from inside a
  kid's session needs the parent code (FR-012), and so does picking a sibling at launch while the last kid is
  stopped (FR-012a).

## Requirements *(mandatory)*

### Functional Requirements

**Setup**

- **FR-001**: A fresh install MUST open the setup flow, in this order: server, administrator sign-in, parent code
  (only if the household has none), kids on this TV, kid sign-in. Each step is fully usable with the remote.
- **FR-002**: The server step MUST accept a server address or a server found on the local network, and MUST refuse
  to continue when the Kids plugin isn't installed or is older than the supported version, with an explanation.
- **FR-003**: The administrator sign-in MUST accept the username and password or Quick Connect, and MUST refuse
  accounts that aren't server administrators.
- **FR-004**: The administrator session MUST be held only in memory while setup or a grown-up action needs it, MUST be
  ended on the server when that action finishes, and MUST never be written to the device's storage, backups or
  restored sessions (constitution III; finding C2).
- **FR-005**: The kids step MUST list the household's kid profiles from the plugin, with avatars and names, and MUST
  let the grown-up pick one or more; it MUST NOT list non-kid accounts.
- **FR-006**: When Quick Connect is enabled on the server, each picked kid MUST be signed in on this TV without anyone
  typing the kid's password, by the administrator approving a Quick Connect request for that kid from the TV itself.
  When it is disabled, Peke suggests turning it on in the dashboard and checking again, and also offers typing that
  kid's password once on this TV; the password is never stored.
- **FR-007**: Setup MUST finish only with at least one kid signed in, and MUST then open the kids' side.

**Daily use**

- **FR-008**: A set-up TV MUST always start in kids mode: with one kid, that kid's home; with several, the kids
  picker. This applies to cold starts, warm starts from the background and returns from the screensaver.
- **FR-009**: The kids picker MUST show only this TV's kids, each with their avatar and name, plus a Grown-ups entry
  that asks for the parent code. It MUST NOT offer adding users or servers.
- **FR-010**: No screen reachable by a kid MUST show Wholphin's server list, the Jellyfin user list or any non-kid
  account (findings C4, C18).
- **FR-011**: Intents, deep links and remote media-session commands MUST act only within the current kid and MUST
  NOT switch users, start setup or open grown-up screens (finding C6).
- **FR-012**: From a kid's home, switching to another kid on this TV MUST pass the parent gate. The sidebar's avatar
  leads to the kids picker only after the code.
- **FR-012a**: On the launch picker, picking a kid MUST be free, except when the last active kid on this TV is
  stopped (time's up, break, outside allowed hours) and a different kid is picked: then the parent gate MUST pass
  first. Picking the stopped kid again opens their stop screen. Kids never type a code.

**Grown-ups**

- **FR-013**: The parent menu, reached only through the parent gate, MUST offer: add time, the current kid's
  settings, the kids on this TV (add, remove), the TV settings already in the grown-up shell, the license screen and
  Reset this TV.
- **FR-014**: Adding a kid to this TV MUST ask for the administrator sign-in again and follow FR-004 to FR-006.
  Removing a kid MUST end and erase only that kid's session on this TV.
- **FR-015**: Reset this TV MUST, after a confirmation, end every kid session of this TV on the server when reachable,
  erase the server, the sessions and the cached kids data, and open setup (findings C3, C5).
- **FR-016**: There MUST be no state in which the TV is set up but not in kids mode; the "leave kids mode" choice is
  removed. The app keeps a single source of truth for "this TV is set up", and every kids-mode check reads it
  (finding C17).
- **FR-017**: The parent gate MUST offer Forgot the code?, which asks for the administrator sign-in and sets a new
  code, following FR-004.

**Resilience**

- **FR-018**: When the TV can't tell its kids or its state (lost cache, unreadable preferences, plugin missing), it
  MUST stay in kids mode and ask for a grown-up; it MUST start setup only when no server is stored at all.
- **FR-019**: A kid whose session no longer works MUST be shown as needing a grown-up; fixing it goes through the
  parent gate and the administrator sign-in.
- **FR-020**: Every new screen and message MUST ship in English and Spanish and follow `design/DESIGN.md` (words,
  focus, D-pad).

### Key Entities

- **TV setup**: the one server this TV belongs to, whether setup is complete, and the kids on this TV. The single
  source of truth for kids mode.
- **Kid on this TV**: a household kid (from the plugin) with a session for this TV; has an avatar, a name and a state
  (ready, needs a grown-up).
- **Administrator session**: a short-lived sign-in used only for setup, adding kids or recovering the code; never
  stored.
- **Parent code**: the household's code, kept by the plugin; unchanged by this feature except for recovery.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A grown-up sets up a new TV with two kids using only the remote in under 5 minutes, without typing a
  kid's password when Quick Connect is on.
- **SC-002**: After setup, after adding a kid and after a code recovery, the TV's storage and its backups contain no
  administrator credentials, and the server shows no administrator session for the TV.
- **SC-003**: In 100% of launches of a set-up TV (cold, warm, after the screensaver, after Home), the first screen is
  a kid's home or the kids picker.
- **SC-004**: None of the review's findings C2, C3, C4, C5, C6, C10, C17 and C18 can be reproduced with their
  original steps.
- **SC-005**: A kid can't reach a non-kid account, a server list or setup from any screen, intent or deep link, as
  checked by the reproduction steps in the review and new tests.

## Assumptions

- Peke hasn't been released, so there are no existing installs to migrate; TVs used for testing are set up again.
- The household runs one Jellyfin server with the Kids plugin (0.0.7.0 or later); one TV belongs to one server.
- The server allows an administrator to approve a Quick Connect request on behalf of another user (the Jellyfin SDK
  1.7.1 exposes this); to be confirmed on the household server during planning.
- Each kid session on this TV must survive the others: the plan checks how Jellyfin handles several users signed in
  from one device and keeps them apart.
- The parent gate, its code check and retry limits, the parent menu's add time and kid settings come from 001–003
  and keep working as they are.
- The plugin's dashboard stays the place to create and mark kids; Peke doesn't create Jellyfin users.
- A kid's avatar is their Jellyfin profile picture, or their initial when there is none, until the plugin's avatar
  field exists.
- Out of scope: per-kid themes, avatar picking (a plugin field, planned separately), several servers per TV and the
  screen redesign itself; new screens use the current look until the redesign feature.
