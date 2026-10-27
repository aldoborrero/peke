# Contract: design tokens

What the app's theme code exposes to screens, and the values tests hold it to. Source: `design/DESIGN.md`.

## Colour roles

| Role | Read as | PekeTV | Mandarina | Caramelo | Papel |
|---|---|---|---|---|---|
| Background | `colorScheme.background` | `#1F2747` | `#12202B` | `#1E1B18` | `#FFF6E6` |
| Surface | `colorScheme.surface`, `surfaceVariant` | `#28335B` | `#1D3340` | `#2E2925` | `#FFFFFF` |
| Primary | `colorScheme.primary` | `#FF927E` | `#FF8A3D` | `#FF6B6B` | `#C63F1A` |
| On primary | `colorScheme.onPrimary` | `#1F2747` | `#12202B` | `#1E1B18` | `#FFFFFF` |
| Secondary | `colorScheme.secondary` | `#65D9CE` | `#80E2D8` | `#5BD6A2` | `#1B5E31` |
| Time | `LocalPekeColors.time` | `#FFDA70` | `#FFD23F` | `#FFC93C` | `#9A6200` |
| Tertiary | `colorScheme.tertiary` | `#B5A0EF` | `#FF6F91` | `#8EC5FF` | `#2B7BC4` |
| Text | `colorScheme.onBackground`, `onSurface` | `#FFFFFF` | `#FFFFFF` | `#FFF7EC` | `#1E2A33` |
| Text, secondary | `colorScheme.onSurfaceVariant`, `LocalPekeColors.textSecondary` | `#C8CDE6` | `#B9C7D0` | `#CFC3B5` | `#4F5B64` |
| Outline | `LocalPekeColors.outline` | `#1F2747` | `#12202B` | `#1E1B18` | `#1E2A33` |
| Focus border | `colorScheme.border`, `LocalPekeColors.focusBorder` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` | `#1E2A33` |

## Contrast (tested)

WCAG 2 contrast ratio, each value DESIGN's, checked to ±0.01 and against the minimum:

| Pair | Minimum | PekeTV | Mandarina | Caramelo | Papel |
|---|---|---|---|---|---|
| Text on background / surface | 4.5 | 14.59 / 12.25 | 16.57 / 13.13 | 16.13 / 13.54 | 13.65 / 14.64 |
| Text secondary | 4.5 | 9.26 / 7.77 | 9.58 / 7.59 | 9.90 / 8.31 | 6.50 / 6.97 |
| Time | 4.5 | 10.78 / 9.05 | 11.48 / 9.09 | 11.16 / 9.36 | 4.75 / 5.10 |
| On primary on primary | 4.5 | 6.71 | 7.07 | 6.18 | 5.09 |
| Primary | 3 | 6.71 / 5.64 | 7.07 / 5.60 | 6.18 / 5.18 | 4.74 / 5.09 |
| Secondary | 3 | 8.59 / 7.21 | 10.88 / 8.62 | 9.46 / 7.93 | 7.27 / 7.80 |
| Tertiary | 3 | 6.42 / 5.39 | 6.26 / 4.96 | 9.45 / 7.93 | 4.14 / 4.44 |
| Focus border | 3 | 14.59 / 12.25 | 16.57 / 13.13 | 17.14 / 14.38 | 13.65 / 14.64 |
| Primary vs secondary | 1.28 PekeTV, 1.5 others | 1.28 | 1.54 | 1.53 | 1.53 |

## Type

`MaterialTheme.typography`, Nunito only, weights 700/800/900, sizes as in [research.md](../research.md) R4. No style
under 11.25 sp (15 px).

## Shape

`MaterialTheme.shapes`: small 12 dp, medium 18 dp, large 27 dp; pills and avatars `CircleShape`.

## Focus

`Modifier.pekeFocus(shape)` with `PekeFocus` values (data-model.md); shared components pass tv-material
`scale = 1f` and no border. `LocalPekeFocusScale` false on grown-up destinations.
