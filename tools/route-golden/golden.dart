// WEEM A6.4 — YolGo rota motorunun karşılaştırma verisi (tools/route-golden/make_golden.sh kurar ve çalıştırır).
//
// Sabit tohumlu rastgele şehirler (ızgara sokaklar, yaya yolları, merdivenler, servis cepleri, park, tepe/çukur) ve her birinde
// rastgele kurallarla (onay, kara liste, aşırı eğim, hayalet kenar, öğrenilmiş kalite, ters yön oranı/yasağı, saatli izin, elle
// ters yön kesimi, elle bisiklet yolu, kötü nokta, kötüden kaçınma, mod, parametre) tek bacak ve plan sorguları. Çıktı: OSM girdisi,
// yükseklik tepeleri, KNG1 bayt özeti ve her sorgunun sonucu + A* sayaçları. Kotlin `route.RouterGoldenTest` grafı kendisi kurar,
// baytları ve sonuçları karşılaştırır.
import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:kurye_nav/core/geo.dart';
import 'package:kurye_nav/core/physics.dart';
import 'package:kurye_nav/data/graph_builder.dart';
import 'package:kurye_nav/data/overpass.dart';
import 'package:kurye_nav/data/road_graph.dart';
import 'package:kurye_nav/routing/planner.dart';
import 'package:kurye_nav/routing/quality.dart';
import 'package:kurye_nav/routing/route.dart';
import 'package:kurye_nav/routing/route_hours.dart';
import 'package:kurye_nav/routing/route_stats.dart';
import 'package:kurye_nav/routing/router.dart';
import 'package:kurye_nav/surface/surface_models.dart';

// Yerel metre ↔ derece: sabit çarpanlar (trigonometri yok; iki dilde bit bit aynı sonuç).
const lat0 = 40.40, lon0 = 49.85;
const kx = 84822.0, ky = 111320.0;
double latOf(double north) => lat0 + north / ky;
double lonOf(double east) => lon0 + east / kx;

/// Tepeler: [doğu m, kuzey m, yükseklik m (eksi: çukur), yarıçap m]; yalnız + − × ÷.
double elevation(List<List<double>> bumps, double la, double lo) {
  final x = (lo - lon0) * kx, y = (la - lat0) * ky;
  var e = 0.0;
  for (final b in bumps) {
    final dx = x - b[0], dy = y - b[1];
    e += b[2] / (1 + (dx * dx + dy * dy) / (b[3] * b[3]));
  }
  return e;
}

/// FNV-1a 64, onaltılık (Kotlin testinde aynısı).
String fnv(Uint8List bytes) {
  var h = 0xcbf29ce484222325;
  for (final b in bytes) {
    h ^= b;
    h *= 0x100000001b3;
  }
  return BigInt.from(h).toUnsigned(64).toRadixString(16);
}

const streetNames = [
  'Nizami küçəsi', 'Füzuli küçəsi', 'Azadlıq prospekti', '28 May', 'Bülbül prospekti', 'Rəşid Behbudov küçəsi',
  'Səməd Vurğun küçəsi', 'Xaqani küçəsi', 'Neftçilər prospekti', 'Üzeyir Hacıbəyov küçəsi',
];

T pick<T>(math.Random r, List<(int, T)> weighted) {
  final total = weighted.fold(0, (a, w) => a + w.$1);
  var x = r.nextInt(total);
  for (final (w, v) in weighted) {
    if (x < w) return v;
    x -= w;
  }
  return weighted.last.$2;
}

class City {
  City(this.r);
  final math.Random r;
  final raw = OsmRaw();
  final nodes = <List<Object>>[];
  final ways = <List<Object>>[];
  final east = <int, double>{}, north = <int, double>{};
  var _nid = 1, _wid = 1000;

  int node(double e, double n) {
    final id = _nid++;
    final la = latOf(n), lo = lonOf(e);
    raw.addNode(id, la, lo);
    nodes.add([id, la, lo]);
    east[id] = e;
    north[id] = n;
    return id;
  }

