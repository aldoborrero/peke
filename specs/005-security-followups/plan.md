# Implementation Plan: Security follow-ups

**Branch**: `005-security-followups` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-security-followups/spec.md`

## Summary

Close the review leftovers that 004 didn't close, each with a small, local change:

- **Opening videos (C13, C14)**: the Play key goes where OK goes (direct play only from "Continue watching"), the
  home reads its current rows, and direct play swaps only its own loading page (R4, R5).
- **Microphone (C11)**: a per-TV "Voice search" setting, off by default, that a grown-up turns on behind the gate
  after an explanation; the system prompt goes to the grown-up; the mic button never asks; offline recognition is
  preferred; the music visualiser's request goes (R8).
- **Player and focus (C15, C16)**: kids never get the debug overlay; stop screens focus their title, not Grown-ups
  (R6, R7).
- **Other devices (P1, P4)**: the media session accepts only Peke and trusted controllers, with transport commands
  only; only the Kids plugin's session messages show (R9, R10).
- **Connection (C7)**: HTTPS first for a bare host, a warning before any `http://` server, "Not encrypted" in This
  TV, trust anchors unchanged by the owner's decision, and no kid removed on an empty list without a per-kid answer
  (R1–R3).
- **Error toast (P5)**: non-blocking and friendly (R11). **P2** is cut: the plugin 0.0.7.0 already cuts streams
  (R12).

## Technical Context

- **Language/Version**: Kotlin 2.4 (K2), AGP 9.2.1, Gradle 9.8, JVM 11 target (unchanged).
- **Primary Dependencies**: Compose for TV, Hilt, Navigation 3, Proto DataStore, Media3 1.11.1 session
  (`MediaSession.Callback`, `ControllerInfo.isTrusted`), Jellyfin SDK 1.7.1 discovery. No new dependency.
- **Storage**: one new proto3 field, `SearchPreferences.kids_voice_search` (tag 3, default false). No Room change.
- **Testing**: JUnit4, MockK, Robolectric, coroutines-test. Command:
  `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest`, then `nix develop -c ./gradlew --stop`.
- **Target Platform**: Android TV, Google TV and Fire TV; minSdk 23, target/compile 37; D-pad.
- **Project Type**: single Android app module (`app/`).
- **Performance Goals**: none new. The bare-host lookup keeps the SDK's parallel candidate check (one round).
- **Constraints**: no emulator use without the coordinator; every new string in English and Spanish; MPV, FFmpeg
  and AV1 untouched.
- **Scale/Scope**: about 25 files changed, 3 new source files (`KidsSessionCallback`, `VoiceSearchConsent`,
  `KidsPlayKey` helpers), about 8 new or extended test classes, about 15 new strings per language.

## Constitution Check

*Gate before Phase 0, re-checked after Phase 1.*

| Principle | Check | Status |
|---|---|---|
| I. Kids-only app | No grown-up viewing added; Voice search is a TV setting in the grown-up menu | Pass |
| II. Lean fork, recognisable core | `PlaybackPage`, `PlayerFactory`, `ServerRepository` keep their shape; changes are a parameter, a callback and a lookup rule. Dead visualiser permission code is deleted | Pass |
| III. The plugin decides | Kids removal waits for the plugin's per-kid answer; time stays server-side (P2 confirmed in the plugin) | Pass |
| IV. Parent-gated and fail-closed | The mic consent sits behind the gate; an unreadable voice pref is off; an empty kids list removes nobody; kid-safe defaults for the debug overlay | Pass |
| V. TV-first, D-pad | Stop screens no longer focus Grown-ups; all new dialogs are D-pad buttons | Pass, fixes C16 |
| VI. Private by default | `RECORD_AUDIO` kept and justified (kid voice search with grown-up consent). The voice goes to the TV's speech service: see Complexity Tracking | Pass with justification |
| VII. GPLv2 and branding | No change | Pass |
| VIII. Tested where it matters | Unit tests for the lookup rule, the HTTP warning flow, the kids refresh rule, the Play key rule, `replaceTop`, the session commands and connection rule, the message filter, the voice visibility rule and the toast handler | Pass |

