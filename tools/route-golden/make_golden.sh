#!/usr/bin/env bash
# YolGo rota motoru (yolgo-test @ 870ab6e) ile WEEM rota motorunun birebir karşılaştırma verisini üretir (A6.4, MAP.md 3.13–3.16).
#
#   tools/route-golden/make_golden.sh <yolgo-checkout> <dart> [çıktı.json.gz]
#
# <yolgo-checkout>: `git clone --depth 1 -b yolgo-test https://github.com/Myapplab/kurye_nav /tmp/ref-yolgo` (salt okunur; yazılmaz).
# <dart>: Dart SDK'nın `dart` aracı (Flutter gerekmez; ör. storage.googleapis.com/dart-archive … dartsdk-linux-x64-release.zip).
# Çıktı varsayılanı: logic/map/src/test/resources/route/golden-v1.json.gz (Kotlin `route.RouterGoldenTest` okur).
#
# Kum havuzu geçici klasörde: YolGo'nun saf Dart rota dosyaları kopyalanır; Flutter'a bağlı `i18n.dart` ve ağa bağlı `overpass.dart`
# yerine yalnız gereken parçalar (tl, OsmRaw/OsmWay) yazılır; `router.dart`'a üç sayaç satırı eklenir (bacak, yığın çekişi,
# gevşetilen kenar) — hesap değişmez. Rastgelelik sabit tohumlu; aynı SDK ile aynı çıktı.
set -euo pipefail

src=${1:?YolGo checkout}
dart=${2:?dart}
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../.." && pwd)
out=${3:-$repo/logic/map/src/test/resources/route/golden-v1.json.gz}

[ "$(git -C "$src" rev-parse --short HEAD 2>/dev/null || true)" = "870ab6e" ] || echo "uyarı: YolGo checkout 870ab6e değil" >&2

box=$(mktemp -d)
trap 'rm -rf "$box"' EXIT
mkdir -p "$box/lib/core" "$box/lib/data" "$box/lib/routing" "$box/lib/surface" "$box/lib/calibration" "$box/bin"

for f in core/geo.dart core/physics.dart core/day_scope.dart data/road_graph.dart data/graph_builder.dart \
    routing/router.dart routing/planner.dart routing/route.dart routing/quality.dart routing/route_hours.dart \
    surface/bad_spots.dart surface/surface_models.dart calibration/speed_band.dart; do
    cp "$src/lib/$f" "$box/lib/$f"
done

cat > "$box/pubspec.yaml" <<'EOF'
name: kurye_nav
publish_to: none
environment:
  sdk: ^3.8.0
EOF

# Flutter'sız dil yardımcıları (physics.dart yalnız tl kullanır).
cat > "$box/lib/core/i18n.dart" <<'EOF'
enum Lang { tr, az }

abstract final class I18n {
  static Lang lang = Lang.tr;
  static bool get az => lang == Lang.az;
}

String tl(String tr, String az) => I18n.az ? az : tr;
String trMsg(String s) => s;
EOF

# overpass.dart: yalnız OsmRaw ve OsmWay (http ve app_info bağımlılıkları olmadan).
awk '/^String overpassQuery/{exit} {print}' "$src/lib/data/overpass.dart" \
    | grep -v "package:http/http.dart" | grep -v "package:kurye_nav/core/app_info.dart" > "$box/lib/data/overpass.dart"

cat > "$box/lib/routing/route_stats.dart" <<'EOF'
/// Karşılaştırma sayaçları (yalnız bu araç; YolGo'da yok).
abstract final class RouteStats {
  static int legs = 0, pops = 0, edges = 0;
  static void reset() => legs = pops = edges = 0;
}
EOF

python3 -I - "$box/lib/routing/router.dart" <<'EOF'
import sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
patches = [
    ("import 'package:kurye_nav/routing/route_hours.dart';\n",
     "import 'package:kurye_nav/routing/route_hours.dart';\nimport 'package:kurye_nav/routing/route_stats.dart';\n"),
    ("    {double? heading, double startOffsetS = 0, required EdgeHit targetHit}) {\n",
     "    {double? heading, double startOffsetS = 0, required EdgeHit targetHit}) {\n  RouteStats.legs++;\n"),
    ("    final u = heap.pop();\n    if (u == target) break;\n",
     "    final u = heap.pop();\n    RouteStats.pops++;\n    if (u == target) break;\n"),
    ("      final c = m.segmentCost(e, dir, 0, 1);\n",
     "      RouteStats.edges++;\n      final c = m.segmentCost(e, dir, 0, 1);\n"),
]
for old, new in patches:
    if s.count(old) != 1:
        sys.exit("router.dart yaması uymadı: " + old.strip())
    s = s.replace(old, new)
open(p, 'w', encoding='utf-8').write(s)
EOF

cp "$here/golden.dart" "$box/bin/golden.dart"
(cd "$box" && "$dart" pub get --offline >/dev/null && "$dart" run bin/golden.dart "$box/golden.json")
mkdir -p "$(dirname "$out")"
gzip -9 -n -c "$box/golden.json" > "$out"
echo "yazıldı: $out ($(wc -c < "$out") bayt)"
