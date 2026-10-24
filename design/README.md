# Design

Peke's look and feel, before it's built into the app.

- [DESIGN.md](DESIGN.md): the design system: themes, type, shapes, focus, the mascot, avatars, icons, the screens and
  how they behave.
- [brief.md](brief.md): the brief the design started from.
- `assets/`: SVG sources to turn into Android vector drawables. Text is converted to paths in all of them but
  `logo-peke-text.svg`.
  - `app-icon.svg`, `tv-banner.svg` (the 320×180 Android TV launcher banner), `logo-peke.svg` and its editable
    `logo-peke-text.svg`;
  - `mascot/`: the PekeTV buddy and its moods for the stop screens;
  - `avatars/`: six kid avatars, each on its own background colour;
  - `icons/`: twenty two-tone interface icons on a 48 grid (PekeTV only), and `icons/outline/` with the same icons as
    single-colour outlines to tint.

The screens themselves are on the design canvas: <https://claude.ai/artifact/Qz7qEF5wga9NjR5ihsc1aq> (page "PekeTV
palette"; "First pass" keeps the earlier exploration and the palette comparison).

These assets are original work for Peke, under the repository's GPLv2 like the rest of the code, and so is the
README banner (`docs/assets/banner.svg`): the PekeTV buddy and the "peke" wordmark saying "Peke for Jellyfin".
