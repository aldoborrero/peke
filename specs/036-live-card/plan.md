# Implementation Plan: The live card says what's on, the Live page shows now and next

**Spec**: [spec.md](spec.md)

## Approach

- `kids/livetv/KidsLiveNow.kt` (new, pure): `LiveProgramme`, `LiveCard`, `LivePicture` and `livePictures` (O2),
  `showsLogoPill` (O3), `TimeLeft`/`timeLeft` and `LiveStatus`/`liveStatus` (O4, D1, D2), `nowNext`, `guideRefreshAt`,
  `LiveGuideWindow` (3 h), `liveProgramme` (plugin item → programme, UTC → TV clock), `liveCard` (home row item →
  card), `withKidsChannels`/`withKidsLiveChannels` (D4).
- `kids/livetv/KidsLiveCard.kt` (new): `rememberLiveStatus` (a `derivedStateOf` over `LocalClock`), the time and
  TalkBack texts (also on `Resources`, for tests), `LivePictureBox` (fallback through the pictures on load errors, the
  pill), `LiveProgress`, `LiveChannelLine`, `KidsLiveCard`.
- `kids/livetv/KidsLiveViewModel.kt` (new): `KidsLiveChannelsViewModel` (the kid's channels for the home) and
  `KidsLiveViewModel` (the page: channels + guide, read on start, again at `guideRefreshAt`, stopped when left).
- `kids/livetv/KidsLiveGuide.kt`: the page per `P031LiveB2Page`, on `KidsLiveViewModel` instead of `LiveTvViewModel`.
- `kids/livetv/KidsLiveTvService.kt`: `KidsChannel.imageTag`, `channels` flow, `guide(now)`.
- `kids/server/KidsApi.kt`, `KidsDtos.kt`: `liveTvPrograms(start, end)`; `EpisodeTitle` and `IndexNumber`.
- `services/LiveRow.kt`: the row asks for Thumb too.
- `kids/home/KidsHomeUi.kt`, `KidsHomeLayout.kt`: the CHANNEL card is `KidsLiveCard` at 320×180; the old
  `KidsChannelCard`, `channelPicture` and `channelImageUrl` go.
- `ui/main/HomePage.kt`: in kids mode the Watch live rows get the kid's channels (`withKidsLiveChannels`).
- `kids/ui/KidsCards.kt`: `KidsUnderCard` and `kidsTitleGap` shared with the live card; `KidsLiveBadge` and
  `KidsChannelPicture` go.
- Strings ES/EN; `kids_live_badge` and `kids_next_programme` go.
- `design/DESIGN.md`: the live card and page.

No new dependency. The grown-up guide (`LiveTvViewModel`, `TvGuideGrid`) is unchanged.
