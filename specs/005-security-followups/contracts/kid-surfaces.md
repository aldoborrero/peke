# Contract: what a kid, a grown-up and other devices get

Research R1, R4–R11. "Kid screen" is anything rendered while a kid is current, stop screens included.

## Opening videos (FR-001 to FR-004)

| Where | OK | Play key |
|---|---|---|
| Home, "Continue watching" (and the combined Next up row) | direct play (`KidsDirectPlay.resume`) | same as OK |
| Home, any other row | details page | details page |
| Library, favourites, collection, search and recommended grids | details page | details page |
| Series page, an episode | as today | as today (already on a details page) |

Direct play: the loading page is replaced by the player only while it is the top entry. Otherwise the result is
dropped, and the "nothing to play" message shows only while the loading page is on top.

## Player (FR-012)

| Setting "Playback debug info" | Grown-up (never plays in Peke) | Kid |
|---|---|---|
| off | — | normal controls |
| on | — | normal controls, no debug overlay, no debug key |

## Focus on stop screens (FR-013)

| Screen | Initial focus | Grown-ups reached by |
|---|---|---|
| Time's up, break, outside allowed hours | the title (focusable, does nothing on OK) | Down |
| Kids picker | the first kid (unchanged) | moving past the last kid |
| This TV has no kids | nothing (unchanged) | any move |

## Microphone (FR-005 to FR-011)

| Voice search (This TV) | Android permission | Kid's search page | Grown-up's search dialogs |
|---|---|---|---|
| off | any | no mic | no mic |
| on | granted | mic, no prompt | mic, no prompt |
| on | withdrawn in system Settings | no mic | no mic |

Turning it on: explanation → the grown-up confirms → system prompt → on only if granted. Denied for good: the
setting stays off and says "Settings → Apps → Peke → Permissions → Microphone". Peke opens no system screen.

Explanation text (EN; ES ships alongside): "Voice search lets kids say what they're looking for. Their voice is sent
to this TV's speech service, not to your Jellyfin server. Android will ask you to allow the microphone."

## Server address (FR-014, FR-015)

| Typed or picked | HTTPS answers | Result |
|---|---|---|
| `host` or `host:port` | yes | HTTPS, no warning |
| `host` or `host:port` | no, HTTP does | warning; HTTP after confirm |
| `https://…` | yes | HTTPS |
| `https://…` | certificate not trusted (system or user CAs) | "Peke can't reach this server" |
| `http://…` | yes | HTTPS, no warning |
| `http://…` | no | warning; HTTP after confirm |
| discovered on the network | as `http://…` above | as `http://…` above |

Warning text (EN; ES ships alongside): title "This connection isn't encrypted"; body "Someone on your home network
could see or change what this TV and your server say to each other, including the parent code check. Use an https://
address if your server has one."; buttons "Use it anyway" and "Back".

This TV shows the server address, with "Not encrypted" under it for `http://`.

## Other devices (FR-017, FR-018)

| Source | Accepted | Can do |
|---|---|---|
| Peke itself | yes | the transport commands of `data-model.md` |
| A trusted controller (system UI, the remote's media keys, Google TV's controls) | yes | the same |
| Any other app on the TV or a paired device | no | nothing |
| Any controller: set, add, replace, remove items | — | refused |
| Jellyfin session message from the Kids plugin | shown, unless it repeats Peke's own warning | — |
| Jellyfin session message from anyone else | dropped | — |
| Jellyfin remote control (pause, stop) from an account allowed to | unchanged; the server decides | — |

## Errors (FR-019)

A background error toast reads "Oops, something went wrong" (ES alongside), never blocks the main thread, and leaves
the details in the log.
