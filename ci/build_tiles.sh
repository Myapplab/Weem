#!/usr/bin/env bash
# Gömülü vektör altlık (S39; MAP.md 6.19): Protomaps günlük gezegen yapısından (build.protomaps.com, son 7 gün tutulur) Geniş Bakü kesiti
# → map/src/main/assets/basemap/baku.pmtiles + tiles.json. go-pmtiles yalnız gereken karoları HTTP aralık isteğiyle indirir (gezegen inmez).
# Stil şablonları ve glifler repoda (tools/basemap/make_style.cjs). Yalnız apk.yml'de; üretilemezse çıkış 1 (APK işi kırmızı; build_map.sh gibi).
# Yerel deneme: PMTILES_SRC=<yerel ya da uzak .pmtiles> PMTILES_BIN=<go-pmtiles> ci/build_tiles.sh
set -uo pipefail
cd "$(dirname "$0")/.."
OUT=map/src/main/assets/basemap
# Baku.wideBox (logic/map Geo.kt): batı,güney,doğu,kuzey.
BBOX=49.58,40.28,50.25,40.65
# z15 Protomaps'in en yüksek yakınlığı (bütün ayrıntı); MapLibre 15–19 arasını büyüterek çizer. Dosya bu sınırı aşarsa z14'e inilir.
MAX_MB=${TILES_MAX_MB:-70}
BIN=${PMTILES_BIN:-}
if [ -z "$BIN" ]; then
  GOBIN=/tmp/pmtiles-bin go install github.com/protomaps/go-pmtiles@v1.31.2 || { echo "::error title=Gömülü altlık::go-pmtiles kurulamadı"; exit 1; }
  BIN=/tmp/pmtiles-bin/go-pmtiles
fi
SRC=${PMTILES_SRC:-}
VERSION=local
if [ -z "$SRC" ]; then
  for i in 0 1 2 3 4 5 6 7 8 9; do
    d=$(date -u -d "$i days ago" +%Y%m%d)
    if curl -sfI --max-time 20 "https://build.protomaps.com/$d.pmtiles" > /dev/null; then SRC="https://build.protomaps.com/$d.pmtiles"; VERSION=$d; break; fi
  done
  [ -n "$SRC" ] || { echo "::error title=Gömülü altlık::build.protomaps.com'da son 10 günün yapısı yok"; exit 1; }
fi
echo "kaynak: $SRC"
mkdir -p "$OUT"
TMP=$(mktemp -d)
for z in 15 14; do
  rm -f "$TMP/baku.pmtiles"
  if ! "$BIN" extract "$SRC" "$TMP/baku.pmtiles" --bbox=$BBOX --maxzoom=$z --download-threads=8; then
    echo "::error title=Gömülü altlık::kesit alınamadı (z$z)"; exit 1
  fi
  bytes=$(stat -c %s "$TMP/baku.pmtiles")
  echo "z0–$z: $((bytes / 1048576)) MB"
  [ "$bytes" -le $((MAX_MB * 1048576)) ] && break
  [ "$z" = 14 ] || echo "::warning title=Gömülü altlık::z$z kesiti $MAX_MB MB'ı aşıyor, z14 deneniyor"
done
"$BIN" show "$TMP/baku.pmtiles" | head -20
mv "$TMP/baku.pmtiles" "$OUT/baku.pmtiles"
printf '{"version":"%s","maxzoom":%s,"bytes":%s,"bbox":"%s"}\n' "$VERSION" "$z" "$bytes" "$BBOX" > "$OUT/tiles.json"
echo "::notice title=Gömülü altlık::$VERSION · z0–$z · $((bytes / 1048576)) MB"
