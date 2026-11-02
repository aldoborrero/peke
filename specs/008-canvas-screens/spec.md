# Feature Specification: Canvas screens

**Feature Branch**: `008-canvas-screens`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "Feature 008 Canvas screens. Bring the remaining kid screens from Wholphin's structure to
the design canvas boards (*PekeTV.dc.html, 1 canvas px = 0.75 dp) and design/DESIGN.md, taking them as defaults without
a clarification round. Six phases, each its own PR: 1 kids home (HomePekeTV, HomeRailPekeTV, HomeDrawerPekeTV: header
with greeting and time left, rows layout, rail and drawer); 2 details and seasons (DetailsPekeTV, KSeasonsPekeTV);
3 player and next-up (PlayerPekeTV controls, progress bar, time-left warning; KNextUpPekeTV countdown); 4 live TV
(LiveGuidePekeTV); 5 search, voice, favourites, library (KSearchPekeTV, KVoicePekeTV, KFavoritesPekeTV,
KLibraryPekeTV; replace Material mic, search and filter icons with Peke icons, adding icons to the set per DESIGN Icons
and IconSpecTest); 6 screensaver, update available, microphone permission (OtherScreensaverPekeTV, OtherUpdatePekeTV,
OtherMicPekeTV). Out of scope: grown-ups screens (007 phase 6) and behaviour changes; keep every rule of 004-006
(direct play only from Continue watching, no people rows, no trailers, gate rules, Back behaviour)."

Sources: the design canvas boards named above (HTML drawn at 1280×720; 1 canvas px = 0.75 dp/sp), `design/DESIGN.md`
(themes, type, shape and space, focus, icons, Behaviour, Words), constitution 2.1.0.

Builds on [007-peke-interface](../007-peke-interface/spec.md), which gave Peke its themes, Nunito, shapes, the focus
treatment, the pictures and icons, and restyled the picker, the stops and first setup inside Wholphin's layouts. This
feature replaces those layouts on the remaining kid screens with the boards' own. As in 007, it changes how screens
look, not what they do: every flow, rule and gate of [004](../004-tv-setup-flows/spec.md),
[005](../005-security-followups/spec.md) and [006](../006-grown-ups-screens/spec.md) stays as it is.

## Clarifications

### Session 2026-10-27

The owner asked for the boards and DESIGN.md to be taken as defaults without a question round. Where a board and the
app disagree, these are the decisions, recorded here:

- Q: The home boards differ: HomePekeTV has no sidebar and a search button in the header; HomeRailPekeTV has the rail.
  Which one? → A: HomeRailPekeTV with the closed rail, and HomeDrawerPekeTV when it opens: DESIGN Behaviour keeps the
  sidebar on the kids home, and Search is in it. HomePekeTV's header (greeting, time left) is the same header without
  the rail; its search button and star badge aren't added.
- Q: The kids home today shows the focused item's backdrop, logo, details and overview above the rows (Wholphin's
  header). Keep it? → A: No. The boards show a calm page: the greeting header, then rows on the theme background, with
  each card's title under it. The backdrop and the focused-item details don't show on the kids home.
- Q: The home header shows "45 min left today". The time-left bar (007) already shows the same on every screen. →
  A: On the kids home the header's pill takes the bar's place: with a daily limit and the bar on Top or Bottom, the home
  shows the pill and no bar; Hidden shows neither; the other screens keep the bar. The pill grows with the bar's own
  first-warning rule.
- Q: The drawer board draws two-tone icons on the dark sheet. → A: Outline icons, as DESIGN Icons requires (two-tone only
  on a light surface): in "text, secondary" for most items, primary for the page you're on, and "on primary" on the
  focused coral item.
- Q: The details board has no backdrop. → A: The kids' details page draws on the theme background with the poster on the
  left, as the board does; no backdrop image behind it. Grown-ups never see details pages (constitution I).
- Q: The player's warning says "This episode can finish". → A: That line shows only when the kid's rules say so; the
  plugin doesn't tell the TV today, so the warning shows the minutes left and no second line. Nothing is invented.
- Q: The next-up board has "Episode 2 of 3 in a row". → A: Shown when the kid's episodes-in-a-row cap applies (004's
  autoplay rule), with the real count; hidden otherwise.
- Q: The live board shows each channel's art on a coloured tile, with the buddy where a channel has none. → A: The
  channel's own logo on the tile when it has one, else the buddy. The tile colours cycle through tertiary, secondary,
  time and primary in channel order.
- Q: The library board has sort chips (A to Z, Newest, Not watched). → A: They are the sort and filter choices the
  library page already offers kids, drawn as chips; no new sort or filter is added. If the page offers kids none today,
  no chips are added.
