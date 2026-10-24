// WEEM gömülü vektör altlığı (S39; MAP.md 6.19): Protomaps basemap stilinden (açık/koyu) sadeleştirilmiş, sprite'sız, tek yazı tipli
// stil şablonları + Noto Sans glifleri → map/src/main/assets/basemap/. Karolar (baku.pmtiles) CI'da ci/build_tiles.sh ile üretilir.
//
// Kullanım (paketler ayrı boş klasörde açılır, kurulum betiği çalışmaz):
//   d=$(mktemp -d) && (cd "$d" && npm pack @protomaps/basemaps@5.7.2 smp-noto-glyphs@2.0.0 && mkdir b g &&
//     tar xzf protomaps-basemaps-5.7.2.tgz -C b && tar xzf smp-noto-glyphs-2.0.0.tgz -C g)
//   node tools/basemap/make_style.cjs "$d/b/package" "$d/g/package"
// Çıktı repoya girer; karolar girmez (.gitignore).
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const [basemapsDir, glyphsDir] = process.argv.slice(2);
if (!basemapsDir || !glyphsDir) throw new Error('kullanım: make_style.cjs <@protomaps/basemaps klasörü> <smp-noto-glyphs klasörü>');
const basemaps = require(path.resolve(basemapsDir, 'dist/cjs/index.cjs'));

const OUT = path.resolve(__dirname, '../../map/src/main/assets/basemap');
const FONT = 'NotoSans';
// Azerbaycan Latin (ç ö ü / ğ ı ş İ Ə / ə), Kiril, noktalama ve ok (→), № gibi simgeler. Öteki aralıklar uygulamada boş glif dosyasıyla
// karşılanır (eksik aralık MapLibre'de karonun bütün yazılarını bekletir).
const RANGES = ['0-255', '256-511', '512-767', '1024-1279', '8192-8447', '8448-8703'];
// Uygulama kendi yer işaretlerini çizer; sprite gerektirenler ve Bakü'de anlamsız düşük yakınlık katmanları çıkar.
const DROP = new Set(['pois', 'roads_shields', 'places_country', 'places_region', 'boundaries_country', 'boundaries',
  'water_label_ocean', 'earth_label_islands', 'landcover']);
// Çok yazılı ad ifadeleri yerine tek ad (Bakü'de `name` Azerbaycanca): her karede daha az ifade değerlendirmesi.
const NAME = ['coalesce', ['get', 'name:az'], ['get', 'name']];
const NAMED = new Set(['roads_labels_minor', 'roads_labels_major', 'places_subplace', 'places_locality', 'water_waterway_label',
  'water_label_lakes']);

function simplify(layers, flavor) {
  const out = [];
  for (const l of layers) {
    if (DROP.has(l.id)) continue;
    const layer = JSON.parse(JSON.stringify(l));
    if (layer.type === 'symbol') {
      const lay = layer.layout || {};
      for (const k of ['icon-image', 'icon-size', 'icon-padding', 'icon-rotate', 'icon-rotation-alignment']) delete lay[k];
      if (lay['text-font'] !== undefined) lay['text-font'] = ['literal', [FONT]];
      if (NAMED.has(layer.id)) lay['text-field'] = NAME;
      if (layer.id === 'roads_oneway') {
        // Tek yön oku sprite yerine glif: çizgi yönünde "→".
        Object.assign(lay, {
          'text-field': '→', 'text-font': ['literal', [FONT]], 'text-size': 13, 'text-keep-upright': false,
          'text-rotation-alignment': 'map', 'symbol-spacing': 120,
        });
        layer.paint = { 'text-color': flavor === 'dark' ? '#8a8f98' : '#9a9590' };
      }
      layer.layout = lay;
    }
    out.push(layer);
  }
  return out;
}

function style(flavor) {
  return {
    version: 8,
    name: `WEEM ${flavor}`,
    sources: {
      protomaps: { type: 'vector', url: '{{TILES}}', attribution: '© Protomaps · © OpenStreetMap contributors' },
    },
    glyphs: '{{GLYPHS}}',
    layers: simplify(basemaps.layers('protomaps', basemaps.namedFlavor(flavor), { lang: 'az' }), flavor),
  };
}

fs.mkdirSync(path.join(OUT, 'glyphs', FONT), { recursive: true });
for (const flavor of ['light', 'dark']) {
  const s = style(flavor);
  const text = JSON.stringify(s);
  if (/sprite|icon-image/.test(text)) throw new Error('sprite kalmamalı');
  fs.writeFileSync(path.join(OUT, `style-${flavor}.json`), text + '\n');
  console.log(`style-${flavor}.json: ${s.layers.length} katman, ${text.length} bayt`);
}
for (const r of RANGES) {
  const raw = zlib.gunzipSync(fs.readFileSync(path.join(glyphsDir, 'fixtures', 'glyphs', `${r}.pbf.gz`)));
  fs.writeFileSync(path.join(OUT, 'glyphs', FONT, `${r}.pbf`), raw);
}
fs.writeFileSync(path.join(OUT, 'glyphs.json'),
  JSON.stringify({ font: FONT, ranges: RANGES, source: 'smp-noto-glyphs 2.0.0 (Noto Sans, SIL OFL 1.1)' }) + '\n');
console.log(`glifler: ${RANGES.join(' ')}`);
