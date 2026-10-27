# Phase 3 report: player and next-up

Spec 008, User Story 3, tasks T020–T026. Builds on Phase 2.

## Changes

New:
- `kids/playback/KidsPlayerUi.kt`:
  - `kidsPlayerTitle`: "Space Pals · Episode 4" on one line.
  - `inARow`: the in-a-row chip, only with an autoplay cap. It counts the episode that's ending and never goes past the
    cap.
  - `KidsNextUpPanel` (KNextUpPekeTV), a 560 px panel on the outline colour at 95%, 36 px corners: the next episode's
    picture (200×112), "Next Up", "{n}. {name}", Play now and Stop. Play now is a primary pill with the countdown drawn
    as a ring that empties round Peke's play icon.
  - `KidsInARowChip`: "Episode 2 of 3 in a row" in the time colour.
  - `KidsPlayerWarning` (PlayerPekeTV): a dark pill with the buddy and "5 minutes left" in the time colour.

Changed:
- `ui/playback/overlay/PlaybackControls.kt`:
  - Kids get PlayerPekeTV's round transport buttons (`playbackButtonStyle`): play and pause 76 px in primary, the
    others 62 px on the outline colour at 92% with white icons. They turn primary with Peke's border and ring on focus.
  - Audio uses Peke's audio icon instead of FontAwesome's.
  - Kids don't see the position and remaining-time labels.
  - Grown-up players keep Wholphin's buttons. The settings button stays hidden for kids (005).
- `ui/playback/overlay/SeekBar.kt`: for kids, the bar is 12 px in primary on the outline colour at 50%, twice as thick
  while focused. Seeking is unchanged.
- `ui/playback/overlay/PlaybackController.kt`: for kids, the title is "{show} · Episode {n}" (or the film's name) at
  26 px, without the subtitle line or "Ends at".
- `ui/playback/PlaybackPage.kt`: kids get the next-up panel at the bottom right and the in-a-row chip at the bottom
  left. The picture stays full screen instead of shrinking to 60%, since the panel sits over the credits.
  - Play now does what the card did: report the interaction, hide the controls, play the next episode.
  - Stop does what Back did at that moment: stop the countdown while it runs; otherwise cancel next-up while playing,
    or go back.
  - The countdown, its 15 s and autoplay are unchanged.
- `ui/playback/PlaybackViewModel.kt`: `autoPlayedInARowCount`, a read-only getter of the count the view model already
  keeps. No logic changed.
- `kids/ui/KidsTimerEffects.kt`: while the player is on top, the warning is `KidsPlayerWarning` at the top right; on
  other pages it is unchanged. The same moments, chime and 5 s. (Phase 1 changed the bar part of this file.)
- Strings, English and Spanish: `kids_episode_number`, `kids_play_now`, `kids_stop`, `kids_in_a_row`. "Next Up" reuses
  `next_up`.

005 holds: nothing grown-up shows on a kid's player. No settings, speed or debug; the dialogs are unchanged.

Differences from the board:
- The board shows pause, next episode and subtitles. Previous, rewind, fast-forward and audio stay as well, round in the
  same style. Removing them would change what a kid can do.
- The buttons keep Wholphin's places (transport in the centre, subtitles and audio on the right) rather than the
  board's single row on the left. Moving them would change the D-pad focus order on the player.
- The warning's second line, "This episode can finish", isn't shown. The plugin doesn't tell the TV (spec
  clarification).

## Tests

`kids/KidsPlayerTest.kt` (2), written before the code:
- the in-a-row chip: no cap, a zero cap, counting from 1 and capped;
- the title line.

The playback view model tests are unchanged.

## Validation

- `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`: BUILD
  SUCCESSFUL, 838 tests, 0 failures.
- Phase 3 alone, in the scratch clone: `clean :app:assembleDebug testDebugUnitTest` BUILD SUCCESSFUL.
- `nix fmt`: 4 files changed on the first run, 0 on the second.
- `nix develop -c pre-commit run --all-files`: all hooks passed.

## Screenshots

None. The emulator is still unreachable (`adb shell` times out), and the player can't be shown without starting
playback, which counts the kid's time. Compare with the rendered boards in `…/scratchpad/boards/PlayerPekeTV.png` and
`KNextUpPekeTV.png`. The owner can check them by playing an episode to its end (quickstart 3).

## APK size

Clean debug builds, Phase 2 → Phase 3: arm64-v8a +33,174, armeabi-v7a +33,175, x86_64 +33,167 bytes.

## Shared files

- `kids/ui/KidsTimerEffects.kt`: Phase 1 changed the bar, Phase 3 the warning.
- `kids/ui/KidsDetails.kt` (Phase 2): `episodeLabel` is used here.
- `strings.xml`: its own block.
- `tasks.md` and `plan.md`.

## Commit message

```
Give the kids player round buttons and a next-up panel

For kids the controls show the show and episode on one line above a
thick progress bar in the primary colour, with round transport
buttons. The time warning on the player is a dark pill with the buddy
in the top right. Next-up becomes a panel in the bottom right with
the episode's picture, Play now with the countdown round its icon,
and Stop, plus how many episodes in a row when the kid has a cap.
```
