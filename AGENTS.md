# Agent rules for Peke

Peke for Jellyfin is a kids-only Android TV app, a full fork of Wholphin (GPLv2). The project
rules are in `.specify/memory/constitution.md`; this file adds how agents work in the repo. When
a skill in `.claude/skills/` conflicts with the constitution or this file, the constitution and
this file win.

## Tools

- Build, test and lint only through the Nix devshell: `nix develop -c ./gradlew …`,
  `nix develop -c pre-commit run --files …`. The devshell has adb, the `android` CLI, apkanalyzer,
  r8, retrace, lint, ktlint, detekt, perfetto, `trace_processor_shell`, python3 with protobuf, jq,
  bundletool and scrcpy.
- Format with `nix fmt` (treefmt: nixfmt, deadnix, ktlint, actionlint; config in
  `nix/formatter.nix`). `nix flake check` fails on unformatted files.
- Never install tools with `curl | bash`, pip, npm or npx, `android sdk install` or `android update`.
  If a tool is missing, ask to add it to `nix/devshell.nix`.
- Search files with fd and text with rg; use `ast-grep` for structural queries.
- Temporary files go in the session scratchpad, never in the repo: no `tmp/`, no report files
  unless asked.

## Git and credentials

- Never run git write commands (commit, branch, checkout -b, push, stash, tag, config) unless the
  owner asks. The owner commits and signs. No AI attribution in commits or PRs.
- Run throwaway git experiments in a separate clone, never in a worktree of this repo: `git config`
  in a worktree writes the shared `.git/config`.
- Never handle credentials: Jellyfin passwords, GitHub tokens (the extensions token for
  `read:packages` is the owner's), signing keys or any login.
- Don't install spec-kit's git or GitHub extensions, and don't create `.specify/extensions.yml`.

## Code

- The Kotlin namespace is `dev.aldoborrero.peke`. The only code under
  `com.github.damontecres.wholphin.mpv` is the external `wholphin-mpv` library's API (and the
  `wholphin-mpv-stub` module that mirrors it): its native code binds those names, so never rename
  them.
- Fixes from Wholphin are ported by hand onto the renamed paths; there are no upstream merges.
- Android TV only: D-pad first (drive devices with `adb shell input keyevent KEYCODE_DPAD_*`, not
  taps), no phone or foldable layouts, keep `MainActivity`'s `configChanges`.
- Direct play first: never remove MPV or the Media3 FFmpeg and AV1 decoders, or weaken their R8
  keep rules, to save size.
- Tests stay on JUnit4, Robolectric, MockK and Hilt. No test-framework migration and no new
  dependency without a plan entry.
- Kid profiles never reach system Settings, the external world or grown-up screens without the
  parent gate.
- No analytics, telemetry or third-party uploads (Codecov, Perfetto share links, crash services).
  Traces and logs from devices stay local.
