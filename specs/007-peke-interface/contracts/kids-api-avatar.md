# Contract: kid avatar in the Kids plugin (Kids API 1.1)

**Source**: the plugin's own contract, `specs/012-kids-avatar/contracts/kids-api-v1-avatar.md` in jellyfin-kids
(`origin/main`), shipped in plugin 0.0.8.0 and released with 0.0.9.0. What Peke relies on is summarised here; the
plugin's file wins where they differ.

## Capability

`GET /Kids/Info` (anonymous) lists `"avatar"` in `Features`. `ApiVersion` stays `1`; `ApiMinorVersion` (new, raised on
every additive change, missing means 0) is 1 with avatars and 2 on 0.0.9.0 (adds `bonusUndo`, `breakEnd`). Peke checks
the `avatar` feature before reading `Avatar`, never the minor version alone.

## Where `Avatar` appears

- `GET /Kids/Profiles` (any signed-in user, also outside allowed hours): each entry `{"UserId", "Revision", "Avatar"}`,
  so the picker shows every kid from one request.
- `GET /Kids/Users/{userId}/Profile` (self or admin), the `PUT` answer and the `409` body.

Values: `leo`, `max`, `nico`, `lucy`, `sara`, `mia`, always lowercase. **Omitted** when the kid has none. The server
holds only the id; the pictures ship in the app.

## Writing the profile (full replacement)

`PUT /Kids/Users/{userId}/Profile` replaces the whole profile: a body without `Avatar` **clears** it. Peke never
changes a kid's avatar (the dashboard does), but every `PUT` it sends (the daily time on "{Kid}'s rules", spec 006)
MUST carry the cached `Avatar` unchanged. It does: `KidProfileMapper.toUpdate` starts from the profile JSON the
server last sent and replaces only the fields Peke edits (tested in `KidProfileRepositoryTest`). The plugin binds the body
with System.Text.Json defaults, so fields it doesn't know are ignored, not rejected. `Avatar` is matched case-insensitively after
trimming; an unknown value gets `400` with `errors.Avatar`, after the revision check (stale and invalid gives `409`).

## Client behaviour

| Plugin | `Avatar` | Peke shows |
|---|---|---|
| no `avatar` feature (0.0.7.0 and older) | — | Jellyfin profile image (`Users/{id}/Images/Primary`), else the initial |
| `avatar` | one of the six | that avatar on its colour (DESIGN Avatars) |
| `avatar` | omitted, or a value Peke doesn't ship | Jellyfin profile image, else the initial |

The value is cached with the kid: it is read from the profile JSON the server last sent (`KidProfile.serverJson`), so an
offline launch shows the same picture, with no column or migration of its own.
