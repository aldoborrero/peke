# Feature Specification: The grown-up area: rules that only edit, a stats page, the new menu

**Feature Branch**: `033-grown-up-area` (from `030-tv-rules`; its PR supersedes 030's)

**Created**: 2026-11-19

**Status**: Implemented

**Input**: The owner's approved brief "Zona de adultos: normas y estadísticas" (2026-11-19, "Vale me gusta"), with
the sketches' proposals: the TV shows today, the last 7 days and the most watched; one household switch for advanced
stats on the plugin; the names "Descansos", "Siguiente episodio solo", "Estadísticas". The API with the Kids plugin is
the stats contract agreed the same day (`GET /Kids/Users/{id}/Stats?days=N`, feature `stats`).

**Supersedes**: spec 030's "{Kid}'s day" (D11, D12, D14, D15, FR-013, FR-014) and its read-only rows (D1's text rows,
FR-003, FR-005's "the rest MUST stay non-focusable text", D9); spec 006 FR-013 (the rules page's read-only text) and
FR-014 (the dashboard's address on the rules page). Spec 030's editors, save path, grant handling (FR-008, 006
FR-012) and unlimited autoplay (D10, FR-010, FR-011) still hold.

## Decisions

Defaults taken without asking the owner (standing rule for kids-mode work); each is easy to change. The layout and
words follow the canvas page "Zona de adultos" (boards ZAMenu, ZARules, ZAStats and their states), drawn the same day.

- **D1. One page, one job.** "Rules" only edits; "Stats" only informs. What Jellyfin decides (rating, libraries,
  allowed hours), live TV channels, picks and the dashboard's address leave the TV.
- **D2. The rules, full width.** Four rows in 030's order, each with an icon, its name, one sentence that follows the
  value shown, and ‹ value ›: Daily time ("Can watch 1 hour each day"; No limit: "Can watch as much as they like"),
  Breaks ("Rests 15 min every 30 min in a row"; without the status: "Rests after 30 min in a row"; No limit: "Can
  watch without stopping for a break"), Autoplay, named "Next episode by itself" ("Stops after 3 episodes in a row";
  Off: "Each episode waits for them to pick the next"; No limit: "The next episode always starts by itself"), and
  Search ("Can search for series and films"; Not allowed: "Only what's on their home"). The sentence follows the value
  while it is changed, so it says what OK would save; "OK saves · Back leaves it as it was" stays as a second line
  then. The old label "Time in a row, then a break" with "Then a break" under it said the same twice; "Breaks" and
  its sentence replace both. Values are written as the boards do: "1 h", "45 min", "1 h 30", "3 in a row" ("3
seguidos"), "Yes"/"No" ("Sí"/"No"), "No limit". Each row has its icon in a rounded square, coloured per rule
  (daily time "time", breaks "secondary", next episode "primary", search "tertiary"). "Saved" shows by the name of the
  rule just saved; "OK saves · Back leaves it as it was" sits under the row while its value isn't saved.
- **D3. One word for no limit.** "No limit" ("Sin límite") for daily time too, which said "Unlimited".
- **D4. Breaks only with the plugin.** The Breaks row needs `sessionLimit` as in 030 D6. Without it the row is gone,
  not text: the page has no read-only rows.
- **D5. The kid switch, variant (a).** With two kids or more on this TV, the header holds one avatar chip per kid
  (avatar and name), the kid being shown marked. The page opens on the kid watching the TV; Up from the first rule
  reaches the chips, Left and Right move between them, OK shows that kid's rules, Down returns to the rules. With one
  kid there are no chips. The switch is one composable (`KidSwitch`), so variant (b), a "Change" button in the header,
  replaces only it. The owner picks between them on the canvas.
- **D6. Another kid's rules.** They come from the cache, refreshed from the server with that kid's own token when
  shown, and are saved with the grown-up's grant like the current kid's (the plugin lets a grant holder change any
  kid). A change the grant ran out for is saved, after the code, for the kid it was made for.
- **D7. Stats.** A page, "Stats" ("Estadísticas"), with one card per kid of this TV in this TV's order: the avatar and
  name with a status pill (on a break with the minutes left, else time's up, else "Watching now" for the kid whose
  session is open), then watched today, left today (or No limit) and extra time today (from each kid's `/Status`, asked
  with their own token, as the picker does); then, with the plugin's `stats`, the last 7 days as bars with the daily
  allowance line, and with `AdvancedStats` "Most watched this week" (two).
- **D8. Bars.** One bar per day of `Days`, oldest first, today last, height by `UsedMinutes`, scaled to the largest of
  the week's minutes and allowances. Today's bar is "time" (yellow); a day over its limit (`UsedMinutes` above
  `AllowanceMinutes` + `BonusMinutes`) is "primary" (coral); the rest "secondary". Each day with an allowance has a
  dashed mark at it, so a week at one allowance reads as one line; an unlimited day has none. Day letters under the
  bars come from the TV's locale.
- **D9. States.** Without `stats`: today only, no bars, and a line that the last 7 days come with a newer Kids plugin.
  With `stats` and `AdvancedStats` off: no "Most watched", and one line at the end that "Advanced stats" can be turned
  on in the plugin's page of the dashboard. With it on: the line says more detail is on the plugin's dashboard page.
  Offline: when no kid's status came from the server, one card instead of the kids' ("Can't reach the server at
  home"); when only some, those cards show the last status cached with an "Offline" pill, or "No figures yet". 3+
  kids: the cards are a row that scrolls sideways, two in view; one kid gets a wider card. A legend (today, over the
  limit, daily time) sits by the title when there are bars; an over-limit day says by how much ("+15") above its
  bar, today's bar is labelled "Today", and the line is labelled with today's allowance. "Most watched" shows two.
- **D10. Stats and the grant.** The stats call carries the grant, like Add time. A 403 asks for the code, then loads
  again; leaving the code shows today only, with a line that the week needs the parent code.
- **D11. Focus.** Read-only: each card is a focus stop, Left and Right walk them, and the page opens on the first.
  Back returns to the menu focused on Stats (spec 025). The page asks for fresh figures on opening.
- **D12. Menu.** Add time, Rules, Stats, Switch kid (2+ kids), Settings. "Rules" and "Stats" have no kid's name: both
  cover every kid now. Lines as board "Menú de adultos": "More minutes for {Kid} today"; "Time, breaks, episodes and
  search"; "How each kid is doing today and this week" (with one kid, "How {Kid} is doing…"); "Who watches this TV";
  "This TV, parent code, advanced". Back from Rules, Stats and Settings returns to their card.
- **D13. Time's up.** A grown-up on Rules or Stats isn't thrown out by time's up, as 030 did for the day.

## User Scenarios & Testing

### User Story 1 — Change a rule of any kid (P1)

1. **Given** Rules, **When** the grown-up changes Next episode by itself to 5 and presses OK, **Then** only autoplay
   is sent for the kid shown and "Saved" shows.
2. **Given** two kids, **When** the grown-up presses Up, Right and OK, **Then** the second kid's rules show, and a
   change is saved for that kid.
3. **Given** the grant ran out, **When** OK is pressed, **Then** the code is asked and the change saved after it for
   the kid it was made for.

### User Story 2 — See how each kid is doing (P1)

1. **Given** a plugin with `stats` and advanced stats on, **Then** each kid's card shows today, seven bars with the
   allowance line, today and over-limit days marked, and the most watched.
2. **Given** advanced stats off, **Then** no "Most watched", and one line about turning it on.
3. **Given** a plugin without `stats`, **Then** today only.
4. **Given** the server can't be reached, **Then** the pill and the last cached figures.

### User Story 3 — The menu (P2)

1. **Given** one kid, **Then** Add time, Rules, Stats and Settings; **Given** two, Switch kid before Settings.
2. **Given** Rules, Stats or Settings opened from the menu, **When** Back, **Then** the menu focused on its card.

## Requirements

- **FR-001**: Rules MUST show only the editable rules, full width, each with an icon, its name, a sentence that
  follows the value shown and its stepper (D1, D2, D4).
- **FR-002**: Rules MUST NOT show today's use, allowed hours, live TV, picks, what Jellyfin decides or the dashboard's
  address.
- **FR-003**: With two kids or more Rules MUST offer a kid switch in the header, opening on the kid watching; saves
  and the grant MUST apply to the kid shown (D5, D6).
- **FR-004**: Stats MUST show one read-only card per kid of this TV with today's figures, and with `stats` the 7-day
  bars and the allowance line, and with `AdvancedStats` the most watched (D7, D8).
- **FR-005**: Stats MUST handle no `stats`, advanced stats off, offline and a 403 as D9 and D10 say.
- **FR-006**: The menu MUST list Add time, Rules, Stats, Switch kid (2+ kids) and Settings, and Back from each page
  MUST return to its card (D12).
- **FR-007**: Strings MUST ship in English and Spanish.
