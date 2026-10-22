# Data model: Kids mode backed by the Jellyfin Kids server plugin (client)

**Feature**: [spec.md](spec.md) | **Research**: [research.md](research.md)

## KidProfile (Room `kid_profiles`, changed): cache of the server's kid profile

A row exists if and only if the server last said this device user is a kid. The key and foreign key (`userId` →
`users.rowId`, cascade) are unchanged from v1.

| Field | Type | Default | Notes |
|---|---|---|---|
| `userId` | Int | key | Device user row |
| `searchAllowed`, `autoplayEnabled`, `autoplayCap`, `pickedItemIds`, `sections` | as v1 | v1 defaults | From the server `KidProfile` |
| `revision` | Long | 0 | Server `Revision`. 0 only for rows that never came from a server |
| `dailyAllowanceMinutes` | Int? | null | Server `DailyAllowanceMinutes`, 0–1440; null means unlimited |
| `sessionLimitMinutes` | Int? | null | Server `SessionLimitMinutes`, 5–120; null means none. Only edited here: the server counts the sitting |
| `serverJson` | String? | null | Raw server profile object, used as the base of the next `PUT` (R3) |

`LiveTvChannelIds` (plugin 0.0.6.0, `liveTv`) has no column: the dashboard picks the channels, the TV only counts
them (`KidProfileMapper.liveTvChannelIds`, read from `serverJson`) and sends them back unchanged on every `PUT`,
which is a full replacement. A profile without the field (older plugin) sends none, which means no live TV.

**Mapping** (server ↔ app):
- `Sections[].Section` uses `Picked`, `KeepWatching`, `Libraries`, `New` and `Favourites`, which map to the
  `KidsHomeSection` entries. Unknown names from the server are dropped, then `KidsHomeSectionEntry.normalize` runs.
- `PickedItemIds` are UUID strings.

## KidsServer (Room `kids_servers`, new): what the device knows about one server's plugin

| Field | Type | Notes |
|---|---|---|
| `serverId` | UUID | Key; foreign key to `servers.id`, cascade on delete |
| `apiVersion` | Int? | From the last 200 of `/Kids/Info`; null after a 404 |
| `features` | String | Comma-separated features from the last 200; empty after a 404 |
| `checkedAt` | Long? | Wall-clock millis of the last decisive answer (200 or 404) |
| `lockType` | String? | `NUMERIC` or `REMOTE`, the last known household lock type |
| `verifier` | String? | Last household verifier (`pbkdf2-sha256$…`); never sent anywhere |
| `lockChangedAt` | String? | The lock's `ChangedAt` read before `verifier` was fetched; a different one drops the verifier (v43) |
| `offlineFailures` | Int | Wrong codes in a row checked against `verifier`, default 0 (v43) |
| `offlineBlockedAt` | Long? | Monotonic millis when the last offline wait started (v43) |

**Derived state** for a server (`KidsServerStatus`):
- `AVAILABLE`: `apiVersion == 1`, and the features include `profiles`, `parentLock`, `enforcement` and `sitting`
  (plugin 0.0.5.0). A version-1 plugin with `profiles` and `parentLock` but not the rest is **outdated**: it is not
  available, and the gate and kids settings say to update it.
- `GONE`: not available, and at least one cached kid row belongs to a user of this server. This is the fail-closed
  state.
- `ABSENT`: neither. No kids UI is shown.

## ParentGrant (memory only)

`serverId`, `userRowId`, `grant` (string) and `validUntil` (monotonic millis). It is dropped when it expires, on a
403, when the lock changes, and on process death.

## KidsStatus (`KeyValueService`, key `kids_status_<userRowId>`)

The server's `GET /Kids/Users/{id}/Status`, the only source of time in kids mode (clarification 2026-10-19). The
last one is cached per kid, so a denial survives restarts and holds offline.

| Field | Type | Notes |
|---|---|---|
| `date` | String | Server `Date` |
| `usedSeconds`, `bonusSeconds` | Long | |
| `allowanceSeconds`, `remainingSeconds` | Long? | Absent for unlimited kids |
| `timeUp` | Boolean | |
| `warnAtSeconds` | List<Long> | Default `[300, 60]` |
| `nextDayStartsAt`, `serverTime` | String? | ISO instants |
| `enforced` | Boolean | The household turned server enforcement on (feature 5) |
| `deniedReason` | String? | `timeUp`, `outsideSchedule` or `sessionLimit`, only while enforced and denied |
| `deniedUntil` | String? | When the denial ends if nothing changes; absent for `outsideSchedule` |
| `sittingLimitSeconds`, `sittingUsedSeconds`, `sittingRemainingSeconds` | Long? | The sitting, counted by the server across the kid's devices; absent without a sitting limit |
| `breakRemainingSeconds` | Long? | During the household break after the sitting limit |
| `breakSeconds` | Long? | The household break length |

