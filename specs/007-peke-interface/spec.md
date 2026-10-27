# Feature Specification: Peke's interface

**Feature Branch**: `007-peke-interface`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "Feature 007 Peke's interface: implement design/DESIGN.md. Six phases, each its own PR:
1 visual base (four themes as colour roles, PekeTV default; migrate stored Wholphin AppThemeColors to PekeTV keeping
old proto numbers reserved; bundle Nunito 700/800/900 as font resources; Typography and Shapes per DESIGN's mapping;
focus treatment in shared defaults; theme picker in Settings with four themes). 2 assets as vector drawables (mascot
moods, avatars, two-tone and outline icons, adaptive app icon, TV banner). 3 kid screens (picker with the plugin's
avatar field, API 1.1 `avatar` capability, falling back to the Jellyfin image or the initial; home with the sidebar;
details and player; kid-visible timer bar in the time colour with an icon, keep Top/Bottom/Hidden). 4 stop screens
with the mascot (time's up, break, bedtime, can't play, offline) and loading, empty and error states. 5 first setup
with the canvas look. 6 grown-ups screens full-screen, blocked on the owner's review of the 006 flows, not
implemented. Take DESIGN.md's decisions as defaults and record them; no clarification round."

Sources: `design/DESIGN.md` (themes and contrast table, type, shape and space, focus, the buddy, avatars, icons,
screens, Behaviour, Words, Mapping to Compose), `design/README.md`, the SVGs in `design/assets/`, the design canvas
(<https://claude.ai/artifact/Qz7qEF5wga9NjR5ihsc1aq>, page "PekeTV palette"; DESIGN.md stands in for it, as it can't
be opened here), constitution 2.1.0.

Builds on [004-tv-setup-flows](../004-tv-setup-flows/spec.md), [005-security-followups](../005-security-followups/spec.md)
and [006-grown-ups-screens](../006-grown-ups-screens/spec.md), all merged. This feature changes how screens look, not
what they do: every flow, rule and gate of 004–006 stays as it is. Where DESIGN.md's Behaviour section describes a
behaviour, 004–006 already built it.

## Clarifications

### Session 2026-10-27

The owner asked for DESIGN.md's decisions to be taken as defaults without a question round. They are recorded here:

- Q: Which theme does a TV start on, and what happens to a TV that saved a Wholphin theme? → A: PekeTV for every TV.
  A saved Wholphin theme (purple, blue, green, orange, bold blue, OLED black, red, brown) becomes PekeTV once, on
  first launch of this version. The Wholphin values stay reserved in the stored settings format and are never
  renumbered or reused.
- Q: Who picks the theme, and where? → A: A grown-up, in Settings › This TV, behind the parent code like the rest of
  Settings. It applies to the whole TV. A per-kid theme ("a household or a kid can pick later") is out of scope.
- Q: Which themes does the picker offer? → A: PekeTV, Mandarina, Caramelo and Papel, in that order; Papel is labelled
  as the daytime theme. The Wholphin themes don't come back.
