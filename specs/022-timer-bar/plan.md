# Plan: The kids' timer bar only while playing, its timer left, centre or right

**Spec**: [spec.md](spec.md)

## Approach

- `kids/timer/KidsTimerBar.kt`: add `KidsTimerLabelPlacement` (START, CENTER, END) and
  `KidsTimerBarSetting.placement` / `setPlacement`, key `kids_timer_label`, default CENTER. Position is untouched.
- `kids/ui/KidsTimerEffects.kt`: the bar is drawn when `timeLeftBarShown(top, position)` (playback page and not
  hidden). The band becomes local: the player's warning sits below it. `band` parameter goes.
- `kids/ui/TimeLeftBar.kt`: a `placement` parameter aligns the label in the column, with 64 px at the start or end.
  Previews: three placements × four palettes, top and bottom.
- `ui/nav/ApplicationContent.kt`, `ui/nav/NavDrawer.kt`: drop the band the sidebar pages kept for the bar.
- Settings: `AppPreference.KidsTimerPlacement` in This TV after the bar row; the row is a small composable
  (`TimerPlacementPreference`) over `ChoicePreference`, so it is testable; `PreferencesViewModel.setTimerPlacement`.
- Strings in English and Spanish.
- DESIGN: the band rule in "Shape and space" and the timer bar in "Behaviour".

## Constitution Check

- III (the plugin decides): presentation only, no count changes.
- IV (parent-gated): the setting lives in Settings, behind the gate as before.
- V (D-pad): the row is a ChoicePreference, D-pad operable.
- VII: no third-party material. VIII: tests for the predicate, placements, stored values and the row.
- No dependency, permission or asset change; APK size unaffected.

## Tests

- `TimeLeftBarShownTest`: playback pages yes; home, details, search, favourites, grown-up, gate/stop pages no; hidden no.
- `TimeLeftBarPlacementTest` (Robolectric): label left/centre/right at top and bottom.
- `KidsTimerBarSettingTest`: old stored positions read the same, placement defaults to centre, set/get.
- `TimerPlacementPreferenceTest` (Robolectric): OK opens the choice, picking "On the right" reports END.
- `TimeLeftBandTest` stays for the band, now used by the warning; `PekePreferencesTest` lists the new row.
