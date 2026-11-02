# Contract: boards → screens

Each board (design canvas, `*PekeTV.dc.html`, 1280×720, 1 px = 0.75 dp) and what draws it in the app. Values are the
board's px; research.md has the full measurements.

| Board | Phase | Destination | Composables |
|---|---|---|---|
| HomeRailPekeTV, HomePekeTV | 1 | `Home` | `HomePage` (kids branch) → `KidsHomeHeader`, `KidsHomeCard` |
| HomeDrawerPekeTV | 1 | any page with the sidebar | `NavDrawer`, `NavigationDrawerAndroid` |
| DetailsPekeTV | 2 | `SeriesOverview`; `MediaItem` series and films | `KidsDetailsHeader`, kids episode cards |
| KSeasonsPekeTV | 2 | `SeriesOverview` | `KidsSeasonsDialog` |
| PlayerPekeTV | 3 | `Playback` | `PlaybackController`, `PlaybackControls`, `SeekBar`, `KidsTimerEffects` |
| KNextUpPekeTV | 3 | `Playback` | `NextUpEpisode` (kids panel), in-a-row chip |
| LiveGuidePekeTV | 4 | `KidsLiveTv` | `KidsLiveGuide` |
| KSearchPekeTV | 5 | `Search` | `SearchPage`, `SearchEditTextBox`, `VoiceSearchButton` |
| KVoicePekeTV | 5 | `Search` | `VoiceSearchOverlay` |
| KFavoritesPekeTV | 5 | `Favorites` | `FavoritesPage` |
| KLibraryPekeTV | 5 | folders, box sets, playlists a kid reaches | `CollectionFolderGrid` header and cards |
| OtherScreensaverPekeTV | 6 | screensaver | `AppScreensaverContent` (kid content) |
| OtherUpdatePekeTV | 6 | `UpdateApp` (grown-up) | `InstallUpdatePage` |
| OtherMicPekeTV | 6 | Settings › This TV › Voice search; setup's voice step | `VoiceSearchConsent` |

Rules for every board:
- Colours by role: board `#1F2747` background, `#28335B` surface, `#FF927E` primary, `#65D9CE` secondary, `#FFDA70`
  time, `#B5A0EF` tertiary, `#FFFFFF` text, `#C8CDE6` text secondary; `rgba(31,39,71,a)` is the outline colour at a.
- Focus: 007's border and ring (`pekeFocus`), scale on kid screens only.
- Icons: Peke's outline set; the boards' two-tone icons on dark surfaces become outline icons (DESIGN Icons).
- Text never below 15 px.