  void way(List<int> ns, Map<String, String> tags) {
    final id = _wid++;
    raw.addWay(OsmWay(id, ns, tags));
    ways.add([id, ns, tags]);
  }

  Map<String, String> streetTags() {
    final hw = pick(r, [
      (30, 'residential'), (10, 'tertiary'), (8, 'secondary'), (7, 'primary'), (3, 'trunk'), (8, 'unclassified'),
      (5, 'living_street'), (5, 'service'), (2, 'road'), (3, 'track'), (4, 'cycleway'), (9, 'footway'), (4, 'path'),
      (2, 'pedestrian'), (1, 'steps'), (1, 'primary_link'),
    ]);
    final t = <String, String>{'highway': hw};
    if (r.nextDouble() < 0.7) t['name'] = streetNames[r.nextInt(streetNames.length)];
    final o = r.nextDouble();
    if (o < 0.18) {
      t['oneway'] = 'yes';
    } else if (o < 0.23) {
      t['oneway'] = '-1';
    } else if (o < 0.25) {
      t['junction'] = 'roundabout';
    }
    if (r.nextDouble() < 0.05) t['oneway:bicycle'] = 'no';
    if (r.nextDouble() < 0.03) t['cycleway'] = 'opposite_lane';
    final s = r.nextDouble();
    if (s < 0.06) {
      t['surface'] = 'gravel';
    } else if (s < 0.09) {
      t['surface'] = 'unpaved';
    } else if (s < 0.13) {
      t['surface'] = 'sett';
    } else if (s < 0.15) {
      t['surface'] = 'cobblestone';
    }
    final b = r.nextDouble();
    if (b < 0.05) {
      t['bicycle'] = 'designated';
    } else if (b < 0.08) {
      t['bicycle'] = 'yes';
    } else if (b < 0.11) {
      t['bicycle'] = 'no';
    }
    if (r.nextDouble() < 0.04) t['foot'] = 'no';
    final a = r.nextDouble();
    if (a < 0.03) {
      t['access'] = 'private';
    } else if (a < 0.05) {
      t['access'] = 'destination';
    }
    if (hw == 'footway' && r.nextDouble() < 0.15) t['footway'] = r.nextBool() ? 'sidewalk' : 'crossing';
    final c = r.nextDouble();
    if (c < 0.05) {
      t['cycleway:right'] = 'lane';
    } else if (c < 0.08) {
      t['cycleway'] = 'track';
    } else if (c < 0.10) {
      t['cycleway:both'] = 'lane';
    }
    if (hw == 'service' && r.nextDouble() < 0.1) t['service'] = 'driveway';
    return t;
  }

  Map<String, String> footTags() {
    final t = <String, String>{
      'highway': pick(r, [(50, 'footway'), (20, 'path'), (10, 'pedestrian'), (8, 'steps'), (12, 'cycleway')]),
    };
    if (r.nextDouble() < 0.3) t['name'] = streetNames[r.nextInt(streetNames.length)];
    final b = r.nextDouble();
    if (b < 0.1) {
      t['bicycle'] = 'designated';
    } else if (b < 0.15) {
      t['bicycle'] = 'no';
    }
    if (r.nextDouble() < 0.05) t['foot'] = 'no';
    if (r.nextDouble() < 0.05) t['surface'] = 'gravel';
    return t;
  }

  /// Bir sıra düğüm boyunca rastgele uzunlukta yollar (boşluk, ara şekil noktası, ters çizim yönü).
  void streets(List<int> line) {
    var k = 0;
    while (k < line.length - 1) {
      if (r.nextDouble() < 0.07) {
        k++;
        continue;
      }
      final end = math.min(line.length - 1, k + 1 + r.nextInt(4));
      final ns = <int>[line[k]];
      for (var q = k + 1; q <= end; q++) {
        if (r.nextDouble() < 0.3) {
          final a = line[q - 1], b = line[q];
          ns.add(node((east[a]! + east[b]!) / 2 + (r.nextDouble() - 0.5) * 20, (north[a]! + north[b]!) / 2 + (r.nextDouble() - 0.5) * 20));
        }
        ns.add(line[q]);
      }
      way(r.nextDouble() < 0.5 ? ns : ns.reversed.toList(), streetTags());
      k = end;
    }
  }
}

