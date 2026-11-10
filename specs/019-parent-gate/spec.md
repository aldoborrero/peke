# Feature Specification: The parent gate as on the canvas

**Feature Branch**: `019-parent-gate`

**Created**: 2026-11-09

**Status**: Draft

**Input**: User description: "019-parent-gate: the parent gate as a full-screen page per canvas board 7
(ParentGatePekeTV), with in-gate error pills (board S5), both code types, Back pill, all four palettes"

The gate's look follows the canvas "Peke TV UX" (<https://claude.ai/artifact/Qz7qEF5wga9NjR5ihsc1aq>), board 7
"ParentGatePekeTV" and the "Parent gate errors" column of board S5. The owner approved the canvas flows. Only the code
entry changes: the parent menu and the pages behind it stay as spec 006 left them.

## Decisions

Design defaults taken without asking the owner, as the goal asked (recommended option each time):

- **D1. A page over the screen it was opened from.** The gate fills the whole screen with the theme's background, as
  on board 7, but still opens over the screen that asked for it. Every caller keeps its result (the menu, Settings
  straight away without a kid, or going back to the page that asked) and nothing in navigation changes.
- **D2. The lock, not the buddy.** Board 7 draws the grown-ups lock at the top left, not the buddy, and DESIGN says
  grown-up screens have no mascot. The page uses Peke's grown-ups outline icon in "text, secondary".
- **D3. The right side follows the code type.** With remote arrows it is board 7's D-pad cross with OK in the middle.
  With number buttons it is a number pad drawn the same way: 1 to 9 in three rows, then 0 and OK, the keys in surface
  colour and OK in primary. The extra remote keys a household code may use (colour, media and channel keys) show as a
  small row under the cross, as today.
- **D4. "Back to {Kid}" is a label for the Back key, not a button.** With remote arrows the arrows type the code, so
  focus can't travel to a button. For one behaviour with both types, the pill is never focusable: it shows the Back
  icon and "Back to {Kid}" (just "Back" without a kid, e.g. from the picker) and tells the grown-up which key leaves.
  Back always leaves the gate, with any code type and with or without digits typed.
- **D5. A wrong code stays on the page.** The dots turn primary and "Wrong code" shows under them in primary; the
  entry is empty again, ready for the next try. The "Wrong code" dialog with Try again and "Forgot the code?" goes:
  the line "Forgot it? Press OK with no code" is always on the page, so recovery is still one press away, and trying
  again no longer costs a press. Dialogs that offer a choice stay (no code set, can't check, plugin missing, recovery
  only, the administrator sign-in).
- **D6. Too many tries and can't reach the server are pills on the page,** in surface colour with an icon, under the
  dots, as on S5. The text for "can't reach" is S5's, which the app already has: "Can't reach the server to check the
  parent code. Try again in a moment." A pill stays until the next key is typed.
- **D7. Words.** "Grown-ups only" for the title and "parent code" in the hint, never the board's "household code" or
  "passcode" (DESIGN "Words"). The hint follows the type: "Enter the parent code with the remote's arrows or number
  buttons." or "Enter the parent code with the remote's number buttons."
- **D8. Codes longer than four.** The page shows four dots, more as the code grows, up to eight; past eight the dots
  stay full.
- **D9. Changing the parent code uses the new page** for its first step, "Enter the current parent code": same dots,
  cross or pad, Back pill and an inline "Wrong code". It has no "Forgot it?" line, as today. The new code, entered
  twice, keeps its existing dialog (setting a code is not the gate).
- **D10. A little less height than the board.** The page keeps the board's 64 px at the sides but uses 40 px above
  and below and 24 px between the left column's parts (board: 28). In Spanish, with a three-line error pill, the
  board's spacing doesn't fit 720 px and the Back pill was squeezed.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The gate looks like board 7 (Priority: P1)

A grown-up chooses Grown-ups (the sidebar, a stop screen, the picker, a page that needs the code). The whole screen
becomes the gate: the lock, "Grown-ups only", a line on how to enter the code, four big dots, the Back pill and a
drawing of the keys that make the code. Each key typed fills a dot in the theme's "time" colour.

**Why this priority**: it is the change the owner asked for; everything else hangs off this page.

**Independent Test**: open the gate in each palette and with each code type; compare against board 7.

**Acceptance Scenarios**:

1. **Given** a kid "Lucy" on the home and a remote-arrows code, **When** Grown-ups is chosen, **Then** the page shows
   "Grown-ups only", the arrows hint, four empty dots, "Back to Lucy" and the D-pad cross with OK.
2. **Given** a number-buttons code, **When** the gate opens, **Then** the hint names the number buttons and the right
   side shows the number pad.
3. **Given** the gate, **When** two keys are typed, **Then** two dots are filled in "time" and two are outlines.
4. **Given** any of PekeTV, Caramelo, Mandarina or Papel, **When** the gate opens, **Then** it uses that palette's
   background, surface, primary, time and text colours.
5. **Given** the gate, **When** it shows, **Then** "Forgot it? Press OK with no code" is on the page.

