# Implementation Plan: The parent gate as on the canvas

**Branch**: `019-parent-gate` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/019-parent-gate/spec.md`

## Summary

Replace the gate's code dialog (`KidsPasscodeDialog` → `PinEntryCreate` in a `BasicDialog`) with a full-screen page,
`ParentCodePage`, drawn from board 7 with the theme roles, in a full-screen dialog window so every caller keeps its
result (spec D1). The refusals move from a callback that raised Toasts, and from the `WrongCode` dialog, into the
`Passcode` stage, which the page shows as S5 pills (D5, D6). `ParentCodeChange`'s current-code step uses the same page
(D9).

## Technical Context

**Language/Version**: Kotlin, Jetpack Compose for TV (`androidx.tv.material3`)

**Primary Dependencies**: none new

**Storage**: N/A

**Testing**: JUnit4, Robolectric with the Compose test rule, MockK (existing setup)

**Target Platform**: Android TV, 960×540 dp

**Project Type**: Android TV app

**Performance Goals**: no new work on the main thread; the page draws a fixed set of shapes

**Constraints**: D-pad only; arrows are code input with the remote type; no Toasts in the code entry

**Scale/Scope**: one new composable file, edits to the gate, its view model, the code change and strings

## Constitution Check

- **I. Kids-only**: unchanged; the page is the gate itself.
- **III. The plugin decides**: verification still goes through `ParentLockService`; nothing decided locally.
- **IV. Parent-gated, fail-closed**: same stages and outcomes; a refusal leaves the gate closed. Back closes the gate
  and returns to the caller's screen, never past it. Nothing new is stored.
- **V. TV-first**: the page has one focus target (the entry), so focus can't land on a parent control by default; the
  Back pill is a label (D4). Text is not the only cue: dots change colour and each pill has an icon.
- **VI. Private**: no data or permission change.
- **VII. Branding**: Peke's own icons and words.
- **VIII. Tested**: view model tests for the refusals in the stage, Robolectric tests for the page (errors, Back pill,
  Back key, both types, Spanish).
- Dependencies, assets, APK size: none added; no size change expected beyond a few KB of code.

Result: pass, no complexity tracking.

## Project Structure

### Documentation (this feature)

```text
specs/019-parent-gate/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/gate-page.md
└── tasks.md
```

### Source Code

```text
app/src/main/java/dev/aldoborrero/peke/kids/
├── ui/ParentCodePage.kt          # new: the page, dots, pills, cross and pad
├── ui/KidsParentGate.kt          # Passcode stage → ParentCodePage; WrongCode dialog removed
├── ui/KidsParentGateViewModel.kt # GateStage.Passcode carries the refusal; WrongCode stage removed
├── ui/KidsPasscodeDialog.kt      # keeps ParentLockHint/parentLockSymbol; the dialog goes
└── ui/ParentCodeChange.kt        # current-code step → ParentCodePage, wrong code inline
app/src/main/res/values{,-es}/strings.xml
app/src/test/java/dev/aldoborrero/peke/kids/
├── KidsParentGateViewModelTest.kt # + refusal tests
└── ParentCodePageTest.kt          # new
design/DESIGN.md                   # Behaviour: the gate page, its errors and Back
```

**Structure Decision**: the existing single app module.
