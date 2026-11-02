# Phase 4 report: live TV

Spec 008, User Story 4, tasks T027–T031. Builds on Phase 3.

## Changes

New:
- `kids/livetv/KidsLiveGuide.kt`:
  - `channelNowNext`, a pure function. It finds the programme airing now (start ≤ now < end), the first one starting
    at or after its end (or after now, in a gap), and how far the current one has gone. Wholphin's placeholder
    programmes for channels without a guide count as no guide.
  - `KidsLiveGuide` (LiveGuidePekeTV): "Watch live" at 44 px, then one full-width card per channel. Each card is on
    surface, with 32 px corners and 24 px padding, and holds:
    - a 160 px tile in one of the theme's colours, with the channel's picture or the buddy when there is none;
    - the LIVE badge and the channel name (22 px, "text, secondary");
    - the programme on now (34 px), or the channel name without a guide;
    - its progress as a 12 px bar in the time colour on the text colour at 18%;
    - "Next: {programme}" (20 px).
  - It reads the same `LiveTvViewModel` state: the kid's channels and programmes (004 and 006). Clicking a card plays
    the channel, as the guide's channel cell did for kids (no channel menu: `ITEM_MENUS` is off). Focusing a card asks
    the view model for more programmes, as the grid did.

Changed:
- `ui/nav/DestinationContent.kt`: `KidsLiveTv` shows `KidsLiveGuide` instead of Wholphin's guide grid.
  `TvGuideGrid` stays in the code; no other page used it, and it isn't deleted here.
- Strings, English and Spanish: `kids_next_programme`. The title reuses `watch_live`.

Behaviour kept:
- The same channels and programmes, and the same Play destination.
- A channel the server refuses still shows 006's message page from the player.
- No channels: the buddy and the empty line. The grid showed a hard-coded English error.

Differences from the board and the old page:
- The programme dialog isn't reachable from the list. For kids it only offered Watch, which the card's click does.
- The tile colours cycle through the theme's colours by channel id. The board picks them by hand.

## Tests

`kids/KidsLiveGuideTest.kt` (4, Robolectric, since `TvProgram` loads Android classes), written before the code:
- now, next and progress;
- a programme's end;
- no guide;
- a gap in the guide.

## Validation

- `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`: BUILD
  SUCCESSFUL, 842 tests, 0 failures. The first run failed the 4 new tests: `TvProgram` needs Android to load, so the
  test runs under Robolectric now.
- Phase 4 alone, in the scratch clone: `clean :app:assembleDebug testDebugUnitTest` BUILD SUCCESSFUL.
- `nix fmt`: 1 file changed on the first run, 0 on the second.
- `nix develop -c pre-commit run --all-files`: all hooks passed.

## Screenshots

None: the emulator still times out on every `adb shell`. The board is rendered in
`…/scratchpad/boards/LiveGuidePekeTV.png`. The owner can open Live TV from the sidebar (quickstart 4).

## APK size

Clean debug builds, Phase 3 → Phase 4: arm64-v8a +16,497, armeabi-v7a +16,500, x86_64 +16,503 bytes.

## Shared files

- `ui/nav/DestinationContent.kt` (Phase 1 changed the home's entry).
- `kids/ui/KidsCards.kt` (Phase 1; `tileColor` and `KidsLiveBadge` are used here, unchanged).
- `strings.xml`.
- `tasks.md` and `plan.md`.

## Commit message

```
Show kids their live channels as cards

Watch live is now a list with one wide card per channel: the
channel's picture on a coloured tile, a LIVE badge and its name, the
programme on now with how far it has gone, and what comes next.
Clicking a card plays the channel, as before.
```
