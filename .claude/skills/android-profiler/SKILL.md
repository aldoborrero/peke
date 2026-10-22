---
name: android-profiler
description: Record or analyse a Perfetto trace or heap dump from an Android TV device.
  Use only when the user asks to record or analyse a trace, or provides a .perfetto-trace/.pftrace
  file.
license: Complete terms in LICENSE.txt
metadata:
  author: Google LLC
  last-updated: '2026-10-17'
  keywords:
  - android performance
  - debugging
  - profiling
  - recording
  - trace analysis
  - memory leaks
  - bottleneck
  - jank
  - SQL
---

Your primary role is to figure out what the user wants to do (disambiguation),
find the right instructions for it (workflow discovery), and follow them
step-by-step (following the execution plan). If the request is unclear, work
with the user to finalize the execution plan before proceeding.

## Peke

- Tools come from the Nix devshell: `adb`, `perfetto`, `trace_processor_shell` (v57.2).
  No unpinned downloads; see `references/perfetto/setup.md`.
- Devices are TV boxes on USB or network adb (`adb connect HOST:5555`). Drive them with
  `adb shell input keyevent KEYCODE_DPAD_*`, not taps.
- Keep traces, configs and SQL files in the session scratchpad, never in `/tmp/` or the repo.
  Report findings in chat; no report files unless asked.
- Local trace files only: no URLs, no Perfetto UI share links, no uploads.
- Never suggest dropping MPV or the FFmpeg/AV1 decoders to fix a finding.

## Prerequisites and Setup

Before executing any workflows, read [`references/env_setup.md`](references/env_setup.md). It defines
what to set `$SKILL_ROOT` to - the anchor every other path in this skill is
written against.

## Intent Disambiguation

Do not guess the user's intent. If the user request is not clear, **ask the
user** what they want to do before proceeding.

## Recording

Route all recording requests through
[recording/recording_orchestrator](recording/recording_orchestrator.md). This defines guidelines and
pre-flight checks or dependency checks that apply to all recording workflows,
and ensures you have the necessary setup to proceed. Read the orchestrator and
execute the plan it describes based on what the user wants to record (for
example, a system trace or a heap dump).

## Analysis

Route all analysis requests through
[analysis/analysis_orchestrator](analysis/analysis_orchestrator.md).
