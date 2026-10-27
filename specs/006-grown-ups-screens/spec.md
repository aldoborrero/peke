# Feature Specification: Grown-ups screens

**Feature Branch**: `006-grown-ups-screens`

**Created**: 2026-10-26

**Status**: Draft

**Input**: User description: "Feature 006 grown-ups screens, option C of the grown-ups UX review: remove marking and
unmarking kids from the TV; a new Add time widget; '{Kid}'s rules' replaces Kid's settings; task H answerable on the
TV; regroup the menu and Settings; parent gate fixes; small items."

Sources: `reports/Peke grown-ups UX review.md` (findings F-E1, F-G1–F-G6, F-M1–F-M4, F-K1–F-K11, F-S5, option C and
its proposed tree in §6), the owner's decisions on the Add time widget (2026-10-26, final), constitution 2.1.0, and
`design/DESIGN.md` (Behaviour, Words). The skills `ux-heuristics` and `design-everyday-things` with their Peke
sections were used to read the findings.

Builds on [004-tv-setup-flows](../004-tv-setup-flows/spec.md) and [005-security-followups](../005-security-followups/spec.md),
both merged. What this feature changes from 004: FR-013's menu list (kids on this TV, the license and Reset this TV
now live in Settings, reached from the menu) and "the current kid's settings", which become that kid's rules with
one editable rule.

## Clarifications

### Session 2026-10-26

The owner chose option C and asked for the review's recommendations to be taken as defaults. These were decided
without a question round and are recorded here:

- Q: Which Add time control? → A: (owner, final) A horizontal stepper over 5, 10, 15, 20, 30, 45 min, 1 h, 1 h 30
  and 2 h; no wrap-around; the arrow at each end is dimmed. It opens on the last amount given on this TV (15 the
  first time), kept per TV. OK confirms directly; Back returns to the menu. Maximum 2 h.
- Q: What does the result line say? → A: (owner, final) The kid's name and "Can watch until 19:15". Variants: past
  bedtime starts (amber warning), a break comes before it ends, time already left ("Had 10 min left. Can watch until
  19:25").
- Q: When time can't be added? → A: (owner, final) A line says why instead of hiding Add time: in a break (with "Keep
  watching now", which ends the break), bedtime ("allowed hours are set in Jellyfin"), no daily limit, offline.
- Q: Undo and "Keep watching now" need plugin 0.0.9.0 (its spec 013), built in parallel. What without it? → A: Both
  are gated on the plugin's `bonusUndo` and `breakEnd` features. Without them, Undo is hidden and a break shows
  only when it ends.
- Q: Where does the dashboard link go? → A: To the server's dashboard page of the Kids plugin, shown as an address
  in large type with "then pick Leo". A kid's own editor address carries a 36-character id, too long to read off a
  TV and type. No QR code yet (it needs an encoder, a dependency the constitution wants planned).
- Q: Without a kid (picker, "This TV has no kids"), what does the code open? → A: Settings, straight away; Kids on
  this TV lives inside it (review §6).
- Q: What does an empty OK on the code entry do? → A: Opens "Forgot the code?" (the administrator sign-in), without
  a server check or a throttle count; Back from there returns to the code entry.
- Q: Is "Change the parent code" still able to switch between a number code and a remote-buttons code? → A: No. The
  type is chosen in setup or the dashboard; changing the code keeps the current type (review §6).
- Q: Does the rules page keep a "Saved" toast? → A: No toast: the line under Daily time says "Saved" for a few
  seconds.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - No slip on the TV deletes a kid (Priority: P1)

A grown-up opens Leo's page on the TV to change his daily time. On the way there is no switch that turns a kid
profile off. Marking and unmarking kids is done only in the Kids plugin's dashboard.

**Why this priority**: today one OK on a "Kid profiles" switch deletes a kid's whole profile (allowance, sitting,
picks, sections, channels) on the server, on every TV, with no confirmation or undo (F-K1, severity 4).

**Independent Test**: open every grown-up page reachable from the parent gate; none offers to mark or unmark a kid,
and no request that creates or deletes a kid profile can be sent from the TV.

**Acceptance Scenarios**:

1. **Given** the parent menu, **When** the grown-up opens the kid's rules or Settings, **Then** no page lists users
   with a kid on/off switch.
