# Quickstart: validating kids mode with the Jellyfin Kids plugin

## Prerequisites

- **Build and unit tests**: `nix develop -c ./gradlew assembleDefaultDebug testDefaultDebugUnitTest`.
- **A dev Jellyfin 12.x server with the plugin**: from `jellyfin-kids`, run its process-compose dev loop. That repo's
  `docs/plan.md` §6 provisions an admin, a key and a library.
  - For allowance checks, use a plugin build that includes feature 3.
- **Two users on that server**: an administrator (`admin`) and a kid user (`kid`).
- **An Android TV emulator or device** with the debug APK, signed in as both users.

## Scenarios

1. **No plugin** (User Story 6): point the TV at a server without the plugin.
   - Settings show no "Kids mode" entry.
   - Logcat shows a single `/Kids/Info` request, answered 404.
2. **First setup** (User Story 3): with `admin` current, open Settings → Kids mode.
   - Set a numeric code, then mark `kid`.
   - `GET /Kids/Profiles` with the admin token lists `kid`.
3. **Second TV** (User Story 1): sign `kid` in on another device or emulator. It opens in kids mode with the
   sections set on TV 1.
4. **Gate and grant** (User Story 2): as `kid`, open the grown-ups lock.
   - A wrong code shows "wrong passcode".
   - Six wrong codes show the wait.
   - The right code opens the menu, and a settings change appears in the dashboard.
5. **Offline unlock**: after step 4, cut the network. The right code still opens the gate, and a settings change
   says the server can't be reached.
6. **Conflict**: change the same kid on the dashboard, then on the TV without reloading. The TV shows "changed
   elsewhere" with the server values.
7. **Allowance** (User Story 4): set a 10-minute allowance on the dashboard (or the TV), then play.
   - The bar and the warnings follow the server status.
   - At zero, the time's up screen appears within about a minute.
   - "Add time" (15 minutes) resumes playback.
8. **Plugin gone** (User Story 5): disable the plugin on the server, then restart the app.
   - `kid` still opens in kids mode.
   - The gate opens with the cached code.
   - The parent menu offers "turn off kids mode here", which returns the server to stock Wholphin.
   - On a TV that never cached the code, the gate says the plugin is missing and offers an administrator sign-in.
     `admin` signs in, and the only action is "turn off kids mode here".

## Automated checks

The unit tests under `app/src/test/java/com/github/damontecres/wholphin/kids/` cover:
- detection;
- profile mapping and conflicts;
- grant expiry;
- offline verification;
- the allowance poller;
- combined limits.

`TestDbMigrations.migrate41To42` covers the schema. It runs as an instrumented test.
