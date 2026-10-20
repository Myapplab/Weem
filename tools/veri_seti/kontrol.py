#!/usr/bin/env python3
"""WEEM günlük ZIP denetimi. Yalnız standart kütüphane; ağ/harici komut/OCR yok.

Kullanım: python3 -I tools/veri_seti/kontrol.py WEEM_teklif_2026-10-10_Cumartesi.zip
Öz-test: python3 -I tools/veri_seti/kontrol.py --self-test
Çıkış: 0 şema/hash geçerli, 1 hatalı paket, 2 okunamayan ZIP.
"""
import argparse
import csv
import hashlib
import io
import json
import math
from collections import Counter
from datetime import date, datetime
from pathlib import Path, PurePosixPath
import re
import tempfile
import unittest
import zipfile

SOURCES = {'kuryeplus', 'surus_paneli', 'yolgo', 'manual'}
COMMON = ('wall_ms', 'mono_ns', 'schema_version', 'source', 'scooter_id')
OFFER_FIELDS = ('feeAzn', 'platformKm', 'pickupAirKm', 'deliveryAirKm', 'routeKm',
                'routeSeconds', 'demGainM', 'demLossM', 'temperatureC', 'windKmh', 'rainMm')
WEEKDAYS = ('Pazartesi', 'Salı', 'Çarşamba', 'Perşembe', 'Cuma', 'Cumartesi', 'Pazar')
PRIVATE_KEYS = {'customername', 'customer_name', 'customeraddress', 'customer_address',
                'dropoffaddress', 'phone', 'telephone', 'doorcode', 'door_code', 'customernote'}
PHONE = re.compile(r'(?<!\d)(?:\+994|00994)[ ()-]*\d{2}[ ()-]*\d{3}[ -]*\d{2}[ -]*\d{2}(?!\d)')


def number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def match_offers(raw, live):
    """Anahtar öncelikli, sonra tekil platform + ilk görülme ±5 sn + ücret ±0,01 AZN."""
    used_raw, used_live, pairs = set(), set(), []
    for key in sorted(raw.keys() & live.keys()):
        used_raw.add(key); used_live.add(key); pairs.append((key, key, 'offerKey'))
    candidates = {}
    for ykey, y in live.items():
        if ykey in used_live:
            continue
        hits = []
        for rkey, r in raw.items():
            if rkey in used_raw or r.get('platform') != y.get('platform'):
                continue
            rt, yt, rf, yf = (r.get('firstSeenAtMs'), y.get('firstSeenAtMs'), r.get('feeAzn'), y.get('feeAzn'))
            if all(number(x) for x in (rt, yt, rf, yf)) and abs(rt - yt) <= 5000 and abs(rf - yf) <= .010001:
                hits.append(rkey)
        candidates[ykey] = hits
    for ykey, hits in candidates.items():
        if len(hits) == 1 and sum(hits[0] in xs for xs in candidates.values()) == 1:
            used_raw.add(hits[0]); used_live.add(ykey); pairs.append((hits[0], ykey, 'platform_time_fee'))
    denominator = len(raw) + len(live) - len(pairs)
    return {'matched': len(pairs), 'onlyRawstats': len(raw) - len(used_raw),
            'onlyYolgo': len(live) - len(used_live), 'ratio': len(pairs) / denominator if denominator else None,
            'ambiguousFallback': sum(len(xs) > 1 or (len(xs) == 1 and sum(xs[0] in ys for ys in candidates.values()) > 1) for xs in candidates.values()),
            'pairs': pairs}


def scalar_fields(value, prefix=''):
    if isinstance(value, dict):
        for key, child in value.items():
            if key in {'field_sources', 'source_flags'} or key.endswith('_source') or key.endswith('_sources'):
                continue
            yield from scalar_fields(child, f'{prefix}.{key}' if prefix else key)
    elif isinstance(value, list):
        for i, child in enumerate(value):
            yield from scalar_fields(child, f'{prefix}[{i}]')
    else:
        yield prefix, value