- Q: Which font? → A: Nunito, weights 700, 800 and 900 only, shipped inside the app (OFL, with its license among the
  app's open-source licenses). No font is downloaded at run time.
- Q: How big are DESIGN's pixel values on a TV? → A: 1 canvas px = 0.75 dp/sp (DESIGN.md), so the 28 px title is 21 sp
  and a 24 px corner is 18 dp. Where DESIGN gives a range, the plan picks one value per role.
- Q: Grown-up screens and focus? → A: Grown-up screens use the same palette and font, with the border and ring but no
  scale-up (DESIGN Focus). Their full-screen layout is Phase 6, which waits for the owner's review of the 006 flows.
- Q: Where does a kid's avatar come from? → A: From the Jellyfin Kids plugin: a field of the kid's profile, chosen by a
  grown-up in the dashboard, offered when the plugin advertises the `avatar` feature (Kids API 1.1). Without it, or
  with an unknown value, the kid's Jellyfin profile image shows, and without that the kid's initial on a coloured
  circle. Picking avatars on the TV is out of scope (the dashboard decides, Principle III).
- Q: The two-tone icons are PekeTV only. What do other themes show? → A: The outline icons, tinted with the theme's
  roles (DESIGN Icons). The mascot, avatars, app icon and banner keep their PekeTV colours in every theme: they are
  pictures, not interface colours.
- Q: What does the timer bar look like? → A: A bar in the theme's "time" colour with the time-left icon and the
  minutes in text, where the Top/Bottom/Hidden setting puts it. In the last five minutes (the plugin's first warning
  point) it grows and shows the warning colour treatment, at the same moment as the existing warning; it never
  flashes.
- Q: Does Phase 6 get tasks? → A: It is specified (User Story 7) and gets a placeholder task list marked blocked; no
  task in it is started until the owner finishes the review of the 006 flows.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Peke looks like Peke (Priority: P1) — Phase 1, visual base

Leo turns on the TV. Peke opens in PekeTV: night-blue background, navy cards, coral buttons, rounded shapes and
Nunito's round letters. Wherever the remote's focus lands, the item has a white border, a coral ring around it and
grows a little, so Leo sees it from the sofa. A parent who once picked Wholphin's purple theme on this TV also sees
PekeTV now. In Settings › This TV, behind the parent code, a grown-up can switch the whole TV to Mandarina, Caramelo
or Papel (for daytime).

**Why this priority**: every later phase draws with these colours, fonts, shapes and focus; without them each screen
would restyle itself.

**Independent Test**: install over a TV that had a Wholphin theme saved; the app opens in PekeTV; focus anything on the
kids home and see the border, ring and scale; change the theme in Settings and see every screen follow.

**Acceptance Scenarios**:

1. **Given** a new install, **When** Peke opens, **Then** it uses PekeTV.
2. **Given** a TV with any Wholphin theme saved, **When** this version first opens, **Then** the stored theme is PekeTV
   and stays so on later launches.
3. **Given** a stored theme value Peke doesn't know (damaged or from a newer version), **When** Peke opens, **Then** it
   uses PekeTV.
4. **Given** the kids home, **When** a card or avatar takes focus, **Then** it shows a 4.5 dp border in the theme's
   focus-border colour, a 6 dp ring in the primary colour and scales up 6–8%; on a grown-up screen only the border and
   ring show.
5. **Given** Settings › This TV, **When** the grown-up opens Theme, **Then** the list shows PekeTV, Mandarina, Caramelo
   and Papel, opens on the current one, and picking one restyles the app at once and keeps it after a restart.
6. **Given** Papel, **When** an item has focus, **Then** its border is dark (`#1E2A33`), not white.
7. **Given** any screen in any theme, **When** text is drawn, **Then** it uses Nunito at 700, 800 or 900, and no text
   style is smaller than DESIGN's Small (15 px, 11 sp).

---

### User Story 2 - The pictures exist in the app (Priority: P1) — Phase 2, assets

The buddy, its five moods, the six avatars, the twenty icons in both sets, the launcher icon and the TV banner are
part of the app and look like the design's SVGs. Peke's launcher icon on the TV home is the buddy, and its banner says
"peke for Jellyfin".

**Why this priority**: Phases 3–5 place these pictures; the launcher icon and banner are Peke's identity on the TV
(Principle VII).

**Independent Test**: a preview page or test lists every drawable; each renders without error; the launcher shows the
new icon and banner.

**Acceptance Scenarios**:

1. **Given** each SVG under `design/assets/` (except the editable `logo-peke-text.svg`), **When** it is converted,
   **Then** the app has a matching drawable, and any SVG feature the conversion can't keep is listed with what was done
   instead.
2. **Given** the Android TV launcher, **When** Peke is listed, **Then** it shows the buddy as the app icon (adaptive,
   with a separate background and foreground) and the 320×180 banner, never the Jellyfin logo.
3. **Given** an outline icon, **When** it is drawn in any theme, **Then** it takes the colour it is tinted with.

---

### User Story 3 - A kid finds their own face and their shows (Priority: P1) — Phase 3, kid screens

Mia can't read. On "Who's watching?" she finds the girl with the white background, which her parent picked for her in
the dashboard. Her home has the sidebar with her avatar at the top, and rows of big rounded cards. A show's page shows
its seasons and episodes in the same style; the player shows a sunny time bar with a small clock, which grows when
only five minutes are left.

**Why this priority**: these are the screens kids use every day.

**Independent Test**: with a plugin that offers avatars and one that doesn't, open the picker, the home, a series, a
film and the player for a kid with a daily limit.

**Acceptance Scenarios**:

1. **Given** the plugin advertises `avatar` and Mia's profile has `mia`, **When** the picker shows, **Then** Mia's tile
   is the Mia avatar on its white circle with her name under it.
2. **Given** the plugin doesn't advertise `avatar`, or the value is missing or unknown, **When** the picker shows,
   **Then** the kid's Jellyfin profile image shows, and without one their initial on a circle in a theme colour.
3. **Given** the picker, **When** it opens, **Then** focus starts on the first kid, never on Grown-ups (Principle V).
4. **Given** the kids home, **When** the sidebar opens, **Then** it shows the kid's avatar, Search (if allowed), Home,
   Favourites, Live TV (if the kid has channels) and Grown-ups at the bottom, with outline icons; the selected item is
   drawn in primary and the others in "text, secondary" while closed.
5. **Given** a series or film details page, **When** it shows, **Then** it follows DESIGN's look and keeps 004's kids
   rules (no More, delete, playlists, remote trailers or people rows).
6. **Given** a kid with a daily limit and the timer bar on Top or Bottom, **When** the kid watches, **Then** a bar in
   the "time" colour with the time-left icon and the minutes left shows there; Hidden shows nothing.
7. **Given** five minutes or less left, **When** the existing warning shows, **Then** the bar grows and stays that way
   until the time ends; nothing flashes.

---

### User Story 4 - Stopping is friendly (Priority: P2) — Phase 4, stops and states

Leo's time runs out. The buddy waves: "That's all for today!". At bedtime the buddy sleeps; in a break it shows when
he can watch again; a video that won't play gets the puzzled buddy and "Oops, this one won't play"; with the server
unreachable the buddy searches for a signal. Loading shows the buddy or soft placeholders, an empty row says so
kindly, and Grown-ups sits small in the same corner on every stop.

**Why this priority**: stops happen every day and must feel fair (DESIGN Principles), but they already work; this
phase changes their look.

**Independent Test**: force each stop and state (time's up, break, outside hours, can't play, offline, loading, empty,
error) and compare with the canvas boards 6a–6d and S1–S5.

**Acceptance Scenarios**:

1. **Given** each stop, **When** it shows, **Then** it has the mood from DESIGN's mascot table, a Display-style title,
   one supporting line and Grown-ups in the same corner, never focused first.
2. **Given** an error for a kid, **When** it shows, **Then** it has no HTTP status or technical detail; those show only
   on grown-up screens.
3. **Given** every new string, **When** the app is in Spanish, **Then** it is translated and wraps to two lines rather
   than shrinking.

---

### User Story 5 - Setting up looks like Peke too (Priority: P3) — Phase 5, first setup

A parent sets up a new TV: select or add the server, pick the kids, sign in, Quick Connect, the administrator. Each
step has the buddy in the header, the palette and the big focus, and does exactly what 004 built.

**Why this priority**: seen once per TV.

**Independent Test**: reset the TV (by the owner, not the agent) and walk through setup; every step matches canvas
boards 21–26 and works as before.

**Acceptance Scenarios**:

1. **Given** each setup step, **When** it shows, **Then** it uses the theme, the buddy header and the grown-up focus
   (border and ring, no scale), and 004's behaviour and tests are unchanged.

---

### User Story 6 - Measured size (Priority: P2) — all phases

The owner knows what the font and the pictures cost before merging each phase.

**Independent Test**: each phase's plan states the expected change and records the measured APK size per ABI.

**Acceptance Scenarios**:

1. **Given** the end of each phase, **When** it is ready to merge, **Then** the APK size per ABI is recorded next to
   the base's size, both built the same way (FR-027).

---

### User Story 7 - Grown-ups screens full-screen (Priority: P3) — Phase 6, blocked

The parent menu, Add time, a kid's rules, the grown-up setup and Settings become full-screen pages in the palette, with
plain lists and no mascot (canvas boards 8–12), instead of dialogs over the kid's screen.

**Status**: **Blocked** on the owner's review of the 006 grown-up flows (memory: grown-ups flows review). Nothing in
this story is implemented in 007 until that review ends; it may be re-specified then.

**Acceptance Scenarios** (provisional):

1. **Given** the parent gate passed, **When** the menu opens, **Then** it is a full-screen page in 006's fixed order,
   with border-and-ring focus and no scale.

---

### Edge Cases

- A theme value written by a newer Peke and unknown here falls back to PekeTV (FR-003), without rewriting it until a
  grown-up picks a theme.
- The theme changes while a kid watches (a grown-up changed it): screens restyle on the next composition; playback
  isn't interrupted.
- A kid name longer than the tile wraps to two lines, then ellipsis; it never shrinks below Body size.
- Spanish strings 30% longer wrap rather than shrink (DESIGN Type).
- A kid profile image that fails to load shows the initial (FR-018).
- The plugin offers `avatar` but returns a value outside the six: treated as no avatar.
- The timer bar with no daily limit (unlimited kid) doesn't show.
- A low-end TV box: focus animation is scale and opacity only, no blur (DESIGN Focus).
- MPV's own surface: the timer bar draws over both player backends.

## Requirements *(mandatory)*

### Functional Requirements

**Phase 1, visual base**

- **FR-001**: Peke MUST offer four themes, PekeTV, Mandarina, Caramelo and Papel, each one set of the colour roles in
  DESIGN's Themes table (background, surface, primary, on primary, secondary, time, tertiary, text, text secondary,
  outline, focus border), with the table's exact values.
