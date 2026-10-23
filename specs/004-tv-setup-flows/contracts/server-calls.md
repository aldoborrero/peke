# Contract: server calls per flow

Research R1, R2, R5, R7. "Kid device" is `pekeDeviceId("$baseId:$userId")`; "admin device" is random per admin
sign-in. Nothing in this table is stored, except the kid tokens.

| Flow | Call | Auth | Device id |
|---|---|---|---|
| Server step | `GET /System/Info/Public`, `GET /Kids/Info` | none | install id |
| Admin sign-in | `POST /Users/AuthenticateByName` or the Quick Connect trio, then the `IsAdministrator` check | none → admin token | admin device |
| Code step | `GET /Kids/Parent/Lock`, `PUT /Kids/Parent/Lock` | admin token | admin device |
| Kids step | `GET /Kids/Profiles`, `GET /Users` (names, image tags); images from `/Users/{id}/Images/Primary` | admin token (images: none) | admin device |
| Kid sign-in, Quick Connect on | `GET /QuickConnect/Enabled`; `POST /QuickConnect/Initiate`; `POST /QuickConnect/Authorize?code=&userId=`; `POST /Users/AuthenticateWithQuickConnect`; `GET /Users/Me` | none; **admin token** for Authorize; then the kid token | **kid device** for Initiate, AuthenticateWithQuickConnect and Users/Me |
| Kid sign-in, password fallback | `POST /Users/AuthenticateByName` (password typed once, never stored); `GET /Users/Me` | none → kid token | kid device |
| End of admin action | `POST /Sessions/Logout` | admin token | admin device |
| Daily kid use | everything as today | kid token | kid device |
| Remove a kid, reset | `POST /Sessions/Logout` per kid (best effort when offline) | kid token | kid device |
| Parent gate | `POST /Kids/Parent/Verify` (grant) | a kid token | that kid's device |
| Code recovery | `PUT /Kids/Parent/Lock` without the current code | admin token | admin device |

## Errors shown in setup

| Response | Message (EN; ES ships alongside) |
|---|---|
| `/Kids/Info` missing or older than 0.0.7.0 | "This server needs the Jellyfin Kids plugin 0.0.7.0 or later." |
| Sign-in by a non-administrator | "This account can't set up the TV. Sign in with a server administrator." |
| Authorize 403, device access | "{Kid} can only use some devices. In the dashboard, allow this TV for {Kid}, then try again." |
| Authorize 403, sessions | "{Kid} is signed in on too many devices. Sign them out somewhere, then try again." |
| Authorize 404 | "The code expired. Trying again…" (a new request starts automatically) |
| `Users/Me` refused after sign-in | "{Kid}'s account is turned off or outside their allowed hours. Check it in the dashboard." |
| Quick Connect off | "Quick Connect is off on this server. Turn it on in Dashboard → General, or type {Kid}'s password once." |
