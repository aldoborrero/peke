# Quickstart: Canvas screens

## Build and test (every phase)

```sh
nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin
nix fmt && nix fmt            # the second run changes nothing
nix develop -c pre-commit run --all-files
nix develop -c ./gradlew --stop
```

Size: clean debug builds of the previous phase and this one, per ABI, compared with `apkanalyzer apk compare`.

## On the emulator

Install the x86_64 debug APK: copy it to `C:\Users\aldob\AppData\Local\Temp\peke\peke-debug.apk` and run
`adb.exe -s emulator-5554 install -r` from `/mnt/c`. Drive with `adb shell input keyevent KEYCODE_DPAD_*`, capture
with `adb exec-out screencap -p`. Never type the parent code, never reset the TV, never start playback on purpose.

Compare each screen with its board (render the board at 1280×720 with headless Chromium; the TV screenshot is
1920×1080, so 1 board px = 1.5 screenshot px).

1. **Home**: greeting and time left; wide, channel and poster cards with titles; the rail; Left opens the sidebar
   (avatar, name, time left, items, Grown-ups at the bottom, page dimmed). Back on the home opens the sidebar, again quits.
2. **Details**: from a poster row open a series, then a season: poster, title, chips, Play, heart, episodes; the
   season chip opens Seasons. A film page has the same header.
3. **Player**: reached only by playing; screenshots only if playback starts by accident (stop it at once). The
   next-up panel and the warning are checked in code review and by their tests.
4. **Live TV**: from the sidebar: the list of channel cards.
5. **Search**: the pill field and icons; type with the on-screen keyboard; favourites; a folder from a picks row.
6. **Screensaver**: start it with `adb shell am start -n …/.PekeDreamService` is not possible for an app screensaver;
   wait for the in-app screensaver's timeout, or check the system dream with `adb shell am start -a android.intent.action.MAIN -n com.android.systemui/.Somnambulator`.
   Update and microphone pages are behind the parent code: owner only.
