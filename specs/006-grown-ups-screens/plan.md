# Implementation Plan: Grown-ups screens

**Branch**: `006-grown-ups-screens` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/006-grown-ups-screens/spec.md`

## Summary

Option C of the grown-ups review, by the owner's priority:

1. **No marking on the TV (F-K1)**: the kid switches go with Kid's settings; `mark`, `unmark` and `deleteProfile` are
   deleted (R1).
2. **Add time**: a stepper stage in the parent gate over fixed amounts, opening on this TV's last amount, with a
   result line forecast from the status, a busy state, a confirmation with Undo (plugin 0.0.9.0) and inline errors;
   a reason line where it can't add time, with "Keep watching now" on a break (R2–R6).
3. **"{Kid}'s rules"**: one page for the current kid, Daily time the only focus stop, the rest as text, the
   dashboard address at the end; the old editors and their view model are deleted (R7).
4. **Task H**: channel names on the rules page; a refused channel opens a kids message page with Grown-ups (R8).
5. **Regroup**: fixed menu order; Settings straight from the picker; Settings › Household (change the parent code)
   and This TV (Kids on this TV, timer bar) (R9, R10).
6. **Gate**: empty OK opens recovery, Quick Connect in recovery, "parent code" everywhere, no dead focus stops (R11).
7. **Small items**: 0.0.7.0 in the outdated text, "Parent code changed", choices open on the current value (R12).

## Technical Context

- **Language/Version**: Kotlin 2.4 (K2), AGP 9.2.1, Gradle 9.8, JVM 11 target (unchanged).
- **Primary Dependencies**: Compose for TV, Hilt, Navigation 3, DataStore (`KeyValueService`), Jellyfin SDK 1.7.1
  (`UserPolicy.accessSchedules`, `enableLiveTvAccess`), OkHttp through `KidsApi`. No new dependency.
- **Storage**: one `KeyValueService` key, `kids_add_time_minutes`. No Room or proto change.
- **Testing**: JUnit4, MockK, Robolectric, coroutines-test. `nix develop -c ./gradlew --console=plain assembleDebug
  testDebugUnitTest compileDebugAndroidTestKotlin`, then `nix develop -c ./gradlew --stop`.
- **Target Platform**: Android TV, Google TV and Fire TV; minSdk 23, target/compile 37; D-pad.
- **Project Type**: single Android app module (`app/`).
- **Performance Goals**: none new; the rules page reads cached data and makes at most two calls (pick names, channels).
- **Constraints**: plugin 0.0.9.0 is built in parallel: everything it adds is feature-gated; every string in English
  and Spanish; MPV, FFmpeg and AV1 untouched.
- **Scale/Scope**: about 25 files changed, 3 deleted (`KidsSettingsPage`, `KidsSettingsViewModel`,
  `KidProfileSettings`) plus a test, about 6 new source files, about 60 strings per language changed or added.

## Constitution Check

*Gate before Phase 0, re-checked after Phase 1.*

| Principle | Check | Status |
|---|---|---|
| I. Kids-only app | Grown-ups still "change kids settings (the daily time) and add time"; no grown-up viewing added. No amendment needed | Pass |
| II. Lean fork, recognisable core | Deleted code is Peke's own kids editors. `KidProfileRepository`, `KidsApi`, `ServerRepository`, the player keep their shape; `ChoicePreference`/`DialogPopup` gain one parameter | Pass |
| III. The plugin decides | The bonus, its undo and ending a break are server calls; the result line is only a forecast from the server's status; bedtime is read from Jellyfin, never written | Pass |
| IV. Parent-gated and fail-closed | Every new action (stepper, Undo, Keep watching now, rules, change code) is behind the gate and its grant; a grant that runs out asks again; unknown status means "offline", no Add time; the refused-channel page's Grown-ups opens the gate | Pass |
| V. TV-first, D-pad | Stepper on Left/Right; one focus stop on the rules page; no dead focus stops in the gate; focus never starts on Grown-ups on the channel page | Pass, fixes F-G5, F-K4 |
| VI. Private by default | No permission, data collection or upload added | Pass |
| VII. GPLv2 and branding | The dashboard address is the household's own server; nothing implies an official Jellyfin app | Pass |
| VIII. Tested where it matters | Unit tests for the stepper and its stored amount, the preview and availability rules, the bonus/undo/break-end calls, the gate's empty OK and Quick Connect, the rules view model's save and pending retry, `householdAuth`; deleted code's tests deleted | Pass |

**Permissions (VI)**: none added or removed.

**APK size**: no dependency, asset or native library change; expected release size within ±20 KB per ABI. Measured size recorded at the end.

**Measured (2026-10-26, T037)**: not compared with the base build. The worktree's debug APKs are packaged
incrementally (arm64-v8a 39,765,416 bytes, x86_64 39,813,615), which carries unused zip space, so a fair comparison
needs clean builds of both commits in a separate clone; that wasn't run. No dependency, asset or native library
changed.

**Lines (T037)**: Kotlin under `app/src/main/java` +2,255 / −1,250 (975 in changed files, 1,280 in 5 new files;
`kids/ui/settings/` deleted), tests +781 / −84, strings in English and Spanish +171 / −84. The review's
−700 estimate for option C didn't count the Add time widget, Quick Connect in recovery or the refused-channel page.

**Post-design re-check (Phase 1)**: the contracts keep every rule above. Pass.

## Project Structure

### Documentation (this feature)

```text
specs/006-grown-ups-screens/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── grown-up-screens.md
│   └── kids-api-bonus-undo.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

