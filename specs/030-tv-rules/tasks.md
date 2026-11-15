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
