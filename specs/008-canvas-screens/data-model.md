# Data model: Canvas screens

No stored data changes. The feature adds small value types that decide layout from state the app already has.

| Type | Fields | From | Rule |
|---|---|---|---|
| `KidsCardKind` | WIDE, CHANNEL, POSTER | a home row's `HomeRowConfig` | keep watching, next up, their combined row → WIDE; TV programmes and channels → CHANNEL; the rest → POSTER |
| `HomeTimeLeft` | minutes, today, nearEnd | `KidsLimits`, `KidsTimerBarPosition` | null without a daily limit, while unknown, or when the bar is Hidden; minutes rounded up and never 0; `today` when the allowance runs out no later than the sitting |
| Season circle | background, text colour | the season's index, focus | not focused: secondary, tertiary, time, primary in turn with "on primary"; focused: the background colour with the number in "time" |
| `InARow` | n, cap | the player's autoplay count and the profile's cap | shown only with a cap; n is the count of episodes played in a row including the one ending, capped at the cap |
| Channel now/next | now, next, progress | a channel's programmes and the clock | now: the programme whose start ≤ now < end; next: the first that starts at or after now's end; progress = elapsed / length, 0–1 |
| Tile colour | — | an item or channel id | primary, secondary, tertiary, time in turn by the id's hash |
