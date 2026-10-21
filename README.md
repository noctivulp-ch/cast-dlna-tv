<div align="center">

# cast-dlna-tv

**Receptor DLNA/UPnP para Android TV, pensado para manejarse con el mando.**

![Licencia](https://img.shields.io/badge/licencia-GPL--3.0--or--later-FF6B00?style=flat-square&labelColor=1A1A1A)
![Plataforma](https://img.shields.io/badge/plataforma-Android%20TV-3A3A3A?style=flat-square&labelColor=1A1A1A)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-3A3A3A?style=flat-square&logo=kotlin&labelColor=1A1A1A)

</div>

---

## Qué es

cast-dlna-tv lleva al televisor el receptor de escritorio [cast-dlna](#tecnologías): aparece en
la lista de destinos del teléfono y reproduce lo que se le envía. Hereda el protocolo UPnP y lo
aprendido con controladores reales, y añade lo propio de la televisión: una sola pantalla, con
una cara «a la espera» que muestra el nombre y la dirección del receptor, y una cara
«reproduciendo» con mandos que aparecen al tocar el mando y se retiran solos.

## Funciones principales

- `MediaRenderer:1` (DMR-1.50): reproducción, pausa, búsqueda y volumen.
- Interfaz diseñada para el mando a distancia.
- Reproducción con Media3 ExoPlayer, incluido HLS.

## Tecnologías

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin |
| Interfaz | Jetpack Compose, con Wulfenite UI (sistema de diseño de NoctiVulp) |
| Reproductor | AndroidX Media3 (ExoPlayer, HLS, OkHttp) |
| Protocolos | UPnP AV propio, portado del receptor de escritorio cast-dlna |

## Puesta en marcha

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Estado

Completo a falta de probarlo en una red real: el descubrimiento SSDP y el envío de pantalla
desde un emisor real están pendientes de validar. Detalle en [`docs/guia.md`](docs/guia.md).

## Licencia

Código bajo **GPL-3.0-or-later** ([`LICENSE`](LICENSE)). Los componentes de terceros y sus licencias
están en [`NOTICE`](NOTICE). El nombre y la identidad visual no forman parte de la licencia: ver
[`TRADEMARKS.md`](TRADEMARKS.md). Para aportar, [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Apoyo

Este proyecto forma parte del trabajo de **NoctiVulp**: herramientas para Linux y Android,
publicadas como software libre. Las compilaciones oficiales y las novedades salen en Patreon.

<p align="center">
  <a href="https://www.patreon.com/cw/NoctiVulp"><img src="https://img.shields.io/badge/Patreon-NoctiVulp-FF6B00?style=for-the-badge&logo=patreon&logoColor=white&labelColor=1A1A1A" alt="NoctiVulp en Patreon"></a>
</p>

---

<p align="center"><sub>© 2026 NoctiVulp · <b>Powered by AI</b> — desarrollado con la asistencia de Claude, de Anthropic.</sub></p>
