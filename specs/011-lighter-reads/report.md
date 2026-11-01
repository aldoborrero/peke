# Spec 011 report: lighter season and playback reads

Follow-up to [spec 010](../010-client-requests/report.md): the reads it left as they were. Measured with
the same scripted kid session as 010 (`PekeNet`/`PekeImg` request log, `peke_tv` emulator, household
server, kid "Manuel", Coil image cache emptied before each run). "Before" is 010's "after"
([requests-after.md](../010-client-requests/requests-after.md), build bcbee673); "after" is the debug
build at a41eb91d, run on 2026-11-01.

## Result

| | 010 after | 011 after |
|---|---|---|
| API requests | 62 | 59 |
| API bytes | 1.05 MB | 591 KB |
| Image requests (cold image cache) | 36 | 37 |
| Image bytes | 2.24 MB | 1.59 MB |

Per step:

| Step | API before | API after | API bytes before | API bytes after | Images before | Images after |
|---|---|---|---|---|---|---|
| 1-cold-start | 21 | 19 | 68 KB | 66 KB | 11 / 413 KB | 12 / 496 KB |
| 2-scroll-row | 0 | 0 | 0 KB | 0 KB | 1 / 44 KB | 2 / 83 KB |
| 3-open-series | 4 | 4 | 9 KB | 9 KB | 3 / 157 KB | 3 / 157 KB |
| 4-open-season | 4 | 3 | 399 KB | 176 KB | 4 / 171 KB | 4 / 171 KB |
| 5-five-episodes | 0 | 0 | 0 KB | 0 KB | 5 / 258 KB | 5 / 258 KB |
| 6-play-30s | 12 | 12 | 236 KB | 39 KB | 1 / 796 KB | 0 / 0 KB |
| 7-back-from-play | 3 | 3 | 10 KB | 10 KB | 0 / 0 KB | 0 / 0 KB |
| 8-back-to-home | 5 | 5 | 34 KB | 36 KB | 0 / 0 KB | 0 / 0 KB |
| 9-search | 3 | 3 | 122 KB | 67 KB | 10 / 409 KB | 10 / 409 KB |
| 10-favorites | 3 | 3 | 0 KB | 0 KB | 0 / 0 KB | 0 / 0 KB |
| 11-live-tv | 3 | 3 | 170 KB | 155 KB | 1 / 50 KB | 1 / 50 KB |
| 12-home-idle-60s | 4 | 4 | 30 KB | 32 KB | 0 / 0 KB | 0 / 0 KB |
| **Totals** | **62** | **59** | **1.05 MB** | **591 KB** | **36 / 2.24 MB** | **37 / 1.59 MB** |

The extra images in steps 1 and 2 and the 2 KB more in steps 8 and 12 aren't from these changes: the
test runs played Bing episodes 2, 6, 7, 8 and 13, so Continue watching held other items (one more
thumb, and the same thumb URL fetched 4 times by the cold image cache, see below). Live TV moves with
the guide's programmes.

## Per brief step

Each line is one commit on `011-lighter-reads`, oldest first.

