# Data model: Grown-ups screens

No Room or proto change. Paths are relative to `app/src/main/java/dev/aldoborrero/peke/`.

## Add-time amount (per TV)

| Field | Store | Values | Default |
|---|---|---|---|
| `kids_add_time_minutes` | `KeyValueService` (DataStore, this TV only) | one of `ADD_TIME_STEPS` | 15 when missing or not a step |

`ADD_TIME_STEPS = [5, 10, 15, 20, 30, 45, 60, 90, 120]` minutes. Written only after the server added the time.

## Bonus (plugin)

| Field | From | Notes |
|---|---|---|
| status | `POST …/WatchTime/Bonus` answer | as today (`KidsStatus`) |
| `BonusId` | same answer, optional | present with `bonusUndo` (plugin 0.0.9.0); Undo needs it |

`BonusResult` becomes `BonusOutcome(result, bonusId)`, where `result` is `ADDED`, `NEEDS_GRANT`, `UNREACHABLE` or
`FAILED`.

## Gate stages (in memory)

```text
Passcode ──right──▶ Menu ──Add time──▶ AddTime(index) ──OK──▶ AddTime(sending) ──ok──▶ Added(minutes, until, bonusId)
   │  └─empty OK─▶ AdminSignIn(recovery, fromEmpty)          │      └─fail─▶ AddTime(error) ─Retry─▶ sending
   │                     └─Back─▶ Passcode                    └─NEEDS_GRANT─▶ Passcode(pending = minutes) ─right─▶ sending
   └─right, no kid─▶ (Settings opens, gate closes)
Added ─10 s / Done / Back─▶ closed (grant ends)       Added ─Undo─▶ Added(undoing) ─ok─▶ closed (time taken back)
Menu ─Keep watching now─▶ (break ended, gate closes)
```

`AdminSignIn` gains `quickConnectCode` (while approving) and `fromEmpty`.

## Add-time availability (derived)

| Value | When (first match) |
|---|---|
| `Offline` | plugin not available, no status, or the last status request had no answer |
| `OnBreak(endsAt, canEnd)` | denial is the sitting's break; `canEnd` with `breakEnd` |
| `Bedtime` | denial is outside allowed hours |
| `Unlimited` | the status has no allowance |
| `Available` | otherwise (time left or time's up) |

## Add-time preview (derived)

| Field | Rule |
|---|---|
| `hadLeft` | allowance left now, null when time's up or under a minute |
| `until` | now + allowance left + amount |
| `bedtimeAt` | end of today's allowed-hours window containing now, when `until` is after it |
| `breakAt` | now + sitting left, when that is before `until` |

## Kid's rules (derived, read only except Daily time)

| Line | Source |
|---|---|
| Daily time (editable) | `KidProfile.dailyAllowanceMinutes` |
| Today | status `UsedSeconds` of `AllowanceSeconds + BonusSeconds` |
| Time in a row, then a break | `KidProfile.sessionLimitMinutes`, status `BreakSeconds` |
| Allowed hours | `UserPolicy.accessSchedules`, today's windows |
| Search, voice search | `KidProfile.searchAllowed`; this TV's voice search setting and microphone permission |
| Autoplay | `KidProfile.autoplayEnabled`, `validAutoplayCap` |
| Live TV | `UserPolicy.enableLiveTvAccess`; `KidsLiveTvService.channelsFor` names |
| Picks | `KidProfile.pickedItemIds` → item names |
| What Jellyfin decides | `UserPolicy.maxParentalRating`, `blockUnratedItems`, `enableAllFolders`/`enabledFolders` |
| Dashboard | `{server url}/web/#/configurationpage?name=Kids` |

Pending save: the value chosen while the grant had run out, saved after the gate passes again.
