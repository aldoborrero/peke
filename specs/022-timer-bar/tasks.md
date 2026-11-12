# Tasks: The kids' timer bar only while playing, its timer left, centre or right

**Input**: [plan.md](plan.md), [spec.md](spec.md)

## Phase 1: Setting

- [X] T001 `KidsTimerLabelPlacement` and `KidsTimerBarSetting.placement` (`kids/timer/KidsTimerBar.kt`)
- [X] T002 [P] Tests: old stored position, placement default and set (`KidsTimerBarSettingTest.kt`)

## Phase 2: US1 — the bar only on the player

- [X] T003 `timeLeftBarShown`, local band for the player warning (`kids/ui/KidsTimerEffects.kt`)
- [X] T004 Drop the sidebar pages' band (`ui/nav/ApplicationContent.kt`, `ui/nav/NavDrawer.kt`)
- [X] T005 [P] Tests (`TimeLeftBarShownTest.kt`)

## Phase 3: US2 — left, centre, right

- [X] T006 `placement` on `TimeLeftBar`, previews (`kids/ui/TimeLeftBar.kt`)
- [X] T007 Settings row, strings en/es (`AppPreference.kt`, `PreferencesContent.kt`, `PreferencesViewModel.kt`,
  `TimerPlacementPreference.kt`, `strings.xml`)
- [X] T008 [P] Tests (`TimeLeftBarPlacementTest.kt`, `TimerPlacementPreferenceTest.kt`, `PekePreferencesTest.kt`)

## Phase 4: Polish

- [X] T009 DESIGN "Shape and space" and "Behaviour" (`design/DESIGN.md`)
- [X] T010 `nix fmt`, pre-commit on changed files, unit tests, debug build
- [X] T011 Report (`reports/report.md`)
