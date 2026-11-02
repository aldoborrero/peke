# Client requests after (spec 010)

The session of [requests-before.md](requests-before.md), run again the same way on 2026-10-31 on the
same emulator, server and kid, with the debug build at bcbee673 (the request log
plus the fixes listed in [report.md](report.md)). The Coil image cache was emptied before the run
again. The screens reached at each step match the "before" run.

A first "after" run hit a 5 s network stall at the emulator's first requests after a cold boot
(`/Kids/Info` timed out), which added retries to step 1; it was run again and the clean run is the
one below.

## Before and after, per step

"API" is every request but images; images include `/UserImage` and the trickplay sheet.

| Step | API before | API after | API bytes before | API bytes after | Images before | Images after |
|---|---|---|---|---|---|---|
| 1-cold-start | 30 | 21 | 106 KB | 68 KB | 12 / 456 KB | 11 / 413 KB |
| 2-scroll-row | 0 | 0 | 0 KB | 0 KB | 0 / 0 KB | 1 / 44 KB |
| 3-open-series | 6 | 4 | 18 KB | 9 KB | 3 / 157 KB | 3 / 157 KB |
| 4-open-season | 5 | 4 | 410 KB | 399 KB | 4 / 171 KB | 4 / 171 KB |
| 5-five-episodes | 5 | 0 | 51 KB | 0 KB | 5 / 258 KB | 5 / 258 KB |
| 6-play-30s | 11 | 12 | 531 KB | 236 KB | 1 / 796 KB | 1 / 796 KB |
| 7-back-from-play | 4 | 3 | 391 KB | 10 KB | 0 / 0 KB | 0 / 0 KB |
| 8-back-to-home | 5 | 5 | 34 KB | 34 KB | 0 / 0 KB | 0 / 0 KB |
| 9-search | 3 | 3 | 122 KB | 122 KB | 10 / 409 KB | 10 / 409 KB |
| 10-favorites | 3 | 3 | 0 KB | 0 KB | 0 / 0 KB | 0 / 0 KB |
| 11-live-tv | 3 | 3 | 175 KB | 170 KB | 1 / 62 KB | 1 / 50 KB |
| 12-home-idle-60s | 5 | 4 | 31 KB | 30 KB | 0 / 0 KB | 0 / 0 KB |
| **Totals** | **80** | **62** | **1.82 MB** | **1.05 MB** | **36 / 2.25 MB** | **36 / 2.24 MB** |

What moved, per step:

- **1 Cold start, 30 → 21.** `/UserViews` 5 → 2, `/Kids/Info` 2 → 1, `/Users/Me` 2 → 1,
  `/Kids/Users/{kid}/Status` 2 → 1, `/Items?ids` 2 → 1, and the watching rows (`/UserItems/Resume`,
  `/Shows/NextUp`, `/DisplayPreferences/default`) are no longer read a second time half a second after
  the first load. The favourite rows don't ask for a total count. Continue watching held one more
  series in the "after" run (Bing, watched in the "before" run), hence one more
  `/Shows/{id}/Episodes?adjacentTo` and one more thumb.
- **3 Open a series, 6 → 4.** No second seasons read on the first resume and no read of the cast's
  people.
- **5 Move across 5 episodes, 5 → 0** (51 KB): focusing an episode no longer reads it for cast rows
  kids don't see. The same read on the first focused episode is gone from step 4.
- **6 Play 30 s, 531 KB → 236 KB.** The play queue (`/Shows/{id}/Episodes`, 100 episodes) comes
  without media sources, chapters and trickplay: 529 KB in 2.5 s → 228 KB in 0.1 s. The count went
  11 → 12 only because the 30 s window held five progress reports instead of four.
- **7 Back from the player, 391 KB → 10 KB.** The played episode is read again (10 KB) instead of the
  whole season (400 KB) and its extras.
- **8 Back to the home.** Same count: the refresh of the watching rows no longer reads the libraries,
  and the extra series in Continue watching costs one `adjacentTo` lookup.
- **11 Live TV.** The channel logo is asked at 200 px high: 62 KB → 50 KB.
- **12 Home again, idle 60 s, 5 → 4.** No libraries read.

