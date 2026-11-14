# Implementation Plan: Back from a kid's rules returns to the grown-up menu

**Spec**: [spec.md](spec.md)

## Approach

- `kids/ui/GrownUpMenuReturn.kt`: a singleton that opens the menu's page and remembers its place in the back stack,
  so it can tell the page the menu opened (only while on top, only in the kid's pages), and removes it when the menu
  shown again closes. `BackToMenu` wraps a page: with an item, Back (registered before the page's own handlers, which
  therefore win) shows the menu; its closing calls back to remove the page.
- `KidsParentGateViewModel.openRules`/`openThisTv` (with a kid) open through it. `returnToMenu()` goes straight to the
  menu with a live grant (`ParentLockService.hasLiveGrant`), else the code entry stays; the lock read late doesn't
  replace the menu.
- `KidsParentGate(returnTo = …)` calls it and passes the item to `GrownUpMenuPage(focus = …)`.
- `GrownUpGuard(destination = …)` (from `DestinationContent`) wraps grown-up kid pages in `BackToMenu`, with the gate
  on its own view model key so the rules page's own gate keeps its state.

No change to the kids home, the sidebar, the Back pills, Settings rows or the timer bar (tasks 023, 024, PR #21).
