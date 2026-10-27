# Icon consistency report

Spec 007, a pass over the twenty icons in both sets after the icon audit. Builds on Phase 5 (`cc01f249`).

## Changes

- `design/DESIGN.md`, Icons: the owner's rules, written out: two-tone only on a light surface, the 48 grid and the live
  area, stroke 3.5 (3 only for the small white triangles), round caps and joins on the root, explicit fills, corner
  radii 4, 7 and 9, which icons have the highlight, one composition for both sets, the circle only on the transport
  buttons, and the ink coverage measured.
- `design/assets/icons/*.svg` and `outline/*.svg` (40): redrawn. Each icon was drawn once as shared geometry and
  written out as both sets, so they match.
  - Mechanical: caps and joins round on the root `<svg>`, every shape with an explicit fill, every stroke 3.5 (it was
    2.5 to 8), radii from 4, 7 and 9 (they were 2 to 9).
  - Optical:
    - Padding: everything is inside 4–44, or 3.5–44.5 for the round ones. Settings was at 1.2, time left at 1.5 on
      top, bedtime at 2.2 on the left, switch kid at 2.2 on the sides.
    - The narrow icons are wider: break from 10 per side to 7.2, voice from 9 to 6.2. The flat ones are taller: audio
      from 8.2 top and bottom to 5.7, subtitles from 8.2 to 6.2.
    - Back is a fat arrow and watched a fat check, without the circle. Play, pause and next episode keep their circle.
    - Switch kid is two kids side by side in both sets; two-tone had them overlapping.
    - The search handle is a capsule with a 3.5 outline; it was an 8 stroke under a 3.5 stroke in two-tone and a 5
      stroke in outline.
    - Pause and next episode draw their bars as 3.5 lines. Outlined 8-wide pills read as "0" in the player (seen on
      the emulator), and thinner pills would need a radius off the scale.
    - Settings has a smaller gear: the same petal circles behind the body in two-tone, one path in outline.
    - The highlight is on favourites, search, time left and voice. Home and picks lost theirs; they aren't round.
- `app/src/main/res/drawable/ic_peke_*.xml`, `ic_peke_outline_*.xml` (40): converted again, as in Phase 2.
- `app/src/test/.../ui/theme/IconSpecTest.kt` (new, plain JUnit). It parses `design/assets/icons` and checks:
  - the 48 viewBox;
  - stroke widths from the allowed set;
  - round caps and joins on the root, with no shape overriding them;
  - explicit fills: palette colours in two-tone, `none` or `currentColor` in outline;
  - radii from the scale, at most half the short side;
  - the live area, from the exact stroked outlines (`java.awt.geom`, round caps and joins);
  - the same bounds in both sets of each icon;
  - the highlight only on the listed icons;
  - and that no main source outside `PekeArt.kt` uses `.twoTone` or a two-tone drawable.

  Run against the old icons, it fails 6 of its 8 tests.
- `ui/theme/PekeArt.kt`: `PekeIcon.twoTone`'s KDoc carries the light-surface rule.
- `specs/007-peke-interface/assets-conversion.md`: the root caps and joins row, and an "Icon consistency pass" section.

The app draws only the outline set, so no Kotlin changed apart from the KDoc. The two-tone rule holds today: nothing
uses the two-tone set, and the test fails if something starts to.

## Audit, before and after

I ran the owner's audit script unchanged on both sets. I also ran a checker, `check.py`, that tests each spec rule and
draws the sheets on night and on cream. It follows each shape's inherited attributes; the audit's own count ignores
group scope.

