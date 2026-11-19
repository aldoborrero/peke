# Implementation Plan: The grown-up area

**Spec**: [spec.md](spec.md)

## Approach

- **Plugin API** (`kids/server`): `KidsFeatures.STATS`; `KidsStats`, `KidsStatsDay`, `KidsTopSeries`, `KidsBreaks`
  as the contract's JSON; `KidsApi.stats(baseUrl, caller, grant, userId, days)` on `Kids/Users/{id}/Stats?days=N`.
  Built against fakes until the plugin's `kids-stats` branch ships.
- **Rules** (`kids/ui/rules`): the view model drops the policy, channels, picks, voice search and dashboard paths
  (and `ApiClient`, `KidsLiveTvService`, `VoiceSearchSetting`); it gains this TV's kids from `TvSetupRepository` and
  `select(kid)`, which shows that kid's cached profile and refreshes it with their token. `save` and `retryPending`
  act on the kid shown, the pending change remembers its kid. The page drops the read-only rows and the right
  column; each `StepperRow` gets an icon and a sentence that follows the shown value. `KidSwitch` is the header's
  chips (variant a), reached with Up from the first rule.
- **Stats** (`kids/ui/stats`, replacing `kids/ui/day`): `KidStatsViewModel` loads, per kid of this TV,
  `KidsAllowanceService.lastKnownStatus` (fresh with the kid's token, else cached) and, with `stats`, `KidsApi.stats`
  with `ParentLockService.auth()`. `KidStatsCard` is the read-only card; `StatsBars.of(days)` computes the bars.
- **Navigation**: `Destination.KidStats` replaces `KidDay` (grown-up, `isGrownUp`), `GrownUpMenuItem.STATS`,
  `KidsParentGateViewModel.openStats`, the menu's cards in the new order, time's up holding off on Stats.
- Grown-up pages here take `SafeArea.page` (spec 028): the 58/28 dp safe edge plus a grown-up ring, like the menu,
  Add time and Settings.
- Strings (`values`, `values-es`) and `design/DESIGN.md` (After the parent code, Grown-up pages, A kid's rules,
  Stats).