class Report:
    def __init__(self):
        self.files = {}; self.errors = Counter(); self.warnings = Counter()
        self.raw = {}; self.live = {}; self.routes = {}; self.scooter_times = []
        self.scooter_previous = {}; self.scooter_gaps = 0; self.manifest = {}
        self.matching = {}; self.route_ratio = None; self.coverage_ratio = None

    def row(self, row, name, stats):
        if not isinstance(row, dict):
            self.errors['Kayıt JSON nesnesi değil'] += 1; stats['schema'] += 1; return
        bad = False
        for key in COMMON:
            if key not in row:
                self.errors[f'Ortak alan eksik: {key}'] += 1; bad = True
        version = row.get('schema_version')
        expected = 3 if name.startswith('rawstats/') else 1
        if version != expected:
            self.errors['schema_version geçersiz'] += 1; bad = True
        if row.get('source') not in SOURCES:
            self.errors['source geçersiz'] += 1; bad = True
        if row.get('power_verified') is not False:
            self.errors['power_verified false değil'] += 1; bad = True
        for key in ('wall_ms', 'mono_ns'):
            value = row.get(key)
            if value is None:
                if row.get(key + '_source') != 'unavailable':
                    self.errors[f'{key} null kaynak bayrağı eksik'] += 1; bad = True
                self.warnings[f'Eksik saat: {key}'] += 1
            elif not isinstance(value, int) or isinstance(value, bool) or value < 0:
                self.errors[f'{key} geçersiz'] += 1; bad = True
        if row.get('scooter_id') is None and row.get('scooter_id_source') not in {'unavailable', 'unavailable_or_multiple'}:
            self.errors['scooter_id null kaynak bayrağı eksik'] += 1; bad = True
        if name == 'yolgo-live.jsonl' or name == 'rawstats/economics-offers.jsonl':
            key = row.get('offerKey')
            if not isinstance(key, str) or not re.fullmatch(r'offer-\d+-\d+', key) or row.get('platform') not in {'wolt', 'bolt'}:
                self.errors['Teklif kimliği/platform geçersiz'] += 1; bad = True
            else:
                (self.live if name == 'yolgo-live.jsonl' else self.raw)[key] = row
            first, last = row.get('firstSeenAtMs'), row.get('lastSeenAtMs')
            if not all(number(x) for x in (first, last)) or first > last:
                self.errors['İlk/son görülme zamanı tutarsız'] += 1; bad = True
            elif last - first > 7200000:
                self.warnings['İki saatten uzun görülen teklif'] += 1
        if name == 'yolgo-routes.jsonl':
            self.routes[row.get('recordId')] = row
        for key in ('feeAzn', 'platformKm', 'routeKm', 'routeSeconds', 'pickupAirKm', 'deliveryAirKm', 'demGainM', 'demLossM', 'amountAzn'):
            value = row.get(key)
            if value is not None and (not number(value) or value < 0):
                self.errors['Negatif/geçersiz ücret, km veya süre'] += 1; bad = True
            if number(value) and value > (100 if key.endswith('Km') else 10000 if key == 'feeAzn' else 1000000):
                self.warnings['Şüpheli yüksek ücret, km veya süre'] += 1
        km, seconds = row.get('routeKm'), row.get('routeSeconds')
        if number(km) and number(seconds) and km > 0 and (seconds <= 0 or km * 3600 / seconds > 120):
            self.warnings['Şüpheli rota hızı (>120 km/saat) veya sıfır süre'] += 1
        shown = row.get('shown', {})
        if isinstance(shown, dict) and all(number(shown.get(k)) for k in ('minLo', 'minHi')) and shown['minLo'] > shown['minHi']:
            self.errors['Platform süre aralığı ters'] += 1; bad = True
        for path, value in scalar_fields(row):
            stats['fields'] += 1; stats['empty'] += value is None
            leaf = re.split(r'[.\[]', path)[-1].rstrip(']')
            if leaf.lower() in PRIVATE_KEYS and value not in (None, '[note]', '[tel]', '[code]'):
                self.errors['Müşteri alanı veri setinde'] += 1; bad = True
            if leaf in {'lat', 'lon'} and value is not None:
                if not number(value) or abs(value) > (90 if leaf == 'lat' else 180) or abs(value * 1000 - round(value * 1000)) > 1e-6:
                    self.errors['Konum sınırı/3 ondalık kuralı bozuk'] += 1; bad = True
            if isinstance(value, str) and PHONE.search(value):
                self.errors['Maskelenmemiş telefon şüphesi'] += 1; bad = True
        if name == 'rawstats/economics-scooter.jsonl':
            wall, mono = row.get('wall_ms'), row.get('mono_ns'); scooter = row.get('scooter_id')
            if number(wall):
                self.scooter_times.append(wall)
                previous = self.scooter_previous.get(scooter)
                if previous:
                    if wall < previous[0] or (number(mono) and number(previous[1]) and mono < previous[1]):
                        self.warnings['Scooter zamanı geri gitti/yeniden başlama'] += 1
                    if wall - previous[0] > 5000:
                        self.scooter_gaps += 1
                self.scooter_previous[scooter] = (wall, mono)
        stats['schema'] += bad

    def finish(self):
        self.matching = match_offers(self.raw, self.live)
        declared = self.manifest.get('matching', {})
        for key in ('matched', 'onlyRawstats', 'onlyYolgo', 'ambiguousFallback'):
            if declared.get(key) != self.matching[key]:
                self.errors['Manifest eşleşme sayıları tutarsız'] += 1
        ratio = declared.get('ratio')
        if ratio != self.matching['ratio'] and not (number(ratio) and number(self.matching['ratio']) and abs(ratio - self.matching['ratio']) < 1e-6):
            self.errors['Manifest eşleşme oranı tutarsız'] += 1
        computed = 0
        for row in self.live.values():
            route = self.routes.get(row.get('recordId'), {})
            computed += number(route.get('routeKm', row.get('routeKm')))
        self.route_ratio = computed / len(self.live) if self.live else None
        for rkey, ykey, _ in self.matching['pairs']:
            r, y = self.raw[rkey], self.live[ykey]
            rf, yf = r.get('feeAzn'), y.get('feeAzn')
            rk, yk = r.get('shown', {}).get('km'), y.get('platformKm')
            if number(rf) and number(yf) and abs(rf - yf) > .010001:
                self.warnings['Eşleşen teklif ücretleri farklı'] += 1
            if number(rk) and number(yk) and abs(rk - yk) > .05:
                self.warnings['Eşleşen platform km farklı'] += 1
        summary = self.manifest.get('summary', {})
        declared_route = summary.get('routeComputedRatio')
        if declared_route != self.route_ratio and not (number(declared_route) and number(self.route_ratio) and abs(declared_route - self.route_ratio) < 1e-6):
            self.errors['Manifest rota kapsaması tutarsız'] += 1
        collection = self.manifest.get('collection', {})
        if collection.get('complete') is not True:
            self.warnings['RAW Stats gün toplaması tamamlanmamış'] += 1
        for item in self.manifest.get('rawstatsFiles', []):
            if item.get('available') is not True:
                self.warnings['Kaynakta olmayan RAW Stats dosyası'] += 1
            self.warnings['Kaynakta reddedilen bozuk satır'] += item.get('rejectedRows', 0)
        # Native coverage compares valid 1 Hz sample segments (max 5 s gap) to shift windows.
        starts = {}
        for shift in self.manifest.get('shifts', []):
            if number(shift.get('shiftStartMs')):
                starts[shift['shiftStartMs']] = shift.get('shiftEndMs') or summary.get('coverageAsOfMs', self.manifest.get('wall_ms'))
        try:
            midnight = int(datetime.fromisoformat(self.manifest['day'] + 'T00:00:00+04:00').timestamp() * 1000)
            windows = [(max(start, midnight), min(end, midnight + 86400000)) for start, end in starts.items() if number(end)]
            windows = [(a, b) for a, b in windows if b > a]
            duration = sum(b - a for a, b in windows)
            covered = 0
            samples = sorted(set(t for t in self.scooter_times if any(a <= t <= b for a, b in windows)))
            for a, b in zip(samples, samples[1:]):
                if 0 < b - a <= 5000 and any(start <= a and b <= end for start, end in windows):
                    covered += b - a
            self.coverage_ratio = min(1, covered / duration) if duration else None
            declared_coverage = summary.get('scooterCoverageRatio')
            if declared_coverage != self.coverage_ratio and not (number(declared_coverage) and number(self.coverage_ratio) and abs(declared_coverage - self.coverage_ratio) < 1e-4):
                self.errors['Manifest scooter kapsaması tutarsız'] += 1
        except (KeyError, TypeError, ValueError):
            self.errors['Manifest gün/vardiya şeması geçersiz'] += 1


