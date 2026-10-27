# Phase 4 report: stops and states

Spec 007, User Story 4, tasks T047–T052. Builds on Phase 3.

## Changes

New:
- `kids/ui/KidsStop.kt`: `stopMood(denial)` (time's up → waving, outside hours → sleeping, break → the buddy);
  `KidsTrouble` (`OFFLINE` with the searching buddy when the error or a cause is an `IOException`, else `OOPS` with the
  puzzled one); `KidsTroubleMessage` (the stop layout for an error, with Grown-ups opening the gate);
  `KidsEmptyMessage` (the buddy and a line); `isKidScreen()` (kids mode, outside grown-up pages).

Changed:
- `kids/ui/KidsTimesUpPage.kt`: `KidsGrownUpsMessage` takes a `mood`: the buddy above a Display title and a supporting
  line in "text, secondary", Grown-ups small in the bottom-right corner on every stop. Focus still starts on the message
  (Down or Right reaches Grown-ups). Time's up, bedtime and the break pass their mood; the refused channel is puzzled.
- `kids/ui/KidsEndScreen.kt`: the cheering buddy at the end of what the kid watched.
- `kids/ui/GrownUpGuard.kt`: `LocalGrownUpPage`, true inside grown-up pages.
- `ui/components/ErrorMessage.kt`: on a kid screen, `KidsTroubleMessage` instead of the error text and the "send logs"
  button; grown-up pages keep the details. `kidsTitle` lets a caller name the trouble.
- `ui/playback/PlaybackPage.kt`: a playback error says "Oops, this one won't play" to kids.
- `ui/components/CircularProgress.kt`: `LoadingPage` shows the buddy above a primary-coloured spinner on kid screens;
  `MainActivity.kt`: starting up shows it too.
- `ui/detail/CardGrid.kt`, `ui/search/SearchPage.kt`: empty grids and empty searches show `KidsEmptyMessage` to kids
  (an all-empty per-type search used to be a blank page); a failed search section no longer shows kids the exception.
- `ui/components/EditTextBox.kt`, `ui/preferences/StringInputDialog.kt`: fix from Phase 1's colour mapping: the search
  box text and icons and the input dialog's title used `onPrimaryContainer`/`onSecondaryContainer` on plain surfaces,
  which is navy on navy in PekeTV; they use `onSurface` now.
- Strings (English and Spanish): `kids_oops_title`, `kids_oops_wont_play`, `kids_oops_hint`, `kids_offline_title`,
  `kids_offline_hint`, `kids_empty`, `kids_search_empty`.
- Spec: `tasks.md` (T047–T052), `plan.md` (Measured).

## Tests

`kids/KidsStopScreenTest.kt` (2), written before the code: each stop has its mood; a kid's error is offline when the
exception or any cause is a network error (`UnknownHostException`, a wrapped `SocketTimeoutException`, `IOException`)
and "oops" otherwise, with the searching and puzzled moods. The layouts themselves have no unit tests (no Compose UI
test setup in the module); they were checked on the emulator where reachable.

## Validation

- `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`: BUILD
  SUCCESSFUL, 821 tests, 0 failures.
- `nix fmt`: 1 file changed on the first run, 0 on the second.
- `nix develop -c pre-commit run --all-files`: all hooks passed.
- One `clean :app:assembleDebug` for the size failed in `packageDebug` with no cause in the log; the same command passed
  on a fresh daemon right after, and the full validation above passed before it.

## Screenshots

In `…/scratchpad/shots/`:
- `p4-search-empty.png`: a kid's search with no results: the buddy and "Nothing found. Try another word!", with the
  search text readable after the colour fix.
- `p4-home.png`: the kids home after the changes.
- `p4-player-skip-intro.png`: the player's Skip Intro pill in the new focus (a Continue watching item started while I
  was driving the remote; I stopped it after a few seconds).

Not reachable without the parent code or changing the server: time's up, bedtime, the break, the refused channel, the
offline page (the server would have to go away) and a playback error. Starting up is too quick on the emulator to
capture.

## APK size

Clean debug builds, Phase 3 → Phase 4: arm64-v8a +17,331, armeabi-v7a +17,337, x86_64 +17,330 bytes (code and
strings).

## Commit message

```
Let the buddy narrate the stops, errors and empty pages

Time's up, bedtime, a break and a refused channel show the PekeTV
buddy in its mood, a big title and Grown-ups small in the corner.
Kids get the buddy instead of error details, empty lists and searches
say so with the buddy, and loading shows it above the spinner. The
search box and the input dialog draw their text in a readable colour
again.
```
