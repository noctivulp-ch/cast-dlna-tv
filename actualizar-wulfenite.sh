#!/usr/bin/env bash
# Rehace el AAR de Wulfenite y lo trae aquí.
#
# La librería se consume como artefacto y no como módulo porque usa su propio
# catálogo de versiones, que no se puede compartir sin tocar su configuración
# —y la librería la lleva otra línea de trabajo: lo que falte se avisa, no se
# arregla desde aquí—.
set -euo pipefail
LIB="${1:-../../#Libs/wulfenite_ui/Compose}"
cd "$(dirname "$0")"
(cd "$LIB" && ./gradlew :wulf-compose:assembleRelease --console=plain -q)
cp "$LIB/wulf-compose/build/outputs/aar/wulf-compose-release.aar" app/libs/
echo "wulf-compose actualizado en app/libs/"
