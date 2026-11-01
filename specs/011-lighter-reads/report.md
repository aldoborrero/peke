# Spec 011 report: lighter season and playback reads

Follow-up to [spec 010](../010-client-requests/report.md): the reads it left as they were. Measured with
the same scripted kid session as 010 (`PekeNet`/`PekeImg` request log, `peke_tv` emulator, household
server, kid "Manuel", Coil image cache emptied before each run). "Before" is 010's "after"
([requests-after.md](../010-client-requests/requests-after.md), build bcbee673); "after" is the debug
build at 2464c022, run on 2026-11-01, after the regressions found in review were fixed (see
[After the review](#after-the-review)). The owner's rule for this spec: the network is lighter only where
no function is lost; where a saving couldn't keep the behaviour, it was reverted.

## Result

| | 010 after | 011, first pass (a41eb91d) | 011 after review (2464c022) |
|---|---|---|---|
| API requests | 62 | 59 | 58 |
| API bytes | 1.05 MB | 591 KB | 771 KB |
| Image requests (cold image cache) | 36 | 37 | 37 |
| Image bytes | 2.24 MB | 1.59 MB | 1.60 MB |

The review gave back 180 KB of the first pass: the season read again whole on coming back from the
player (+164 KB) and the kids' search asking every image type again (+33 KB).

Per step, 010 after against 011 after review:

| Step | API before | API after | API bytes before | API bytes after | Images before | Images after |
|---|---|---|---|---|---|---|
| 1-cold-start | 21 | 18 | 68 KB | 71 KB | 11 / 413 KB | 12 / 496 KB |
| 2-scroll-row | 0 | 0 | 0 KB | 0 KB | 1 / 44 KB | 2 / 83 KB |
| 3-open-series | 4 | 4 | 9 KB | 9 KB | 3 / 157 KB | 3 / 157 KB |
| 4-open-season | 4 | 3 | 399 KB | 176 KB | 4 / 171 KB | 4 / 171 KB |
| 5-five-episodes | 0 | 0 | 0 KB | 0 KB | 5 / 258 KB | 5 / 258 KB |
| 6-play-30s | 12 | 12 | 236 KB | 39 KB | 1 / 796 KB | 0 / 0 KB |
| 7-back-from-play | 3 | 3 | 10 KB | 174 KB | 0 / 0 KB | 0 / 0 KB |
| 8-back-to-home | 5 | 4 | 34 KB | 40 KB | 0 / 0 KB | 0 / 0 KB |
| 9-search | 3 | 3 | 122 KB | 100 KB | 10 / 409 KB | 10 / 409 KB |
| 10-favorites | 3 | 3 | 0 KB | 0 KB | 0 / 0 KB | 0 / 0 KB |
| 11-live-tv | 3 | 3 | 170 KB | 124 KB | 1 / 50 KB | 1 / 64 KB |
| 12-home-idle-60s | 4 | 5 | 30 KB | 38 KB | 0 / 0 KB | 0 / 0 KB |
| **Totals** | **62** | **58** | **1.05 MB** | **771 KB** | **36 / 2.24 MB** | **37 / 1.60 MB** |

Step 7 costs 164 KB more than in 010: 010 read only the episode that played on coming back, a saving that
lost the state of the episodes autoplay played next (review item 1). The extra images in steps 1 and 2,
and the few KB more in steps 8 and 12, aren't from these changes: the test runs played Bing episodes, so
Continue watching holds other items (one more thumb, and the same thumb URL fetched 4 times by the cold
image cache, see below). Requests at the edge of a step move between steps from run to run (step 1 has
18 here, 19 in the two cold starts checked on their own). Live TV moves with the guide's programmes.

## Per brief step

Each line is one commit on `011-lighter-reads`, oldest first; the last column says what review changed.

| Step | Commit | Change | Before → after | After review |
|---|---|---|---|---|
| 1 | 4bf14c10 | Trickplay sheets fetched when a seek preview first shows, not at playback start. | Playing an episode: 815 KB sheet → 0. | The first previews of a seek right after opening the controls showed black: the sheets are fetched when the controls open (2464c022). Playing without opening the controls still fetches none. |
| 2 | 0c392e91 | The season's episode list comes without `MediaSources`; the focused episode is read again (after 250 ms of focus) only where its sources are shown. | `/Shows/{id}/Episodes` on opening a season: 400 KB in 0.87 s → 177 KB in 0.12 s. | Menus and media information wait for that read; it's shared with the people row (e964a097). |
| 3 | 0246fa99 | In-order episode queues hold 10 episodes and read the next 10 near their end; shuffled queues keep up to 100. | Queue on play: 228 KB → 25 KB. | Windows of only missing episodes are skipped; opening the queue row reads the windows left (d2b64dca). |
| 4a | 8eb2e8c4 | The resolved home settings carry the libraries read to build them. | Cold start `/UserViews`: 2 → 1. | Only their first load uses them; not part of equality (90974a00). |
| 4b | 6d9ecd21 | `ItemHandOver`: the series page hands the series over to the season page it opens. | Opening a season: `GET /Items/{series}` gone. | Emptied on a user switch and when the parent grants end (cbd1dfd7). |
| 4c | 219763b0 | `LatestNextUpWorker` kept scheduled across starts instead of cancelled and run again. | Cold start `/DisplayPreferences/default`: 2 → 1. | Unchanged. |
| 4d | — | Not done, see below. | — | — |
| 5 | a41eb91d | Kids' search asks no extra fields and one primary image. | Search "a": 125 KB → 68 KB. | Every image type again, one of each; combined results ask their fields (617f64af): 102 KB. |

## After the review

An adversarial review of 009..011 and a check against upstream Wholphin's history found functions the
savings had lost. Each is fixed in its own commit; the table says what each test showed.

| # | Commit | Lost function | Fix | Test |
|---|---|---|---|---|
| — | cbd1dfd7 | `ItemHandOver` held an item with its kid's user data until taken: another kid could get it. | Emptied on a user switch (`UserSwitchListener`) and in `ParentLockService.forgetGrants`, which every way out of the grown-up pages goes through. | 3 unit tests (switch empties it, same user keeps it, ending the grants empties it). |
| 1 | 43b08b20 | Coming back to a season read only the focused episode: after autoplay the episodes played next kept their old watched state and progress, and a kid's Play on one started from 0 (upstream #1092 reloads the season on purpose). | The season is read again whole on return, without media sources (177 KB); its extras only when the season changes. | Device: from episode 9, Play now to 10 and 11, Back: 9 and 10 show watched, 11 its progress; Play on 11 resumed. |
| 2 | 3d65ff39 | A failed full read of a queued item played the slim item, found no media sources and skipped it: a network cut skipped the whole queue. | The player shows its error instead. | Unit test. Device: network stalled (emulator latency 120 s; the TV image has no airplane mode for its Ethernet), Next: one `GET /Items/{id}` timed out and the kids "can't find your shows" page showed; nothing more was skipped. |
| 3 | d2b64dca | A window of only virtual episodes added nothing but kept the queue going, so the series ended early; the queue row listed at most ~9 episodes. | `continueEpisodes` reads on while a window adds nothing; opening the queue row reads the windows left, up to 100 items. | 3 unit tests. Device: opening the queue row on episode 15 read 9 windows to the end of the season (225 KB, only then). |
| 4 | e964a097 | The episode menu, its version and track choices and the media information dialog were empty when opened before the focused episode's read (250 ms + GET); grown-up pages read the focused episode twice (people and sources). | One shared read of the focused episode; the menu (long press and More) and the media information wait for it when the listed episode has no media sources. | Compiles; no harness for `SeriesViewModel`. Not driven on the device: these are grown-up pages behind the parent gate. Play wasn't changed: it passes only the id, and the player reads the episode and the saved version itself, as before 011. |
| 5 | ae507df1 | 009..011 read only the watching rows on coming back to the home (upstream #1653 reloads all rows on resume). | After 30 minutes away, or on another day, every row is read again and the dates played are forgotten. | Compiles; not driven (30 minutes). |
| 6 | 90974a00 | The libraries handed over with the home settings served every later load, also after a change of the user's policy or live TV access; and as part of equality, an equal rebuild reset all rows to loading. | They serve only the first load of those settings; equality is user and rows. | 2 unit tests (equality). Device: two cold starts still read `/UserViews` and `/DisplayPreferences/default` once (19 requests). |
| 7 | 1645c1ad | Avatars and channel logos were asked at fixed sizes: the guide logo, drawn at 120 dp, at 200 px (240 needed at 1080p, 480 at 4K); the picker avatar, 129 dp, at 320 px (516 needed at 4K). | Asked at their drawn size on the screen's density. | 2 Robolectric tests (1080p: 258 / 240 px; 4K: 516 / 480 px). |
| 8 | 617f64af | Kids' search with only the primary image type lost the series' and parent's pictures a card falls back to; in combined mode kids see grown-up cards, which lost their fields. | Kids' rows ask one image of each type; combined results ask the fields as before. | Device: search "a", 3 screens of episode results, every card with a picture; 102 KB. |
| 9 | 4ae5d34c | Once a minute while paused also slowed reports while buffering (`isPlaying` is false then); a seek while paused waited for the next minute. | Only a real pause (`playWhenReady` off) slows the reports; a seek while paused is reported at once. | 3 unit tests. |
| 10 | 2464c022 | Fetched at the first preview, the first previews of a seek right after opening the controls were black (issue #432, upstream #445). | The sheets are fetched when the controls open, the one at the playing position first. | Device, Coil cache emptied: before the fix, the first preview at 1m22s was black and the next showed the picture; after it, the first preview showed at once, and the sheet (814 KB) was fetched once. |

## Checks

- `nix develop -c ./gradlew :app:testDebugUnitTest` passes (916 tests); ktlint passes on every changed
  file; each commit compiles on its own (checked in a separate clone).
- The cold start that never loaded the home after a reinstall (first pass, below) was looked for again:
  10 cycles of `adb install -r` and first launch with the full logcat, and the home loaded every time
  (6 rows, 20 requests). Not reproduced, so nothing was changed for it.

## Left as they are, and why

- **4d, the player re-reading the item it was handed** (`GET /Items/{id}`, 10 KB on play). From a
  kid's season page the episode comes without media sources, chapters and trickplay, so the player
  needs the full read anyway.
- **The season list costs 177 KB** for 78 episodes, now also on each return from the player: mostly
  each episode's overview, which the kids page doesn't show but grown-up pages do.
- **Cold image cache fetching one URL several times**: Coil doesn't join concurrent requests for one
  URL; only seen with an empty cache.
- **A cold start that never loaded the home** happened once in the first pass, on the first launch
  right after `adb install -r` (one `/UserViews`, the settings read, the display preferences, the picks,
  then no row; the spinner stayed). Of 31 more cold starts then, one more hadn't loaded 12 s in, again
  the first after a reinstall. In review, 10 reinstall-and-first-launch cycles with the full logcat all
  loaded. The logs show nothing pointing at 010 or 011 (the single settings build, the reads shared in
  flight or the libraries handed over): the shared library read runs in the app's own scope, so a
  cancelled caller can't cancel it for the others. The cause isn't known, and the 009 build wasn't
  needed, as nothing reproduced.

## Notes

- Side effects of the test runs on the server, for the kid: Bing episodes 2, 6, 7, 8, 11, 12, 13 and
  15 have new progress, and 6, 9 and 10 are marked watched. In the first pass a key sequence marked
  episode 6 as a favourite; it was unmarked right after (`DELETE /UserFavoriteItems`).
- The session script and the log summaries are kept outside the repo, as in 010.
