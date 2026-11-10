# Data model: The parent gate as on the canvas

No stored data changes. One state type changes.

## GateStage.Passcode

| Field | Type | Meaning |
|---|---|---|
| `type` | `ParentLockType?` | the household's code type; null while loading |
| `attempt` | `Int` | bumped on every refusal; a new value clears the entry |
| `refusal` | `GateRefusal?` | why the last code was refused, shown on the page until a key is typed |

Transitions on `submit(code)`:

- empty code → `AdminSignIn(recovery = true)`, returning to the same `Passcode` (refusal cleared) on Back
- valid → menu / `onPassed` / pending add time, as before
- `Wrong` → `Passcode(type, attempt + 1, Wrong)` (was `WrongCode`)
- `Throttled(s)` → `Passcode(type, attempt + 1, Wait(s))`
- `CannotCheck` → `Passcode(type, attempt + 1, CannotCheck)`

`GateStage.WrongCode` and `retryCode()` are removed; `forgotCode()` no longer has a `WrongCode` case.

## CodeChangeStep.Current

Unchanged fields (`type`, `attempt`, `wrong`); `wrong` now shows inline instead of a Toast.
