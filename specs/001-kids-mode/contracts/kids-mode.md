# Contracts: kids mode (internal)

The app exposes no external API. These are the internal contracts that shared code and kids UI
rely on, and they are the only surface shared code may use.

## KidsModeService (`kids/KidsModeService.kt`, @Singleton)

```kotlin
val state: StateFlow<KidsModeState>          // Inactive | Active(profile, user)
val isActive: Boolean                         // state.value is Active
val locked: StateFlow<Boolean>                // device kids lock (fail-closed)
suspend fun setKidProfile(user: JellyfinUser, isKid: Boolean)
suspend fun updateProfile(profile: KidProfile)
suspend fun kidUserRowIds(): Set<Int>
suspend fun unlock()                          // only after strong passcode; clears kids lock
```

Guarantees:

- `Inactive` whenever the current user has no `kid_profiles` row. When inactive, shared code
  MUST behave exactly as before.
- When a kid profile becomes active, `locked` becomes true and is persisted.

## ParentPasscodeStore (`kids/gate/ParentPasscodeStore.kt`)

```kotlin
suspend fun isSet(): Boolean
suspend fun set(newPasscode: String, current: String?): Boolean  // false if current wrong / invalid
suspend fun verify(candidate: String): Boolean                   // constant-time
```

## KidsTimerService (`kids/timer/KidsTimerService.kt`, @Singleton)

```kotlin
val state: StateFlow<KidsTimerState>      // Idle | Running(remaining, total) | Expired
val warnings: SharedFlow<Duration>        // emits 5.minutes, 1.minutes
fun start(minutes: Int); fun addTime(minutes: Int); fun stop(); fun end()
fun onForeground(); fun onBackground()    // called from MainActivity onResume/onPause
val lastMinutes: Int
```

## UI contracts

- `LocalKidsMode: KidsModeState`, provided at the root of `MainContent`.
- `ParentGatePanel(mode: Light | Strong, onPassed, onDismiss)`, an end-edge panel. Strong mode
  verifies with `ParentPasscodeStore`. Remote number keys and the on-screen keypad are
  equivalent.
- `KidsParentMenu`, shown after the light gate:

  | Option | Needs strong passcode |
  |---|---|
  | Timer start/update/stop/add | No |
  | Kids settings | Yes |
  | Leave kids mode | Yes |

- `Destination.KidsTimesUp` is full screen. Its only focusable control is the lock, which opens
  the gate → add time / end timer.
- `Destination.KidsSettings` is full screen, reached only through the strong gate or from adult
  Settings.

## Shared-code choke points (each: `if kids active → kids behavior, else unchanged`)

| Location | Kids behavior |
|---|---|
| `HomeSettingsService.loadCurrentSettings` | `updateCurrent(userId, KidsHomeRows.build(...))` |
| `HomePage` (`showViewMore`, `onLongClickItem`) | no view more, no long-press |
| `NavDrawerService` `_state.update` / `NavDrawer` items | Home, My stuff, Search (if allowed), lock |
| `NavDrawer` BackHandler | Back on Home with drawer open → light gate |
| `ContextMenuDialog` | no menu |
| `ExpandablePlayButtons` | hide watched, delete, more, remote trailers |
| `PlaybackPage` `PlaybackSettings` | no subtitle download, no speed, no debug |
| `PlaybackViewModel` autoplay | kids policy + cap + `KidsEndScreen` |
| `DestinationContent` playback backend | never external player |
| `MainActivity.handleIntent` | ignore non-MAIN intents |
| `UserSwitchListener` | skip Seerr login |
| `SwitchUserViewModel.getUsers` | only kid profiles while locked |
| `MainContent` `LocalInterfaceCustomization` | no rating toggles |