- **FR-002**: PekeTV MUST be the default theme for a new install.
- **FR-003**: A stored Wholphin theme, an unset value or an unknown value MUST resolve to PekeTV; on first launch of this
  version a stored Wholphin value MUST be rewritten to PekeTV once. The Wholphin values MUST stay reserved in the
  stored format, never renumbered or reused.
- **FR-004**: Every screen MUST read colours through the theme's roles, so switching theme restyles every screen;
  text on primary MUST use "on primary".
- **FR-005**: Each theme's contrast-critical pairs MUST meet DESIGN's rules: text, text secondary, time and on primary
  4.5:1; primary, secondary, tertiary and focus border 3:1 on background and on surface; primary vs secondary at least
  1.28:1 in PekeTV and 1.5:1 in the others. An automated test MUST check these against the values in DESIGN's
  contrast table (±0.01).
- **FR-006**: Peke MUST ship Nunito at weights 700, 800 and 900 inside the app, and use it for all text, mapped to
  DESIGN's type styles (Display, Title, Headline, Section, Body, Supporting, Small) at 0.75 sp per canvas px. No
  downloadable fonts, and Nunito's OFL license MUST be listed with the app's licenses.
- **FR-007**: Corners MUST follow DESIGN's Shape (small 16 px, medium 22–24 px, large 36 px; pills fully rounded; avatars
  round) at 0.75 dp per px.