- Q: The screensaver board is a sleeping buddy on a night sky. → A: That replaces the kid's screensaver picture; it keeps
  the existing screensaver settings (when it starts, the clock) and moves the buddy slowly so nothing burns in.
- Q: The update board shows a download icon and a downloading line. → A: The update page keeps what it does today
  (grown-up only, behind the gate); the board gives it its layout. An icon the set lacks is added to both sets under
  DESIGN's icon rules.
- Q: Which icons replace Material's? → A: Search uses Peke's search, the microphone Peke's voice; any filter or sort
  icon a kid screen still shows gets a Peke icon, added to both sets if missing, following DESIGN Icons and passing
  IconSpecTest.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A home that greets the kid (Priority: P1) — Phase 1

Lucy turns on the TV. Her home says "Hi, Lucy!" and "What do you want to watch?", shows "45 min left today" on the
right, and below it her rows: Keep watching as wide cards with a progress bar, Watch live as channel cards with a LIVE
badge, and her picks as posters, each with its name underneath. On the left a quiet rail shows her avatar, Search, Home
(highlighted), Favourites, Live TV and a lock for Grown-ups. Pressing Left opens the sidebar wide: her avatar, name and
time left at the top, the items with their names, Grown-ups small at the bottom, and the page behind dimmed.

**Why this priority**: the home is where every session starts.

**Independent Test**: open the kids home for a kid with a daily limit and live channels; compare with HomeRailPekeTV,
open the sidebar and compare with HomeDrawerPekeTV; check that clicks, the Play key, Back and the gate behave as in
004–005.

**Acceptance Scenarios**:

1. **Given** the kids home, **When** it shows, **Then** a header with "Hi, {Kid}!" (Headline) and "What do you want to
   watch?" (Supporting) sits above the rows, and no backdrop or focused-item details show.
2. **Given** a kid with a daily limit and the bar on Top or Bottom, **When** the home shows, **Then** the header shows the
   time-left pill ("{N} min left today", time colour, with the time-left icon) at the right and the time-left bar doesn't
   show on the home; with Hidden, or without a limit, neither shows.
3. **Given** a Keep watching row, **When** it shows, **Then** its cards are wide (16:9), with a rounded progress bar
   inside the card's bottom edge, and the title under the card.
4. **Given** a Watch live row, **When** it shows, **Then** each channel is a surface card with a LIVE badge and the
   channel's name.
5. **Given** any row, **When** a card has focus, **Then** it has 007's border, ring and scale, and its title under it is in
   the text colour while the others are in "text, secondary".
6. **Given** the rail closed, **When** the home shows, **Then** the rail is narrow, shows the kid's avatar at the top,
   outline icons for Search (if allowed), Home, Favourites and Live TV (if the kid has channels), the current page on a
   rounded surface tile in primary, and Grown-ups (lock) at the bottom.
7. **Given** the sidebar opens, **When** it shows, **Then** it is wide with the avatar, the kid's name and their time
   left at the top, each item with its icon and name, the focused item in primary with "on primary" text, Grown-ups small
   at the bottom, and the home behind dimmed.
8. **Given** the home, **When** the kid clicks a card, presses Play or presses Back, **Then** the outcome is 004–005's:
   Continue watching plays straight away, every other row opens the details page, the first Back opens the sidebar and a
   second Back quits; opening the sidebar focuses the current page, never the avatar.

---

### User Story 2 - A show's page and its seasons (Priority: P1) — Phase 2

Lucy opens Space Pals. The page shows its poster on the left, the title big, chips with the season and the number of
episodes, two lines about the show, a big "Play episode 4" button and a heart for favourites; below, the episodes as
cards with their number and name, the watched ones ticked and the one in progress with its bar. Choosing the season
opens a "Seasons" list: one row per season with its number in a coloured circle and how many episodes it has.

**Why this priority**: every row except Keep watching leads here (004).

**Independent Test**: open a series with several seasons and a film; compare with DetailsPekeTV and KSeasonsPekeTV;
check that the page has no More, delete, playlist, trailer or people rows.

**Acceptance Scenarios**:

1. **Given** a series, **When** its page shows, **Then** it has the poster on the left, the title in Display style, the
   season and episode-count chips, the overview in two or three lines, the main Play button (72 px tall) and the round
   favourite button, and the episode row below.
2. **Given** the episode row, **When** it shows, **Then** each episode is a wide card with "{n}. {name}" under it, a
   ticked badge when watched and a progress bar when started.
