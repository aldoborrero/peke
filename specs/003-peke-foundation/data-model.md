# Data Model: Peke foundation

See [research.md](research.md) R3. Kids data moves out of `AppDatabase` into its own Room database.

## KidsDatabase (new, file `peke_kids`, version 1)

Owned by `kids/data/KidsDatabase.kt`, schemas exported to `app/schemas/dev.aldoborrero.peke.kids.data.KidsDatabase/`.
No foreign keys to `AppDatabase` (Room can't span databases); integrity is kept by `KidsDataCleaner`.

### `kid_profiles` (KidProfile)

Same columns as `AppDatabase` v41's `kid_profiles`, plus `serverId`.

| Column | Type | Notes |
|---|---|---|
| `userId` | Int, PK | `JellyfinUser.rowId` in `AppDatabase` (stable while the user row exists) |
| `serverId` | UUID (TEXT), indexed | **new**: the user's server, replaces the JOINs on `users` |
| `searchAllowed`, `autoplayEnabled`, `autoplayCap`, `pickedItemIds`, `sections`, `revision`, `dailyAllowanceMinutes`, `sessionLimitMinutes`, `serverJson` | unchanged | Cache of the plugin's profile (spec 002) |

Rules:
- A row exists if and only if the server last said the user is a kid (unchanged).
- Queries formerly joined with `users` become single-table: `userIdsForServer(serverId)`,
  `observeCountForServer(serverId)`, `deleteForServer(serverId)`, `serverIds()`.
- `mostRecentServerId()` moves to code: kids `serverIds()` ordered by `AppDatabase` `users.lastUsed`.
- Writers set `serverId` from the `JellyfinUser` they already hold.

### `kids_servers` (KidsServer)

Unchanged columns (`serverId` UUID PK, plugin info, features, lock data). Only the foreign key to `servers` is
dropped.

## AppDatabase (changed: 41 → 1)

- Restarts at version 1 (clarified 2026-10-22: full fork, no installs, database restarts at v1): the `KidProfile`
  and `KidsServer` entities move to `KidsDatabase`; the Seerr entities (`SeerrServer`, `SeerrUser`),
  `NavDrawerPinnedItem` and the deprecated `PlaybackLanguageChoice` are removed with their DAOs; Wholphin's
  migrations and old schemas are deleted, and `1.json` is the only exported schema.
- Entities: `servers`, `users`, `ItemPlayback`, `LibraryDisplayInfo`, `playback_effects`, `ItemTrackModification`,
  `RememberedTab`, `SeriesTrackChoice`.
- No install has any version: `dev.aldoborrero.peke` is a new application id that was never released.

## KidsDataCleaner (new)

| Trigger | Effect |
|---|---|
| `ServerRepository.removeUser(user)` | delete `kid_profiles` row for `user.rowId` |
| `ServerRepository.removeServer(server)` | delete `kid_profiles` where `serverId`, and the `kids_servers` row |
| App start (after both DBs open) | delete `kid_profiles` whose `userId` has no `users` row, and `kids_servers` whose `serverId` has no `servers` row |

## DataStore proto (`PekeDataStore.proto`)

- `PlayerBackend.EXTERNAL_PLAYER = 3` and the `external_player = 24` field are removed. Any unknown stored value maps
  to `EXO_PLAYER`.
- Removed with their features, without `reserved` markers (clarified 2026-10-22: full fork, no installs, database
  restarts at v1):
  `cinema_mode`, `play_theme_songs` and `ThemeSongVolume`, `auto_play_next`, `auto_play_next_delay_seconds`,
  `pass_out_protection_ms`, `seek_bar_steps`, `update_url`, `nav_drawer_switch_on_focus`, and the deprecated
  `direct_play_ass`, `direct_play_dolby_vision_e_l`, `combine_continue_next`, `remembered_tabs`,
  `current_server_id` and `current_user_id`. The remaining fields keep their numbers.
- `AppUpgradeHandler` has no Wholphin steps; a fresh install takes all its defaults from `AppPreferencesSerializer`.

## Version (unchanged type, new source)

`Version(major, minor, patch, numCommits?, hash?)` parsed from `git describe --match v*` (`0.1.0`,
`0.1.0-3-gabc123`). `versionCode` = count of `v*` tags, minimum 1.
