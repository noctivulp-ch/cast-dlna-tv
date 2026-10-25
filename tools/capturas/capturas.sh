#!/usr/bin/env bash
# Regenera las tres capturas del README (docs/capturas/*.png): la cara «a la
# espera», la cara «reproduciendo» con los mandos a la vista, y un corte
# diagonal entre las dos.
#
#   tools/capturas/capturas.sh [AVD]          # AVD de Android TV, por defecto «tv»
#
# Arranca el emulador sin ventana si no hay uno conectado, instala el APK de
# release, y para la segunda cara le envía por SOAP (con `adb forward`) un vídeo
# que genera ffmpeg y sirve el propio host. Necesita el SDK de Android con el
# AVD, ffmpeg y Python con Pillow y NumPy.
set -euo pipefail
cd "$(dirname "$0")/../.."

avd="${1:-tv}"
apk=app/build/outputs/apk/release/app-release.apk
paquete=app.castdlna.tv
puerto_video=8777
trabajo="$(mktemp -d)"
emulador=""
servidor=""
limpiar() {
  [ -n "$servidor" ] && kill "$servidor" 2>/dev/null
  adb forward --remove tcp:8201 2>/dev/null || true
  [ -n "$emulador" ] && adb emu kill >/dev/null 2>&1
  rm -rf "$trabajo"
}
trap limpiar EXIT
mkdir -p docs/capturas
[ -f "$apk" ] || ./gradlew assembleRelease

if ! adb get-state >/dev/null 2>&1; then
  "${ANDROID_HOME:-$HOME/Android/Sdk}/emulator/emulator" -avd "$avd" \
    -no-window -no-audio -no-boot-anim -no-snapshot-save >/dev/null 2>&1 &
  emulador=$!
  adb wait-for-device
  until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
fi

adb install -r "$apk" >/dev/null
adb shell am force-stop "$paquete"
adb shell monkey -p "$paquete" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1
sleep 6
adb exec-out screencap -p > "$trabajo/espera.png"

# Un fractal en movimiento: contenido propio, sin derechos de nadie.
ffmpeg -loglevel error -y -f lavfi -i "mandelbrot=size=1280x720:rate=30" -t 20 \
  -pix_fmt yuv420p -c:v libx264 -crf 28 -preset veryfast "$trabajo/demo.mp4"
python3 -m http.server "$puerto_video" --bind 0.0.0.0 --directory "$trabajo" >/dev/null 2>&1 &
servidor=$!
adb forward tcp:8201 tcp:8201 >/dev/null

soap() {  # acción cuerpo
  curl -sf -X POST http://127.0.0.1:8201/ctrl/avt -H 'Content-Type: text/xml; charset="utf-8"' \
    -H "SOAPACTION: \"urn:schemas-upnp-org:service:AVTransport:1#$1\"" \
    --data "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:$1 xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"><InstanceID>0</InstanceID>$2</u:$1></s:Body></s:Envelope>" >/dev/null
}
# 10.0.2.2 es el host visto desde dentro del emulador.
soap SetAVTransportURI "<CurrentURI>http://10.0.2.2:$puerto_video/demo.mp4</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>"
soap Play "<Speed>1</Speed>"
sleep 8
adb shell input keyevent KEYCODE_DPAD_CENTER   # los mandos aparecen al tocar el mando
sleep 1.5
adb exec-out screencap -p > docs/capturas/reproduciendo.png
soap Stop ""

cp "$trabajo/espera.png" docs/capturas/espera.png
python3 tools/capturas/corte_diagonal.py docs/capturas/espera.png docs/capturas/reproduciendo.png \
  docs/capturas/espera-reproduciendo.png
ls -1 docs/capturas
