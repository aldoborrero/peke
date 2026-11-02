# Client requests before (spec 010)

Captured on 2026-10-31 on the `peke_tv` emulator (Android TV, x86_64) against the household server,
debug build of `010-client-requests` at f9c26ca5 (the request log only), kid "Manuel" signed in.
Every request of the Jellyfin SDK, the Kids plugin, Coil and ExoPlayer goes through
`RequestLogInterceptor` (tags `PekeNet` and `PekeImg`); MPV does its own networking and isn't seen.
The Coil image cache was emptied before the run (`run-as … rm -rf cache/coil3_image_cache`) so images
count too; app data and the sign-in were kept.

## Session

Driven with `adb shell input keyevent` (script kept outside the repo), a marker line in the log
between steps:

1. Cold start (`am force-stop`, then launch), wait 20 s for the rows.
2. Scroll the first row 4 to the right and back.
3. Open a series (Bing, from "Kids TV Shows").
4. Open its first season.
5. Move across 5 episodes.
6. Play the 6th episode for 30 s.
7. Back (out of the player, to the season).
8. Back twice, to the home.
9. Open search from the sidebar, type "a", wait.
10. Open Favorites (the kid has none).
11. Open Live TV.
12. Back on the home from the sidebar, idle 60 s.

Steps of the brief that couldn't be run: there is no library grid a kid can reach (the kids sidebar
has Search, Home, Favorites and Live TV only), so Favorites and Live TV stand in for it; and picking
another kid opens the parent gate, whose code isn't typed by agents, so the kid switch isn't measured
(the switch-related candidates were read from code only).

Images include `/UserImage` and trickplay sheets. "ms" is the time to the end of the body.

## Step 1-cold-start

API: 30 requests, 108651 B; images: 12 requests, 467098 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Kids/Profiles` | 1 | - | - | 442 | 60 |
| `GET /Kids/Info` | 2 | - | - | 56 | 570 |
| `GET /Kids/Users/{id}/LiveTv/Channels` | 1 | - | - | 39 | 1040 |
| `GET /Kids/Users/{id}/Status` | 2 | - | - | 67 | 408 |
| `GET /Kids/Users/{id}/Profile` | 1 | - | - | 23 | 398 |
| `GET /Users/Me` | 2 | - | - | 96 | 4502 |
| `GET /UserViews` | 5 | - | - | 190 | 18335 |
| `POST /Sessions/Capabilities` | 1 | - | - | 28 | 0 |
| `GET /DisplayPreferences/default` | 3 | - | - | 53 | 1359 |
| `GET /Items` | 2 | - | - | 42 | 1160 |
| `GET /Items` | 4 | Overview, CanDelete | 25 | 199 | 24083 |
| `POST /LiveTv/Programs` | 1 | - | - | 71 | 922 |
| `GET /UserItems/Resume` | 2 | Overview, SortName, MediaSourceCount, CanDelete | 25 | 110 | 50054 |
| `GET /Shows/NextUp` | 2 | Overview, SortName, MediaSourceCount, CanDelete | 25 | 376 | 4712 |
| `GET /Shows/{id}/Episodes` | 1 | - | 1 | 25 | 1048 |

| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `/UserImage` | none | 1 | 1 | 20153 |
| `Items/Images/Thumb` | fillHeight=270 | 4 | 3 | 211509 |
| `Items/Images/Primary` | fillHeight=300 | 7 | 7 | 235436 |

## Step 2-scroll-row

API: 0 requests, 0 B; images: 0 requests, 0 B


## Step 3-open-series

API: 6 requests, 18480 B; images: 3 requests, 160472 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Items/{id}` | 1 | - | - | 55 | 5853 |
| `GET /Items` | 2 | PrimaryImageAspectRatio, CanDelete | 20 | 65 | 6496 |
| `GET /Items/{id}/SpecialFeatures` | 1 | - | - | 23 | 2 |
| `GET /Items` | 1 | - | - | 27 | 6081 |
| `GET /Items/{id}/Similar` | 1 | Overview, SortName, MediaSourceCount, CanDelete, PrimaryImageAspectRatio | 25 | 37 | 48 |

| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `Items/Images/Primary` | fillHeight=570 | 1 | 1 | 70275 |
| `Items/Images/Primary` | fillHeight=396 | 2 | 2 | 90197 |

## Step 4-open-season

API: 5 requests, 419581 B; images: 4 requests, 174674 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Items/{id}` | 2 | - | - | 97 | 16531 |
| `GET /Items` | 1 | CanDelete | 20 | 28 | 2786 |
| `GET /Shows/{id}/Episodes` | 1 | MediaSources, MediaSourceCount, Overview, CustomRating, PrimaryImageAspectRatio, CanDelete, ParentId | 100 | 896 | 400262 |
| `GET /Items/{id}/SpecialFeatures` | 1 | - | - | 19 | 2 |

| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `Items/Images/Primary` | fillHeight=219 | 4 | 4 | 174674 |

## Step 5-five-episodes

API: 5 requests, 52018 B; images: 5 requests, 263779 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Items/{id}` | 5 | - | - | 366 | 52018 |

| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `Items/Images/Primary` | fillHeight=219 | 5 | 5 | 263779 |

## Step 6-play-30s

API: 11 requests, 543457 B; images: 1 requests, 815520 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Items/{id}` | 1 | - | - | 68 | 10499 |
| `POST /Items/{id}/PlaybackInfo` | 1 | - | - | 35 | 2916 |
| `GET /MediaSegments/{id}` | 1 | - | - | 43 | 331 |
| `GET /Items/{id}/Similar` | 1 | Overview, SortName, MediaSourceCount, CanDelete, PrimaryImageAspectRatio | 20 | 42 | 48 |
| `GET /Shows/{id}/Episodes` | 1 | Overview, Trickplay, SortName, Chapters, MediaSources, MediaSourceCount, CanDelete, PrimaryImageAspectRatio | 100 | 2524 | 529459 |
| `POST /Sessions/Playing` | 1 | - | - | 55 | 0 |
| `GET /Kids/Users/{id}/Status` | 1 | - | - | 29 | 204 |
| `POST /Sessions/Playing/Progress` | 4 | - | - | 108 | 0 |

| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `/Videos/Trickplay/320/0.jpg` | none | 1 | 1 | 815520 |

## Step 7-back-from-play

API: 4 requests, 400471 B; images: 0 requests, 0 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `POST /Sessions/Playing/Stopped` | 1 | - | - | 73 | 0 |
| `GET /Items/{id}/SpecialFeatures` | 1 | - | - | 37 | 2 |
| `GET /Kids/Users/{id}/Status` | 1 | - | - | 53 | 205 |
| `GET /Shows/{id}/Episodes` | 1 | MediaSources, MediaSourceCount, Overview, CustomRating, PrimaryImageAspectRatio, CanDelete, ParentId | 100 | 888 | 400264 |

## Step 8-back-to-home

API: 5 requests, 34753 B; images: 0 requests, 0 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Items` | 1 | PrimaryImageAspectRatio, CanDelete | 20 | 39 | 3248 |
| `GET /UserViews` | 1 | - | - | 31 | 3667 |
| `GET /DisplayPreferences/default` | 1 | - | - | 24 | 453 |
| `GET /UserItems/Resume` | 1 | Overview, SortName, MediaSourceCount, CanDelete | 25 | 55 | 25029 |
| `GET /Shows/NextUp` | 1 | Overview, SortName, MediaSourceCount, CanDelete | 25 | 206 | 2356 |

## Step 9-search

API: 3 requests, 124932 B; images: 10 requests, 418788 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Items` | 3 | Overview, SortName, MediaSourceCount, CanDelete | 50 | 284 | 124932 |

| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `/UserImage` | none | 1 | 1 | 0 |
| `Items/Images/Primary` | fillHeight=396 | 6 | 6 | 323923 |
| `Items/Images/Primary` | fillHeight=219 | 3 | 3 | 94865 |

## Step 10-favorites

API: 3 requests, 144 B; images: 0 requests, 0 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Items` | 3 | Overview, SortName, MediaSourceCount, CanDelete | 50 | 119 | 144 |

## Step 11-live-tv

API: 3 requests, 179091 B; images: 1 requests, 63869 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Kids/Users/{id}/Status` | 1 | - | - | 25 | 205 |
| `GET /LiveTv/Channels` | 1 | - | - | 33 | 730 |
| `POST /LiveTv/Programs` | 1 | - | - | 81 | 178156 |

| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `Items/Images/Primary` | none | 1 | 1 | 63869 |

## Step 12-home-idle-60s

API: 5 requests, 31686 B; images: 0 requests, 0 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /UserViews` | 1 | - | - | 32 | 3667 |
| `GET /DisplayPreferences/default` | 1 | - | - | 22 | 453 |
| `GET /UserItems/Resume` | 1 | Overview, SortName, MediaSourceCount, CanDelete | 25 | 64 | 25005 |
| `GET /Shows/NextUp` | 1 | Overview, SortName, MediaSourceCount, CanDelete | 25 | 211 | 2356 |
| `GET /Kids/Users/{id}/Status` | 1 | - | - | 20 | 205 |

## Totals

API: 80 requests, 1913264 B; images: 36 requests, 2364200 B

## Unnecessary calls

Ordered by how often they fire in a day of use. Code sites are at f9c26ca5.

