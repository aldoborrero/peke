# Research: Wholphin for Kids (kids mode v1)

Phase 0 output for [plan.md](plan.md). All paths are relative to
`app/src/main/java/com/github/damontecres/wholphin/` unless stated otherwise. Findings come from a
code survey of `main` at `621686d8`.

## R1. Where kids-mode state lives and how shared code sees it

- **Decision:** A `@Singleton` `KidsModeService` in package `kids` derives the active kid profile
  by combining `ServerRepository.currentUserFlow` with the `kid_profiles` Room table. It exposes
  `state: StateFlow<KidsModeState>`, where the state is `Inactive` or `Active(profile)`.
  - Shared ViewModels and services inject it directly.
  - Compose reads it through `LocalKidsMode`, a `staticCompositionLocalOf` provided in
    `MainContent.kt` next to `LocalInterfaceCustomization`.
  - When inactive, every shared call site behaves exactly as today.
- **Rationale:**
  - The codebase has no `@Binds` interfaces. Services are concrete `@Singleton @Inject`
    classes, and there is a CompositionLocal pattern for UI config (`LocalInterfaceCustomization`).
  - Per-profile kids mode (clarification Q1) means the kids implementation always exists, so a
    no-op interface implementation adds nothing.
  - One boolean-ish read per call site keeps the upstream diff small (constitution II).
- **Alternatives considered:**
  - A `KidsPolicy` interface with a default implementation. Rejected: there is no second
    implementation, and it's not the house style.
  - Sprinkling `currentUser.isKid` checks. Rejected: it scatters logic and is harder to test.

## R2. Persisting kid profiles

- **Decision:** New Room entity `KidProfile`, table `kid_profiles`.
  - The PK `userId` is `users.rowId`, with an FK that cascades on delete. This mirrors
    `RememberedTab` and `SeriesTrackChoice`.
  - `AppDatabase` goes from version 40 to 41 via `AutoMigration(40, 41)`.
  - The exported schema `app/schemas/.../41.json` is committed.
  - List fields (picked item ids, section order) are stored as JSON strings with a new
    `@TypeConverter`, following the existing `GetItemsFilter` JSON converter.
- **Rationale:** This is the established per-user pattern. A new table needs no hand-written
  migration and leaves `users` untouched.
- **Alternatives considered:**
  - Columns on `users`. Rejected: they widen a core entity for a niche feature.
  - `KeyValueService` JSON. Rejected: it's not relational, and there's no cascade when a user
    is removed.

## R3. Parent passcode storage

- **Decision:** One passcode per device. It is stored in the existing `DataStore<Preferences>`
  "key_value" store under a global key, as PBKDF2WithHmacSHA256 with a random 16-byte salt and
  120k iterations, using `javax.crypto` (platform API, no new dependency). Verification uses a
  constant-time compare. A `PasscodeHasher` object is unit-tested.
- **Rationale:**
  - FR-008 requires a non-reversible verifier.
  - The codebase has no crypto utilities, and the platform provides PBKDF2.
  - Device scope matches the "parent of this TV" model.
- **Alternatives considered:**
  - Reusing the profile PIN. Rejected in clarification Q2, and that PIN is plaintext.
  - Android Keystore. Rejected: overkill for a verifier.

## R4. Session timer that the clock can't extend and that survives restarts

- **Decision:** `KidsTimerService` (`@Singleton`) with states `Idle`, `Running(remaining)` and
  `Expired`.
  - **Time source:** elapsed time is measured with an injected `MonotonicClock` (default
    `SystemClock.elapsedRealtime`), so changing the wall clock has no effect.
  - **Counting:** it counts only while the activity is resumed, via hooks in
    `MainActivity.onResume`/`onPause`.
  - **Persistence:** it checkpoints remaining time and state to the key_value DataStore every
    15 s and on pause. On process start it restores the checkpoint, and a reboot can only lose
    up to 15 s of counted time, which never adds time.
  - **Expiry:** `Expired` is persisted and survives restarts.
  - **Scope:** one timer per device. It applies to every kid profile, because switching between
    kids must not reset it.
  - **Warnings:** emitted as events at 5 min and 1 min.
- **Rationale:**
  - It meets FR-013 to FR-016 and SC-004/SC-005.
  - The codebase has no clock abstraction, and `LocalClock` is UI-only, so a small injected one
    makes the logic unit-testable.
- **Alternatives considered:**
  - WorkManager or AlarmManager. Rejected: not needed for a foreground-only timer, and the app
    can't play in the background anyway.
  - A per-kid timer. Rejected: kid-to-kid switching would reset it. The spec's Key Entities line
    was adjusted to "per device".

## R5. Stopping playback on expiry

