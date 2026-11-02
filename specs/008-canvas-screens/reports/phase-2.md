# Phase 2 report: details and seasons

Spec 008, User Story 2, tasks T012–T019. Builds on Phase 1.

## Changes

New:
- `kids/ui/KidsDetails.kt`: the kids details parts of DetailsPekeTV and KSeasonsPekeTV.
  - `KidsDetailsHeader`: the poster on the left (260×380 px, 30 px corners), then the title in Display, the chips, the
    overview (22 px, "text, secondary", up to 3 lines, 640 px wide) and the buttons.
  - `KidsChip` (18 px on surface; focusable when it opens something), `KidsPlayPill` (72 px, primary, Peke's play
    icon, 28 px text) and `KidsRoundButton` (72 px on surface, a Peke icon or a FontAwesome glyph).
  - `KidsPlayButtons`: the Play pill, then round buttons for what the page already offered: restart when started,
    version when there are several, watched and favourite. `KidsFavouriteButton` is Peke's heart in primary when it is
    a favourite.
  - `KidsSeasonsDialog`: the Seasons list. A 640 px panel with 36 px corners; one row per season with its number in a
    52 px circle, its name and its episode count; the focused row in primary. It opens on the current season.
  - Pure helpers: `seasonCircle`, `episodeLabel` ("4. Lost Robot") and `runtimeMinutes`.
- `ui/detail/series/KidsSeriesOverviewContent.kt`: the season page for kids (the board's details page).
  - The series' poster, title and overview.
  - The season chip, which opens the Seasons list when there is more than one season, and the episode count.
  - "Play episode N" for the focused episode, then restart, watched and favourite as before.
  - The episodes as wide cards 260×146 px with "{n}. {name}", the watched badge and the progress bar.
  - The season extras row, as before.

Changed:
- `ui/detail/series/SeriesOverviewContent.kt`: kids get `KidsSeriesOverviewContent`. Same actions, focus requesters,
  Play key and season change as the Wholphin layout; the people rows were never shown to kids.
- `ui/detail/series/SeriesDetails.kt`: kids get `KidsDetailsHeader`. Chips: the year and the season count. Buttons:
  Play, shuffle, watched and favourite, as before. The seasons and "More like this" rows use kids posters (180×264 px).
- `ui/detail/movie/MovieDetails.kt`: kids get `KidsDetailsHeader`. Chips: the year and the length. Buttons: Play or
  Resume, restart, version, watched and favourite, as before. Chapters, extras and "More like this" are unchanged.
- `ui/nav/ApplicationContent.kt`: no backdrop while a kid is current. No kid board has one, and it covers every kids
  details page whatever its view model submits.
- `kids/ui/KidsCards.kt` (from Phase 1): `KidsPictureCard` takes `useSeriesForPrimary`, so episode cards show their
  own picture.
- Strings, English and Spanish: `kids_play_episode`, `kids_episode_count`, `kids_season_count`, `kids_runtime`. The
  list title reuses `tv_seasons`.

004's rules hold:
- no More, delete, playlists or people rows (`LocalInterfaceCustomization`, unchanged);
- Play, resume, restart, the version choice, watched, favourite and shuffle do what they did;
- the extras rows are the same rows as before.

Differences from the board:
- The board's heart is the show's. On the season page the favourite button keeps what it did before: it marks the
  focused episode. Changing that would be a behaviour change.
- Restart, version, watched and shuffle stay as round buttons next to the heart, since the page offered them before.
  Restart, version and shuffle use FontAwesome glyphs; Peke has no icon for them.
- The overview isn't focusable on the kids page; Wholphin's opened a details dialog. Kids lose that dialog, which held
  the same text.
- Series pages keep their seasons row of posters. Merging the season page into the series page would change navigation.

## Tests

`kids/KidsDetailsTest.kt` (3), written before the code:
- the season circle colours in turn and on the focused row;
- the episode label with and without a number;
- the film length in minutes.

The series and movie view model tests are unchanged.

## Validation

- `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`: BUILD
  SUCCESSFUL, 836 tests, 0 failures.
- Phase 2 alone, in a scratch clone of the base with the Phase 3 files left out: `clean :app:assembleDebug
  testDebugUnitTest` BUILD SUCCESSFUL. This confirms the phase builds on its own.
- `nix fmt`: 7 files changed on the first run, 0 on the second.
- `nix develop -c pre-commit run --all-files`: all hooks passed.

## Screenshots

None. The Windows emulator has stopped answering since Phase 1: `adb devices` lists it, but every `adb shell` command
times out, and `adb reconnect` doesn't help. I didn't restart or reset it. The boards to compare against are rendered
in `…/scratchpad/boards/DetailsPekeTV.png` and `KSeasonsPekeTV.png`. The owner can check a series, a season page with
the Seasons list, and a film (quickstart 2).

## APK size

Clean debug builds, Phase 1 → Phase 2: arm64-v8a +49,779, armeabi-v7a +49,784, x86_64 +49,789 bytes. This is dex
and strings.

## Shared files

- `kids/ui/KidsCards.kt` (Phase 1): one new parameter.
- `strings.xml`: its own block.
- `tasks.md` and `plan.md`.

## Commit message

```
Lay out the kids details pages like the canvas

Series, season and film pages show the poster on the left, a big
title, chips for the season and episodes or the year and length,
the overview, a large Play button and round buttons for the rest.
Episodes are wide cards with their number and name, and the season
chip opens a Seasons list with a coloured number per season. Kid
pages no longer draw a backdrop behind them.
```
