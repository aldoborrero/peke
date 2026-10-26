# Data model: Security follow-ups

No Room schema changes: `peke` and `peke_kids` keep their versions and exported schemas.

## Kids voice search (app preferences)

- **Where:** `app/src/main/proto/PekeDataStore.proto`, `message SearchPreferences`, new field
  `bool kids_voice_search = 3;`.
- **Default:** false (proto3 default). `AppPreferencesSerializer` doesn't set it, so new installs, test TVs from 004
  and an unreadable value all read off.
- **Written by:** This TV's Voice search setting and setup's Voice step, only after the grown-up confirmed the
  explanation and Android reported the permission granted. Turning it off writes false.
- **Read by:** `SearchPage` and `SearchForDialog` through `voiceSearchShown(enabled, granted, available)`.
- **Rule:** the microphone shows only when `kids_voice_search && show_voice_search_button && RECORD_AUDIO granted &&
  speech service available`. The kid's `searchAllowed` (plugin profile) already decides whether Search exists.

## Server lookup result

- `ServerLookup.Found(server, insecure: Boolean)`: `insecure` is true when the chosen address is `http://`.
- **Choice rule** (R1): among answering, set-up Jellyfin candidates, any `https://` one wins over every `http://` one;
  inside a scheme, the SDK's best score wins.

## Setup screen states (in memory)

- `SetupScreen.Server` gains `httpWarning: JellyfinServer?`: the server waiting for the grown-up to confirm the HTTP
  warning. Confirm → plugin check with that server; Back → `null`.
- `SetupScreen.Voice(server)`: shown after `finish()` on a first setup when voice search is off and a signed-in kid
  has `searchAllowed`. Leaves to the launcher either way.
- Neither state is stored; a warm start mid-setup restarts from the stored `TvSetupStep` as in 004.

## Kids list refresh (behaviour, no new data)

| Server's `/Kids/Profiles` answer for a row on this TV | Before | After (FR-016) |
|---|---|---|
| Listed, same revision | kept | kept |
| Listed, new revision | profile refreshed | profile refreshed |
| Missing (list empty or partial) | profile deleted, signed out, row removed | `refresh(server, user)`: removed only on a definite plugin `NotAKid` with the plugin confirmed available; kept otherwise |
| List call fails | nothing changes | nothing changes |

## Media session commands (constant)

`KidsSessionCallback.PLAYER_COMMANDS`, granted to accepted controllers: `PLAY_PAUSE`, `PREPARE`, `STOP`,
`SEEK_TO_DEFAULT_POSITION`, `SEEK_IN_CURRENT_MEDIA_ITEM`, `SEEK_BACK`, `SEEK_FORWARD`, `SEEK_TO_PREVIOUS`,
`SEEK_TO_PREVIOUS_MEDIA_ITEM`, `SEEK_TO_NEXT`, `SEEK_TO_NEXT_MEDIA_ITEM`, `GET_CURRENT_MEDIA_ITEM`, `GET_TIMELINE`,
`GET_METADATA`, `GET_AUDIO_ATTRIBUTES`, `GET_VOLUME`, `GET_DEVICE_VOLUME`, `GET_TRACKS`. Everything else is withheld.
