# Phase 5 report: search, voice, favourites, library

Spec 008, User Story 5, tasks T032–T037. Builds on Phase 4.

## Changes

New icon:
- `design/assets/icons/filter.svg` and `outline/filter.svg`: sliders, three 3.5 lines with a knob each.
  - Two-tone: the knobs are coral, teal and sunny, outlined in navy.
  - Outline: everything in `currentColor`. The lines stop at the knobs in both sets, so the two sets draw the same
    picture.
  - Inside the 4–44 live area, round caps and joins on the root, explicit fills, no highlight.
- Converted with AGP's `Svg2Vector` (`com.android.tools:sdk-common` 32.2.1 from the Gradle cache, the same driver as
  007) to `res/drawable/ic_peke_filter.xml` and `ic_peke_outline_filter.xml`, with no warnings.
- Added as `PekeIcon.FILTER`. `DrawablesTest` lists it. DESIGN.md now says twenty-one icons.

Search (KSearchPekeTV):
- `ui/components/EditTextBox.kt`: both search boxes use Peke's search icon in the secondary colour instead of
  Material's. For kids the box is a 72 px pill with 28 px text.
- `ui/components/VoiceSearchButton.kt`: the microphone is a 72 px circle in tertiary with Peke's voice icon, instead of
  FontAwesome's.
- `ui/search/SearchPage.kt`:
  - For kids the field comes first and the microphone after it, with the board's paddings.
  - The view options button (the type filter) uses Peke's filter icon instead of FontAwesome's sliders.
  - Result rows use kids cards: posters 180×264 px, episodes 260×146 px with "{n}. {name}".
  - The Play key, long press, the empty and error states (007) are unchanged.

Voice (KVoicePekeTV):
- `VoiceSearchOverlay` now fills the screen on the page colour:
  - two tertiary circles (300 px at 18%, 220 px at 30%, growing with the voice level as before) with Peke's voice icon
    at 160 px;
  - "Speak to search" (52 px), or the starting, processing or error text in its place;
  - what was heard in quotes on a surface pill in the time colour;
  - Retry for retryable errors, as before;
  - "Press Back to stop".
- The states, timings, auto-dismiss and Back are unchanged.

Favourites (KFavoritesPekeTV):
- `ui/detail/FavoritesPage.kt`: kids get a title row: Peke's heart in primary, "{Kid}'s favourites" (44 px) and the
  hint pill "Press the heart on any show to keep it here". With no favourites, kids see the buddy with the hint.
- The tabs per type and the grids stay.

Library grids (KLibraryPekeTV):
- `ui/components/CollectionFolderGrid.kt`: for kids the title is at the start in Title style. Grids draw kids posters
  (1.41 high per wide, 22 px corners, the title under them, the heart badge on favourites). This covers folders, box
  sets and playlists from a kid's rows, and the favourites tabs.
- `ui/components/FilterByButton.kt`: the filter button uses Peke's filter icon instead of FontAwesome's.

Strings, English and Spanish: `kids_voice_title`, `kids_voice_back`, `kids_favourites_title`, `kids_favourites_hint`.

Differences from the boards:
- **Library chips:**
  - The board's A to Z, Newest and Not watched chips aren't drawn. The grid header keeps Wholphin's sort, filter, view
    options and random buttons: turning them into three chips would remove choices kids have today.
- **Search view options are still shown to kids.** The view options button lets a kid change the search settings
  (combined results, the microphone button, the types searched). It was like that before this feature, and hiding it
  would be a behaviour change. Raised for the owner, not changed.
- **Favourites:** the tabs per type and the grid header stay; the board shows one grid. The hint sits in the title
  row rather than under the grid, so it never covers a card.
- **Films in search** are posters like the shows. The board draws them as small tiles with the title inside.
- **Material icons left in place:** the check and delete icons inside the filter dropdown and on details pages.
  The task named the search, microphone and filter icons, which are all Peke's now.

## Tests

- `IconSpecTest` (8) and `DrawablesTest` pass with the new icon: grid, strokes, caps and joins, fills, live area, the
  same bounds in both sets, no highlight, and it inflates at 48 dp.
- No new pure logic otherwise. The search, voice and favourites tests are unchanged.

## Validation

- `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`: BUILD
  SUCCESSFUL, 842 tests, 0 failures.
- Phase 5 alone, in the scratch clone: `clean :app:assembleDebug testDebugUnitTest` BUILD SUCCESSFUL.
- `nix fmt`: the first run stopped on a ktlint rule (a private constant not in screaming snake case). After the rename,
  0 changed on two runs.
- `nix develop -c pre-commit run --all-files`: all hooks passed.

## Screenshots

None: the emulator still times out on every `adb shell`. Rendered boards: `…/scratchpad/boards/KSearchPekeTV.png`,
`KVoicePekeTV.png`, `KFavoritesPekeTV.png`, `KLibraryPekeTV.png`. The owner can check them with quickstart 5. The
microphone only shows when voice search is on for the TV.

## APK size

Clean debug builds, Phase 4 → Phase 5: arm64-v8a +2,354, armeabi-v7a +2,352, x86_64 +2,354 bytes.

## Shared files

- `ui/theme/PekeArt.kt`, `DrawablesTest.kt`, `design/DESIGN.md` and the icon folders: Phase 6 adds the download icon
  to them.
- `kids/ui/KidsCards.kt` and `KidsDetails.kt` are used, unchanged.
- `strings.xml`.
- `tasks.md` and `plan.md`.

## Commit message

```
Bring search, voice, favourites and grids to the canvas look

Search gets a big pill with Peke's search icon and a round lilac
microphone with Peke's voice icon, and its results use the kids
cards. Listening fills the screen with the microphone in soft
circles, "Speak to search" and what was heard. Favourites get a
title with the kid's name and a hint about the heart, and grids
draw posters with their titles. A new filter icon, drawn in both
sets, replaces the FontAwesome filter and sliders glyphs.
```