Map<String, Object?> makeCity(int seed, int cols, int rows, double spacing, int legCases, int planCases) {
  final r = math.Random(seed);
  final c = City(r);
  final grid = <List<int>>[];
  for (var j = 0; j < rows; j++) {
    final row = <int>[];
    for (var i = 0; i < cols; i++) {
      final e = i * spacing + (r.nextDouble() - 0.5) * spacing * 0.3;
      final n = j * spacing + (r.nextDouble() - 0.5) * spacing * 0.3;
      row.add(c.node(e, n));
    }
    grid.add(row);
  }
  for (final row in grid) {
    c.streets(row);
  }
  for (var i = 0; i < cols; i++) {
    c.streets([for (var j = 0; j < rows; j++) grid[j][i]]);
  }
  // Blok içi çaprazlar (yaya yolu, patika, meydan, merdiven, bisiklet yolu).
  for (var j = 0; j < rows - 1; j++) {
    for (var i = 0; i < cols - 1; i++) {
      if (r.nextDouble() >= 0.12) continue;
      final a = grid[j][i], b = grid[j + 1][i + 1];
      final m = c.node((c.east[a]! + c.east[b]!) / 2 + (r.nextDouble() - 0.5) * 30, (c.north[a]! + c.north[b]!) / 2 + (r.nextDouble() - 0.5) * 30);
      c.way([a, m, b], c.footTags());
    }
  }
  // Çıkmaz kollar (servis cebi, ara sokak, yaya yolu, toprak yol).
  for (var k = 0; k < cols * rows ~/ 10; k++) {
    final a = grid[r.nextInt(rows)][r.nextInt(cols)];
    final b = c.node(c.east[a]! + (r.nextDouble() - 0.5) * 140, c.north[a]! + (r.nextDouble() - 0.5) * 140);
    c.way([a, b], {
      'highway': pick(r, [(4, 'service'), (3, 'residential'), (2, 'footway'), (1, 'track')]),
      if (r.nextDouble() < 0.3) 'name': streetNames[r.nextInt(streetNames.length)],
    });
  }
  // Park: içindeki yaya yolları park yolu (onaysız sürülür).
  if (r.nextDouble() < 0.8) {
    final i0 = r.nextInt(cols - 3), j0 = r.nextInt(rows - 3);
    final e0 = i0 * spacing - spacing * 0.4, e1 = (i0 + 3) * spacing + spacing * 0.4;
    final n0 = j0 * spacing - spacing * 0.4, n1 = (j0 + 3) * spacing + spacing * 0.4;
    final p = [c.node(e0, n0), c.node(e1, n0), c.node(e1, n1), c.node(e0, n1)];
    c.way([...p, p.first], pick(r, [(3, {'leisure': 'park'}), (1, {'leisure': 'garden'}), (1, {'landuse': 'recreation_ground'})]));
    for (var k = 0; k < 4; k++) {
      final a = grid[j0 + r.nextInt(4)][i0 + r.nextInt(4)], b = grid[j0 + r.nextInt(4)][i0 + r.nextInt(4)];
      if (a == b) continue;
      final m = c.node((c.east[a]! + c.east[b]!) / 2, (c.north[a]! + c.north[b]!) / 2);
      c.way([a, m, b], {'highway': pick(r, [(3, 'footway'), (1, 'path')]), if (r.nextDouble() < 0.2) 'bicycle': 'no'});
    }
  }
  // Yaya meydanı (alan): yalnız graf bölgesi.
  if (r.nextDouble() < 0.5) {
    final e = r.nextDouble() * (cols - 1) * spacing, n = r.nextDouble() * (rows - 1) * spacing;
    final p = [c.node(e, n), c.node(e + 60, n), c.node(e + 60, n + 60), c.node(e, n + 60)];
    c.way([...p, p.first], {'highway': 'pedestrian', 'area': 'yes'});
  }
  final w = (cols - 1) * spacing, h = (rows - 1) * spacing;
  final bumps = [
    for (var k = 0, nb = 3 + r.nextInt(4); k < nb; k++)
      [r.nextDouble() * w, r.nextDouble() * h, -20 + r.nextDouble() * 65, 80 + r.nextDouble() * 320],
  ];
  final g = buildRoadGraph(c.raw, (la, lo) => elevation(bumps, la, lo));
  final bytes = g.serialize();

  final cases = <Map<String, Object?>>[];
  for (var k = 0; k < legCases; k++) {
    cases.add(legCase(r, g, w, h));
  }
  for (var k = 0; k < planCases; k++) {
    cases.add(planCase(r, g, w, h));
  }
  return {
    'seed': seed,
    'nodes': c.nodes,
    'ways': c.ways,
    'bumps': bumps,
    'kng1': {'len': bytes.length, 'fnv': fnv(bytes), 'nodes': g.nodeCount, 'edges': g.edgeCount},
    'cases': cases,
  };
}

