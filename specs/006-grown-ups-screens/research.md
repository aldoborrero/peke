# Research: Grown-ups screens

Decisions taken while planning spec 006. File paths are relative to `app/src/main/java/dev/aldoborrero/peke/`.

## R1. Removing kid marking from the TV

- **Decision**: delete the "Kid profiles" switches with the page that holds them, and the write paths only they use:
  `KidProfileRepository.mark` / `unmark` and `KidsApi.deleteProfile`. `KidsApi.putProfile` and
  `KidProfileRepository.save` stay for the daily time.
- **Rationale**: F-K1 (severity 4). Spec 004 already says the dashboard is where kids are marked. Keeping an unused
  delete call is a loaded gun for a later screen.
- **Alternatives**: option B (keep it last, with a confirmation): rejected by the owner's choice of C.

## R2. The Add time stepper

- **Decision**: a new stage `GateStage.AddTime(index, sending, error)` in `KidsParentGateViewModel`, rendered by a new
  `kids/ui/AddTimeStepper.kt` inside the gate's dialog. Steps `ADD_TIME_STEPS = [5, 10, 15, 20, 30, 45, 60, 90, 120]`.
  Left/Right move the index and stop at the ends; the arrows dim at the ends. The amount is stored with
  `KeyValueService` under `kids_add_time_minutes` (a per-TV DataStore key, like the timer bar), read when the stepper
  opens; a missing or unknown value opens on 15. Only a successful bonus stores it.
- **Rationale**: owner's decision. `KeyValueService` is the per-TV store the timer bar already uses; a proto field
  would be overkill for one integer.
- **Alternatives**: a list (today), a slider (no fixed steps), per-kid memory (the owner asked per TV).

## R3. The result line

- **Decision**: a pure function `AddTimePreview.of(status, limits, minutes, now, schedules)` in
  `kids/timer/AddTimePreview.kt`, unit tested, returns `until`, `hadLeft`, `bedtimeAt` (when `until` passes it) and
  `breakAt` (when the sitting ends before `until`). `until = now + allowance left (0 when time's up) + minutes`. The
  bedtime is the end of today's access-schedule window that contains now, from the current kid's `UserPolicy`
  (`accessSchedules`: day of week, start and end hours, in the server's local time, read as the TV's). The break is
  `now + sitting left` when that comes first.
- **Rationale**: the server decides (constitution III); the line is only a forecast from the status the TV already
  has, so the plugin needs no change.
- **Alternatives**: asking the plugin for a preview (no such call).

## R4. Why time can't be added

- **Decision**: `AddTimeAvailability.of(pluginStatus, status, limits, reachable)` → `Available`, `OnBreak(endsAt,
  canEnd)`, `Bedtime`, `Unlimited`, `Offline`, checked in that order after `Offline`. `Offline` means the plugin isn't
  available, there is no status, or the last status request had no answer. `KidsAllowanceService` gains
  `reachable: StateFlow<Boolean?>` (true after an answer, false after `Unreachable`). The menu shows the reason as
  `DialogItemText` in Add time's place; for `OnBreak` with `breakEnd`, "Keep watching now" is an action below it.
- **Rationale**: F-M1, F-M4: the place stays, so the menu's order doesn't move.

## R5. Keeping the stop screen behind the confirmation

- **Decision**: `KidsTimeLimits.holdStop: MutableStateFlow<Boolean>`. The gate sets it while the add-time stepper,
  sending or the confirmation are on screen, and clears it when the gate closes. `KidsTimerEffects` leaves time's up
  only when it's clear (the key joins its `LaunchedEffect`). After the confirmation closes, the effect runs again and
  leaves time's up, resuming playback through the existing `interrupted.timeAdded`.
- **Rationale**: the gate lives inside `KidsTimesUpPage`; when the bonus lifts the stop, that page is replaced and
  would take the confirmation and Undo with it.
- **Alternatives**: an app-level banner with Undo (needs focus outside a dialog); delaying the status update (would
  lie to the rest of the app).

## R6. Undo and "Keep watching now" (plugin 0.0.9.0)

- **Update (implement time)**: the plugin's contract was published (Kids client API 1.2); `contracts/kids-api-bonus-undo.md`
  now follows it: features `bonusUndo` and `breakEnd`, an integer `BonusId`, 404 and 409 on undo. The rest of this
  entry is what was planned before it.
- **Decision**: the plugin's contract (`jellyfin-kids` spec 013) didn't exist when this was planned; checked again
  at implement time. Until then the client follows the owner's shape, in one place in `kids/server/KidsApi.kt`:
  - `POST /Kids/Users/{id}/WatchTime/Bonus` answers the status plus `BonusId` (read as optional).
  - `DELETE /Kids/Users/{id}/WatchTime/Bonus/{bonusId}` takes it back and answers the status.
  - `POST /Kids/Users/{id}/WatchTime/Break/End` ends the break and answers the status.
  Gated by the `GET /Kids/Info` features `bonusUndo` and `breakEnd` (`KidsFeatures.BONUS_UNDO`, `BREAK_END`).
  Both need the parent grant, like the bonus. Undo shows only with a `BonusId` and the feature.
- **Rationale**: owner's note. One place to change when the contract lands.
- **Undo window and grant**: the grant isn't forgotten right after the bonus any more: it ends when the
  confirmation closes (10 s, or at once without Undo). The grant lasts about 4 min 45 s, so the window fits.

## R7. "{Kid}'s rules"

