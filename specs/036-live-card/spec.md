# Feature Specification: The live card says what's on, the Live page shows now and next

**Feature Branch**: `036-live-card`

**Created**: 2026-11-28

**Status**: Implemented

**Input**: Option B of task 031 ("el programa primero"), approved by the owner on 2026-11-28 with every recommended
detail, on the canvas "Peke TV UX", page "Pendiente · noviembre", row "031 · TV en directo · B revisada":
`P031LiveB2`, `P031LiveB2Idle`, `P031LiveB2Two`, `P031LiveB2NoPic`, `P031LiveB2NoGuide`, `P031LiveB2Long` and
`P031LiveB2Page`. This supersedes the card part of 014's and 031's proposals. Builds on spec 034 (the row's timer).

## Owner's decisions

- **O1. No "live" mark.** "LIVE"/"EN DIRECTO" goes; the row's title, the progress bar and the time left say it.
- **O2. Picture order.** The episode's still (Thumb) → the series' poster (Primary) → the channel's logo on the channel's
  tile → Peke's TV.
- **O3. No logo, no pill.** A channel without a logo has no pill on the card; its name is in line 2.
- **O4. Time texts.** "quedan 12 min", "queda 1 min", "acaba ya" in the last minute, "quedan 1 h 20 min" from an hour;
  "12 min left", "1 min left", "ending now", "1 h 20 min left" in English.
- **O5. Long names.** On the Live page a long series name wraps to two lines; on the home card it ends with "…".

## Defaults taken (standing rule for kids-mode work)

- **D1. Whole minutes, rounded down.** 12 min 30 s left reads "12 min"; under a minute (and past the end, until the
  timer brings the next programme) reads "ending now". On the hour: "queda 1 h" / "quedan 2 h".
- **D2. Once a minute.** The card's text and bar come from the TV's clock through a value that only changes once a
  minute, so neither the card nor the Live page recomposes on the clock's 2 s tick.
- **D3. The home row keeps 034's data and timer.** Programmes come from `POST /LiveTv/Programs` (`isAiring`), now asking
  for Thumb with Primary; the timer swaps the card at a programme's end.
- **D4. Every channel has a card.** The kids home adds a card for each of the kid's channels (from the plugin's
  `LiveTv/Channels`, already read per profile revision, now with the logo tag) that has nothing airing: the no-guide
  card. So a gap in the guide (034's D6) shows the channel's card instead of hiding the row; clicking it plays the
  channel.
- **D5. The Live page reads the plugin.** The page reads the kid's channels from the same service and the guide with
  one `GET /Kids/Users/{id}/LiveTv/Programs` for three hours from now, instead of `LiveTvViewModel`'s Jellyfin
  channels and 48 hours of `POST /LiveTv/Programs`. It reads again only when, on some channel, the last programme it
  knows starts (no "later" from then), or at its end if it already airs; after a failed read, a minute later; never
  sooner than 15 s. While the page is left it doesn't read.
- **D6. Titles follow the app's cards.** The card's series line is the app's card title (text colour and 900 when
  focused, "text, secondary" and 800 otherwise), as keep watching beside it; the boards draw it white always.
- **D7. Poster on dark.** The poster sits in the middle of a dark card: "surface" on the home, the page colour on the
  Live page's surface rows, so the card, its pill and its bar stand out (seen on the emulator); a logo or a picture that can't
  load gives way to the next in O2's order.

## Requirements

- **FR-001**: The home's live card MUST be 320×180 with the programme's picture in O2's order, the logo pill (O3) and
  the aired bar, and under it the series (one line, "…") and "channel · time left".
- **FR-002**: The time left MUST follow O4 and D1, and MUST change once a minute (D2).
- **FR-003**: At a programme's end the card MUST show the next programme (034's timer, D3).
- **FR-004**: A channel with nothing airing MUST show as a card with its logo (or Peke's TV) and its name only (D4).
- **FR-005**: TalkBack MUST read "PekeTV: Hey Duggee, quedan 12 minutos" (channel, series, time left in words).
- **FR-006**: The Live page MUST show one row per channel with now (still, series on up to two lines, episode,
  channel · time left, bar) and "Luego · HH:MM" with its picture and series (`P031LiveB2Page`).
- **FR-007**: The Live page MUST ask only for three hours of guide from the plugin and MUST NOT recompose on the clock's
  ticks (D2, D5).
- **FR-008**: Strings MUST exist in English and Spanish; no "LIVE" badge remains.

## Requests

| | Before | After |
|---|---|---|
| Home start | `Kids…/LiveTv/Channels` (cached) + `POST /LiveTv/Programs` (airing) | the same; the programmes also carry Thumb |
| Home, each minute | nothing | nothing (local) |
| Open the Live page | `GET /LiveTv/Channels` + `POST /LiveTv/Programs` for 48 h (60 KB for one channel, 152 KB in 014) | `GET /Kids/Users/{id}/LiveTv/Programs` for 3 h; channels from the cached `Kids…/LiveTv/Channels` |
| Live page, each 2 s | recomposition of every row | nothing; once a minute the time left |
| Live page, later | nothing (the 48 h covered it) | one read when a channel runs out of "later" |

## Success Criteria

- **SC-001**: Unit tests cover the picture order and the pill, the time left at 61/60/59 min, 1 min and the last
  minute, the status moving once a minute, now/next from the guide, a channel without a guide, the home row with
  every channel, the plugin's programme mapping, the request window (one request, three hours, no Jellyfin guide), the
  read-again timing, the image URLs, and the ES/EN texts and TalkBack line.
- **SC-002**: `./gradlew :app:testDebugUnitTest :app:assembleDebug` and pre-commit pass.
