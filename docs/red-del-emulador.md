# La red del emulador, y qué se puede probar en ella

> Describe el entorno de pruebas del autor (emulador y red de pruebas). Los scripts que se
> mencionan son herramientas de ese entorno y no se publican con el repositorio.

Medido el 2026-08-17 contra el AVD `tv` (Android 16, `sdk_google_atv64_x86_64`,
1920×1080 @ 320 dpi) con cast-dlna-tv **en primer plano** y verificado con los
contadores de `/proc/net/snmp` del propio emulador.

**Resumen en una línea:** el emulador ya está puenteado a la red ZeroTier y se
le puede castear desde el teléfono (sección «El puente por ZeroTier»). Lo de
abajo describe cómo se comporta la red interna del emulador **sin** ese puente.

Sin puente, el descubrimiento SSDP **sí se puede probar en el emulador**, pero
el controlador tiene que correr **dentro**: lo que no cruza es el NAT hacia la
LAN de casa.

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

## Puentear la red del emulador: las opciones que se estudiaron

Se acabó montando la **opción C**, que está en la sección siguiente. Lo que va
aquí es por qué las otras dos se descartaron, que sigue valiendo si algún día
cambia el entorno —por ejemplo, si aparece un cable.

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

**Opción C — ZeroTier.** Es la que se ha montado. Ver la sección siguiente.

---

## El puente por ZeroTier (opción C), montado

Dos guiones en `scripts/`. El primero deja la red preparada, el segundo arranca
el emulador dentro de ella y comprueba que se le puede castear.

```bash
sudo ./scripts/puente-zerotier.sh montar   # br-cast = ztu7tbxgmf + tap-tv, y el DHCP
     ./scripts/emulador-en-zerotier.sh arrancar    # SIN sudo (ver más abajo)
     ./scripts/emulador-en-zerotier.sh comprobar
     ./scripts/emulador-en-zerotier.sh parar
sudo ./scripts/puente-zerotier.sh desmontar       # deja la red como estaba
```

**Verificado el 2026-08-17, de punta a punta:** el emulador coge `10.123.72.242`
por DHCP, hace ping al teléfono personal (`10.123.72.227`) con 3/3 y rtt 21–240
ms, y el teléfono lo ve en su lista de dispositivos.

### Lo medido antes de montarlo

| Comprobación | Resultado |
|---|---|
| Teléfono personal alcanzable | **✓** `ping -I ztu7tbxgmf 10.123.72.227` 3/3, rtt 164–680 ms |
| `ztu7tbxgmf` admite puente | **✓** `tun type tap`, `BROADCAST,MULTICAST,UP`, mtu 2800 |
| Teléfono en la tabla de vecinos | **✓** `10.123.72.227 lladdr 7e:48:87:9c:90:a6` |
| `-net-tap` en el emulador | **✓** |
| Direcciones | PC `10.123.72.142`, teléfono `10.123.72.227` |

El rtt de cientos de milisegundos importa: es un enlace por internet, no una
LAN. El descubrimiento y el control lo aguantan; el *cast de pantalla* va a ir
peor que en casa, y ese retraso será del enlace y no del receptor.

### El paso que no puede dar el guion

**Marcar el equipo como puente en ZeroTier.** En my.zerotier.com → la red →
sección **Members** → desplegar la fila de esta PC (`10.123.72.142`) → casilla
**`Active Bridge`**, entre `Authorized` y `Do Not Auto-Assign IPs`. Ese es el
nombre exacto: no existe ninguna opción llamada «Allow Bridging», y buscarla
hace perder el tiempo. Sin la casilla, ZeroTier descarta las tramas cuya MAC de
origen no conoce —las del emulador son justo eso— y el puente queda mudo **sin
dar ningún error**.

### Qué va con `sudo` y qué no

`puente-zerotier.sh` **sí** (crea interfaces, mueve direcciones, levanta el
DHCP). `emulador-en-zerotier.sh` **no**, y no es un detalle de estilo: `sudo`
vacía `DISPLAY`, `XAUTHORITY` y `XDG_RUNTIME_DIR`, así que el emulador arranca
sin poder abrir ventana y parece que «no arranca» cuando el proceso está vivo.

### El cortafuegos, que es el fallo que más despista

Con `ufw` activo, los `DHCPDISCOVER` del emulador se descartan antes de llegar a
dnsmasq. El síntoma engaña porque todo lo demás parece correcto: `tap-tv` en
`LOWER_UP`, dnsmasq escuchando en `0.0.0.0:67`, y el emulador pidiendo dirección
en bucle —`DhcpClient: Broadcasting DHCPDISCOVER` en su logcat— sin que nadie
conteste. El guion lo abre solo:

```bash
ufw allow in on br-cast to any port 67 proto udp
```

Solo hace falta para el puente. El tráfico entre el emulador y el teléfono va
**puenteado** y no pasa por netfilter —`br_netfilter` no está cargado—, así que
el cortafuegos no lo toca.

