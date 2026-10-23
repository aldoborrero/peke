# Data model: TV setup and daily flows

## TvSetup (new, kids database `peke_kids` v2)

One row per TV, the single source of truth for kids mode (research R3).

| Field | Type | Notes |
|---|---|---|
| `id` | Int | Always 1 |
| `serverId` | UUID | The one server this TV belongs to; references `servers.id` in the app database |
| `serverUrl` | String | Kept here too, so the TV can tell it's set up even if the app database is lost |
| `step` | enum | `SERVER`, `ADMIN`, `CODE`, `KIDS`, `SIGN_IN`, `DONE`: the first unfinished setup step |
| `lastKidRowId` | Int? | The `users.rowId` of the last kid who became current (FR-012a) |
| `completedAt` | Instant? | Set when `step` becomes `DONE` |

**Migration 1 → 2**: creates the table. Existing installs (test TVs only, Peke is unreleased) with a stored server
and kids get a row with `step = DONE`; without a server, no row. Covered by a migration test (constitution VIII).

## KidOnTv (existing `users` rows, narrowed)

A Wholphin `JellyfinUser` row (`users` table, app database). From now on a row exists only for a kid on this TV.

| Field used | Notes |
|---|---|
| `rowId`, `id` (user id), `serverId`, `name` | As today |
| `accessToken` | The kid's token, made with the kid's own device id (research R2) |
| `requireLogin`, `pin` | No longer set by Peke: kids sign in through setup only |

The kid's device id is derived from the install id and `id`, so it isn't stored. No administrator row is ever
written, and the "Sign in automatically" setting goes.

## Derived state

```text
TvState
├── Loading                    → treated as kids mode (picker or kid) until resolved
├── NotSetUp                   → no server stored anywhere: setup from SERVER
├── InSetup(step)              → setup resumes at step; no kid screens
└── SetUp(server, kids)        → kids mode, always
     ├── kids = []                         → "This TV has no kids" (Grown-ups only)
     ├── one kid                           → that kid's home
     └── several                           → picker (FR-012a for a stopped last kid)
```

Rules:
- `TvSetup` missing but a server stored → `SetUp` (fail closed, FR-018).
- `TvSetup` unreadable → `SetUp` if a server is stored, else `NotSetUp`.

Kid readiness:
- `Ready`: has a token that `Users/Me` accepted last time it was checked, or not yet checked offline;
- `NeedsGrownUp`: the server refused the token (401, revoked, disabled);
- `Stopped`: the cached status says time's up, break or outside allowed hours, by the wall clock.

## AdminSession (in memory only)

| Field | Notes |
|---|---|
| `server` | From `TvSetup` or the setup's server step |
| `userId`, `name` | The administrator |
| `token` | Never written to Room, DataStore, prefs, logs or backups |
| `deviceId` | Random per sign-in |

Lifecycle: opened for setup, adding a kid, recovering the code or setting the TV up again. `close()` calls
`Sessions/Logout` and drops the client when the action finishes, is cancelled, or the grown-up leaves the screen.
The process dying also ends it, since nothing was stored. The server-side token then expires or stays in the
dashboard until removed, and the plan accepts that.

## Removed state

- `kids_lock` (key-value store), `KidsModeService.locked`, `isLockedNow`, `lockEngaged`;
- the `<pkg>_server` most-recent-session prefs (`MostRecentServer`);
- the per-user `requireLogin` and `pin` flows of the user list (`PinEntry`).
