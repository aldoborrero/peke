# Peke developer's guide

See [CONTRIBUTING.md](CONTRIBUTING.md) for how to propose a change, and [AGENTS.md](AGENTS.md) for the rules AI
agents follow in this repository.

## Overview

Peke is a kids-only Android TV client for Jellyfin, forked from [Wholphin](https://github.com/damontecres/Wholphin).
It is written in Kotlin and talks to the server through the official
[Jellyfin Kotlin SDK](https://github.com/jellyfin/jellyfin-sdk-kotlin). Household rules (kid profiles, the parent
code, screen time, live TV channels) come from the [Jellyfin Kids](https://github.com/aldoborrero/jellyfin-kids)
server plugin through its `/Kids` API.

The app is a single activity (`MainActivity`) with an MVVM architecture. It uses:

- [Compose for TV](https://developer.android.com/training/tv/playback/compose) for the UI
- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) for navigation
- [Room](https://developer.android.com/training/data-storage/room) and
  [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) for local data
- [Hilt](https://developer.android.com/training/dependency-injection/hilt-android) for dependency injection
- [Media3/ExoPlayer](https://developer.android.com/media/media3/exoplayer) and [MPV](https://github.com/mpv-player/mpv)
  for playback
- [Coil](https://coil-kt.github.io/coil/) for images and [OkHttp](https://square.github.io/okhttp/) for HTTP

## Development environment

Everything runs from the Nix devshell (`direnv allow`, or `nix develop`). It provides the JDK, the Android SDK, adb,
ktlint and the other tools listed in `nix/devshell.nix`.

```sh
nix develop -c ./gradlew assembleDebug           # build the debug APKs
nix develop -c ./gradlew testDebugUnitTest       # unit tests (JUnit4, Robolectric, MockK)
nix develop -c ./gradlew compileDebugAndroidTestKotlin
nix fmt                                          # treefmt: ktlint, nixfmt, deadnix, actionlint
nix flake check                                  # includes the format check
```

Android Studio works too: open the project after `direnv allow` so it picks up the same SDK. Code follows
[ktlint](https://github.com/pinterest/ktlint) 1.8.0, the version in `.pre-commit-config.yaml` and the devshell.
[pre-commit](https://pre-commit.com) runs ktlint and a few other checks on each commit.

### Playback extensions

MPV and the Media3 FFmpeg and AV1 decoders come from
[wholphin-extensions](https://github.com/damontecres/wholphin-extensions) on GitHub Packages. The README explains the
`read:packages` token they need. You can also build them locally and put the AARs in `app/libs`; Gradle prefers those
files over the remote ones.

Without the extensions, debug builds use the `wholphin-mpv-stub` module, which compiles but throws when MPV is used,
and leave the decoders out. Release builds refuse to build without them, since Peke plays files directly instead of
asking the server to transcode.

The `com.github.damontecres.wholphin.mpv` package is the API of that external library. Its native code binds those
class names, so the package keeps Wholphin's name while the rest of the app lives under `dev.aldoborrero.peke`.

## Code organization

Everything is under `dev.aldoborrero.peke`:

- `kids` - Peke's own code: kid profiles and the plugin client (`server`), the parent gate (`gate`), the kids' home
  and live TV (`home`, `livetv`), screen time (`timer`), autoplay and refused playback (`playback`), the kids
  database (`data`) and its screens (`ui`)
- `data` - app data models and the Room database for servers, users and display settings
- `preferences` - settings that aren't UI code
- `services` - Hilt-injected services, often used by ViewModels for API calls
- `ui` - screens and ViewModels
- `util` - utility classes and functions

Specifications for each feature are in `specs/`, written with [spec-kit](https://github.com/github/spec-kit). The
project constitution in `.specify/memory/constitution.md` sets the rules every feature follows.

## Settings

Peke stores settings in four places.

### App preferences

Settings that apply to the whole app, whatever the current user. They live in `AppPreferences`, a proto DataStore
defined in `app/src/main/proto/PekeDataStore.proto`, and are read through `UserPreferencesService` or by injecting
`DataStore<AppPreferences>`. The `AppPreference` objects in `AppPreference.kt` build the settings screens with the
composables in `dev.aldoborrero.peke.ui.preferences`.

Only grown-ups see Settings. A kid's options (autoplay, search, home sections) come from their plugin profile, not
from these preferences.

To add an app setting:

1. Add the field to `PekeDataStore.proto` and build to generate the classes.
2. Add an `AppPreference` object in `AppPreference.kt` and put it in a `PreferenceGroup`.
3. Set its default for new installs in `AppPreferencesSerializer`. Proto3 defaults are zero, false or the first enum
   value, so this is only needed when the default is something else.

### Room

`AppDatabase` holds servers, users, per-library display settings and playback state. Some values, such as
`GetItemsFilter` or `ViewOptions`, are stored as JSON in a column; use that sparingly.

`KidsDatabase` (`peke_kids`) holds what Peke caches from the plugin: kid profiles and the plugin's state for each
server. It is a separate database, and `KidsDataCleaner` removes kids data when a server or user goes away.

### Key-value settings

Small per-user values saved as JSON `@Serializable` objects through `KeyValueService`. Use sparingly.

### Experimental

Settings that may be unstable or need more testing live in `ExperimentalPreferences` inside `AppPreferences`, and
may change or go away at any time. Check `ExperimentalPreferences.enabled` before reading them; there are helper
functions for that.