| | Before | After |
|---|---|---|
| Icons breaking a rule (of 40) | 39 | 0 |
| Outside the live area | 30 | 0 |
| Caps or joins not round | 36 | 0 |
| Shapes with no fill | 5 icons | 0 |
| Stroke widths in use | 2.5, 3, 3.5, 4, 5, 8 | 3.5, and 3 on three small triangles |
| Rect radii in use | 2, 2.5, 4, 6, 7, 9 | 4, 7, 9 |
| Highlights | favourites, home, picks, search, voice | favourites, search, time left, voice |
| Outline ink, range | 16.1–33.8% | 16.5–28.3% |
| Outline ink within 22–28% (±0.5) | 11 of 20 | 12 of 20 |

Outline set, padding (left, top, right, bottom, in grid units) and ink:

| Icon | Before | Ink | After | Ink |
|---|---|---|---|---|
| audio | 5.2 8.2 5.7 8.2 | 19.2 | 4.7 5.7 6.0 5.7 | 21.4 |
| back | 3.2 3.2 3.2 3.2 | 25.6 | 4.0 6.0 4.2 6.0 | 17.4 |
| bedtime | 2.2 5.2 5.0 5.2 | 19.6 | 4.7 6.5 4.5 4.7 | 18.8 |
| break | 10.2 3.2 9.2 3.2 | 22.0 | 7.2 4.0 7.2 4.2 | 24.4 |
| episodes | 4.2 4.2 4.2 6.2 | 26.6 | 4.2 4.7 4.2 4.2 | 26.8 |
| favourites | 2.9 3.9 2.9 5.2 | 19.1 | 4.2 6.7 4.2 5.7 | 17.1 |
| grown-ups | 7.2 6.0 7.2 4.2 | 21.8 | 6.2 4.2 6.2 4.2 | 22.7 |
| home | 5.2 6.2 5.2 5.2 | 22.5 | 4.7 5.2 4.7 4.2 | 23.3 |
| live | 3.2 3.2 3.2 6.2 | 26.5 | 4.2 4.2 4.2 5.2 | 25.8 |
| next-episode | 3.2 3.2 3.2 3.2 | 30.2 | 4.0 4.0 4.0 4.0 | 25.7 |
| pause | 3.2 3.2 3.2 3.2 | 30.1 | 4.0 4.0 4.0 4.0 | 23.1 |
| picks | 4.2 3.2 4.2 6.7 | 19.2 | 4.1 4.2 4.1 5.8 | 19.2 |
| play | 3.2 3.2 3.2 3.2 | 24.9 | 4.0 4.0 4.0 4.0 | 24.2 |
| search | 5.2 5.2 5.5 5.5 | 16.1 | 4.2 4.2 4.4 4.4 | 18.8 |
| settings | 1.2 1.2 1.2 1.2 | 33.8 | 3.5 3.5 3.5 3.5 | 28.3 |
| subtitles | 3.2 8.2 3.2 8.2 | 25.3 | 4.2 6.2 4.2 6.2 | 25.4 |
| switch-kid | 2.2 9.7 2.2 6.2 | 27.8 | 4.2 8.2 4.2 5.2 | 28.3 |
| time-left | 5.2 1.5 5.2 2.2 | 22.4 | 6.2 4.0 6.2 3.5 | 21.6 |
| voice | 9.2 3.2 9.2 4.2 | 20.2 | 6.2 4.2 6.2 4.2 | 21.2 |
| watched | 3.2 3.2 3.2 3.2 | 25.3 | 4.5 6.0 4.6 4.7 | 16.5 |

(The audit measures padding from the rendered pixels. Its 3.2 is the 3.25 of a 3.5 stroke on r 19, and the live-area
check allows 0.1 for antialiasing.)

### Not met: the ink target for single shapes

The ink target is the one rule that isn't met everywhere. A single closed shape that already fills the 4–44 square
covers at most about 19% with a 3.5 stroke, since coverage is roughly the perimeter times 3.5:
- back (17.4), watched (16.5), favourites (17.1) and picks (19.2);
- bedtime and search (18.8), open shapes with one small part each.

