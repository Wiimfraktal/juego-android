# Fuga Orbital — guía para continuar el proyecto

Juego Android (Kotlin, Canvas propio, sin motor). Tetris radial espacial: las naves salen del planeta en el centro y viajan arriba o abajo; el jugador las coloca lejos de la franja roja central. Esta guía resume todo lo decidido en la conversación de desarrollo (v0.1 → v0.49).

## Cómo trabaja el usuario
- Responder en español, lenguaje simple y personal, pocos tokens, sin tablas ni listas largas salvo necesidad; si no hay siguiente paso claro, proponer uno.
- Prueba solo en el celular con **Obtainium** apuntando al Release `latest`. No usa PC para compilar.
- Quiere que todo se haga en automático, sin pedir plan ni confirmación.
- Cada cambio: commit + push a `main` → GitHub Actions compila y publica el Release **"Fuga Orbital v0.N"** (N = número de ejecución) con `fuga-orbital-v0.N.apk`. Verificar con:
  `gh run watch <id> --repo Wiimfraktal/juego-android --exit-status` y errores con `gh run view <id> --log-failed | grep "e: "`.
  (No hay SDK de Android local; el CI es el compilador. `gh` GraphQL está bloqueado, usar `gh api` REST.)
- Commits en español; terminar con las líneas de atribución que indique la sesión.

## Estructura (app/src/main/java/com/cristal/abismo/)
- **Game.kt**: lógica en cuadrícula entera. `Piece` (controlada), `Placed` (naves fijadas, con gravedad visual `vy/vv`), `Shapes`. Zona roja = franja horizontal `|y-cy| <= ZONE` (ZONE=1, 3 filas). Bombas 10 % solo en Normal, máx. 2 seguidas por sentido y 4 en total. Dificultad `diff`: 0 Normal, 1 Heroico (4 filas menos, sin bombas), 2 Legendario (como Heroico + cada 2 s la nave actual sigue sola en `free` y sale otra). Power-ups: `shields` (sobre-escudo: `clearBand(ZONE+3)`), `revive()` (Regeneración). Anti-exploit: nunca retroceder hacia el centro (`prog`/`progMax`); límite de 4 reinicios de contacto por giro/arrastre.
- **GameView.kt** (~4500 líneas): todo el dibujo, UI y entrada. Fases: -1 carga (viaje galaxia→sistema solar→planeta, `drawTravel`, etapas T_GAL/T_THRU/T_SYS y llegada APPROACH), 0 menú, 1 explosión inicial, 2 partida. Pestañas en orden visual `TAB_ORDER = [3,0,1,2]`: MEJORAS | MENÚ | PERSONALIZAR | PERFIL (+ engrane, también en partida). Eventos (8: meteoros, tormenta solar, flota aliada con curvas bezier y láseres, eclipse por fases, aurora, agujero de gusano, ventisca) con `EV_DUR`=26 s. Configuración: música/efectos/voz, FPS (120/60/30 con Choreographer + preferredDisplayModeId), Gráficos Calidad/Rendimiento (`gfxAtStart` fija resoluciones). Hora y batería arriba. Planeta del menú gira sobre eje vertical con `Textures.Spinner` (mapa equirectangular, luz fija).
- **Textures.kt**: ruido 3D, planetas (`makePlanet`, lite para vistas previas), mapa de superficie y Spinner, naves por campo de distancia (`makeShipSprite`, cp variable; S, Z y O de doble casco sin alas), texturas de eventos (humo, plasma, aurora, espiral, sombra, anillo, placa), galaxia de viaje.
- **DiskCache.kt**: caché binaria (volcado de píxeles) con `VER` (subir al cambiar generadores). Sprites con `spriteTag` = resolución.
- **Secure.kt**: lingotes y power-ups firmados con HMAC (`Secure.put`, `verify`); `PurchaseProvider`/`NoPurchases` = base para la futura tienda con dinero (pestaña MEJORAS, recuadro "Próximamente"; el usuario pidió no poner nombres, cantidades ni títulos todavía).
- **Announcer.kt**: voz TTS del teléfono procesada (grave, eco de estadio). Frases: base + rachas + rangos + conteo de líneas estilo Halo (2 Doble, 3 Triple, 4 Masacre, 5 Exterminador … 10 Leyenda viviente). Subir `VER` para regenerar.
- **MusicPlayer.kt**: música en bucle sin cortes (dos MediaPlayer encadenados en HandlerThread) y `Sfx` (SoundPool: place_0..6, bar_solo, boom, intro_boom, recharge).
- Otros: Skins (24 superficies, 4 "Clasificado"), Factions (24 facciones), Ranks (estilo Halo Reach), Achievements/Stats, Emblem (14 formas, 30 símbolos, 32 colores), Backgrounds (fondos y `MusicThemes`).

## Música y sonido (tools/)
- `tools/banda.py` (principal) usa `tools/orquesta.py` y `tools/make_music.py`. Uso:
  `python3 tools/banda.py <VSCO> <carpeta_bancos> <salida> [game|neutron|regio|coro|solo|rock|duro|megaboom|all]`
  Luego copiar los `.ogg`/`.wav` a `app/src/main/res/raw/`.
- Bancos (no van en el repo; descargar con `git clone --depth 1` a una carpeta de trabajo):
  - VSCO 2 CE (CC0): github.com/sgossner/VSCO-2-CE — orquesta, órgano, clarinete, flauta, violín, arpa, güiro, congas.
  - Karoryfer (CC0) en github.com/sfzinstruments: karoryfer.big-rusty-drums, karoryfer.emilyguitar, karoryfer.meatbass.
  - SalamanderGrandPiano (CC-BY 3.0, Alexander Holm): github.com/sfzinstruments/SalamanderGrandPiano.
  - Sonatina Symphonic Orchestra (CC Sampling+): github.com/peastman/sso — coro real (Samples-looped/Chorus).
  - No existen (accesibles) acordeón ni guitarra acústica reales; el usuario quiere todo natural salvo **Duro** (french house con sintetizador permitido).
- Motor de mezcla: paneo con retardo interaural, reverb de sala (`room=True`), bus maestro con compresor + limitador (`master_bus`, suavizado circular para bucles sin salto).
- Temas: Épica (rock orquestal), Tensión, Rock (banda en vivo), Heroica, Neutron (folk-pop con piano de cola), Duro, Épica coral (himno: coro, órgano, metales; suena en el menú si se equipa), Regio (cumbia regia: clarinete, güiro, congas).
- No copiar canciones con derechos de autor (el usuario pidió covers; se explicó que solo se hacen piezas originales del mismo estilo).

## Pendientes / ideas abiertas
- Probar en emulador (MSI App Player) con control de computadora desde una sesión con "Computer use" activado.
- Tienda con dinero real y paquetes de edición limitada (estructura lista en Secure.kt).
- Si se consiguen grabaciones libres de acordeón o guitarra acústica, integrarlas.
