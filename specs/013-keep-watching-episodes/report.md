# 013: distinguir los episodios de "Continuar viendo"

En la foto del Chromecast, la fila "Continuar viendo" del inicio de niños tenía cuatro tarjetas
iguales: el arte ancho de la serie ("Curious George") y el título "Jorge el curioso". No había forma
de saber qué episodio retomaba cada una. Lo comprobé en el emulador `peke_tv` (build debug, niño
"Manuel", servidor de casa) el 2026-11-04. "Antes" es `main` en 892ba701 y "después" es esta rama.

## Cambios

1. **c78737be: la tarjeta ancha de un episodio lo muestra a él.**
   - Imagen: usa la Primary del episodio (el fotograma que extrae Jellyfin). Si el episodio no
     tiene Primary, usa el thumb o el backdrop de la serie, igual que antes.
   - Título: "Ep. 6 · <nombre>". Si el episodio es de la temporada 2 o posterior, añade la
     temporada: "S2 · Ep. 6" en inglés y "T2 · Ep. 6" en español. Debajo va el nombre de la serie,
     más pequeño (línea de apoyo de DESIGN: 18 px, peso 800, "text, secondary").
   - TalkBack lee las palabras completas: "Jorge el curioso, Season 2, Episode 23, <nombre>".
   - Las películas y los demás ítems siguen igual. Todas las tarjetas anchas guardan el hueco de la
     segunda línea, así que la fila mide lo mismo mientras carga, con películas o con episodios.
   - El foco, la barra de progreso y la insignia de visto no cambian.
2. **41097d54: una tarjeta por serie** (en el inicio de niños, el flag `onePerSeries` de
   `ContinueWatchingCombined`). De cada serie se queda el episodio con `userData.lastPlayedDate` más
   reciente. Si hay empate, gana el de más progreso. La tarjeta ocupa el primer puesto en el que
   aparecía la serie, y el orden de la fila no cambia. Las películas y los ítems sin serie no se
   tocan. El inicio de los adultos no usa el flag.

## Peticiones

No hay peticiones nuevas. `getResume` y `getNextUp` ya piden `imageTypeLimit = 1` sin filtrar
`enableImageTypes`, así que la Primary del episodio llega con los ítems de la fila. No cambié ni los
campos ni los tipos de imagen. La deduplicación trabaja sobre los ítems combinados que ya llegan. Si
la fila queda más corta, no se piden más ítems. En el logcat del "después" salen las mismas
peticiones `/UserItems/Resume` y `/Shows/NextUp` que en el "antes".

## Antes y después

| | Antes (892ba701) | Después |
|---|---|---|
| Tarjetas en la fila | 25 (el límite) | 9 |
| Series distintas | 9 | 9 |
| "Jorge el curioso" | 11 tarjetas, todas con el mismo arte y título | 1: "S2 · Ep. 23 · …" con su fotograma |
| "Bing" | 7 tarjetas | 1: "Ep. 6 · Batido" |

- Antes: [before-row-start.png](before-row-start.png), [before-row-end.png](before-row-end.png)
- Después: [after-row-start.png](after-row-start.png), [after-row-end.png](after-row-end.png)

La fila de después, recorrida con el D-pad (título, segunda línea y lo que lee TalkBack):

```
S2 · Ep. 23 · Episodio 23 / Episodio 24 | Jorge el curioso | [Jorge el curioso, Season 2, Episode 23, Episodio 23 / Episodio 24]
Ep. 1 · La apuesta              | La vuelta al mundo de Willy Fog   | [La vuelta al mundo de Willy Fog, Episode 1, La apuesta]
Ep. 1 · Episodio 1              | Hey Duggee                        | [Hey Duggee, Episode 1, Episodio 1]
Ep. 1 · David el gnomo          | David el Gnomo                    | [David el Gnomo, Episode 1, David el gnomo]
S8 · Ep. 1 ·  /                 | Daniel Tigre y su mundo           | [Daniel Tigre y su mundo, Season 8, Episode 1,  / ]
Ep. 1 · Caillou hace galletas   | Caillou                           | [Caillou, Episode 1, Caillou hace galletas]
Ep. 6 · Batido                  | Bing                              | [Bing, Episode 6, Batido]
Ep. 1 · El viaje de D'Artacan   | D'Artacan y los tres mosqueperros | [D'Artacan y los tres mosqueperros, Episode 1, El viaje de D'Artacan]
Ep. 1 · El xilófono mágico      | Bluey                             | [Bluey, Episode 1, El xilófono mágico]
```

La tarjeta cabe sin recortes. La fila crece una línea (unos 37 px) y "Watch live" baja lo mismo.

## Verificación

- 923 tests unitarios en verde, entre ellos los nuevos de `KidsHomeLayoutTest` (etiqueta del
  episodio e imagen propia frente a la de la serie) y de `KidsHomeRowsTest` (la fila de niños pide
  una tarjeta por serie, y la deduplicación cubre el último visto, el empate por progreso, el
  primer puesto, las películas y una fila sin nada que quitar). ktlint pasa con pre-commit.
- El español se comprobó solo en los recursos (`values-es`: "Ep. %1$d", "T%1$d · Ep. %2$d"). El
  emulador está en inglés, y la app no hace caso al idioma por app de Android. Cambiar el idioma de
  la app exige entrar en la zona de adultos, y no lo toqué.

## Notas

- La temporada solo aparece de la 2 en adelante. Los ítems de la fila no dicen cuántas temporadas
  tiene la serie, y averiguarlo costaría una petición por serie. Por eso un episodio de la T1 de una
  serie con varias temporadas sale como "Ep. 3", sin temporada.
- Algunos nombres de la biblioteca no aportan nada: "Episodio 1" sale como "Ep. 1 · Episodio 1", y
  Daniel Tigre tiene un episodio llamado " / ". Son metadatos del servidor; la app no los filtra.
- El primer arranque del build de `main`, justo después de instalarlo, se quedó en el splash
  después de `GET /Items`. Con un force-stop y un relanzamiento cargó bien. No volvió a pasar ni lo
  investigué.
- Con foco en la última tarjeta de la fila, el anillo llega al borde derecho de la pantalla
  ([after-row-end.png](after-row-end.png)). El tamaño de la tarjeta no cambia en esta rama, así que
  no parece venir de aquí, pero no lo comprobé en `main`.
