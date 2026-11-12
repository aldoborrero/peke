# Research: The parent gate as on the canvas

## R1. Page or navigation destination

- **Decision**: a full-screen `Dialog` window (`usePlatformDefaultWidth = false`) filled with the background.
- **Rationale**: the gate is opened from nine places (picker, stops, sidebar, grown-up guard, rules page, …) as a
  composable with `onDismissRequest`/`onPassed`. A window over them keeps every result, Back handling and the
  background-epoch close without touching navigation. It also keeps keys from reaching the screen behind.
- **Alternatives**: a Navigation 3 destination — every caller would need a result channel and the stop screens'
  timers would leave composition; rejected.

## R2. Where the refusal lives

- **Decision**: `GateStage.Passcode(type, attempt, refusal)`. Any refusal bumps `attempt` (which clears the entry)
  and sets `refusal`; the page hides it once a key is typed. `GateStage.WrongCode` and `retryCode()` go.
- **Rationale**: the page renders state; no Toast from a callback. `submit`'s `onRefused` stays as an optional
  callback so existing calls compile; the gate no longer passes it.
- **Alternatives**: keep the `WrongCode` stage and draw it as the page — two stages for one screen; rejected.

## R3. Right side for the number type

- **Decision**: a 3×4 pad (1–9, then 0 and OK), keys in surface, OK in primary, same radii as the cross.
- **Rationale**: matches what a number code is typed with and keeps the board's composition.
- **Alternatives**: no drawing for numbers (empty half), or the cross for both (wrong: arrows don't type numbers).

## R4. Sizes

Board px through `cpx`/`csp`: padding 64, gap 80, left column 480 max, gap 28; icon 64; title 44/900
(`displaySmall`); hint 24/800; dots 40, gap 18, outline 4; Back pill 22/800, radius 28, padding 12×28; cross 380 wide,
keys 110 high, gap 18, radius 28, OK round (55), OK text 26/900, arrows 44. Number pad keys 80 high to fit four rows
in the 720 board height. Pills from S5: radius 26, padding 20×24, icon 48, text 21/900 (wait) and 19/800 (can't
reach), "Wrong code" 22/900 in primary.

## R5. Icons

Lock: `PekeIcon.GROWN_UPS` outline. Pills: `TIME_LEFT` outline in "time" for too many tries, `LIVE` outline (a TV) in
secondary for can't reach, as S5 draws a timer and a TV. Back pill: `BACK` outline.