**Permissions (VI)**: the merged manifest keeps `RECORD_AUDIO`; no permission is added. Its runtime request moves from
the kid's mic button to the grown-up's consent dialog.

**APK size**: no dependency, asset or native library change; expected release size within ±20 KB per ABI. Measured
size recorded at the end (as in 004, `assembleRelease` may need the owner's extension credentials; then a debug build
comparison is recorded instead).

**Post-design re-check (Phase 1)**: unchanged; the contract keeps every rule above. Pass.

## Project Structure

### Documentation (this feature)

```text
specs/005-security-followups/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── kid-surfaces.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

Paths under `app/src/main/java/dev/aldoborrero/peke/` unless shown in full.

```text
app/src/main/proto/PekeDataStore.proto              # kids_voice_search (R8)
app/src/main/res/xml/network_security_config.xml    # comment only (R2)
app/src/main/res/values/strings.xml                  # new strings (EN)
app/src/main/res/values-es/strings.xml               # new strings (ES)

kids/setup/SetupFlow.kt            # SetupServerLookup.pick, insecure flag, HTTP warning, Voice step (R1, R8)
kids/data/KidProfileRepository.kt  # refreshServer: per-kid confirmation (R3)
kids/playback/KidsDirectPlay.kt    # replaceTop (R5)
kids/ui/KidsTimesUpPage.kt         # title focus (R7)
kids/KidsPlayKey.kt                # new: kids Play key destination helper (R4)
ui/main/HomePage.kt                # rememberUpdatedState rows, Play key rule (R4)
ui/components/ItemGrid.kt, RecommendedContent.kt, CollectionFolderView.kt
ui/search/SearchPage.kt, ui/detail/collection/CollectionDetails.kt   # grid Play key in kids mode (R4)
services/NavigationManager.kt      # replaceTop (R5)
ui/playback/PlaybackPage.kt        # debug overlay gate (R6)
ui/components/VoiceSearchButton.kt, VoiceInputManager.kt             # no request, visibility rule, prefer offline (R8)
ui/search/SearchForDialog.kt       # visibility rule (R8)
ui/preferences/VoiceSearchConsent.kt   # new: explanation + permission for the grown-up (R8)
ui/preferences/PreferencesContent.kt   # Voice search and server rows in This TV (R1, R8)
preferences/AppPreference.kt       # KidsVoiceSearch, ServerAddress rows in pekePreferences (R1, R8)
ui/detail/music/NowPlayingPage.kt  # visualiser permission request removed (R8)
services/KidsSessionCallback.kt    # new: controller and command rules (R9)
services/PlayerFactory.kt, services/MusicService.kt                  # use the callback (R9)
services/ServerEventListener.kt, kids/KidsServerMessages.kt        # message filter (R10)
util/ExceptionHandler.kt           # non-blocking friendly toast (R11)

app/src/test/java/dev/aldoborrero/peke/
├── kids/setup/SetupServerLookupTest.kt      # new
├── kids/setup/SetupFlowViewModelTest.kt     # HTTP warning, Voice step
├── kids/KidProfileRepositoryTest.kt         # empty and partial lists
├── kids/KidsDirectPlayTest.kt               # replaceTop paths
├── kids/KidsPlayKeyTest.kt                  # new
├── services/NavigationManagerTest.kt        # new
├── services/KidsSessionCallbackTest.kt      # new
├── kids/KidsServerMessagesTest.kt           # new
├── ui/components/VoiceSearchShownTest.kt    # new
└── util/ExceptionHandlerTest.kt             # new
```

**Structure Decision**: the single `app/` module, as in 004. New kids-only code goes under `kids/` or `services/`;
Wholphin files keep their layout and only gain a parameter or a check.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Principle VI: a kid's voice goes to the TV's speech service, not only the household server | Voice search for kids who can't type or read; chosen in spec 001 (Q1), now behind a grown-up's informed consent, off by default, preferring on-device recognition | A household-server recogniser doesn't exist in Jellyfin; dropping voice search removes search for pre-readers |
| C7 left partly open: plain HTTP stays usable | Owner decision Q1: A, so households with an HTTP-only LAN server keep working; the grown-up confirms a warning and This TV shows it | Requiring HTTPS was declined by the owner |