/// Eşit maliyet şehri: düğümler ikili kesirli enlem/boylamda (tam temsil), düz zemin, düzgün ızgara ve aynı düğümlerden geçen çift
/// yollar. Aynı satırdaki yatay ve aynı satır çiftindeki dikey kenarlar bit bit eşit uzunlukta: yığının eşit anahtar sırası,
/// komşuluk sırası ve "ilk en küçük" kuralları sonucu ve sayaçları belirler.
Map<String, Object?> makeTieCity(int seed, int n, int legCases, int planCases) {
  final r = math.Random(seed);
  final c = City(r);
  const step = 1 / 1024;
  final grid = [
    for (var j = 0; j < n; j++)
      [
        for (var i = 0; i < n; i++)
          () {
            final id = c._nid++;
            final la = 40.0 + j * step, lo = 50.0 + i * step;
            c.raw.addNode(id, la, lo);
            c.nodes.add([id, la, lo]);
            return id;
          }(),
      ],
  ];
  for (var j = 0; j < n; j++) {
    c.way(grid[j], {'highway': 'residential', if (j.isEven) 'name': streetNames[j % streetNames.length]});
  }
  for (var i = 0; i < n; i++) {
    c.way([for (var j = 0; j < n; j++) grid[j][i]], {'highway': r.nextDouble() < 0.3 ? 'tertiary' : 'residential'});
  }
  for (var k = 0; k < n; k++) {
    final j = r.nextInt(n), i = r.nextInt(n - 1);
    final ns = [grid[j][i], grid[j][i + 1]];
    c.way(r.nextBool() ? ns : ns.reversed.toList(), {'highway': pick(r, [(3, 'residential'), (1, 'footway'), (1, 'living_street')])});
  }
  final g = buildRoadGraph(c.raw, (la, lo) => 0.0);
  final bytes = g.serialize();
  GeoPoint nodeNear() {
    final v = r.nextInt(g.nodeCount);
    return r.nextDouble() < 0.7 ? GeoPoint(g.nodeLat[v], g.nodeLon[v]) : GeoPoint(g.nodeLat[v] + step / 4, g.nodeLon[v]);
  }
  final cases = <Map<String, Object?>>[];
  for (var k = 0; k < legCases; k++) {
    final o = r.nextDouble() < 0.5 ? plainOpts(r) : randomOpts(r, g);
    final from = nodeNear(), to = nodeNear(), probe = nodeNear();
    final input = {
      'kind': 'leg', 'opts': o, 'avoidBad': r.nextBool(), 'from': [from.lat, from.lon], 'to': [to.lat, to.lon],
      'heading': null, 'startOffsetS': 0.0, 'probe': [probe.lat, probe.lon],
    };
    cases.add(run(input, () {
      final leg = routeLeg(modelOf(g, o, avoidBad: input['avoidBad'] as bool), from, to);
      return {'leg': legJson(leg, probe)};
    }));
  }
  for (var k = 0; k < planCases; k++) {
    cases.add(planCase(r, g, 0, 0, at: nodeNear, plain: r.nextDouble() < 0.5));
  }
  return {
    'seed': seed,
    'nodes': c.nodes,
    'ways': c.ways,
    'bumps': <List<double>>[],
    'kng1': {'len': bytes.length, 'fnv': fnv(bytes), 'nodes': g.nodeCount, 'edges': g.edgeCount},
    'cases': cases,
  };
}

