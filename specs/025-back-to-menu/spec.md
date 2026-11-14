# Feature Specification: Back from a kid's rules returns to the grown-up menu

**Feature Branch**: `025-back-to-menu`

**Created**: 2026-11-13

**Status**: Implemented

**Input**: "Back from '{Kid}'s rules' goes back to the grown-up menu instead of the kids home; the same for Settings
opened from the menu." The owner hit it on the emulator: choosing a page in the menu closed the gate and pushed the
page over the kid's page, so Back landed on the kid and dropped the grown-up out of the menu they came from.

Spec 021 D4 says Back always goes up one level (Add time → the menu → the kid). The menu's pages are one level under
the menu, so Back from them now goes to the menu, as Add time's already does (006 FR-005).

## Decisions

Design defaults taken without asking the owner (standing rule for kids-mode work):

- **D1. The menu comes back over the page.** Back on the rules or Settings, when the menu opened them, shows the menu
  again in its own window over the page, for the same kid, focused on the item the grown-up came from ("{Kid}'s
  rules" or Settings). The page is removed once the menu closes: the menu is the level above it.
- **D2. The grant.** While the grant is live, the menu shows without the code. If it ran out, the code is asked first,
  as the pages already do, and the menu follows; Back on the code leaves to the kid.
- **D3. Leaving the menu.** Back on the menu leaves to the kid's page under it and ends the grant, as before. Choosing
  an item works as before: the page opened replaces the one the menu came back over (the rules again is the same
  page); Switch kid, Keep watching now and Add time's confirmation return to the kid or the picker as before.
- **D4. Only the page the menu opened.** Pages it opens in turn (the rules from Settings, a Settings sub-page) go back
  to it as usual, and inner levels (a Settings section's rows, a dialog) go up first. A page opened another way keeps
  its Back.
- **D5. Without a kid unchanged.** Settings opened from the picker or "This TV has no kids" (outside the kid's pages)
  keeps Back to the picker or the "no kids" page.
- **D6. Memory only.** Which page the menu opened lives in memory: after the app is killed, Back leaves the page as
  before, and the grant is gone anyway.

## User Scenarios & Testing

### User Story 1 — Back from a kid's rules (P1)

1. **Given** the rules opened from the menu, **When** Back is pressed, **Then** the menu shows for the same kid, with
   the grant live and focus on "{Kid}'s rules".
2. **Given** that menu, **When** Back is pressed, **Then** the menu closes, the grant ends and the kid's page is back.
3. **Given** the rules opened from the menu and the grant ran out, **When** Back is pressed, **Then** the code is asked;
   the right code shows the menu, Back on the code returns to the kid.

### User Story 2 — Back from Settings (P1)

1. **Given** Settings opened from the menu, **When** Back is pressed in a section's rows, **Then** focus returns to the
   section; **When** Back again, **Then** the menu shows with focus on Settings.
2. **Given** Settings opened from the picker, **When** Back is pressed, **Then** the picker is back (unchanged).

## Requirements

- **FR-001**: Back on the page the parent menu opened in the kid's pages MUST show the menu again, focused on that
  page's item, without the code while the grant is live (D1, D2).
- **FR-002**: With the grant run out, the code MUST be asked before the menu shows (D2).
- **FR-003**: Back on that menu MUST end the grant and leave to the kid's page (D3).
- **FR-004**: Inner levels and pages opened from the page MUST keep going up one level first (D4).
- **FR-005**: Settings without a kid MUST keep today's Back (D5).

## Success Criteria

- **SC-001**: Unit and Robolectric tests cover FR-001 to FR-005; the suite stays green.
