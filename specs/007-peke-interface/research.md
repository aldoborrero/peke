# Research: Peke's interface

## R1. Storing the theme and migrating Wholphin's

- **Decision**: `AppThemeColors` keeps value 0 as `APP_THEME_COLORS_UNSPECIFIED` (proto3 needs a zero value; it is also
  what a TV on Wholphin's purple stored, since proto3 doesn't write a default), reserves 1–7 and the names `PURPLE`,
  `BLUE`, `GREEN`, `ORANGE`, `BOLD_BLUE`, `OLED_BLACK`, `RED`, `BROWN`, and adds `PEKE_TV = 8`, `MANDARINA = 9`,
  `CARAMELO = 10`, `PAPEL = 11`. A `DataMigration<AppPreferences>` on the DataStore rewrites 0–7 to `PEKE_TV` once.
  A value above 11 (a newer Peke) isn't rewritten; reading resolves anything unknown to PekeTV.
- **Rationale**: the spec asks for the old numbers reserved, never renumbered or reused. A DataStore migration runs
  before the first read, once (after it writes, `shouldMigrate` is false), and is unit-testable without Android.
- **Alternatives**: renaming 0–3 to the new themes (reuses numbers, rejected by the spec); mapping at read time only
  (works, but the stored value would stay Wholphin's forever and the Settings choice would open on nothing).

## R2. Colour roles

- **Decision**: one `PekePalette` data class with DESIGN's eleven roles and `isLight`, four instances, and a function
  from `AppThemeColors` to a palette (unknown → PekeTV). `PekeTheme` builds the tv-material `ColorScheme` and the
  Material 3 one from it (background, surface, surfaceVariant = surface, primary, onPrimary, primaryContainer =
  primary, secondary, onSecondary = onPrimary, tertiary, onTertiary = onPrimary, onBackground/onSurface = text,
  onSurfaceVariant = text secondary, border = focus border, borderVariant = text secondary, inverseSurface = text,
  inverseOnSurface = background, error from Material's defaults for dark or light) and provides `LocalPekeColors` for
  the roles a `ColorScheme` doesn't have: time, outline, focus border, text secondary.
- **Rationale**: DESIGN's Mapping to Compose; one value set per theme, so a test can read the same values the app
  draws with.
- **Alternatives**: keeping the 70-colour Material palettes per theme (most roles would be invented, not designed).

## R3. Nunito

- **Decision**: three static TTFs, Nunito 3.602 at 700 (Bold), 800 (ExtraBold) and 900 (Black), as `res/font/`
  resources loaded with Compose's `Font(resId, weight)`. Source: Google Fonts' static instances of the upstream
  variable font (`fonts.gstatic.com`, family Nunito v32), the same OFL 1.1 font as `google/fonts/ofl/nunito`. The OFL
  text and a library entry go into AboutLibraries' `app/config` so the license screen lists them.
- **Rationale**: upstream ships only a variable font. Variation axes in font resources need API 26; minSdk is 23, where
  a variable font renders at its default instance. Static files work everywhere, with no downloadable-fonts provider
  (no Play Services dependency, works offline).
- **Size**: 125,376–125,464 bytes per file, 376 KB raw; 171 KB compressed in the APK (measured, plan.md).
- **Alternatives**: the variable font (≈280 KB, wrong weights below API 26); downloadable fonts (needs GMS, not on
  every TV box, and a network call); subsetting to Latin (would save ~40%, but needs fonttools, not in the devshell;
  noted for later).

## R4. Type scale

- **Decision** (canvas px × 0.75 = sp; one value where DESIGN gives a range; all styles Nunito):

| tv-material style | DESIGN style | px | sp | weight |
|---|---|---|---|---|
| displayLarge | Display | 56 | 42 | 900 |
| displayMedium | (between) | 48 | 36 | 900 |
| displaySmall | (between) | 44 | 33 | 900 |
| headlineLarge | Title | 48 | 36 | 900 |
| headlineMedium | Headline | 36 | 27 | 900 |
| headlineSmall | (Headline, small) | 32 | 24 | 900 |
| titleLarge | Section | 24 | 18 | 900 |
| titleMedium | (Body, strong) | 22 | 16.5 | 800 |
| titleSmall | (Body, small) | 20 | 15 | 800 |
| bodyLarge | Body | 22 | 16.5 | 800 |
| bodyMedium | Supporting | 18 | 13.5 | 800 |
| bodySmall | (Supporting, small) | 16 | 12 | 800 |
| labelLarge | (buttons) | 20 | 15 | 800 |
| labelMedium | Small | 16 | 12 | 800 |
| labelSmall | Small, minimum | 15 | 11.25 | 800 |

  Line heights are 1.25× the size. Material 3's `Typography` gets the same styles, for the M3 components still used.
- **Rationale**: DESIGN's Mapping names seven styles; Wholphin's code uses the other eight too, so each gets the nearest
  DESIGN style and nothing goes under Small (15 px). Weight 700 is kept for emphasis inside Body text.

## R5. Shapes

- **Decision**: tv-material `Shapes(extraSmall = 12 dp (16 px), small = 12 dp (16 px), medium = 18 dp (24 px),
  large = 27 dp (36 px), extraLarge = 27 dp)`; pills and avatars use `CircleShape` at the call site.
- **Rationale**: DESIGN's mapping (small 16, medium 22–24 → 24 for home cards, large 36 for dialogs).

## R6. Focus treatment

- **Decision**: `ui/theme/Focus.kt` holds the values (border 4.5 dp = 6 px, ring 6 dp = 8 px, kid scale 1.07,
  grown-up scale 1.0) and `Modifier.pekeFocus(shape)`, a `Modifier.Node` that listens to focus, scales the item in its
  layer (animated, 150 ms), raises its z-index while focused, and draws the border (focus border colour) and then the
  ring (primary) outside the item's shape. Shared components pass tv-material `scale = 1` and `border = None` and
  apply the modifier: `ui/components/Button.kt`, `ListItemWrapper.kt` and the cards in `ui/cards/`. A
  `LocalPekeFocusScale` (true by default) is set to false around grown-up destinations in `DestinationContent`, so
  those keep border and ring without scale. Screens still drawing their own focus are moved to these defaults in the
  phase that restyles them (3–5).
- **Rationale**: DESIGN Focus: two strokes plus scale, never colour alone, transform and opacity only. tv-material's
  `Border` is one stroke and its scale is applied inside the component (see Complexity Tracking).
- **Alternatives**: `Glow` for the ring (blurred); a `Border` brush can't paint two colours across the stroke width.

## R7. The theme choice

- **Decision**: `AppPreference.ThemeColors` becomes a choice over `[PEKE_TV, MANDARINA, CARAMELO, PAPEL]` with names
  from `R.array.app_theme_colors` ("PekeTV", "Mandarina", "Caramelo", "Papel (daytime)" / "Papel (de día)"), default
  PEKE_TV, an index mapping that sends anything else to 0, and it joins Settings › This TV after the timer bar. The
  `ChoicePreference` dialog already opens on the current value (006 R12). `MainActivity` and the screensaver already
  recompose on preference changes, so the theme applies at once.
- **Rationale**: DESIGN Behaviour lists the This TV settings; the theme is a property of the TV.

## R8. SVG to VectorDrawable (Phase 2)

- **Decision**: AGP's `Svg2Vector` (`com.android.tools:sdk-common` 32.2.1, the code behind Android Studio's Vector
  Asset tool), run from the Gradle cache with the devshell's `java`; nothing installed (assets-conversion.md). Each file is checked by rendering it (a
  Robolectric test that inflates every drawable, and the emulator). Features VectorDrawable lacks: `<text>` (already
  paths), CSS `<style>`, `<use>`, masks, filters, `<pattern>`, and gradients before API 24 (minSdk 23: inline
  gradients need `aapt:attr` and render from API 24; on 23 they fall back to the first stop). The log lists any
  use per file and the workaround (flatten, expand `<use>`, replace gradients with solid fills).
- Confirmed in Phase 2 (T020): the SVGs use none of the unsupported features; only `currentColor` needed replacing.

## R9. Avatars from the plugin (Phase 3)

- **Decision**: proposed in [contracts/kids-api-avatar.md](contracts/kids-api-avatar.md): feature `avatar` in
  `/Kids/Info` features, `ApiVersion` 1 with minor 1 (Kids API 1.1), and `Avatar` (`leo`, `max`, `nico`, `lucy`,
  `sara`, `mia` or null) on the kid profile. Unknown values are ignored. Cached in the kids database with the profile.
- **Rationale**: Principle III; the same picture on every TV.

## R10. Timer bar (Phase 3)

- **Decision**: `KidsTimerBarPosition` (Top, Bottom, Hidden) stays. The bar is drawn in `PekeColors.time` with the
  outline time-left icon and "12 min" text in Small style; from the plugin's first `WarnAtSeconds` point (300 s by
  default) it grows from 6 dp to 12 dp with the label in Body style, at the moment `KidsTimerEffects` shows the
  existing warning, and stays until the end. No blinking.