- **Decision**: a new page `kids/ui/rules/KidRulesPage.kt` with `KidRulesViewModel`, replacing
  `kids/ui/settings/*` (deleted). `Destination.KidsSettings` becomes `Destination.KidRules`. Full-screen, two
  columns so it fits on one TV screen without scrolling: on the left the avatar and "Leo's rules", Daily time (the
  only focus stop, a `ChoicePreference`), "Today: 35 of 60 minutes", and the dashboard box; on the right the
  read-only lines. Daily time's options are `[5, 10, 15, 30, 45, 60, 90, 120, 180, 240, Unlimited]`, plus the
  current value in order if it's another one.
- **Saving**: `KidProfileRepository.save`, one at a time (mutex) from the cached profile. `NEEDS_GRANT` keeps the
  value as `pending` and shows `KidsParentGate(onPassed = retry)`; after the gate the value is saved (F-K7).
  `SAVED` shows "Saved" under the row for 3 s and asks the allowance service for a fresh status.
- **Read-only data**: the profile (sitting, search, autoplay, picks) from `KidProfileDao`; the break length from the
  status (`BreakSeconds`); the policy (rating, unrated, libraries, access schedule, `enableLiveTvAccess`) from
  `ServerRepository.currentUserDto`; channel names from `KidsLiveTvService.channelsFor`; pick names from
  `itemsApi.getItems(ids)`; voice search from `AppPreferences` and the microphone permission.
- **Dashboard address**: `{server url}/web/#/configurationpage?name=Kids`, with "then pick Leo". The kid's own page
  (`?name=KidsUser&userId=…`) needs a 36-character id, which nobody types from a TV; a QR code would carry it later.

## R8. The refused channel

- **Decision**: a kid destination `Destination.KidsChannelRefused` with `KidsGrownUpsMessage` (the stop screens'
  layout: focus on the message, Down to Grown-ups) titled "This channel isn't on {Kid}'s list", subtitle "A grown-up
  can add it on the dashboard". `PlaybackViewModel.kidsRefused` replaces the playback page with it instead of a toast
  and Back. Grown-ups opens the gate and its menu, where "{Kid}'s rules" lists the channels. Back leaves the page.
  Recordings keep their toast (kids never see recordings).
- **Rationale**: F-E1 for live TV, which is what the owner asked for; the general "can't play" screen stays out of
  scope.

## R9. Menu order and Settings from the picker

- **Decision**: the menu is built in a fixed order (header, Add time or its reason, Switch kid, rules, Settings).
  Kids on this TV leaves the menu. Without a kid, a right code (or a recovery) opens Settings at once and closes the
  gate. Settings gets groups Household (Change the parent code) and This TV (server address, Kids on this TV, voice
  search, timer bar, screensaver, clock, language, subtitles), then More, About and Start over.
- **Kids on this TV from Settings**: `PreferencesViewModel.openKidsOnTv()`, like `openReset()`.
- **Timer bar in Settings**: `AppPreference.KidsTimerBar`, rendered specially in `PreferencesContent` from
  `KidsTimerBarSetting`, a `ChoicePreference`.

## R10. Change the parent code from Settings

- **Decision**: `AppPreference.ChangeParentCode` opens `kids/ui/ParentCodeChange.kt` (current code, then the new one
  twice with `SetPinFlow`), `ParentCodeChangeViewModel` calls `ParentLockService.setCode(type, new, current)` with the
  household's current type. From the picker there is no current kid, and `auth()` needs one; `setCode` now uses
  `householdAuth()`: the current kid's grant, else any live grant on this TV's server (the gate stored it for the kid
  whose token checked the code). `auth()` itself is unchanged, since the bonus adds time to `auth().user`.
- **Rationale**: review §6 keeps the change on the TV; the code type stays in setup and the dashboard.

## R11. Parent gate

- **Empty OK** (F-G1): `KidsPasscodeDialog` gets a `forgotHint` flag that shows "Forgot it? Press OK with no code";
  `KidsParentGateViewModel.submit("")` goes to `AdminSignIn(recovery = true, fromEmpty = true)` without calling
  `verify` (no server check, no throttle count). Back on the recovery returns to the code entry.
- **Quick Connect** (F-G4): `KidsAdminSignInDialog` gets the setup step's "Use Quick Connect" / "Use a password"
  switch; `GateStage.AdminSignIn` gets `quickConnectCode`; the view model runs `AdminSessions.openWithQuickConnect`
  in a job cancelled on switching back, on Back and in `reset()`. For recovery the session is kept as for a password;
  for the plugin-missing path it's closed at once and leads to the admin menu.
- **Dead lines** (F-G5): the explanations of NoCode, RecoveryOnly, PluginMissing and AdminMenu become
  `DialogItemText`. **Titles** (F-G6): "Parent code" for those stages, "Forgot the code?" for the recovery sign-in.
- **One name** (F-G2): `enter_pin`, `confirm_pin`, `pin_too_short` and the gate's strings say "parent code"; Spanish
  "código de padres", replacing "PIN" and "código parental".

## R12. Choices open on the current value

- **Decision**: `DialogPopup` / `DialogPopupContent` get `initialFocusIndex`; `ChoicePreference` passes the selected
  index. That covers Daily time, the timer bar and every Settings choice.

## R13. No new dependency, no schema change

No Room or proto change (the add-time amount is a `KeyValueService` key). No new library. The deleted Kotlin is
larger than what's added.