- **Decision:** A small collector, `KidsTimerEffects`, composed once in `ApplicationContent`,
  watches the timer.
  - **On `Expired`:** it calls `navigationManager.replace(Destination.KidsTimesUp)`. Replacing
    the back stack disposes `PlaybackPage`, whose `LifecycleStartEffect` already calls
    `viewModel.release()`. That reports the stop through
    `TrackActivityPlaybackListener.release()` and releases the player. The same path is used for
    the server's `PlaystateCommand.STOP` (`PlaybackViewModel.kt:1466`).
  - **On a warning:** it shows an overlay toast and plays a short `ToneGenerator` beep. It ducks
    by setting `playerFactory.currentPlayer?.volume` to 0.2 for the beep and then restoring it.
    Audio focus can't be used because the video player has `handleAudioFocus=false` (see
    PlayerFactory.kt:159). The volume is set only when `COMMAND_SET_VOLUME` is available, which
    covers MPV.
- **Rationale:** It reuses the existing release-and-report path, needs no new playback code, and
  needs no audio asset (constitution: nothing copied from YouTube Kids).
- **Alternatives considered:**
  - Having the ViewModel collect the timer. Rejected: that path would only work during playback,
    and the time's-up screen must also appear on browse screens.

## R6. Kids home rows

- **Decision:** `KidsHomeRows.build(profile, libraries)` produces a `HomePageSettings`. In
  `HomeSettingsService.loadCurrentSettings(userId)`, when kids mode is active, it calls the
  existing `updateCurrent(userId, settings)`. That hook resolves the settings in memory and never
  saves them. Rows, in the order and visibility the profile sets:

  | Section | Row type |
  |---|---|
  | Keep watching | `ContinueWatchingCombined` |
  | Picked by your grown-ups | `ByParent(pickedId)`, one row per pick |
  | Libraries | `ByParent(libraryId, sort = SortName asc)`, one row per accessible library |
  | New | `RecentlyAdded(parentId = null)`, or per library if a null parent isn't supported |
  | My favourites | `Favorite(SERIES)` and `Favorite(MOVIE)` |

  - **Card size:** kids rows get larger `HomeRowViewOptions` (`heightDp` +25 %, `showTitles = true`).
  - **One page per row:** this already holds, because home calls `fetchDataForRow` with
    `limit = maxItemsPerRow`.
  - **Empty and 404 rows:** already hidden (`HomePage.kt:405`, `HomeViewModel.kt:199`).
  - **"View more":** turned off by passing `showViewMore = !kids` at `HomePage.kt:227`.
- **Rationale:** It uses Wholphin's own row engine and fetch code without changes (constitution
  I) and covers FR-003a/b. Picks the kid can't access return 404 or empty and are hidden, which
  covers FR-003c's "limited to accessible items" at display time.
- **Alternatives considered:**
  - A separate kids HomePage. Rejected: it duplicates UI.
  - Persisting kids settings to the server's DisplayPreferences. Rejected: they would leak into
    the user's other clients.

## R7. Navigation, back and escape routes

- **Decision:**
  - **NavDrawer** (`ui/nav/NavDrawer.kt`): in kids mode it renders Home, My stuff, Search (only
    when `profile.searchAllowed`), and a Grown-ups lock item last. The lock never gets initial
    focus. ProfileIcon, NowPlaying, library items, More and Settings are hidden. Library items are
    filtered at `NavDrawerService`'s `_state.update` (:245). The hardcoded items get `if (!kids)`.
  - **My stuff:** reuses `Destination.Favorites`, with context menus suppressed (below).
  - **Top-level Back:** in kids mode, a `BackHandler` in `NavDrawer` for the "drawer open on
    Home" case shows the parent gate instead of falling through to the activity (FR-010).
  - **Context menus:** an early return in `ContextMenuDialog` (`ui/components/ContextMenu.kt:145`)
    when kids mode is active removes all long-press menus at once (18 call sites, one change).
    Long-press on home becomes a no-op.
  - **Detail action row:** `ExpandablePlayButtons` (`ui/components/PlayButtons.kt:76`), shared by
    movie, episode and series footer, hides mark-watched, delete, "more" and remote trailers in
    kids mode. Local trailers stay.
  - **Playback:** `PlaybackSettings` is built in one place (`PlaybackPage.kt:741`) and gets
    `hasSubtitleDownloadPermission = false` and `playbackSpeedEnabled = false`. The debug option is
    hidden. In `DestinationContent.kt:83-114` the external player is never chosen in kids mode.
  - **Intents:** `MainActivity.handleIntent` (:488-544) ignores non-MAIN intents in kids mode.
  - **Seerr:** `UserSwitchListener` skips Seerr login for kid profiles, so Discover never
    activates.
  - **Remote trailers** (`TrailerService`, which opens YouTube URLs outside the app) are hidden.
- **Rationale:** These are single choke points found in the survey, each a one- or two-line
  condition (constitution II).
- **Alternatives considered:**
  - A separate kids NavDisplay. Rejected: it duplicates navigation.

## R8. User switcher and "leave kids mode"