Paths under `app/src/main/java/dev/aldoborrero/peke/` unless shown in full.

```text
app/src/main/res/values/strings.xml, values-es/strings.xml   # new and changed strings

kids/server/KidsApi.kt, KidsDtos.kt       # bonus id, undo, break end, features; deleteProfile removed (R1, R6)
kids/data/KidProfileRepository.kt         # mark/unmark removed (R1)
kids/timer/KidsAllowanceService.kt        # addBonus → bonus id, undoBonus, endBreak, reachable (R4, R6)
kids/timer/KidsTimeLimits.kt              # holdStop (R5)
kids/timer/AddTime.kt                     # new: steps, stored amount, preview and availability (R2–R4)
kids/gate/ParentLockService.kt            # householdAuth for setCode (R10)
kids/ui/KidsParentGate.kt, KidsParentGateViewModel.kt   # menu, stepper stages, empty OK, Quick Connect (R2–R6, R9, R11)
kids/ui/AddTimeStepper.kt                 # new: stepper and confirmation UI (R2)
kids/ui/KidsPasscodeDialog.kt, KidsAdminSignIn.kt       # forgot hint, Quick Connect (R11)
kids/ui/KidsTimerEffects.kt               # honour holdStop (R5)
kids/ui/KidsTimesUpPage.kt                # channel refused page reuses KidsGrownUpsMessage (R8)
kids/ui/ParentCodeChange.kt               # new: change the parent code from Settings (R10)
kids/ui/rules/KidRulesPage.kt, KidRulesViewModel.kt     # new: the kid's rules (R7)
kids/ui/settings/*                        # deleted
ui/nav/Destination.kt, DestinationContent.kt, kids/ui/GrownUpGuard.kt   # KidRules, KidsChannelRefused
ui/playback/PlaybackViewModel.kt          # refused channel page (R8)
ui/components/Dialogs.kt, ui/preferences/ChoicePreference.kt   # initial focus (R12)
ui/preferences/PreferencesContent.kt, PreferencesViewModel.kt, preferences/AppPreference.kt   # Settings groups (R9, R10)
design/DESIGN.md                          # Behaviour: "After the parent code"

app/src/test/java/dev/aldoborrero/peke/
├── kids/AddTimeTest.kt                   # new
├── kids/KidsApiTest.kt                   # bonus id, undo, break end
├── kids/KidsParentGateViewModelTest.kt   # stepper, menu without a kid, empty OK, Quick Connect
├── kids/KidRulesViewModelTest.kt         # new
├── kids/ParentLockServiceTest.kt         # householdAuth
├── kids/KidProfileRepositoryTest.kt      # mark/unmark tests removed
└── kids/KidsSettingsEditsTest.kt         # deleted
```

**Structure Decision**: the single `app/` module. New kids code under `kids/`; Wholphin files gain a parameter or a
special case at most.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Client calls for plugin 0.0.9.0 written before its contract is published | The owner builds both in parallel; the calls are feature-gated, so an older plugin never sees them | Waiting would leave Undo and "Keep watching now" for another feature |
