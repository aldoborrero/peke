# Tasks: The parent gate as on the canvas

**Input**: [plan.md](plan.md), [spec.md](spec.md), [data-model.md](data-model.md), [contracts/gate-page.md](contracts/gate-page.md)

## Phase 1: Foundation

- [X] T001 Strings in English and Spanish: title "Grown-ups only", the two hints, "Back to %1$s", "Back"
  (`app/src/main/res/values/strings.xml`, `values-es/strings.xml`)
- [X] T002 `GateStage.Passcode` gains `refusal`; every refusal bumps `attempt` and sets it; remove `WrongCode` and
  `retryCode()` (`kids/ui/KidsParentGateViewModel.kt`)
- [X] T003 [P] View model tests: wrong, throttled and can't-check stay on the entry with the refusal; empty OK after a
  wrong code opens recovery without verify and Back returns with no refusal (`KidsParentGateViewModelTest.kt`)

## Phase 2: User stories 1–3 (P1) — the page, its errors, Back

- [X] T004 `ParentCodePage` per the contract: full-screen window, left column, dots, pills, Back pill, cross for
  `REMOTE`, pad for `NUMERIC`, extra-key row; previews for the four palettes, both types and the errors
  (`kids/ui/ParentCodePage.kt`)
- [X] T005 Gate uses it, with "Back to {Kid}" when `withKid` and a kid is known, else "Back"; drop the Toasts and the
  `WrongCode` dialog (`kids/ui/KidsParentGate.kt`)
- [X] T006 Remove `KidsPasscodeDialog`, keep `parentLockSymbol` and `ParentLockHint`, now in `kids/ui/ParentLockHint.kt`
- [X] T007 [P] Robolectric tests: title, hint per type, cross vs pad, "Back to Lucy"/"Back", pill not focusable, Back
  key with keys typed dismisses, each refusal shows and hides on the next key, the entry clears, Spanish
  (`ParentCodePageTest.kt`)

## Phase 3: User story 4 (P2) — changing the code

- [X] T008 Current-code step on `ParentCodePage` with `Wrong` inline, no forgot hint, no Toast
  (`kids/ui/ParentCodeChange.kt`)

## Phase 4: Polish

- [X] T009 DESIGN "Behaviour": the gate page, its errors and the Back pill (`design/DESIGN.md`)
- [X] T010 `nix fmt`, pre-commit on changed files, unit tests, debug build

## Phase 5: Follow-up — one kind of key per code (D11)

- [X] T011 `ParentLockKeys`: arrows codes are `UDLR` only; drop `Extra` and `availableExtras()`; `PasscodeHasher.isValid`
  takes the type (`kids/gate/`)
- [X] T012 No extra-key row under the cross; `ParentLockHint` shows only the arrows for `REMOTE`; `ExtraKeyHint` goes
  (`ParentCodePage.kt`, `ParentLockHint.kt`, `PinArrowRow(withDigits)`)
- [X] T013 Strings: the arrows hint and the setup choice name only the arrows (en, es)
- [X] T014 [P] Tests: `ParentLockTest`, `PasscodeHasherTest`, `ParentLockHintTest`, `ParentCodePageTest`