### Por qué hace falta un DHCP propio

La imagen del AVD no deja `adb root` («adbd cannot run as root in production
builds»), así que no hay forma de escribirle una dirección estática desde fuera,
y ZeroTier asigna las suyas por su controlador —que no llega a un invitado
puenteado—. De ahí el `dnsmasq` acotado a `br-cast` con un rango de cuatro
direcciones altas.

Servir DHCP sobre un puente que da a una red ajena suena arriesgado y conviene
saber por qué no lo es aquí: **los miembros de ZeroTier reciben su dirección del
controlador y nunca emiten un DHCP DISCOVER**, así que el único que va a aceptar
la oferta es el emulador. Aun así el guion lo levanta y lo mata con el emulador,
en vez de dejarlo puesto.

### La salida a la LAN de casa, sin la cual conecta pero no reproduce

El puente mete al emulador en la red de ZeroTier, pero **el medio no viaja por
ahí**. Quien manda el vídeo anuncia la URL con *su* dirección de la wifi de
casa —CastLab mandó `http://192.168.0.11:9125/…`—, y el emulador, que solo está
en `10.123.72.0/24`, no tiene forma de llegar. El síntoma es engañoso porque
todo lo visible funciona: el teléfono lo encuentra, conecta, dice que envía, y
la pantalla se queda **en negro y sin audio** con cualquier medio.

Se resuelve dando salida al emulador a través de la PC, que está en las dos
redes. Lo hace `puente-zerotier.sh montar`:

```bash
sysctl -w net.ipv4.ip_forward=1
iptables -t nat -A POSTROUTING -s 10.123.72.0/24 -o wlan0 -j MASQUERADE
ufw route allow in on br-cast out on wlan0
```

Se traduce en origen —y no se enruta a secas— porque el router de casa no sabe
devolver tráfico a `10.123.72.0/24`. La ruta por defecto ya la da el DHCP
(`option 3`), y se comprueba en el emulador con `ip route show table all`.

### «Reproduce pero no suena» no es un fallo de la app

PipeWire guarda el volumen **por aplicación**. Si el flujo de `qemu-system-x86_64`
quedó a cero alguna vez, se queda a cero en todos los arranques siguientes, y el
síntoma despista mucho: ExoPlayer decodifica —se ve el `c2.android.aac.decoder`
en el logcat—, la posición avanza y el volumen de Android está bien. Lo delata
`pactl list sink-inputs`, con `Volume: … 0% / -inf dB` y `Mute: no`.

`emulador-en-zerotier.sh comprobar` lo mira y lo corrige solo.

### Manejar el emulador sin su ventana de controles

En Niri/Wayland con XWayland satélite, la ventana de controles extendidos del
emulador no responde bien. No hace falta: `scripts/mando.sh` hace lo mismo por
adb, y de paso sirve igual contra un televisor real.

```bash
./scripts/mando.sh app            # trae cast-dlna-tv al frente
./scripts/mando.sh abajo abajo ok # el D-pad, encadenable
./scripts/mando.sh foto           # captura la pantalla
./scripts/mando.sh que-hay        # qué app está en primer plano
```

La captura es además la forma honesta de comprobar la interfaz: enseña qué se
está viendo de verdad, sin depender de que la ventana pinte bien.

### Cómo saber que funcionó

Las tres primeras las hace `emulador-en-zerotier.sh comprobar`; la cuarta es
manual porque hace falta el teléfono:

1. **El emulador cogió dirección de la red ZeroTier.** Si `eth0` sigue en
   `10.0.2.x`, el TAP no llegó al DHCP y lo demás no va a funcionar.
2. **Un M-SEARCH multicast desde el host recibe respuesta de la app** para
   `urn:schemas-upnp-org:device:MediaRenderer:1` — y con `SERVER: …
   cast-dlna-tv/1.0`, **no** con `Chromecast/…`. Ese detalle es el que distingue
   haber puenteado de estar hablando con DIAL. Se busca ese tipo y no
   `upnp:rootdevice` precisamente porque solo lo sirve nuestra app.
3. **El `LOCATION` anunciado es alcanzable.** `curl` al `device.xml` en la
   dirección del emulador. Descubrir y no poder leer el descriptor es el fallo
   típico de estos montajes: aparece en la lista del móvil y luego no reproduce.
4. **Un controlador del teléfono lo encuentra y reproduce.** Lo único que valida
   el camino entero.

### Antes de dar por bueno un fallo

Dos cosas que ya hicieron perder tiempo en esta sesión:

- **Comprobar qué app está en primer plano.** Toda la primera tanda de medidas se
  hizo con otra app encima; hubo que repetirla. Se mira con
  `adb shell dumpsys activity activities | grep ResumedActivity`.
- **No confundir "responde algo" con "responde la app".** El Chromecast de
  fábrica contesta a `upnp:rootdevice` y a `ssdp:all`. Buscar siempre un tipo que
  **solo** sirva cast-dlna-tv, y mirar la cabecera `SERVER`.
