# Contract: Peke releases, versions and update assets

Spec FR-022, FR-024a, FR-027; research R5–R7. Producer: `.github/workflows/release.yml`. Consumer:
`UpdateChecker` / `getDownloadUrl`.

## Tags and versions

- Tags: plain `vMAJOR.MINOR.PATCH`, starting at `v0.1.0`. No Wholphin tags remain in the repo (owner runbook,
  [quickstart.md](../quickstart.md) §1).
- `versionName`: `git describe --tags --long --match=v*` without the leading `v` (e.g. `0.1.0-0-gabc1234` on the
  tag commit is shown as `0.1.0-0-gabc1234`; `Version` parses both forms). `0.0.0` without tags.
- `versionCode`: number of `v*` tags, at least 1 (`v0.1.0` → 1, `v0.2.0` → 2).
- Release title (`name` in the GitHub API) = the tag, e.g. `v0.1.0`; `Version.tryFromString` parses it.

## Release feed

| Use | URL |
|---|---|
| Latest (default `UpdateUrl`) | `https://api.github.com/repos/aldoborrero/peke/releases/latest` |
| By version | `https://api.github.com/repos/aldoborrero/peke/releases/tags/v{major}.{minor}.{patch}` |

Nothing in the app references `damontecres/Wholphin` releases. Pre-releases are ignored by `/latest`.

## Assets per release

| Asset | Contents |
|---|---|
| `Peke-armeabi-v7a.apk` | release, 32-bit ARM |
| `Peke-arm64-v8a.apk` | release, 64-bit ARM |
| `Peke-x86_64.apk` | release, x86_64 (emulators) |
| `Peke.apk` | release, universal |

All release APKs contain MPV and the FFmpeg and AV1 decoders, are signed with the CI key and are verified with
`apksigner` before upload; SHA-256 sums are printed in the job log.

Selection (`getDownloadUrl`, `ASSET_NAME = "Peke"`): first match of `Peke-release-{abi}.apk`,
`Peke-release.apk`, `Peke-{abi}.apk`, `Peke.apk`, where `{abi}` is the device's first supported ABI. Debug builds
look for `Peke-debug-{abi}.apk` / `Peke-debug.apk` only (none are published, so debug builds never self-update).

## Build outputs (before renaming)

`Peke-{buildType}-{versionName}-{versionCode}{-abi}.apk`; the workflow copies them to the short names above.
