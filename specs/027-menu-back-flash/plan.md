# Implementation Plan: No flash of the page below when Back leaves the grown-up menu

**Spec**: [spec.md](spec.md)

## Approach

- `GrownUpMenuReturn.close` returns whether it removed the page.
- `BackToMenu`: `onMenuClosed` returns that; when the page was removed, the menu stays composed ("covering") instead of
  closing, until the page leaves composition with the `NavDisplay` exit (which disposes `BackToMenu` and so the menu's
  window). If the page becomes the menu's page again (the rules chosen over the rules: same back stack entry), the
  cover ends at once.
- `GrownUpWindow` reads a `LocalGrownUpWindowInert` local that `BackToMenu` provides while covering, and consumes
  every key then.
- Simplest of the options in the GOAL: no change to the kid pages' transitions or the back stack order, and the
  page under the menu is never drawn alone however slow the next page is to compose.