| # | Call | Seen | Why it isn't needed | Fired at |
|---|---|---|---|---|
| U1 | `GET /Items/{episode}` per focused episode | 6 in step 4–5 (63 KB), again on every focus move | Reads the episode's people for the cast rows, which kids never see (`RestrictedFeature.PEOPLE`) | `ui/detail/series/SeriesOverview.kt:158` → `SeriesViewModel.kt:552` |
| U2 | `GET /UserViews` | 5 at cold start, 1 on every return to the home | The library list is read by every settings rebuild and every home load or refresh, also when the libraries were read a moment ago by the call before | `HomeSettingsService.kt:270` (via `UserSwitchListener.kt:54,73`), `HomeViewModel.kt:231`, `HomeViewModel.kt:156` |
| U3 | Home "watching" rows refreshed right after the first load (`/UserViews`, `/DisplayPreferences/default`, `/UserItems/Resume`, `/Shows/NextUp`) | 4 at cold start, 0.5 s after the first load | `LifecycleStartEffect` calls `refreshWatching` on the home's first start too, not only on a return | `ui/main/HomePage.kt:119-122` → `HomeViewModel.kt:144` |
| U4 | Kid home settings built 3 times on a kid start (`/UserViews` + `/Items?ids` each) | 3 builds, 3 `/UserViews`, 2 `/Items?ids` | The user switch and two `homeChanges` emissions (profile read, live TV channels read) each run `loadCurrentSettings`, unordered and uncancelled | `services/UserSwitchListener.kt:46-56,73`, `kids/KidsProfilePolicy.kt:43-52` |
| U5 | `GET /Shows/{series}/Episodes?startItemId…&limit=100` with `DetailItemFields` (Trickplay, Chapters, MediaSources…) | 1 per episode play: 529 KB, 2.5 s | The play queue asks for 100 episodes with every detail field; the player needs the next few, and their details when they play | `services/PlaylistCreator.kt:62-72` |
| U6 | `GET /Shows/{series}/Episodes?seasonId…&limit=100` (MediaSources, Overview…) reloaded on return | 400 KB on every return from the player | `LaunchedEffect(Unit)` reloads the whole season when the page comes back; only the played episode's user data can have changed | `ui/detail/series/SeriesOverview.kt:92-97` → `SeriesViewModel.kt:354` |
| U7 | `GET /Items/{season}/SpecialFeatures` reloaded on return | 1 per return | Same reload as U6; extras of a season don't change by watching | `SeriesViewModel.kt:378-381` |
| U8 | Series details: seasons `GET /Items?parentId…` twice on open, again on every return | 2 on open, 1 per return | `LifecycleResumeEffect` calls `refresh()` on the first resume too, right after `init` loaded them | `ui/detail/series/SeriesDetails.kt:163-166` → `SeriesViewModel.kt:260` |
| U9 | Series details: `GET /Items?ids=<people>` (6 KB) | 1 per series opened | Loads the people's favourite state for the cast row, hidden for kids | `SeriesViewModel.kt:211-215` |
| U10 | `GET /Kids/Info` twice | 2 at cold start, concurrent | `KidsModeService` and `ParentLockService` both ask the plugin at start, at the same moment | `kids/KidsModeService.kt:199`, `kids/gate/ParentLockService.kt:221` → `KidsPluginService.kt:159` |
| U11 | `GET /Users/Me` twice | 2 at cold start, concurrent | `MainActivity` and `LaunchRule` both update the user | `MainActivity.kt:525`, `kids/setup/LaunchRule.kt:227` → `ServerRepository.kt:349` |
| U12 | `GET /Kids/Users/{kid}/Status` twice | 2 at cold start (and after each playback, see below) | The first composition of the timer effects asks for a refresh while the poll's first fetch is already going; the queued request then fetches again | `kids/ui/KidsTimerEffects.kt:185-187` → `KidsAllowanceService.kt:221` |
| U13 | `GET /Items/{series}` on the season page | 1 per season opened | The season page gets the series again; it was read by the series page a moment before | `SeriesViewModel.kt:116-119` (separate view model per page) |
| U14 | `GET /Items/{episode}` again when it plays | 1 per play | Read on focus (U1) and again by the player | `ui/playback/PlaybackViewModel.kt:340` |
| U15 | `GET /Items?isFavorite…&enableTotalRecordCount=true` home rows | 2 per home load | The total count isn't shown | `HomeSettingsService.kt:1059-1110` |
| U16 | `GET /UserImage` (20 KB, then 304s) | 1 per screen with the avatar | Avatar asked at original size | `ui/nav/NavDrawer.kt:162`, `kids/setup/KidsPicker.kt:201` |
| U17 | `POST /Sessions/Playing/Progress` every 5 s | 6 in 30 s of playback | Fine while playing; while paused it repeats the same position (not exercised in this session) | `util/TrackActivityPlaybackListener.kt:71` |

Not counted as unnecessary:

- The three `/Items?searchTerm` calls of a search are one per result type (series, episodes, …), and
  the three Favorites calls one per tab type; neither repeats.
- The two home favourites and the two "recently added" rows are different rows (different types or
  libraries).
- `GET /Videos/{id}/Trickplay/320/0.jpg` (815 KB) is the seek preview prefetch; the kids seek bar
  shows it. Left for a later spec.
- `POST /LiveTv/Programs` (178 KB) on Live TV is the guide itself.
