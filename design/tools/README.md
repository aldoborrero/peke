# Logo generator

The logo set, the banners and the app icon are generated, so the lockups stay in proportion and the text stays Nunito
Black outlines. The font is the app's own `app/src/main/res/font/nunito_black.ttf` (and `nunito_extrabold.ttf` for the
tagline). The rules the scripts follow are in [DESIGN.md](../DESIGN.md), Logo.

- `gen.py` draws the buddy mark, the wordmark (and its editable `peke-wordmark-text.svg`), the horizontal and stacked
  lockups and the "for Jellyfin" lockups into `design/assets/logo/`. The constants at the top are the design: the
  outline `O`, the x-height `X`, the tracking and kerning, the rim and the lockup spacing.
- `compose.py` places the lockups and the mark: `docs/assets/banner.svg`, `docs/assets/social-preview.png` (rendered
  with `rsvg-convert`), `design/assets/tv-banner.svg`, `design/assets/app-icon.svg`, and the two 108 dp adaptive icon
  layers in `build/peke-logo/`, which only exist to be converted.

Run them with `bin/peke-logo`, which direnv puts on `PATH`, from any directory in the repository:

```sh
peke-logo all       # gen, then compose: run this after changing either script, compose.py imports gen.py
peke-logo gen       # only design/assets/logo/
peke-logo compose   # only the banners, the social preview, the app icon and the adaptive layers
peke-logo --help
```

Python with fontTools and `rsvg-convert` come from the devshell; without direnv, `peke-logo` re-runs itself through
`nix develop`. The output is deterministic, so a run with nothing changed leaves `git status` clean.

## Drawables

The app's drawables are converted from these files with AGP's `Svg2Vector`, as in
[specs/007-peke-interface/assets-conversion.md](../../specs/007-peke-interface/assets-conversion.md):

| Source | Drawable (`app/src/main/res`) | After converting |
|---|---|---|
| `design/assets/logo/peke-horizontal.svg` | `drawable/logo_peke.xml` | — |
| `design/assets/tv-banner.svg` | `drawable/ic_banner.xml` | set `width`/`height` to `160dp`/`90dp` |
| `design/assets/app-icon.svg` | `mipmap-anydpi/ic_launcher.xml` | set `width`/`height` to `48dp` |
| `build/peke-logo/ic_launcher_foreground.svg` | `drawable/ic_launcher_foreground.xml` | — |
| `build/peke-logo/ic_launcher_monochrome.svg` | `drawable/ic_launcher_monochrome.xml` | — |

Then run `DrawablesTest`.
