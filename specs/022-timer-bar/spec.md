# Feature Specification: The kids' timer bar only while playing, its timer left, centre or right

**Feature Branch**: `022-timer-bar`

**Created**: 2026-11-12

**Status**: Implemented

**Input**: "The kids' time-left bar shows only while something is playing; on every other screen it isn't needed. A
household chooses where the bar's timer sits along the bar: left, centre or right."

Today (specs 006 and 008) the bar and its label are drawn on every kid page except the home, which keeps the time left
in its header, and the pages in the sidebar start below the band the bar takes. The label is always centred.

## Decisions

D1 is the owner's decision (2026-11-12). The others are design defaults taken without asking the owner (the owner's
standing rule for kids-mode work):

- **D1. "Playing" is the playback page while the video plays; a paused video doesn't count (owner's decision).** The
  bar shows on `Destination.Playback` and `Destination.PlaybackList` (live TV plays through `Playback` too), with the
  controls, Next Up or the episodes-in-a-row card on screen. While the video is paused the bar is hidden, and it shows
  again when playback resumes. "Paused" is the player's `playWhenReady` being false, that is the kid or the remote
  paused it; buffering isn't a pause, so the bar doesn't flicker while a video loads. Before a player exists the page
  counts as playing. This is about the bar only: the countdown between polls (`KidsTimeLimits.setPlaying`), warnings
  and time's up are unchanged.
- **D2. Nowhere else.** Home, details, search, favourites, live guide, grown-up pages, the gate and the stop screens
  draw no bar. The home keeps its time-left pill (it still hides with "Hidden"); warnings, chimes and the stop screens
  are unchanged.
- **D3. The band goes from the sidebar pages.** They never show the bar now, so they no longer keep room for it
  (`NavDrawer`'s `timeLeftBand` padding is removed). The band stays inside the timer UI: on the player the warning
  pill sits below the label when the bar is at the top, whatever its size and placement.
- **D4. Placement is a second setting.** `KidsTimerBarPosition` (top, bottom, hidden) stays as it is, with its stored
  key `kids_timer_bar`, so a TV that had chosen "At the bottom" or "Hidden" keeps it. The new
  `KidsTimerLabelPlacement` (start, centre, end) is stored under its own key `kids_timer_label`, default centre, which
  is today's look. Placement follows the layout direction (start is the left in English and Spanish).
- **D5. Left and right keep the overscan margin.** At the start or end the label keeps DESIGN's 64 px (48 dp) from the
  edge, as the player's title and controls do; the bar itself still runs edge to edge.
- **D6. One more row in This TV.** "Timer on the bar" right under "Timer bar", a choice of "On the left", "In the
  middle", "On the right" ("Tiempo en la barra": "A la izquierda", "En el centro", "A la derecha"). It shows even when
  the bar is hidden, like the other rows that only matter in some states.

## User stories

### US1 (P1): the bar only on the player

A kid browsing sees no bar; once a video plays the bar and its label show at the top or bottom as set, and keep
showing with the controls up or on Next Up. Pausing hides the bar; playing again brings it back.

**Acceptance**: on a playback page the bar is drawn while the video plays and not while it is paused; on the home,
details, search, a grown-up page or a stop screen it isn't; with "Hidden" it is never drawn.

### US2 (P1): the timer left, centre or right

A grown-up picks in Settings › This TV where the label sits; the player then draws it there, at the top or bottom.

**Acceptance**: each placement puts the label at the left, centre or right of the screen, at the top and the bottom;
an old stored bar position still reads the same; the Settings row changes the stored placement.

## Requirements

- **FR-001** The bar is drawn only while the page on top is a playback page and its video isn't paused (D1, D2).
- **FR-002** Warnings, chimes, time's up, the home pill and the countdown behave as before.
- **FR-003** The label's placement is start, centre or end, stored per TV, default centre (D4).
- **FR-004** The stored bar position keeps its key and values.
- **FR-005** Settings › This TV has a row for the placement, in English and Spanish (D6).
- **FR-006** At start or end the label keeps the overscan margin (D5); on the player the warning never covers it (D3).

## Out of scope

The home's time-left pill (it stays in the header), `KidsTimeLimits`, anything outside the kids timer.
