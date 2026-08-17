# cast-dlna-tv

Receptor DLNA/UPnP (`MediaRenderer:1`, DMR-1.50) para Android TV. Hermano del
receptor de escritorio [`cast-dlna`](../cast-dlna/), del que hereda el stack
UPnP y, sobre todo, lo aprendido peleando con controladores reales.

## Estado

El receptor responde las 27 acciones de `scripts/probe-renderer.py` del
proyecto hermano y reproduce, pausa, busca y ajusta volumen. Falta la interfaz
—irá con Wulfenite UI— y validar el descubrimiento SSDP fuera del emulador.

## Probar

```bash
# Emulador de Android TV (creado con avdmanager, imagen android-36 x86_64)
QT_QPA_PLATFORM=xcb ~/Android/Sdk/emulator/emulator -avd tv -gpu auto

./gradlew assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 forward tcp:8201 tcp:8201

# La misma batería que valida el receptor de escritorio
../cast-dlna/scripts/probe-renderer.py --url http://127.0.0.1:8201/device.xml --suite
```

El emulador está tras un NAT, así que el descubrimiento por SSDP no llega desde
la red local: se prueba redirigiendo el puerto. El descubrimiento hay que
validarlo en un televisor de verdad o en un dispositivo Android normal.

## Lo que hereda del receptor de escritorio

Cada una de estas decisiones costó una sesión de depuración con apps reales:

| Detalle | Por qué |
|---|---|
| `HEAD` sin cuerpo, con `keep-alive` real | Un `HEAD` con cuerpo descarrila la conexión: el cliente lee ese cuerpo como el principio de la respuesta siguiente. Hay controladores que comprueban con `HEAD` que el receptor sigue vivo en cada vuelta de su sondeo |
| `sec:ProductCap` en el descriptor | Sin él, los controladores que mandan subtítulos por `sec:CaptionInfoEx` anuncian que el dispositivo «no tiene soporte para subtítulos» |
| MIME de subtítulo al principio del `Sink` | Hay controladores que solo miran el principio de la lista |
| Reanuncio al cambiar de IP | Los controladores guardan el dispositivo por UDN junto a su dirección, con media hora de validez |
| Identificador estable entre arranques | Uno nuevo en cada inicio aparece como un receptor distinto en la lista |
| Aceptar `SetVolume` y descartarlo en compartir pantalla | Devolver un fault hace que el controlador dé el receptor por roto |
| Las cuatro vías de subtítulos | Samsung, BubbleUPnP/WVC, `<res>` de texto y cabecera `CaptionInfo.sec` |

## Lo propio de Android

| Detalle | Por qué |
|---|---|
| `MulticastLock` | Sin él el sistema descarta los paquetes multicast que no van dirigidos a la app, y los `M-SEARCH` no llegan nunca |
| `usesCleartextTraffic` | DLNA es HTTP sin cifrar; desde Android 9 se bloquea por defecto y no se puede descargar nada |
| `LEANBACK_LAUNCHER` y `touchscreen` no requerido | Sin ambos la app no aparece en el catálogo del televisor |
| Servicio en primer plano | En un televisor se sale a la pantalla de inicio constantemente, y el receptor tiene que seguir anunciándose |
| Llamadas al reproductor en el hilo principal | ExoPlayer solo se deja tocar desde el hilo que lo creó, y las peticiones llegan desde los hilos del servidor |

## Lo que aquí sale gratis

El retraso creciente de los directos, que en el receptor de escritorio quedó
sin resolver, aquí se declara y ya está: `MediaItem.LiveConfiguration` con
`targetOffsetMs` y el margen de velocidad. Es el mismo mecanismo que allí no
se pudo usar porque mpv no lo tiene y cambiar la velocidad a mano saturaba el
audio.
