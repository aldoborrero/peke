# Tasks: The grown-up screens as on the canvas

**Input**: [plan.md](plan.md), [spec.md](spec.md)

## Phase 1: Foundation

- [X] T001 Grown-up kit: page frame, header, card, row, Back pill (moved from `ParentCodePage`), S5 pill, notices
  singleton and host (`kids/ui/GrownUpKit.kt`)
- [X] T002 Strings in English and Spanish for every new line (`values/strings.xml`, `values-es/strings.xml`)

## Phase 2: US1–US2 (P1) — the menu and Add time

- [X] T003 Menu page per board 8 with 006's items, D2–D4 (`kids/ui/GrownUpMenuPage.kt`)
- [X] T004 Add time per board 9, Added as S5 pill with Undo, D5–D6 (`kids/ui/AddTimeStepper.kt`)
- [X] T005 Gate stages use the pages; toasts → notices (`kids/ui/KidsParentGate.kt`, `ParentCodeChange.kt`)
- [X] T006 [P] Robolectric tests: menu cards, order, first focus, unavailable add time, Back; Add time arrows, OK,
  Added pill, Undo, Back (`GrownUpMenuPageTest.kt`, `AddTimePageTest.kt`)

## Phase 3: US3 (P2) — a kid's rules

- [X] T007 Rules per board 10, daily time stepper (`kids/ui/rules/KidRulesPage.kt`)
- [X] T008 [P] Tests: only daily time focusable, Right + OK saves, Back doesn't (`KidRulesPageTest.kt`)

## Phase 4: US4 (P2) — Settings

- [X] T009 Section list for Peke settings (`preferences/AppPreference.kt`)
- [X] T010 Two-column page, board 11 header without a kid (`ui/preferences/PekeSettingsPage.kt`, `GrownUpGuard.kt`)
- [X] T011 Row style for click, switch and slider preferences (`ui/preferences/*Preference.kt`)
- [X] T012 [P] Tests: sections, focus in and out, Back up one level, header without a kid (`PekeSettingsPageTest.kt`)

## Phase 5: Polish

- [X] T013 DESIGN: stale lines 5–6, Behaviour for the grown-up pages (`design/DESIGN.md`); 007 T056–T058
- [X] T014 `nix fmt`, pre-commit on changed files, unit tests, debug build; previews in four palettes and Spanish
- [X] T015 Report (`reports/report.md`)
