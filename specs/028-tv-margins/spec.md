# Feature Specification: TV-safe margins on the home rows and grown-up pages

**Feature Branch**: `028-tv-margins`

**Created**: 2026-11-14

**Status**: Implemented

**Input**: "On a real TV nothing that matters touches or crosses the screen edge: the focused card in a home row keeps a
margin on the right as on the left, and grown-up pages keep their content and focus rings inside the TV-safe area."
Seen by the owner on a Chromecast (1920×1080, 320 dpi, overscan off on the TV).

Before: a kids home row scrolled to its last card put that card's end on the screen's right edge, so its scale-up,
border and ring ran off the screen. The grown-up pages started their content 54 dp (108 px) from the sides, so a
focused card's ring reached 44 dp (88 px); each page had its own numbers (54, 48 or 36 dp).

## User Scenarios

### US1 · A home row's last card stays on screen (P1)

1. **Given** the kids home, **When** focus moves to the last card of any row, **Then** the card, its scale-up, border
   and ring are fully on screen, and the ring ends at least 58 dp (116 px) from the right edge.
2. **Given** a row with more cards than fit, **When** it scrolls, **Then** cards past the right edge still peek there
   (the row runs to the edge), and earlier cards peek cut at the row's start line.

### US2 · Grown-up pages keep their rings inside the TV-safe area (P1)

1. **Given** the grown-up menu, a kid's rules, Add time or Settings, **When** an element at the page's side or bottom
   has focus, **Then** its ring stays at least 58 dp from the sides and 28 dp from the top and bottom.

### US3 · Other full-screen kid pages (P2)

1. **Given** Watch live, **When** a channel row has focus, **Then** its ring stays 58 dp from the right edge.
2. **Given** the kids home header, **Then** the clock ends 58 dp from the right edge.

### Unchanged

- The player (its clock, title, bar, buttons and time chips keep `SafeArea.Horizontal`/`Vertical`, 48/27 dp), colours,
  type, the sidebar's own layout, the parent gate and the code pages (centred, far from the edges).

## Requirements

- **FR-001**: The theme MUST hold one TV-safe edge for pages, `SafeArea.EdgeHorizontal` 58 dp and
  `SafeArea.EdgeVertical` 28 dp (Android TV's overscan guidance), and nothing focused on a page crosses it.
- **FR-002**: Grown-up pages MUST take their padding from one shared value, `SafeArea.page`: the edge plus a grown-up
  focus (border and ring, no scale-up), 68.5 dp by 38.5 dp.
- **FR-003**: A kids home row MUST still reach the screen's right edge, and MUST end its last card where the card's
  focused scale-up, border and ring end `PekeFocus.Spare` (4 dp) inside the edge (`FocusRoom.toScreenEnd`).
- **FR-004**: A row's start MUST keep lining its first card up with the title above it.

## Decisions

Design defaults taken without asking the owner:

- **D1. 58/28 dp for pages, 48/27 dp for the player.** The goal names Android TV's 58 dp sides and 28 dp top and
  bottom; DESIGN said 48/27 (Compose's playback guidance). Pages now keep 58/28; the player is out of scope and keeps
  48/27 until the owner decides (open question in the handoff).
- **D2. The end margin is the safe edge, not a mirror of the start.** The page's content starts 114 dp from the screen's
  left (78 dp sidebar plus 36 dp). Mirroring that on the right would leave a wide empty band; the last card's ring
  ends 62 dp (124 px) from the right edge instead, and the unfocused card at 58 dp plus the card's room (about 82 dp
  for wide cards). The header clock ends at 58 dp, nearly on the ring's line.
- **D3. The row's start is left as it was.** The row bleeds a card's room past the title's line, so the first card's
  ring fits; once scrolled, the previous card peeks cut at that line (x ≈ 181 px), which says there is more to the
  left. It sits right of the sidebar, inside the safe area.
- **D4. Grown-up pages take one padding**, `SafeArea.page` (68.5 by 38.5 dp), in place of 72/56 and 64/44 canvas px.
  The vertical value is a touch less than the menu's 56 px and more than Rules' and Settings' 44 px.
- **D5. Not changed here**: kid details pages (series, film, seasons), search and favourites keep their 36 dp side
  padding: their rows' rings can end about 16 dp inside the edge. They touch the files of a parallel task (029) and
  need their own check; listed as follow-up.
