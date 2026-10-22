# Quickstart: validating kids mode v1

## Prerequisites

- Nix with flakes. From `.worktree/kids-mode`, run `nix develop`, or `direnv allow`.
- A Jellyfin server (10.10+) with:
  - an adult user
  - a kid user, restricted in the dashboard: library access, max parental rating, "block items
    with no rating"
  - at least two libraries, one collection and some partially watched episodes for the kid
- An Android TV device or emulator, reached with `adb connect <ip>`.

## Build and automated checks

```bash
nix develop -c ./gradlew testDefaultDebugUnitTest     # all unit tests, incl. kids/*
nix develop -c ./gradlew assembleDefaultDebug
nix develop -c adb install -r app/build/outputs/apk/default/debug/*.apk
```

Expected: build succeeds, every test passes, and the schema `41.json` is generated under
`app/schemas/`.

## Manual scenarios

Each scenario maps to the spec.

1. **Setup (Story 3):**
   1. Sign in as adult and open Settings → Kids mode. You are asked to create a passcode.
   2. Mark the kid user as a kid profile and pick a collection.
   3. Expected: the recommendations checklist is shown.
2. **Kids home (Story 1, FR-003a):**
   1. Switch to the kid.
   2. Expected sections, in this order: Keep watching, Picked by your grown-ups, one row per
      library A–Z, New, My favourites.
   3. Expected absences: no "view more", no long-press menu, a drawer that shows only Home / My
      stuff / lock, and no ratings in the header.
3. **Restricted content (Story 1.4):** expected: no item above the kid's rating appears in any
   row, in search (once enabled) or in details.
4. **Escapes (Story 2, SC-002):** Back on Home opens the drawer, and Back again shows the gate.
   Also try:
   - the lock → wrong answer → new challenge
   - correct answer → menu, where "Leave kids mode" asks for the passcode
   - relaunching the app → still the kid home
5. **Timer (Story 4, SC-004/005):**
   1. Start a 5-minute timer and play an episode.
   2. At 1 min remaining: expected toast and beep, with audio dipping and not pausing.
   3. At 0: expected playback stops and Time's up appears.
   4. Kill and relaunch: expected Time's up again.
   5. Through the gate, choose add 5 min: expected you are back home.
   6. Change the device clock while the timer runs: expected remaining time does not grow.
6. **Autoplay (Story 5):**
   1. With default settings, an episode ends → expected the end screen shows Watch again / Home.
   2. Enable autoplay with cap 2: expected autoplay stops after 2 episodes and the end screen
      appears.
7. **Adult unchanged (FR-021, SC-006):** switch back to the adult. Expected: home rows,
   drawer, context menus and player options are exactly as before.
