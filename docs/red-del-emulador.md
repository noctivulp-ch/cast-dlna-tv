# La red del emulador, y qué se puede probar en ella

Medido el 2026-08-17 contra el AVD `tv` (Android 16, `sdk_google_atv64_x86_64`,
1920×1080 @ 320 dpi) con cast-dlna-tv **en primer plano** y verificado con los
contadores de `/proc/net/snmp` del propio emulador.

**Resumen en una línea:** el descubrimiento SSDP **sí se puede probar en el
emulador**, pero el controlador tiene que correr **dentro**. Lo que no cruza es
el NAT hacia la LAN de casa.

---

## Lo que se midió

| Prueba | Resultado |
|---|---|
| La app anuncia dentro del emulador | **✓** +18 datagramas al arrancar |
| Esos anuncios salen a la LAN | **✗** el host no ve nada de `10.0.2.x` |
| M-SEARCH multicast desde la LAN | **✗** no entra |
| M-SEARCH unicast por `redir add udp` | entra, pero **lo contesta el Chromecast de fábrica** |
| M-SEARCH multicast **desde dentro** | **✓** la app responde bien |
| Control (SOAP, `device.xml`) por `adb forward` | **✓** funciona entero |

Los +18 del arranque son exactamente la ráfaga inicial: `repeat(3)` × 6 tipos.
El intervalo de anuncio en régimen es `MAX_AGE / 3` = **600 s**, así que una
ventana de observación corta no ve nada y eso **no** es un fallo.

### La prueba de control, que es la que cierra el asunto

Buscando desde dentro del emulador:

| ST buscado | Respuestas de la app |
|---|---|
| `urn:schemas-upnp-org:device:MediaRenderer:1` | **2** |
| `urn:schemas-upnp-org:service:AVTransport:1` | **2** |
| `urn:schemas-upnp-org:device:MediaServer:1` | **0** |

Los dos son el `repeat(2)` de `Ssdp.responder()` —manda cada respuesta dos veces
porque UDP sobre wifi se pierde—. Y el **0** del tipo que la app no sirve
demuestra que las respuestas son suyas y no de otro proceso.

---

## Por qué no cruza, y por qué el unicast engaña

El emulador usa la red interna de QEMU (SLIRP): `10.0.2.15/24`, pasarela
`10.0.2.2`. El host **no tiene interfaz en esa red** — solo la ve por los
reenvíos de puerto. El multicast no se enruta ahí.

La consola del emulador **sí** admite reenvío UDP:

```bash
printf 'auth %s\nredir add udp:11900:1900\n' "$(cat ~/.emulator_console_auth_token)" | nc 127.0.0.1 5554
```

y los datagramas llegan (comprobado: +10 `InDatagrams` por 10 envíos). **Pero no
sirve**, y el motivo es la parte que no es obvia:

- un datagrama **unicast** se entrega a **UN** socket;
- un datagrama **multicast** se entrega a **TODOS** los sockets unidos al grupo.

Medido: multicast → **2 sockets**; unicast → **1 socket**.

En un Android TV hay **dos** procesos ligados a UDP 1900: cast-dlna-tv y el
receptor **Chromecast/DIAL** de fábrica (`uuid:6300378d-…`, `SERVER: …
Chromecast/1.6.18`). Al unicast le toca Chromecast, así que buscando
`upnp:rootdevice` parece que "algo responde" cuando no es la app.

En producción esto **no es un problema**: los controladores reales usan
multicast, y ahí los dos reciben y los dos contestan.

---

## Qué se puede probar hoy, sin tocar la red

### Todo el control, por TCP

```bash
adb -s emulator-5554 forward tcp:18201 tcp:8201
curl -s http://127.0.0.1:18201/            # estado legible
curl -s http://127.0.0.1:18201/device.xml  # descriptor UPnP
```

Los `controlURL` salen del descriptor (`/ctrl/avt`, `/ctrl/rcs`, `/ctrl/cms`).
Una llamada SOAP de ejemplo, verificada de punta a punta:

```bash
curl -s -X POST http://127.0.0.1:18201/ctrl/avt \
  -H 'Content-Type: text/xml; charset="utf-8"' \
  -H 'SOAPAction: "urn:schemas-upnp-org:service:AVTransport:1#Play"' \
  --data-binary '<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID><Speed>1</Speed></u:Play></s:Body></s:Envelope>'
```

`SetAVTransportURI`, `Play` y `GetTransportInfo` responden correctamente.

### El descubrimiento, desde dentro

```bash
adb -s emulator-5554 shell "printf 'M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n' | timeout 4 nc -u -w3 239.255.255.250 1900"
```

`nc` no imprime la respuesta —cierra el socket antes de que llegue, porque
`responder()` espera hasta `MX × 500 ms`— así que se comprueba con los
contadores:

