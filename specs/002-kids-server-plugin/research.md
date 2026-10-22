# Research: Kids mode backed by the Jellyfin Kids server plugin (client)

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Inputs: the plugin contracts (feature 1 `kids-api-v1.md`, feature 2 `kids-api-v1-parent-lock.md`, feature 3
`kids-api-v1-allowance.md` in the `kids-allowance-tracking` worktree), the plugin's `docs/plan.md` and constitution,
and the kids mode v1 code in `app/src/main/java/com/github/damontecres/wholphin/kids/`. The earlier `/KidsMode` study
is kept in [study/](study/README.md).

## R1. How to call `/Kids` endpoints

- **Decision**: a small `KidsApi` on top of the app's `@StandardOkHttpClient` (`services/hilt/AppModule.kt`). Each
  call takes the server URL and, when authenticated, the user's access token; it builds the `Authorization` header
  with the SDK's `AuthorizationHeaderBuilder`, the same way `@AuthOkHttpClient` does. JSON with kotlinx
  serialization (`ignoreUnknownKeys`, `explicitNulls = false`), PascalCase names through `@SerialName`.
- **Rationale**:
  - The SDK's `ApiClient.request()` (1.7.1) takes no custom headers, so it cannot send `X-Kids-Grant`.
  - Its `InvalidStatusException` keeps only the status code. The client needs the body of a 409 (the current
    profile), a 400 (field errors) and a 429 (`RetryAfterSeconds`).
  - The `@AuthOkHttpClient` always uses the *current* user's token. Kids calls sometimes need another signed-in
    user's token: reading each kid's own profile ("self"), or verifying from the user switcher where no user is
    current.
- **Alternatives**: the SDK `ApiClient` (rejected for the reasons above); a second Retrofit stack (a new dependency,
  against constitution "no new dependency").

## R2. Detection and caching (FR-001–FR-005)

- **Decision**: `KidsPluginService` checks `GET {server}/Kids/Info` anonymously:
  - when the current server changes;
  - when the app returns to the foreground, if the last check is more than 5 minutes old.

  The result goes into a Room row per server (`kids_servers`).
- **Interpretation**:
  - **200** stores the API version and features.
  - **404** stores "no plugin".
  - **Any other answer**, or a network or parse error, keeps the last row unchanged.
  - **Kids mode is available** when `ApiVersion == 1` and the features include both `profiles` and `parentLock`.
  - **An unknown version** counts as not available.
- **Fail closed** (FR-025): the cached kid rows are what put a user in kids mode. Detection never deletes them. A
  server that is not available but still has cached kids is in the **gone** state:
  - kids mode keeps working from the cache;
  - the parent menu shows the notice and the "turn off kids mode here" action (Q4).
- **Rationale**: the check is one small anonymous GET. Recording only the last good answer means a flaky network
  can neither enable nor disable kids mode.

## R3. Profiles: cache and writes (FR-006–FR-010)

- **Decision**: keep the v1 `kid_profiles` table as the cache, keyed by the device user row as before. Add:
  - `revision`, the server revision;
  - `dailyAllowanceMinutes` and `sessionLimitMinutes`;
  - `serverJson`, the raw profile object as last received.
- **Writes**:
  - A write is the cached JSON object with the app's edited fields replaced, plus `Revision`. Fields the app does
    not know survive a TV edit (FR-010), and the plugin can add fields without an app release.
  - `PUT` with revision 0 marks a kid; `DELETE` unmarks one.
  - On 200 the response replaces the cache. On 409 the returned profile replaces the cache, and the UI says the
    profile changed elsewhere (FR-009).
- **Reads**:
  - `GET /Kids/Users/{id}/Profile` with that user's own token, whenever the user becomes current.
  - `GET /Kids/Profiles` then each changed kid, when the user switcher or kids settings load.
  - **200** upserts the row. **404** deletes it (not a kid). **Anything else** keeps the cache (FR-027).
- **Defaults when marking**: v1 `KidProfile` defaults (search off, autoplay off, cap 3, all sections). These match
  the plugin's defaults.
- **Alternatives**:
  - A separate cache table (rejected: `KidsModeService` and every consumer already read `kid_profiles`).
  - Modelling only the known fields (rejected: a TV edit would then drop fields the app does not know).

## R4. Parent lock, grants and the offline verifier (FR-012–FR-018)

- **Decision**: `ParentLockService` replaces v1's `ParentPasscodeStore` and `ArithmeticChallenge` (Q2: the
  challenge lock is dropped).
- **Which server and token a check uses**: the current server and user. When no user is current (for example the
  kids lock guard on the server list), it uses the server of the most recently used kid, with any stored token on
  it.
- **Lock type**: `GET /Kids/Parent/Lock`, with the cached value used on any failure.
- **Checking a code**:
  - **Online**, it calls `POST /Kids/Parent/Verify`:
    - `Valid:true` keeps the grant in memory, keyed by server and user row, valid until `ExpiresAt` minus 15 s
      (measured from receipt with the monotonic clock). It then fetches `GET /Kids/Parent/Verifier` with the grant
      and caches the type and verifier on the server row (FR-015).
    - `RetryAfterSeconds` makes the gate wait (FR-014).
  - **Offline** (network error only): it checks the code with `PasscodeHasher.verify` against the cached verifier.
    No grant results, so server writes then fail with a "can't reach the server" message, and leaving kids mode still
    works.
  - **Without a cached verifier**, an offline check says the code can't be checked now (edge case).
