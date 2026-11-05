# Informe del spec 012: el buddy que no se va en el primer arranque

Tras instalar Peke de cero en el Chromecast (build benchmark, 2026-11-04 ~23:07), configurar la TV y
autorizar al kid "Manuel" con Quick Connect, la app se quedaba para siempre en la página de carga (el
buddy) en lugar del home del kid. Tras forzar la detención y volver a abrir, cargaba en menos de un
segundo. En el spec 011 ya se había visto lo mismo en el emulador (build debug) en 2 de unos 33
arranques, siempre el primero tras `adb install -r`.

## Causa

`ApplicationContent` calculaba qué mostrar en las páginas del kid (`kidPagesRoute`: SHOW, WAIT o
LAUNCH_RULE) fuera del `NavDisplay`, y la lambda de contenido de cada `NavEntry` capturaba ese valor.
Navigation 3 (navigation3-runtime 1.1.7) recuerda las entradas con el back stack como única clave:
`rememberDecoratedNavEntries` hace `remember(backStack.toList()) { backStack.map(entryProvider) }`
(comprobado en el bytecode con `javap`). Mientras el back stack no cambia, un `entryProvider` nuevo se
ignora y la entrada del home conserva el `route` de su primera composición.

Si `ApplicationContent` se dibuja por primera vez cuando `KidsModeService.state` todavía es `Loading`,
la entrada del home se queda en WAIT para siempre, aunque el estado pase a `Active` unos milisegundos
después. Esa ventana existe en cada entrega al kid: `restoreUser` cambia el usuario actual,
`KidsModeService` emite `Loading` (`onStart`) y lee el perfil de Room. Normalmente la lectura termina
antes del siguiente frame. Justo después del setup o de una reinstalación (Room y el JIT en frío, la
sincronización de los kids, el home que se construye y los workers que se ejecutan por primera vez) a
veces llega tarde, y entonces se pierde para siempre. Lo mismo valía para LAUNCH_RULE: su
`LaunchedEffect` no se habría disparado nunca en una entrada creada en WAIT.

### Por qué encaja con lo observado

- **La captura del atasco no tiene la barra lateral ni el saludo.** Con SHOW el home va dentro de
  `NavDrawer`, que siempre dibuja la barra. El buddy a pantalla completa es el `LoadingPage` de
  WAIT/LAUNCH_RULE.
- **Las peticiones observadas.** `/Users/Me` es el `refreshUser` de `TvLauncher.open`. `/UserViews`,
  `/LiveTv/Recordings/Folders` e `/Items?ids` son la construcción de los ajustes del home por
  `UserSwitchListener`, que no depende de la ruta, por el camino del perfil fijo del kid
  (`KidsProfilePolicy.homeSettings`, que solo se usa si el perfil está en caché). `/DisplayPreferences/default`
  es `LatestNextUpWorker`, que en una instalación nueva se ejecuta enseguida. Faltan las filas del home
  (`/UserItems/Resume`, `/Items?ParentId…`) porque `HomePage` nunca se llegó a componer.
- **El perfil estaba en caché.** El inicio de sesión del kid solo acaba en `SignedIn` si
  `kidProfileRepository.isKid` lo encuentra, y el home se construyó con el perfil. Con el perfil en caché,
  `KidsModeService.state` pasa a `Active`. Lo comprobé con una sonda temporal: KidsModeService real, base
  de kids real de Room (Robolectric, en memoria) y `Dispatchers.Default`, simulando 50 entregas del setup.
  Las 50 llegaron a `Active`. La sonda no forma parte del cambio.
- **Al relanzar funciona**: la nueva composición se hace con el estado ya resuelto, o al menos sin la
  carga del primer arranque.

### Sospechosos del encargo descartados

- **KidsModeService/lookUp con el token temporal del admin.** Las lecturas se agrupan por `rowId` del
  usuario guardado y el admin nunca es un usuario guardado (`AdminSession` vive en memoria con su propio
  `ApiClient`). En cualquier caso, con el perfil en caché el estado no depende de la lectura del servidor.
- **`KidsPluginService.askShared`.** `start()` del async LAZY despacha a `Default` y no se ejecuta dentro
  del `synchronized`. Una respuesta nula o un error terminan la lectura como "sin respuesta", que marca
  `unanswered`, no la deja colgada.
- **`ServerRepository.updateUserDto`, el setup/LaunchRule y los ids de dispositivo por kid.** La entrega
  llega a `AppContent` con el usuario correcto y su token, como confirma el log del servidor.
- **El colector único de ajustes del home (010).** Construyó los ajustes; no es lo que bloquea.
- **R8.** También pasa en debug.

## Corrección

`ui/nav/ApplicationContent.kt`: el `NavDisplay` con su enrutado pasa a un composable interno,
`KidPages`, y la ruta se lee dentro de cada entrada a través de `rememberUpdatedState`. Así una entrada
creada en WAIT se actualiza cuando el estado cambia. El resto de lo que se dibuja (`DestinationContent`,
`NavDrawer`, el error sin usuario) no cambia.

## Comprobación

- Test de regresión `ui/nav/KidPagesTest.kt` (Robolectric + Compose): las páginas se dibujan primero en
  WAIT y después la ruta pasa a SHOW (debe verse la página) o a LAUNCH_RULE (debe llamarse una vez a la
  regla de lanzamiento). Con la extracción pero sin la corrección fallan los dos: "could not find any
  node … 'home'" y "expected:<1> but was:<0>". Con la corrección pasan.
- `nix develop -c ./gradlew :app:testDebugUnitTest`: 921 tests, 0 fallos.
- `nix develop -c pre-commit run --files …` (ktlint incluido) pasa en los dos archivos cambiados.
- **No probado en un dispositivo.** El emulador `peke_tv` arrancó, pero adb nunca lo vio en línea (el
  servidor adb de Windows se caía y relanzaba solo). El Chromecast es la TV del owner y lleva el build
  de release. Además, el atasco necesita un inicio de sesión nuevo con Quick Connect, que requiere las
  credenciales del admin.

### Cómo verificarlo en el dispositivo (owner)

1. Instalar el build con la corrección en el Chromecast sin datos: desinstalar `dev.aldoborrero.peke`
   e instalar el APK nuevo.
2. Configurar la TV y autorizar a Manuel con Quick Connect, como el 2026-11-04.
3. Debe abrirse el home de Manuel con la barra lateral. Repetir 3 o 4 veces (desinstalar e instalar)
   para cubrir la carrera.

## Queda igual (fuera del alcance de la corrección)

- La lambda `page` de `KidPages` (preferencias, usuario, servidor, estado del cajón) sigue capturada
  por entrada, como antes: si las preferencias cambian mientras una página está en el back stack, esa
  página conserva las de su creación hasta que el back stack cambie. No se ha visto ningún fallo por
  ello y cambiarlo altera más que el atasco. Si interesa, se arregla igual, con `rememberUpdatedState`.
- El `NavDisplay` de `MainContent` usa el mismo patrón, pero cada navegación reemplaza `backStack[0]` y
  crea una entrada nueva, así que no se queda con valores viejos de esta forma.
