# Research: Security follow-ups

Paths are relative to `app/src/main/java/dev/aldoborrero/peke/` unless they start with `app/` or `specs/`. Each
decision was checked against the code on `005-security-followups` (004 plus nothing else).

## R1. HTTPS first for a bare host, and when the HTTP warning shows (FR-014)

- **Facts:**
  - `kids/setup/SetupFlow.kt` `SetupServerLookup.find(address)` takes the best `getRecommendedServers(address)`
    result: `sortedBy { it.score }` (enum order `GREAT, GOOD, OK, BAD`), first that isn't `BAD`.
  - The SDK 1.7.1 `AddressCandidateHelper` (checked with `javap` in the debug AAR) prefixes a bare host with
    `http://`, then adds an `https://` candidate and the 8096/8920 port variants, and sorts the candidates by its own
    score. For an `http://` input it also adds the `https://` variant. `RecommendedServerIssue.SecureConnectionFailed`
    marks a TLS failure.
  - So today the choice between an `http://` and an `https://` candidate with the same score depends on the SDK's
    internal order, which isn't a contract.
- **Decision:** a pure function `SetupServerLookup.pick(results)` chooses: among the non-`BAD` results whose system
  info says a set-up Jellyfin server, any `https://` one first (best score among them), else the best `http://` one.
  `find()` returns `ServerLookup.Found(server, insecure = url is http)`.
  - A typed `http://host` that also answers on HTTPS ends up on HTTPS, with no warning. That is stricter than the
    owner's rule and needs no extra decision.
  - A server found by local discovery (`discover()` gives `http://ip:8096`) is looked up through `find()` like a
    typed address, so it gets the HTTPS upgrade when available, and the warning otherwise.
- **Warning flow:** `addServer()` stops after `find()` when `insecure`, and shows `SetupScreen.Server(httpWarning =
  server)`. "Use it anyway" goes on to the plugin check with that server; "Back" clears it and keeps the address
  field. No other state is stored: confirming the warning isn't remembered, because the setup row is only written
  after the plugin check. A warm start mid-setup restarts at the stored step, which is past the server step.
- **This TV:** a read-only row in the PEKE preferences screen shows the server address, with "Not encrypted" when the
  URL is `http://`. It reads `serverRepository.current` / the setup row.
- **Alternatives considered:** sorting by the SDK's score only (rejected: not a contract); asking the grown-up to
  choose between HTTP and HTTPS candidates (rejected: one more decision nobody needs).

## R2. Trust anchors stay as they are (FR-015)

- **Facts:** `app/src/main/res/xml/network_security_config.xml` allows cleartext and trusts `system` and `user`
  anchors; `AndroidManifest.xml` also sets `usesCleartextTraffic="true"`.
- **Decision:** no change to the file, per the owner's answer (Q2: A, Q1: A keeps HTTP). Add a comment in the XML
  naming the decision and spec 005, so a later review doesn't "fix" it blindly. No HTTPS-to-HTTP fallback exists
  after setup: the stored URL's scheme is used as is by the SDK client, `KidsApi`, Coil, ExoPlayer and MPV.
- **Alternatives considered:** dropping `user` anchors (rejected by the owner); per-host cleartext (impossible to
  express for a host chosen at runtime in a static config).

## R3. An empty or partial kids list never removes kids by itself (FR-016)

- **Facts:** `kids/data/KidProfileRepository.kt` `refreshServer()` deletes the profile, marks the row not-a-kid and
  calls `forgetUser()` (sign-out and row removal) for every row missing from `/Kids/Profiles`. `refresh(server,
  user)` already asks `/Kids/Users/{id}/Profile` with the user's own token, and removes only on a definite
  `KidsResult.NotAKid` while `pluginService.confirmAvailable(server)` holds (`deleteIfNotAKid`).
- **Decision:** in `refreshServer()`, a row missing from the list goes through `refresh(server, user)` instead of
  being deleted. Rows without a token are kept, as `refresh` already does. The 004 cleanup of non-kid rows (an
  administrator left by an older version) still happens: the plugin answers `NotAKid` for them.