| Step | Commit | Change | Before → after |
|---|---|---|---|
| 1 | 4bf14c10 | Trickplay sheets are fetched when a seek preview first shows, not at playback start. The prefetch skips the sheet the preview itself loads (a first version fetched it twice). | Playing an episode: 815 KB sheet → 0. A kid who seeks fetches the same sheet once, as before. |
| 2 | 0c392e91 | The season's episode list comes without `MediaSources`. The focused episode is read again (`GET /Items/{id}`, after 250 ms of focus) only where its sources are shown: grown-up pages (tracks, versions, item menus) and a kid's episode with more than one version. Play goes by id; the player reads the episode itself, as before. | `/Shows/{id}/Episodes` on opening a season: 400 KB in 0.87 s → 177 KB in 0.12 s; no per-focus read for a kid's single-version episodes. |
| 3 | 0246fa99 | In-order episode queues hold 10 episodes and read the next 10 (`startItemId` = last, limit 11) when the playing item is 3 or fewer from the end. Shuffled queues keep up to 100. New unit test. | Queue on play: 228 KB → 25 KB (24 KB more each 7 episodes played). |
| 4a | 8eb2e8c4 | The resolved home settings carry the libraries read to build them; the home uses them. | Cold start `/UserViews`: 2 → 1. |
| 4b | 6d9ecd21 | `ItemHandOver`: opening a season from the series page hands the series over; the season page takes it once, else reads it. Holds one item, emptied when taken. | Opening a season: `GET /Items/{series}` (6 KB) gone. |
| 4c | 219763b0 | Traced the second `/DisplayPreferences/default`: `LatestNextUpWorker`. The scheduler saw the null user before the session is restored as a sign-out, cancelled the periodic work and enqueued it again, so it ran at once on every start. It now cancels only when a user goes away, and keeps the schedule (`UPDATE`, like the suggestions worker). Scheduler test updated, one added. | Cold start `/DisplayPreferences/default`: 2 → 1. |
| 4d | — | Not done, see below. | — |
| 5 | a41eb91d | Kids' search asks for no extra fields and one primary image (`imageTypeLimit=1`, `enableImageTypes=Primary`); grown-up search keeps `SlimItemFields`. | Search "a": 125 KB → 68 KB, same images. |

## Checks

- `nix develop -c ./gradlew :app:testDebugUnitTest` passes (902 tests); ktlint passes on every changed
  file; each commit built, installed and driven on `peke_tv`.
- Step 1: seek previews show on the kids seek bar (controls open, Up, Right) and the sheet is fetched
  once when they first show.
- Step 2: the season page, "Play episode 6" and playback are as before. Only single-version episodes
  were at hand, so the kid's version choice and the grown-up track display weren't driven (grown-up
  pages are behind the parent gate).
- Step 3: pressing Next six times from episode 6 read the next window at the 7th item; the queue row
  then continued past episode 15. At the end of episode 7 the "Next up" card showed episode 8 and
  "Play now" played it (the kid's profile has autoplay off, so there was no countdown to watch). The
  sitting limit runs on the plugin's status poll, which doesn't read the queue.
- Step 4: series → season and season → back work; 2 cold starts after the change show one
  `/UserViews` and one `/DisplayPreferences/default`.
- Step 5: search results look the same, and a series and an episode open from them.

## Left as they are, and why

- **4d, the player re-reading the item it was handed** (`GET /Items/{id}`, 10 KB on play). From a
  kid's season page the episode now comes without media sources, chapters and trickplay, so the player
  needs the full read anyway; a hand-over would only help where the full item was already read.
- **The season list still costs 177 KB** for 78 episodes: mostly each episode's overview. The kids
  season page shows the series overview, not the episodes', but grown-up pages show the focused
  episode's, and the view model doesn't know which page it serves. A candidate if the kids page gets
  its own fields.
- **Cold image cache fetching one URL several times**: in step 1 the same Continue watching thumb was
  fetched 4 times (3 for 2 URLs in 010). Coil doesn't join concurrent requests for one URL; only seen
  with an empty cache.
- **A cold start that never loaded the home** happened once, on the first launch right after
  `adb install -r` (one `/UserViews`, the settings read, then nothing; the spinner stayed for the
  whole run). In 31 more cold starts, 4 of them right after reinstalling, the home loaded every time
  but once, again the first launch after a reinstall, where it hadn't loaded 12 s in (not watched
  longer). The cause isn't known; the first launch after an install is the lead.

## Notes

- Side effects of the test runs on the server, for the kid: Bing episodes 2, 6, 7, 8 and 13 have new
  progress, and episode 6 is marked watched (played to the end while testing autoplay). A key sequence
  also marked episode 6 as a favourite; it was unmarked right after (`DELETE /UserFavoriteItems`).
- The session script and the log summaries are kept outside the repo, as in 010.
