# Tasks: Back on the kids home

- [x] T001 [US1] [US2] `NavDrawer.kt`: `backAsksToLeave` replaces `backOpensNavDrawer`; `backClosesNavDrawer` on every
  page; `DrawerBackHandler` shows the question and hands "Leave" on to the system
- [x] T002 [US2] `kids/ui/KidsLeaveDialog.kt` with "Keep watching" focused first; strings in English and Spanish
- [x] T003 [US1] [US2] Tests: `NavDrawerBackTest` (rules) and `DrawerBackCloseTest` (sidebar closes on the home, the
  question shows, Keep watching and Back dismiss, Leave quits, other pages unchanged)
- [x] T004 Constitution 2.1.1, `AGENTS.md`, DESIGN "Behaviour"
- [x] T005 `nix develop -c ./gradlew :app:testDebugUnitTest :app:assembleDebug`, pre-commit on changed files
