# Feature Specification: The "Watch live" row stays, with the channel's logo

**Feature Branch**: `034-live-row`

**Created**: 2026-11-25

**Status**: Implemented

**Input**: On the kids home the "Watch live" row vanishes in the last minute of every programme and stays gone for up
to 30 minutes, and its card shows Peke's TV instead of the channel's logo (diagnosis in task 031's report, "TV en
directo"). Fix both without redesigning the card (task 031, waiting for the owner).

## Cause

- The row asks `POST /LiveTv/Programs` for `isAiring` with `minEndDate = now + 1 min`. In a programme's last minute
  it is excluded and the next one hasn't started: the row comes back empty and the home hides it.
- The home reads a row again on a short return only for what is being watched; the live row waited for a full reload
  (30 minutes away, or another day). Nothing read it while the home was shown.
- `ProgramItemFields` had no `ItemFields.CHANNEL_IMAGE`, so Jellyfin sent no `ChannelPrimaryImageTag` and the card
  fell back to Peke's TV.

## Decisions

Defaults taken without asking the owner (standing rule for kids-mode work):

- **D1. Airing means airing.** The row asks only `isAiring`: a programme stays until its end, and from its end the next
  one, which has started, takes its place.
- **D2. A timer to the end, not polling.** While the home is shown, the live row is read again 2 s after the first
  programme on it ends (time for the server to move on). If everything shown has already ended (the server's clock is
  behind) it tries again after 1 minute. Only that row is replaced; the rest of the home keeps its state.
- **D3. Read on return.** A return to the home reads the live row again with the keep watching rows. While the home
  is left the timer does nothing: the return reads the row.
- **D4. Same code on both homes.** The grown-up home's "Watch live" row uses the same query and view model, so it is
  fixed too. Search, which shares `ProgramItemFields`, also gets the channel's logo tag (harmless).

## Requirements

- **FR-001**: A programme airing now MUST be in the row until it ends (D1).
- **FR-002**: At a boundary with no gap the row MUST show the next programme, never nothing (D1, D2).
- **FR-003**: The live row MUST be read again on return to the home and when the first programme on it ends, without
  reloading the other rows (D2, D3).
- **FR-004**: The card MUST show the channel's logo when the channel has one; Peke's TV stays the fallback.
- **FR-005**: The card's layout and look, the Live TV page and the guide do not change.

## Success Criteria

- **SC-001**: Unit tests cover a programme ending in 30 s, a boundary with no gap, the timer at each end time, the
  return, and the card's image URL from the channel's tag; the suite stays green.
