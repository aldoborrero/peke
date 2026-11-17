# F-Droid

Peke can be built by F-Droid's build server from source, MPV and the Media3 FFmpeg and AV1 decoders included. The Nix
devshell stays the way Peke is built, tested and released; nothing here changes those builds. Nothing has been
submitted to F-Droid yet.

| File | What it is |
|---|---|
| `fdroid/metadata/dev.aldoborrero.peke.yml` | The recipe, in [fdroiddata](https://gitlab.com/fdroid/fdroiddata) format |
| `fdroid/srclibs/{wholphin-extensions,androidx-media}.yml` | The two srclibs fdroiddata doesn't have yet |
| `fdroid/build-extensions.sh` | Builds the three extension AARs into `app/libs/`; the recipe calls it |
| `fastlane/metadata/android/{en-US,es-ES}/` | Title, descriptions, changelogs, icon, TV banner and TV screenshots |

## How the recipe works

There is one build entry per ABI, each built separately like
[Just Player](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.brouken.player.yml), and the native
dependencies come from srclibs like [mpv-android](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/is.xyz.mpv.yml):

1. **srclibs**: wholphin-extensions at the tag in `gradle/libs.versions.toml`, androidx/media at the Media3 version
   wholphin-extensions pins, and FFmpeg, dav1d, cpu_features, mbedtls, freetype, fribidi, harfbuzz, libunibreak,
   libass, libplacebo and mpv at the versions wholphin-extensions' scripts download. Lua has no git repository: the
   tarball is downloaded and checked against a fixed SHA-256, as mpv-android's recipe does.
2. **prebuild** (`build-extensions.sh prepare`): checks every srclib is at the version wholphin-extensions pins and
   fails if not; links FFmpeg, dav1d and cpu_features into androidx/media's decoder modules and copies the libmpv
   dependencies to where `get_dependencies.sh` would clone them; points the Media3 JNI build at the recipe's NDK;
   drops the foojay JDK download plugin; removes the GitHub Packages repository from `settings.gradle.kts`, which
   `fdroid scanner` rejects as an unknown Maven repository. Nothing is compiled yet, so the scanner sees only sources.
3. **build** (`build-extensions.sh build <abi>`): runs androidx/media's `build_ffmpeg.sh` and `build_dav1d.sh` with the
   decoder list and API level from wholphin-extensions' `build_decoders.sh`, the Media3 decoder AARs, libmpv for the
   entry's ABI with wholphin-extensions' `buildall.sh`, and the wholphin-mpv AAR for that ABI. The three AARs land in
   `app/libs/`, where `app/build.gradle.kts` already prefers local AARs.
4. **gradle**: `assembleRelease` with `-Ppeke.versionCode=<code> -Ppeke.updateChecker=false`, and `output:` picks the
   entry's split APK.

The two Gradle properties only exist for F-Droid; without them a build is the same as before:

- `peke.versionCode` replaces the tag count. F-Droid needs a different versionCode per ABI: the recipe uses
  `10 × tags + ABI` (1 armeabi-v7a, 2 arm64-v8a, 3 x86_64), so v0.2.0, the second tag, is 21, 22 and 23. These codes
  are higher than the GitHub releases' codes, but the F-Droid APKs are signed with F-Droid's key, so neither installs
  over the other anyway.
- `peke.updateChecker=false` turns off the in-app updater, which downloads APKs from GitHub releases. F-Droid doesn't
  accept self-updaters in its builds.

Everything uses NDK 29.0.14206865, the NDK wholphin-extensions' CI uses. The one difference from the Nix build in
`wholphin-extensions-build`: Media3 sets no `ndkVersion`, so there its JNI glue builds with AGP's default NDK 28.2;
F-Droid installs one NDK per build entry, so the recipe sets `ndkVersion` and `ndkPath` to NDK 29 for the two decoder modules.

The recipe has `AutoUpdateMode: None` and `UpdateCheckMode: None`, like mpv-android and Just Player: the versionCode is
counted from git tags, so F-Droid's checkupdates can't read it from a file. Each release needs a merge request to
fdroiddata (see [Releasing a new version](#releasing-a-new-version)). Committing the versionCode to a file would let
F-Droid pick up tags on its own (`UpdateCheckMode: Tags` with `UpdateCheckData`), but it changes how releases are cut.

No anti-features apply: Peke needs a Jellyfin server, which is free software (so not `NonFreeNet`), has no tracking or
non-free dependencies, and crash reports go only to the household's own Jellyfin server, never to a third party.

## Testing the recipe locally

`fdroid build` runs on a normal machine too; it skips the `sudo:` block (apt packages) and expects the tools on
`PATH`. The FHS environment in `wholphin-extensions-build` has all of them except `fdroidserver`; build a copy with
`fdroidserver` added and platform 37 / build-tools 37.0.0 for Peke, then:

```sh
# a sparse fdroiddata checkout with its config/ and srclibs/
git clone --depth 1 --filter=blob:none --sparse https://gitlab.com/fdroid/fdroiddata.git fdroiddata
cd fdroiddata && git sparse-checkout set config srclibs
cp <peke>/fdroid/srclibs/*.yml srclibs/
mkdir -p metadata && cp <peke>/fdroid/metadata/dev.aldoborrero.peke.yml metadata/
fdroid lint dev.aldoborrero.peke
fdroid rewritemeta dev.aldoborrero.peke && git diff --exit-code metadata/   # the recipe is already formatted

# config.yml (chmod 600):
#   sdk_path: <sdk>
#   ndk_paths: {29.0.14206865: <sdk>/ndk/29.0.14206865}
#   gradle: <a script running https://gitlab.com/fdroid/gradlew-fdroid's gradlew.py>
# To build an unreleased commit, point Repo: at a local clone and commit: at its hash in a copy of the recipe.
fdroid build -v --no-tarball dev.aldoborrero.peke:22
```

fdroidserver's Nix wrapper puts its own `python3` first on `PATH`, without jinja2, which mbedtls needs (the recipe
installs `python3-jinja2` on the build server). Locally, run `fdroid build` with `PYTHONPATH` pointing at a
site-packages that has jinja2 and markupsafe.

Use a separate `GRADLE_USER_HOME` so Gradle can't read credentials from `~/.gradle/gradle.properties`: the point is
building without the GitHub Packages token. One entry takes 7 to 11 minutes on 32 cores once the srclibs are cloned (cloning them the first time took about
17 minutes) and about 9 GB of disk
(androidx/media's git history is most of it). The unsigned APK lands in `unsigned/`.

## Bumping wholphin-extensions

1. Bump `wholphin-extensions` in `gradle/libs.versions.toml` as usual.
2. In the recipe, set the `wholphin-extensions@` srclib to the new tag, then run `fdroid build` for one entry:
   `build-extensions.sh prepare` stops with "srclib X is at …, wholphin-extensions wants …" for every dependency the
   new tag moved (Media3, FFmpeg, dav1d, mpv, libass, libplacebo, freetype, mbedtls, fribidi, harfbuzz, libunibreak).
   Update those srclib refs and repeat. The decoder list and API level are read from wholphin-extensions, so they
   follow without changes.
3. If wholphin-extensions moves to another NDK (`v_ndk` in `wholphin-mpv/src/native/include/depinfo.sh` and
   `ndkVersion` in `wholphin-mpv/build.gradle.kts`), change `ndk:` in every build entry.
4. If `get_dependencies.sh` gains a dependency or changes how it fetches one (for example, a new tarball),
   `build-extensions.sh` needs the same change; the check only covers the dependencies it knows.

## Releasing a new version

For each release tag `vX.Y.Z`, with N the number of `v*` tags up to and including it:

1. Add `fastlane/metadata/android/{en-US,es-ES}/changelogs/{10N+1,10N+2,10N+3}.txt` (under 500 characters) before
   tagging.
2. In fdroiddata, copy the last three build entries, set `versionName: X.Y.Z`, `commit: vX.Y.Z` and the three
   versionCodes in both `versionCode:` and `peke.versionCode=`, then `CurrentVersion` and `CurrentVersionCode`
   (10N+3). Open a merge request.

## Submitting to F-Droid

Not done yet. When the first tag the recipe builds exists (v0.2.0, with this recipe's Gradle changes in it):

1. Fork https://gitlab.com/fdroid/fdroiddata on GitLab and branch from `master`.
2. Copy `fdroid/metadata/dev.aldoborrero.peke.yml` to `metadata/` and the two files in `fdroid/srclibs/` to `srclibs/`.
3. Run `fdroid lint`, `fdroid rewritemeta` and `fdroid build -v -l dev.aldoborrero.peke` (the latest entry) locally.
4. Push and open a merge request with the "App inclusion" template. The pipeline builds every entry on F-Droid's
   build server; answer the reviewers there.
5. F-Droid signs the APKs with its own key. Reproducible builds with the owner's key need the GitHub release APKs to
   be byte-identical to F-Droid's, which today they can't be: `BuildConfig.BUILD_TIME` changes on every build and the
   GitHub APKs share one versionCode across ABIs.
