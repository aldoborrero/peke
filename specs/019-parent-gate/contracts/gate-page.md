# Contract: the parent code page

`ParentCodePage(title, type, attempt, refusal, backLabel, forgotHint, onConfirm, onDismissRequest)`

- Fills the screen with the theme background in its own window; Back calls `onDismissRequest` with any input.
- Focus starts on the entry, the only focusable node. Keys map through `ParentLockKeys` for `type`; arrows are input
  with `REMOTE` and focus never moves.
- OK (key up) calls `onConfirm(input)`, including an empty input.
- A new `attempt` clears the input. `refusal` shows until the first key typed after it:
  - `Wrong` → dots in primary, "Wrong code" in primary
  - `Wait(s)` → pill "Too many tries. Wait s seconds"
  - `CannotCheck` → pill "Can't reach the server to check the parent code. Try again in a moment."
- `backLabel`: "Back to {Kid}" with a kid, else "Back"; a label with the Back icon, not focusable. *(Removed by spec
  023: no pill, no parameter.)*
- `forgotHint`: shows "Forgot it? Press OK with no code" (the gate) or not (the current-code step).
- Text: title `displaySmall`, hint 24/800 in text secondary; strings in English and Spanish.