2. **Given** a kid unmarked in the dashboard, **When** the TV next hears from the server, **Then** the kid leaves this
   TV as before (spec 004 edge case); the TV itself never unmarks.

---

### User Story 2 - Add time says who, how much and until when (Priority: P1)

Leo's time is up. A parent presses Grown-ups, types the code and Add time. A row shows "‹ 15 min ›" for Leo, with
"Can watch until 19:15" under it. Right moves to 20 min and the line says 19:20. OK sends it; the row shows that it's
sending and ignores a second OK. Then "15 more minutes for Leo · until 19:15" shows with Undo for 10 seconds, and the
TV returns to Leo, resuming what was playing. On a break, at bedtime, for a kid without a daily limit or with the
server unreachable, the menu says why time can't be added instead of hiding the item.

**Why this priority**: adding time is the daily grown-up task. Today the amount is a list, nothing says until when,
nothing confirms it, Back ends the menu, and the item vanishes without a reason on a break or at bedtime (F-M1,
F-M2, F-M3).

**Independent Test**: with a kid whose time is up, add 15 minutes through the gate and check the result line, the
confirmation and the resumed playback; then repeat on a break, at bedtime, with an unlimited kid and offline.

**Acceptance Scenarios**:

1. **Given** the parent menu for Leo, **When** the grown-up picks Add time, **Then** a stepper opens on the last amount
   given on this TV (15 the first time), with Leo's name and "Can watch until HH:MM".
2. **Given** the stepper on 5 min, **When** the grown-up presses Left, **Then** nothing changes and the left arrow is
   dimmed; on 2 h the right arrow is dimmed and Right does nothing.
3. **Given** Leo has 10 minutes left, **When** the stepper shows 15 min, **Then** the line reads "Had 10 min left. Can
   watch until" the time 25 minutes from now.
4. **Given** the amount would carry Leo past the start of his allowed hours' end (bedtime), **When** the stepper shows
   it, **Then** an amber line says bedtime starts at that time and Leo stops then.
5. **Given** Leo's sitting ends before the added time does, **When** the stepper shows the amount, **Then** a line says
   a break comes at that time.
6. **Given** the stepper, **When** the grown-up presses OK, **Then** the amount is sent once; a second OK while sending
   does nothing; on success the stepper's amount becomes this TV's next starting amount.
7. **Given** the server added the time and the plugin supports undo, **When** the confirmation shows, **Then** Undo is
   offered for 10 seconds, after which the TV returns to the kid; Undo takes the time back and keeps Leo stopped.
8. **Given** sending failed, **When** the error shows, **Then** it is inline with Retry; a grant that expired asks for
   the code again and then sends the same amount.
9. **Given** the stepper, **When** the grown-up presses Back, **Then** the menu shows again, with the grant still live.
10. **Given** Leo is on a break, **When** the menu opens, **Then** in Add time's place a line says when the break
    ends and, with the plugin's `breakEnd`, "Keep watching now" ends the break.
11. **Given** bedtime, no daily limit, or the server unreachable, **When** the menu opens, **Then** in Add time's place
    a line says so (bedtime: "allowed hours are set in Jellyfin").

---

### User Story 3 - Leo's rules on one screen (Priority: P1)

The parent opens "Leo's rules". The page shows Leo's avatar and name, his daily time (the only thing to change here),
and as plain text everything else that decides what he can watch: today's use, time in a row and breaks, allowed
hours, search and voice search, autoplay, live TV channel names, picks and what Jellyfin decides. A large line says
where to change the rest: the server's dashboard address.

**Why this priority**: today the page is titled "Kids settings", starts with household items, lists every kid, opens
minutes on "Unlimited", and has 6–7 rows that take focus and do nothing (F-K2, F-K3, F-K4, F-K6, F-K11).

**Independent Test**: open the rules page for a kid with a 60-minute limit; Daily time has focus, its list opens on 60
with Unlimited last, a change says "Saved", and D-pad stops at no other row.

**Acceptance Scenarios**:

1. **Given** the parent menu for Leo, **When** the grown-up opens "Leo's rules", **Then** the page shows only Leo,
   with his avatar and name in the title.
