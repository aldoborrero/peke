# Implementation Plan: Back on the kids home

**Branch**: `024-home-back` | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)

## Summary

In `ui/nav/NavDrawer.kt`, `backOpensNavDrawer` becomes `backAsksToLeave` (home, drawer closed) and
`backClosesNavDrawer` holds on every page with the drawer open. `DrawerBackHandler` shows the new `KidsLeaveDialog`
(`kids/ui/KidsLeaveDialog.kt`); its "Leave" disables the handler and dispatches Back again so the system quits as before.

## Technical Context

Kotlin, Compose for TV; no new dependencies. Tests: JUnit4 + Robolectric Compose rule (existing `DrawerBackCloseTest`,
`NavDrawerBackTest`, now driving the real `DrawerBackHandler`).

## Constitution Check

- **IV. Parent-gated**: leaving stays ungated; the dialog asks, no code. Wording clarified (2.1.1, PATCH).
- **V. TV-first, D-pad complete**: the dialog is D-pad only, focus starts on the safe choice.
- **VI. Private by default**: nothing logged or sent. No new dependency; MPV and decoders untouched.
- **VIII. Tested**: Robolectric tests for each acceptance case.

## Files

- `app/src/main/java/dev/aldoborrero/peke/ui/nav/NavDrawer.kt`: Back rules and handler.
- `app/src/main/java/dev/aldoborrero/peke/kids/ui/KidsLeaveDialog.kt`: new.
- `app/src/main/res/values{,-es}/strings.xml`: `kids_leave_*`.
- Tests: `ui/nav/NavDrawerBackTest.kt`, `ui/nav/DrawerBackCloseTest.kt`.
- Docs: constitution, `AGENTS.md`, `design/DESIGN.md` "Behaviour".
