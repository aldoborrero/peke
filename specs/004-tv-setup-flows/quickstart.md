# Quickstart: validating the TV flows

How to show that feature 004 works end to end. Contracts: [tv-flows.md](contracts/tv-flows.md),
[server-calls.md](contracts/server-calls.md).

## Prerequisites

- A Jellyfin 12.x server with the Kids plugin 0.0.7.0+, Quick Connect on, and two kid profiles marked in the plugin
  (the household server has Manuel and another kid).
- The Windows emulator `peke_tv` (or a TV) and the devshell's adb. The owner types every password and approves
  every Quick Connect; agents never handle credentials.

```sh
nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest
```

Install with `adb install -r` after `adb uninstall dev.aldoborrero.peke.debug`, so every check starts from a fresh
install.

## Scenarios

1. **Setup (US1, SC-001, SC-002)**
   - Run the steps: server → admin → (code) → pick both kids → sign-in. Time it.
   - No kid password is typed.
   - Dashboard → Devices: one device per kid for the TV, no administrator device.
   - `adb shell run-as dev.aldoborrero.peke.debug` and search the databases, prefs and datastore for the admin's
     user id: nothing found.
2. **Launch (US2, SC-003)**
   - Cold start, Home and reopen, and a screensaver exit: each lands on the picker (two kids).
   - Remove one kid, then repeat: each lands on that kid's home.
   - Airplane mode, cold start: the kid's home opens with the offline states.
3. **Stopped sibling (FR-012a)**
   - Give a kid 1 minute in the dashboard and let it run out.
   - Home, reopen, pick the sibling: the gate opens.
   - Pick the same kid: the time's-up screen.
4. **Switch kid inside a session (FR-012)**: the sidebar avatar asks for the code.
5. **Parent menu (US3)**
   - With the code: add time, remove a kid, add them back (admin sign-in, then the admin device is gone again).
6. **Reset (US4)**
   - Reset this TV: the server step shows, and the dashboard shows no devices for the TV.
   - Offline reset: setup shows, and a message says to remove the devices in the dashboard.
7. **Recovery (US5)**: Forgot the code? → admin → new code; the old code fails.
8. **Review findings (SC-004, SC-005)**: replay the reproduction steps of C2, C3, C4, C5, C6, C10, C17 and C18 from
   `reports/Peke adversarial review.md`, for example:
   - `adb shell am start -a dev.aldoborrero.peke.PLAYBACK …` naming another user;
   - `peke://search` on a kid without search;
   - unmarking the last kid in the dashboard.

   None reaches a non-kid, a user list or setup.
9. **Backups (C8)**: `adb shell bmgr backupnow dev.aldoborrero.peke.debug` reports the package as not eligible.
