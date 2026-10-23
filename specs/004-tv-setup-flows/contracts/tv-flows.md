# Contract: TV flows and routes

Spec FR-001–FR-020; research R3, R6–R8; [data-model.md](../data-model.md). Replaces the routing in 003's
[grown-up-shell.md](../../003-peke-foundation/contracts/grown-up-shell.md): there is no grown-up account in the app
any more, so the shell's pages are reached from the parent menu instead.

## First screen, on every cold start, warm start, resume and screensaver exit

| `TvState` | First screen |
|---|---|
| `Loading` | Peke's starting-up page; never a list of users or servers |
| `NotSetUp` | Setup: server step |
| `InSetup(step)` | Setup at `step`; a fresh administrator sign-in when `step` is past `ADMIN`, since the session isn't stored |
| `SetUp`, no kids | "This TV has no kids" with Grown-ups |
| `SetUp`, one kid | That kid's home (or their stop screen) |
| `SetUp`, several kids | Kids picker |

## Setup steps

| Step | Screen | Leaves to |
|---|---|---|
| `SERVER` | Address entry and servers found on the network. Checks `/Kids/Info` (plugin 0.0.7.0+) | `ADMIN` |
| `ADMIN` | Administrator sign-in: username and password, or Quick Connect. Non-administrators refused | `CODE` if the household has no code, else `KIDS` |
| `CODE` | Set the household code twice | `KIDS` |
| `KIDS` | Household kids with picture and name, multi-select; none → how to mark kids, Check again | `SIGN_IN` |
| `SIGN_IN` | One row per picked kid: signing in, done, or a problem with what to do (R1 failure cases); Quick Connect off → turn it on / Check again / Type the password | `DONE` once at least one kid is signed in; the admin session closes |

Back on a step returns to the previous one. Back on `SERVER` quits.

## Kids picker (`SetUp`, several kids)

- Shows only this TV's kids, then Grown-ups. No add-user or add-server item.
- Picking the last kid → their home, or their stop screen when stopped.
- Picking another kid → their home, unless the last kid is `Stopped`; then the parent gate opens first (FR-012a).
- A kid who `NeedsGrownUp` → the parent gate, then the administrator sign-in to sign them in again.

## Inside a kid's session

| Action | Rule |
|---|---|
| The sidebar's avatar (switch kid) | Parent gate, then the picker (FR-012) |
| Grown-ups (sidebar, stop screens) | Parent gate, then the parent menu |
| Back on the home | First Back opens the sidebar, second quits (constitution IV, 2.1.0) |
| An intent or deep link | Runs as the current kid with their rules, or is dropped; never switches user |

## Parent menu (after the gate)

| Item | Needs the admin | Effect |
|---|---|---|
| Add time | no | As today |
| This kid's settings | no | As today (grant) |
| Kids on this TV → remove | no | Signs out and deletes that kid's row; their device id's session ends |
| Kids on this TV → add | yes | `AdminSession`, the `KIDS` and `SIGN_IN` steps for kids not on the TV, then closes |
| This TV (player and screensaver settings, from 003's Peke settings list) | no | As today, minus Quick Connect "authorize another device" and "Sign in automatically" |
| License | no | As today |
| Reset this TV | no | Confirm, sign out every kid (best effort), erase server, users, kids data and `TvSetup`, then the `SERVER` step |

Closing the menu returns to the kid, or the picker. Grown-up destinations (settings, debug, kids settings, license)
render only while a parent grant is live; otherwise they redirect to the gate.

## Parent gate

- Code check as today (plugin grant with a kid's token; offline verifier fallback; retry limits).
- No kid with a working token → the offline verifier only; no verifier → only Forgot the code?.
- Forgot the code? → administrator sign-in (`AdminSession`) → set a new code → the session closes.
