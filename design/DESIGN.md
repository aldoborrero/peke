# Peke design system

The look and behaviour of Peke for Jellyfin, as decided on the design canvas
(<https://claude.ai/artifact/Qz7qEF5wga9NjR5ihsc1aq>, page "PekeTV palette"). It isn't implemented yet: the app still
uses Wholphin's theme. The plan is a spec-kit feature that adds these themes to `ui/theme/`, bundles Nunito, and
restyles the screens below.

Values are in canvas pixels, drawn at 1280×720. A TV renders 1920×1080 at 960×540 dp, so **1 canvas px = 0.75 dp**
(a 24 px radius is 18 dp, a 28 px title is 21 sp).

## Principles

- **Made for kids on a TV.** Children of 3 to 10, some can't read yet, sit across the room with a D-pad remote.
  Everything is big, rounded and easy to find again: avatars, colours and the mascot carry meaning, not just words.
- **Grown-ups are a short, calm detour.** Their screens use the same palette with plain lists and no mascot.
- **Friendly stops.** Running out of time, a break or bedtime is never a punishment: the mascot explains, and the
  grown-ups' way in stays small in the same corner.
- **Peke's own identity.** Never Jellyfin's logo or its purple-to-blue gradient, so Peke can't pass for an official
  Jellyfin app.

## Themes

Every screen reads colours by role, so a theme is one set of values. **PekeTV is the default**; the others are planned
themes a household or a kid can pick later. PekeTV matches the PekeTV channel logo.

| Role | PekeTV | Mandarina | Caramelo | Papel (daytime) |
|---|---|---|---|---|
| Background | `#1F2747` night | `#12202B` ink | `#1E1B18` espresso | `#FFF6E6` cream |
| Surface (cards, chips) | `#28335B` navy | `#1D3340` deep sea | `#2E2925` cocoa | `#FFFFFF` white |
| Primary (buttons, focus ring) | `#FF927E` coral | `#FF8A3D` tangerine | `#FF6B6B` coral | `#E4572E` tomato |
| On primary | `#1F2747` | `#12202B` | `#1E1B18` | `#FFFFFF` |
| Secondary (live, TV, toggles on) | `#65D9CE` teal | `#2EC4B6` teal | `#5BD6A2` mint | `#3BA55C` leaf |
| Time (time left, stars, code dots) | `#FFDA70` sunny | `#FFD23F` sun | `#FFC93C` honey | `#F6AE2D` marigold |
| Tertiary (extras, posters) | `#B5A0EF` lilac | `#FF6F91` berry | `#8EC5FF` sky | `#7CC6FE` sky |
| Text | `#FFFFFF` | `#FFFFFF` | `#FFF7EC` | `#1E2A33` |
| Text, secondary | `#C8CDE6` | `#B9C7D0` | `#CFC3B5` | `#4F5B64` |
| Outline (mascot, icons) | `#1F2747` | `#12202B` | `#1E1B18` | `#1E2A33` |

Rules:
- **Text on primary uses "on primary"**, not white: white on coral doesn't reach 4.5:1.
- **Body text and secondary text reach 4.5:1** on the background and on surfaces.
- **Colours that must be told apart also differ in lightness.** Caramelo's coral and mint are close, so focus and
  states never rely on hue alone there.
- **Papel is too bright for a dark room;** it's the daytime option, never the default.

## Type

Nunito (Google Fonts, OFL), bundled in the app. Weights 700, 800 and 900 only.

| Style | Size (px) | Weight | Use |
|---|---|---|---|
| Display | 56 | 900 | Stop screens ("That's all for today!"), the wordmark |
| Title | 44–48 | 900 | Screen titles ("Who's watching?", "Watch live") |
| Headline | 32–36 | 900 | Dialog titles, kid names on profiles |
| Section | 24 | 900 | Row titles ("Keep watching") |
| Body | 20–22 | 800 | Card titles, list rows, buttons |
| Supporting | 18–19 | 800 | Hints and secondary lines, in "text, secondary" |
| Small | 15–16 | 800 | Badges ("LIVE"), episode labels; never smaller |

Spanish runs 20 to 30% longer than English, so titles wrap to two lines rather than shrinking.

## Shape and space

- **Corners:** posters and episode cards 20–24 px, home cards 24, list rows 22, dialogs 36, buttons and chips fully
  rounded (pills), avatars round.
- **Screen padding:** 48–64 px on the sides, 40–56 px top and bottom, so nothing sits in the TV's overscan edge.
- **Gaps:** 20–24 px between cards in a row, 26–28 px between rows, 12–16 px inside lists.
- **Targets:** buttons are at least 52 px tall, main actions 72 px.

## Focus

The focused element must be obvious from the sofa:
- a **6 px white border**, then
- an **8 px ring in the primary colour** (coral in PekeTV), and
- a **6–8% scale-up** for cards and avatars.

Never colour alone. In the grown-up screens the scale-up is dropped and only the border and ring remain. Motion is
transform and opacity only, no blur, so cheap TV boxes keep up.

## The PekeTV buddy

A rounded TV with a face: a teal frame, a sunny screen, two ear-like antennas, navy outline, rosy cheeks
(`assets/mascot/`). It's the app icon, the logo and the narrator of the stop screens:

| Mood | Where |
|---|---|
| `peke-tv-buddy` | Logo, starting up, grown-up setup header |
| `-waving` | Time's up for today |
| `-sleeping` (lilac frame, moon) | Outside allowed hours |
| `-puzzled` | Something won't play |
| `-searching` (antennas looking for a signal) | Can't reach the server |
| `-cheering` | Episodes in a row reached |

The wordmark is "peke" in Nunito Black, one colour per letter: sunny, coral, teal, lilac (`assets/logo-peke.svg`).
The banner adds "for Jellyfin" in white underneath; the app icon never carries text.

## Avatars

Six kawaii kids (`assets/avatars/`): Leo, Max and Nico; Lucy, Sara and Mia. They share the buddy's style (big round
head, navy outline, shiny eyes, rosy cheeks) and differ by hair, accessories, skin tone and background colour, so a
child who can't read finds their own. A kid's avatar will be a field of their profile in the Jellyfin Kids plugin,
picked by a grown-up, so every TV shows the same one.

