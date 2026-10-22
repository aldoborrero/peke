# Quickstart: validating Peke foundation

Spec: [spec.md](spec.md). Contracts: [identity](contracts/identity-and-intents.md),
[releases](contracts/release-assets.md), [grown-up shell](contracts/grown-up-shell.md).

## 1. Owner runbook: tags (run by the owner, not by an agent)

Wholphin's inherited tags must go before the first Peke release (research R5). The repo has 53 `v*` tags
(`v0.0.1` … `v1.0.9`) and `develop`, locally and on `origin` (aldoborrero/peke). Check the list first:

```sh
git tag --list                                   # expect v0.0.1 … v1.0.9 and develop
git ls-remote --tags origin | grep -v '\^{}'     # same set on aldoborrero/peke
gh release list -R aldoborrero/peke              # expect no releases
```

Stop Wholphin's tags coming back, then delete them locally and on the peke remote:

```sh
git config remote.upstream.tagOpt --no-tags
git tag --list 'v*' develop | xargs git tag -d
git ls-remote --tags origin | grep -v '\^{}' | sed 's#.*refs/tags/##' \
  | xargs -n 20 git push origin --delete
```

The first release is then `git tag -s v0.1.0 && git push origin v0.1.0` (signed with the YubiKey), which builds
`versionName 0.1.0`, `versionCode 1`.

## 2. Extensions credentials (owner)

MPV and the FFmpeg/AV1 decoders come from `maven.pkg.github.com/damontecres/wholphin-extensions`, which needs a
classic PAT with only `read:packages`. Put it where Gradle reads it, never in the repo:

```properties
# ~/.gradle/gradle.properties
WholphinExtensionsUsername=<github login>
WholphinExtensionsPassword=<classic PAT, read:packages>
```

or export `ORG_GRADLE_PROJECT_WholphinExtensionsUsername` / `…Password`. For CI, add the same values as the
repository secrets `EXTENSIONS_USERNAME` and `EXTENSIONS_PASSWORD` on aldoborrero/peke.

## 3. Build and tests

```sh
nix develop -c ./gradlew assembleDebug testDebugUnitTest      # no credentials needed (stub MPV, warning)
nix develop -c ./gradlew assembleRelease                      # needs §2; fails clearly without it
nix develop -c ./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -E 'wholphin-mpv|decoder-(ffmpeg|av1)'
```

Expected: build and all unit tests pass; ktlint (pre-commit) is clean on changed files; the release classpath
lists `wholphin-mpv`, `decoder-ffmpeg` and `decoder-av1`, and not `room-testing`, `androidx.test:runner`,
`tvprovider`, `acra-http`, `acra-dialog` or `material3-adaptive-navigation3`.

## 4. APK size and manifest

```sh
ls -l app/build/outputs/apk/release/*.apk
aapt2 dump badging app/build/outputs/apk/release/Peke-release-*-arm64-v8a.apk | grep -E "package|uses-permission|application-label"
```

Expected: package `dev.aldoborrero.peke`, label `Peke`; permissions exactly as in plan.md's table; size recorded in
the PR (expected ~44–45 MB arm64-v8a, ~40–41 MB armeabi-v7a). `lib/arm64-v8a/` contains `libmpv.so`,
`libffmpegJNI.so`, `libdav1dJNI.so`.

## 5. Manual checks on a TV (with a server running the Jellyfin Kids plugin ≥ 0.0.7.0)

| # | Steps | Expected |
|---|---|---|
| 1 | Install Peke next to Wholphin; sign both in to the same server; alternate sign-in and playback 10 times | Both stay signed in; dashboard shows two devices, one "Peke for Jellyfin" (SC-001) |
| 2 | Fresh install: add server, sign in an administrator | Grown-up shell; set the household code, mark a kid, sign the kid in, switch to the kid |
| 3 | As the kid: open the player's settings | No debug-info toggle, speed, video scale or subtitle delay rows; the overlay shows only after a grown-up turns on "Show playback debug info" in settings (SC-002) |
| 4 | As the kid: Favorites tabs, search (when allowed) with music/people/photos favourited on the server | Only video types |
| 5 | As the kid: open a home-videos row with photos | No photos or albums; no "unsupported item" |
| 6 | As the kid: play a movie on a server with cinema intros; open a collection with theme music | No pre-roll; no theme music |
| 7 | Gate → Leave → pick the administrator | Grown-up shell only; `adb shell am start -a android.intent.action.VIEW -d "peke://…"` to a details page opens the shell (SC-003) |
| 8 | `adb shell am start -d "wholphin://…"` | Not handled by Peke |
| 9 | Shell → Settings | Playback switches present (backend ExoPlayer/MPV/prefer MPV, FFmpeg, AV1, bitrate, resolution, refresh rate), debug info, Quick Connect authorize, Experimental; no Seerr, external player, home customization, nav pins, Wholphin autoplay or pass-out protection |
| 10 | Switch TV language to Spanish; walk kids screens, gate, time's up, shell, settings | No English text (SC-005) |
| 11 | Play the reference set (H.264/AAC, HEVC/AC3, AV1, DTS, TrueHD, ASS subs) and compare the dashboard's play method with Wholphin 1.0.9 | Same direct-play results (SC-004) |
| 12 | Server without the plugin | Shell explains the plugin is needed; no kid can use it |
| 13 | With a published `v0.1.x` release newer than the installed one: Shell → Settings → Update | Finds and installs the right ABI asset; a kid never sees an update toast; no request to `damontecres/Wholphin` (SC-007) |
| 14 | Remove a kid user, then a server, from the user/server lists; restart | No orphan kids rows (kids settings lists no ghost kid) |

## 6. Measured on 2026-10-22 (implementation)

Release build: **pending the owner's extensions token**. `assembleRelease` stops with "Peke release builds need FFmpeg
decoder, AV1 decoder, MPV: set WholphinExtensionsUsername and WholphinExtensionsPassword…" (the guard from T043).
Re-run §3–4 once the token is in `~/.gradle/gradle.properties`; the expected size stays ~44–45 MB arm64-v8a.

Debug build (not minified, MPV stub, no FFmpeg/AV1 decoders, versions still from Wholphin's tags until §1 is done):

| APK | Size |
|---|---|
| `Peke-debug-…-arm64-v8a.apk` | 37.1 MB (Wholphin kids-mode debug was 45.0 MB) |
| `Peke-debug-…-armeabi-v7a.apk` | 35.8 MB |
| `Peke-debug-…-x86_64.apk` | 37.2 MB |
| `Peke-debug-….apk` (universal) | 48.4 MB |

Merged manifest (debug, `aapt2 dump badging`): package `dev.aldoborrero.peke.debug`, label `Peke (debug)`, locales
`en` and `es` only, leanback not required. Permissions: `INTERNET`, `ACCESS_LOCAL_NETWORK`, `RECORD_AUDIO`,
`REQUEST_INSTALL_PACKAGES`, `WRITE/READ_EXTERNAL_STORAGE` (maxSdk 28), and from libraries `ACCESS_NETWORK_STATE`,
`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` and the AndroidX dynamic-receiver permission. No
`POST_NOTIFICATIONS`, EPG or external-player query.

Unit tests: 545 pass (`clean assembleDebug testDebugUnitTest`); ktlint 1.8.0 clean on the 91 changed Kotlin files.