```bash
adb -s emulator-5554 shell "cat /proc/net/snmp" | awk '/^Udp: [0-9]/{print "entrantes="$2, "salientes="$5}'
```

**Lo único que queda fuera** es castear **desde otro aparato** al emulador. Para
eso hace falta el puenteo de abajo, o instalar en hardware real.

---

## Tarea: puentear la red del emulador a la LAN

Lo que sigue **no está hecho ni probado**. Es el terreno verificado y las
opciones, para que quien lo monte no empiece de cero.

### Lo que ya está disponible en esta máquina (comprobado)

| Pieza | Estado |
|---|---|
| `-net-tap <interfaz>` en el emulador | **soportado** (`emulator -help`) |
| `-net-tap-script-up` / `-down` | soportado |
| `/dev/net/tun` | existe, permisos `666` |
| módulo `tun` | cargado |
| `sudo` | el usuario está en `wheel` |
| LAN | **`wlan0`, 192.168.0.10/24** |
| Overlays ZeroTier | `ztpp6awghq` 10.208.209.142/24, `ztu7tbxgmf` 10.123.72.142/24 |

### El obstáculo de verdad: la LAN es wifi

**Un TAP no se puede puentear a una interfaz wifi en modo cliente.** No es un
problema de configuración: 802.11 usa tramas de 3 direcciones y una estación no
puede presentar varias MAC sin modo 4-address/WDS, que ni los controladores ni
el punto de acceso suelen dar. Cualquier receta de `brctl addif br0 wlan0` va a
fallar o a funcionar a medias.

De ahí que las opciones no sean equivalentes:

**Opción A — cable.** Con Ethernet el puente es limpio y es el camino corto:
TAP + eth en un `br0`, el emulador queda como un aparato más de la LAN y el
multicast cruza solo. Si hay un cable a mano, **es la opción a intentar primero**.

**Opción B — wifi con proxy ARP + relé de multicast.** Sin cable no hay puente,
así que se enruta y se relega el multicast a mano:

1. TAP en la misma subred que la LAN, con proxy ARP (`parprouted` es la
   herramienta clásica para justo este caso).
2. Un relé SSDP en el host entre el TAP y `wlan0`: reenviar
   `239.255.255.250:1900` en las dos direcciones (`smcroute`, `igmpproxy`, o un
   relé propio en Python, que para SSDP son pocas líneas).
3. **Lo que hay que vigilar:** la app anuncia `LOCATION: http://<su
   IP>:8201/device.xml`, sacada de `ConnectivityManager` (ver
   `Receptor.ipLocal`). Esa URL tiene que ser **alcanzable desde el teléfono**, o
   el controlador descubre el aparato y luego no puede leer el descriptor ni el
   medio. Es el fallo más probable de este camino, y no se ve en el
   descubrimiento: se ve después, como "aparece y no reproduce".

**Opción C — ZeroTier.** Los overlays de ZeroTier **sí** son L2 y admiten puente
y multicast. Si el teléfono de pruebas se une a la misma red ZeroTier, se puede
puentear el TAP contra la interfaz `zt…` y evitar del todo el problema del wifi.
Sin verificar, pero es la que menos pelea con el medio físico.

### Cómo saber que funcionó

Tres comprobaciones, en orden. Las dos primeras salen de este documento:

1. **El host ve los anuncios.** Escuchando en `239.255.255.250:1900` unido al
   grupo por `wlan0`, tienen que aparecer paquetes con origen en la subred del
   emulador. Hoy solo aparecen los del router (192.168.0.1).
2. **Un M-SEARCH multicast desde el host recibe respuesta de la app** para
   `urn:schemas-upnp-org:device:MediaRenderer:1` — y con `SERVER: …
   cast-dlna-tv/1.0`, **no** con `Chromecast/…`. Ese detalle es el que distingue
   haber puenteado de estar hablando con DIAL.
3. **Un controlador del teléfono lo encuentra y reproduce.** Es lo único que
   valida la opción B de punta a punta, porque es lo que ejercita que el
   `LOCATION` anunciado sea alcanzable.

### Antes de dar por bueno un fallo

Dos cosas que ya hicieron perder tiempo en esta sesión:

- **Comprobar qué app está en primer plano.** Toda la primera tanda de medidas se
  hizo con otra app encima; hubo que repetirla. Se mira con
  `adb shell dumpsys activity activities | grep ResumedActivity`.
- **No confundir "responde algo" con "responde la app".** El Chromecast de
  fábrica contesta a `upnp:rootdevice` y a `ssdp:all`. Buscar siempre un tipo que
  **solo** sirva cast-dlna-tv, y mirar la cabecera `SERVER`.