## Icons

Twenty two-tone icons on a 48 grid (`assets/icons/`): filled shapes in the theme colours, a 3.5 px navy outline and a
small white highlight on the rounder ones. Used at 48, 32 and 24 dp, converted to Android vector drawables. In the
collapsed sidebar, icons that aren't selected are drawn as plain outlines in "text, secondary" so the rail stays
quiet.

## Screens

The canvas holds every screen and state the app has today; board names are in brackets.

- **Kid's path:** who's watching, with avatars (1b); home with the sidebar closed and open (2b, 2c); show details (3);
  player with the time warning (4); live TV (5); voice search, results, favourites, a library, seasons (13–17); next
  episode countdown and the episodes-in-a-row end screen (18, 19); channel not available (20).
- **Stops and the parent gate:** time's up, break, outside allowed hours, can't play (6a–6d); parent gate (7).
- **Grown-ups:** parent menu, add time, a kid's settings, grown-up setup, Peke settings (8–12).
- **First setup:** select server, add server, select user, sign in, Quick Connect, administrator sign-in (21–26).
- **States:** starting up, home loading, can't reach the server, empty states, messages and gate errors (S1–S5).
- **Other:** screensaver, update available, microphone permission (27–29).

## Behaviour

Decided while testing the app:
- **Sidebar:** the kids' home keeps Wholphin's nav drawer. It holds the kid's avatar, Search (if allowed), Home,
  Favourites, Live TV (if the kid has channels) and Grown-ups at the bottom. Libraries never show for kids.
- **Back:** on the home, the first Back opens the sidebar and a second Back quits the app, as in Wholphin. The parent
  gate opens only on purpose: Grown-ups in the sidebar or on a stop screen. Inner screens go back step by step.
- **Launch:** a set-up TV always opens on the kids' side, from what the TV stores, without waiting for the server.
  With one kid, their home opens (or their stop screen); with several, the kids picker ("Who's watching?") shows this
  TV's kids only, then Grown-ups, which is never focused first. A TV with no kids says so, with only Grown-ups. There
  is no list of servers or users.
- **The picker and a stopped kid:** picking is free, except that while the last kid is stopped (time's up, a break,
  outside allowed hours, or a stop the TV can't read) picking a sibling asks for the parent code first, so a kid can't
  carry on as a sibling. Picking the stopped kid again just shows their stop screen. A kid whose session no longer
  works shows "Needs a grown-up" and goes through the gate and the administrator.
- **Switching kids:** the avatar in the sidebar asks for the parent code, then opens the picker. Once a kid is
  chosen, the grown-up's code stops counting, so the new kid can't open grown-up pages with it.
- **Kids never type a code:** codes are for grown-ups only. A kid picks a picture; the parent code is asked only when
  a grown-up has to decide.
- **After the parent code:** the parent menu (add time, this kid's settings, kids on this TV, This TV, license, Reset
  this TV), never the user list. Leaving the menu ends the code's grant.
- **Reset this TV:** the only way out of kids mode. After a confirmation it signs every kid out (as far as the server
  can be reached), erases the server, the kids and this TV's setup, and starts setup again at the server step.
- **Opening videos:** items in "Continue watching" play straight away, resuming. Everywhere else a kid gets the
  details page first: a series shows its seasons and episodes, a film shows Play.
- **Details pages for kids:** no More button, delete, playlists, remote trailers (they open another app) or people
  rows (cast & crew, guest stars).
- **Relaunch:** on a set-up TV, opening the app, coming back to it or leaving the screensaver always follows the
  launch rule above, never server selection. A link or search from outside Peke opens as the current kid, with their
  rules, or is dropped; it never switches kid.

## Words

Short, warm, and about what happens next. No blame ("You watched all your time", not "Time limit exceeded"), no
technical errors for kids ("Oops, this one won't play"); details such as HTTP statuses and log sending are for
grown-ups only. Every string ships in English and Spanish.

## Mapping to Compose

| Design | Compose (`androidx.tv.material3`) |
|---|---|
| Theme roles | one `ColorScheme` per theme: background, surface, primary, onPrimary, secondary, tertiary, onBackground, onSurfaceVariant (text, secondary), plus "time" as an extra theme colour |
| Type styles | `Typography`: displayLarge (display), headlineLarge (title), headlineMedium (headline), titleLarge (section), bodyLarge (body), bodyMedium (supporting), labelMedium (small) |
| Corners | `Shapes`: small 16 px, medium 22–24 px, large 36 px; pills via `CircleShape` |
| Focus | the components' focus border and scale (`Border`, `ClickableSurfaceScale`) set once in shared defaults |
| Themes | Wholphin's `ThemeColors`/`AppThemeColors` gain PekeTV, Mandarina, Caramelo and Papel; the Wholphin ones go |
