# Research: Canvas screens

Decisions for the plan. Board values are canvas px; the app uses 0.75 dp/sp per px (DESIGN.md).

## R1. Board units in code

- **Decision**: a small `ui/theme/Canvas.kt` with `Int.cpx` / `Float.cpx` (dp) and `Int.csp` (sp) that multiply by
  0.75, so layout code can carry the board's numbers ("`320.cpx`" next to the board's `width: 320px`). The type styles of
  007 stay the source for text where a board size matches one (24 px Section → `titleLarge`, 22 px Body → `bodyLarge`,
  18 px Supporting → `bodyMedium`, 16 px Small → `labelMedium`); a board size between styles (28 px greeting, 26 px player
  title, 34 px programme) uses the nearest style with `fontSize = n.csp`, keeping its Nunito weight.
- **Rationale**: reviewers can check a screen against its board line by line; no new theme API.
- **Alternatives**: converting every value by hand (unreadable diffs); new Typography slots (tv-material's are fixed).

## R2. The kids home (Phase 1)

- **Decision**: `HomePage` keeps its view model, rows, clicks, Play key and context menu. In kids mode it passes
  `HomePageContent` a kids header (`KidsHomeHeader`) instead of `HomePageHeader`, a no-op backdrop update and clears
  the backdrop on entry, and draws cards through `KidsHomeCard`, which picks a card kind per row (R3). The board's
  paddings (40/48 px) and gaps (26 px between rows, 14 px title to cards) replace Wholphin's.
- **Rationale**: the behaviour of 004–005 lives in `HomePage` (`clickDestination`, `playKeyDestination`, the remembered
  handlers of C13); leaving it untouched keeps their tests meaningful.
- **Alternatives**: a separate kids home page (duplicates the focus and click wiring that 005 hardened).

## R3. Card kinds

- **Decision**: `kidsCardKind(row)`: Continue watching, Next up and their combined row → **WIDE** (320×180 px, 16:9
  thumb, progress bar 10 px inside a 14 px padding, title under it); Watch live (`TvPrograms`) and channel rows →
  **CHANNEL** (240×112 px surface card, LIVE badge, channel name); everything else → **POSTER** (140×200 px). Titles:
  20 px, text colour when focused, "text, secondary" otherwise. Card corners 24 px (wide, channel), 22 px (poster).
  A pure function, unit tested.
- **Rationale**: the board shows three kinds by row; the row type is known before items load.
- **Alternatives**: from each item's type (an episode in picks would turn wide, unlike the board).

## R4. Time left on the home

- **Decision**: `homeTimeLeft(limits, position)`: null when there's no daily limit, the limit is unknown or the bar is
  Hidden; otherwise the minutes left (`barMinutes`), whether the day's allowance is what runs out (then "{N} min left
  today", else "{N} min left") and `nearEnd`. `KidsTimerEffects` doesn't draw the bar while the top destination is the
  home; the warning toast still shows. The pill: surface, pill shape, padding 10/20 px, outline time-left icon 24 px,
  20 px text in "time"; near the end the text grows to Section size, as the bar does. A pure function, unit tested.
- **Rationale**: one indicator per screen (spec clarification); the bar's own rule for "near the end".

## R5. Rail and drawer

- **Decision**: the existing drawer (`NavDrawer`, `NavigationDrawerAndroid`) keeps its focus logic (005: open focuses
  the current page, never the avatar; Back rules). Its look changes:
  - closed rail 104 px wide on a "rail" colour (the background 16% darker; PekeTV `#1A2140` as on the board), avatar
    64 px, icons 36 px, gap 18 px, padding 32 px top and bottom, the current page on a 64 px surface tile with 22 px
    corners in primary, the others in "text, secondary", Grown-ups' lock at the bottom;
  - open sheet 380 px wide over the page (the page is no longer pushed right) and the page at 35% opacity; header with
    the avatar 72 px, the kid's name (26 px, 900) and the time left (16 px, time colour, from R4); rows with 18 px gap
    between icon and 24 px name, 22 px corners, the current page on surface, the focused row in primary with "on
    primary" and the focus border and ring; Grown-ups at the bottom in 20 px "text, secondary".
- **Rationale**: boards 2b and 2c; outline icons per DESIGN Icons (clarification).
- **Alternatives**: tv-material's `ModalNavigationDrawer` (Peke's copy exists because the stock one can't be restyled).

## R6. Details and seasons (Phase 2)

What exists: the series page (`SeriesDetails`) has a header without a poster, Play/Shuffle/Watched/Favourite buttons,
a row of season cards, extras and "More like this"; a season card opens `SeriesOverview`, which has a `TabRow` of
seasons, the focused episode's details, a row of episode cards and the play buttons (`ExpandablePlayButtons`). The film
page (`MovieDetails`) has the same header and buttons, chapters, extras and "More like this". Kids already lose More,
delete, playlists and people rows through `LocalInterfaceCustomization` (004).

- **Decision**: the board's Details page is the season page (`SeriesOverview`): poster left, the series title, the
  chips (the season's name and its episode count), the series overview, "Play episode N" for the focused episode and the
  round favourite button, then the episode row. The season chip opens the Seasons dialog (KSeasonsPekeTV) when there
  is more than one season; the `TabRow` doesn't show for kids. The series page (`SeriesDetails`) and the film page get
  the same header (`KidsDetailsHeader`: poster, Display title, chips, overview, main Play pill, favourite), and their
  rows below (seasons, chapters, extras, "More like this") draw with the kids cards. Navigation, what Play does, and
  every rule of 004 stay: nothing is added or removed from the pages, only their look.
- Sizes: poster 260×380 px, 30 px corners; title 56 px Display; chips 18 px on surface, 16 px corners, padding
  6/16; overview 22 px, "text, secondary", up to 3 lines, 640 px wide; Play pill 72 px tall, 28 px text, 36 px side
  padding, Peke's play icon 28 px; favourite 72 px round on surface with Peke's heart (primary when a favourite, "text,
  secondary" otherwise). Episodes: wide cards 260×146 px, 22 px corners, "{n}. {name}" 18 px under them, the watched
  badge (Peke's watched icon) and the progress bar. Seasons dialog: 640 px panel on surface, 36 px corners, 36 px
  padding, "Seasons" 36 px, rows 24 px corners with padding 16/22 on the background colour, a 52 px number circle
  (cycling secondary, tertiary, time, primary; the focused row's circle is the background colour with the number in
  "time"), the name 26 px and the episode count 18 px; the focused row is primary with "on primary" text.
- No backdrop behind kids' details pages: their view models don't submit it in kids mode and the page clears it.
- Watched, restart and shuffle stay where they are (kids had them before), drawn as round surface buttons beside the
  favourite with Peke icons where one exists (watched), FontAwesome otherwise.
- **Alternatives**: merging the series page into the season page (changes navigation: out of scope).

## R7. Player and next-up (Phase 3)

What exists: `PlaybackController` (title, subtitle, "ends at") and `PlaybackControls` (seek bar on a Canvas,
previous/rewind/play/forward/next, subtitles and audio on the right, 36 dp buttons); `NextUpEpisode` (a card at the
bottom centre with the countdown in a circle); `KidsTimerEffects`' warning (a time-coloured pill at the top centre for
5 s). The episodes-in-a-row count is private to `PlaybackViewModel` (`autoPlayedInARow`).

- **Decision**: for kids, the controller's title is "{Series} · {episode name}" (or the film's name) at 26 px 900; the
  seek bar is 12 px with 6 px corners, primary on the outline colour at 50% (the thumb stays, it shows focus); the
  transport buttons are circles: play/pause 76 px in primary with "on primary" icon, the others 62 px on the outline
  colour at 92% with white outline icons (previous, rewind and forward keep their drawables, tinted). Audio uses
  Peke's audio icon. Grown-up-only buttons stay hidden (005).
- The warning: while the top page is the player, the warning is a pill at the top right (outline colour at 92%, 40 px
  corners, the buddy 64×56 px, the minutes in "time" 26 px); elsewhere it stays as 007 drew it. Same moments, same
  chime. "This episode can finish" isn't shown (the TV doesn't know it).
- Next up for kids: a panel at the bottom right, 560 px wide, outline colour at 95%, 36 px corners, 26 px padding: the
  image 200×112 px (22 px corners), "Next Up" 18 px, "{n}. {name}" 24 px, Play now (52 px pill, primary, with the
  countdown drawn as a ring around a small play icon) and Stop (surface pill; it does what Back does today: cancel
  autoplay and stay). The in-a-row chip at the bottom left shows "Episode {n} of {cap} in a row" when the profile has a
  cap: `PlaybackViewModel` exposes the count it already keeps as a read-only state (no logic change); tested.
- **Alternatives**: a separate kids player (no: the player stays recognisable, constitution II).

## R8. Live TV (Phase 4)

What exists: `Destination.KidsLiveTv` shows Wholphin's EPG grid (`TvGuideGrid`) of the kid's channels; clicking a
channel plays it, clicking a programme opens a dialog with Watch.

- **Decision**: kids get `KidsLiveGuide`: "Watch live" (44 px Title), then a list of full-width channel cards (surface,
  32 px corners, 24 px padding, 28 px gap): a 160 px tile with 30 px corners in the cycling tile colour with the
  channel's image (or the buddy), the LIVE badge (16 px) and the channel name (22 px, "text, secondary"), the programme
  on now (34 px), its progress (12 px bar, "time" on text at 18%) and "Next: {programme}" (20 px). It reads the same
  `LiveTvViewModel` state (channels and programmes); clicking a card plays the channel exactly as the grid's channel
  cell does. The program dialog isn't reachable from the list (it only offered Watch to kids). Pure helpers for "now"
  and "next" per channel and the progress fraction, unit tested.

## R9. Search, voice, favourites, library (Phase 5)

What exists: search has a 40 dp text box with Material's search icon, FontAwesome's microphone and a view options
button (`fa_sliders`); results are one row per type. Voice listening is a dialog with a pulsing primary circle.
Favourites are tabs per type with Wholphin's grid header (sort, filter, view options, random). Kids reach grids through
folders in their rows, box sets, playlists and extras groups; those show the sort/filter header.

- **Decision**:
  - Search: the field is a 72 px pill on surface with Peke's search icon (32 px, secondary) and 28 px text; the
    microphone a 72 px circle in tertiary with Peke's voice icon in "on primary"; the view options button stays (it is
    there today) with a new Peke **filter** icon (sliders). Result rows draw with the kids cards: shows and films as
    posters 180×264 px, episodes wide; Section titles.
  - Voice: the dialog becomes the board: two tertiary circles (300 px at 18%, 220 px at 30%), Peke's voice icon 160 px,
    "Speak to search" 52 px, what was heard in a surface pill in "time" 30 px, "Press Back to stop" 20 px. States and
    errors as today.
  - Favourites: the title "{Kid}'s favourites" (44 px) with Peke's heart (52 px, primary); the tabs stay (one per
    type the kid has), drawn as chips; each tab's grid uses kids poster
    cards (7 per row, 214 px tall) with the heart badge; the grid header (sort, filter, view options) doesn't show on
    favourites for kids; the hint pill "Press the heart on any show to keep it here" sits under the grid. Empty
    favourites show 007's buddy and a line.
  - Library grids: the title in 44 px and the header's sort and filter as chips at the right: the current sort's name
    (opens the existing sort choice), "Not watched" (the existing played filter toggled) — the view options and random
    buttons keep their place with Peke-style round buttons. Cards are kids posters.