## Not measured by this session

- **Kid switch** (parent gate): the settings build now runs once per start instead of up to three
  times; seen in step 1, not on a switch.
- **Paused playback**: progress reports while paused go from every 5 s to every 60 s. Checked by hand:
  at the end-of-episode "Next up" card, a build without this change reported every 5 s.
- **Autoplay**: checked by hand with the new queue: at the end of an episode "Play now" reads the next
  episode's details (`GET /Items/{id}`), its trickplay and playback info, and plays it.
- **Search typed fast**: older searches are now cancelled; one letter as in this session doesn't
  show it.

## Raw tables, after

### Step 1-cold-start

API: 21 requests, 69924 B; images: 11 requests, 422437 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Kids/Profiles` | 1 | - | - | 95 | 60 |
| `GET /Kids/Info` | 1 | - | - | 15 | 285 |
| `GET /Kids/Users/{id}/Status` | 1 | - | - | 24 | 205 |
| `GET /Kids/Users/{id}/Profile` | 1 | - | - | 23 | 398 |
| `GET /Kids/Users/{id}/LiveTv/Channels` | 1 | - | - | 41 | 1043 |
| `GET /UserViews` | 2 | - | - | 48 | 7334 |
| `GET /Users/Me` | 1 | - | - | 32 | 2251 |
| `POST /Sessions/Capabilities` | 1 | - | - | 26 | 0 |
| `GET /Items` | 1 | - | - | 18 | 580 |
| `GET /DisplayPreferences/default` | 2 | - | - | 33 | 906 |
| `GET /Items` | 4 | Overview, CanDelete | 25 | 154 | 24100 |
| `POST /LiveTv/Programs` | 1 | - | - | 47 | 927 |
| `GET /UserItems/Resume` | 1 | Overview, SortName, MediaSourceCount, CanDelete | 25 | 70 | 25005 |
| `GET /Shows/NextUp` | 1 | Overview, SortName, MediaSourceCount, CanDelete | 25 | 222 | 4790 |
| `GET /Shows/{id}/Episodes` | 2 | - | 1 | 48 | 2040 |

| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `/UserImage` | maxHeight=320 | 1 | 1 | 20153 |
| `Items/Images/Thumb` | fillHeight=270 | 3 | 2 | 166848 |
| `Items/Images/Primary` | fillHeight=300 | 7 | 7 | 235436 |

### Step 2-scroll-row

API: 0 requests, 0 B; images: 1 requests, 44661 B


| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `Items/Images/Thumb` | fillHeight=270 | 1 | 1 | 44661 |

### Step 3-open-series

API: 4 requests, 9184 B; images: 3 requests, 160472 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Items/{id}` | 1 | - | - | 73 | 5870 |
| `GET /Items` | 1 | PrimaryImageAspectRatio, CanDelete | 20 | 27 | 3264 |
| `GET /Items/{id}/SpecialFeatures` | 1 | - | - | 20 | 2 |
| `GET /Items/{id}/Similar` | 1 | Overview, SortName, MediaSourceCount, CanDelete, PrimaryImageAspectRatio | 25 | 33 | 48 |

| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `Items/Images/Primary` | fillHeight=570 | 1 | 1 | 70275 |
| `Items/Images/Primary` | fillHeight=396 | 2 | 2 | 90197 |

### Step 4-open-season

