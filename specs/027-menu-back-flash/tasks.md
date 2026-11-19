# Tasks: No flash of the page below when Back leaves the grown-up menu

**Input**: [plan.md](plan.md), [spec.md](spec.md)

- [X] T001 Test first: `MenuBackFlashTest` on `KidPages` with `GrownUpMenuReturn`, frame by frame (rules, Settings,
  keys while covering, rules over rules, Back to the menu unchanged)
- [X] T002 `GrownUpMenuReturn.close` returns whether it removed the page; `BackToMenu` covers until the page has gone
  (`kids/ui/GrownUpMenuReturn.kt`, `GrownUpGuard.kt`)
- [X] T003 `GrownUpWindow` ignores keys while covering (`kids/ui/GrownUpKit.kt`)
- [X] T004 DESIGN.md Behaviour note
