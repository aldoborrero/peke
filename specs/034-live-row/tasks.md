# Tasks: The "Watch live" row stays, with the channel's logo

**Input**: [plan.md](plan.md), [spec.md](spec.md)

- [X] T001 Request only `isAiring`, no one-minute cut (`services/LiveRow.kt`, `services/HomeSettingsService.kt`)
- [X] T002 [P] Ask for `CHANNEL_IMAGE` with programmes (`ui/UiConstants.kt`)
- [X] T003 Read the live row on return and at the first end time (`ui/main/HomeViewModel.kt`, `services/LiveRow.kt`)
- [X] T004 [P] Extract the card's image URL (`kids/home/KidsHomeLayout.kt`, `kids/home/KidsHomeUi.kt`)
- [X] T005 Tests: ending in 30 s, boundary, timer, return, image URL (`services/LiveRowTest.kt`)
