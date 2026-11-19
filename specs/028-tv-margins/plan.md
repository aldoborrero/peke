# Implementation Plan: TV-safe margins

**Branch**: `028-tv-margins` | **Date**: 2026-11-14 | **Spec**: [spec.md](spec.md)

## Summary

`ui/theme/SafeArea.kt` gains the page edge (58/28 dp) and the grown-up page padding built on it. `FocusRoom` gains an
`end` (default `side`) and `toScreenEnd()`, which the kids home rows use, so the last card stops inside the edge while
the row still runs to the screen's edge. Grown-up pages, Watch live and the home clock take the shared values.

## Technical Context

Kotlin, Compose for TV; no new dependencies. Tests: JUnit4 (`TvSafeMarginsTest`, plain JVM), existing
`KidsRowFocusGeometryTest`.

## Constitution Check

- **V. TV-first, D-pad complete**: focus stays on screen; no navigation change.
- **VI. Private by default**: nothing logged or sent. MPV and decoders untouched.
- **VIII. Tested**: the geometry is unit-tested; emulator screenshots before and after.

## Files

- `ui/theme/SafeArea.kt`, `ui/theme/FocusRoom.kt`
- `ui/main/HomePage.kt` (kids rows, clock), `kids/livetv/KidsLiveGuide.kt`
- `kids/ui/GrownUpMenuPage.kt`, `kids/ui/AddTimeStepper.kt`, `kids/ui/rules/KidRulesPage.kt`,
  `ui/preferences/PekeSettingsPage.kt`
- Test: `app/src/test/java/dev/aldoborrero/peke/ui/theme/TvSafeMarginsTest.kt`
- Docs: `design/DESIGN.md` "Shape and space"
