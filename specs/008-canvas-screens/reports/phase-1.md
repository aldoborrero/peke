# Phase 1 report: kids home

Spec 008, User Story 1, tasks T001–T011. Builds on `main` (`1ab9994c`, spec 007 merged).

## Changes

New:
- `ui/theme/Canvas.kt`: `cpx`/`csp` (1 canvas px = 0.75 dp/sp) so layout code carries the boards' numbers, and
  `PekePalette.rail`, the sidebar colour (the background 16% darker: `#1A2140` in PekeTV, as on the boards).
- `kids/home/KidsHomeLayout.kt`: `kidsCardKind` (keep watching and next up → wide, live → channel, the rest → poster)
  and `homeTimeLeft` (what the header shows of the time left: nothing without a daily limit, while unknown or with the
  bar Hidden; "today" when the allowance runs out before the sitting; bigger from the first warning point).
- `kids/home/KidsHomeUi.kt`: `KidsHomeHeader` ("Hi, {Kid}!", "What do you want to watch?", the time-left pill and the
  clock when it's on), `KidsTimeLeftPill`, `KidsHomeCard` (wide 320×180, channel 240×112 with LIVE, poster 140×200)
  and `KidsTimeLeftViewModel`.
- `kids/ui/KidsCards.kt`: the kids' card parts, shared with Phases 2 and 5: `KidsPictureCard` (picture, or the title on
  a theme-coloured tile; the title under the card, in the text colour when focused and "text, secondary" otherwise),
  `KidsCardSurface` (Peke's focus, no tv-material scale or border), `KidsProgress` (a 10 px pill inside the card),
  watched, favourite and LIVE badges.

Changed:
- `ui/main/HomePage.kt`: in kids mode the home passes `HomePageContent` the kids header, kids cards and the board's
  paddings (40/48 px, rows to the right edge) and doesn't send backdrops; the focused item's backdrop, logo and details
  are gone for kids. Clicks, the Play key, the context menu and focus restore are the same code as before.
- `ui/cards/ItemRow.kt`: a `kidsLayout` option: the first card lines up with the row title (the row bleeds past the
  page padding by one gap so the ring isn't cut) and 18 dp above and below the cards for the ring. Other rows unchanged.
- `ui/nav/NavigationDrawerAndroid.kt`, `ui/nav/NavDrawer.kt`: the sidebar of HomeRailPekeTV and HomeDrawerPekeTV.
  - Closed rail: 104 px in the rail colour; the avatar at 64 px; 36 px outline icons on 64 px tiles; the current page on a
    surface tile in primary, the others in "text, secondary"; Grown-ups at the bottom.
  - Open: 380 px over the page instead of pushing it, with the page faded to 35%; the avatar at 72 px with the kid's
    name and time left; rows with 24 px names; the focused row in primary with "on primary" and the border and ring;
    Grown-ups at 20 px.
  - Unchanged: the gate on the avatar and on Grown-ups, opening on the current page and never on the avatar, and Back
    (the first Back on the home opens the sidebar, the second quits).
- `kids/ui/KidsTimerEffects.kt`: the time-left bar doesn't draw on the home, whose header shows the time instead. The
  warning still shows. (Phase 3 changes another part of this file.)
- `ui/nav/DestinationContent.kt`: the kids home clears the backdrop.
- **Cross-cutting fix**:
  - `MainActivity.kt` and `MainContent.kt` painted the page in tv-material's default Surface colour, which is
    "surface" (`#28335B`), not DESIGN's background (`#1F2747`). Every screen was one shade too light, and the boards'
    surface cards and chips didn't stand out from it.
  - Both root surfaces now use the background colour. This changes the look of every screen, kid and grown-up, towards
    the boards.
- `ui/nav/Backdrop.kt`: no backdrop layer when there is no image (its scrims darkened the top-right corner on their own).
- Strings, English and Spanish: `kids_live_badge`, `kids_home_greeting`, `kids_home_question`,
  `kids_home_time_left_today`, `kids_home_time_left`.
- Spec: `specs/008-canvas-screens/` (spec, plan, research, data model, contract, quickstart, tasks, checklist).

Differences from the boards, on purpose:
- Board 2b's search button and star badge (HomePekeTV) aren't added: Search is in the rail (spec clarification).
- The LIVE badge is 15 px, not 14: DESIGN's Small is the floor.
- The gap from a card to its title is 16 dp, not 10 px, and rows have 18 dp above and below their cards. The boards'
  focused card overlaps its title; the app's ring and scale would cover it.
- The drawer's focused row keeps 007's ring as well as the border (the board draws the border only); DESIGN Focus wants
  both.
- Row titles come from the server and the app ("Continue watching"), not the board's "Keep watching".

## Tests

`kids/KidsHomeLayoutTest.kt` (4), written before the code:
- the card kind per row type;
- no time left without a limit, while unknown or when Hidden;
- "today" only when the allowance runs out first;
- the minutes rounded up, and the near-end state.

004–005's home tests (`HomePage` click and Play-key destinations, the drawer's Back rule) are unchanged and pass.

## Validation

- `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`: BUILD
  SUCCESSFUL, 833 tests, 0 failures.
- `nix fmt`: 3 files changed on the first run, 0 on the second.
- `nix develop -c pre-commit run --all-files`: all hooks passed.

## Screenshots

In `/tmp/claude-1000/-home-aldo-Dev-aldoborrero-Wholphin--worktree-peke-canvas/89310757-42c7-4863-99b6-3742b3ade235/scratchpad/shots/`:
- `p1-home.png`: the kids home. Greeting, clock, keep watching as wide cards with titles under them, the live card,
  posters, and the rail with Home on its tile.
- `p1-later.png`: the second keep-watching card focused. Its title is in the text colour and the others' are in
  "text, secondary".
- `p1-left.png`: the open sidebar, from a build just before the right-edge fix: avatar and name, rows, Grown-ups at the
  bottom, the page faded behind.
- `base-home.png`: the same home on the base build, for comparison. Boards rendered from the canvas: `../boards/`.

Not shown:
- The time-left pill: the emulator's kid has no daily limit. `KidsHomeLayoutTest` covers it.
- The second half of the emulator session. The Windows emulator went offline after this phase's screenshots (`adb`
  reports `offline`; `adb reconnect` didn't help), so no later Phase 1 screenshot was possible.

Two things about the emulator, for the next run:
- It now takes D-pad keys only with `input dpad keyevent`.
- It renders a few seconds behind the input.

## APK size

Clean debug builds, base → Phase 1: arm64-v8a +49,873, armeabi-v7a +49,867, x86_64 +49,864 bytes.
- dex: +48.4 KB of new, unminified Compose code.
- `resources.arsc`: +0.7 KB.

This is a little over the plan's +40 KB estimate. Details are in plan.md, Measured.

## Shared files

- `kids/ui/KidsCards.kt` is created here and used by Phases 2 and 5.
- `kids/ui/KidsTimerEffects.kt` changes again in Phase 3.
- `strings.xml` gets one block per phase.
- `tasks.md` and `plan.md` (Measured) are updated in every phase.

## Commit message

```
Give the kids home its canvas look

The kids home greets the kid by name and shows the time left in its
header instead of the bar. Keep watching draws as wide cards with the
progress inside, live channels as cards with a LIVE badge, and the
rest as posters, each with its title underneath. The sidebar is a
narrow rail that opens over the page with the kid's name and time
left. Pages now sit on the theme's background colour instead of the
surface colour.
```
