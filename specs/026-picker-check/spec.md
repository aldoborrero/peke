# Feature Specification: Kid picker check mark on top, Down to Continue

**Feature Branch**: `026-picker-check`

**Created**: 2026-11-14

**Status**: Implemented

**Input**: "In setup's kid picker ('¿Quién ve esta tele?') the selected kid's check mark is cut by the circle and
covered by the ring, and Down from the kids focuses 'Volver a comprobar'." The owner saw both on a Chromecast.

The check was drawn inside the avatar's round surface, which clips its content and draws the 3 dp selected border over
it. Down had no explicit target, so focus went to whichever button sat nearer the kid: with one kid in the middle,
"Check again".

## Decisions

Design defaults taken without asking the owner (standing rule for kids-mode work):

- **D1. On the edge.** The badge (the check mark, or the lock on the kids picker and "Kids on this TV") is 40 dp, its
  centre on the avatar's edge at 45° towards the bottom end, drawn over the selected border and the focus ring, on the
  surface colour so it reads over both.
- **D2. Grows with focus.** While the avatar is focused the badge grows with it, by the same scale and animation, about
  the avatar's centre, so it stays on the edge.
- **D3. Down to the main action.** Down from any kid goes to Continue when it can be pressed (a kid is picked and
  nothing is loading), else to Check again. Up from the buttons is unchanged.
- **D4. Only setup's picker.** The other screens using the card get the moved badge (D1, D2) but keep their focus
  order.

## User Scenarios & Testing

### User Story 1 — Picking a kid (P1)

1. **Given** a kid is picked, **When** the picker shows, **Then** the check mark shows whole on the avatar's ring,
   focused or not.
2. **Given** a kid is picked and focused, **When** Down is pressed, **Then** Continue is focused.
3. **Given** no kid is picked, **When** Down is pressed, **Then** Check again is focused.

## Requirements

- **FR-001**: The card's badge MUST be drawn outside the avatar's clipped surface, centred on its edge at the bottom
  end, above its border and focus ring (D1).
- **FR-002**: The badge MUST scale with the focused avatar about the avatar's centre (D2).
- **FR-003**: Down from a kid in setup's picker MUST focus Continue when enabled, else Check again (D3).

## Success Criteria

- **SC-001**: Robolectric tests cover FR-001 to FR-003; the suite stays green.