/// Kuralsız seçenekler (yalnız mod ve varsayılan parametreler): eşitlikler bozulmasın.
Map<String, Object?> plainOpts(math.Random r) => {
      'mode': RideMode.values[r.nextInt(2)].name,
      'params': {
        'flatWhPerM': 0.0135, 'climbWhPerM': 0.38, 'climbSafety': 1.0, 'flatKmh': 28.5, 'climbKmh': 18.5, 'walkKmh': 4.5,
        'maxGrade': 0.18, 'mainRoadPenalty': 1.15, 'wrongWayKmh': 18.0, 'walkRadius': 150.0, 'parkPenaltyS': 15.0,
        'qualityWeight': 0.10, 'turnPenaltyS': r.nextBool() ? 12.0 : 0.0, 'bikewayFactor': 0.85,
      },
      'approved': <int>[], 'blocked': <int>[], 'steep': <int>[], 'ghost': <int>[], 'learned': <List<Object>>[],
      'wwRatio': <List<Object>>[], 'wwAvoid': <int>[], 'reverseHours': <List<int>>[], 'wrongWayEdges': <int>[],
      'bikeways': <int>[], 'marks': <Map<String, Object?>>[],
      'departure': DateTime.utc(2026, 10, 1).millisecondsSinceEpoch,
    };

GeoPoint somewhere(math.Random r, RoadGraph g, double w, double h) {
  final x = r.nextDouble();
  if (x < 0.6) {
    final n = r.nextInt(g.nodeCount);
    return GeoPoint(g.nodeLat[n] + (r.nextDouble() - 0.5) * 60 / ky, g.nodeLon[n] + (r.nextDouble() - 0.5) * 60 / kx);
  }
  if (x < 0.95) return GeoPoint(latOf(r.nextDouble() * h), lonOf(r.nextDouble() * w));
  return GeoPoint(latOf(-500 + r.nextDouble() * (h + 1000)), lonOf(-500 + r.nextDouble() * (w + 1000)));
}