- **Alternatives**: hiding view options from kids (a behaviour change; reported for the owner instead).

## R10. Screensaver, update, microphone (Phase 6)

- **Decision**:
  - Screensaver: while a kid is current, instead of item art, the night scene: background `#121730` (the background
    40% darker), five stars ("text, secondary" at 40–60%), the moon (`time` at 90%), a hill (background colour) along
    the bottom, the sleeping buddy (240×200 px) and the clock (40 px, "text, secondary" at 80%) when on. The buddy moves
    to a new place every `duration` like the neutral screensaver does now. The settings that pick items (types, age)
    no longer apply to kids; animate, duration, clock and dim do. Drawn as one vector drawable (`peke_night.xml`) plus
    the existing sleeping buddy.
  - Update: a left column 360 px (Peke's new **download** icon 120 px in secondary, "Update available" 44 px, installed
    version 18 px, new version 22 px in "time", "Download & Update" 64 px pill primary and "Later" 60 px pill surface)
    and the release notes on a surface panel (30 px corners, "What's new" 22 px). The download progress keeps its
    dialog (its behaviour), restyled with a 14 px bar in secondary.
  - Microphone consent: a dialog on surface, 720 px, 36 px corners, 40 px padding, centred: Peke's voice icon 120 px in
    tertiary, "Search by talking?" 36 px, the explanation 21 px "text, secondary", Allow (primary, focused) and Not now
    (background colour). The denied stages use the same card with one button.

## R11. Icons

- **Decision**: new icons are drawn in both sets with the generator rules of 007's icon pass (48 grid, live area 4–44,
  3.5 strokes, round caps and joins on the root, explicit fills, radii 4/7/9), converted with `Svg2Vector` as in 007
  R8, added to `PekeIcon`, and checked by `IconSpecTest` and `DrawablesTest`.
