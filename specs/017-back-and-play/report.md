# 017 · Mando y reproductor: informe

## 1. Back con el sidebar abierto

- `ui/nav/NavDrawer.kt`: nueva `backClosesNavDrawer(drawerValue, destination)`, verdadera con el drawer abierto en
  cualquier página que no sea el home. Un segundo `BackHandler` (solo en `activeEntry`, igual que el del home) pide el
  foco para la página, y eso cierra el drawer (su estado sigue al foco). El siguiente Back retrocede una página, como
  antes.
- Home sin cambios: Back con el drawer cerrado lo abre; con el drawer abierto, Back va al sistema y la app se cierra
  sin gate (Principio IV).
- El foco vuelve a donde estaba: la página va envuelta en `focusRequester` + `focusRestorer()` + `focusGroup()`. El
  `focusGroup()` hace falta porque `focusRestorer()` no crea su propio focus target y, sin él, se queda sin efecto.
  Efecto secundario a propósito: D-pad Right desde el drawer también vuelve al elemento que tenía el foco, y no al
  vecino geométrico. Si no hay nada guardado, entra el foco por defecto de la página.
- Prioridad: los dos handlers del drawer se registran antes que la página, así que los `BackHandler` de la página
  (`KidsTimesUpPage`, el gate de perfil, `GrownUpGuard` y `ResetTv`, compuestos dentro o por encima) siguen ganando
  donde aplican.
- Tests: `NavDrawerBackTest` (home/no home × abierto/cerrado) y `DrawerBackCloseTest` (Compose/Robolectric, con el
  mismo cableado: Back cierra y devuelve el foco al elemento anterior, el siguiente Back retrocede, y en el home abre
  y luego deja pasar el Back).
- KDoc de `backOpensNavDrawer` y la regla de Back en DESIGN.md "Decided while testing the app" actualizados.

## 2. Icono de play/pausa

- `playbackButtonStyle(large = true)`: padding de 24 a 20 cpx, así que el icono pasa de 28 a 36 px dentro del círculo
  de 76 px (47 %, frente al 42 % de los botones de 62 px con icono de 26 px).
- Sin ajuste óptico: el centroide del triángulo (x = 24,33 de 48) ya queda unos 0,25 px a la derecha del centro.
  Además, el glifo lleva su propio anillo, que se descentraría dentro del círculo si se moviera el icono.
- KDoc y DESIGN.md (nueva entrada "Player buttons") con los números nuevos. Test: `PlaybackButtonStyleTest`.

## Verificación

- `testDebugUnitTest` completo en verde (con el cambio 1), tests de `ui.playback.*` en verde (cambio 2), ktlint y
  pre-commit en verde.
- Sin dispositivo: el Chromecast no estaba disponible y el emulador peke_tv no aparecía en adb. Quedan pendientes las
  capturas antes/después del reproductor en pausa y la prueba con mando en el dispositivo.
