# Contract: Peke identity and intents

Applies from feature 003. Spec FR-001–FR-007; research R4.

## Device identity

| Item | Release | Debug |
|---|---|---|
| Application id | `dev.aldoborrero.peke` | `dev.aldoborrero.peke.debug` |
| Kotlin namespace / packages | `com.github.damontecres.wholphin` | `dev.aldoborrero.peke` (except the external `wholphin-mpv` API) |
| Launcher label (`app_name`) | `Peke` | `Peke (debug)` |
| Long name (`app_name_long`) | `Peke for Jellyfin` | same |
| Launcher icon, TV banner | Wholphin's current art (temporary, constitution VII deviation) | debug banner as today |
| Leanback | `required="false"` (Google TV, Android TV, Fire TV sideload) | same |
| Screensaver (DreamService) label | `app_name` | same |

## Jellyfin identity

| Item | Value |
|---|---|
| Client name (`ClientInfo.name`, auth header `Client=`) | `Peke for Jellyfin` |
| Client version | `BuildConfig.VERSION_NAME` (e.g. `0.1.0`) |
| Device name | the SDK's device name (`Settings.Global.DEVICE_NAME`, else manufacturer + model) |
| Device id | first 32 hex chars of `sha256("<ANDROID_ID>:peke")`; same value in the API client, the auth header and crash reports |
| DisplayPreferences client key | `Peke` (`Peke (Debug)` in debug) |

Guarantee: on one TV, Peke's device id never equals Wholphin's, so a sign-in in one app doesn't revoke the
other's tokens.

## Intents

| Intent | Value | Behaviour |
|---|---|---|
| Launcher | `MAIN` + `LAUNCHER`, `LEANBACK_LAUNCHER` | unchanged |
| Deep link scheme | `peke://` (replaces `wholphin://`) | same paths as Wholphin's `Intents.md` |
| Playback action | `dev.aldoborrero.peke.PLAYBACK` (replaces `com.github.damontecres.wholphin.PLAYBACK`) | same extras |
| `VIEW`, `SEARCH` | with `peke` scheme only | unchanged handling |

While a kid is locked in, every external intent is ignored (unchanged). For a grown-up, intents that target a
destination outside the shell allow-list ([grown-up-shell.md](grown-up-shell.md)) open the shell instead.
Peke registers no `wholphin` scheme and no `com.github.damontecres.wholphin.*` action.
