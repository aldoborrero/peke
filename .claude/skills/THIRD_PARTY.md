# Third-party skills

The skills in this folder are development-time reference material. They are not built into
the APK. Each keeps its licence file next to its content. When a skill conflicts with
`.specify/memory/constitution.md` or `AGENTS.md`, those win.

## android/skills (Google LLC, Apache-2.0)

Source: https://github.com/android/skills, commit `95ea931a1ad8f9b2be132ae0cfb2aad964d71118`
(2026-10-21). Each folder has `LICENSE.txt`, a copy of the upstream root `LICENSE.txt`
(Apache-2.0, unmodified).

The mirrored developer.android.com pages under each skill's `references/android/` are
CC BY 4.0 prose with Apache-2.0 code samples (https://developer.android.com/license).
`android-profiler/references/perfetto/bin/trace_processor` keeps its AOSP Apache-2.0 header.

### agp-9-upgrade

Path: `build-system/agp/agp-9-upgrade`. Local modifications:

- Moved `references/android/build/` to `references/android/build-docs/` (the repo's `build/`
  ignore hid it) and updated the links in `SKILL.md`.
- `references/buildconfig.md` and `references/recipes.md` link each other locally instead of
  developer.android.com/agents.
- Added a Peke section: already on AGP 9.2.1; don't remove `android.*` flags from
  `gradle.properties` without asking; Gradle through `nix develop -c`.
- Step 6 asks before removing flags. Verification uses `nix develop -c ./gradlew …`.

### android-intent-security

Path: `security/android-intent-security`. Local modifications:

- Added a Peke note: intent paths that skip the parent gate from a kid profile are
  vulnerabilities.
- Replaced the "minSdk is 36" comments with minSdk 23 notes; wrapped `setStrictColumns` and
  `setStrictGrammar` in an API 29 check; noted the API 28 guard for `hasSigningCertificate`.
- `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`: check the merged manifest (androidx.core adds it)
  instead of declaring it by hand.
- The hardening report is optional and goes in chat, not a file.

### android-permissions-security

Path: `security/android-permissions-security`. Local modifications:

- Narrowed the description to merged-manifest permission and exported-component audits.
- Added a Peke section: justify every permission in the plan (Principle VI); audit the merged
  manifest; kid profiles never open system Settings; phone-centric samples mostly don't apply.
- Dropped the prerequisite that adds `core-ktx`/`activity-ktx`; never add dependencies from the
  skill.
- Replaced the `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` redirect (audit table, §6 step 4,
  `RuntimePermissionsActivity` sample, checklist) with graceful degradation or the parent gate.
- `grantUriPermissions="true"` is kept only when a provider really shares URIs.
- `CallerVerifier`: returns the API 28+ result directly; the API 23-27 `GET_SIGNATURES`
  fallback requires every signer to match.

### android-profiler

Path: `profilers/android-profiler`. Local modifications:

- Narrowed the description to explicit trace recording or analysis on Android TV.
- Added a Peke section: devshell tools, USB or network adb, D-pad input, scratchpad, chat
  reports, local traces only, never drop MPV or decoders.
- `references/perfetto/setup.md`: use the devshell's `trace_processor_shell` (v57.2); the
  bundled `bin/trace_processor` downloader is a fallback only with the user's approval.
- Recording workflow: §0 records with plain `adb shell perfetto … && adb pull`; the helper
  scripts are optional, need approval, and are pinned to the `v57.2` tag instead of `main`.
  `record_android_trace` always gets `--no-open`; outputs go to the scratchpad.
- `references/perfetto/sql.md`, `perfetto_sql.md`, `perfetto_trace_analysis.md`: local trace
  files only (no URLs or ui.perfetto.dev links); SQL files in the scratchpad, not `/tmp/`;
  report in chat instead of `perfetto_analysis_report.md`.
- Restored the YAML frontmatter (`name`, `description`, `keywords`) that upstream had mangled
  into markdown in the 3 workflow files and 6 hint files, so the orchestrators' `name:`
  discovery works. The `name` values are new (taken from the file names).
- Orchestrators: `grep_search` (a Gemini tool name) replaced with an `rg` example.
- `trace_config_reference.md`: mentions `adb shell perfetto` alongside `record_android_trace`.

### anr-audit-and-fix

Path: `performance/anr-audit-and-fix`. Local modifications:

- Fixed three corrupted lines in `references/anr_trace_report_template.md` (`<>/span>`,
  `term>ination`, `b>ypassed`).
- `references/trace_analysis_guide.md` links the template locally and says
  `registerCalledFromWrongThreadListener` is for local logging only (Principle VI), not
  production telemetry.

### r8-analyzer

Path: `performance/r8-analyzer`. Local modifications:

- Added a Peke section: Path A doesn't apply on AGP 9.2.1; Gradle and Python through the
  devshell; never `pip install`; outputs in `app/build/reports/r8/` or the scratchpad; keep
  rules for MPV JNI, Media3, kotlinx-serialization and the Jellyfin SDK stay without a release
  build and a direct-play test; never trade decoders for size.
- Path A steps 2-3 called `scripts/*.py` that upstream doesn't ship; rewritten to use the
  inline scripts in `references/CONFIGURATION-ANALYZER.md`.
- `references/CONFIGURATION-ANALYZER.md`: outputs moved from `tmp/r8analysis` to
  `app/build/reports/r8/keepradius`; scripts live in the scratchpad; protobuf from the
  devshell; fixed the §5 indentation error; §8 no longer points at a missing script;
  `./gradlew` runs through `nix develop -c`.
- `references/CONFIGURATION.md`: optimized resource shrinking applies to AGP 8.12-8.13
  (was "more than 8.6"), matching the bundled AGP doc.
- `references/REDUNDANT-RULES.md`: the Gson note holds only with `@SerializedName` and does
  not cover kotlinx-serialization (Jellyfin SDK).

## humanizer

Source: https://github.com/blader/humanizer v3.1.0, MIT, "Copyright (c) 2025 Siqi Chen".
Licence: `humanizer/LICENSE`. Patterns credited to Wikipedia's "Signs of AI writing".

Local modifications: kept only `SKILL.md` and `LICENSE`. Removed `AGENTS.md` (maintainer
instructions that Claude Code injects and that tell agents to run `npx`), `README.md`,
`CHANGELOG.md`, `agents/`, `scripts/`, `.claude-plugin/`, `.cursor-plugin/` and `.github/`.

## crafting-effective-readmes

Source: https://github.com/joshuadavidthomas/agent-skills (skill `crafting-effective-readmes`),
MIT, "Copyright (c) 2025 Josh Thomas". Licence: `crafting-effective-readmes/LICENSE`.

Bundled references keep their own terms:

- `references/art-of-readme.md`: hackergrrl/art-of-readme, CC BY 2.0.
- `references/make-a-readme.md`: makeareadme.com by Danny Guo.
- `references/standard-readme-spec.md` and `references/standard-readme-example-{minimal,maximal}.md`:
  RichardLitt/standard-readme, MIT.

Local modifications:

- Added Source lines to the two standard-readme examples; fenced the maximal example so its
  placeholder links aren't read as local files.
- `art-of-readme.md` translation links and the `standard-readme-spec.md` "historically" link
  point to the upstream repos (the files aren't bundled).
- Replaced the reference to the missing `writing-clearly-and-concisely` skill with `humanizer`
  (`style-guide.md`, `README.md`).
- The review step checks build files instead of `package.json`.
- Added a Peke note: GPLv2 and the Jellyfin branding rules ("Peke for Jellyfin"); the
  templates' MIT badges and npm steps don't apply.

## spec-kit (speckit-* skills and .specify/)

Source: https://github.com/github/spec-kit 1.2.0, MIT. The `speckit-*` skills and the managed
files in `.specify/` are unmodified (they match the SHA-256 values in
`.specify/integrations/*.manifest.json`).

Local modifications:

- Removed `speckit-taskstoissues` (deprecated upstream; creates GitHub issues without asking).
  `.specify/integrations/claude.manifest.json` still lists it, and a `specify` refresh may
  re-add it.
- Added `.specify/templates/overrides/tasks-template.md`: the core template with "Commit after
  each task or logical group" replaced by "Agents never run git write commands; the owner
  commits (signed)." The override takes priority and survives `specify` upgrades.

## Removed

Deleted from this folder: `adaptive`, `android-cli`, `navigation-event` (android/skills), and
`kotlin-patterns`, `kotlin-testing`, `kotlin-coroutines-flows` (https://github.com/affaan-m/ecc,
MIT, commit `90606c8315ee09cafc303515c5f33024db6b9882`): phone-, server- or KMP-oriented, or
in conflict with the constitution.
