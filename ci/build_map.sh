#!/usr/bin/env bash
# Gömülü harita (MAP.md 2.11; YolGo ci/build.sh 202–231): Geofabrik Azerbaycan özeti → pyosmium Bakü kesiti → logic/map MapBuild
# → map/src/main/assets/map (graf, SRTM, yerler, manifest). Geofabrik olmazsa Overpass'a düşülür. Üretilemezse çıkış 1 (APK işi kırmızı).
# Yalnız apk.yml'de (weem-v* etiketi / elle); PR CI'ında değil (Actions dakikası).
set -uo pipefail
cd "$(dirname "$0")/.."
OUT=map/src/main/assets/map
LOG=$(mktemp)
SRC=""
if curl -fsSL --retry 3 -o /tmp/az.osm.pbf https://download.geofabrik.de/asia/azerbaijan-latest.osm.pbf >> "$LOG" 2>&1 \
   && echo "pbf: $(du -m /tmp/az.osm.pbf | cut -f1) MB" >> "$LOG" \
   && { python3 -c 'import osmium' 2>/dev/null \
        || pip install -q osmium >> "$LOG" 2>&1 \
        || pip install -q --break-system-packages osmium >> "$LOG" 2>&1; } \
   && python3 ci/osm_extract.py /tmp/az.osm.pbf /tmp/roads.json /tmp/pois.json >> "$LOG" 2>&1; then
  SRC="/tmp/roads.json /tmp/pois.json"
  echo "OSM özeti: $(tail -n 1 "$LOG")"
else
  echo "::warning title=Geofabrik::$(tail -n 5 "$LOG" | tr '\n' ' ') — Overpass'a düşülüyor"
fi
# shellcheck disable=SC2086
if timeout 1500 ./gradlew -q -p logic :map:buildMap --console=plain --args="$(pwd)/$OUT $SRC"; then
  ver=$(python3 -c "import json;print(json.load(open('$OUT/manifest.json'))['version'])")
  echo "::notice title=Gömülü harita::$ver · $(du -sm "$OUT" | cut -f1) MB"
else
  echo "::error title=Gömülü harita::üretilemedi"
  exit 1
fi
