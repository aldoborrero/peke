# Data model: Peke's interface

## AppThemeColors (stored, `PekeDataStore.proto`)

| Number | Name | Meaning |
|---|---|---|
| 0 | `APP_THEME_COLORS_UNSPECIFIED` | proto3 default; was `PURPLE`. Migrates to `PEKE_TV` |
| 1–7 | reserved (`BLUE`, `GREEN`, `ORANGE`, `BOLD_BLUE`, `OLED_BLACK`, `RED`, `BROWN`) | Wholphin; migrate to `PEKE_TV`; never reused |
| 8 | `PEKE_TV` | default |
| 9 | `MANDARINA` | |
| 10 | `CARAMELO` | |
| 11 | `PAPEL` | light, daytime |

Rules:
- **Migration** (once, before the first read): stored number 0–7 → 8. Numbers 8–11 and anything above 11 stay.
- **Resolution** (every read): 8–11 → that palette; anything else → PekeTV.
- **Fresh install**: the serializer's default is `PEKE_TV`.

## PekePalette (code, `ui/theme/PekePalette.kt`)

Fields, in DESIGN's role order: `background`, `surface`, `primary`, `onPrimary`, `secondary`, `time`, `tertiary`,
`text`, `textSecondary`, `outline`, `focusBorder`, and `isLight` (Papel only). Values: [contracts/design-tokens.md](contracts/design-tokens.md).

## Theme choice (Settings)

| Index | Value | English | Spanish |
|---|---|---|---|
| 0 | `PEKE_TV` | PekeTV | PekeTV |
| 1 | `MANDARINA` | Mandarina | Mandarina |
| 2 | `CARAMELO` | Caramelo | Caramelo |
| 3 | `PAPEL` | Papel (daytime) | Papel (de día) |

Value → index: the position above, or 0 for anything else. Index → value: the list above, or `PEKE_TV`.

## Focus treatment

| Field | Kid screens | Grown-up screens |
|---|---|---|
| border | 4.5 dp, `focusBorder` | same |
| ring | 6 dp, `primary`, outside the border | same |
| scale | 1.07 | 1.0 |
| motion | scale 150 ms; border and ring appear with focus | — |

## Avatar (Phase 3)

`KidAvatarName`: `leo`, `max`, `nico`, `lucy`, `sara`, `mia`, each with its drawable and background colour (DESIGN
Avatars). Read from the plugin profile's `Avatar` when the plugin has `avatar`; stored with the kid in the kids
database (new nullable column, Room migration). Anything else is null.

Display order: avatar → Jellyfin profile image → initial on a circle.
