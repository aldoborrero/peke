# Implementation Plan: No Back pill on grown-up screens

**Branch**: `023-no-back-pill` | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)

## Summary

Delete `BackKeyPill` (`kids/ui/GrownUpKit.kt`) and its three uses: `ParentCodePage` (gate and code change, through
`backLabel`), `GrownUpMenuPage` and `AddTimeStepper`. Drop `backLabel`, `gateBackLabel`, the `BACK` test tags and the
strings `kids_gate_back_to` / `kids_gate_back` (en, es). The bottom rows keep their spacer, so the notice pill and
Add time's actions stay on the right. Back handling is untouched.

## Constitution Check

UI-only removal: no new dependency, no change to the gate's grant or fail-closed rules, D-pad focus unchanged (the
pill never took focus). Tests: the Robolectric tests that looked for the pill now check it is gone; the Back tests
stay.

## Files

- `app/src/main/java/dev/aldoborrero/peke/kids/ui/{GrownUpKit,ParentCodePage,ParentCodeChange,KidsParentGate,GrownUpMenuPage,AddTimeStepper}.kt`
- `app/src/main/res/values{,-es}/strings.xml`
- `app/src/test/java/dev/aldoborrero/peke/kids/{ParentCodePageTest,GrownUpMenuPageTest}.kt`
- `design/DESIGN.md`, notes in specs 019 and 021
