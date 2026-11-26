# Tasks: Edit a kid's rules on the TV

**Input**: [plan.md](plan.md), [spec.md](spec.md)

- [X] T001 `RuleChange`, `save(change)`, `sessionLimitEditable` and the choice lists (`kids/ui/rules/KidRulesViewModel.kt`)
- [X] T002 `StepperRow`, `RuleEditors` and the read-only rows that remain (`kids/ui/rules/KidRulesPage.kt`)
- [X] T003 [P] Strings in English and Spanish (`res/values/strings.xml`, `res/values-es/strings.xml`)
- [X] T004 [P] Tests: `KidRulesViewModelTest` (each rule's PUT body changes only its field, autoplay off keeps the cap,
  cap held to 1–10, sitting limit only with `sessionLimit`, grant ran out for autoplay and search), `KidRulesPageTest`
  (four focus stops in order, Up/Down past the ends, each stepper saves, unsaved value dropped on leaving, sitting as
  text without the feature, "Saved")
- [X] T005 DESIGN.md Screens: a kid's rules

## Second round

- [X] T006 Unlimited autoplay: feature, cap 0 kept, policy, rules stepper and save guard
- [X] T007 [P] "{Kid}'s day": view model, page, destination, menu card, Back to the menu, time's up
- [X] T008 Rules page without today's use
- [X] T009 [P] Strings in English and Spanish; DESIGN.md (menu, rules, the day)
- [X] T010 [P] Tests: `KidsAutoplayPolicyTest` (0 never stops, no chip), `KidRulesViewModelTest` (No limit only with
  the feature, PUT carries 0, never sent without it), `KidRulesPageTest` (No limit after 10, no usage figures),
  `KidDayPageTest` (figures, one focus stop, unreachable, status mapping, view model), `GrownUpMenuReturnTest` and
  `BackToMenuTest` (menu → day → Back → menu focused on the day), `KidsParentGateViewModelTest` (openDay keeps the
  grant)