**Denial** (`KidsStatus.denial`, `KidsDenial`):

| Source | Denial | Screen |
|---|---|---|
| `deniedReason` `timeUp`, or `timeUp` without a reason | `TIME_UP` | Time's up |
| `deniedReason` `outsideSchedule` | `BEDTIME` | Bedtime |
| `deniedReason` `sessionLimit`, or `breakRemainingSeconds` > 0 without a reason | `SITTING_OVER` | Time for a break, with the break left |
| `deniedReason` `sessionLimit` with `breakRemainingSeconds` 0 | none | The server reports the break over |
| any other `deniedReason` | `TIME_UP` | Fail closed |

## Refusal (memory only)

The `KidsDenial` from a refused playback: `PlaybackInfo` `ErrorCode` `NotAllowed` or a stream 403 with
`X-Application-Error-Code: PlaybackNotAllowed`, mapped from `X-Kids-Reason`. A refusal without a reason (Jellyfin's
own 403 outside allowed hours) asks for the status at once instead. It is cleared by the first status requested
after it, and on a kid switch.

## Kids live TV channels (memory only, `KidsLiveTvService`)

The current kid's effective channels from `GET /Kids/Users/{id}/LiveTv/Channels`, read with the kid's token when
the kid becomes current or their profile revision changes, and only when the plugin has `liveTv`.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Channel `Id` |
| `name`, `number` | String? | `Name`, `ChannelNumber` |

Items whose `Type` is not `TvChannel`, or without an id, are left out. Answers: 200 → the list (empty means no live
TV); not a kid, 404 or 403 → none; other errors → what was known for this kid.

**Shown** (`KidsLiveTv.shown`): the plugin has `liveTv` and the list is not empty. Then the kids drawer has "Live TV"
(`Destination.KidsLiveTv`, Wholphin's guide limited to these ids) and the home has "Watch live"
(`HomeRowConfig.TvPrograms`) after keep watching.

**Live TV refusals** (`KidsPlaybackRefusal.kindOf`, from `X-Kids-Reason`):

| Reason | Kind | What the kid sees |
|---|---|---|
| `liveTvChannel` | `LIVE_TV_CHANNEL` | "This channel isn't available", back to where they were |
| `liveTvRecording` | `LIVE_TV_RECORDING` | "This isn't available", back to where they were |
| anything else, or none | `TIME` | The time refusal (Refusal above) |

## KidsLimits (derived, not stored)

Built from the last `KidsStatus` (with the time it was received), the refusal, and the playing time on this TV
since then:
- **allowanceRemaining** and **sittingRemaining**: the server's values minus the playing time since the status, never
  below 0;
- **remaining** and **total**: whichever of the two ends first, for the bar; none while denied;
- **denial**: the refusal, else the status's denial. The countdown never sets it;
- **breakRemaining**: on a `SITTING_OVER` denial, the server's break left minus the time since the status.

The gate's "add time" only sends a server bonus for today's allowance. The sitting, its break and bedtime have no
override in the app.

**Polling** (`KidsAllowanceService.interval`): every 60 s; every 10 s while denied or with 2 minutes or less left of
either limit; on a break, 1 s after the break should end (at most 10 s).

## State transitions

```text
Server status:   ABSENT ──Info 200 (v1, profiles+parentLock+enforcement+sitting)──▶ AVAILABLE ──Info 404 / v≠1 / feature missing──▶ GONE (if cached kids) or ABSENT
                 GONE ──Info 200──▶ AVAILABLE        GONE ──"turn off kids mode here" (gated)──▶ ABSENT (rows deleted)
                 (an Info 200 with profiles+parentLock but not the rest is also "outdated": update the plugin)
Kid row:         none ──GET Profile 200 / PUT 200──▶ cached ──GET 404 / DELETE 204──▶ none   (errors: unchanged)
Grant:           none ──Verify valid──▶ held ──expiry / 403 / lock change──▶ none
Denied:          not ──status denial / refusal──▶ denied (time's up, bedtime or sitting over)
                 denied ──a status asked for after it without a denial (bonus, next day, break over, allowed hours)──▶ not
```