3. **Given** a series with more than one season, **When** the kid opens the season choice, **Then** the Seasons list shows
   one row per season with its number in a coloured circle, its name and its episode count, opening on the current
   season; choosing one shows its episodes.
4. **Given** a film, **When** its page shows, **Then** it has the same layout without seasons and episodes.
5. **Given** any details page for a kid, **When** it shows, **Then** it has no More button, delete, playlists, remote
   trailers or people rows (004), and Play behaves as before.

---

### User Story 3 - A player kids can read (Priority: P1) — Phase 3

While Space Pals plays, the controls show the show's name, a thick coral progress bar and three round buttons: pause
(the big coral one), next episode and subtitles. With five minutes left a dark pill at the top right shows the buddy
and "5 minutes left" in sunny letters. At the end, a Next Up panel shows the next episode's picture and name, "Play now"
with its countdown and "Stop", and, when the kid has an episodes-in-a-row cap, "Episode 2 of 3 in a row".

**Why this priority**: kids spend most of their time here.

**Independent Test**: play an episode; show the controls; compare with PlayerPekeTV; reach the end of an episode with a
next one and compare with KNextUpPekeTV.

**Acceptance Scenarios**:

1. **Given** the controls show, **When** they're on screen, **Then** the title ("{Series} · Episode {n}" or the film's
   name) sits above a thick progress bar in primary on a dark track, and the transport buttons are round: play or pause
   large in primary, the others smaller on a dark surface with white outline icons.
2. **Given** a time warning, **When** it shows, **Then** it is a dark pill at the top right with the buddy and the minutes
   left in the time colour; it doesn't flash, and the chime and timing are as before.
3. **Given** the end of an episode with a next one, **When** next-up shows, **Then** it is a panel at the bottom right with
   the next episode's image, "Next Up", its number and name, Play now (focused, with the countdown drawn on it) and Stop;
   what each does is unchanged.
4. **Given** a kid with an episodes-in-a-row cap, **When** next-up shows, **Then** a chip at the bottom left says
   "Episode {n} of {cap} in a row"; without a cap it doesn't show.
5. **Given** the player for a kid, **When** the controls show, **Then** nothing the kid didn't have before appears (no
   debug info, no extra options), per 005.

---

### User Story 4 - Live TV as a list of channels (Priority: P2) — Phase 4

Lucy opens Watch live. Each channel is a wide card: its picture on a coloured tile, a LIVE badge and the channel's name,
the programme on now in big letters, how far it has gone in a sunny bar, and what comes next.

**Why this priority**: only kids with channels see it.

**Independent Test**: a kid with two live channels opens Live TV; compare with LiveGuidePekeTV; picking a channel plays it
as before, and a refused channel shows 006's message.

**Acceptance Scenarios**:

1. **Given** Live TV, **When** it shows, **Then** a "Watch live" title is above one full-width card per channel of the kid's
   channels, in the order the server gives.
2. **Given** a channel card, **When** it shows, **Then** it has the channel tile (logo, else the buddy), the LIVE badge,
   the channel name, the programme on now (Headline), its progress in the time colour and "Next: {programme}" when
   known.
3. **Given** a channel with no guide data, **When** its card shows, **Then** it shows the channel name in the programme's
   place and no bar or next line.
4. **Given** a channel card, **When** the kid clicks it, **Then** it plays as before, and a refused channel shows 006's
   message page.

---

### User Story 5 - Search, voice, favourites and a library (Priority: P2) — Phase 5

Lucy opens Search. A big rounded search field with Peke's search icon and, when voice search is on for this TV, a round
lilac microphone. Results are grouped (Shows as posters, Films as wide cards). Speaking shows a big microphone, "Speak to
search" and what she said. Her favourites page says "Lucy's favourites" with a heart, her favourites as posters with a
heart badge and a hint about the heart. A library from her picks shows its name, posters in a grid and chips to sort.

**Why this priority**: used often, but after home, details and the player.

**Independent Test**: compare each page with KSearchPekeTV, KVoicePekeTV, KFavoritesPekeTV and KLibraryPekeTV; no
Material icon remains on them.

**Acceptance Scenarios**:

1. **Given** Search, **When** it shows, **Then** the field is a full-width pill with Peke's search icon, and the
   microphone, when allowed (005), is a round button in tertiary with Peke's voice icon.
2. **Given** results, **When** they show, **Then** each group has a Section title and cards per DESIGN, titles under them.
3. **Given** voice search listening, **When** it shows, **Then** the page has the microphone in tertiary circles, "Speak
   to search" (Title), the words heard in a pill in the time colour, and "Press Back to stop"; Back stops as before.
