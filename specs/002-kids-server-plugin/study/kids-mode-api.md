# Contract: Kids mode plugin API

Proposed in [spec.md](../spec.md) and [research.md](../research.md). Nothing here is implemented.

## API surface (route prefix `/KidsMode`)

**Auth model.**
- The kid's TV session holds the kid's token. Reads that touch the kid's own data accept "self or admin".
- **Mutations** require **either** an admin token **or** a short-lived **parent grant** obtained by verifying the passcode. The grant is sent in the `X-KidsMode-Grant` header.
- Grants are random 256-bit tokens kept in server memory. They are bound to `(deviceId, issuing userId)`, last 5 minutes and are single-household.

| Method & path | Auth | Request → Response |
|---|---|---|
| `GET /KidsMode/Info` | `[AllowAnonymous]` | → `{"pluginVersion":"1.0.0.0","apiVersion":1,"features":["profiles","passcode","watchTime","deviceLock"],"passcodeSet":true}` |
| `GET /KidsMode/Users/{userId}/Profile` | `[Authorize]`, self or admin | → `KidProfileDto` or 404 (not a kid). `{"userId":"…","revision":7,"searchAllowed":false,"autoplayEnabled":true,"autoplayCap":3,"pickedItemIds":["…"],"sections":[{"section":"KEEP_WATCHING","visible":true},…],"dailyAllowanceMinutes":90,"sessionDefaultMinutes":30}` |
| `GET /KidsMode/Profiles` | `[Authorize]` | → `[{userId, revision}]`. Lets the user switcher know which users are kids. It returns only ids, never settings of other users |
| `PUT /KidsMode/Users/{userId}/Profile` | admin or grant | `KidProfileDto` carrying the `revision` it is based on → 200 with the new revision, or **409 with the current value** |
| `DELETE /KidsMode/Users/{userId}/Profile` | admin or grant | Unmarks the user as a kid |
| `POST /KidsMode/Parent/Verify` | `[Authorize]` | `{"passcode":"1234"}` → `{"valid":true,"grant":"…","expiresAt":"…"}`, or `{"valid":false,"retryAfterSeconds":30}`. **Never return 401**, because clients treat 401 as an expired session |
| `PUT /KidsMode/Parent/Passcode` | admin, or grant plus `current` | `{"current":"…","new":"…"}`, or `{"verifier":"pbkdf2-sha256$…"}` for migration (admin or grant, and only when none is set) |
| `GET /KidsMode/Parent/Verifier` | grant or admin | → `{"verifier":"…"}`, cached by the app for offline use (see [security](../research.md#security-considerations)) |
| `GET /KidsMode/Users/{userId}/WatchTime?date=YYYY-MM-DD` | self or admin | → `{"date":"…","usedSeconds":3120,"allowanceSeconds":5400,"bonusSeconds":600,"remainingSeconds":2880,"withinSchedule":true,"serverTime":"…"}` |
| `POST /KidsMode/Users/{userId}/WatchTime/Report` | self | `{"segments":[{"id":"uuid","deviceId":"…","startedAt":"…","seconds":60}]}`. Idempotent by segment `id`, which makes offline replay safe. Optional, because the server also counts playback itself |
| `POST /KidsMode/Users/{userId}/WatchTime/Bonus` | admin or grant | `{"minutes":15}` |
| `GET /KidsMode/Devices/{deviceId}/Lock` / `PUT` | GET: self; PUT: admin or grant | `{"lockRequired":true,"remoteUnlockRequestedAt":null}` |

**Server-side counting.**
- A hosted service subscribes to `ISessionManager.PlaybackStart`, `PlaybackProgress` and `PlaybackStopped` (confirmed in `ISessionManager.cs`).
- It adds clamped deltas for non-paused progress per kid and per server-local date. This counts playback from **every** client, not just Wholphin.
- When `remaining <= 0`, it calls `SendPlaystateCommand(..., PlaystateCommand.Stop)` and `SendMessageCommand` on that kid's sessions. **Unverified:** the exact `controllingSessionId` nullability and `PlaystateRequest` shape in 12.x.
- Harder enforcement is possible but **unverified**: register an extra `IAuthorizationHandler` from the plugin that fails `PlaybackInfo` and stream requests for out-of-time kids, or temporarily rewrite `AccessSchedules`. Rewriting the schedule is effective but intrusive.

## Code skeletons

**`Jellyfin.Plugin.KidsMode.csproj`**
```xml
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net10.0</TargetFramework>
    <RootNamespace>Jellyfin.Plugin.KidsMode</RootNamespace>
    <Nullable>enable</Nullable>
    <TreatWarningsAsErrors>true</TreatWarningsAsErrors>
    <GenerateDocumentationFile>true</GenerateDocumentationFile>
  </PropertyGroup>
  <ItemGroup>
    <FrameworkReference Include="Microsoft.AspNetCore.App" />
    <!-- Compile against the lowest supported 12.x; targetAbi 12.0.0.0 -->
    <PackageReference Include="Jellyfin.Controller" Version="12.0.0"><ExcludeAssets>runtime</ExcludeAssets></PackageReference>
    <PackageReference Include="Jellyfin.Model" Version="12.0.0"><ExcludeAssets>runtime</ExcludeAssets></PackageReference>
    <PackageReference Include="Jellyfin.Data" Version="12.0.0"><ExcludeAssets>runtime</ExcludeAssets></PackageReference>
  </ItemGroup>
  <ItemGroup>
    <EmbeddedResource Include="Configuration\configPage.html" />
    <EmbeddedResource Include="Configuration\configPage.js" />
  </ItemGroup>
</Project>
```
`Jellyfin.Controller` 12.2.0 does not pull in `Jellyfin.Data` or `Jellyfin.Database.Implementations` (per its nuspec), so reference them explicitly if you use `User` or `UserEntityExtensions`.

**`build.yaml`**: `targetAbi: "12.0.0.0"`, `framework: "net10.0"`, `artifacts: ["Jellyfin.Plugin.KidsMode.dll"]`.

**`Plugin.cs`**
```csharp
using System; using System.Collections.Generic; using System.Globalization;
using Jellyfin.Plugin.KidsMode.Configuration;
using MediaBrowser.Common.Configuration; using MediaBrowser.Common.Plugins;
using MediaBrowser.Model.Plugins; using MediaBrowser.Model.Serialization;

namespace Jellyfin.Plugin.KidsMode;

public sealed class Plugin : BasePlugin<PluginConfiguration>, IHasWebPages
{
    public Plugin(IApplicationPaths paths, IXmlSerializer xml) : base(paths, xml) => Instance = this;

    public static Plugin? Instance { get; private set; }
    public override string Name => "Kids Mode";
    public override Guid Id => Guid.Parse("5b1f3c1e-0000-4000-8000-000000000000"); // generate a real one

    public IEnumerable<PluginPageInfo> GetPages() =>
    [
        new() { Name = "KidsMode", EmbeddedResourcePath = $"{GetType().Namespace}.Configuration.configPage.html" },
        new() { Name = "KidsModeJs", EmbeddedResourcePath = $"{GetType().Namespace}.Configuration.configPage.js" },
    ];
}

// Configuration/PluginConfiguration.cs
public class PluginConfiguration : BasePluginConfiguration
{
    public string? PasscodeVerifier { get; set; }            // pbkdf2-sha256$iter$saltHex$hashHex
    public List<KidProfileEntry> Kids { get; set; } = new();
    public int DefaultDailyAllowanceMinutes { get; set; } = 0; // 0 = no daily limit
}
public class KidProfileEntry
{
    public Guid UserId { get; set; }
    public long Revision { get; set; }
    public bool SearchAllowed { get; set; }
    public bool AutoplayEnabled { get; set; }
    public int AutoplayCap { get; set; } = 3;
    public List<Guid> PickedItemIds { get; set; } = new();
    public List<SectionEntry> Sections { get; set; } = new();
    public int? DailyAllowanceMinutes { get; set; }
}
```

**`PluginServiceRegistrator.cs`**
```csharp
using MediaBrowser.Controller; using MediaBrowser.Controller.Plugins;
using Microsoft.Extensions.DependencyInjection;

namespace Jellyfin.Plugin.KidsMode;

public sealed class PluginServiceRegistrator : IPluginServiceRegistrator
{
    public void RegisterServices(IServiceCollection services, IServerApplicationHost host)
    {
        services.AddSingleton<KidsStore>();            // wraps Plugin.Instance.Configuration + lock, revisions, PBKDF2 verify
        services.AddSingleton<ParentGrantService>();   // in-memory grants + per-device throttling
        services.AddSingleton<WatchTimeStore>();       // JSON file under Plugin.Instance.DataFolderPath
        services.AddHostedService<WatchTimeTracker>(); // subscribes to ISessionManager.PlaybackProgress/Stopped
    }
}
```

**`Api/KidsModeController.cs`**
```csharp
using System; using System.Net.Mime; using System.Threading.Tasks;
using MediaBrowser.Controller.Library;
using Microsoft.AspNetCore.Authorization; using Microsoft.AspNetCore.Http; using Microsoft.AspNetCore.Mvc;

namespace Jellyfin.Plugin.KidsMode.Api;

[ApiController]
[Route("KidsMode")]
[Produces(MediaTypeNames.Application.Json)]
public sealed class KidsModeController : ControllerBase
{
    private const string GrantHeader = "X-KidsMode-Grant";
    private readonly KidsStore _store; private readonly ParentGrantService _grants; private readonly IUserManager _users;

    public KidsModeController(KidsStore store, ParentGrantService grants, IUserManager users)
        => (_store, _grants, _users) = (store, grants, users);

    [HttpGet("Info")]
    [AllowAnonymous]
    public ActionResult<KidsModeInfo> GetInfo() => new KidsModeInfo(
        Plugin.Instance!.Version.ToString(), ApiVersion: 1,
        Features: ["profiles", "passcode", "watchTime", "deviceLock"], PasscodeSet: _store.PasscodeSet);

    [HttpGet("Users/{userId}/Profile")]
    [Authorize]
    [ProducesResponseType(StatusCodes.Status200OK)][ProducesResponseType(StatusCodes.Status404NotFound)]
    public ActionResult<KidProfileEntry> GetProfile([FromRoute] Guid userId)
    {
        if (!IsAdmin() && CallerId() != userId) return Forbid();
        return _store.GetProfile(userId) is { } p ? p : NotFound();
    }

    [HttpPut("Users/{userId}/Profile")]
    [Authorize]
    public async Task<ActionResult<KidProfileEntry>> PutProfile(
        [FromRoute] Guid userId, [FromBody] KidProfileEntry body, [FromHeader(Name = GrantHeader)] string? grant)
    {
        if (!IsAdmin() && !_grants.IsValid(grant, DeviceId())) return Forbid();   // a kid token alone never suffices
        if (_users.GetUserById(userId) is null) return NotFound();
        var result = await _store.SaveProfileAsync(userId, body).ConfigureAwait(false); // checks body.Revision
        return result.Conflict ? Conflict(result.Current) : result.Saved!;
    }

    [HttpPost("Parent/Verify")]
    [Authorize]
    public ActionResult<VerifyResponse> Verify([FromBody] VerifyRequest req)
    {
        var (caller, device) = (CallerId(), DeviceId());
        if (_grants.RetryAfter(caller, device) is { } wait) return new VerifyResponse(false, null, null, (int)wait.TotalSeconds);
        if (!_store.VerifyPasscode(req.Passcode)) { _grants.RecordFailure(caller, device); return new VerifyResponse(false, null, null, null); }
        var g = _grants.Issue(caller, device, TimeSpan.FromMinutes(5));
        return new VerifyResponse(true, g.Token, g.ExpiresAt, null);   // 200 even on failure: never 401
    }

    private Guid CallerId() => Guid.TryParse(User.FindFirst("Jellyfin-UserId")?.Value, out var id) ? id : Guid.Empty;
    private string? DeviceId() => User.FindFirst("Jellyfin-DeviceId")?.Value;
    private bool IsAdmin() => User.IsInRole("Administrator") || User.FindFirst("Jellyfin-IsApiKey")?.Value == "True"; // verify API-key claim value
}

public record KidsModeInfo(string PluginVersion, int ApiVersion, string[] Features, bool PasscodeSet);
public record VerifyRequest(string Passcode);
public record VerifyResponse(bool Valid, string? Grant, DateTimeOffset? ExpiresAt, int? RetryAfterSeconds);
```
Server-side verification: `Rfc2898DeriveBytes.Pbkdf2(Encoding.UTF8.GetBytes(code), salt, iter, HashAlgorithmName.SHA256, 32)` compared with `CryptographicOperations.FixedTimeEquals`.

**Kotlin detection sketch (SDK 1.7.1 or 1.9.x)**
```kotlin
@Serializable data class KidsModeInfo(val pluginVersion: String, val apiVersion: Int, val features: List<String>, val passcodeSet: Boolean)
suspend fun ApiClient.detectKidsPlugin(): KidsModeInfo? =
    try { get<KidsModeInfo>("/KidsMode/Info").content.takeIf { it.apiVersion >= 1 } }
    catch (e: InvalidStatusException) { if (e.status == 404) null else throw e }
```