- **Writes** carry `X-Kids-Grant` when a grant is held. A 403 drops the grant, and the settings page asks for the
  code again (FR-013).
- **First code and changing the code** (Q1):
  - An administrator profile (`currentUserDto.policy.isAdministrator`) sets the code with `{LockType, Code}`.
  - A grant holder changes it with `{LockType, Code, Current}`, after entering the current code again.
  - Every lock change revokes grants, so the app verifies the new code right away to get a fresh grant and
    verifier.
- **Removing the lock** is not offered (FR-018).
- **Rationale**: the grant is the only way a kid's token can make parent changes. Keeping it in memory and bound to
  the sign-in follows plugin constitution IV. The verifier format is the same, so `PasscodeHasher.verify` checks
  server verifiers unchanged.
- **Leftovers**: v1's DataStore keys `kids_passcode_verifier` and `kids_lock_type` are no longer read. v1 was never
  released, so they are not migrated.

## R5. Daily allowance (FR-019–FR-023)

- **Decision**: `KidsAllowanceService` polls `GET /Kids/Users/{id}/Status` while a kid is current, the app is in
  the foreground, and the server advertises `allowance`:
  - every 60 s;
  - every 15 s when 2 minutes or less are left;
  - right away when playback ends (the back stack leaves the player).
- **Caching**: the last status is kept in `KeyValueService` per user row, so a time-up survives restarts and works
  offline (FR-023).
- **Warnings**: a warning fires when the remaining time crosses one of `WarnAtSeconds` between two successive
  statuses. With 15 s polling near the end, a 60 s warning is at most 15 s late.
- **Time up**:
  - **Shown** when `TimeUp` is true.
  - **Lifted** when a later status says false, which happens after a bonus or a new day.
  - **Adding time** from the gate uses `POST …/WatchTime/Bonus` with the grant (FR-022).
- **Unlimited kids** (no `AllowanceSeconds`) have no allowance indicator.
- **`WatchTime/Report` is not used**: Wholphin has no offline downloads, so the server sees all playback.
- **Rationale**: no server push exists for plugin events (plugin plan §2.3: fixed socket message types), so polling
  is the contract's intended model. The extra state stays outside Room because only the latest status matters.

## R6. Sitting limit (Q3)

- **Decision**: an optional profile field `SessionLimitMinutes` (5–120, null or absent = none).
- **Starting a sitting**: when kids mode becomes active (inactive → active) with the timer idle and a limit set,
  the device starts its v1 `KidsTimerService` with that limit.
- **Unchanged from v1**: the parent menu still starts, changes or stops the sitting timer by hand, and the timer
  persists and counts monotonically as before. A stopped timer stays idle until the next activation.
- **Showing the control**: the plugin omits null properties, so a missing field cannot tell "no limit" from "an old
  plugin". The settings control is shown when `Info.Features` contains `sessionLimit`, or the cached profile
  carries the field. This is an assumption about how the plugin will advertise the field; it will be confirmed with
  the plugin when it adds the field.
- **One indicator**: `KidsTimeLimits` combines the sitting timer and the allowance into one state:
  - **remaining**: the smaller of the two;
  - **expired**: either one has run out;
  - **warnings**: from both.

  The bar, the warnings and the time's up switch read it.

## R7. Admin rights for first setup (Q1)

- **Decision**: kids settings can be opened from any profile through the existing `AppPreference.KidsMode` entry,
  which is hidden unless kids mode is available for the current server. Then:
  - **No lock, current user is an administrator**: they set the household code first.
  - **No lock, not an administrator**: settings show only a message pointing to an administrator profile or the
    dashboard.
  - **With a lock**: the code is asked for, and the resulting grant authorises changes. With an administrator
    profile current, the admin token alone is enough on the server, but the gate is still shown (constitution IV).
- **Rationale**: it uses the session that is already signed in, so no new credentials are stored (constitution
  III).

## R8. Hiding entry points without the plugin (FR-005, User Story 6)

- **Decision**: `PreferencesContent` filters out `AppPreference.KidsMode` unless `KidsPluginService` says kids mode
  is available for the current server.
- **Other entry points**: none. The kids UI (`LocalKidsMode`, the drawer, the gate) appears only for users with a
  cached kid row, and rows only come from the server.
- **Rationale**: one small, explicit extension point in shared code (constitution II).

## R9. Database

- **Decision**: Room version 41 → 42 as an `AutoMigration`:
  - new columns on `kid_profiles`, with defaults;
  - a new `kids_servers` table: server id (foreign key to `servers`, cascade), plugin API version, features, when it
    was checked, lock type, verifier.
- **Test**: a `TestDbMigrations` case for 41 → 42.
- **Rationale**: 41 exists only on this unreleased branch, but dev installs may have it. Changing 41 in place would
  break them with an identity-hash mismatch.

## R10. Constitution notes

- **Principle III**: "the client owns … daily limits". In plugin mode the server counts the daily allowance. This
  is the plugin's purpose and the user's decision, so it is recorded under Complexity Tracking. A MINOR amendment to
  principle III (the server may decide time limits when the household plugin provides them) is recommended, through
  `/speckit-constitution`.
- **Principle II**: shared code changes are limited to:
  - registering two more lifecycle observers and injections in `MainActivity`;
  - the preference filter;
  - the user switcher refresh;
  - the Room version.
