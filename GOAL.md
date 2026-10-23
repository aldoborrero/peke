# Goal: Peke for Jellyfin, foundation (spec 003)

Turn this repo (an independent hard fork of Wholphin, now github.com/aldoborrero/peke) into **Peke for Jellyfin**: a kids-only Android TV app for households that run the Jellyfin Kids server plugin (0.0.7.0 or later). This first feature lays the foundation: identity, a slimmer build, its own database for kids data, and fixes for the known leaks. The screen redesign comes in later features.

## Decisions already made by the user
- Independent app, not a Wholphin flavor. No regular upstream merges; playback and Jellyfin SDK fixes are cherry-picked when needed.
- Name: "Peke for Jellyfin" (short "Peke"). applicationId `dev.aldoborrero.peke`. Kotlin namespace and packages `dev.aldoborrero.peke.*` (full fork; only the external `wholphin-mpv` API keeps its package).
- License stays GPLv2-only. Keep Wholphin's credits, the git history and the OSS license screen.
- Branding follows Jellyfin's rules ("X for Jellyfin" only, own logo). The logo is being drawn in /home/aldo/Dev/aldoborrero/peketv-logo; use it if finished, or a placeholder otherwise.
- Kids mode only: grown-ups use the app just to set up the TV (add the server, sign in kids, admin recovery sign-in, the parent gate, kids settings and adding time). Grown-ups watch with another client.

## Inputs
- Study: /home/aldo/Dev/aldoborrero/Wholphin/reports/Wholphin Kids version reducida.md and its notes in /home/aldo/Dev/aldoborrero/Wholphin/research_notes/Wholphin Kids version reducida/ (features_and_screens.md, build_deps_size.md).
- The existing constitution at .specify/memory/constitution.md was written for "kids mode inside Wholphin" and needs a MAJOR revision for an independent kids app.
- Existing specs: specs/001-kids-mode and specs/002-kids-server-plugin.
- Server contracts: github.com/aldoborrero/jellyfin-kids, under specs/*/contracts/.

## Scope of feature 003
1. Constitution 2.0.0, through /speckit-constitution. Replace the Wholphin-centric principles:
   - kids-only app;
   - free to delete Wholphin code, but keep the player and data/API layers recognisable for cherry-picks;
   - the plugin decides (the server is the source of truth);
   - parent-gated and fail-closed;
   - TV-first, D-pad complete;
   - private by default;
   - GPLv2 and Jellyfin branding;
   - tested where it matters.
2. Identity:
   - the applicationId above;
   - the app name "Peke" (launcher label "Peke"; "Peke for Jellyfin" where space allows);
   - launcher icon and TV banner from the logo;
   - its own deep-link scheme (`peke://`) and custom actions;
   - a Jellyfin client name and device name that identify Peke.
3. Build:
   - drop MPV (link the stub or remove it), the FFmpeg and AV1 extension decoders, the updater and its markdown renderer, Seerr and discover, tvprovider and the EPG launcher channels, DASH, and the external player;
   - force ExoPlayer;
   - locales en and es only;
   - manifest: drop REQUEST_INSTALL_PACKAGES, the storage permissions, POST_NOTIFICATIONS, EPG, the FileProvider and the external-player <queries>. The microphone and voice search are an open question; ask.
   - Simplify the flavors: one product, no appstore or firetv split unless needed.
   - Fix upstream mistakes: room-testing and the test runner on implementation, and unused deps.
4. Data:
   - move the kids tables into their own Room database;
   - leave Wholphin's database as is, or prune what's unused if it's safe;
   - since this is a new applicationId, there's no data to migrate from the Wholphin install.
5. Leak fixes from the study:
   - the player debug info and other advanced options reachable by kids;
   - music, people and photos reachable from Favorites and Search;
   - cinema mode on by default (off for Peke);
   - theme music (off);
   - "Leave" in the gate leading to a grown-up profile that opens full Wholphin. Instead, grown-up profiles get a minimal grown-up shell: kids settings, a short "This TV" settings group, the user list and the license.
6. Kids strings translated to Spanish.

## Done when
- The build passes, unit tests pass, and ktlint is clean on changed files (`nix develop -c ./gradlew assembleDebug testDebugUnitTest`, or the flavor-less equivalent).
- The release APK size is measured, with a target of about 10–11 MB per ABI.
- The specs, plan, tasks and constitution are written.
- Nothing is committed; the user signs the commits.