2. **Given** the rules page, **When** it opens, **Then** Daily time has focus and is the only row that takes focus.
3. **Given** Daily time is 60 min, **When** its list opens, **Then** 60 min is focused and Unlimited is last.
4. **Given** the grown-up picks 90 min, **When** the server saves it, **Then** the row shows 90 min and says "Saved".
5. **Given** the grant ran out on the page, **When** the grown-up picks a value, **Then** the code is asked again and
   the value is saved after it, never dropped.
6. **Given** the rules page, **When** it shows, **Then** it lists as text: today's use, time in a row and the break,
   allowed hours from Jellyfin, search and voice search on this TV, autoplay, live TV channel names (or "Live TV is
   off in Jellyfin"), picks and Jellyfin's restrictions, and the dashboard address for the rest.

---

### User Story 4 - A missing channel can be explained on the TV (Priority: P2)

Leo asks why a channel is missing. The parent opens Leo's rules and reads the channel names he has, or "Live TV is
off in Jellyfin". When Leo opens a channel that the server refuses, he sees "This channel isn't on Leo's list" with
the Grown-ups button, which leads through the code to his rules.

**Why this priority**: task H fails on the TV today (F-K6, F-E1).

**Independent Test**: refuse a channel for a kid in the plugin and open it on the TV; check the message, then
Grown-ups → code → menu → rules with the channel list.

**Acceptance Scenarios**:

1. **Given** a kid with three picked channels, **When** the rules page shows, **Then** it lists their names.
2. **Given** a kid whose Jellyfin account has Live TV turned off, **When** the rules page shows, **Then** it says "Live
   TV is off in Jellyfin".
3. **Given** a refused channel, **When** the kid tries to play it, **Then** a message page names the kid and offers
   Grown-ups (not focused first); Back returns where the kid was.

---

### User Story 5 - Each thing where its level says (Priority: P2)

The parent menu shows a header line (kid and status), then Add time, Switch kid (with 2+ kids), "Leo's rules" and
Settings, always in that order. Settings has Household (Change the parent code) and This TV (Kids on this TV, voice
search, the timer bar, the screensaver, the clock, language, subtitles). From the picker the code opens Settings.

**Why this priority**: household, kid and TV items are mixed today and the menu's items move (F-K2, F-M4, F-S5).

**Independent Test**: open the menu from a kid and from the picker; check the order and where the parent code, the
timer bar and Kids on this TV are.

**Acceptance Scenarios**:

1. **Given** a kid's parent menu, **When** it opens, **Then** the items are, in order: the header line, Add time (or
   its reason line), Switch kid (2+ kids only), "{Kid}'s rules", Settings.
2. **Given** the picker or "This TV has no kids", **When** the code passes, **Then** Settings opens straight away, with
   Kids on this TV inside.
3. **Given** Settings, **When** the grown-up opens Household › Change the parent code, **Then** it asks the current
   code, then the new one twice, and keeps the code's type.
4. **Given** Settings › This TV, **When** the grown-up changes the timer bar, **Then** the kid's bar moves.

---

### User Story 6 - The parent gate explains itself (Priority: P2)

The code entry says "Forgot it? Press OK with no code". OK with no code opens "Forgot the code?", where the
administrator can type a password or approve Quick Connect from a phone. Every gate dialog says "parent code", and
none puts focus on a line that does nothing.

**Why this priority**: today "Forgot" appears only after a wrong code, recovery means about 60 presses of typing,
the code has three names, and dead lines take focus (F-G1, F-G2, F-G4, F-G5, F-G6).

**Independent Test**: on the gate, press OK with no code; recover with Quick Connect; walk every gate state and check
the words and focus.

**Acceptance Scenarios**:

1. **Given** the code entry, **When** it shows, **Then** a line under the dots says "Forgot it? Press OK with no code".
2. **Given** the code entry is empty, **When** the grown-up presses OK, **Then** "Forgot the code?" opens without a
   server check and without counting an attempt.
3. **Given** "Forgot the code?", **When** the grown-up picks Quick Connect, **Then** a code shows to approve in another
   Jellyfin app, and approval as an administrator leads to the new code, twice.