Back and watched dropped from about 25% because their circle went, as decided. Getting them to 22% would take marks
that change their meaning, or a thicker stroke. DESIGN.md records the band as measured (21–28% for icons with inner
parts, 16–20% for the single shapes). This is a call for the owner: accept the lighter single shapes, or have them
redrawn with inner detail. The owner chose more body for back and watched: see "Back and watched with more body" below.

## Contact sheets

In `…/scratchpad/icons/`, where `…` is
`/tmp/claude-1000/-home-aldo-Dev-aldoborrero-Wholphin--worktree-peke-ui/a7e0cd28-f10a-4b0c-96dd-b2992a2b1cdb/scratchpad`:
- Before: `before/sheet-night.png` (two-tone on cream chips, outline in white), `before/sheet-cream.png`,
  `before/sheet-night-bare.png` (two-tone straight on night, where the outlines vanish), `before/icon-sheet.png` (the
  audit's own sheet).
- After: `after/sheet-night.png`, `after/sheet-cream.png`, `after/sheet-night-bare.png`, `after-audit/icon-sheet.png`
  and `after-audit/icon-audit.md` (the audit's own output).
- Spec check tables: `before/check.md`, `after/check.md`.
- Scripts: `gen.py` (the shared geometry that wrote both sets), `check.py`, and `icon_audit.py` (a copy of the audit).

## Screenshots

In `…/scratchpad/shots/`, from the final build on the emulator:
- `icons-rail.png`: the kids home with the rail closed (search, home, favourites, live TV, grown-ups).
- `icons-sidebar.png`: the sidebar open, with Search focused.
- `icons-search-empty.png`: a search with no results, with the rail beside it.
- `icons-player.png`: the player's controls, paused. Play is focused, next episode reads ▷|, and subtitles and audio
  are on the right.

To get the player screenshot I started a Continue watching episode, paused it about 8 seconds in, and stopped it.
The pause icon only shows while playing, and I didn't catch the controls in that state; the contact sheets show it.
The search page's microphone, search and filter icons are Material icons, not Peke's, so this pass didn't touch them.

## Validation

- `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`: BUILD
  SUCCESSFUL, 829 tests (821 plus the 8 new ones), 0 failures.
- `nix fmt`: 1 file changed on the first run (`IconSpecTest.kt`), 0 on the second.
- `nix develop -c pre-commit run --all-files`: all hooks passed.
- `Svg2Vector` printed no warnings for the 40 files.

## APK size

Clean debug builds, Phase 5 → icons:
- per ABI: arm64-v8a +206, armeabi-v7a +207, x86_64 +203 bytes;
- `res/drawable/` alone: +214 bytes. The rest is the version string, which is in the manifest and the dex files.

## Commit message

```
Redraw the icons to one set of rules

Both icon sets now share one grid: nothing outside the 4–44 live
area, 3.5 strokes with round caps and joins, explicit fills, corner
radii of 4, 7 or 9, and the same picture in two-tone and outline.
Back and watched lose their circle, which only the player's buttons
keep; switch kid shows two kids side by side in both sets; the bars
of pause and next episode are lines, so they don't read as zeros.

DESIGN.md spells out the rules, including that two-tone icons are
drawn only on a light surface, and IconSpecTest checks them on the
SVGs, along with keeping the two-tone set off the dark screens.
```

## Back and watched with more body

The owner's decision: back and watched get more body so their outline ink lands inside 22–28% like the rest, with the
matching change in two-tone. They stay without the circle, inside the live area, with 3.5 strokes, round caps and joins
and radii from the scale. Builds on `e21102b5`.

### What was tried

Each candidate was drawn in both sets and measured with `check.py`. Each was rendered at 24, 32 and 48 dp (48, 64 and
96 px on a 1080p TV), outline on night and two-tone on a cream chip (`…/scratchpad/icons/cand-preview.png`).

| Candidate | Outline ink | Verdict |
|---|---|---|
| back: fuller fat arrow (head the full height, shaft 20 thick) | 17.9% | reads best as a plain arrow, but can't reach the band |
| back: the same arrow with a line in the shaft | 19.9% | still short, and the line means nothing |
| back: arrow on a rounded-square badge | 26.2% | in the band, but the arrow is tiny at 24 dp and reads as a keyboard key |
| back: fat U-turn arrow | 24.8% | **chosen** |
| watched: broader fat check | 17.3% | short, and it left the live area |
| watched: check on a rounded-square badge | 24.6% | **chosen** |
| watched: fat outlined check on the badge | 30.2% | over the band, and the double contour clogs at 24 dp |

A single closed shape gains ink only through its perimeter, about 3.5 × the perimeter, so no fuller arrow or check
reaches 22% inside the 4–44 square. Both chosen shapes add an inner contour:
- **Back is a fat U-turn arrow**, the curved arrow on a remote's Back key. It is still a free shape, and the head stays
  big and left-pointing, so at 24 dp it reads as "go back" and not as a plain left arrow (next to rewind and the
  sidebar's chevrons). The inner curve gives it the second contour.
- **Watched is a check on a rounded-square badge** (36 square, radius 9), a ticked box, which kids know as "done". Its
  check is a 3.5 line, like the subtitles lines and the clock hand, and is still clear at 24 dp. The badge isn't the
  transport circle, so the rule "the circle only on transport buttons" holds.

### Before and after

| Icon | Ink before | Ink after | Padding after (L T R B) |
|---|---|---|---|
| back, outline | 17.4% | 24.8% | 4.2 6.2 4.0 6.5 |
| back, two-tone | 33.5% | 40.1% | 4.2 6.2 4.0 6.5 |
| watched, outline | 16.5% | 24.6% | 4.2 4.2 4.2 4.2 |
| watched, two-tone | 26.1% | 63.7% | 4.2 4.2 4.2 4.2 |

- The checker still finds 0 of 40 icons breaking a rule.
- Outline ink is 17.1–28.3% across the set, with 14 of 20 inside 22–28% (12 before).
- Still under the band: favourites (17.1%) and picks (19.2%), which are single shapes; bedtime and search (18.8%),
  which are open shapes; audio, voice and time left (21.2–21.6%).
- The audit script agrees: back L4.2 T6.2 R4.0 B6.5 at 24.8%, watched 4.2 all round at 24.6%.
- DESIGN.md: the shapes of back and watched, the badge in the 9 radius, and the measured ink bands.

### Files

- `design/assets/icons/back.svg`, `watched.svg`, and both in `outline/`.
- `app/src/main/res/drawable/ic_peke_back.xml`, `ic_peke_outline_back.xml`, `ic_peke_watched.xml` and
  `ic_peke_outline_watched.xml`, converted with the same `Svg2Vector` driver, with no warnings. The generator
  reproduced the other 18 icons byte for byte.
- `design/DESIGN.md`, Icons.
- No code or test changed: `IconSpecTest` already covers the new shapes.

Sheets, in `…/scratchpad/icons/`:
- `after/sheet-night.png`, `after/sheet-cream.png` and `after/sheet-night-bare.png`;
- `after/back-watched-sizes.png`, the two icons at 24, 32 and 48 dp;
- `after-audit/icon-sheet.png` and `after-audit/icon-audit.md`.

The sheets from before this change are in `body-before/after/`.

### Validation

- `IconSpecTest`: 8 tests, 0 failures.
- `nix develop -c ./gradlew --console=plain assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`: BUILD
  SUCCESSFUL, 829 tests, 0 failures.
- `nix fmt`: 0 changed on both runs.
- `nix develop -c pre-commit run --all-files`: all hooks passed.

### Commit message

```
Give the back and watched icons more body

Back becomes a fat U-turn arrow, like the Back key on a remote, and
watched a check on a rounded-square badge. Both sets change together,
and the outline icons now cover about a quarter of the grid, like the
rest of the set, instead of looking thin next to them.
```