/// Rastgele kurallar; JSON'a girdi olarak yazılır, CostModel/PlanRequest bunlardan kurulur.
Map<String, Object?> randomOpts(math.Random r, RoadGraph g) {
  final ways = [for (var i = 0; i < g.wayOsmId.length; i++) g.wayOsmId[i]];
  List<int> someWays(double p) => [for (final w in ways) if (r.nextDouble() < p) w];
  List<int> someEdges(double p) => [for (var e = 0; e < g.edgeCount; e++) if (r.nextDouble() < p) e];
  final rich = r.nextDouble() < 0.7;
  final approved = rich ? someWays(0.12) : <int>[];
  final hours = <List<int>>[];
  if (rich && r.nextDouble() < 0.35) {
    for (final w in approved) {
      if (r.nextDouble() < 0.4) {
        final start = r.nextInt(1440);
        hours.add([w, start, r.nextDouble() < 0.1 ? start : r.nextInt(1440)]);
      }
    }
  }
  final learned = <List<Object>>[];
  if (rich) {
    for (final w in someWays(0.1)) {
      if (r.nextDouble() < 0.7) {
        learned.add([w, 0.0, 0.0, [10, 25, 30, 60, 90][r.nextInt(5)]]);
      } else {
        learned.add([w, r.nextDouble(), r.nextDouble() < 0.5 ? 0.8 + r.nextDouble() * 0.2 : r.nextDouble(), 0]);
      }
    }
  }
  final marks = <Map<String, Object?>>[];
  final nMarks = rich ? r.nextInt(7) : 0;
  final day0 = DateTime.utc(2026, 10, 1).millisecondsSinceEpoch;
  for (var k = 0; k < nMarks; k++) {
    final e = r.nextInt(g.edgeCount);
    final pts = g.geometry(e);
    final p = pts[r.nextInt(pts.length)];
    marks.add({
      'id': 'm$k',
      'way': g.osmIdOf(e),
      'lat': p.lat + (r.nextDouble() - 0.5) * 20 / ky,
      'lon': p.lon + (r.nextDouble() - 0.5) * 20 / kx,
      'at': day0 + r.nextInt(3) * 86400000 + r.nextInt(86400000),
      'label': SurfaceLabel.values[r.nextInt(SurfaceLabel.values.length)].name,
      // "kapalı" işaret Y13 ile (MAP.md 3.16 SORU 1): WEEM 1.0 motorunda yok, karşılaştırmaya girmez.
      'access': r.nextDouble() < 0.2 ? Passability.passable.name : Passability.unknown.name,
      'source': r.nextDouble() < 0.9 ? 'user' : 'auto',
      'removed': r.nextDouble() < 0.15,
    });
  }
  final wwRatio = <List<Object>>[];
  final wwAvoid = <int>[];
  for (final w in approved) {
    if (r.nextDouble() < 0.3) wwRatio.add([w, [0.0, 0.02, 0.1, 0.6, 1.0][r.nextInt(5)]]);
    if (r.nextDouble() < 0.15) wwAvoid.add(w);
  }
  return {
    'mode': RideMode.values[r.nextInt(2)].name,
    'params': {
      'flatWhPerM': 0.0135, 'climbWhPerM': 0.38, 'climbSafety': r.nextDouble() < 0.2 ? 1.46 : 1.0,
      'flatKmh': r.nextDouble() < 0.2 ? 24.0 : 28.5, 'climbKmh': 18.5, 'walkKmh': 4.5,
      'maxGrade': [0.18, 0.10, 0.05][r.nextInt(3)], 'mainRoadPenalty': r.nextDouble() < 0.2 ? 1.7 : 1.15,
      'wrongWayKmh': 18.0, 'walkRadius': [150.0, 60.0, 300.0][r.nextInt(3)], 'parkPenaltyS': 15.0,
      'qualityWeight': [0.10, 0.0, 0.30][r.nextInt(3)], 'turnPenaltyS': [12.0, 0.0, 30.0][r.nextInt(3)],
      'bikewayFactor': [0.85, 1.0, 0.6][r.nextInt(3)],
    },
    'approved': approved,
    'blocked': rich ? someWays(0.05) : <int>[],
    'steep': rich ? someWays(0.06) : <int>[],
    'ghost': rich ? someEdges(0.02) : <int>[],
    'learned': learned,
    'wwRatio': wwRatio,
    'wwAvoid': wwAvoid,
    'reverseHours': hours,
    'wrongWayEdges': rich ? someEdges(0.03) : <int>[],
    'bikeways': rich ? someWays(0.03) : <int>[],
    'marks': marks,
    'departure': day0 + r.nextInt(30) * 86400000 + r.nextInt(86400000),
  };
}