4. **Given** Favourites, **When** it shows, **Then** the title is "{Kid}'s favourites" with Peke's heart, the items are
   posters with a heart badge, and a hint pill says "Press the heart on any show to keep it here"; empty favourites keep
   007's empty state.
5. **Given** a library grid a kid reaches, **When** it shows, **Then** the title is the library's name, the items a poster
   grid with titles under them, and its sort and filter choices, if the page offers kids any, are chips at the top right.
6. **Given** any of these pages, **When** an icon shows, **Then** it is from Peke's outline set; every icon added to the
   set follows DESIGN Icons and passes the icon checks.

---

### User Story 6 - The other screens (Priority: P3) — Phase 6

When the TV sits idle, the screensaver shows the sleeping buddy on a night sky with the moon and the time. A grown-up
who opens "Update available" sees the versions, what's new and big Download and Later buttons. A grown-up turning on
voice search sees "Search by talking?" with the microphone, the explanation and Allow / Not now.

**Why this priority**: seen rarely.

**Independent Test**: start the screensaver; compare with OtherScreensaverPekeTV. Update and microphone pages are behind
the parent gate; compare with OtherUpdatePekeTV and OtherMicPekeTV when a grown-up opens them.

**Acceptance Scenarios**:

1. **Given** the screensaver starts, **When** it shows, **Then** it is the sleeping buddy on a night sky with the moon,
   a few stars, a hill and the time when the clock is on; the buddy moves slowly; any key leaves it as before.
2. **Given** an update, **When** its page shows, **Then** the left column has the download icon, "Update available"
   (Title), the installed and new versions (new in the time colour) and Download & Update / Later, and the right panel
   "What's new" and the download progress; what each button does is unchanged.
3. **Given** the microphone consent (005), **When** it shows, **Then** it is a dialog with the voice icon, "Search by
   talking?" (Headline), the explanation, and Allow (focused) / Not now; the permission flow is unchanged.

---

### User Story 7 - Measured, unchanged behaviour (Priority: P2) — all phases

The owner reviews each phase as one PR, with its APK size change and no behaviour change.

**Independent Test**: each phase's report records the size per ABI and the unchanged unit tests of 004–006.

**Acceptance Scenarios**:

1. **Given** the end of each phase, **When** it is reported, **Then** the APK size per ABI against the previous phase,
   the validation results and screenshots are recorded.

### Edge Cases

- A kid with no daily limit: no time-left pill or line anywhere (home header, drawer).
- A long kid name ("Maximiliano"): the greeting wraps to two lines rather than shrinking; the drawer's name ellipsises
  after one line.
- Spanish strings 20–30% longer wrap rather than shrink (DESIGN Type).
- A Keep watching item with no 16:9 image falls back to the poster or the series' image, then to a coloured tile with
  the title, as the cards do today.
