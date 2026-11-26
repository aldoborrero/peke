# Tasks: The "Watch live" row stays, with the channel's logo

**Input**: [plan.md](plan.md), [spec.md](spec.md)

- [X] T001 Request only `isAiring`, no one-minute cut (`services/LiveRow.kt`, `services/HomeSettingsService.kt`)
- [X] T002 [P] Ask for `CHANNEL_IMAGE` with programmes (`ui/UiConstants.kt`)
- [X] T003 Read the live row on return and at the first end time (`ui/main/HomeViewModel.kt`, `services/LiveRow.kt`)
- [X] T004 [P] Extract the card's image URL (`kids/home/KidsHomeLayout.kt`, `kids/home/KidsHomeUi.kt`)
- [X] T005 Tests: ending in 30 s, boundary, timer, return, image URL (`services/LiveRowTest.kt`)

After review:

- [X] T006 The timer keeps the row on a failed read and retries after 1 min (`services/LiveRow.kt`, `ui/main/HomeViewModel.kt`)
- [X] T007 A gap keeps the row while it airs, then hides it until the next start (`services/LiveRow.kt`, `services/HomeSettingsService.kt`)
- [X] T008 Quick retries at 15 s twice (`services/LiveRow.kt`)
- [X] T009 View model tests on virtual time (`ui/main/HomeViewModelLiveRowTest.kt`)
