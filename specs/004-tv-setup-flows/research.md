# Research: TV setup and daily flows

Phase 0 of [plan.md](plan.md). Server facts are from Jellyfin's source at `v12.2` (commit ca0f16eb), the version the
household server runs (`/System/Info/Public` reports 12.2.0); the Kids plugin facts are from its 0.0.7.0 code; app
facts are from `main` after PR #2 (b256c829). Paths without a prefix are under
`app/src/main/java/dev/aldoborrero/peke/`.

## R1. Signing a kid in without their password

**Decision**: the TV starts a Quick Connect request for each picked kid (`QuickConnect/Initiate`, sent with that
kid's device id, see R2), the in-memory administrator session approves it for the kid
(`QuickConnect/Authorize?code=&userId=<kid>`), and the TV redeems the secret
(`Users/AuthenticateWithQuickConnect`) to get the kid's token. SDK 1.7.1 has `authorizeQuickConnect(code, userId)`.
Before starting, `GET /QuickConnect/Enabled` (anonymous) tells whether Quick Connect is on; when it's off, the step
explains how to turn it on and offers the kid's password once (spec FR-006).

**Rationale**:
- `QuickConnectController.Authorize` takes `userId`; a caller other than that user must be an administrator.
- `QuickConnectManager.AuthorizeRequest` creates the session with `SessionManager.AuthenticateDirect` for the target
  user, using the device id, device name, client and version that the TV sent with `Initiate`; the approver's device
  plays no part. No password check.
- The household server has Quick Connect enabled (`/QuickConnect/Enabled` → `true`).

**Failure cases the step must explain** (all come back from `Authorize`):
- 403 when the kid's account doesn't allow access from all devices and the TV isn't on its list (`CanAccessDevice`),
  or when the kid has reached `MaxActiveSessions`;
- 404 for an expired code (10 minutes after `Initiate`; polling doesn't extend it);
- 400 for an unknown user.

A disabled kid or one outside their access schedule still gets a token that fails on every request, so the step
checks the new token with `Users/Me` before saving it.

**Handling the secret**: it gives a usable token for 10 minutes after approval and can be redeemed more than once in
that window, so it's kept in memory only, never logged or saved, and dropped once redeemed.

**Alternatives considered**:
- Quick Connect approved from a phone signed in as the kid: needs each kid's account on a phone; rejected.
- A plugin endpoint that opens a kid session: works, but it's new security-sensitive plugin code, and Jellyfin already
  does this.
- Typing each kid's password: kept only as the fallback when Quick Connect is off.

## R2. Several kids on one TV, and one device id per kid

**Decision**: each kid on the TV gets its own Jellyfin device id: `pekeDeviceId("$baseId:$userId")`, derived from
the existing install id plus the kid's user id. The administrator session gets a random throwaway id per sign-in,
as `KidsApi.isAdministrator` already does. All of a kid's calls (SDK and `KidsApi`) send that kid's device id.

**Rationale**:
- Tokens of different users on one device id don't revoke each other (`GetAuthorizationToken` only removes the
  same user's tokens on that device). Live sessions are per user since Jellyfin 12.0.
- But `Logout` from one token ends **every** live session on that device id. With today's single id
  (`services/hilt/PekeDevice.kt`), ending the admin session, removing a kid or a kid's re-sign-in would drop the
  siblings' live sessions and WebSockets.
- jellyfin-androidtv does the same: it hashes the base id with the user.
- The plugin binds a parent grant to the user and the token's device; a per-kid id stays stable for that kid, so
  grants keep working.

**Existing kid tokens** (test TVs, and the old user list until US1 replaces it, sign kids in under the install id):
- They keep working with the kid's id in the header. Jellyfin authenticates by token alone: `AuthorizationContext`
  finds the device row by token, and a different `DeviceId` header doesn't rewrite it (`AuthorizationContext.cs:132-183`).
- The live session follows the header (`GetSessionKey(app, deviceId, userId)`), while the token's device row keeps the
  install id. Signing such a token out deletes that row and ends the live sessions of the install id, not the kid's
  own, which then idles out. The dashboard lists the token under the install id until the kid signs in again.
- The plugin's grants keep working: `CallerResolver.DeviceKeyFor` takes the device the token was registered with, not
  the header, and a kid's grant request and its later use send the same kid id anyway.

**Alternatives considered**: keep one id and reconnect the others after every logout (fragile, and the dashboard
shows one device for all kids); a random id per sign-in (every re-sign-in leaves a stale device in the dashboard).

## R3. One source of truth for kids mode

**Decision**: a `TvSetup` record in the kids database (`peke_kids`, version 2 with a migration and a test) replaces
the `kids_lock` key and both flags (`KidsModeService.locked`/`isLockedNow` and `lockEngaged`). See
[data-model.md](data-model.md). Every kids-mode check reads `TvSetupRepository.state`.

**Rationale**:
- Today `locked` means "the key isn't false and kids are cached" while `lockEngaged` means "the key is true"
  (`kids/KidsModeService.kt:143-156`, `287-290`). The readers are split between them, which is the cause of findings C3,
  C4, C5 and C17.
- In the new flow a set-up TV is always in kids mode, so the lock is no longer a separate fact: it follows from
  "this TV is set up".
- Missing data fails closed: no setup row but a server stored → treated as set up (kids mode, ask a grown-up). Only
  no server at all → setup.

**Alternatives considered**: a proto DataStore field (no migration, but the kids on the TV live in Room and the two
would need to be written in step); keeping `kids_lock` with one reader (keeps the "unlocked" state the spec removes).

## R4. Where the kids' sessions live

**Decision**: kid tokens stay in Wholphin's `users` table (`JellyfinUser`), so the player and data layers keep
working as they are (constitution II). A row exists only for kids on this TV. `TvSetup` lists those rows. The
`<pkg>_server` most-recent-session prefs (`data/MostRecentServer.kt`) go: the launch rule reads `TvSetup`.

The administrator never goes through `ServerRepository.changeUser`, which is the only place that saves a sign-in.
It lives in an in-memory `AdminSession`: its own `ApiClient`, the throwaway device id, and a `close()` that calls
`Sessions/Logout`. That deletes the token and its device row, so the dashboard shows nothing left. `close()` runs
when its action ends, on process death (nothing was stored anyway) and when setup is abandoned.

Offline launch: `changeUser` today needs `getCurrentUser` over the network, so an offline launch with one kid falls
back to the user list. The new launch opens the kid from the stored row and refreshes the user in the background.

## R5. Listing the household's kids during setup

**Decision**: with the admin session, read `GET /Kids/Profiles` (user ids of the kid profiles) and `GET /Users`
(names and image tags), and join them. Images load from `/Users/{id}/Images/Primary`, which needs no sign-in.

**Rationale**: `/Kids/Profiles` returns only user id and revision. `/Users/Public` leaves out hidden users, and kids
are often hidden from the login screen. `/Users` with an admin token lists everyone.

## R6. "The last kid is stopped" (FR-012a)

**Decision**:
- `TvSetup.lastKidRowId` is written whenever a kid becomes current. Today's `lastUsed` is set only at sign-in.
- "Stopped" is computed from that kid's cached status (`kids_status_<rowId>`, `kids/timer/KidsAllowanceService.kt`):
  time's up for the day, in a break, or outside allowed hours, checked against the wall clock. A denial whose end
  can't be worked out counts as stopped (fail closed).
- A fresh status is fetched with that kid's token when online. Offline, the cache decides.

**Alternatives considered**: always asking the plugin (fails offline); a fixed cool-down after a relaunch (doesn't
match "time's up").

## R7. Parent gate without a grown-up account

The gate already checks the code with a kid's token and the plugin's grant (`kids/gate/ParentLockService.kt`), with
the offline verifier as a fallback. Changes:
- Remove the path where an admin token counts as permission without a code (`isAdmin()` in `auth()`, `:355-367`).
  The admin acts only through `AdminSession`.
- When the TV has no kid with a working token (no kids, all revoked), the gate checks the code against the cached
  offline verifier. With no verifier either, it offers only Forgot the code?, which means the administrator sign-in.
- Recovery (`FR-017`): with the admin session, `PUT /Kids/Parent/Lock` sets a new code without the current one. The
  plugin already allows this for administrators.

## R8. Entry points that switch users or reach grown-up screens

- `services/IntentService.prepare` (`:120-155`) switches to any saved user an intent names, before the lock check, and
  on error goes to the user list (`MainActivity.kt:527-538`). New rule: an intent runs only as the current kid, or is
  dropped. User ids in intents are ignored (C6).
- `MainActivity.handleAppStart` (`:596-601`): the warm start skips every check. The launch rule (R3, R6) runs on every
  start and resume (C5).
- `PekeDreamService` (`:75-81`) restores the last saved session. It now shows only the current kid's artwork, or Peke's
  neutral art when no kid is current (C10).
- `ui/nav/DestinationContent.kt` (`:107-130`, `372-391`) renders Settings, Debug and Kids settings for an active kid
  with no check. Grown-up destinations need a live parent grant (C12).
- Media session (`services/PlayerFactory.kt:242-245`): its default commands don't switch users, so FR-011 holds.
  Limiting controllers stays a later item (P1).
- Backups: `android:allowBackup="false"` plus `dataExtractionRules` and `fullBackupContent` that exclude everything,
  so tokens, the verifier and setup state never leave the TV (C8). `allowBackup` alone doesn't stop device-to-device
  transfer on Android 12 and later.

## R9. Plugin version

The server step reads the anonymous `GET /Kids/Info` and requires plugin 0.0.7.0 or later. Today the version is
checked only by features (0.0.5.0's), and adding a server doesn't check the plugin at all.

## Not in this feature

These are review findings this feature doesn't close, left for a later spec:
- C7: cleartext HTTP and user CAs (owner decision pending: warn or require HTTPS);
- C11: the microphone permission prompt seen by the child;
- C13–C16: smaller UI findings;
- P1: limiting media-session controllers;
- P2, P4, P5.
