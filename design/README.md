# Design

Peke's look and feel, before it's built into the app.

- [DESIGN.md](DESIGN.md): the design system: themes, type, shapes, focus, the mascot, avatars, icons, the screens and
  how they behave.
- [brief.md](brief.md): the brief the design started from.
- `tools/`: the generator for the logo set, the banners and the app icon, run from the devshell.
- `assets/`: SVG sources to turn into Android vector drawables. Text is converted to paths in all of them but
  `logo/peke-wordmark-text.svg`.
  - `logo/`: the logo set: the buddy mark, the wordmark, the horizontal and stacked lockups, each in colour and in
    one colour (`-mono`, `currentColor`), and the "for Jellyfin" lockups for dark and light backgrounds. DESIGN.md's
    Logo section has the clear space, the minimum sizes and which file goes where;
  - `app-icon.svg` (the mark on coral) and `tv-banner.svg` (the 320×180 Android TV launcher banner);
  - `mascot/`: the PekeTV buddy and its moods for the stop screens;
  - `avatars/`: six kid avatars, each on its own background colour;
  - `icons/`: twenty two-tone interface icons on a 48 grid (PekeTV only), and `icons/outline/` with the same icons as
    single-colour outlines to tint.

The screens themselves are on the design canvas: <https://claude.ai/artifact/Qz7qEF5wga9NjR5ihsc1aq> (page "PekeTV
palette"; "First pass" keeps the earlier exploration and the palette comparison).

These assets are original work for Peke, under the repository's GPLv2 like the rest of the code, and so are the
README banner (`docs/assets/banner.svg`) and the GitHub social preview (`docs/assets/social-preview.png`): the
horizontal "Peke for Jellyfin" lockup.
