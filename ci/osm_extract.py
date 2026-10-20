# WEEM: YolGo ci/osm_extract.py (kurye_nav yolgo-test @ 870ab6e) aynen; MAP.md 2.11. Çağıran: ci/build_map.sh.
# Geofabrik Azerbaycan özeti (.osm.pbf, her gün güncel) → Bakü yolları + yerler (Overpass JSON biçiminde).
# Overpass'tan çok daha hızlı ve güvenilir. Kullanım: osm_extract.py az.pbf roads.json pois.json
import json, sys
import osmium

pbf, out_roads, out_pois = sys.argv[1:4]
S, W, N, E = 40.280, 49.580, 40.650, 50.250  # Abşeron (logic/map geo.Baku.wideBox ile aynı)

HW = {'trunk', 'trunk_link', 'primary', 'primary_link', 'secondary', 'secondary_link', 'tertiary',
      'tertiary_link', 'unclassified', 'residential', 'living_street', 'service', 'road', 'track',
      'cycleway', 'path', 'footway', 'pedestrian', 'steps'}

def inside(lat, lon):
    return S <= lat <= N and W <= lon <= E

def poi_kind(t):
    a = t.get('amenity')
    if a in ('restaurant', 'fast_food', 'cafe'): return a
    if t.get('shop') == 'mall': return 'mall'
    if t.get('shop') in ('convenience', 'supermarket', 'bakery', 'butcher'): return 'shop'
    if t.get('station') == 'subway': return 'subway'
    if (t.get('highway') == 'traffic_signals' or t.get('crossing') == 'traffic_signals'
            or (t.get('highway') == 'crossing' and t.get('crossing:signals') == 'yes')): return 'signal'
    return None

class H(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.nodes, self.ways, self.pois = {}, [], []

    def node(self, n):
        if len(n.tags) == 0 or not n.location.valid() or not inside(n.location.lat, n.location.lon): return
        t = {x.k: x.v for x in n.tags}
        if poi_kind(t):
            self.pois.append({'type': 'node', 'id': n.id, 'lat': n.location.lat, 'lon': n.location.lon, 'tags': t})

    def way(self, w):
        park = w.tags.get('leisure') in ('park', 'garden') or w.tags.get('landuse') == 'recreation_ground'
        if 'highway' not in w.tags and 'amenity' not in w.tags and 'shop' not in w.tags and 'station' not in w.tags and not park:
            return
        t = {x.k: x.v for x in w.tags}
        try:
            locs = [(nd.ref, nd.location.lat, nd.location.lon) for nd in w.nodes]
        except osmium.InvalidLocationError:
            return
        if not locs or not any(inside(la, lo) for _, la, lo in locs): return
        hw = t.get('highway')
        # Ekim planı madde 10: kapalı park alanı (GraphBuilder.isParkAreaTags); içindeki yaya yolları park yolu olur.
        if park and hw is None and len(locs) >= 4 and locs[0][0] == locs[-1][0]:
            for ref, la, lo in locs:
                self.nodes[ref] = (la, lo)
            self.ways.append({'type': 'way', 'id': w.id, 'nodes': [r for r, _, _ in locs],
                              'tags': {k: v for k, v in t.items() if k in ('leisure', 'landuse')}})
        if hw in HW:
            ped_area = hw == 'pedestrian' and t.get('area') == 'yes'
            ok = ped_area or (t.get('area') != 'yes' and t.get('access') not in ('private', 'no')
                              and t.get('service') not in ('parking_aisle', 'driveway'))
            if ok and len(locs) >= 2:
                for ref, la, lo in locs:
                    self.nodes[ref] = (la, lo)
                self.ways.append({'type': 'way', 'id': w.id, 'nodes': [r for r, _, _ in locs], 'tags': t})
        if poi_kind(t) and t.get('highway') != 'traffic_signals':
            la = sum(x[1] for x in locs) / len(locs); lo = sum(x[2] for x in locs) / len(locs)
            self.pois.append({'type': 'way', 'id': w.id, 'center': {'lat': la, 'lon': lo}, 'tags': t})

h = H()
h.apply_file(pbf, locations=True)
nodes = [{'type': 'node', 'id': i, 'lat': round(la, 7), 'lon': round(lo, 7)} for i, (la, lo) in h.nodes.items()]
json.dump({'elements': nodes + h.ways}, open(out_roads, 'w'))
json.dump({'elements': h.pois}, open(out_pois, 'w'))
print(f'yollar {len(h.ways)} · düğüm {len(nodes)} · yer {len(h.pois)}')
