# Informe 016: velocidad en el Chromecast

Objetivo: fotogramas más fluidos en el Chromecast con Google TV del dueño (sabrina: S905D2 4×A55, Mali-G31,
armeabi-v7a, 2 GB, Android 14) sin cambiar el aspecto (DESIGN.md: borde de 6 px + anillo de 8 px + escala al enfocar,
página atenuada tras la barra lateral abierta, esquinas redondeadas). Rama `016-speed` desde `origin/main` (`993bb9b9`).

**Estado: sin medir en el dispositivo.** El Chromecast no aparecía en `adb devices` (el puerto 36941 ya no respondía y
mDNS no anunciaba nada), así que, por indicación del coordinador, los cambios se han hecho sin la tele y las mediciones
quedan preparadas para cuando se vuelva a emparejar ([Medición](#medición)). Cada cambio se apoya en el análisis de la
traza de referencia y en tests unitarios. Ninguno se ha confirmado con números en el dispositivo; si alguno no mejora,
se revierte su commit.

## Antes y después

Recorrido de referencia (`journey.sh`): fila 1 derecha/izquierda, fila 2, abrir y cerrar la barra lateral, abrir una
serie, episodios, atrás. Build `benchmark` compilada con `speed-profile`.

### gfxinfo

| | Antes | Después |
|---|---|---|
| Fotogramas | 194 | pendiente |
| Janky | 15,5 % | pendiente |
| p50 / p90 / p99 | 25 / 42 / 125 ms | pendiente |
| Slow UI thread | 16 | pendiente |

### Traza por fase (frame timeline de SurfaceFlinger y RenderThread)

"Antes" sale de la traza de referencia, pasada por las mismas consultas que usará el "después" (`sql/1_frames.sql` y
`sql/3_render.sql` del script de medición, que reproducen el `09_phase.out` original).

| Fase | Fotogramas | Janky | Media / máx. (ms) | DrawFrame RT media / máx. (ms) | Después |
|---|---|---|---|---|---|
| P1 fila 1 der ×6 | 58 | 54 | 35,3 / 42,3 | 14,1 / 25,7 | pendiente |
| P2 fila 1 izq ×6 | 58 | 58 | 31,4 / 45,3 | 12,4 / 30,7 | pendiente |
| P5 fila 2 izq ×5 | 10 | 10 | 42,0 / 81,3 | 15,1 / 22,6 | pendiente |
| P7 cerrar barra | 12 | 12 | 38,3 / 62,6 | 15,3 / 17,3 | pendiente |
| P9 abrir serie | 13 | 13 | 55,4 / 148,7 | 16,5 / 29,4 | pendiente |
| P11 serie der ×4 | 9 | 9 | 34,9 / 39,7 | 15,8 / 20,7 | pendiente |
| P12 atrás a inicio | 10 | 10 | 59,3 / 217,2 | 18,1 / 27,8 | pendiente |

Otros datos de la referencia: 54 `flush layers` (197,5 ms en total, máx. 18,9 ms) y 36 `drawLayer [graphicsLayer]
1920×1080` de la barra lateral; peor fotograma en el hilo principal: 191 ms al volver a inicio y 103–111 ms al abrir la
serie. En las fases P4 y P6 las pulsaciones se juntan en la traza y no salen como fase propia, igual que en la consulta
original.

## Qué ha cambiado

Un commit por cambio, en el orden del encargo.

### 1. Atenuado de la página sin capa a pantalla completa

`ui/nav/NavDrawer.kt`. La barra abierta atenuaba la página con `graphicsLayer { alpha }`, una capa fuera de pantalla de
1920×1080 en cada fotograma de la animación. Ahora `Modifier.dimmedTo` pinta encima el fondo del tema con la opacidad
que falta (`1 − alfa`). Sobre un fondo opaco el resultado es idéntico píxel a píxel, y las páginas de niños están sobre
el fondo liso del tema, sin backdrop. Se descartó `CompositingStrategy.ModulateAlpha`: aplica el alfa a cada operación
por separado y las tarjetas que se solapan se verían distintas.

Test: `PageDimTest` compara píxel a píxel la capa con el velo al 35 %, al 70 % y al 100 % sobre tarjetas solapadas y
redondeadas.

### 2. Transición corta entre páginas de niños

`ui/nav/ApplicationContent.kt`. Nav3 1.1.7 usa por defecto un fundido cruzado de 700 ms al entrar y al volver (lo dice
el bytecode de `defaultTransitionSpec`). Durante casi un segundo mantiene dos páginas compuestas y dibujadas con alfa.
`KidPages` usa ahora un fundido de 150 ms (`KID_PAGES_FADE_MILLIS`) en los dos sentidos. Sigue siendo opacidad, como
pide DESIGN.md.

Test: `KidPagesTest` comprueba que la página anterior desaparece en ≤ 250 ms al entrar y al volver. Con 700 ms falla
(752 ms).

Conservar el estado de inicio: ya estaba resuelto. Cada entrada conserva su ViewModel mientras está en la pila; la
posición y los scrolls van en `rememberSaveable` dentro del `SaveableStateHolder` de Nav3; al volver solo se recargan
las filas de "seguir viendo". El fotograma de 217 ms al volver es la composición nueva de las filas visibles, no una
recarga de datos. Ver [Lo que no se ha hecho](#lo-que-no-se-ha-hecho).

### 3a. Un solo relleno de pantalla completa por fotograma

`MainActivity.kt` y `MainContent.kt`. El fondo se pintaba cuatro veces a 1080p en cada fotograma: el fondo de la
ventana del tema (negro), el `Surface` de `MainActivity`, `.background()` en `MainContent` y el `containerColor` de su
`Surface`. En la Mali-G31 cada relleno a pantalla completa cuesta del orden de 1–2 ms de GPU. Ahora:

- `MainContent`: solo el color del `Surface` (sin el `.background()` repetido).
- `MainActivity`: el `Surface` con el color de página envuelve únicamente la carga previa a las preferencias. Una vez
  hay preferencias, `MainContent` pinta el suyo. `window.setBackgroundDrawable(null)` después de `setContent`: la
  ventana de arranque sigue usando el tema y Compose cubre siempre la ventana entera.

Sin test unitario propio (la ventana no se puede comprobar en Robolectric). Lo cubren la suite completa y la build
`benchmark`. Confirmar con `debug.hwui.overdraw` (ver [Revisar en la tele](#revisar-en-la-tele)).

### 3b. Las tarjetas no pintan el color de superficie debajo de la imagen

`kids/ui/KidsCards.kt`. `KidsPictureCard` pintaba el color de superficie redondeado debajo de cada imagen, que lo tapa
entera. Ahora la tarjeta no tiene color de fondo (`Color.Transparent`) y la superficie solo aparece mientras carga la
imagen, como `placeholder` de Coil (`ColorPainter`). El crossfade de Coil está desactivado, así que no hay fundido
entre ambos. Si no hay imagen o falla, sigue saliendo la baldosa de color de siempre.

El tratamiento de foco (`PekeFocusNode`) ya era ligero: un solo nodo de dibujo, sin `graphicsLayer`, trazados en caché
y la escala aplicada en el `DrawScope`. Skia ya convierte los trazados redondeados en rrect. No se ha tocado.

Test: `KidsPictureCardLoadingTest`, con un cargador de Coil que nunca termina: la tarjeta muestra el color de
superficie mientras carga. Sin el relleno temporal, falla.

### 4. Abrir y cerrar la barra lateral ya no recompone la página

`ui/nav/NavDrawer.kt`. `NavDrawer` leía `drawerState` en su cuerpo (el objetivo del alfa y el `enabled` del
`BackHandler`). Cada apertura o cierre recomponía `NavDrawer`, que pasaba una cadena de `Modifier` nueva a la página, y
la recomposición bajaba por toda la home (23–35 ms en la referencia). Ahora:

- `rememberPageAlpha`: un `Animatable` con el mismo muelle y umbral que `animateFloatAsState`. El estado del cajón se
  lee en un efecto (`snapshotFlow`) y el valor solo en el dibujo.
- `DrawerBackHandler`: el `BackHandler` en un composable propio, así su lectura del estado solo lo recompone a él.

Test: `PageAlphaTest`, que abre y cierra el cajón y comprueba que la página se compone una sola vez y que el alfa llega
a 0,35 y vuelve a 1. Con la versión anterior falla.

## Revisar en la tele

Cambios que pueden notarse a la vista y que hay que mirar en el dispositivo antes de darlos por buenos:

- [ ] **Transición de 150 ms (cambio 2):** el paso entre inicio y una serie es más rápido que antes (700 ms). Si se
  prefiere más suave, subir `KID_PAGES_FADE_MILLIS`, hasta unos 250 ms.
- [ ] **Imágenes con transparencia en tarjetas (cambio 3b):** un póster o miniatura PNG con zonas transparentes mostraría
  el fondo de la página en vez del color de superficie. Con JPEG no hay diferencia.
- [ ] **Overdraw (cambio 3a):** `adb shell setprop debug.hwui.overdraw show`, recorrer inicio y una serie, y después
  `adb shell setprop debug.hwui.overdraw false`. El fondo debería verse sin tinte (1×), y no azul o verde como antes.

## Lo que no se ha hecho

- **Mantener inicio compuesto bajo la serie.** Haría falta una `SceneStrategy` propia que dejase la home compuesta y
  oculta mientras la serie está encima. Cuesta memoria en un aparato de 2 GB, afecta al foco, a los `BackHandler` y al
  ciclo de vida de la entrada (el refresco al volver). Sin poder medir no compensa el riesgo. Los cambios 3a y 3b
  abaratan esa recomposición.
- **Quitar el `graphicsLayer` del título de cada tarjeta** (`KidsPictureCardTitle`, que se desplaza con la escala): es un
  RenderNode sin capa fuera de pantalla, y moverlo al dibujo obligaría a regrabar el texto en cada fotograma de la
  animación. Se deja.
- **Ningún cambio se ha descartado por medición**, porque no se ha podido medir. Si alguno no ayuda en la tele, se
  revierte su commit y se anota aquí.

## Medición

El script del coordinador (`speed-measure.sh`, con `phases.sh` y `sql/`) hace por cada etiqueta: compilar
`:app:assembleBenchmark`, `adb install -r` del APK `armeabi-v7a` (misma clave de depuración, conserva la sesión del
niño), `cmd package compile -m speed-profile -f`, arrancar en inicio, N recorridos con `dumpsys gfxinfo` (reset antes de
cada uno), un recorrido más con la configuración Perfetto de la referencia (`config.pftxt`, 36 s), y las consultas por
fase sobre la traza. No hace `pm clear` ni desinstala, y se relanza solo dentro del devshell.

```sh
# antes: lo instalado ahora en la tele (si sigue siendo la build de referencia)
SKIP_INSTALL=1 speed-measure.sh 192.168.105.146:PUERTO before 3
# después: compila e instala esta rama
speed-measure.sh 192.168.105.146:PUERTO after 3
```

Medir `before` antes de instalar nada: después de `after` ya no está la build de referencia en la tele. Para medir cada
cambio por separado, ejecutar el script desde un clon aparte con ese commit, con una etiqueta por commit.
