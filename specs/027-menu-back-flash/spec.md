# Feature Specification: No flash of the page below when Back leaves the grown-up menu

**Feature Branch**: `027-menu-back-flash`

**Created**: 2026-11-14

**Status**: Implemented

**Input**: "Back on the grown-up menu, when it was shown again over a page, goes straight to the kid's page." The owner
saw it on a Chromecast: with the menu shown again over "{Kid}'s rules" (spec 025), Back faded the menu out, the rules
page showed fully for a moment, then it went. The same for Settings opened from the menu.

The cause: the menu's window closed at once, and only then did the page leave with the kid pages' fade, so the page
stood alone on screen for the length of the fade (longer on a slow TV box, which also composes the kid's page then).

## Decisions

Design defaults taken without asking the owner (standing rule for kids-mode work):

- **D1. The menu stays until the page has gone.** When closing the menu removes the page under it (spec 025 D1), the
  menu keeps covering the screen until the page has left, its fade included, and only then closes, uncovering the
  kid's page already drawn. The page is removed in the same step as before, so the back stack is at the kid's page at
  once; the kid pages' fade is unchanged for every other move.
- **D2. Nothing reacts meanwhile.** While it covers, the menu ignores keys: a second Back or a press on an item does
  nothing. The grant still ends when Back closes the menu, as before.
- **D3. Other ways out of the menu.** Every close that removes the page covers the same way (Back, the code asked
  again then dismissed, Keep watching now, Add time's confirmation, the app going to the background). A choice that
  opens another page (Settings over the rules) keeps the menu over the old page until it has gone, so the new page
  shows without the old one in between. Choosing the same page again (the rules over the rules) leaves the page where
  it is, and the menu closes at once, as before.
- **D4. Back to the menu unchanged.** Back from the page to the menu, the menu's focus, inner levels going up first and
  pages the menu didn't open keep spec 025's behaviour.

## User Scenarios & Testing

### User Story 1 — Back on the menu over a page (P1)

1. **Given** the menu shown again over the rules, **When** Back is pressed, **Then** the back stack is at the kid's page
   in one step, the grant ends, and the rules page is never on screen without the menu over it.
2. **Given** the menu shown again over Settings, **When** Back is pressed, **Then** the same.
3. **Given** the menu covering while the page leaves, **When** Back or OK is pressed, **Then** nothing happens.

## Requirements

- **FR-001**: Closing the menu shown again over a page MUST remove the page at once and keep the menu drawn until the
  page has left the screen (D1).
- **FR-002**: The menu MUST ignore keys while it covers (D2).
- **FR-003**: Back from the page to the menu MUST stay as spec 025 has it (D4).

## Success Criteria

- **SC-001**: Robolectric tests on the kid pages' `NavDisplay` show, frame by frame, that the page is never composed
  without the menu after Back on the menu, for the rules and Settings; spec 025's tests stay green.