API: 4 requests, 409041 B; images: 4 requests, 174674 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Items/{id}` | 1 | - | - | 25 | 5870 |
| `GET /Items` | 1 | CanDelete | 20 | 20 | 2786 |
| `GET /Shows/{id}/Episodes` | 1 | MediaSources, MediaSourceCount, Overview, CustomRating, PrimaryImageAspectRatio, CanDelete, ParentId | 100 | 869 | 400383 |
| `GET /Items/{id}/SpecialFeatures` | 1 | - | - | 25 | 2 |

| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `Items/Images/Primary` | fillHeight=219 | 4 | 4 | 174674 |

### Step 5-five-episodes

API: 0 requests, 0 B; images: 5 requests, 263779 B


| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `Items/Images/Primary` | fillHeight=219 | 5 | 5 | 263779 |

### Step 6-play-30s

API: 12 requests, 241749 B; images: 1 requests, 815520 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Items/{id}` | 1 | - | - | 57 | 10501 |
| `POST /Items/{id}/PlaybackInfo` | 1 | - | - | 62 | 2916 |
| `GET /MediaSegments/{id}` | 1 | - | - | 37 | 331 |
| `GET /Items/{id}/Similar` | 1 | Overview, SortName, MediaSourceCount, CanDelete, PrimaryImageAspectRatio | 20 | 99 | 48 |
| `GET /Shows/{id}/Episodes` | 1 | Overview, SortName, MediaSourceCount, CanDelete, PrimaryImageAspectRatio | 100 | 118 | 227748 |
| `POST /Sessions/Playing` | 1 | - | - | 57 | 0 |
| `GET /Kids/Users/{id}/Status` | 1 | - | - | 71 | 205 |
| `POST /Sessions/Playing/Progress` | 5 | - | - | 107 | 0 |

| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `/Videos/Trickplay/320/0.jpg` | none | 1 | 1 | 815520 |

### Step 7-back-from-play

API: 3 requests, 10706 B; images: 0 requests, 0 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `POST /Sessions/Playing/Stopped` | 1 | - | - | 26 | 0 |
| `GET /Kids/Users/{id}/Status` | 1 | - | - | 22 | 205 |
| `GET /Items/{id}` | 1 | - | - | 61 | 10501 |

### Step 8-back-to-home

API: 5 requests, 34504 B; images: 0 requests, 0 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Items` | 1 | PrimaryImageAspectRatio, CanDelete | 20 | 31 | 3264 |
| `GET /DisplayPreferences/default` | 1 | - | - | 16 | 453 |
| `GET /UserItems/Resume` | 1 | Overview, SortName, MediaSourceCount, CanDelete | 25 | 53 | 25005 |
| `GET /Shows/NextUp` | 1 | Overview, SortName, MediaSourceCount, CanDelete | 25 | 178 | 4790 |
| `GET /Shows/{id}/Episodes` | 1 | - | 1 | 21 | 992 |

### Step 9-search

API: 3 requests, 124908 B; images: 10 requests, 418788 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Items` | 3 | Overview, SortName, MediaSourceCount, CanDelete | 50 | 155 | 124908 |

| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `/UserImage` | maxHeight=320 | 1 | 1 | 0 |
| `Items/Images/Primary` | fillHeight=396 | 6 | 6 | 323923 |
| `Items/Images/Primary` | fillHeight=219 | 3 | 3 | 94865 |

### Step 10-favorites

API: 3 requests, 144 B; images: 0 requests, 0 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Items` | 3 | Overview, SortName, MediaSourceCount, CanDelete | 50 | 82 | 144 |

### Step 11-live-tv

API: 3 requests, 174225 B; images: 1 requests, 51696 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /Kids/Users/{id}/Status` | 1 | - | - | 14 | 205 |
| `GET /LiveTv/Channels` | 1 | - | - | 38 | 730 |
| `POST /LiveTv/Programs` | 1 | - | - | 62 | 173290 |

| Image | Size | Count | Distinct URLs | Bytes (sum) |
|---|---|---|---|---|
| `Items/Images/Primary` | maxHeight=200 | 1 | 1 | 51696 |

### Step 12-home-idle-60s

API: 4 requests, 30453 B; images: 0 requests, 0 B

| Endpoint | Count | Fields | Limit | ms (sum) | Bytes (sum) |
|---|---|---|---|---|---|
| `GET /DisplayPreferences/default` | 1 | - | - | 16 | 453 |
| `GET /UserItems/Resume` | 1 | Overview, SortName, MediaSourceCount, CanDelete | 25 | 57 | 25005 |
| `GET /Shows/NextUp` | 1 | Overview, SortName, MediaSourceCount, CanDelete | 25 | 174 | 4790 |
| `GET /Kids/Users/{id}/Status` | 1 | - | - | 20 | 205 |

### Totals

API: 62 requests, 1104838 B; images: 36 requests, 2352027 B
