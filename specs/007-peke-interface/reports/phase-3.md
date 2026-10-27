# Phase 3 report: kid screens

Spec 007, User Story 3, tasks T031–T046. Builds on Phase 2.

## Changes

Avatars, following the plugin's real contract (jellyfin-kids `specs/012-kids-avatar/contracts/kids-api-v1-avatar.md`):
- `kids/server/KidsDtos.kt`: `KidsFeatures.AVATAR = "avatar"`; `KidProfileMapper.avatarId(profile)` reads `Avatar` from
  the profile JSON the server last sent (`KidProfile.serverJson`), like the live TV channels. No Room column or
  migration: the profile JSON is already cached and refreshed when its revision changes. Saves already send it back
  (T031, previous commit).
- `kids/data/KidProfileDao.kt`: `observeAll()` (a query; no schema change).
- `kids/data/KidAvatars.kt` (new): each kid's `PekeAvatar` by row id for a server, empty while the cached plugin row
  lacks `avatar`; only the six lowercase ids count.
- `kids/setup/KidAvatar.kt`: `KidPicture` and `kidPicture()` (avatar → Jellyfin picture → initial, the picture
  falling back to the initial when it can't load); `KidAvatar` draws the avatar; `KidAvatarCard` takes the avatar,
  uses `pekeFocus(CircleShape)` without tv-material's scale and focused border, names in Headline.
- `kids/setup/KidsPicker.kt`: the picker's state carries each kid's avatar (from `KidAvatars`, offline); title in
  Title style; wider gaps for the focus ring; Grown-ups with Peke's outline icon.

Sidebar:
- `ui/nav/NavDrawer.kt`: the kid's avatar at the top (`NavDrawerViewModel.avatar`); Search, Home, Favourites, Live TV
  and Grown-ups with Peke's outline icons; the current page in primary, the others in "text, secondary" while the rail
  is closed (`navItemColor`).
- `ui/nav/ProfileGate.kt`: the icon is a drawable resource; `kids/ui/KidsUi.kt` passes `PekeIcon.GROWN_UPS.outline`.

Home, details, player:
- `ui/cards/ItemRow.kt`: `CardGap` (18 dp, DESIGN's 24 px) as the minimum gap and 16 dp above and below a row for the
  focus ring and scale; `ui/main/HomePage.kt`: row spacing and the scroll offset for the taller rows.
- `ui/components/PlayButtons.kt`: Peke's play icon and room for the focus ring.
- `ui/playback/overlay/PlaybackControls.kt`: Peke's play, pause, subtitles and next-episode outline icons.

Timer bar:
- `kids/timer/KidsTimeLimits.kt`: `KidsLimits.firstWarning` (the latest of the plugin's `WarnAtSeconds`) and
  `nearEnd`.
- `kids/ui/TimeLeftBar.kt` (new): the bar in the "time" colour along the top or bottom, with the outline time-left icon
  and "12 min" on a time-coloured pill; from the first warning point the bar grows from 6 to 12 dp and the label to
  Section size, at the same moment as the warning, and stays so; nothing flashes. Top/Bottom/Hidden unchanged.
- `kids/ui/KidsTimerEffects.kt`: uses it; the warning now matches it (time colour, the same icon) and sits below the
  bar when the bar is at the top.
- Strings: `kids_time_bar_minutes` in `values/strings.xml` and `values-es/strings.xml`.

Spec: `tasks.md` (T031–T046, with what changed from the plan: no Room migration, `PekeAvatar` from Phase 2,
`ApiMinorVersion` not read since the contract says to check `Features`), `plan.md`, `data-model.md`,
`contracts/kids-api-avatar.md`.

## Tests

Written before the code they cover:
- `kids/KidAvatarsTest.kt` (3): `Avatar` read from the cached profile and omitted when none; avatars only with the
  `avatar` feature, only the six lowercase ids, only this server's kids; the avatar → picture → initial order.
- `kids/setup/KidsPickerTest.kt`: + each kid shows the avatar picked in the plugin (18 tests).
- `kids/KidsTimeLimitsTest.kt`: + the bar stands out from the first warning point to the end, also counted down while
  playing and for a sitting with its own points; not when unlimited, stopped or without points (30 tests).
- `kids/TimeLeftBarTest.kt` (1): minutes rounded up, never 0.
- `KidsFakes.kt`, `KidsParentGateViewModelTest.kt`, `setup/RefusedSessionsTest.kt`: the fake DAO's `observeAll` and the
  picker's new `KidAvatars` parameter.

## Validation

- `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`: BUILD
  SUCCESSFUL, 819 tests, 0 failures.
- `nix fmt`: 9 files changed on the first run, 0 on the second.
- `nix develop -c pre-commit run --all-files`: all hooks passed.

## Screenshots

In `…/scratchpad/shots/`, PekeTV:
- `p3-home.png`: the kids home with Peke's outline icons in the rail, Home in coral, the focused card's ring.
- `p3-home-row3.png`: a lower row ("Kids TV Shows") focused.
- `p3-series-details.png`: a series page with the coral Play pill, border and ring.

Not reachable on this TV without the parent code or a server change: the picker (one kid opens straight home; Switch
kid needs the code), the avatar (the kid on the emulator has none set, so his Jellyfin picture shows: the fallback),
and the timer bar (that kid's bar doesn't show: unlimited or Hidden). Their logic is covered by the tests above.

## APK size

Clean debug builds, Phase 2 → Phase 3: arm64-v8a +118, armeabi-v7a +122, x86_64 +120 bytes (code and two plurals).

## Commit message

```
Show kids' avatars and the time left on the kid screens

The picker and the sidebar show the avatar a grown-up picked in the
Kids plugin (feature "avatar"), read from the cached profile, then the
Jellyfin picture, then the initial. The sidebar and the player use
Peke's outline icons, rows leave room for the focus ring, and the
time left is a bar in the time colour with its icon that grows from
the first warning on.
```
