# Quickstart: validating the security follow-ups

How to show that feature 005 works. Contract: [kid-surfaces.md](contracts/kid-surfaces.md); data:
[data-model.md](data-model.md).

## Prerequisites

- 004 merged, or this branch on top of it.
- The household server (`https://jellyfin.homik.xyz`) with the Kids plugin 0.0.7.0+ and a kid with search allowed.
  For the HTTP checks, a plain-HTTP address of a test server on the LAN (for example `http://<lan-ip>:8096`).
- A TV or the emulator, when the coordinator says it's free. The owner types every password and approves every
  Quick Connect; agents never handle credentials.
- For P1, an API < 28 device if one is at hand (Fire TV or an older Android TV), besides a recent one.

```sh
nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest
nix develop -c ./gradlew --stop
```

## Scenarios

1. **Play key (US1, C13)**
   - On a kid's home: `adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE` on a Picks item → details page.
   - The same on a "Continue watching" item → plays, resuming.
   - Open Favourites and repeat on a grid item → details page.
2. **Back during direct play (US1, C14)**
   - Throttle the network (or use a slow item), OK on "Continue watching", then `KEYCODE_BACK` at once.
   - Wait 10 s: still on the home, nothing plays.
   - Give the kid 1 minute, start a "Continue watching" item as it runs out: the time's-up screen stays, no frames play.
3. **Microphone (US2, C11)**
   - Fresh install, set up: if a kid has search allowed, the last step offers voice search; choose Not now.
   - As the kid, open Search: no mic.
   - Grown-ups → code → This TV → Voice search: the explanation, then the system prompt. Allow.
   - As the kid: the mic shows and works with no prompt.
   - `adb shell pm revoke dev.aldoborrero.peke.debug android.permission.RECORD_AUDIO`, reopen Search: no mic, no
     prompt.
   - Turn Voice search off: no mic.
4. **Debug info (US3, C15)**: This TV → Playback debug info on; play as the kid: controls show, no overlay.
5. **Focus (US4, C16)**: let a kid's time run out; press `KEYCODE_DPAD_CENTER` at once: nothing opens. `KEYCODE_DPAD_DOWN`
   then `KEYCODE_DPAD_CENTER`: the gate opens. Repeat for a break and for outside allowed hours.
6. **Media session (US5, P1)**
   - During kids playback, `adb shell dumpsys media_session`: Peke's session is listed.
   - `adb shell cmd media_session dispatch pause` / `play` (or the remote's media keys) work.
   - A third-party media controller app (installed by the owner) can't connect or gets no transport.
   - On an API < 28 device, the remote's play/pause and fast-forward keys still work in the player (R9 risk).
7. **Messages (US5, P4)**: from a grown-up's Jellyfin web client, Dashboard → Activity → the kid's session → Send
   message: nothing shows on the TV. A Kids plugin warning (5 minutes left) still shows.
8. **HTTP (US6, C7)**
   - Setup with `jellyfin.homik.xyz` typed bare: HTTPS is used, no warning; This TV shows the https address.
   - Reset, then setup with the LAN `http://` address of a server without HTTPS: the warning shows; Back returns to
     the address; Use it anyway goes on. This TV shows "Not encrypted".
9. **Empty kids list (US6, FR-016)**: covered by `KidProfileRepositoryTest`; on a device, unmark one kid in the
   dashboard and check that only that kid leaves the TV.
10. **Error toast (P5)**: covered by `ExceptionHandlerTest`.