- **FR-008**: Focus MUST be set once, in shared defaults the components use: a 6 px border in the theme's focus-border
  colour, an 8 px ring in primary, and a 6–8% scale on kid screens; grown-up screens keep border and ring without
  scale. Motion is scale and opacity only.
- **FR-009**: Settings › This TV MUST have a Theme choice listing the four themes (Papel marked as daytime), opening on
  the current one, that applies at once and persists; it is behind the parent code like the rest of Settings.
- **FR-010**: The Wholphin themes MUST NOT be offered anywhere.

**Phase 2, assets**

- **FR-011**: The app MUST contain vector drawables for the buddy and its five moods, the six avatars, the twenty
  two-tone and twenty outline icons, the logo and the TV banner, converted from `design/assets/`.
- **FR-012**: The outline icons MUST be tintable; the two-tone icons keep their PekeTV colours.
- **FR-013**: The launcher icon MUST be adaptive (background and foreground layers, with a monochrome layer), drawn from
  `app-icon.svg`, and the TV banner from `tv-banner.svg`, replacing Wholphin-derived ones.
- **FR-014**: Every conversion MUST be checked to render, and any SVG feature a vector drawable can't express
  (gradients beyond what VectorDrawable supports, filters, masks, `<text>`, `<use>`, CSS) MUST be listed with the
  workaround.

**Phase 3, kid screens**

- **FR-015**: The kids picker MUST show each kid with their avatar (FR-017–FR-018), name in Headline style, Grown-ups
  last and never focused first.
- **FR-016**: Peke MUST read the kid's avatar from the plugin profile when the plugin advertises the `avatar` feature
  (Kids API 1.1), cache it with the profile for offline launch, and accept only the six known avatar names.