RideParams paramsOf(Map<String, Object?> j) => RideParams(
      flatWhPerM: j['flatWhPerM'] as double, climbWhPerM: j['climbWhPerM'] as double, climbSafety: j['climbSafety'] as double,
      flatKmh: j['flatKmh'] as double, climbKmh: j['climbKmh'] as double, walkKmh: j['walkKmh'] as double,
      maxGrade: j['maxGrade'] as double, mainRoadPenalty: j['mainRoadPenalty'] as double, wrongWayKmh: j['wrongWayKmh'] as double,
      walkRadius: j['walkRadius'] as double, parkPenaltyS: j['parkPenaltyS'] as double, qualityWeight: j['qualityWeight'] as double,
      turnPenaltyS: j['turnPenaltyS'] as double, bikewayFactor: j['bikewayFactor'] as double,
    );

List<SurfaceMark> marksOf(List<Map<String, Object?>> j) => [for (final m in j) SurfaceMark.fromJson(m)];
Map<int, LearnedQuality> learnedOf(List<List<Object>> j) =>
    {for (final l in j) l[0] as int: (q: l[1] as double, c: l[2] as double, r: l[3] as int)};
Map<int, ReverseHours> hoursOf(List<List<int>> j) => {for (final h in j) h[0]: ReverseHours(h[1], h[2])};
Map<int, double> ratioOf(List<List<Object>> j) => {for (final x in j) x[0] as int: x[1] as double};

CostModel modelOf(RoadGraph g, Map<String, Object?> o, {required bool avoidBad}) => CostModel(
      g,
      mode: RideMode.values.byName(o['mode'] as String),
      params: paramsOf(o['params'] as Map<String, Object?>),
      approved: (o['approved'] as List<int>).toSet(),
      blocked: (o['blocked'] as List<int>).toSet(),
      steep: (o['steep'] as List<int>).toSet(),
      avoidBad: avoidBad,
      ghost: (o['ghost'] as List<int>).toSet(),
      learned: learnedOf(o['learned'] as List<List<Object>>),
      marks: marksOf(o['marks'] as List<Map<String, Object?>>),
      wwRatio: ratioOf(o['wwRatio'] as List<List<Object>>),
      wwAvoid: (o['wwAvoid'] as List<int>).toSet(),
      reverseHours: hoursOf(o['reverseHours'] as List<List<int>>),
      wrongWayEdges: (o['wrongWayEdges'] as List<int>).toSet(),
      bikeways: (o['bikeways'] as List<int>).toSet(),
      departure: DateTime.fromMillisecondsSinceEpoch(o['departure'] as int, isUtc: true),
    );

List<Object> line(List<RoutePoint> pts) {
  var la = 0.0, lo = 0.0, el = 0.0;
  for (final p in pts) {
    la += p.lat;
    lo += p.lon;
    el += p.ele;
  }
  return [pts.length, la, lo, el];
}

Map<String, Object?> legJson(RouteLeg l, GeoPoint probe) => {
      'rideM': l.rideM, 'walkM': l.walkM, 'timeS': l.timeS, 'energyWh': l.energyWh, 'climbM': l.climbM,
      'descentM': l.descentM, 'wrongWayM': l.wrongWayM, 'cost': l.cost, 'quality': l.quality, 'wrongWayRisk': l.wrongWayRisk,
      'pushM': l.pushM, 'startWalkM': l.startWalkM, 'streets': l.streets, 'warnings': l.warnings,
      'segs': [for (final s in l.segs) [s.edge, s.startM, s.lenM, s.walk ? 1 : 0]],
      'turns': [for (final t in l.turns) [t.atM, t.angle, t.name, t.lat, t.lon, t.sharpness]],
      'ride': line(l.ride), 'walk': line(l.walk), 'startWalk': line(l.startWalk),
      'park': [l.parkPoint.lat, l.parkPoint.lon],
      'pushedRuns': [for (final run in pushedRuns(l)) line(run)],
      'progress': progressAlong(l.ride, probe),
    };

Map<String, Object?> run(Map<String, Object?> input, Map<String, Object?> Function() body) {
  RouteStats.reset();
  Map<String, Object?> out;
  try {
    out = body();
  } on RoutingException catch (e) {
    out = {'error': e.message};
  } catch (e) {
    out = {'crash': '${e.runtimeType}'};
  }
  return {...input, 'expect': {...out, 'stats': [RouteStats.legs, RouteStats.pops, RouteStats.edges]}};
}

