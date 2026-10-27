# Phase 5 report: first setup

Spec 007, User Story 5, tasks T053–T055. Builds on Phase 4.

## Changes

- `kids/setup/SetupFlow.kt`: every setup step (select and add server, the administrator sign-in, the parent code,
  picking kids and their sign-in with Quick Connect, voice search) is drawn by `SetupFlow`, which now:
  - shows the Peke logo (`R.drawable.logo_peke`: the buddy and the wordmark, Phase 2) as the header of every step;
  - draws step titles in DESIGN's Title style (`headlineLarge`) in the text colour;
  - provides `LocalPekeFocusScale` false and `LocalGrownUpPage` true: grown-up focus (border and ring, no scale-up)
    on the steps' buttons, rows and kid avatars, and errors with their details.
- `ui/components/CircularProgress.kt`: the shared spinner is drawn in primary (it was the focus-border colour, white).
- Spec: `tasks.md` (T053–T055), `plan.md` (Measured, and the total for Phases 1–5).

No behaviour changed: the steps, their order, Back and the view model are as 004 built them. No string changed.

## Tests

No new tests: the change is presentation only (a header, a title style and two composition locals). The setup flow's
existing tests (`SetupFlowViewModel`, `KidsPickerTest`, `RefusedSessionsTest` and the rest of 004's) run unchanged and
pass.

## Validation

- `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`: BUILD
  SUCCESSFUL, 821 tests, 0 failures.
- `nix fmt`: 1 file changed on the first run, 0 on the second.
- `nix develop -c pre-commit run --all-files`: all hooks passed.
- As in Phase 4, the first `clean :app:assembleDebug` for the size failed in `packageDebug` with no cause in the log and
  passed on a fresh daemon.

## Screenshots

`…/scratchpad/shots/p5-home.png`: the build running on the emulator. The setup steps show only on a TV being set up or
after the parent code (adding kids, signing a kid in again); I didn't reset the TV or type the code, so they need the
owner.

## APK size

Clean debug builds, Phase 4 → Phase 5: arm64-v8a −6, armeabi-v7a −11, x86_64 0 bytes.

Phases 1–5 against the base (`0d9d65c9`): arm64-v8a +101,174, armeabi-v7a +101,183, x86_64 +101,191 bytes
(Nunito +171 KB, the vectors +45 KB, the old launcher art −150 KB, code about +35 KB).

## Commit message

```
Give first setup Peke's header and grown-up focus

Every setup step shows the Peke logo above a title in the new type,
with the focus border and ring but no scale-up, as on the other
grown-up screens, and setup errors keep their details. Spinners use
the primary colour.
```
