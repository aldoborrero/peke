# Research: Kids mode server plugin for Jellyfin 12.x

Design study for [spec.md](spec.md), checked against the `release-12.z` sources on 2026-10-17. The proposed API is in [contracts/kids-mode-api.md](contracts/kids-mode-api.md).

## Which version "version 12" is

**There is no Jellyfin 10.12 release.** The 10.12 development line shipped as **Jellyfin 12.0** after the project changed its version scheme and dropped the "10." prefix ("Switch to new version scheme, dropping the 10.x prefix", https://github.com/jellyfin/jellyfin/pull/16758).

- **Releases.** v12.0 came out on 2026‑09‑08, v12.1 on 2026‑09‑15 and **v12.2 on 2026‑10‑05**, which is current (https://github.com/jellyfin/jellyfin/releases). The maintenance branch is `release-12.z`.
- **NuGet.** `Jellyfin.Controller`, `Jellyfin.Model`, `Jellyfin.Data`, `Jellyfin.Common` and `Jellyfin.Database.Implementations` all have versions 12.0.0, 12.1.0 and 12.2.0. The last 10.x version is 10.11.11 (https://api.nuget.org/v3-flatcontainer/jellyfin.controller/index.json).
- **Target.** Build for Jellyfin **12.x**. The user's "version 12" is correct; "10.12" is a name that was never released.

## Plugin basics for 12.x

**Target framework.** Use **`net10.0`**. The 12.0 release notes say: "The server now targets .NET 10. Plugins have to be retargeted and rebuilt." `MediaBrowser.Controller.csproj` on `release-12.z` has `<TargetFramework>net10.0</TargetFramework>` and `VersionPrefix 12.2.0`.

**ABI.**
- The plugin manager loads a plugin only if `_appVersion >= targetAbi`, where `targetAbi` comes from the plugin's `meta.json` (`Emby.Server.Implementations/Plugins/PluginManager.cs`). An unparseable value counts as compatible.
- Each plugin is loaded in its own `PluginLoadContext`. A `TypeLoadException` marks it NotSupported, which is what a 10.11 build hits on 12.
- Recommendation: compile against **12.0.0** packages with `targetAbi: "12.0.0.0"` so the plugin runs on 12.0 through 12.2.
- The official repo manifest does the same. Its latest entries are mostly `12.0.0.0` (Playback Reporting 19, Webhook 22, LDAP 24), with a few `12.1.0.0` (https://repo.jellyfin.org/files/plugin/manifest.json).

**Template status.**
- `jellyfin/jellyfin-plugin-template` is still on 10.11: `net9.0`, `Jellyfin.Controller 10.11.5`, `targetAbi 10.11.0.0` (https://github.com/jellyfin/jellyfin-plugin-template).
- Use it for structure, but take the 12.x details from a plugin that has been ported. **Playback Reporting** is the closest match because it has a REST controller, its own storage, a hosted service and config pages (https://github.com/jellyfin/jellyfin-plugin-playbackreporting).
  - It uses `net10.0`, `Jellyfin.Controller`/`Jellyfin.Data` `12.*-*`, `<FrameworkReference Include="Microsoft.AspNetCore.App" />` and `targetAbi 12.0.0.0`.
  - Its controller is `[ApiController][Authorize(Policy = Policies.RequiresElevation)][Route("user_usage_stats")]`, and `using Jellyfin.Database.Implementations.Entities` gives it the `User` entity.

**Core types (checked in `release-12.z`).**
- **`BasePlugin<TConfiguration>`** (`MediaBrowser.Common.Plugins`) provides `Configuration`, `SaveConfiguration()`, `ConfigurationChanged`, `ConfigurationFilePath` (an XML file under `plugins/configurations`) and `DataFolderPath`.
- **`IHasWebPages.GetPages()`** returns `PluginPageInfo`, which has `Name`, `DisplayName`, `EmbeddedResourcePath`, `EnableInMainMenu`, `MenuSection` and `MenuIcon`.
- **Config page.** It is an embedded HTML file containing `<div data-role="page" class="page type-interior pluginConfigurationPage">`. Its JS uses `ApiClient.getPluginConfiguration(id)`, `ApiClient.updatePluginConfiguration(id, cfg)` and `Dashboard.processPluginConfigurationUpdateResult` (from the template's `configPage.html`). The page can also call the plugin's own endpoints through `ApiClient.fetch` / `ApiClient.getUrl`.
- **`IPluginServiceRegistrator`** (`MediaBrowser.Controller.Plugins`) has one method, `void RegisterServices(IServiceCollection, IServerApplicationHost)`. It is instantiated with `Activator.CreateInstance` before the DI container is built, and it is skipped if the plugin is not enabled and supported.
- **Controllers** are plain `ControllerBase` classes in the plugin assembly and are discovered automatically.
  - Policies are in `MediaBrowser.Common.Api.Policies`: `RequiresElevation`, `FirstTimeSetupOrElevated`, `LocalAccessOnly`, `IgnoreParentalControl`, `LocalAccessOrRequiresElevation` and others.
  - A plain `[Authorize]` means "any authenticated user". That default handler also **enforces the user's access schedule** (`requirement.ValidateParentalSchedule && !user.IsParentalScheduleAllowed()` → Fail, in `DefaultAuthorizationHandler.cs`), and admins bypass it.
  - `[AllowAnonymous]` works; core uses it, for example on the plugin image endpoint.
  - `Jellyfin.Api` is not a NuGet package, so plugins read the claims directly: `"Jellyfin-UserId"`, `"Jellyfin-DeviceId"`, `"Jellyfin-IsApiKey"`, and the role `"Administrator"` (`Jellyfin.Api/Constants/InternalClaimTypes.cs`, `UserRoles.cs`).

**Packaging.**
- `build.yaml` carries `name`, `guid`, `version`, `targetAbi`, `framework`, `artifacts` and `changelog`.
- `jprm` builds the zip and writes `meta.json`. `jprm repo add` updates a `manifest.json` that you host on GitHub Pages or a release.
- Manifest format: `[{guid, name, overview, description, owner, category, versions:[{version, changelog, targetAbi, sourceUrl, checksum(md5), timestamp}]}]`, as in the official manifest above. Users add the manifest URL under Dashboard → Plugins → Repositories.

**Breaking changes that matter.**
- **10.10 → 10.11:** .NET 8 → .NET 9. Entities moved to `Jellyfin.Database.Implementations.Entities`. The library moved to EF Core.
- **10.11 → 12.0:**
  - .NET 10.
  - `IUserManager.Users`/`UsersIds` became `GetUsers()`/`GetUsersIds()`.
  - `RenameUser`/`ResetPassword`/`ChangePassword` now take a user id.
  - `ISearchEngine` became `ISearchManager`.
  - `IAuthenticationProvider.HasPassword` was removed.
  - **Legacy authorization is disabled by default**, including on upgraded installs (https://github.com/jellyfin/jellyfin/pull/15559). Discussion in the PR names `X-Emby-Token`, `X-Emby-Authorization`, `X-MediaBrowser-Token` and the `api_key` query parameter, but this is not confirmed from the code. Use the standard `Authorization` header.
  - The `/emby/*` and `/mediabrowser/*` prefixes were removed.
  - "Parental filtering is enforced on additional endpoints."
  - Swashbuckle 10 changes the generated OpenAPI document.
- **12.0 → 12.2:** No plugin-relevant breaks found, but the 12.1/12.2 notes were not read in full.

## Storage options

| Option | Who can write | Verdict |
|---|---|---|
| Plugin XML config (`BasePlugin<T>.Configuration`) | Admin, through `POST /Plugins/{id}/Configuration` (RequiresElevation), and the plugin itself | Good for small, admin-owned state. It is included in server config backups. The config page sends the whole object back on save, so a save can overwrite concurrent API writes |
| `DisplayPreferences.CustomPrefs` / `UserConfiguration` | **The user's own token** (`DisplayPreferencesController` is plain `[Authorize]` and resolves the caller's own userId) | **Reject** for anything a kid must not change |
| `UserData` (per-item played/progress data) | The user | Not relevant |
| Plugin-owned JSON or SQLite under `DataFolderPath` | The plugin only | Best for frequent writes such as watch time. Playback Reporting uses SQLite through `SQLitePCL.pretty` |

**Recommendations.**
- **Kid profile settings (MVP):** store them in the plugin XML config as `List<KidProfileEntry>`, keyed by the Jellyfin user **GUID**, not the Room `rowId`. Add a `Revision` counter for optimistic concurrency. If concurrent edits become a problem, move them to a JSON file under `DataFolderPath`.
- **Parent passcode verifier:** use **one per server** (household) in the XML config, with an optional per-kid override later.
  - The app's verifier format is `pbkdf2-sha256$iter$saltHex$hashHex`, using PBKDF2‑HMAC‑SHA256 with 120k iterations and a 32-byte key. One block equals the full output, so it matches .NET's `Rfc2898DeriveBytes.Pbkdf2(pw, salt, iter, HashAlgorithmName.SHA256, 32)`.
  - The **existing local verifier can therefore be uploaded unchanged** during migration, without re-entering the passcode.
  - Minor doc drift: `data-model.md` says `saltB64`/`hashB64`, but `PasscodeHasher.kt` uses hex.
- **Watch time:** store daily aggregates `(userId, localDate, usedSeconds, bonusSeconds)` in a plugin-owned JSON file. Write it atomically with temp-file-and-rename, flushing about every 60 s and on stop. Use SQLite only if per-session history is needed.
- **Allowed hours:** do not store these at all. Use **`UserPolicy.AccessSchedules`**, covered next.

**`AccessSchedules` gives server-side hours for free.**
- `AccessSchedule` has `DynamicDayOfWeek DayOfWeek`, `double StartHour` and `double EndHour`.
- `IsParentalScheduleAllowed` passes if there are no schedules, or if any schedule matches `DateTime.UtcNow.ToLocalTime()`. That is the **server's** time zone, and the end is inclusive (`Jellyfin.Data/UserEntityExtensions.cs`).
- It is enforced at login (`UserManager.AuthenticateUser` throws "User is not allowed access at this time") and on **every** `[Authorize]` request for non-admins.
- This works across every client, but it only covers windows such as bedtime. It is not a duration budget. A daily allowance is what the plugin adds.

## What Jellyfin already provides

From `MediaBrowser.Model/Users/UserPolicy.cs` (12.x):
- `MaxParentalRating` (int?) and `MaxParentalSubRating`
- `BlockUnratedItems` (`UnratedItem[]`, per item type)
- `AllowedTags` / `BlockedTags`
- `EnableAllFolders` / `EnabledFolders` (Guid[])
- `AccessSchedules`
- `EnableLiveTvAccess`, `EnableContentDeletion`, `EnableUserPreferenceAccess`, `EnableRemoteControlOfOtherUsers`
- `MaxActiveSessions`
- `IsHidden`

Admins write the policy with `POST /Users/{userId}/Policy` (RequiresElevation). The kid's own token can read it through `GET /Users/Me` (`UserDto.Policy`). In plugin code, use `IUserManager.UpdatePolicyAsync(Guid, UserPolicy)`.

**Answer: yes, "kid = a Jellyfin user with a restrictive policy, plus a small plugin" covers most of it.**
- **The server already enforces, for every client:** content (rating, unrated, tags, libraries), allowed hours, Live TV, deletion, remote control, a hidden login tile and session caps.
- **The plugin only adds:**
  - (a) the "is a kid" flag and the Wholphin UX settings (sections, picks, search, autoplay)
  - (b) a household parent passcode with server-side verification
  - (c) a **daily allowance** across devices
  - (d) device-lock policy
  - (e) optional "apply recommended policy" helpers, so the admin config page can set rating, unrated blocking and folders in one click by calling `UpdatePolicyAsync`
- **Do not duplicate:** rating, tag and folder filtering, or allowed hours.

## How the Kotlin app would detect and use it

- **SDK.** Wholphin uses `jellyfin-sdk` **1.7.1**, whose generated API is for 10.11. SDK **1.9.0** (2026‑09‑08) "is now based on 12.0, and no longer compatible with Jellyfin 10.11 and earlier" (https://github.com/jellyfin/jellyfin-sdk-kotlin/releases/tag/v1.9.0). This argues for plugin mode on 12.x servers only and local mode everywhere else.
- **Custom endpoints work with either SDK.** `ApiClient.request(method, pathTemplate, pathParameters, queryParameters, requestBody)` and the reified `ApiClient.get<T>()`, `post<T>()` and `delete<T>()` extensions (`HttpMethodExtensions.kt`) return `Response<T>` and use the client's `Authorization` header. Use `@Serializable` DTOs and keep calls on the ApiClient. Do not build URLs with `api_key`, which no longer works on 12.
- **Detection.** `GET /Plugins` is **RequiresElevation** (class-level attribute in `PluginsController.cs`), so a kid token cannot use it. Use the anonymous `GET /KidsMode/Info` instead.
  - 200 means plugin mode, gated on `apiVersion`.
  - 404 means local mode.
  - A network error means keep the last known mode.
  - Cache the result per `serverId` and check again on each server connect.
  - `[AllowAnonymous]` matters here: a kid outside their schedule fails every `[Authorize]` call, yet the app still needs to know the plugin exists and show "not allowed right now".
- **Offline and fallback.**
  - Room stays as a **read-through cache** of the server profile, with `revision` and `syncedAt`.
  - Kids mode keeps working offline from the cache. The device lock and session timer are always local.
  - Watch segments are queued in Room and replayed with idempotent ids.
  - If the plugin disappears from a server that was in plugin mode, fail closed: keep the cached kid flags and lock, and show a "server plugin unavailable" notice behind the parent gate.
- **Migration from local to server.** On first detection, behind the parent gate with an admin login or a passcode-backed grant:
  1. If the server has no passcode, upload the local verifier with `PUT Parent/Passcode {verifier}`.
  2. For each local `KidProfile`, map `rowId` to `JellyfinUser.id` (UUID) and `PUT` it, but only if the server returns 404 for that user. The server wins if a profile already exists.
  3. Mark the server as migrated in Room.

  Keep the Room schema. Add `serverRevision: Long?` and `source: LOCAL|SERVER` through an AutoMigration.

## Security considerations

- **Where verification happens.** In plugin mode it happens **on the server**. The kid's device never needs the verifier to unlock settings, and wrong attempts are rate-limited per `(userId, deviceId)` with exponential backoff, for example 5 tries then 30 s, doubling.
- **Offline unlock** needs a cached verifier on the device. That is no worse than today's local mode, so the same PBKDF2 parameters apply. The verifier is served only to a grant or admin and is never returned in the plugin XML to non-admins. Plugin XML is only readable through the RequiresElevation `/Plugins/{id}/Configuration`.
- **A kid's token cannot change its own settings or extend its time:**
  - Every mutating endpoint checks `IsAdmin() || grants.IsValid(header, deviceId)`. A kid token alone is never enough, even for its own userId.
  - Bonus time is a mutation and needs a grant.
  - Server-counted usage comes from playback events. A client-reported `Report` can only **add** usage, never subtract it.
  - Never put trust-relevant state in `CustomPrefs` or `UserConfiguration`, which the kid's token can write.
  - Kid accounts must not be administrators. Admins bypass schedules and every check. The setup checklist should warn if a kid is an admin.
  - Kid accounts should have no password, or one the parent holds, so the kid cannot log in elsewhere as someone else. The real escape route, logging in as the parent, is guarded by the parent's password and PIN.
- **Grants** are short-lived, bound to the device, held only in memory, and dropped on restart. Compare them in constant time.
- **API keys** carry `Jellyfin-IsApiKey` and no user id. Treat them as admin explicitly. **Unverified:** whether `IsInRole("Administrator")` is true for API keys.
- **Anonymous `Info`** exposes only the version, features and `passcodeSet`. If even that is too much, drop `passcodeSet`.

## Two-mode architecture recommendation

```kotlin
interface KidsConfigSource {
    val mode: Mode                       // LOCAL or SERVER
    fun observeProfile(user: JellyfinUser): Flow<KidProfile?>
    suspend fun kidUserIds(serverId: UUID): Set<UUID>
    suspend fun saveProfile(p: KidProfile, auth: ParentAuth): SaveResult   // Ok | Conflict(current) | Forbidden
    suspend fun verifyPasscode(code: String): ParentAuth?                  // Local token or server grant
    suspend fun watchTime(user: JellyfinUser): WatchBudget?                // null in LOCAL: no daily budget
    suspend fun reportSegments(segments: List<WatchSegment>)
}
class LocalSource(dao, passcodeStore) : KidsConfigSource            // today's behaviour
class ServerPluginSource(api, dao /* cache */, outbox) : KidsConfigSource
class KidsConfigSourceResolver(...)  // per serverId, from cached /KidsMode/Info
```

`KidsModeService`, `KidsSettingsViewModel` and `KidsParentGateViewModel` depend on the resolved source instead of `KidProfileDao` and `ParentPasscodeStore` directly.

**What lives where.**
- **Server:** content policy (native), kid flag and UX settings, household passcode, daily allowance and usage, bonus time, device-lock policy.
- **Device:** device lock state, which is enforced locally and must work offline. Also the session timer, which stays per device, and the cache and outbox.

**Conflict rules.**
- The server is authoritative in plugin mode.
- Profile writes use optimistic concurrency on `revision`. On 409, show the server value and let the parent re-apply.
- Usage merges as a grow-only union of segment ids. Effective remaining time is `allowance + bonus − max(serverUsed, localUnsyncedUsed + serverUsed)`.
- Offline bonus time is not allowed. Alternatively, record it locally as a device-only extension that never syncs.
- Lock: the stricter of local and server wins.

**Phased rollout.**
1. **Plugin MVP:**
   - `Info`, profile GET/PUT/DELETE, `Profiles`, `Parent/Verify` with grants, and `Parent/Passcode`.
   - XML config storage and an admin config page that lists users and marks kids.
   - Then add `ServerPluginSource` in the app, plus detection and one-time migration.
2. **Daily allowance:** server playback-event counting, the `WatchTime` endpoints, and the app showing the cross-device budget next to the session timer.
3. **Enforcement and policy helpers:** stop commands when time runs out, an "apply recommended policy" button that calls `UpdatePolicyAsync`, an `AccessSchedules` editor in the config page, and remote unlock or lock-policy endpoints.
4. **Extras:** a per-kid passcode override, activity history, an optional 10.11 build (net9, separate branch), and possibly hard enforcement through an authorization handler after it is verified.

## Not verified for 12.x
- The exact list of legacy auth carriers disabled in 12.0. The four names come from discussion in PR 15559, not from code.
- Whether `IsInRole("Administrator")` is true for API keys, and the exact `Jellyfin-IsApiKey` claim value.
- The `SendPlaystateCommand` / `PlaystateRequest` details, and whether a plugin-registered `IAuthorizationHandler` is honoured.
- The exact name of the user-deleted event, for cleaning up orphaned kid entries.
- Anything in the 12.1 and 12.2 release notes, which were not read in full.
- Whether the 12.x dashboard still renders legacy `data-role="page"` config pages unchanged. Ported plugins such as Playback Reporting still ship HTML/JS pages, which suggests it does.
- Whether SDK `get<T>` decoding works for app-defined `@Serializable` types. It uses kotlinx and likely does, but test it.

