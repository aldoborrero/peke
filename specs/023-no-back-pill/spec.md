# Feature Specification: No Back pill on grown-up screens

**Feature Branch**: `023-no-back-pill`

**Created**: 2026-11-13

**Status**: Done

**Input**: "023-no-back-pill: remove the Back pill from the parent gate and grown-up screens (brief)"

The parent gate (spec 019, board 7), the parent menu and Add time (spec 021, boards 8–9) showed a pill, "Back to
{Kid}" or "Back", that never took focus and only named the remote's Back key. The owner saw it on the emulator on
2026-11-13 and found it adds nothing: with the remote, a grown-up already knows how to go back. This departs from
boards 7–9 on purpose.

## Decisions

- **D1. No Back pill.** The gate, the parent code change, the parent menu and Add time show no Back pill. The space
  it took closes up; nothing replaces it. This supersedes 019 D4 / FR-005 and 021 D4 / FR-003 as far as the pill
  goes.
- **D2. Back is unchanged.** Back still leaves the gate with any code type, and still goes up one level: Add time →
  the menu → the kid.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A grown-up sees the page without the pill (Priority: P1)

**Independent Test**: open the gate, the menu and Add time; none shows "Back to {Kid}" or "Back", and Back works as
before.

**Acceptance Scenarios**:

1. **Given** the gate opened in Lucy's session, **When** it shows, **Then** there is no "Back to Lucy" and Back leaves.
2. **Given** the parent menu, **When** it shows, **Then** there is no Back pill; a notice pill still shows on the right.
3. **Given** Add time, **When** it shows, **Then** there is no Back pill; sending and the buttons stay where they were.

### Edge Cases

- The parent code change (Settings, Household) uses the same page as the gate: no pill there either.

## Requirements *(mandatory)*

- **FR-001**: The gate, the parent code change, the parent menu and Add time MUST NOT show a Back pill (D1).
- **FR-002**: Back MUST behave as before on each of them (D2).
- **FR-003**: Text used only by the pill MUST go, in English and Spanish.

## Success Criteria *(mandatory)*

- **SC-001**: On the four pages, no element names the Back key.
- **SC-002**: From any of them, Back reaches the kid (or the picker) in the same presses as before.
- **SC-003**: Unit tests stay green.

## Assumptions

- Canvas boards 7–9 keep their Back pill; the app departs from them (DESIGN "Behaviour" records it).
- Focus, kid screens and everything else on these pages are out of scope.