- **FR-017**: Each avatar MUST be drawn on its round background colour from DESIGN's Avatars table.
- **FR-018**: Without a usable avatar, the picker and sidebar MUST show the kid's Jellyfin profile image, and without
  one (or if it fails to load) the kid's initial on a circle in a theme colour.
- **FR-019**: The kids home MUST use the sidebar from DESIGN Behaviour with outline icons, the kid's avatar at the top
  and Grown-ups at the bottom, and cards and rows per DESIGN Shape and space.
- **FR-020**: Series and film details pages and the player overlay MUST follow DESIGN's look while keeping 004's kid
  rules.
- **FR-021**: The timer bar MUST be visible to the kid in the "time" colour with the time-left icon and the minutes
  left, at the Top, Bottom or Hidden position the existing setting chooses; from the plugin's first warning point it
  MUST be more prominent (taller and bolder), together with the existing warning, without flashing.

**Phase 4, stops and states**

- **FR-022**: Time's up, break, outside allowed hours, can't play and can't reach the server MUST each show the mood in
  DESIGN's mascot table, a title and one supporting line from DESIGN Words, and Grown-ups small in the same corner.
- **FR-023**: Starting up, home loading, empty states and errors MUST follow canvas boards S1–S5; kid-facing errors carry
  no technical detail.
- **FR-024**: Every new or changed string MUST ship in English and Spanish.

**Phase 5, first setup**

- **FR-025**: Every setup step (select server, add server, select user, sign in, Quick Connect, administrator sign-in)
  MUST use the theme, the buddy header and grown-up focus, with 004's behaviour unchanged.

**Phase 6, grown-ups screens (blocked)**

- **FR-026**: The parent menu, Add time, a kid's rules, grown-up setup and Settings SHOULD become full-screen pages per
  canvas boards 8–12. Blocked: not implemented until the owner's review of the 006 flows ends.

**All phases**

- **FR-027**: Each phase MUST record the expected and measured APK size per ABI against the base: the release APK when
  the owner builds it (release builds need the owner's extensions token), and always clean debug builds of the base and
  the phase with a per-file diff of what the phase added.
- **FR-028**: No phase MAY remove or weaken MPV, the FFmpeg or AV1 decoders, or their keep rules, and no new library
  dependency is added (Nunito and the drawables are resources).

### Key Entities

- **Theme**: a named set of colour roles (FR-001); one is chosen per TV. Stored as an enumerated value whose Wholphin
  numbers are reserved.
- **Type style**: one of seven text styles with a size and a Nunito weight.
- **Focus treatment**: border colour and width, ring colour and width, scale (kid screens only).
- **Avatar**: one of six named pictures with a background colour; a field of the kid's plugin profile, cached with it.
- **Drawable asset**: a converted SVG with its source path and any conversion note.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After upgrading, 100% of TVs that had a Wholphin theme open in PekeTV.
- **SC-002**: In all four themes, every contrast-critical pair meets DESIGN's minimum, checked automatically on every
  build.
- **SC-003**: From 3 metres, a tester can name the focused item on the kids home in every theme at first glance
  (border, ring and scale visible on cards and avatars).
- **SC-004**: A child who can't read finds their own profile on the picker by picture on the first try.
- **SC-005**: No kid screen shows Wholphin's purple theme, the Jellyfin logo, or an HTTP status.
- **SC-006**: Each phase's APK grows by no more than its plan's stated estimate plus 10%, and the measurement is
  recorded.
- **SC-007**: Every 004–006 flow behaves as before: their unit tests stay green without changing their assertions about
  behaviour.

## Assumptions

- DESIGN.md is the source of truth for values; the canvas is a reference that can't be opened here.
- The Kids plugin will add the `avatar` feature and an avatar field to the kid profile (Kids API 1.1). The exact field
  name and values are proposed in this feature's contract and must be agreed with the plugin before Phase 3 ships;
  until then Peke behaves as with an older plugin (FR-018).
- Nunito's OFL allows bundling in a GPLv2 app; the font files are shipped unmodified with their license.
- The theme is per TV, not per kid; a per-kid theme is a later feature.
- Phases ship in order 1 → 5; Phase 2 can be reviewed in parallel with Phase 1. Phase 6 waits for the owner.
- Screenshots for review are taken on the emulator without typing the parent code; anything behind the gate that can't
  be reached that way is reported, not bypassed.