- **Limit, stated honestly:** this protects against a glitchy or empty list and a lazy forgery. A full
  man-in-the-middle on plain HTTP can also forge the per-kid `NotAKid` answers. HTTPS is the real protection, and the
  warning of R1 tells the grown-up so.

## R4. The Play key follows the click rule (FR-001, FR-002)

- **Facts:**
  - `ui/main/HomePage.kt:203-207`: `onClickPlay` always navigates to `Destination.Playback(item)`.
  - `:142-149`: `onClickItem` is a `remember {}` lambda capturing `homeRows` (a plain `val` at `:118`) from the
    first composition. `onLongClickItem` has the same capture.
  - `ui/components/ItemGrid.kt:231`, `RecommendedContent.kt:428`, `CollectionFolderView.kt:1002/1023`
    (`gridActions.onClickPlayRemoteButton`), `SearchPage.kt:506` and `CollectionDetails.kt:140` wire the grid Play
    key straight to playback. Others pass `{ _, _ -> }`.
- **Decision:**
  - HomePage: `val currentRows by rememberUpdatedState(homeRows)` used by every remembered lambda. In kids mode,
    `onClickPlay` navigates to `currentRows.getOrNull(row).clickDestination(item, clickToPlay, kidsMode = true)`,
    the same function OK uses.
  - Grids: one helper `kidsPlayKeyDestination(item): Destination` (= `item.destination()`, the details page) used by
    every kid-reachable `onClickPlay` above when `LocalKidsMode.current.isActive`. Music-only pages
    (`PlaylistDetails`, `SongDetailsPage`, `ArtistDetailsPage`, `AlbumDetailsPage`, `NowPlayingPage`) aren't reachable
    by kids (`KidsItemTypes.refuses`) and are left alone.
  - `SeriesOverviewContent.kt:302` (Play on an episode of a series page) is already on a details page and stays.
