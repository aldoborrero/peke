# Implementation Plan: Episode label without a stray "/"

**Spec**: [spec.md](spec.md)

## Approach

- `kids/home/KidsHomeLayout.kt`: `keepWatchingEpisode` keeps the name only when it has a letter or digit
  (`String.hasWords()`), instead of only when it isn't blank. `keepWatchingTitle` and `keepWatchingDescription` in
  `KidsHomeUi.kt` already leave out a null name, so they need no change.
- `KidsHomeLayoutTest`: a test for the name rules and the season.

No change to strings, other cards or pages.
