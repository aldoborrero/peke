# Tasks: TV-safe margins

- [x] T001 [US1] [US2] `SafeArea`: `EdgeHorizontal`/`EdgeVertical`, `PageHorizontal`/`PageVertical`, `page`
- [x] T002 [US1] `FocusRoom.end` and `toScreenEnd()`; the kids home rows use it
- [x] T003 [US2] Grown-up menu, Add time, a kid's rules and Settings (and its notice pill) take `SafeArea.page`
- [x] T004 [US3] Watch live's right padding and the home clock take the edge
- [x] T005 Test: `TvSafeMarginsTest` (ring of every home card kind, grown-up padding, rows' start unchanged)
- [x] T006 DESIGN "Shape and space"
- [x] T007 `nix develop -c ./gradlew :app:testDebugUnitTest :app:assembleDebug`, pre-commit on changed files, emulator
  screenshots
