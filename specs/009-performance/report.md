# 009 report: performance

Peke felt slow on a Chromecast with Google TV (sabrina: S905D2, Mali-G31, armeabi-v7a, 2 GB, Android 14) with a debug
build. This branch fixes what two read-only audits found, in the order of the brief. Builds on `008-canvas-screens`
(`0570708a`).

No numbers were measured on a device: no emulator or device was connected, and the benchmarks need Peke set up with a
kid signed in on the release package (`dev.aldoborrero.peke`), which needs a grown-up's credentials. The commands to
take them are under [Measuring](#measuring).

## 1. Benchmark build type

- `app/build.gradle.kts`: a `benchmark` build type: the release build (R8, resource shrinking) signed with the debug key,
  not debuggable, same application id as release. It builds without the MPV, FFmpeg and AV1 extensions (MPV stub, no
  decoders): the release-only check still fails a real release without them, but no longer runs for the benchmark and
  baseline-profile tasks (task names with `benchmark`, `nonminified` or `baselineprofile`).
- `AndroidManifest.xml`: `<profileable android:shell="true"/>`, so Perfetto and the profilers can attach to
  non-debuggable builds.
- `PekeApplication`: `Composer.setDiagnosticStackTraceEnabled` was on in every debug build, which makes every
  composition slower; it is now off unless built with `-Ppeke.composeDiagnostics=true` (`BuildConfig.COMPOSE_DIAGNOSTICS`).

A debug build stays slow by nature (ART runs it without most optimisations). Judge speed on `benchmark`:
`nix develop -c ./gradlew :app:assembleBenchmark` (APKs in `app/build/outputs/apk/benchmark/`).

## 2. Data

- Backdrops: `BackdropService.submit` returns at once in kids mode (kid pages draw no backdrop), so no 500 ms wait, no
  full-size download and no Palette pass. The series, movie, collection and episode pages launch it apart instead of
  awaiting it before their own loading; the collection grid doesn't send its focused item's backdrop for kids.
- Home rows: the names of the rows' parents (libraries, picks) are read in one `getItems(ids)` request instead of one
  `getItem` per row; a library row's title comes from the libraries already loaded, so `fetchDataForRow` no longer reads
  the item again (a pick still does, for the 404 that drops a removed pick). Row queries ask for one image per type and,
  outside paging, no total record count.
- Keep watching: resume and next up are read in parallel; the adjacent-episode request per next-up item no longer asks
  for images; the last-played cache isn't wiped every time the home's view model is created (it still expires after 2 h,
  on a user switch and per series on playback or watched changes).
- Home loading: a first load shows the home as soon as its first row arrives and fills the others in as they come, instead
  of waiting for all of them. A row only takes the initial focus once every row above it has loaded, so the focus still
  lands on the first row with cards. Coming back to the home (`LifecycleStartEffect`) only reads the keep watching, next
  up and continue watching rows again, not every row.
- Trickplay: the sheets prefetched at playback start go to the disk cache only, not the memory cache.

## 3. UI

- Sidebar (`NavigationDrawerAndroid.kt`): the animated width and side padding are read in the layout phase, so the open
  and close animation no longer recomposes the whole sheet each frame; the scope object is remembered.
- Time left: `KidsTimeLimits` still ticks every second, but the header pill and the sidebar's profile read a minute-level
  `HomeTimeLeft` (`distinctUntilChanged`), and `KidsTimerEffects` reads `expired`, the shown minutes and `nearEnd` through
  `derivedStateOf`. The bar's fill is drawn in `drawBehind` from a lambda; its look and behaviour are the same (fill from
  the start edge, growing from the first warning point).
- Episode rows (`KidsSeriesOverviewContent`, `SeriesOverviewContent`): the focused episode is reported from
  `onFocusChanged` instead of a state write during composition, and the kids row's restore index is read once per list,
  so moving along the row doesn't recompose every card.
- Home (`HomePage.kt`): the focus position is passed as a provider; the focused item is derived and read only by the
  header (and the backdrop, through `snapshotFlow`, never for kids), so a D-pad step no longer recomposes the page. The
  kids header never reads it.
- Kids cards (`KidsCards.kt`): the focus is read only by the title under the card (colour, weight and its slide), so a
  focus change recomposes the title, not the card and its picture.
- Focus ring (`Focus.kt`): the border and ring paths and strokes are kept in the node and made again only when the size,
  direction, density or shape change, instead of on every frame of the focus animation.

## 4. Lazy lists

`contentType` on the home's rows (by kids card kind, error, loading), `ItemRow`'s cards and view-more card, the episode
row and the card grid; a key (the episode id) on the episode row. No keys on the home rows, `ItemRow` or the grid: rows
are rebuilt as a new list when the settings change, a row may hold the same item twice (a duplicate key crashes a lazy
list), and the grid pages in placeholders whose key would change on load and drop the focus.

## 5. Baseline profile

- New module `:baselineprofile` (`com.android.test` + `androidx.baselineprofile` 1.5.0, Macrobenchmark 1.5.0,
  UiAutomator 2.4.0), all in that test module; the app only gains the plugin and `baselineProfile(project(...))`
  (`profileinstaller` was already there).
- `BaselineProfileGenerator`: cold start → kids home → along the first row and back → open and close the sidebar → down
  to the series row (`seriesRow`, default 1, the row under keep watching) → open a series → back. D-pad only, never the
  parent gate.
- `KidsBenchmarks`: cold start and scrolling the home's first row, without compilation and with the baseline profile.
- The app is set to `mergeIntoMain = true`: the generated profile is committed as
  `app/src/main/generated/baselineProfiles/baseline-prof.txt` (and `startup-prof.txt`), so every build type ships it.
  Nothing is generated yet.

Size: the dependencies are test-only; the shipped APK changes only by the profile file once generated (tens of KB).

## 6. Not done

The Nav3 crossfade and the drawer dim (`graphicsLayer` alpha vs a scrim) were to be tried only with measurements; with
no device they were left as they are.

## Measuring

On the emulator or the Chromecast, connected over adb, with Peke installed from the variant below, set up, and a kid
signed in (the benchmark and profile builds share the release package and are signed with the debug key, so a release
install signed with another key has to be removed first):

```sh
# baseline profile (installs nonMinifiedRelease); writes app/src/main/generated/baselineProfiles/
nix develop -c ./gradlew :app:generateBaselineProfile
# if the series aren't on the second row of the kids home:
nix develop -c ./gradlew :app:generateBaselineProfile -Pandroid.testInstrumentationRunnerArguments.seriesRow=2

# start-up and home scrolling, without and with the profile (installs benchmarkRelease)
nix develop -c ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

The results stay local: `baselineprofile/build/outputs/connected_android_test_additional_output/`.

## Checks

- `nix develop -c ./gradlew :app:testDebugUnitTest`: passing after each step.
- `nix develop -c pre-commit run --files …` (ktlint 1.8.0): passing.
- `:app:assembleBenchmark`, `:app:assembleNonMinifiedRelease`, `:app:assembleBenchmarkRelease` and the
  `:baselineprofile` APKs build without the extensions.