---

### User Story 2 - Errors inside the gate (Priority: P1)

A wrong code, too many tries or a server that can't be reached is said on the page, not in a system toast or a
dialog that hides the page.

**Why this priority**: toasts are easy to miss and the board shows the errors in place.

**Independent Test**: drive the gate with a wrong code, a throttled answer and an unreachable server.

**Acceptance Scenarios**:

1. **Given** the gate, **When** a wrong code is confirmed, **Then** the entry empties, the dots show in primary and
   "Wrong code" shows on the page; no toast and no dialog.
2. **Given** a wrong code shown, **When** a key is typed, **Then** the message goes and the dot fills as usual.
3. **Given** the server answers "too many tries, wait 30 seconds", **When** the code is confirmed, **Then** a pill
   says "Too many tries. Wait 30 seconds".
4. **Given** the server can't be reached and no code is cached, **When** the code is confirmed, **Then** a pill says
   "Can't reach the server to check the parent code. Try again in a moment."
5. **Given** a wrong code shown, **When** OK is pressed with nothing typed, **Then** "Forgot the code?" opens without
   asking the server and without counting a try, and Back from it returns to the entry.

---

### User Story 3 - Back leaves, whatever the code type (Priority: P1)

**Why this priority**: with the arrows type the arrows type the code, so Back is the only way out, and the page has to
say so.

**Independent Test**: with each code type, type some keys and press Back.

**Acceptance Scenarios**:

1. **Given** the gate opened in Lucy's session, **When** it shows, **Then** the pill says "Back to Lucy" and can't take
   focus.
2. **Given** the gate opened from the picker or "This TV has no kids", **When** it shows, **Then** the pill says
   "Back".
3. **Given** the gate with keys typed, of either type, **When** Back is pressed, **Then** the gate closes and the
   screen behind is as before (the stop screen, the home, the picker).
4. **Given** the arrows type, **When** an arrow is pressed, **Then** it types a key and focus does not move.

---

### User Story 4 - Changing the parent code (Priority: P2)

**Acceptance Scenarios**:

1. **Given** Settings › Household › change the parent code, **When** it opens, **Then** "Enter the current parent
   code" shows on the same page as the gate, with "Back" and without "Forgot it?".
2. **Given** that page, **When** a wrong current code is confirmed, **Then** "Wrong code" shows on the page and the
   entry empties; no toast.

### Edge Cases

- The code type is still loading: the page shows nothing until it is known (as today), so no key is lost to the wrong
  type.
- The app goes to the background with the gate open: the gate closes, as today.
- The no-code, recovery-only and plugin-missing states, and the administrator sign-in, keep their dialogs.
- A code longer than eight keys: eight full dots.
- Spanish runs longer: the title and hint wrap to more lines, never shrink.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The code entry MUST fill the screen with the theme's background and show, on the left, the grown-ups
  lock, "Grown-ups only", a hint for the household's code type, the code dots, the errors, "Forgot it? Press OK with
  no code" and the Back pill; on the right, the drawn keys for the code type (D3).
- **FR-002**: Dots MUST be 40 canvas px circles, 18 px apart: typed ones filled in "time", empty ones a 4 px outline in
  "text, secondary"; at least four, at most eight (D8).
- **FR-003**: A wrong code MUST empty the entry and show "Wrong code" in primary on the page, with the dots in primary
  until the next key; no toast and no dialog (D5).
- **FR-004**: Too many tries and can't reach the server MUST show as S5 pills on the page until the next key (D6); no
  toast.
- **FR-005**: The Back pill MUST say "Back to {Kid}" inside a kid's session and "Back" otherwise, MUST NOT take focus,
  and Back MUST close the gate with either code type (D4).
- **FR-006**: Both code types, the empty-OK recovery (006 FR-020, FR-021), the throttle, the no-code and
  plugin-missing paths, fail-closed behaviour and every caller's result MUST stay as they are.
- **FR-007**: All new text MUST ship in English and Spanish, with "parent code" / "código de padres".
- **FR-008**: The page MUST work in PekeTV, Caramelo, Mandarina and Papel using the theme roles only.
- **FR-009**: The first step of changing the parent code MUST use the same page, without "Forgot it?" (D9).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Side by side with board 7, the PekeTV gate has the same parts in the same places and colours.
- **SC-002**: No system toast shows while a code is being entered in the gate or in the current-code step.
- **SC-003**: With either code type, one press of Back leaves the gate from any state of the entry.
- **SC-004**: The existing gate tests pass unchanged in what they check; new tests cover the in-gate errors and Back.

## Assumptions

- The throttle's seconds are shown as the server gave them, without a countdown, as today.
- The extra-key row of the remote type stays as it is today (Wholphin icons); replacing it is out of scope.
- The toasts after a recovered code is saved, in the parent menu and after Undo are not part of the code entry and
  stay; the menu waits for 007 phase 6.
- Screenshots behind the gate are the owner's: the page is checked with previews and Robolectric.