4. **Given** any gate stage, **When** it opens, **Then** focus lands on an action, never on explanatory text.
5. **Given** any grown-up text, **When** it names the code, **Then** it says "parent code" (Spanish "código de
   padres"), never PIN or passcode.

---

### User Story 7 - Small fixes (Priority: P3)

The plugin-outdated text names 0.0.7.0. After a recovery the toast says "Parent code changed" without asking to type
the new code. Every minutes choice opens on its current value.

**Acceptance Scenarios**:

1. **Given** an outdated plugin, **When** the message shows, **Then** it says to update to 0.0.7.0 or later.
2. **Given** a recovered code, **When** the menu opens, **Then** the toast says only "Parent code changed".
3. **Given** any choice list in the grown-up screens, **When** it opens, **Then** the current value has focus.

---

### Edge Cases

- **A plugin older than 0.0.9.0:** no Undo, no "Keep watching now"; the break line gives only its end time.
- **Status unknown when the menu opens** (no answer yet, server unreachable): the reason line says the server can't be
  reached; Add time is not offered, since the server alone adds time (constitution III).
- **The amount stored for this TV is not one of the steps** (a future change of steps): the stepper opens on 15.
- **Allowed hours unknown or none:** no bedtime warning; the rules page says "Any time" for none.
- **Time added while the kid's stop screen is behind the menu:** the stop screen stays until the confirmation closes,
  so the confirmation and Undo stay on screen; then the TV returns to the kid and resumes playback.
- **Undo after the window, or Undo fails:** the time stays added; a failed Undo says so inline.
- **A rules page opened for a kid whose profile can't be read:** Daily time shows the last known value; saving offline
  says the server can't be reached and keeps the value to retry.
- **The kid's rules from Settings outside a kid** (picker): not offered; the rules are per kid and need a current kid.
- **The grant runs out while the stepper is open:** OK asks for the code again, then sends the amount.

## Requirements *(mandatory)*

### Functional Requirements

**Marking kids (US1)**

- **FR-001**: No grown-up screen on the TV MUST offer to mark a user as a kid or unmark a kid; the TV MUST NOT send
  requests that create or delete a kid profile.

**Add time (US2)**

- **FR-002**: Add time MUST be a horizontal stepper over the fixed amounts 5, 10, 15, 20, 30, 45 minutes, 1 hour,
  1 h 30 and 2 hours, without wrap-around, with the arrow at each end dimmed when it can't move.
- **FR-003**: The stepper MUST open on the last amount added on this TV, or 15 minutes when none was or the stored one
  isn't a step. The amount MUST be stored per TV, not per kid or household.
- **FR-004**: The stepper MUST name the kid and show a result line in the grown-up's terms: the clock time until which
  the kid can watch; with time already left, that time first ("Had 10 min left."); an amber warning when the time
  passes the start of the kid's bedtime; a line when a break comes before the added time ends.
- **FR-005**: OK MUST send the amount; Back MUST return to the menu with the grant still live.
- **FR-006**: While sending, the stepper MUST show that it's busy and MUST ignore further OK presses.
- **FR-007**: On success the menu MUST show "N more minutes for {Kid} · until HH:MM"; with the plugin's `bonusUndo`
  feature it MUST offer Undo for 10 seconds; then the TV MUST return to the kid, resuming the interrupted playback if
  time's up had stopped it. A kid's stop screen MUST stay behind it until then.
- **FR-008**: On failure the stepper MUST show an inline error with Retry; a refused grant MUST ask for the code again
  and then send the same amount.
- **FR-009**: When time can't be added, Add time's place in the menu MUST hold a line that says why: on a break
  (with when it ends, and "Keep watching now" with the plugin's `breakEnd` feature, which ends the break), at
  bedtime ("allowed hours are set in Jellyfin"), with no daily limit, or offline.

**The kid's rules (US3, US4)**

- **FR-010**: "{Kid}'s rules" MUST replace Kid's settings, show only the current kid with avatar and name, and be
  reached only from that kid's parent menu.
- **FR-011**: Daily time MUST be the only editable and focusable rule on the page; its choices MUST open on the current
  value with Unlimited last, and a saved change MUST say "Saved".
- **FR-012**: A change refused because the grant ran out MUST be kept, the code asked again, and the change saved after.
- **FR-013**: The page MUST show as non-focusable text: today's use, time in a row and breaks, allowed hours from
  Jellyfin, search and voice search on this TV, autoplay, live TV channel names (or "Live TV is off in Jellyfin"),
  picks, and what Jellyfin decides (rating, unrated, libraries).
- **FR-014**: The page MUST end with a large "Change {Kid}'s rules on the dashboard" and the server's dashboard
  address for the Kids plugin page.
- **FR-015**: The editors the page no longer has (sitting, search, autoplay, picks, home sections, the code type, the
  kid switches) MUST be removed from the app, with their now-unused data paths.
- **FR-016**: A live channel refused by the server MUST show the kid a message page naming the kid, with the
  Grown-ups button (never focused first) leading through the parent gate to the menu.

**Regrouping (US5)**

- **FR-017**: The parent menu MUST show, in this fixed order: a header line with the kid and their status, Add time
  (or its reason line), Switch kid (2+ kids on this TV), "{Kid}'s rules", Settings.
- **FR-018**: From the picker or "This TV has no kids", passing the gate MUST open Settings directly.
- **FR-019**: Settings MUST have a Household group with "Change the parent code" (current code, then the new one twice,
  same type) and a This TV group with Kids on this TV, voice search, the timer bar, screensaver, clock, language and
  subtitles. Reset this TV stays last, alone.

**Parent gate (US6)**

- **FR-020**: The code entry MUST show "Forgot it? Press OK with no code"; OK with no code MUST open the recovery
  without a server check or a throttle count, and Back from the recovery returns to the code entry.
- **FR-021**: Recovery and the plugin-missing sign-in MUST offer Quick Connect as well as the password.
- **FR-022**: Visible text MUST name the code "parent code" (Spanish "código de padres") everywhere; PIN and passcode
  MUST NOT appear.
- **FR-023**: No gate stage MUST put a focusable line without an action on screen; explanations are text that focus
  skips, and each stage's title says what it is.

**Small items (US7)**

- **FR-024**: The plugin-outdated text MUST name version 0.0.7.0.
- **FR-025**: After a recovery the toast MUST say "Parent code changed" only.
- **FR-026**: Every choice list in the grown-up screens MUST open with its current value focused.
- **FR-027**: Every new or changed string MUST ship in English and Spanish, following DESIGN.md's Words.

### Key Entities

- **Add-time amount for this TV**: the last amount a grown-up gave on this TV; a per-TV preference, 15 minutes when
  unset.
- **Bonus**: minutes added to a kid's today by the plugin; with plugin 0.0.9.0 it has an id that Undo takes back.
- **Kid's rules**: the kid's plugin profile (daily time editable; the rest read only) plus what Jellyfin's user policy
  decides (allowed hours, Live TV access, rating, unrated, libraries).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: No sequence of presses on the TV can delete or create a kid profile on the server.
- **SC-002**: Giving 15 more minutes from time's up takes at most 9 presses (code included) and always ends with a
  confirmation that names the kid and the end time.
- **SC-003**: Changing the current kid's daily time from 60 to 90 minutes takes at most 15 presses from the kid's home
  (code included), and an accidental OK in the choice keeps the current value.
- **SC-004**: The rules page has exactly one focus stop (Daily time) when the plugin offers allowances, and no gate
  stage has a focus stop that does nothing.
- **SC-005**: A grown-up can tell, on the TV, which live channels a kid has, or that Live TV is off for them.
- **SC-006**: No visible English or Spanish string says PIN or passcode.

## Assumptions

- The Kids plugin 0.0.7.0+ is installed; Undo and "Keep watching now" appear only with 0.0.9.0's `bonusUndo` and
  `breakEnd` features. If its contract isn't published when this is built, the client follows the shape in the
  owner's note (a bonus id returned by the bonus call, an undo call, a break-end call), kept in one place.
- The TV and the server are in the same time zone, so allowed hours (Jellyfin's access schedule, server local time)
  read the same on the TV's clock.
- The grant from the code lasts a few minutes (spec 002); the Undo window ends before it.
- Bedtime stays Jellyfin's (access schedule); the TV reads it and never writes it.
- Out of scope: a QR code, editing anything but the daily time, the "can't play" stop screen for non-live media,
  the setup flow's findings (F-U1–F-U4), Kids on this TV's findings (F-T1–F-T3) and Settings' cosmetic ones (F-S1–F-S4).
