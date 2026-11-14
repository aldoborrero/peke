# Tasks: Back from a kid's rules returns to the grown-up menu

**Input**: [plan.md](plan.md), [spec.md](spec.md)

- [X] T001 `GrownUpMenuReturn` and `BackToMenu` (`kids/ui/GrownUpMenuReturn.kt`)
- [X] T002 Gate: open the rules and Settings through it, `returnToMenu()`, `returnTo` focus
  (`kids/ui/KidsParentGateViewModel.kt`, `KidsParentGate.kt`, `GrownUpMenuPage.kt`)
- [X] T003 Guard wraps grown-up kid pages (`kids/ui/GrownUpGuard.kt`, `ui/nav/DestinationContent.kt`)
- [X] T004 [P] Tests: `GrownUpMenuReturnTest` (which page, removal, Settings → rules, apart), `BackToMenuTest`
  (menu with focus on rules or Settings, Back on the menu, inner level first, other pages unchanged),
  `KidsParentGateViewModelTest` (live grant → menu, expired → code, late lock, picker unchanged)
- [X] T005 DESIGN.md Behaviour: Back from the menu's pages
