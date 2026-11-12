# Report: 022, the kids' timer bar only while playing, its timer left, centre or right

## What changed

- The bar is drawn only on the playback page (`timeLeftBarShown`, `kids/ui/KidsTimerEffects.kt`). Home, details,
  search, favourites, live guide, grown-up pages, the gate and the stop screens draw none; the home keeps its pill.
- The sidebar pages no longer keep a band for the bar (`NavDrawer`, `ApplicationContent`). The band now lives in the
  timer UI and puts the player's time warning below the label (`maxOf(40 px, band + 14 dp)`, 64 dp with the normal
  label at the top, as before).
- Off the player the warning pill sits at 24 dp from the top, the value it had when the bar was at the bottom.
- `KidsTimerLabelPlacement` (START, CENTER, END), stored under `kids_timer_label`, default CENTER; the position keeps
  `kids_timer_bar` and its values.
- `TimeLeftBar(placement = …)`: the label aligns to the start, centre or end, 64 px (48 dp) from the side edges when
  not centred. Its TalkBack node is now the whole pill (it was the pill's content).
- Settings › This TV: "Timer on the bar" / "Tiempo en la barra" after "Timer bar", with "On the left / In the middle /
  On the right" and "A la izquierda / En el centro / A la derecha" (`TimerPlacementPreference`).
- Previews: `TimeLeftBar.kt` draws the three placements at the top and the bottom in each of the four palettes (the
  right one near the end, bigger), Mandarina and Papel in Spanish.
- DESIGN: "Shape and space" (the band rule) and "Behaviour" (This TV's list, a "Time left" entry).

## Tests

- `TimeLeftBarShownTest`: playback pages yes; 12 other pages no; hidden never.
- `TimeLeftBarPlacementTest`: the label at 48 dp from the left, centred, 48 dp from the right, at the top and bottom.
- `KidsTimerBarSettingTest`: TOP/BOTTOM/HIDDEN stored by earlier versions read the same, placement defaults to the
  middle, both are kept under their own keys.
- `TimerPlacementPreferenceTest`: the row shows the placement; OK, Down, OK picks "On the right"; Up, Up picks "On the
  left"; Spanish strings.
- `TimeLeftBandTest` kept (the band still measures the label); `PekePreferencesTest` lists the new row.

## Not done

- No run on the `peke_tv` emulator in this session (no device attached); see the handoff for what to check.
