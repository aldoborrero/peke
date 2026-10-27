# Contract: grown-up screens

What a grown-up sees and presses. English, then Spanish. `[text]` is skipped by focus.

## Parent menu (with a kid)

```text
Grown-ups                                   Adultos
[text] Leo · 25 minutes left today          Leo · Le quedan 25 minutos hoy
Add time                                    Dar más tiempo
  or [text] Break: ends at 18:20            Descanso: acaba a las 18:20
     Keep watching now (breakEnd)          Seguir viendo ahora
  or [text] Bedtime: allowed hours are set in Jellyfin   Hora de dormir: el horario se pone en Jellyfin
  or [text] Leo has no daily limit          Leo no tiene límite diario
  or [text] Can't reach the server to add time   No se puede conectar con el servidor para dar más tiempo
Switch kid (2+ kids)                        Cambiar de niño
Leo's rules                                 Las normas de Leo
Settings                                    Ajustes
```

Without a kid the code opens Settings directly.

## Add time

```text
Add time for Leo                            Más tiempo para Leo
   ‹  15 min  ›                             (arrows dimmed at 5 min and 2 h)
Can watch until 19:15                       Puede ver hasta las 19:15
Had 10 min left. Can watch until 19:25      Le quedaban 10 min. Puede ver hasta las 19:25
(amber) Bedtime starts at 20:00, Leo stops then    La hora de dormir empieza a las 20:00 y Leo para entonces
A break comes at 18:40                      A las 18:40 toca descansar
OK adds · Back returns                      OK para dar · Atrás para volver
Sending…                                    Enviando…
Couldn't add the time. Retry               No se pudo dar el tiempo. Reintentar
15 more minutes for Leo · until 19:15       15 minutos más para Leo · hasta las 19:15
  Done · Undo (bonusUndo, 10 s)            Hecho · Deshacer
```

Steps: 5, 10, 15, 20, 30, 45 min, 1 h, 1 h 30, 2 h. OK sends once; a grant that ran out asks for the code and sends.

## Leo's rules

Title "Leo's rules" / "Las normas de Leo", with Leo's avatar. Daily time (focused, the only focus stop; list opens on
the current value, Unlimited last; "Saved" / "Guardado"). Text: Today, Time in a row and a break, Allowed hours,
Search and voice search, Autoplay, Live TV (names, or "Live TV is off in Jellyfin"), Picks, What Jellyfin decides.
Then "Change Leo's rules on the dashboard" / "Cambia las normas de Leo en el panel" with
`{server}/web/#/configurationpage?name=Kids` and "then pick Leo" / "y elige a Leo".

## Refused channel (kid)

"This channel isn't on Leo's list" / "Este canal no está en la lista de Leo", "A grown-up can add it on the
dashboard" / "Un adulto puede añadirlo en el panel". Focus on the message; Down to Grown-ups; Back leaves.

## Settings

```text
Household                 Casa
  Change the parent code  Cambiar el código de padres   "The same on every TV of this server"
This TV                   Esta tele
  Server · Kids on this TV · Voice search for kids · Timer bar · Screensaver · Show Clock · Language · Subtitles
More · About · Start over (Reset this TV)
```

## Parent gate

- Entry: "Enter the parent code" / "Escribe el código de padres", hint "Forgot it? Press OK with no code" /
  "¿Lo has olvidado? Pulsa OK sin escribir nada". Empty OK → "Forgot the code?"; Back there → the entry.
- Wrong: "Wrong code": Try again | Forgot the code?
- Forgot: administrator user and password, or "Use Quick Connect" with a code; then the new code twice; toast
  "Parent code changed" / "Código de padres cambiado".
- Explanations in the no-code, recovery-only, plugin-missing and admin-menu stages are text; their title is
  "Parent code" / "Código de padres".