- **Alternatives considered:** refusing `Destination.Playback` for kids in `NavigationManager` unless it comes from
  a details page (rejected: implicit, and the details pages' own Play buttons use the same destination).

## R5. Direct play swaps only its own loading page (FR-003, FR-004)

- **Facts:** `kids/playback/KidsDirectPlay.kt` `play()` resolves on IO, then calls `navigationManager.goBack()` and
  `navigateTo(Playback)` unconditionally. The view model is scoped to the nav entry
  (`rememberViewModelStoreNavEntryDecorator`), but the store is cleared after the exit transition, so a result that
  lands in that window still navigates. With time's up on top, `goBack()` pops the time's-up page or is a no-op.
- **Decision:** `NavigationManager.replaceTop(expected: Destination, with: Destination?): Boolean`, synchronized:
  if `backStack.lastOrNull() == expected` it removes it and adds `with` (when not null) and returns true; otherwise it
  changes nothing and returns false. `play()` calls it with its own destination and shows the "nothing to play" toast
  only when it returned true.
- **Alternatives considered:** cancelling the job on dispose of the page (kept as a side effect of the VM scope, but
  not enough alone because of the transition window).

## R6. The debug overlay never shows for kids (FR-012)

- **Facts:** `ui/playback/PlaybackPage.kt:183` seeds `showDebugInfo` from the pref; `:299` toggles it only with
  `advancedPlayback`; `:499` passes the raw value to the overlay; `:772` gates the dialog.
- **Decision:** seed it with `prefs.showDebugInfo && advancedPlayback` and pass `showDebugInfo && advancedPlayback` at
  `:499`. `advancedPlayback` is false for every kid (`kidsInterfaceCustomization` restricts every feature but search).

## R7. Stop screens focus a non-actionable message (FR-013)

- **Facts:** `kids/ui/KidsTimesUpPage.kt` `KidsGrownUpsMessage` requests focus on the Grown-ups button. It is the
  only kid screen that focuses a gate control by default: the picker focuses the first kid, `NoKidsPage` focuses
  nothing, the sidebar's avatar and Grown-ups are reached by opening the drawer.
- **Decision:** the title text sits in a focusable, non-clickable container that takes the initial focus and draws
  the standard focus border (so the kid sees where focus is); Down moves to Grown-ups (`focusProperties { down = … }`
  plus the column's natural order). OK on the title does nothing. Relying on "nothing focused" as `NoKidsPage` does
  was rejected here, because the stop screen sits inside the kid's app content, where the Compose host can give the
  first focusable child focus on the first key.
- **Test:** no Compose UI test infrastructure runs on the JVM in this repo (only `androidTest`), so the check is in
  the quickstart, on a device. The focus-order pieces stay in one small composable for that reason.

## R8. Microphone: a grown-up turns voice search on (FR-005 to FR-011)

- **Facts:**
  - `ui/components/VoiceSearchButton.kt:150-156` calls `permissionLauncher.launch(RECORD_AUDIO)` on a press when
    the permission is missing. It is used by `ui/search/SearchPage.kt:243` (kids, when search is allowed) and
    `ui/search/SearchForDialog.kt:153` (also used by `kids/ui/settings/KidsSettingsPage.kt`, grown-up).
  - `SearchPage` shows it when `prefs.searchPreferences.showVoiceSearchButton` (default true).
  - `VoiceInputManager.recognitionIntent` has no `EXTRA_PREFER_OFFLINE`.
  - `ui/detail/music/NowPlayingPage.kt:390-421` asks for `RECORD_AUDIO` for the visualiser; kids never reach music.
- **Decision:**
  - New proto field `SearchPreferences.kids_voice_search` (`bool`, tag 3), default false (proto3 default), so no
    migration is needed and an unreadable value is off (fail closed).
  - `VoiceSearchButton` never requests a permission. A pure function
    `voiceSearchShown(enabled, granted, available)` decides visibility; callers pass `kidsVoiceSearch &&
    showVoiceSearchButton`, `voiceInputManager.hasPermission`, `voiceInputManager.isAvailable`. Search itself is
    already hidden for kids without `searchAllowed`. Granted state is re-read on `ON_RESUME`, so a permission withdrawn
    in system Settings hides the mic.
  - This TV gets `AppPreference.KidsVoiceSearch` (an `AppClickablePreference` with an on/off summary), handled in
    `ui/preferences/PreferencesContent.kt` by a `VoiceSearchConsent` composable: an explanation dialog ("the voice
    goes to this TV's speech service, not to your Jellyfin server"), then `RequestPermission` for the grown-up, then
    the pref is set only when granted. If denied and
    `shouldShowRequestPermissionRationale` is false afterwards, the dialog says to allow it in the TV's Settings →
    Apps → Peke → Permissions. Turning it off only clears the pref.
  - Setup: `SetupFlowViewModel.finish()` shows a new `SetupScreen.Voice` before launching when the TV isn't a
    re-sign-in or add flow, voice search is off and any signed-in kid's cached profile has `searchAllowed`. The step
    reuses `VoiceSearchConsent`; "Not now" finishes.
  - `EXTRA_PREFER_OFFLINE = true` on the recognition intent.
  - The visualiser's permission request, rationale dialog and `RecordAudioRationaleDialog` use in `NowPlayingPage`
    are deleted, and the visualiser starts only when the permission is already granted (grown-ups never reach music
    either, so in practice it never starts).
- **Constitution VI:** `RECORD_AUDIO` stays in the merged manifest, justified by kid voice search with grown-up
  consent. The voice leaves the household to the TV's speech service: a deliberate exception chosen in spec 001 (Q1),
  now behind the grown-up's informed consent. See the plan's complexity tracking.
- **Alternatives considered:** asking at first use behind the parent gate (rejected: a kid would see the gate pop up
  from the mic, and the explanation belongs with a calm settings page); a per-kid setting (rejected: the Android
  permission is per app, and the plugin's `searchAllowed` already decides per kid).

## R9. Media session: trusted controllers, transport only (FR-017)

- **Facts:** `services/PlayerFactory.kt:242-245` builds `MediaSession.Builder(context, player).build()` with no
  callback; `services/MusicService.kt:125` does the same for music. Media3 1.11.1 exposes
  `ControllerInfo.isTrusted()`, `getPackageName()`, `ControllerInfo.LEGACY_CONTROLLER_PACKAGE_NAME`,
  `MediaSession.isMediaNotificationController()`, `ConnectionResult.AcceptedResultBuilder.setAvailablePlayerCommands`
  and `Callback.onConnect`, `onPlayerCommandRequest`, `onAddMediaItems`, `onSetMediaItems`.
- **Decision:** a `KidsSessionCallback` (in `services/`, used by both builders):
  - `onConnect`: accept when `controller.packageName == context.packageName` or `controller.isTrusted`; reject
    otherwise (`ConnectionResult.reject()`).
  - Accepted controllers get player commands limited to: play/pause, prepare, stop, seek in the current item, seek
    back/forward, previous/next (item and media item), and every `GET_*` read command. Not granted:
    `SET_MEDIA_ITEM`, `CHANGE_MEDIA_ITEMS`, `SET_SPEED_AND_PITCH`, `SET_SHUFFLE_MODE`, `SET_REPEAT_MODE`,
    `SET_TRACK_SELECTION_PARAMETERS`, `SET_VOLUME`/`SET_DEVICE_VOLUME*`, `SET_PLAYLIST_METADATA`,
    `SET_MEDIA_ITEMS_METADATA`. Session commands: none custom.
  - `onAddMediaItems` and `onSetMediaItems` fail with `SessionError.ERROR_PERMISSION_DENIED` as a second line.
  - The allowed set is a constant (`KidsSessionCallback.PLAYER_COMMANDS`) so a unit test can check it.
- **Risk:** on API 23–27, platform (legacy) controllers, including the system's media-key path, may arrive with
  `LEGACY_CONTROLLER_PACKAGE_NAME` and `isTrusted == false`. Media keys pressed while Peke has focus are delivered to
  the activity first, but `ui/playback/PlaybackKeyHandler.kt:80` leaves some to the session. The quickstart has an
  on-device check on an API < 28 TV (or Fire TV); if keys break there, the fallback is to accept the legacy
  controller with the same transport-only commands.
- **Alternatives considered:** accepting everyone with transport-only commands (rejected: any app could still pause
  or seek a kid's video); no media session for kids (rejected: the remote's media keys and Google TV's controls
  depend on it).

## R10. Session messages: only the Kids plugin's (FR-018)

- **Facts:** `services/ServerEventListener.kt:73-80` advertises `DISPLAY_MESSAGE` and `SEND_STRING`; `:102-115` shows
  any text unless `hideForKids(header)` says a Kids message repeats what Peke shows. `KidsServerMessages.isKids(header)`
  recognises the plugin's headers. The plugin uses `DISPLAY_MESSAGE` for its warnings, so the capability stays.
- **Decision:** show a message only when `KidsServerMessages.isKids(header)` and not `hideForKids(header)`. Peke has no
  grown-up viewing, so the rule holds whoever is current. Extracted to a pure
  `KidsServerMessages.shouldShow(header, hidden)` for the test.

## R11. The error toast never blocks (FR-019)

- **Facts:** `util/ExceptionHandler.kt:34` does `runBlocking { showToast(...) }`, and `ui/Extensions.kt:364`
  `showToast` does `withContext(PekeDispatchers.Main)`. On the main thread this waits for itself (deadlock).
  `MainActivity.kt:422` uses it on `Main` (only an `Error` reaches it, since the block catches `Exception`). The text is
  `"Error: ${exception.message}"`.
- **Decision:** post the toast without blocking: `Handler(Looper.getMainLooper()).post { Toast.makeText(...).show() }`
  (`Context.mainExecutor` needs API 28; minSdk is 23). The text becomes a new friendly string
  (`peke_something_went_wrong`, "Oops, something went wrong"); the exception stays in the log. Peke has no grown-up
  error surface that relies on these toasts.
- **Test:** a Robolectric test calls `handleException` on the main looper and checks it returns and the toast is
  queued (`ShadowToast`).

## R12. P2: cut

The Kids plugin 0.0.7.0 (`jellyfin-kids`, `Enforcement/GateActions.cs`, `KidsPlaybackFilter.cs`,
`DeliveryRegistry.cs`, `PlaybackEnforcer.cs`) gates PlaybackInfo, live-stream opening and every media delivery action
(`Videos/GetVideoStream*`, `Audio/*`, HLS playlists and segments, `Library/GetFile`, live TV files), tracks the
deliveries in flight to kids, cuts them on a stop and sends `Stop` to the session. A direct stream therefore can't
outlive time's up, and nothing is needed on the client.
