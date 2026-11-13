# Feature Specification: Back on the kids home closes the sidebar, then asks before leaving

**Feature Branch**: `024-home-back`

**Created**: 2026-11-13

**Status**: Implemented

**Input**: "On the kids home, Back closes the open sidebar, and with the sidebar closed it asks 'Leave Peke?' instead of
quitting at once." The owner's decision of 2026-11-13 (option "Cerrar menú + diálogo").

Before: on the home, Back opened the sidebar and a second Back quit with no question (specs 016 and 017). An open
sidebar couldn't be closed with Back, and a kid could leave by accident.

## User Scenarios

### US1 · Back closes the open sidebar on the home (P1)

**Acceptance**:

1. **Given** the kids home with the sidebar open (opened with Left), **When** Back, **Then** the sidebar closes and
   focus returns where it was on the page; the app stays.

### US2 · Back on the home asks before leaving (P1)

**Acceptance**:

1. **Given** the kids home with the sidebar closed, **When** Back, **Then** "Leave Peke?" shows with "Keep watching"
   focused and "Leave"; the sidebar stays closed.
2. **Given** the question, **When** "Keep watching" or Back, **Then** it closes and the kid stays on the home.
3. **Given** the question, **When** "Leave", **Then** the app quits as Back did before this spec.

### Unchanged

- Other pages keep spec 017: with the sidebar open Back closes it; with it closed Back goes back a page.
- Left still opens the sidebar. Grown-up pages, the parent gate, the kid picker, setup and the player are unchanged.

## Requirements

- **FR-001**: On any page, Back with the sidebar open MUST close it and return focus to the page.
- **FR-002**: On the kids home with the sidebar closed, Back MUST show "Leave Peke?" and MUST NOT open the sidebar.
- **FR-003**: "Keep watching" MUST have focus first; it and Back MUST dismiss the question.
- **FR-004**: "Leave" MUST quit as Back on the home did before (the Back goes on to the system).
- **FR-005**: The question MUST NOT ask for the parent code (constitution IV: leaving isn't gated).
- **FR-006**: Strings in English and Spanish; the look follows DESIGN (palette roles, Nunito, Peke focus).

## Decisions

Design defaults taken without asking the owner:

- **D1. "Leave" hands the Back on to the system.** The handler steps aside and dispatches Back again, so the system
  does exactly what it did before (on Android 12+ the launcher task goes to the background), rather than calling
  `finish()`.
- **D2. The dialog reuses the kid dialog's panel** (KidsSeasonsDialog: 640 px panel, 36 px corners and padding) and
  the grown-up kit's pill buttons (`GrownUpButton`: "Keep watching" primary, "Leave" surface). The panel is in the
  background colour, not surface, so "Leave" reads as a button on it, as on the grown-up pages; the dim behind the
  dialog sets it off from the page. No Wholphin Material dialog.
- **D3. Words**: "Leave Peke?" / "Keep watching" / "Leave"; "¿Salir de Peke?" / "Seguir viendo" / "Salir". No icon or
  buddy: the question is short and the buttons say what happens next (DESIGN "Words").
- **D4. Same handler, every page.** `backClosesNavDrawer` no longer depends on the page; `backAsksToLeave` replaces
  `backOpensNavDrawer`.
- **D5. Constitution 2.1.1 (PATCH).** Principle IV's wording says Back on the kids home asks before quitting; AGENTS.md
  and DESIGN "Behaviour" follow.

## Success Criteria

- **SC-001**: Robolectric tests cover US1, US2's three cases and the unchanged pages.
- **SC-002**: `:app:testDebugUnitTest :app:assembleDebug` and pre-commit pass.
