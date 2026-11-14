# Implementation Plan: Kid picker check mark on top, Down to Continue

**Spec**: [spec.md](spec.md)

## Approach

- `kids/setup/KidAvatar.kt`: `KidAvatarCard` wraps the round `Surface` in a `Box` of the avatar's size; the badge
  (`KidBadge`) is the surface's sibling, in a layer of the same size above it (`zIndex`), offset to the edge at 45°.
  It reads focus from the surface's `interactionSource` and scales with `graphicsLayer` by `PekeFocus.scale`, with
  `PekeFocus.ANIMATION_MILLIS`, as `pekeFocus` scales the avatar.
- `kids/setup/SetupFlow.kt`: the kids row and its buttons move from `KidsStep` into `KidsChoice`; each card gets
  `focusProperties { down = … }` to Continue's or Check again's `FocusRequester`.

No change to the rest of setup, the kids home or grown-up pages.
