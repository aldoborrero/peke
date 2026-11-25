# Implementation Plan: The "Watch live" row stays, with the channel's logo

**Spec**: [spec.md](spec.md)

## Approach

- `services/LiveRow.kt` (new): the row's request (`isAiring`, no `minEndDate`), `nextRefresh` (end + 2 s, 1 min retry,
  null with nothing to wait for) and `refreshAtEnds`, the timer loop.
- `services/HomeSettingsService.kt`: `TvPrograms` uses `LiveRow.request`.
- `ui/UiConstants.kt`: `ProgramItemFields` adds `ItemFields.CHANNEL_IMAGE`.
- `ui/main/HomeViewModel.kt`: `isRefreshedOnReturn` includes `TvPrograms`; the partial refresh becomes
  `refreshRows(current, userDto, which)`; `refreshLiveAtEnds` follows the live row's programmes in the state and runs
  the timer, reading only the live row.
- `kids/home/KidsHomeLayout.kt`: `ImageUrlService.channelImageUrl(picture)`, used by the channel card in
  `KidsHomeUi.kt` (same URL as before, now testable).

No new dependency, string or layout change.
