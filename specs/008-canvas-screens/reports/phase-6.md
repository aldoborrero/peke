# Phase 6 report: screensaver, update available, microphone

Spec 008, User Story 6, tasks T038–T043. Builds on Phase 5.

## Changes

New icon:
- `design/assets/icons/download.svg` and `outline/download.svg`: a fat down arrow over a tray.
  - Two-tone: the arrow is teal, outlined in navy.
  - Outline: both in `currentColor`.
  - No circle: DESIGN keeps circles for the transport buttons. Inside the live area, 3.5 strokes, round caps and joins.
- Converted with `Svg2Vector` like the filter icon to `ic_peke_download.xml` and `ic_peke_outline_download.xml`, with
  no warnings.
- Added as `PekeIcon.DOWNLOAD`. `DrawablesTest` lists it. DESIGN.md now says twenty-two icons; Phase 5's sentence
  is extended.

Screensaver (OtherScreensaverPekeTV):
- `ui/components/AppScreensaver.kt`: `AppScreensaverContent` draws the night scene for every screensaver, in-app and
  the system dream:
  - the sky (the page colour 42% darker, `#121730`-like in PekeTV), five stars in "text, secondary", the moon in the
    time colour and the hill in the page colour;
  - the sleeping buddy (240×200 px), which moves to another spot every `duration` and floats gently when "animate" is
    on;
  - the time at the bottom right (40 px) when the clock is on.
- The dim setting still applies in the dream.
- Removed as unused: the item art and logo, `ScreensaverViewModel`, `ScreensaverNeutral` and `ScreensaverPlaceholder`,
  whose error text was hard-coded English.
- `PekeDreamService.kt`: no longer collects the item flow or injects `ScreensaverService`.
- `ScreensaverService` and its tests are untouched. Its item flow has no caller now; the owner may delete it in a
  follow-up.

Update available (OtherUpdatePekeTV):
- `ui/preferences/InstallUpdatePage.kt`:
  - On the left, a 360 px column: the download icon in secondary (120 px), "Update available", "Installed version X"
    and "New version Y" (the new one in the time colour), then Download & Update (64 px primary pill) over Later (60 px
    on surface).
  - On the right, the release notes on a surface panel with 30 px corners and "What's new". The panel takes focus with
    Peke's ring and scrolls with up and down as before.
  - The download dialog shows a 14 px bar in the secondary colour when the size is known, and keeps the spinner
    otherwise.
  - Download, permissions, install, cancel and Later (which is Cancel: go back) are unchanged.

Microphone consent (OtherMicPekeTV):
- `ui/preferences/VoiceSearchConsent.kt`: each stage is a 720 px card on surface with 36 px corners: Peke's voice icon
  in tertiary, the title, the explanation in "text, secondary", and Allow (primary, focused first) / Not now on the page
  colour. The denied stages use the same card with one button. The permission flow is unchanged: explain, the system
  prompt, denied or denied for good, and no system settings.

Strings, English and Spanish: `peke_voice_search_allow`, `update_installed_version`, `update_new_version`,
`update_whats_new`, `update_later`.

Differences from the boards:
- **Consent wording:** the dialog keeps 005's grown-up wording ("Turn on voice search?" and where the voice goes)
  instead of the board's "Search by talking?". The text is the grown-up's informed consent from 005, and changing it
  is the owner's call.
- **Download progress:** it stays in its dialog rather than inside the notes panel. The page dims behind it as before.
- **The night scene for every screensaver:** with no kid current, Peke used to show the hopping buddy; now it shows the
  same night scene. No kid's pictures are on an idle TV any more.

## Tests

- `IconSpecTest` and `DrawablesTest` pass with the download icon.
- No new pure logic. `ScreensaverServiceTest` and the voice search consent tests are unchanged and pass.

## Validation

- `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`: BUILD
  SUCCESSFUL, 842 tests, 0 failures.
- Phase 6 alone, in the scratch clone after formatting: `clean :app:assembleDebug testDebugUnitTest` BUILD SUCCESSFUL.
- `nix fmt`: 3 files changed on the first run (unused imports), 0 on the second.
- `nix develop -c pre-commit run --all-files`: all hooks passed.

## Screenshots

None: the emulator still times out on every `adb shell`. The update page and the consent dialog sit behind the parent
code anyway. Rendered boards: `…/scratchpad/boards/OtherScreensaverPekeTV.png`, `OtherUpdatePekeTV.png`,
`OtherMicPekeTV.png`.

## APK size

- Clean debug builds, Phase 5 → Phase 6: arm64-v8a +2,092, armeabi-v7a +2,089, x86_64 +2,086 bytes.
- All six phases against the base: about +153.8 KB per ABI (plan.md, Measured).

## Shared files

- `ui/theme/PekeArt.kt`, `DrawablesTest.kt`, `design/DESIGN.md` (the icon count sentence) and the icon folders, with
  Phase 5.
- `strings.xml`.
- `tasks.md` and `plan.md`.

## Commit message

```
Give the screensaver, update and microphone screens the canvas look

The screensaver is a night sky with the moon, a hill and the buddy
asleep, moving now and then, with the time when the clock is on; it
no longer shows library art. The update page has the versions and big
Download and Later buttons beside the release notes, and the
microphone question is a card with Peke's voice icon and Allow or Not
now. A download icon joins both icon sets.
```
