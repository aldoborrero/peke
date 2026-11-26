# Implementation Plan: Edit a kid's rules on the TV

**Spec**: [spec.md](spec.md)

## Approach

- `kids/ui/rules/KidRulesViewModel.kt`: `RuleChange` (DailyTime, SittingLimit, Autoplay, Search), each applied to the
  profile as last saved. `save(change)` replaces `setDailyTime` and keeps its path: the mutex, the latest row from the
  DAO, nothing sent when unchanged, `KidProfileRepository.save`, and `pending` holding the change when the grant ran
  out. `sessionLimitEditable` is the plugin's `sessionLimit` feature; `sessionLimitSupported` still drives the text.
  Choice lists: `sittingOptions`, `AUTOPLAY_OPTIONS`, `SEARCH_OPTIONS`.
- `kids/ui/rules/KidRulesPage.kt`: daily time's row becomes a generic `StepperRow<T>`; `RuleEditors` lays out the
  four (three without the sitting limit) with one `FocusRequester` each, Up/Down moving between them and scrolling the
  text past the ends. Focus leaving a row resets it to the saved value. Search, autoplay and an editable sitting limit
  leave the read-only rows.
- Strings (`values`, `values-es`): the new lines and values; the dashboard card's words.
- No change to `KidProfileRepository`, `KidProfileMapper` (they already send every field), `GrownUpKit.kt`, the back
  handling of spec 025, or the 026–029 files.
- `design/DESIGN.md` Screens: "A kid's rules" now lists the four rules changed on the TV.

## Second round

- `KidsFeatures.UNLIMITED_AUTOPLAY`; `KidProfile.UNLIMITED_AUTOPLAY_CAP` (0) kept by `validAutoplayCap`;
  `KidsAutoplayPolicy.allowNext` lets 0 through. `inARow` already hides the chip for 0.
- Rules: `unlimitedAutoplay` in the state, `autoplayOptions(unlimited, current)`, `RuleChange.Autoplay(0)` refused
  without the feature; the Today row and `usedMinutes`/`todayMinutes` go.
- `kids/ui/day/`: `KidDay.from(KidsStatus)`, `KidDayViewModel` (current kid, `refresh()`, snapshot, reachable),
  `KidDayPage`/`KidDayContent` reusing the rules' `RuleRow` (now internal).
- `Destination.KidDay` (grown-up, in `isGrownUp`), `DestinationContent`, `GrownUpMenuItem.DAY` and
  `GrownUpMenuReturn.itemFor`, `KidsParentGateViewModel.openDay`, the menu's card (`onDay`), and time's up holding off
  on the day as on the rules (`KidsTimerEffects`).