Map<String, Object?> legCase(math.Random r, RoadGraph g, double w, double h) {
  final o = randomOpts(r, g);
  final from = somewhere(r, g, w, h), to = somewhere(r, g, w, h), probe = somewhere(r, g, w, h);
  final heading = r.nextDouble() < 0.4 ? r.nextDouble() * 360 : null;
  final avoidBad = r.nextBool();
  final offset = (o['reverseHours'] as List).isNotEmpty && r.nextBool() ? r.nextDouble() * 7200 : 0.0;
  final input = {
    'kind': 'leg', 'opts': o, 'avoidBad': avoidBad, 'from': [from.lat, from.lon], 'to': [to.lat, to.lon],
    'heading': heading, 'startOffsetS': offset, 'probe': [probe.lat, probe.lon],
  };
  return run(input, () {
    final leg = routeLeg(modelOf(g, o, avoidBad: avoidBad), from, to, heading: heading, startOffsetS: offset);
    return {'leg': legJson(leg, probe)};
  });
}

Map<String, Object?> planCase(math.Random r, RoadGraph g, double w, double h, {GeoPoint Function()? at, bool plain = false}) {
  final o = plain ? plainOpts(r) : randomOpts(r, g);
  GeoPoint pt() => at != null ? at() : somewhere(r, g, w, h);
  final origin = pt(), probe = pt();
  final stops = [for (var k = 0, n = 1 + r.nextInt(5); k < n; k++) pt()];
  final heading = at == null && r.nextDouble() < 0.4 ? r.nextDouble() * 360 : null;
  final fixed = r.nextDouble() < 0.2;
  final input = {
    'kind': 'plan', 'opts': o, 'origin': [origin.lat, origin.lon], 'stops': [for (final s in stops) [s.lat, s.lon]],
    'heading': heading, 'fixedOrder': fixed, 'probe': [probe.lat, probe.lon],
  };
  return run(input, () {
    final req = PlanRequest(
      graph: g,
      origin: origin,
      stops: stops,
      params: paramsOf(o['params'] as Map<String, Object?>),
      approved: (o['approved'] as List<int>).toSet(),
      blocked: (o['blocked'] as List<int>).toSet(),
      steep: (o['steep'] as List<int>).toSet(),
      heading: heading,
      fixedOrder: fixed,
      ghost: (o['ghost'] as List<int>).toSet(),
      learned: learnedOf(o['learned'] as List<List<Object>>),
      marks: marksOf(o['marks'] as List<Map<String, Object?>>),
      wwRatio: ratioOf(o['wwRatio'] as List<List<Object>>),
      wwAvoid: (o['wwAvoid'] as List<int>).toSet(),
      reverseHours: hoursOf(o['reverseHours'] as List<List<int>>),
      wrongWayEdges: (o['wrongWayEdges'] as List<int>).toSet(),
      bikeways: (o['bikeways'] as List<int>).toSet(),
      departure: DateTime.fromMillisecondsSinceEpoch(o['departure'] as int, isUtc: true),
    );
    final res = computePlans(req);
    return {
      'error': res.error,
      'plans': {
        for (final e in res.plans.entries)
          e.key.name: {'order': e.value.order, 'legs': [for (final l in e.value.legs) legJson(l, probe)]},
      },
    };
  });
}

void main(List<String> args) {
  final cities = [
    makeCity(11, 14, 14, 110, 150, 40),
    makeCity(23, 18, 11, 90, 150, 40),
    makeCity(37, 10, 10, 150, 120, 30),
    makeTieCity(41, 7, 80, 25),
  ];
  File(args.single).writeAsStringSync(jsonEncode({
    'source': 'YolGo yolgo-test@870ab6e; tools/route-golden/golden.dart',
    'cities': cities,
  }));
}