def inspect_zip(path):
    report = Report()
    with zipfile.ZipFile(path) as archive:
        members = archive.infolist(); names = [m.filename for m in members if not m.is_dir()]
        if len(names) != len(set(names)):
            raise ValueError('ZIP içinde aynı adla yinelenen dosya')
        for name in names:
            p = PurePosixPath(name)
            if p.is_absolute() or '..' in p.parts or '\\' in name:
                raise ValueError('ZIP dosya yolu geçersiz')
        manifests = [n for n in names if len(PurePosixPath(n).parts) == 2 and n.endswith('/manifest.json')]
        if len(manifests) != 1:
            raise ValueError('ZIP tek gün ve tek manifest içermeli')
        manifest_name = manifests[0]; prefix = manifest_name.rsplit('/', 1)[0] + '/'
        with archive.open(manifest_name) as stream:
            report.manifest = json.load(stream)
        manifest = report.manifest
        report.row(manifest, 'manifest.json', {'schema': 0, 'fields': 0, 'empty': 0})
        day = date.fromisoformat(manifest['day'])
        if prefix != f'{day}_{WEEKDAYS[day.weekday()]}/' or manifest.get('schema_version') != 1:
            report.errors['Manifest gün adı/schema_version tutarsız'] += 1
        if manifest.get('specVersion') != '2.1.0':
            report.warnings['SPEC sürümü 2.1.0 dışında'] += 1
        listed = {}
        for item in manifest['files']:
            if not isinstance(item, dict) or not isinstance(item.get('name'), str):
                raise ValueError('Manifest dosya listesi geçersiz')
            relative = item['name']; p = PurePosixPath(relative)
            if relative in listed or p.is_absolute() or '..' in p.parts or '\\' in relative:
                raise ValueError('Manifest dosya yolu geçersiz/yinelenen')
            listed[relative] = item
            if not isinstance(item.get('bytes'), int) or isinstance(item.get('bytes'), bool) or item['bytes'] < 0 or not re.fullmatch(r'[0-9a-f]{64}', str(item.get('sha256', ''))):
                report.errors['Manifest boyut/SHA-256 şeması geçersiz'] += 1
            if relative.endswith(('.jsonl', '.csv')) and (not isinstance(item.get('rows'), int) or isinstance(item.get('rows'), bool) or item['rows'] < 0):
                report.errors['Manifest satır şeması geçersiz'] += 1
        if set(names) != {manifest_name} | {prefix + n for n in listed}:
            report.errors['Manifest dosya listesi ZIP ile tutarsız'] += 1
        for name, item in sorted(listed.items()):
            stats = {'rows': 0, 'schema': 0, 'fields': 0, 'empty': 0, 'hash': False}
            report.files[name] = stats
            full = prefix + name
            if full not in names:
                report.errors['Manifestteki dosya ZIP içinde yok'] += 1; continue
            digest = hashlib.sha256()
            with archive.open(full) as stream:
                while chunk := stream.read(65536):
                    digest.update(chunk)
            stats['hash'] = digest.hexdigest() == item.get('sha256')
            if not stats['hash'] or archive.getinfo(full).file_size != item.get('bytes'):
                report.errors['SHA-256/bayt boyutu uyuşmuyor'] += 1
            if name.endswith('.jsonl'):
                with archive.open(full) as binary, io.TextIOWrapper(binary, encoding='utf-8') as stream:
                    for line in stream:
                        if not line.strip():
                            continue
                        stats['rows'] += 1
                        try:
                            row = json.loads(line, parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Sonlu olmayan JSON sayısı')))
                        except (json.JSONDecodeError, ValueError):
                            stats['schema'] += 1; report.errors['Bozuk JSONL satırı'] += 1; continue
                        report.row(row, name, stats)
            elif name.endswith('.csv'):
                with archive.open(full) as binary, io.TextIOWrapper(binary, encoding='utf-8', newline='') as stream:
                    for row in csv.DictReader(stream):
                        stats['rows'] += 1
                        converted = {}
                        for key in COMMON + ('power_verified',):
                            value = row.get(key)
                            converted[key] = None if value == '' else int(value) if key in {'wall_ms', 'mono_ns', 'schema_version'} and value else value == 'true' if key == 'power_verified' else value
                        for key, value in row.items():
                            if key and key.endswith('_source'):
                                converted[key] = value
                        for key in ('lat', 'lon'):
                            if row.get(key):
                                converted[key] = float(row[key])
                        report.row(converted, name, stats)
            if name.endswith(('.jsonl', '.csv')) and stats['rows'] != item.get('rows'):
                report.errors['Manifest satır sayısı tutarsız'] += 1
    report.finish()
    return report


def percent(value):
    return 'bilinmiyor' if value is None else f'%{value * 100:.1f}'


def print_report(r):
    print(f"Gün: {r.manifest.get('day', '?')} · WEEM {r.manifest.get('yolgoVersion', '?')} · RAW Stats {r.manifest.get('rawstatsVersion') or 'bilinmiyor'}")
    print('\n| Dosya | Satır | Şema hatası | Boş alan | SHA-256 |\n|---|---:|---:|---:|---|')
    for name, s in r.files.items():
        rows = s['rows'] if name.endswith(('.jsonl', '.csv')) else '—'
        print(f"| {name.replace('|', '/')} | {rows} | {s['schema']} | {percent(s['empty'] / s['fields'] if s['fields'] else None)} | {'tamam' if s['hash'] else 'HATA'} |")
    m = r.matching
    print('\n| Ölçüm | Sonuç |\n|---|---|')
    print(f"| Eşleşen / yalnız RAW / yalnız canlı kayıt | {m['matched']} / {m['onlyRawstats']} / {m['onlyYolgo']} |")
    print(f"| Eşleşme / rota km kapsaması | {percent(m['ratio'])} / {percent(r.route_ratio)} |")
    print(f"| Scooter kapsaması / >5 sn kopukluk | {percent(r.coverage_ratio)} / {r.scooter_gaps} |")
    print(f"| Şema/hash hataları / şüpheli veya eksik kayıtlar | {sum(r.errors.values())} / {sum(r.warnings.values())} |")
    for label, count in r.errors.most_common(12):
        print(f'HATA: {label}: {count}')
    for label, count in r.warnings.most_common(12):
        if count:
            print(f'KONTROL: {label}: {count}')


def common(schema=1, source='yolgo', wall=1791626400000):
    return {'wall_ms': wall, 'mono_ns': 1000000000, 'schema_version': schema, 'source': source,
            'scooter_id': None, 'scooter_id_source': 'unavailable', 'power_verified': False}


def fixture(path, alter=None):
    live = dict(common(), offerKey='offer-1791626400000-1', platform='wolt', firstSeenAtMs=1791626400000,
                lastSeenAtMs=1791626401000, feeAzn=4.49, platformKm=2.3, recordId='synthetic-record', routeKm=None)
    raw = dict(live, schema_version=3, source='kuryeplus', shown={'km': 2.3})
    data = {'yolgo-live.jsonl': (json.dumps(live) + '\n').encode(),
            'rawstats/economics-offers.jsonl': (json.dumps(raw) + '\n').encode()}
    matching = match_offers({raw['offerKey']: raw}, {live['offerKey']: live})
    matching.pop('pairs')
    manifest = dict(common(), day='2026-10-10', yolgoVersion='synthetic', rawstatsVersion='synthetic', specVersion='2.1.0',
                    matching=matching, shifts=[], collection={'complete': True},
                    summary={'routeComputedRatio': 0, 'scooterCoverageRatio': None}, files=[
                        {'name': name, 'bytes': len(content), 'rows': content.count(b'\n'), 'sha256': hashlib.sha256(content).hexdigest()} for name, content in data.items()])
    if alter:
        alter(data, manifest)
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as z:
        prefix = '2026-10-10_Cumartesi/'
        for name, content in data.items():
            z.writestr(prefix + name, content)
        z.writestr(prefix + 'manifest.json', json.dumps(manifest))


class SelfTest(unittest.TestCase):
    def check(self, alter=None):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'synthetic.zip'; fixture(path, alter); return inspect_zip(path)
    def test_valid_manifest_hash_and_matching(self):
        r = self.check(); self.assertFalse(r.errors); self.assertEqual(r.matching['matched'], 1)
    def test_hash_tampering(self):
        r = self.check(lambda data, _: data.update({'yolgo-live.jsonl': data['yolgo-live.jsonl'].replace(b'4.49', b'5.49')}))
        self.assertGreater(r.errors['SHA-256/bayt boyutu uyuşmuyor'], 0)
    def test_missing_clock_and_private_data(self):
        def change(data, _):
            j = json.loads(data['yolgo-live.jsonl']); j.pop('mono_ns'); j['customerName'] = 'SYNTHETIC PRIVATE'; j['courier'] = {'lat': 40.40912, 'lon': 49.86721}
            data['yolgo-live.jsonl'] = (json.dumps(j) + '\n').encode()
        r = self.check(change)
        self.assertGreater(r.errors['Ortak alan eksik: mono_ns'], 0)
        self.assertGreater(r.errors['Müşteri alanı veri setinde'], 0)
        self.assertGreater(r.errors['Konum sınırı/3 ondalık kuralı bozuk'], 0)
    def test_fallback_boundary_and_ambiguity(self):
        a = {'platform': 'bolt', 'firstSeenAtMs': 10000, 'feeAzn': 4.49}
        b = dict(a, firstSeenAtMs=15000)
        self.assertEqual(match_offers({'r': a}, {'y': b})['matched'], 1)
        self.assertEqual(match_offers({'r': a}, {'y': dict(b, firstSeenAtMs=15001)})['matched'], 0)
        self.assertEqual(match_offers({'r': a, 'r2': a}, {'y': b})['ambiguousFallback'], 1)
        self.assertEqual(match_offers({'r': a}, {'y': b, 'y2': b})['matched'], 0)
    def test_manifest_match_mismatch(self):
        r = self.check(lambda _, manifest: manifest['matching'].update({'matched': 9}))
        self.assertGreater(r.errors['Manifest eşleşme sayıları tutarsız'], 0)
    def test_scooter_gap_and_clock_reversal(self):
        r = Report(); stats = {'schema': 0, 'fields': 0, 'empty': 0}
        for wall, mono in [(10000, 1000), (16000, 2000), (17000, 500)]:
            row = dict(common(3, 'surus_paneli', wall), mono_ns=mono)
            r.row(row, 'rawstats/economics-scooter.jsonl', stats)
        self.assertEqual(r.scooter_gaps, 1)
        self.assertEqual(r.warnings['Scooter zamanı geri gitti/yeniden başlama'], 1)
    def test_zip_traversal_rejected_without_extracting(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'bad.zip'; fixture(path)
            with zipfile.ZipFile(path, 'a') as z: z.writestr('../outside', 'x')
            with self.assertRaises(ValueError): inspect_zip(path)
    def test_images_are_opaque_and_only_hash_checked(self):
        def change(data, manifest):
            name = 'attachments/wolt_earnings_synthetic.png'; content = b'opaque image bytes; not decoded'
            data[name] = content
            manifest['files'].append({'name': name, 'bytes': len(content), 'rows': None, 'sha256': hashlib.sha256(content).hexdigest()})
        r = self.check(change)
        self.assertFalse(r.errors); self.assertTrue(r.files['attachments/wolt_earnings_synthetic.png']['hash'])


def main():
    parser = argparse.ArgumentParser(description='WEEM teklif veri setini çevrimdışı kontrol eder.')
    parser.add_argument('zip', nargs='?', type=Path)
    parser.add_argument('--self-test', action='store_true', help='Sentetik veriyle öz-test')
    args = parser.parse_args()
    if args.self_test:
        result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(SelfTest))
        return 0 if result.wasSuccessful() else 1
    if args.zip is None:
        parser.error('Bir gün ZIP dosyası veya --self-test gerekli')
    try:
        report = inspect_zip(args.zip)
    except (OSError, ValueError, TypeError, KeyError, UnicodeError, zipfile.BadZipFile, RuntimeError) as exc:
        print(f'ZIP okunamadı veya şema geçersiz: {type(exc).__name__}')
        return 2
    print_report(report)
    return 1 if report.errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
