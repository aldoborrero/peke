> **Prerequisite:** Ensure `$SKILL_ROOT` is initialized per
> `$SKILL_ROOT/references/env_setup.md`.

## Use the devshell's `trace_processor_shell`

In Peke the Nix devshell provides `trace_processor_shell` (Perfetto v57.2, pinned) and
`perfetto`. Run it through the devshell:

    nix develop -c trace_processor_shell --version   # smoke test

Every bare `trace_processor ...` command in this skill means
`nix develop -c trace_processor_shell ...`. Don't download prebuilts.

## Fallback: the bundled wrapper (only with the user's approval)

The skill ships a `trace_processor` wrapper at
`$SKILL_ROOT/references/perfetto/bin/trace_processor`. On first run it downloads
`trace_processor_shell` v57.2 (SHA-256 checked) into `~/.local/share/perfetto/prebuilts/`.
Use it only if the devshell binary is unavailable and the user approves the download:

    chmod +x "$SKILL_ROOT/references/perfetto/bin/trace_processor"
    export PATH="$SKILL_ROOT/references/perfetto/bin:$PATH"
    trace_processor --version

If each command runs in a fresh shell, repeat the `export` line in that command.
