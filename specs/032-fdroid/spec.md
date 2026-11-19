# Feature Specification: F-Droid build recipe

**Feature Branch**: `032-fdroid`

**Created**: 2026-11-17

**Status**: Implemented (not submitted)

**Input**: "Make Peke buildable by F-Droid's official build server, so it can later be submitted to f-droid.org. Nix
stays the way the owner builds, tests and releases Peke." The extensions (MPV, Media3 FFmpeg and AV1 decoders) come
prebuilt from GitHub Packages, which needs a token; F-Droid builds everything from source and has no token.

## Decisions

- **D1. Recipe in the repo for now.** `fdroid/metadata/dev.aldoborrero.peke.yml` in fdroiddata format, with the two
  srclibs fdroiddata lacks in `fdroid/srclibs/`. It moves to fdroiddata when the app is submitted.
- **D2. Extensions from srclibs.** Every native dependency is a srclib at the version wholphin-extensions pins (Lua, which
  has no git repository, is a tarball checked against a SHA-256). A script in the repo, `fdroid/build-extensions.sh`,
  lays them out (prebuild) and builds the three AARs into `app/libs/` (build), reusing wholphin-extensions' and
  androidx/media's own build scripts. It fails when a srclib's version differs from what wholphin-extensions pins.
- **D3. One build entry per ABI**, building libmpv only for that ABI; versionCode `10 × tags + ABI` (1 armeabi-v7a,
  2 arm64-v8a, 3 x86_64), passed with `-Ppeke.versionCode`. Without the property Gradle counts tags as before, so the
  Nix and CI builds don't change.
- **D4. No in-app updater in F-Droid builds**: `-Ppeke.updateChecker=false` sets `BuildConfig.UPDATING_ENABLED` to false.
  Default true, as before.
- **D5. Manual updates.** `AutoUpdateMode: None`, `UpdateCheckMode: None`: the versionCode isn't in any file
  checkupdates could read. Each release is a merge request to fdroiddata.
- **D6. No anti-features.** Jellyfin is free software (not NonFreeNet); no tracking or non-free dependencies; crash
  reports go only to the household's own Jellyfin server.
- **D7. F-Droid signs.** Reproducible builds with the owner's key would need byte-identical GitHub APKs, which the
  build time in `BuildConfig` and the shared per-ABI versionCode rule out today.
- **D8. One NDK.** F-Droid installs one NDK per entry, so the Media3 JNI glue builds with NDK 29
  instead of AGP's default 28.2 (`ndkVersion` and `ndkPath`), like FFmpeg, dav1d and libmpv.

## Requirements

- **FR-001**: `fdroid lint` and `fdroid rewritemeta` MUST be clean on the recipe.
- **FR-002**: `fdroid scanner` MUST pass on the source tree after the recipe's prebuild.
- **FR-003**: Each build entry MUST produce its ABI's release APK with `libmpv.so`, `libffmpegJNI.so` and
  `libdav1dJNI.so`, without GitHub credentials and without prebuilt AARs.
- **FR-004**: Builds without `peke.versionCode` and `peke.updateChecker` MUST be unchanged.
- **FR-005**: `fastlane/metadata/android/{en-US,es-ES}/` MUST carry title, descriptions, changelogs for the current
  versionCodes, icon, TV banner and TV screenshots, and say plainly that Peke is a kids-only Android TV app that needs a
  Jellyfin server with the Kids plugin.
- **FR-006**: `fdroid/README.md` MUST explain the recipe, local testing, bumping wholphin-extensions and submission.

## Out of scope

Submitting to F-Droid, app code and UI, the CI release workflow, the Nix devshell's behaviour.
