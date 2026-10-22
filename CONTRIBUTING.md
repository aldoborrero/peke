# Contributing to Peke

Thanks for helping. Peke is a small project, so a clear issue is often as useful as a pull request.

## Code of conduct

Peke follows Jellyfin's [Code of Conduct](https://jellyfin.org/docs/general/community-standards/#code-of-conduct).

## Issues

- Bugs: say what you did, what you expected and what happened, with the device, the Peke version, the Jellyfin
  version and the Jellyfin Kids plugin version. Logs help (for example from `adb logcat`); check them for personal
  information before posting.
- Server-side rules (allowances, the parent code, enforcement, live TV channels) live in the
  [Jellyfin Kids](https://github.com/aldoborrero/jellyfin-kids) plugin. Report those there.
- Feature ideas: describe the problem for a kid or a grown-up first, then the change you have in mind.
- If you used an AI tool to write the issue, say so at the end of it, under "AI usage".

## Pull requests

Open an issue before a large change, so we can agree on it first. Peke is built for one audience, so features for
grown-ups watching (rather than setting the TV up) usually belong in another Jellyfin client.

Before you open the pull request:

1. Work in the Nix devshell (`direnv allow`, or `nix develop`). See [DEVELOPMENT.md](DEVELOPMENT.md).
2. Run the tests: `nix develop -c ./gradlew testDebugUnitTest`.
3. Format: `nix fmt`. `nix flake check` fails on unformatted files.
4. Test on an Android TV device or emulator with the remote's D-pad, and add screenshots if the change touches the
   interface.
5. Add English and Spanish strings for any new text. A unit test fails when a Spanish translation is missing.

Larger features are planned with [spec-kit](https://github.com/github/spec-kit) under `specs/`, following the project
constitution in `.specify/memory/constitution.md`.

By submitting a pull request, you agree that your contribution is licensed under the [GPLv2](LICENSE), like the rest
of Peke.

### AI-assisted contributions

You may use AI tools for issues and pull requests. In an issue, say so at the end, under "AI usage". In a pull
request, you must understand the change and be able to explain it in your own words, and say which parts were
AI-assisted. Agents working in this repository follow [AGENTS.md](AGENTS.md).
