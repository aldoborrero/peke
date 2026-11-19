# Feature Specification: Episode label without a stray "/"

**Feature Branch**: `029-episode-label`

**Created**: 2026-11-14

**Status**: Implemented

**Input**: The owner saw "T8 · Ep. 1 · /" on a keep watching card for "Daniel Tigre y su mundo" on the Chromecast. The
card should read "T8 · Ep. 1".

## Cause

The card's line joins the episode number and the episode's name with " · " (`keepWatchingTitle`), and the name
already dropped blanks. The app adds no "/": the strings are "T%1$d · Ep. %2$d" and "Ep. %1$d", and the join has a
single separator, which the card shows before the "/". So the "/" is the episode's name
as the server sends it, likely a library naming the episode from a file or metadata with no real title. Peke can't
log in to the owner's server to confirm the exact name.

## Decisions

Design defaults taken without asking the owner (standing rule for kids-mode work):

- **D1. A name with no words is no name.** An episode name counts only if it has a letter or a digit (any script:
  "Día 3", "1/2", "大"). Names of only punctuation, symbols or whitespace ("/", "-", "...", " ") are dropped, and the
  card reads as with no name: "T8 · Ep. 1", or the series when there's no number either (unchanged rule).
- **D2. Kept names are not cleaned.** A name with words stays exactly as the server sends it; Peke doesn't trim
  punctuation inside or around it.
- **D3. Only the keep watching label.** The rule lives in `keepWatchingEpisode`, so the card's title and what
  TalkBack reads both drop the name. Other cards and pages show the server's names as before.

## Requirements

- **FR-001**: A keep watching episode whose name has no letter or digit MUST show without its name (D1).
- **FR-002**: A name with a letter or digit MUST show unchanged (D2).
- **FR-003**: Season and number rules stay as before: the season only past the first and with a number.

## Success Criteria

- **SC-001**: Unit tests cover a name present, empty or null, only punctuation ("/"), and no season; the suite stays
  green.
