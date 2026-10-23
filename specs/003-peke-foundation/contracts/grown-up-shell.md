# Contract: grown-up shell and Peke settings

Spec FR-010–FR-014, FR-033; research R1, R2.

## Who gets the shell

| Kids state of the signed-in user | UI |
|---|---|
| `Loading` | loading page (unchanged) |
| `Active` (kid profile) | kids UI (unchanged) |
| `Inactive` (any non-kid: administrator, other grown-up, any user on a server without a usable Kids plugin) and the device's kids lock is **off** | grown-up shell |
| `Inactive` and the kids lock is **on** or unknown (e.g. the kid's cached row is missing after the kids database was lost) | the kids user list (kids only), from which the parent gate is the only way out; never the shell (fail closed, constitution IV) |

The lock is turned off only by the parent gate ("Leave") or by a fresh setup with no lock, so a grown-up who passed
the gate reaches the shell, and a kid whose cache is gone does not.

## Routes allowed for a non-kid (`ApplicationContent` guard)

| Destination | Rendered as |
|---|---|
| `Home` | `GrownUpShell` page |
| `KidsSettings` | kids settings (unchanged, with its own grant/admin checks) |
| `Settings(PreferenceScreenOption.PEKE)` and the sub-screens it links (`EXO_PLAYER`, `MPV`, `HDR_FORMATS`, `SCREENSAVER`, `SKIP_SEGMENTS`, `EXPERIMENTAL`) | Wholphin's settings page with the Peke list |
| `SubtitleSettings` | subtitle style page |
| `License` | license page |
| `Debug` | version/debug page (from "Installed version") |
| `UpdateApp` | update install page |
| anything else (browse, details, search, playback, live TV, favorites, `Settings(BASIC/ADVANCED)`, …) | `GrownUpShell` page |

All of these render full screen, without the nav drawer.

## GrownUpShell page

Items, in order (focus starts on the first):
1. Kids settings → `KidsSettings`
2. Settings → `Settings(PEKE)`
3. Switch user → user list (sign in a kid, switch, sign out, add or switch server)
4. License → `License`

When the current server has no usable Kids plugin (missing, unreachable or lacking a required feature), a message
at the top says Peke needs the Jellyfin Kids plugin 0.0.7.0 or later on this server; kids settings then shows its
existing "plugin missing" state. Update notices appear in Settings (the existing "update available" row), not on the
shell itself.

## Peke settings list (`PreferenceScreenOption.PEKE`)

The groups and rows are listed in research R1 ("Keep" column). The following are never shown in Peke:
`SeerrIntegration`, `ExternalPlayerApp` (and the external-player backend choice), `CustomizeHome`,
`UserPinnedNavDrawerItems`, `ManageMedia`, `DisplayToggles`, `ShowLogos`, `BackdropStyle`, `Slideshow*`,
`PlayThemeMusic`, `RememberSelectedTab`, `CinemaMode`, `ShowNextUpTiming`, `AutoPlayNextUp`,
`AutoPlayNextDelay`, `PassOutProtection`, `UpdateUrl`, `ProtectProfile`, `UserProfileSettings`, `KidsMode`.

Kept on purpose (owner, 2026-10-22): `PlaybackDebugInfo` (turns on the overlay during kid playback),
`QuickConnect` (authorize another device), `ExperimentalPreference.Enable` and the Experimental sub-screen.

A unit test asserts the list contains the playback switches (backend, FFmpeg, AV1, max bitrate, max resolution,
refresh-rate and resolution switching), `PlaybackDebugInfo`, `QuickConnect` and the Experimental entry and none of the excluded rows.
