# Quickstart: Grown-ups screens

## Build and test

```sh
nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin
nix fmt && nix fmt   # second run changes nothing
nix develop -c pre-commit run --all-files
nix develop -c ./gradlew --stop
```

## On a TV (D-pad only)

Prerequisites: a set-up TV with two kids on a server with the Kids plugin 0.0.7.0 (0.0.9.0 for Undo and Keep
watching now); one kid with a 60-minute daily time.

1. **No marking (US1)**: Grown-ups → code → Leo's rules and Settings: no kid on/off switch anywhere.
2. **Add time (US2)**: let Leo's time run out. Down → Grown-ups → code → Add time. The stepper opens on 15 (or the
   last amount), Left stops at 5, Right stops at 2 h with the arrow dimmed; the line says until when. OK: busy, then
   "15 more minutes for Leo · until …" with Undo (0.0.9.0); after 10 s Leo's video resumes. Back on the stepper
   returns to the menu.
3. **Reasons (US2)**: during a break, at bedtime, for an unlimited kid and with the server stopped, the menu shows the
   reason line instead of Add time.
4. **Rules (US3)**: Grown-ups → code → Leo's rules: only Daily time takes focus; its list opens on 60 with Unlimited
   last; pick 90 → "Saved". The dashboard address is shown.
5. **Channels (US4)**: the rules list the channel names; open a refused channel: the message page with Grown-ups.
6. **Regroup (US5)**: from the picker, Grown-ups → code opens Settings; Household › Change the parent code; This TV ›
   Kids on this TV and the timer bar.
7. **Gate (US6)**: OK with no code opens Forgot the code?; Back returns; Use Quick Connect shows a code.

Screens that need the parent code typed were not checked on the emulator by the agent (it never types the code).
