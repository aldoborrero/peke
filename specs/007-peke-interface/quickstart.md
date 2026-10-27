# Quickstart: Peke's interface

## Build and test (every phase)

```sh
nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin
nix fmt && nix fmt            # the second run changes nothing
nix develop -c pre-commit run --all-files
nix develop -c ./gradlew --stop
```

## Phase 1: visual base

1. **Migration**: on a TV (or emulator) that has Peke from `main` with a Wholphin theme saved, install this build.
   Peke opens in PekeTV (night blue `#1F2747`, coral focus ring). `ThemeMigrationTest` covers every stored value.
2. **Focus**: on the kids home, move with the D-pad: each focused card or button shows a white border, a coral ring
   outside it, and grows slightly. In Settings (behind the parent code) rows show border and ring without growing.
3. **Theme choice**: Settings › This TV › Theme lists PekeTV, Mandarina, Caramelo, Papel (daytime), opens on the
   current one; picking Papel turns the app cream with a dark focus border; a restart keeps it.
4. **Type**: all text is Nunito (round terminals); no text smaller than the "LIVE" badge size.
5. **Licenses**: Settings › About › Licenses lists Nunito under the SIL Open Font License 1.1.
6. **Contrast**: `ContrastTest` checks DESIGN's table for the four themes.
7. **Size**: compare clean debug APKs of `main` and this branch per ABI (`apkanalyzer apk compare --different-only`).

Screenshots on the Windows emulator (`emulator-5554`): install with `adb.exe install -r` from `/mnt/c`, drive with
`adb shell input keyevent KEYCODE_DPAD_*`, capture with `adb exec-out screencap -p`. Never type the parent code.

## Phase 2: assets

Every drawable inflates in `DrawablesTest`; the launcher shows the buddy icon and the banner; see
`assets-conversion.md` for anything the conversion changed.

## Phase 3: kid screens

With a plugin offering `avatar`: the picker shows the avatars; without it, profile images or initials. Timer bar on
Top, Bottom and Hidden; at five minutes left it grows with the warning.

## Phase 4: stops and states

Force time's up, a break, outside hours, an unplayable item and an unreachable server; each shows its buddy mood
and Grown-ups in the corner, not focused first.

## Phase 5: first setup

Walked by the owner after a reset (agents never reset the TV).
