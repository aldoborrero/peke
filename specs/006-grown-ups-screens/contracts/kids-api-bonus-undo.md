# Contract: Kids plugin calls added for 0.0.9.0

The client side of the plugin's spec 013 (`jellyfin-kids` `specs/013-bonus-undo-break/contracts/kids-api-v1-bonus-undo-break.md`,
Kids client API 1.2, additive, `ApiVersion` still 1). The calls live in one place, `kids/server/KidsApi.kt`, and are
only made when `GET /Kids/Info` lists the feature. Undo and break end need the parent grant (`X-Kids-Grant`) with the
kid's own token, like the bonus.

| Feature | Call | Answer |
|---|---|---|
| (any) | `POST /Kids/Users/{id}/WatchTime/Bonus` `{"Minutes": n}` | 200 status, plus `BonusId` (integer) and `BonusUndoUntil` (UTC) with `bonusUndo` |
| `bonusUndo` | `DELETE /Kids/Users/{id}/WatchTime/Bonus/{bonusId}` | 200 status; 404 no such bonus (`No such bonus.`) or not a kid; 409 too old (`This bonus can no longer be undone.`); 403 grant |
| `breakEnd` | `POST /Kids/Users/{id}/WatchTime/Break/End` (no body) | 200 status, break over and sitting reset (also 200 when not in a break); 403 grant; 404 not a kid |

Client rules:

- Without the feature, the call is never made and the UI hides Undo / "Keep watching now".
- Peke offers Undo for 10 seconds, and never past `BonusUndoUntil` less `ServerTime` from the same answer (both on
  the server's clock): near the start of the kid's next day the server's window can be shorter. With 0 left, no Undo.
- An undo or break-end answer other than 200 leaves the status alone; 403 drops the grant (`NEEDS_GRANT`).
- Undo: 404 `No such bonus.` (undone on another TV, or cleared by Reset today) closes the confirmation with "That
  time was already taken back" and asks for a fresh status; 404 `This user is not a kid.` is a failure; 409 shows
  "This time can no longer be taken back" inline and hides Undo.
- Break end: a plain 404 (an older plugin without the route) is a failure, never "ended".
- The status in each 200 answer replaces the kid's snapshot (the same ordering rule as the bonus).
- The owner's note named the features `bonus-undo` and `break-end`; the plugin's contract names them `bonusUndo` and
  `breakEnd`, which the client uses.
