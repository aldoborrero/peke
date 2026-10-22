# Contract usage: which plugin endpoints Wholphin calls, and how it reacts

The plugin's contracts are the source of truth:
- `jellyfin-kids/specs/001-kids-plugin-foundation/contracts/kids-api-v1.md`;
- `…/002-kids-parent-lock/contracts/kids-api-v1-parent-lock.md`;
- `…/003-kids-allowance-tracking/contracts/kids-api-v1-allowance.md`.

This file only records the client's side.

- Requests use PascalCase and default JSON. Unknown response fields are ignored. A request body is the cached
  server object with the app's fields replaced.
- **Network error**: a timeout or no route, anything that is not an HTTP answer. It never changes cached state,
  except where noted below.
- **"Keep cache"**: the cached row stays as it is.

| Call | Token | Wholphin reaction |
|---|---|---|
| `GET /Kids/Info` | none | 200: store `ApiVersion` and `Features`. 404: store "no plugin". Other answers or errors: keep the last answer |
| `GET /Kids/Profiles` | any user of the server | 200: for each device user: in the list with a different revision → `GET Profile`; not in the list → delete the cached row. Other: keep cache |
| `GET /Kids/Users/{id}/Profile` | that user (self) | 200: upsert the row. 404: delete the row. Other: keep cache |
| `PUT /Kids/Users/{id}/Profile` | current user + `X-Kids-Grant`, or an admin | 200: replace the row. 409: replace the row with the body and show "changed elsewhere". 403: drop the grant and ask for the code. 400 and other answers: "couldn't save" |
| `DELETE /Kids/Users/{id}/Profile` | as PUT | 204 or 404: delete the row. 403: as PUT |
| `GET /Kids/Parent/Lock` | current or fallback user | 200: lock type (absent means no lock). Other: cached type |
| `POST /Kids/Parent/Verify` | current or fallback user | `Valid:true`: keep the grant, then fetch the verifier. `Valid:false` with `RetryAfterSeconds`: wait. `Valid:false`: wrong code. Network error: check the cached verifier |
| `GET /Kids/Parent/Verifier` | with the grant | 200: cache the type and verifier. Other: keep cache |
| `PUT /Kids/Parent/Lock` | an admin (`{LockType,Code}`), or a grant (`+Current`) | 200: drop the grant, then verify the new code. 400 `Current`: wrong current code. 429: wait. 403: ask for the code |
| `GET /Kids/Users/{id}/Status` | that user (self) | 200: cache the status. Other: keep cache |
| `POST /Kids/Users/{id}/WatchTime/Bonus` | grant or admin | 200: cache the new status. 403: ask for the code |

**Assumed addition** (not yet in the plugin, clarification Q3):
- `KidProfile.SessionLimitMinutes`: an integer 5–120, or omitted for none.
- `Features` contains `"sessionLimit"` when the plugin supports it.

**Never called**:
- `DELETE /Kids/Parent/Lock` (dashboard only);
- `WatchTime/Report` (no offline playback);
- `Admin/PickCandidates` (the app picks with its own search, as in v1).
