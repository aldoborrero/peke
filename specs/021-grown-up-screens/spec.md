# Feature Specification: The grown-up screens as on the canvas

**Feature Branch**: `021-grown-up-screens`

**Created**: 2026-11-11

**Status**: Draft

**Input**: "021-grown-up-screens: the grown-up screens as full-screen pages per the design boards 8–12 (parent menu, add
time, a kid's rules, grown-up shell, Settings). Spec 007 phase 6 (T056–T058), unblocked by the owner."

The look follows the canvas "Peke TV UX" (<https://claude.ai/artifact/Qz7qEF5wga9NjR5ihsc1aq>), boards 8 "Parent
menu", 9 "Add time", 10 "A kid's settings", 11 "Grown-up setup", 12 "Peke settings", and the pills of board S5. It
starts after spec 019, which made the parent gate the full-screen page of board 7.

**Layout follows the boards; content follows spec 006 and DESIGN "Behaviour".** Spec 006 changed what these screens
do on purpose (the add-time stepper, rules read-only except daily time, no "Leave kids mode", Settings straight away
without a kid), and that stays. Where a board and 006 or DESIGN disagree, 006 and DESIGN win and the decision is
recorded below.

## Decisions

Design defaults taken without asking the owner (the owner's standing rule for kids-mode work):

- **D1. Pages, not dialogs.** The parent menu and Add time stay stages of the gate and fill the screen in their own
  window, as the gate does (019 D1), so every caller keeps its result. A kid's rules and Settings are destinations and
  already fill the screen; they get the boards' layout.
- **D2. The menu's content is 006's.** Header as on board 8: the kid's avatar, "Grown-up menu", the kid's name, and on
  the right the time left today in a pill in "time" and the sitting line under it. Then one row of cards in 006's
  fixed order: Add time, Switch kid (2+ kids), "{Kid}'s rules", Settings. Up to four cards, so they share the width
  (the board has three). No "Leave kids mode" (Reset this TV in Settings is the only way out).
- **D3. Add time that can't be used stays in its place.** Its card shows the add-time icon dimmed, "Add time" and the
  reason ("Break: ends at 18:20", "Leo has no daily limit", …) and takes no focus (006 F-M4). During a break the
  plugin can end, the card is "Keep watching now", focusable, with the reason as its line.
- **D4. Back pills name the Back key.** As on the gate (019 D4), "Back to {Kid}" on the menu and "Back" on Add time
  are labels, never focus stops. Back always goes up one level: Add time → the menu → the kid (and the grant ends).
  *(Spec 023 removed the pills; Back is unchanged, and FR-003 keeps only that part.)*
- **D5. Add time keeps 006's stepper** (5 min to 2 h, no wrap-around, the arrow at each end dimmed, opens on the last
  amount given on this TV). Drawn on board 9's terms: one wide focused tile with ‹ amount ›, then the forecast in a
  surface box with the time-left icon ("Can watch until 19:15" in "time"). The bedtime warning uses "time" and the
  bedtime icon instead of the old off-palette amber; the break line uses the break icon.
- **D6. Added is an S5 pill.** After OK, the page shows "15 more minutes for Leo · until 19:15" as board S5's pill
  with Done (focused) and "Undo (10)". Undoing shows "The time was taken back" (or "already taken back") as a pill on
  the same page, then back to the kid. No toasts.
- **D7. A kid's rules on board 10's layout, 006's content.** Title "{Kid}'s rules" with the avatar and "Saved on the
  server, so every TV follows them". Left: rows on surface. Daily time is the only focus stop and a stepper as on the
  board (‹ 60 min ›, value in "time"): Left/Right change it, OK saves, Back leaves without saving a value not yet
  confirmed. The other rows are text; they scroll with Up and Down from Daily time, which keeps the focus, since
  in Spanish or with many picks they don't fit the screen. Right: "Set in the Jellyfin dashboard" with what Jellyfin decides and the
  dashboard's address. No home sections and no toggles (006 keeps those on the dashboard).
- **D8. Settings in two columns (board 12).** Left: "Settings" and the sections DESIGN lists, as pills: Household,
  This TV, Playback, Advanced, About, Reset this TV. Right: a line about the section, then its rows. Focus starts on
  the first section; moving through the sections shows each one; Right enters the rows, Left returns. Back from the
  rows returns to the sections; Back from the sections leaves Settings. The old "More" group goes: Playback and
  Advanced are sections now.
- **D9. Without a kid, Settings carries board 11's header.** From the picker or "no kids", the code opens Settings
  (006). The page then has board 11's header above the two columns: the buddy, "Set up this TV", "Set up this TV for
  the kids. Grown-ups watch with another Jellyfin app." and a pill with the Kids plugin's state. Board 11's cards
  (Kids settings, Switch user, Licenses, plugin missing) aren't added: Kids on this TV is in This TV, the license is
  in About, and the plugin's state is the pill.
- **D10. One row style for Settings.** The preference rows (click, switch, choice, slider) take board 12's row: surface,
  22 px corners, title 22/900, summary 16 in "text, secondary", grown-up focus (border and ring, no scale), and a
  switch drawn as on the board (64×36, on in "secondary"). Wholphin's preference logic stays underneath; only the
  rows' look changes, and they only show to grown-ups.
- **D11. Toasts become S5 pills.** "Keep watching now" that fails, the parent code changed (Settings › Household,
  `Done`), a recovered code saved and the undo results show as S5 pills on the grown-up page that is showing, for four
  seconds. The pill sits at the bottom of the page, above nothing focusable.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The parent menu is board 8 (Priority: P1)

After the parent code in Lucy's session, the whole screen is the grown-up menu: Lucy's avatar and name, the time she
has left, and big cards for what a grown-up can do, Add time focused.

**Independent Test**: open the menu with a kid who has a daily limit, one on a break, one with no limit, and with
two kids; compare with board 8.

**Acceptance Scenarios**:

1. **Given** Lucy with 45 minutes left and a sitting limit, **When** the code passes, **Then** the page shows Lucy's
   avatar, "Grown-up menu", "Lucy", a pill "45 minutes left today" and the sitting line, and cards Add time,
   "Lucy's rules" and Settings, with Add time focused.
2. **Given** two kids on the TV, **Then** Switch kid is a card between Add time and the rules.
3. **Given** Lucy on a break the plugin can end, **Then** the first card is "Keep watching now" with "Break: ends at
   18:20"; without breakEnd it is "Add time" with the reason and isn't focusable, and focus starts on the next card.
4. **Given** the menu, **When** Back is pressed, **Then** the menu closes, the grant ends and Lucy's screen is back.
5. **Given** "Keep watching now" fails, **Then** a pill says why on the menu; no toast.

### User Story 2 - Add time is board 9 (Priority: P1)

**Acceptance Scenarios**:

1. **Given** the menu, **When** Add time is chosen, **Then** the page shows "Grown-up menu · Lucy", "Add time for Lucy",
   the stepper on the last amount given, the forecast box and "Back".
2. **Given** the stepper at 5 min, **Then** the left arrow is dimmed; Left does nothing.
3. **Given** OK, **Then** the page shows the pill "15 more minutes for Lucy · until 19:15" with Done focused and
   Undo counting down; Undo shows "The time was taken back" and returns to Lucy.
4. **Given** Add time, **When** Back is pressed, **Then** the menu shows again with the grant live.

### User Story 3 - A kid's rules is board 10 (Priority: P2)

**Acceptance Scenarios**:

1. **Given** Lucy's rules, **Then** the title, avatar and the server line show; Daily time is focused, its value
   between ‹ › in "time".
2. **Given** Daily time 60 min, **When** Right then OK, **Then** the next value is saved and "Saved" shows.
3. **Given** a changed but unsaved value, **When** Back is pressed, **Then** the page leaves and nothing is saved.
4. **Given** the page, **Then** no other row takes focus.

### User Story 4 - Settings is board 12, and board 11 without a kid (Priority: P2)

**Acceptance Scenarios**:

1. **Given** Settings from the menu, **Then** the left column shows "Settings" and the six sections, Household focused
   and its rows on the right.
2. **Given** focus on a section, **When** Down, **Then** the next section is focused and its rows show.
3. **Given** a section, **When** Right, **Then** focus is on its first row; **When** Back, **Then** focus is on the
   section again; **When** Back again, **Then** Settings closes.
4. **Given** Settings from the picker, **Then** board 11's header (buddy, "Set up this TV", the line, the plugin pill)
   is above the columns.
5. **Given** Household › Change the parent code done, **Then** a pill says "Parent code changed"; no toast.

### Edge Cases

- No kid name known (a session without a user): the menu says "Grown-ups" as the title and no avatar.
- No daily limit: no time pill; the sitting line shows alone if there is one.
- The plugin is gone: board S5's banner "This server no longer has the Kids plugin" under the menu's header.
- Spanish runs longer: card lines and titles wrap to two lines, never shrink.
- A grant that runs out on the rules page: the gate asks again, then saves (unchanged).
- Settings sections that need a dialog (choices, the parent code change, the voice search consent) keep their dialogs.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The parent menu, Add time and its confirmation MUST fill the screen with the theme's background (D1).
- **FR-002**: The menu MUST show 006's items in 006's order, as cards (D2, D3), first focus on the first focusable card.
- **FR-003**: The Back pills MUST be labels for the Back key and Back MUST go up one level from every page (D4).
- **FR-004**: Add time MUST keep 006's steps, forecast, sending, errors and Undo (D5, D6).
- **FR-005**: A kid's rules MUST keep daily time as the only focusable and changeable rule (D7).
- **FR-006**: Settings MUST show DESIGN's sections in two columns with the focus rules of D8, and board 11's header
  without a kid (D9).
- **FR-007**: No Toast MUST show on the menu, Add time, the confirmation, the rules, Settings' parent code change or
  after a recovered code; S5 pills instead (D11).
- **FR-008**: The pages MUST use the theme roles only, in PekeTV, Mandarina, Caramelo and Papel, Nunito, Peke's icons
  (no Material icons) and the grown-up focus (border and ring, no scale).
- **FR-009**: New text MUST ship in English and Spanish with DESIGN "Words" ("parent code").
- **FR-010**: Fail-closed behaviour, the grant rules (leaving the menu ends it, pages need it) and every caller's
  result MUST stay as they are.

## Success Criteria *(mandatory)*

- **SC-001**: Side by side with boards 8–12, each page has the boards' parts in the same places, except where a
  decision above says otherwise.
- **SC-002**: No system toast shows on these screens.
- **SC-003**: From any of these pages, Back reaches the kid (or the picker) in at most three presses and never traps.
- **SC-004**: Unit tests stay green; new Robolectric tests cover each page's first focus, Back and main actions.

## Assumptions

- Board 11's "Leave kids mode", "Switch user" and plugin version on the cards are out (006, DESIGN "Reset this TV").
- Board 10's home sections and toggles are out (006 US3/US4: they're on the dashboard).
- The setup boards 21–26 and phones (spec 020) are out of scope.
- Wholphin's settings pages reached from these rows (ExoPlayer, MPV, subtitles, screensaver) keep their own layout
  and get the new row style.