- **Decision:**
  - A persisted device flag, `kidsLock`, is set when a kid profile becomes active and cleared
    only by "Leave kids mode" after the strong passcode.
  - While `kidsLock` is set, `SwitchUserViewModel.getUsers` returns only kid profiles. The
    "add user/server" options are hidden behind the gate.
  - On launch, a kid profile has no PIN, so `restoreLastSession` restores it directly (FR-001,
    Story 1.1).
  - "Leave kids mode" clears `kidsLock` and navigates to `SetupDestination.UserList`, where every
    user is listed and existing PIN/login protection still applies (Story 2.6).
- **Rationale:** It reuses the switcher and the restore flow. Adult protections stay untouched.
- **Alternatives considered:**
  - Hiding the switcher completely. Rejected: siblings need to switch between kid profiles.

## R9. Parent gate UI

- **Decision:** A new `ParentGatePanel` composable in `kids/ui`.
  - It is an end-edge panel over a 60 % scrim. It contains an on-screen 3×4 keypad of
    tv-material3 `Button`s and also accepts remote number keys, using the key mapping from
    `PinEntry.kt`.
  - **Light gate:** `ArithmeticChallenge.next(random)`, with factors 2–9 and a product above 9.
  - **Strong gate:** passcode entry with dots, reusing `PinEntryDots`.
  - The panel then shows the parent menu: timer controls, Kids settings, and Leave kids mode,
    where the last two need the passcode.
- **Rationale:** `PinEntry` has no on-screen keypad or backspace, and its title takes only a
  resource id. A keypad is required for D-pad-only use (FR-006). Pieces are reused where they fit.
- **Alternatives considered:**
  - Extending `PinEntry` in place. Rejected: its contract (D-pad direction PINs) differs, and
    changing it risks the adult flow.

## R10. Kids settings screen

- **Decision:** A new full-screen `Destination.KidsSettings`, built on the existing preference
  composables (`SwitchPreference`, `ClickPreference`, `SliderPreference`). It is reachable:
  - from the parent menu (strong passcode)
  - from regular Settings via an `AppDestinationPreference` "Kids mode". The first time, it
    starts by creating the passcode.

  Contents:
  - device users, each with a "kid profile" toggle
  - per kid: search allowed, autoplay toggle and cap (1–10), picked collections/playlists
    (reusing `SearchForDialog` with `BOX_SET`/`PLAYLIST`), and section order and visibility
    (up/down list following `HomeRowConfigContent`'s pattern)
  - the passcode change
  - a "server restrictions" summary from `currentUserDto.policy`, shown only when the session is
    the kid's own. Otherwise only the recommendations are listed (spec edge case: the policy can't
    be read).
- **Rationale:** It reuses the preference UI and the picker without new UI primitives.
- **Alternatives considered:**
  - Editing server policy from the app. Out of scope: it needs admin credentials (constitution
    III).

## R11. Autoplay cap and end screen

- **Decision:** `PlaybackViewModel` gains `autoPlayCount`.
  - A new `autoPlayNextUp()` is called from the countdown branch (`PlaybackPage.kt:662`). It
    increments the count, and the count resets on any `reportInteraction()`.
  - `shouldAutoPlayNextUp()` additionally requires kids `autoplayEnabled` and
    `autoPlayCount < cap` when kids mode is active.
  - In the `STATE_ENDED` branch (VM:1081-1122), kids mode with autoplay not allowed shows a new
    `KidsEndScreen` ("Watch again" / "Home") instead of `goBack()` or the next-up card.
  - A pure function `KidsAutoplayPolicy.allowNext(enabled, cap, count)` is unit-tested.
- **Rationale:** It is the minimal change at the points the survey identified. Inactivity
  protection (FR-018) is kept, because it stays in the same predicate.

## R12. Presentation

- **Decision:**
  - Kids mode keeps the user's existing Wholphin color theme. All themes are already dark and
    calm, so no new palette is added in v1.
  - Larger cards come from the kids `HomeRowViewOptions`.
  - The header hides ratings by providing `LocalInterfaceCustomization` with no display toggles
    in kids mode.
  - Drawer entries already have icons.
- **Rationale:** It satisfies FR-019 with zero new theme code (YAGNI).

## R13. Testing approach

- **Unit tests** (JUnit4 + MockK, `StandardTestDispatcher` as in `ServerRepositoryTest`):
  - `PasscodeHasher`
  - `ArithmeticChallenge`
  - `KidsTimerService`, with a fake `MonotonicClock` and an in-memory DataStore
  - `KidsHomeRows`
  - `KidsAutoplayPolicy`
  - `KidsModeService` state derivation
  - switcher filtering
- **Migration:** AutoMigration 40→41 is validated by Room schema export at build time, plus a
  test in `TestDbMigrations.kt` (androidTest) following the existing helper.
- **Robolectric:** a UI smoke test (`BasicUiTests` style) checks that the drawer in kids mode
  shows only the kids entries.
- **Hilt test modules:** new `@Provides` go in a new `KidsModule` so that `TestModule`, which
  replaces `AppModule`/`DatabaseModule`, only needs the new DAO.
