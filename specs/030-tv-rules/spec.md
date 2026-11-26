# Feature Specification: Edit a kid's rules on the TV

**Feature Branch**: `030-tv-rules`

**Created**: 2026-11-15

**Status**: Implemented

**Input**: The owner, 2026-11-15: "las normas de Manuel no permite configurar una puta mierda… lo que quiero es que se
pueda configurar en la TV también". "{Kid}'s rules" changed only the daily time and sent everything else to the
dashboard, which is no use from the sofa.

**Supersedes**: spec 006 FR-011 (daily time the only editable rule), FR-013 for the rows below (they are no longer
text), and FR-015 for the sitting, search and autoplay editors (they come back, on this page). FR-012 (grant ran out)
and FR-014 (the dashboard's address) still hold.

## Decisions

Design defaults taken without asking the owner (standing rule for kids-mode work); the owner approved the list of
rules.

- **D1. Four rules on the TV.** Daily time (as before), Time in a row (the sitting before the break), Autoplay and
  Search are changed on the page. Everything Jellyfin decides (rating, unrated, libraries, allowed hours), live TV
  channels, today's use, picks and home sections stay text; picks and home sections stay on the dashboard (choosing
  collections with a remote is clumsy).
- **D2. One kind of row.** All four are board 10's stepper row, as daily time already was: Left and Right change the
  value shown, held at the ends with the arrow dimmed; OK saves; the line under the label says "OK saves · Back leaves
  it as it was" while the value isn't saved. Search is a two-value stepper (Not allowed, Allowed) rather than a
  switch, so every row works the same way.
- **D3. Leaving a row drops what isn't saved.** Up or Down to another row, or Back, shows the saved value again. Only
  one change is ever waiting, and nothing is saved by moving.
- **D4. Order and focus.** Daily time, Time in a row, Autoplay, Search; the first is focused on open. Up and Down move
  between them; Up on the first and Down on the last scroll the text below, as Up and Down on daily time did.
- **D5. Choices.** Time in a row: 10, 15, 20, 30, 45, 60, 90, 120 min with the current value in order, then No limit
  last (like daily time's Unlimited, so a hurried OK doesn't remove the limit). Autoplay: Off, then 1 to 10 episodes,
  then No limit (D10); Off keeps the cap for when it's on again. Search: Not allowed, Allowed.
- **D6. Time in a row only with the plugin.** It is a row only when the plugin advertises `sessionLimit`: the PUT
  carries it only then. A profile that has the field without the feature shows it as text, as before.
- **D7. Lines.** Daily time: "Counted on the server across every device". Time in a row: "Then a 15 min break" (or
  "Then a break" without the status). Autoplay: "The next episode starts by itself". Search: voice search on or off
  for this TV (a per-TV setting, changed in Settings).
- **D8. Saving.** Each OK saves only that rule on the profile as last saved; every other field, known or not, goes
  back unchanged. "Saved", the grant ran out (the change kept, the code asked, then saved) and errors work as for daily
  time.
- **D9. Words.** The card on the right says which rules are changed on the TV and that picks and home sections are on
  the dashboard; its title is "Change {Kid}'s picks and home on the dashboard".

## User Scenarios & Testing

### User Story 1 — Change a rule from the sofa (P1)

1. **Given** "{Kid}'s rules", **When** the grown-up moves to Autoplay, presses Right to "5 episodes" and OK, **Then**
   only autoplay is sent and "Saved" shows.
2. **Given** a value changed but not saved, **When** they move to another row or press Back, **Then** the rule stays
   as it was.
3. **Given** the grant ran out, **When** OK is pressed on a change, **Then** the code is asked and the change is saved
   after it; Back on the code says it wasn't saved.

### User Story 2 — Time in a row needs the plugin (P2)

1. **Given** a plugin with `sessionLimit`, **Then** Time in a row is a row between Daily time and Autoplay.
2. **Given** a plugin without it, **Then** Time in a row is text (or absent) and not a focus stop.

## Requirements

- **FR-001**: The page MUST let a grown-up change Daily time, Autoplay (Off or 1–10 episodes) and Search, each saved on
  OK (D1, D2, D5).
- **FR-002**: Time in a row MUST be changeable only when the plugin advertises `sessionLimit` (D6).
- **FR-003**: Otherwise a sitting limit the server sent MUST be shown as text.
- **FR-004**: A save MUST change only that rule; every other field of the profile MUST be sent as last saved (D8).
- **FR-005**: The editable rows MUST be the only focus stops, in the order of D4; the rest MUST stay non-focusable text.
- **FR-006**: A value not saved MUST be dropped when focus leaves its row or the page (D3).
- **FR-007**: The autoplay cap MUST stay within 1–10.
- **FR-008**: A change refused because the grant ran out MUST be kept, the code asked again and the change saved after
  (006 FR-012, for every rule).
- **FR-009**: Strings MUST ship in English and Spanish.

## Second round (2026-11-16)

**Input**: the owner tried the first round on the emulator. "Da una opción para que autoplay sea infinito." "Los stats
de Manuel deberían estar en otra pantalla."

### Decisions

- **D10. Autoplay without a limit.** The Autoplay stepper ends with "No limit" ("Sin límite") after 10 episodes, only
  when the plugin advertises `unlimitedAutoplay`; it is saved as `AutoplayCap = 0`. Older plugins only take 1–10, so
  without the feature the stepper stays Off, 1–10 and the app never sends 0. A profile already saved with 0 keeps it
  as a choice. 0 is no cap for the player: autoplay never stops on the count and the "n of N" chip is hidden. The cap
  stays soft as today (task 031); this round doesn't change when the count resets.
- **D11. "{Kid}'s day".** Today's use moves off the rules to its own page, "{Kid}'s day" ("El día de {niño}"):
  watched today (of the allowance with time added), left today, time added today, watched in a row (with the sitting
  limit) and the break left, as rows of text. Before the first status it says the server hasn't answered, or that it
  can't be reached. The page asks for a fresh status on opening.
- **D12. Reached from the menu.** The parent menu has a "{Kid}'s day" card right after "{Kid}'s rules" (only with a
  kid). Back from the day shows the menu again focused on it, as spec 025 does for the rules; the menu's other items
  keep their order. The owner may move it.
- **D13. The rules keep the rules.** "{Kid}'s rules" no longer shows today's use. The household break stays with
  time in a row (under its row, or in its text row), since it is a rule, not a figure of the day.
- **D14. Grant and time's up.** The day is a grown-up page: a grant that ran out asks for the code first, as for the
  rules. A grown-up on the day isn't thrown out by time's up; it shows when they leave, as on the rules.
- **D15. Focus.** The page has no rows to focus: the page itself holds focus, so keys stay on it and Back leaves.

### Requirements

- **FR-010**: Autoplay MUST offer No limit last only when the plugin advertises `unlimitedAutoplay`, and save it as
  `AutoplayCap` 0; without the feature it MUST never send 0 (D10).
- **FR-011**: A cap of 0 MUST be kept by the app and MUST never stop autoplay on the count; the in-a-row chip MUST be
  hidden (D10).
- **FR-012**: "{Kid}'s rules" MUST NOT show today's use (D13).
- **FR-013**: "{Kid}'s day" MUST show today's figures as text with no focus stop of its own, open from the parent menu
  and go back to it focused on its item (D11, D12, D15).
- **FR-014**: The day MUST follow the grown-up grant like the rules, and MUST NOT be replaced by time's up (D14).