- A row with one item, or an empty row: as today (empty rows don't show).
- A series with one season: the season chip shows but there is no season choice to open.
- An episode with no number: "{name}" alone.
- The player with MPV: the controls and the warning draw over both backends.
- A channel with no logo and no guide: the buddy tile and the channel name.
- Voice search off for this TV: no microphone on Search (005).
- Favourites with only films or only shows: one grid, the same look.
- Theme changes: every new surface reads the theme roles, so Mandarina, Caramelo and Papel work too (Papel's dark focus
  border).

## Requirements *(mandatory)*

### Functional Requirements

**All phases**

- **FR-001**: Each screen MUST follow its board's layout, sizes, spacing and hierarchy at 1 canvas px = 0.75 dp/sp, using
  007's theme roles, type styles, shapes and focus treatment; colours MUST come from the theme roles, never fixed values,
  except the pictures (mascot, avatars), which keep their PekeTV colours.
- **FR-002**: No behaviour of 004–006 MAY change: what a click or the Play key opens, direct play only from Continue
  watching, no people rows, no remote trailers, no More/delete/playlists for kids, every parent gate, Back on every
  screen, voice search consent, the timer and its warnings. Their unit tests MUST stay green without changing their
  behaviour assertions.
- **FR-003**: Every new or changed string MUST ship in English and Spanish (DESIGN Words).
- **FR-004**: Icons on kid screens MUST be Peke's outline icons tinted with theme roles; two-tone only on a light surface
  (DESIGN Icons). Icons added to the set MUST be drawn in both sets under DESIGN's rules and pass the icon checks.
- **FR-005**: Each phase MUST record the APK size per ABI against the previous phase (clean debug builds, as in 007),
  and MUST NOT remove or weaken MPV, the FFmpeg or AV1 decoders or their keep rules, or add a library dependency.

**Phase 1, home**

- **FR-006**: The kids home MUST show a header with "Hi, {Kid}!" and "What do you want to watch?", and no backdrop or
  focused-item details.
- **FR-007**: The header MUST show the time-left pill when the kid has a daily limit and the bar position isn't Hidden;
  while the home is on screen the time-left bar MUST NOT show. The pill follows the bar's first-warning rule (bigger).
- **FR-008**: Home rows MUST draw: Keep watching as 16:9 cards with an inner progress bar; Watch live as surface cards
  with a LIVE badge and the channel name; other rows as posters; titles under every card, the focused one in the text
  colour and the rest in "text, secondary"; DESIGN's gaps between cards and rows.
- **FR-009**: The closed rail MUST show the avatar, outline icons, the current page on a rounded surface tile in primary,
  and Grown-ups at the bottom; the open sidebar MUST show the avatar with the kid's name and time left, items with icon
  and name, focused item in primary with "on primary", Grown-ups small at the bottom, and dim the page behind.

**Phase 2, details and seasons**

- **FR-010**: Kids' series and film pages MUST follow DetailsPekeTV: poster left, Display title, chips, overview, main
  Play pill and round favourite button, episode cards with "{n}. {name}", watched badge and progress, on the theme
  background without a backdrop.
- **FR-011**: The season choice MUST follow KSeasonsPekeTV: a "Seasons" panel with one row per season (number circle,
  name, episode count), opening on the current season.

**Phase 3, player and next-up**

- **FR-012**: The player controls MUST follow PlayerPekeTV: title above a thick primary progress bar, round transport
  buttons (play/pause large in primary, others on a dark surface).
- **FR-013**: The time warning in the player MUST be a dark pill at the top right with the buddy and the minutes left in
  the time colour, at the same moments as today.
- **FR-014**: Next-up MUST follow KNextUpPekeTV, with the countdown drawn on Play now and the episodes-in-a-row chip only
  when the cap applies.

**Phase 4, live TV**

- **FR-015**: The kids' Live TV page MUST follow LiveGuidePekeTV: "Watch live" title and one full-width card per channel
  with tile, LIVE badge, channel name, programme on now, progress in the time colour and the next programme.

**Phase 5, search, voice, favourites, library**

- **FR-016**: Search MUST follow KSearchPekeTV (pill field, round tertiary microphone when allowed, grouped results), and
  voice listening KVoicePekeTV.
- **FR-017**: Favourites MUST follow KFavoritesPekeTV (title with the kid's name and heart, poster grid with heart badges,
  the hint pill).
- **FR-018**: A library grid a kid reaches MUST follow KLibraryPekeTV (title, poster grid, sort/filter chips only where
  the page already offers them).
- **FR-019**: No Material search, microphone or filter icon MAY remain on these kid pages.

**Phase 6, other screens**

- **FR-020**: The screensaver MUST follow OtherScreensaverPekeTV, keep its settings, and move the buddy slowly.
- **FR-021**: The update page MUST follow OtherUpdatePekeTV and the microphone consent OtherMicPekeTV, with their flows
  unchanged.

### Key Entities

- **Board**: a canvas screen drawn at 1280×720 that a phase reproduces; its values convert at 0.75 dp per px.
- **Card kind**: wide (16:9, Keep watching and episodes), poster (2:3), channel (surface card with LIVE badge).
- **Icon**: a Peke icon drawn in two-tone and outline under DESIGN's rules.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Side by side with its board, each screen has the same elements in the same places and order; every
  difference is listed in its phase report with the reason.
- **SC-002**: No kid screen in this feature's scope shows a Material icon, Wholphin's backdrop header or a fixed colour
  outside the theme.
- **SC-003**: 100% of 004–006's unit tests pass unchanged in their behaviour assertions after every phase.
- **SC-004**: A kid can reach every item on every restyled screen with the D-pad alone, and focus never lands first on
  Grown-ups or the avatar.
- **SC-005**: Each phase's APK change is recorded and stays within ±100 KB unless the phase adds pictures, which are
  listed.

## Assumptions

- The boards are the reference for layout; DESIGN.md wins where they disagree (icons, focus). Board colours are PekeTV's
  roles; other themes use their own values for the same roles.
- Kid screens are the ones a kid profile reaches; grown-up screens (007 Phase 6) stay as they are, except the update page
  and the microphone consent, which the owner put in this feature.
- Screenshots are taken on the emulator without typing the parent code or starting playback on purpose; screens behind
  the gate or only reachable during playback are reported, not forced.
- Phases ship in order 1 → 6; each can be committed on its own. Shared files are named in the phase reports.
