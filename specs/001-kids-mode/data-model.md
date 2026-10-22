# Data Model: Wholphin for Kids (kids mode v1)

## KidProfile (Room, table `kid_profiles`)

| Field | Type | Default | Notes |
|---|---|---|---|
| `userId` | Int (PK) | — | `users.rowId`. FK → `users(rowId)` ON DELETE/UPDATE CASCADE |
| `searchAllowed` | Boolean | false | FR-003, FR-008 (enabling needs the passcode) |
| `autoplayEnabled` | Boolean | false | FR-017 |
| `autoplayCap` | Int | 3 | Valid 1–10. Values outside the range are clamped |
| `pickedItemIds` | List&lt;UUID&gt; (JSON) | [] | Collections/playlists for "Picked by your grown-ups" (FR-003c). Order is preserved |
| `sections` | List&lt;KidsHomeSection&gt; (JSON) | all, default order | Order plus visibility (FR-003c) |

- A row exists if and only if the user is a kid profile. Deleting the row unmarks the profile.
- When the user is removed from the device, the cascade deletes the row.
- `KidsHomeSection` is an enum: `KEEP_WATCHING`, `PICKED`, `LIBRARIES`, `NEW`, `FAVOURITES`. It
  is stored as `[{"section":"KEEP_WATCHING","visible":true}, …]`. Unknown entries are dropped on
  read, and missing ones are appended visible in default order.

Migration: `AutoMigration(from = 40, to = 41)`, new table only.

## Device-level values (key_value `DataStore<Preferences>`)

| Key | Type | Notes |
|---|---|---|
| `kids_passcode_verifier` | String | `pbkdf2-sha256$<iterations>$<saltB64>$<hashB64>`. Absent means not set yet |
| `kids_lock` | Boolean | Set when a kid profile becomes active. Cleared only by "Leave kids mode" (passcode). Absent while any kid profile exists means locked (fail-closed) |
| `kids_timer` | String (JSON) | `{"state":"IDLE|RUNNING|EXPIRED","remainingMs":Long,"totalMs":Long,"lastMinutes":Int}`. Unparseable → `EXPIRED` if the previous raw value mentions RUNNING, else `IDLE` |

## KidsModeState (in memory)

```text
Inactive
Active(profile: KidProfile, user: JellyfinUser)
```

This is derived from `ServerRepository.currentUserFlow` combined with
`KidProfileDao.observe(userRowId)`.

## Session timer state machine

```text
          start(minutes)               tick reaches 0
 IDLE ─────────────────▶ RUNNING ─────────────────────▶ EXPIRED
   ▲                      │  ▲ addTime(m)                 │
   │       stop()         │  └───────────── addTime(m) ───┤
   └──────────────────────┘                               │
   ▲                         end()                        │
   └──────────────────────────────────────────────────────┘
```

- Remaining time decreases only while the activity is resumed, measured with `MonotonicClock`.
- `start(minutes)` takes minutes in 5–120, in steps of 5. It remembers `lastMinutes` (default 30).
- Warning events fire once each when remaining time crosses 5 min and 1 min.
- `addTime` and `end` are only reachable through the parent gate (UI contract).
- On process start, `RUNNING` resumes from the checkpointed `remainingMs` (checkpoints every 15 s
  and on pause), and `EXPIRED` is restored as is.

## Parent gate challenge

`ArithmeticChallenge(a: Int, b: Int)` with `a, b ∈ [2, 9]` and `a * b ≥ 10`. The answer is always
two digits. A new challenge is generated after every wrong answer.

## Validation rules (from FRs)

- Passcode: at least 4 digits, digits only (FR-008). Changing it requires the current passcode.
- Autoplay cap: 1–10 (FR-017).
- Timer minutes: 5–120, step 5 (FR-013).
- Picked items: `BOX_SET` or `PLAYLIST` only (FR-003c).
