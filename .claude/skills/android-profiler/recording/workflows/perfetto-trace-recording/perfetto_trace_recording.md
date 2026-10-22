---
name: perfetto-trace-recording
description: 'Records Java/native heap dumps, system traces, or custom configs with
  adb shell perfetto on Android (helper scripts only with approval).'
keywords:
- perfetto
- record
- system trace
- heap dump
- memory leak
- custom config
---

> \[!IMPORTANT\] **Scope:** This guide is **strictly for recording traces on
> Android devices** . For other platforms (Linux, macOS, or Chrome), refer to the
> platform documentation on [perfetto.dev/docs](https://perfetto.dev/docs/).

In Peke, prefer plain `adb shell perfetto … && adb pull`: it needs no download. The
official Perfetto helper scripts (`record_android_trace`, `java_heap_dump`,
`heap_profile`, `cpu_profile`) handle pushing configs and pulling traces, but use them
only after the user approves, pinned to a release tag.

Ensure **Developer options** and **USB debugging** (or network debugging) are enabled
and the device shows in `adb devices`. TV boxes usually use network adb:
`adb connect HOST:5555`.

Save configs and pulled traces in the session scratchpad, never in `/tmp/` or the repo.
Traces stay local: never upload them or open them in ui.perfetto.dev.

*** ** * ** ***

## 0. Record without downloads (default)

Write the config to the scratchpad (start from `references/example-configs/`), then:

    cat "$SCRATCH/config.pftxt" | adb shell perfetto -c - --txt \
        -o /data/misc/perfetto-traces/trace.perfetto-trace \
      && adb pull /data/misc/perfetto-traces/trace.perfetto-trace "$SCRATCH/"

`$SCRATCH` is the session scratchpad. Example configs cover jank, CPU sampling, Java
and native heaps, and memory counters, so this path handles every section below.

### Helper scripts (only with the user's approval)

If the user approves a download, fetch the scripts from a Perfetto release tag, not
`main`, and keep them in the scratchpad. Match the devshell's Perfetto (v57.2):

    TOOLS_URL="https://raw.githubusercontent.com/google/perfetto/v57.2/tools"
    cd "$SCRATCH"
    curl -O "$TOOLS_URL/record_android_trace" && chmod +x record_android_trace
    # likewise java_heap_dump, heap_profile, cpu_profile

Always pass `--no-open` to `record_android_trace`, and `-o "$SCRATCH/…"` for output.
The commands below show the helper-script form; each has a config equivalent for §0.

*** ** * ** ***

## 1. Memory Tracing

Use these tools to analyze memory leaks, object retention, C/C++ allocations, or
system-wide memory counters.

### A. Java Heap Dump (ART)

Capture a snapshot of all Java objects in a process to investigate memory leaks.
Reference docs: <https://perfetto.dev/docs/data-sources/java-heap-profiler>.

    # Trigger a Java heap dump for a specific app
    ./java_heap_dump -n YOUR_APP_PACKAGE_NAME -o "$SCRATCH/heap_dump.perfetto-trace"

### B. Native C/C++ Heap Profiling (heapprofd)

Track C/C++ memory allocations (malloc/free) to find native leaks. Reference
docs: <https://perfetto.dev/docs/data-sources/native-heap-profiler>.

    # Profile native allocations for a specific app
    ./heap_profile -n YOUR_APP_PACKAGE_NAME

    # Profile with custom sampling interval (default is 4096 bytes)
    ./heap_profile -n YOUR_APP_PACKAGE_NAME -i 2048

### C. System-wide Memory Counters

Track RSS, Swap, and process stats over time. Reference docs:
<https://perfetto.dev/docs/data-sources/memory-counters-sys-stats>.

**Note:** To record memory counters, you must use a custom config via
`record_android_trace` (see Section 4).

*** ** * ** ***

## 2. Stack Sampling / Callstack Profiling (traced_perf)

Identify CPU hotspots in C/C++ or Rust code by periodically sampling callstacks.
Reference docs: <https://perfetto.dev/docs/data-sources/cpu-profiler>.

    # Profile CPU usage by sampling callstacks at 100Hz (default) for 10 seconds
    ./cpu_profile -n YOUR_APP_PACKAGE_NAME -d 10000

    # Profile at a custom frequency (e.g., 200Hz)
    ./cpu_profile -n YOUR_APP_PACKAGE_NAME -f 200

*** ** * ** ***

## 3. System Tracing (CPU, Scheduling, \& ATrace)

Investigate jank, slow transitions, CPU scheduling, and system calls. Reference
docs: <https://perfetto.dev/docs/data-sources/ftrace>.

You can specify duration, buffer size, and categories directly on the command
line:

    # Record scheduling, frequency, and window manager events for 5 seconds
    ./record_android_trace --no-open -t 5s -b 32mb sched gfx wm -a YOUR_APP_PACKAGE_NAME -o "$SCRATCH/trace.perfetto-trace"

Common categories: `sched` (CPU scheduling), `freq` (CPU frequency), `gfx`
(Graphics), `am` (Activity Manager), `wm` (Window Manager), `view` (View
System).

*** ** * ** ***

## 4. Custom Configs (Mix \& Match Data Sources)

If you need a custom mixture of data sources (e.g., combining Java heap dumps
with ftrace), or control that command-line flags do not offer (ring buffers,
long traces, per-counter polling), synthesize a config:

1. **Read the config reference:** `$SKILL_ROOT/recording/workflows/perfetto-trace-recording/references/trace_config_reference.md` explains config structure and data sources, pointing to exemplar configs in `$SKILL_ROOT/recording/workflows/perfetto-trace-recording/references/example-configs/` that you can start from and merge. For other sources, consult the official guide at <https://perfetto.dev/docs/data-sources/>.
2. **Save the Config:** Write the synthesized text configuration to the scratchpad (e.g., `$SCRATCH/config.pftxt`).
3. **Execute the Trace:** Record it with plain `adb shell perfetto` (§0), or, if the user approved the helper scripts, with `record_android_trace` (specialized scripts do not accept custom configs):

    ./record_android_trace --no-open -c "$SCRATCH/config.pftxt" -o "$SCRATCH/my_trace.perfetto-trace"

If the config has a typo, this fails fast with a parse error naming the bad
field - fix the config and retry.
