# Implementation Plan: The grown-up screens as on the canvas

**Branch**: `021-grown-up-screens` | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md)

## Summary

A small kit of grown-up page parts (`kids/ui/GrownUpKit.kt`: page frame, header, cards, rows, Back pill, S5 pill and
the notice host) draws boards 8–12 with the theme roles. The gate's `Menu`, `AddTime` and `Added` stages move from
`DialogPopup`/`BasicDialog` to full-screen windows built from it (`GrownUpMenuPage.kt`, `AddTimeStepper.kt`). A kid's
rules is laid out again in `KidRulesPage.kt` with a stepper for daily time. Settings for Peke (`PreferenceScreenOption.PEKE`)
becomes a two-column page (`PekeSettingsPage.kt`) whose rows are drawn by Wholphin's preference code, restyled once
in `ClickPreference`, `SwitchPreference` and `SliderPreference`. Toasts on these screens go to `GrownUpNotices`, a
singleton the pages show as S5 pills.

## Technical Context

**Language/Version**: Kotlin, Jetpack Compose for TV (`androidx.tv.material3`)

**Primary Dependencies**: none new

**Storage**: N/A

**Testing**: JUnit4, Robolectric with the Compose test rule, MockK, Hilt (existing)

**Target Platform**: Android TV, 960×540 dp

**Constraints**: D-pad only; Back goes up one level; no Toasts on these screens; grown-up focus (no scale)

**Scale/Scope**: five pages, a shared kit, strings, tests

## Constitution Check

- **I. Kids-only**: no new grown-up feature; Settings shows the same preferences, grouped as DESIGN says.
- **II. Lean fork**: the player and data layers are untouched; the preference logic (`AppPreference`, its groups,
  `PreferencesViewModel`) stays, only the rows' look and the PEKE screen's frame change.
- **III. The plugin decides**: add time, undo, break end and daily time still go through the plugin services.
- **IV. Parent-gated, fail-closed**: the stages, the grant rules and the guards are unchanged; pages still sit behind
  `GrownUpGuard`. Focus never starts on a kid-facing control (these are grown-up pages).
- **V. TV-first**: every page reachable and leavable with the D-pad; Back up one level; icons with every card.
- **VI. Private**: nothing new stored or sent.
- **VII. Branding**: Peke's icons and words, the buddy only on board 11's header (DESIGN mood table).
- **VIII. Tested**: Robolectric tests for the menu, Add time, the rules' stepper, Settings' focus and the notice pill;
  view model tests where logic moved (the rules' unsaved value, notices).
- APK size: no assets or dependencies; a few KB of code.

Result: pass, no complexity tracking.

## Project Structure

```text
app/src/main/java/dev/aldoborrero/peke/
├── kids/ui/GrownUpKit.kt          # new: page frame, header, cards, rows, Back pill, S5 pills, notices
├── kids/ui/GrownUpMenuPage.kt     # new: board 8
├── kids/ui/AddTimeStepper.kt      # board 9 and the Added pill
├── kids/ui/KidsParentGate.kt      # Menu/AddTime/Added → pages; toasts → notices
├── kids/ui/ParentCodeChange.kt    # Done → notice
├── kids/ui/ParentCodePage.kt      # Back pill and S5 pill from the kit
├── kids/ui/rules/KidRulesPage.kt  # board 10
├── kids/ui/GrownUpGuard.kt        # SetupThisTvPage: board 11 header flag
├── ui/preferences/PekeSettingsPage.kt  # new: board 12 (and 11's header)
├── ui/preferences/PreferencesContent.kt # PEKE → PekeSettingsPage; rows shared
├── ui/preferences/{Click,Switch,Slider}Preference.kt # board 12 row style
└── preferences/AppPreference.kt   # pekeSettingsSections (the "More" group goes)
app/src/main/res/values{,-es}/strings.xml
app/src/test/java/dev/aldoborrero/peke/kids/{GrownUpMenuPageTest,AddTimePageTest,KidRulesPageTest,PekeSettingsPageTest}.kt
design/DESIGN.md
```
