# Peke for Jellyfin: UX and UI brief

This is the brief the design canvas started from (2026-10-22). The decisions taken since then, such as the PekeTV
palette, the mascot and the screen behaviour, are in [DESIGN.md](DESIGN.md).

## The product

Peke is an Android TV app made only for children, for households that run their own Jellyfin media server with the
Jellyfin Kids server plugin. A kid picks their profile and watches their shows, films and live channels on the
living-room TV, using the TV remote. Grown-ups set the rules on the server (who is a kid, a daily screen-time
allowance, a limit per sitting with breaks, allowed hours, which live channels each kid sees) and use Peke only to set
the TV up. They watch with another app.

Peke is a fork of Wholphin, an open-source Android TV client for Jellyfin. Today it still looks like Wholphin: a
grown-up media browser. The goal of this work is a kids-first experience and a recognisable identity.

## Who uses it

- Kids, roughly 3 to 10 years old. Some can't read yet. They use a D-pad remote (arrows, OK, Back, sometimes a
  microphone button).
- Grown-ups, a few times: first setup, adding time, changing a kid's settings. They also use the remote.

## Hard constraints

- 10-foot UI on a TV: 1920x1080 (designed at 960x540 dp), viewed from a sofa. Large type, high contrast, safe margins
  (about 5% on each side).
- D-pad only: every element is reachable with arrows, and the focused element is obvious from across the room. No
  hover, no touch, no tiny targets.
- Low-end hardware (Chromecast with Google TV, cheap boxes): simple layouts and animations.
- Two languages: English and Spanish. Spanish strings run about 20 to 30% longer.
- Branding: the name is "Peke for Jellyfin" (short: "Peke"). It must not use the Jellyfin logo or look like an
  official Jellyfin app, and must not use "Jelly" or "fin" compounds. It needs its own logo.
- No ads, no external links, no app store; installed from GitHub releases.
- Kids must never reach system settings, other apps or grown-up screens without the parent code.

## Identity so far

- Placeholder banner for the GitHub README: a rounded TV with a play button and two antennas, purple (#aa5cc3) to
  blue (#00a4dc) gradient on dark navy (#0b1d33), a yellow sparkle (#f4c430). This is a placeholder, not the final
  mark. Those two colours turned out to be Jellyfin's logo gradient, so they were dropped: see the palettes in
  DESIGN.md.
- There is a household live channel called PekeTV, so the brand should work for both the app and that channel.
- Needed: app icon (launcher), TV banner (320x180 dp, shown in the Android TV launcher), logo for in-app use, and a
  look for the kids' screens.

## Screens and flows

Kids' side:

1. Who's watching: pick a kid profile (avatar and name). Kids can't read yet, so avatars matter.
2. Kid home: rows such as Keep watching, Watch live (their channels), the picks a grown-up chose, shows, films.
   Optional search and voice search, only if a grown-up allowed it for that kid. Shows the time left today.
3. Show or film details: play, episodes and seasons.
4. Player: the remote's OK pauses; a small time bar shows the time left; warnings a few minutes before the time
   ends.
5. Live TV: a simple guide limited to that kid's channels.
6. Stop screens, all friendly and without blame:
   - time's up for today, come back tomorrow;
   - break time (after a sitting limit), with a countdown to when they can watch again;
   - outside allowed hours (for example bedtime);
   - something can't play.
7. Parent gate: a grown-up types the household code with the remote's arrows or digits. From there: add time,
   change this kid's settings, or leave to the grown-up setup.

Grown-ups' side (simple, plain, not the focus of the art):

8. Setup: add the Jellyfin server, sign in the kids, set the parent code.
9. Grown-up shell: kids settings, app settings (playback, Quick Connect), switch user, licenses, and a "plugin
   needed" note when the server lacks the Jellyfin Kids plugin.

## What to explore

- A visual language for kids on a TV: shapes, colors, type, focus states, motion that works on cheap hardware.
- How a pre-reader finds their profile and their favourite show.
- How time left is shown without causing anxiety, and how the stop screens feel fair.
- The logo, app icon and TV banner, working at launcher size and for the PekeTV channel.
- Light touches of delight (sounds, small animations) that don't distract or overexcite.
