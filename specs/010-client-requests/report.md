# Spec 010 report: client requests

What the kids app asks the server for, which of those calls it didn't need, and what changed.
Measurements are in [requests-before.md](requests-before.md) and
[requests-after.md](requests-after.md).

## Result

Same scripted kid session on the emulator, before and after:

| | Before | After |
|---|---|---|
| API requests | 80 | 62 |
| API bytes | 1.82 MB | 1.05 MB |
| Image requests (cold image cache) | 36 | 36 |
| Image bytes | 2.25 MB | 2.24 MB |

Biggest single wins: the play queue (529 KB / 2.5 s → 228 KB / 0.1 s per episode played), coming
back from the player to a season (400 KB → 10 KB), one request per focused episode gone, and the
cold start down from 30 to 21 API requests.

## What changed

Each line is one commit on `010-client-requests`, oldest first.

| Commit | Change | Calls (before-report ids) |
|---|---|---|
| f9c26ca5 | Debug-only request log: `RequestLogInterceptor` on the shared OkHttp client (Jellyfin SDK, Kids plugin, Coil, ExoPlayer), tag `PekeNet`, images `PekeImg`; method, path with ids cut to 6 characters, query names (values only for `fields`, `limit`, `startIndex`, `enableTotalRecordCount`, `imageTypeLimit` and image sizes), status, ms, bytes, top page. No headers, tokens, `api_key` or bodies. | — |
| 34e43c71 | The "before" report | — |
| ce7e7a8a | The season page reads the focused episode for its people only where people are shown | U1, U14 |
| 78b842e8 | The home refreshes its watching rows only after it was left, and reuses the libraries of the last load | U2, U3 |
| ce4349e0 | One collector builds the home settings for the latest user switch or profile/channel change and drops a stale build; concurrent library reads share one request, dropped when done | U2, U4 |
| 4112728d | The play queue asks for what it shows (`PlaylistItemFields`); the player reads an episode's details when it reaches it | U5 |
| dc3309fb | Coming back to a season re-reads the played episode only | U6, U7 |
| 68dec1f0 | A series page re-reads its seasons only when coming back to it | U8 |
| 9104d32e | A series page reads people only where the cast row shows | U9 |
| a16b9ba2 | Fix for ce4349e0: the in-flight map is declared before the `init` whose collector uses it | — |
| f6c9e206 | Concurrent Kids plugin checks of one server share one `GET /Kids/Info` | U10 |
| 86b80305 | A user update arriving during another waits for it | U11 |
| d61c5d56 | The status poll answers refreshes asked for before its fetch | U12 |
| fff69b2f | `DatePlayedService` drops a failed lookup instead of keeping it for 2 h | brief |
| 322757d4 | Favourite home rows: no total count, one image per type; continue watching: one image per type | U15 |
| 665d339e | Search cancels an older query's searches and writes each answer to its own query (also a correctness fix: an older answer could land in a newer query's results) | brief |
| abede26f | Bug: subtitle choice was read with `SeriesTrackChoiceType.AUDIO` | brief |
| 1eb18674 | Bug: Favorites' jump to a letter ignored the letter (`nameLessThan` missing) | brief |
| d1f25164 | Progress while paused every 60 s instead of 5 s; pause and resume still report at once | U17 |
| 9979833b | Avatars asked at most 320 px high, channel logos at most 200 px | U16 |
| bcbee673 | The timer effects don't ask for a status refresh on their first start (the poll fetches anyway) | U12 |

No data cache was added. The two shared reads (`getAllUserLibraries`, the plugin check) and the user
update keep a request only while it is in flight and forget it when it ends, failure included.
The existing caches were left as they were, apart from `DatePlayedService` no longer keeping a
failure.

Checks: `nix develop -c ./gradlew :app:testDebugUnitTest` passes (900 tests), ktlint passes on
every changed file, and the debug build was driven on the `peke_tv` emulator for the whole session
plus autoplay into the next episode.

## Left as they are, and why

- **The season's episode list (400 KB, step 4).** It asks for `MediaSources` and `Overview` for up
  to 100 episodes; the chosen-tracks lookup and the play button use the media sources. Slimming it
  means reading them on play, as the queue now does. Next candidate.
- **The play queue still holds up to 100 episodes (228 KB).** Fields were cut; the window wasn't,
  since what happens at the end of a shorter queue needs its own look (kids' autoplay and the sitting
  limit decide when playback stops, not the queue).
- **Trickplay sheet prefetch (815 KB per episode played).** The kids seek bar shows the previews.
  Prefetching on the first seek instead would save it for kids who never seek.
- **The player reads the item it was handed** (`GET /Items/{id}` on play, 10 KB). The destination
  carries only the id; passing the item needs a non-serialisable hand-over. Small.
- **The season page reads the series again** (`GET /Items/{series}`, 6 KB) though the series page
  just did: separate view models per page.
- **Continue watching's `/Shows/{id}/Episodes?adjacentTo` per series.** `DatePlayedService` keeps
  them for 2 h as before (pre-existing cache, not extended).
- **`/DisplayPreferences/default` twice at cold start.** One is the removed-from-next-up list of the
  combined row; the other wasn't traced in time.
- **Two `/UserViews` at cold start.** The settings build and the home load run one after the other,
  so in-flight sharing doesn't join them; passing the libraries along with the settings would.
- **Kid switch, KidsPicker's 3 s status wait, avatars 404 for kids without a photo, `jellyfin.createApi`
  per emission in the picker and setup.** Behind the parent gate, so not driven here; left for a
  session where a grown-up enters the code.
- **Images without a size elsewhere** (player overlays, screensaver, person cards): not on a kids
  path in this session. Images in the session were already sized except the two fixed above.
- **Search results with `Overview` (125 KB for "a").** Each type asks for 50 results with
  `SlimItemFields`; whether the kids result cards need the overview wasn't checked.
- **`CollectionFolderView` slim fields:** no library grid is reachable from a kid profile.
- **No new unit tests** for the in-flight sharing and the "refresh only after leaving" flags: the
  classes involved (`HomeViewModel`, `SeriesViewModel`, `NavDrawerService`) have no test harness yet
  and building one is bigger than the changes. Existing tests for the plugin service, parent lock,
  kids mode, launch rule, server repository, playback and time limits pass.
- **MPV** does its own networking and isn't in the log.

## Notes

- Agents can't commit unsigned in this setup (the request was refused), so every commit is signed.
- The emulator lost its network once during the work (UnknownHostException on every request) and was
  cold-booted; the app data and the kid's sign-in survived.
- An "Allow USB debugging?" prompt for an unknown key appeared on the emulator during one run; the
  run's key presses dismissed it, nothing was allowed, and that run was discarded.
