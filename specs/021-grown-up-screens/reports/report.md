# Report: The grown-up screens as on the canvas

**Spec**: [../spec.md](../spec.md) · **Branch**: `021-grown-up-screens` (on `019-parent-gate`) · **Date**: 2026-11-11

## What changed

| Board | Page | Code |
|---|---|---|
| 8 Parent menu | Full-screen page: avatar, "Grown-up menu", name, time pill and sitting line; cards Add time (or why not, or Keep watching now), Switch kid, "{Kid}'s rules", Settings; Back pill; S5 pill | `kids/ui/GrownUpMenuPage.kt`, gate wiring in `kids/ui/KidsParentGate.kt` |
| 9 Add time | Full-screen page: where, title, how extra time counts; the stepper as one wide tile with ‹ › chevrons; the forecast box; Back pill. Added: S5 pill, Done and Undo (n); the undo's result as a pill | `kids/ui/AddTimeStepper.kt` (`AddTimePage`, `AddTimeAddedPage`) |
| 10 A kid's rules | Title with avatar and "Saved on the server…"; daily time as a stepper row (OK saves); other rules as rows of text; "What Jellyfin decides" and the dashboard on the right; save state as S5 pills | `kids/ui/rules/KidRulesPage.kt` (`KidRulesContent`) |
| 11 Grown-up setup | Settings without a kid gets board 11's header: buddy, "Set up this TV", its line and the Kids plugin's state | `ui/preferences/PekeSettingsPage.kt` (`SetUpHeader`), `LocalSetUpThisTv` from `kids/ui/GrownUpGuard.kt` |
| 12 Settings | Two columns: "Settings" and DESIGN's six sections, the focused section's rows on the right; Right/OK in, Back up one level; rows restyled | `ui/preferences/PekeSettingsPage.kt`, `PreferencesContent.kt` (`section`), `ClickPreference.kt` (`PreferenceRow`), `SwitchPreference.kt` (`PekeSwitch`), `SliderPreference.kt`, `preferences/AppPreference.kt` (`PekeSettingsSection`) |
| S5 | Pills and notices shared by the pages | `kids/ui/GrownUpKit.kt` |

Toasts removed: the recovered code saved (gate), "Keep watching now" failing (menu), Undo's result (Added), the
parent code change's end (Settings). They go through `GrownUpNotices` or the page itself.

## Validation

- `nix develop -c ./gradlew :app:testDebugUnitTest`: green. `nix fmt` and pre-commit on the changed files: clean.
- `:app:lintDebug` fails on 10 errors that were there before this branch (NewApi in the workers and
  `ParentLockService`, `Rating.kt`, `PlayerState.kt`, `SetPinFlow.kt`, two tests); none in the files changed here.
- New Robolectric tests: `GrownUpMenuPageTest`, `AddTimePageTest`, `KidRulesPageTest`, `PekeSettingsPageTest`;
  `PekePreferencesTest` follows the sections; `KidsParentGateViewModelTest` constructs the view model with notices.
- The rules page scrolls its read-only rows with Up and Down from Daily time: in Spanish, and with picks or
  channels, they don't fit 720 px (found on the renders).
- `GrownUpButton` filled the whole row, leaving Undo zero wide (found by `AddTimePageTest`); fixed.
- Previews in the four palettes and Spanish for each page; renders checked with Robolectric's native graphics (not
  committed). No device or emulator was reachable, so no device screenshots.
- APK size: no assets or dependencies added.
