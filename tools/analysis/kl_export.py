"""KuryeLog / KuryePlus export loader for the offline analysis (standard library only).

Reads a ZIP or a folder (or several, merged line by line like BackupFiles.mergeLines):
day folders `YYYY-MM-DD/<log>`, `restaurants.json` and the anonymous-export marker
`ANALIZ_ANONIM.json` at the root (a single top folder around them is tolerated).
Rows are dispatched on `schemaVersion`; damaged lines and unknown versions are counted,
never guessed. Geography helpers mirror `docs/ekonomi-v2/ref.py` (zones, hour blocks)
and WP2 (`OfferGeo`: Plus Code, DMS) so the analysis needs no network and no geocoding.
"""
import bisect
import csv
import io
import json
import math
import os
import re
import zipfile
from collections import Counter
from datetime import datetime, timedelta, timezone

BAKU = timezone(timedelta(hours=4))  # Asia/Baku, no DST since 2016
MARKER = "ANALIZ_ANONIM.json"
MARKER_SCHEMA = "kuryeplus-analysis-export/1"
GPS_HEADER = "t,lat,lon,acc_m,speed_ms,bearing,alt_m"
LOG_NAMES = {  # BackupFiles.logNames (0.14.0) + the v2 logs of SPEC 2.1.0 section 17.2
    "screens.jsonl", "notifs.jsonl", "events.jsonl", "orders.jsonl", "gps.csv", "scooter.jsonl", "scooter_events.jsonl",
    "scooter_averages.jsonl", "platforms.jsonl", "errors.jsonl", "economics-waits.jsonl", "economics-exposure.jsonl",
    "online-sessions.jsonl", "economics-idle.jsonl", "economics-offers.jsonl", "economics-legs.jsonl",
    "economics-payouts.jsonl", "economics-penalties.jsonl",
    "economics-platform-history.jsonl",  # K36 PlatformHistory.FILE
    "economics-platform-totals.jsonl",  # F8.3 PlatformTotals.FILE
    "economics-cash-tips.jsonl",  # K32 CashTips.FILE
    "economics-pocket-cash.jsonl",  # M15 PocketCash.FILE
    "economics-cash-pending.jsonl",  # M17 CashPending.FILE
    "economics-boosts.jsonl",  # K27 BoostLog.FILE
    "economics-energy.jsonl",  # EnergySegments.FILE (energy block per leg / idle spell)
    "economics-scooter.jsonl"}  # ScooterSamples.FILE (1 Hz scooter values of a shift, merged Sürüş Paneli)
ROOT_NAMES = {"restaurants.json", MARKER}
MAX_TOTAL_BYTES = 512 * 1024 * 1024
DAY_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")

# --- ref.py 2.1.0 constants (verbatim) --------------------------------------------------------
HUB_DEFAULT = (40.4093, 49.8671)
ZONES = [("Z1", 0.0, 2.5), ("Z2", 2.5, 6.0), ("Z3", 6.0, 12.0), ("Z4", 12.0, float("inf"))]
HOUR_BLOCKS = [("B0", 0, 480), ("B1", 480, 705), ("B2", 705, 900), ("B3", 900, 1080), ("B4", 1080, 1290), ("B5", 1290, 1440)]
BLOCK_LABEL = {"B0": "00:00-08:00", "B1": "08:00-11:45", "B2": "11:45-15:00", "B3": "15:00-18:00", "B4": "18:00-21:30", "B5": "21:30-24:00"}
LAMBDA_BASE_PER_H = 6.0
ZONE_RATE_MULT = {"Z1": 1.30, "Z2": 1.00, "Z3": 0.45, "Z4": 0.20}
HOUR_BLOCK_MULT = {"B0": 0.30, "B1": 0.60, "B2": 1.40, "B3": 0.90, "B4": 1.50, "B5": 0.80}
GAMMA_PRIOR_SHAPE = 4.0
DOCUMENTED_WINDOW_MS = 60000


def haversine_km(lat1, lon1, lat2, lon2):
    R = 6371.0088
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * R * math.asin(math.sqrt(a))


def zone_of_radius(r_km):
    for zid, lo, hi in ZONES:
        if lo <= r_km < hi:
            return zid
    return "Z4"


def zone_of(lat, lon, hub=HUB_DEFAULT):
    if lat is None or lon is None:
        return "unknown"
    return zone_of_radius(haversine_km(hub[0], hub[1], lat, lon))


def hour_block(minute_of_day):
    m = minute_of_day % 1440
    for bid, lo, hi in HOUR_BLOCKS:
        if lo <= m < hi:
            return bid
    return "B0"


def lambda_prior(zone, hb):
    return LAMBDA_BASE_PER_H * ZONE_RATE_MULT.get(zone, 1.0) * HOUR_BLOCK_MULT[hb]


# --- Open Location Code (public spec; same algorithm as docs/ekonomi-v2/vektorler/olc_ref.py) ---
OLC_A = "23456789CFGHJMPQRVWX"
_PAIR_RES = [20.0, 1.0, 0.05, 0.0025, 0.000125]
PLUS_RE = re.compile(r"(?<![0-9A-Za-z])([23456789CFGHJMPQRVWX]{4,8}\+[23456789CFGHJMPQRVWX]{2,3})(?![0-9A-Za-z])")
DMS_RE = re.compile(r"([0-9]{1,2})°\s*([0-9]{1,2})['′]\s*([0-9]{1,2}(?:[.,][0-9]+)?)[\"″]\s*([NS])[\s,]*"
                    r"([0-9]{1,3})°\s*([0-9]{1,2})['′]\s*([0-9]{1,2}(?:[.,][0-9]+)?)[\"″]\s*([EW])")


def olc_encode(lat, lng, length=10):
    lat = min(max(lat, -90.0), 90.0)
    if lat == 90.0:
        lat -= 0.000125 / 2
    lng = ((lng + 180.0) % 360.0) - 180.0
    la, ln = lat + 90.0, lng + 180.0
    code = ""
    for i in range(5):
        r = _PAIR_RES[i]
        d1 = int(la // r); la -= d1 * r
        d2 = int(ln // r); ln -= d2 * r
        code += OLC_A[d1] + OLC_A[d2]
        if len(code) == 8:
            code += "+"
    if length <= 10:
        return code[:length + 1]
    lr = gr = _PAIR_RES[4]
    for _ in range(length - 10):
        lr /= 5; gr /= 4
        row = min(int(la // lr), 4); la -= row * lr
        col = min(int(ln // gr), 3); ln -= col * gr
        code += OLC_A[row * 4 + col]
    return code


def olc_center(code):
    c = code.replace("+", "").upper()
    lat, lng, lat_res, lng_res = -90.0, -180.0, 0.0, 0.0
    for i in range(0, min(len(c), 10), 2):
        r = _PAIR_RES[i // 2]
        lat += OLC_A.index(c[i]) * r
        if i + 1 < len(c):
            lng += OLC_A.index(c[i + 1]) * r
        lat_res = lng_res = r
    if len(c) > 10:
        lr = gr = _PAIR_RES[4]
        for ch in c[10:]:
            lr /= 5; gr /= 4
            v = OLC_A.index(ch)
            lat += (v // 4) * lr
            lng += (v % 4) * gr
        lat_res, lng_res = lr, gr
    return lat + lat_res / 2, lng + lng_res / 2


def olc_recover(short, ref_lat, ref_lng):
    short = short.upper()
    pad = 8 - short.index("+")
    if pad <= 0:
        return short
    res = 20.0 ** (2 - pad / 2)
    half = res / 2.0
    full = olc_encode(ref_lat, ref_lng)[:pad] + short
    clat, clng = olc_center(full)
    if ref_lat + half < clat and clat - res >= -90:
        clat -= res
    elif ref_lat - half > clat and clat + res <= 90:
        clat += res
    if ref_lng + half < clng:
        clng -= res
    elif ref_lng - half > clng:
        clng += res
    return olc_encode(clat, clng, len(full.replace("+", "")))


def plus_code_center(code, ref_lat=None, ref_lon=None):
    if ref_lat is None or ref_lon is None:
        ref_lat, ref_lon = HUB_DEFAULT
    return olc_center(olc_recover(code, ref_lat, ref_lon))


def dms_lat_lon(text):
    m = DMS_RE.search(text or "")
    if not m:
        return None
    def part(d, mi, s, h):
        v = int(d) + int(mi) / 60.0 + float(s.replace(",", ".")) / 3600.0
        return -v if h in "SW" else v
    return part(*m.group(1, 2, 3, 4)), part(*m.group(5, 6, 7, 8))


def from_card_text(text, ref_lat=None, ref_lon=None):
    """DMS first, then a Plus Code token; addresses without coordinates return None (never geocode)."""
    if not text:
        return None
    d = dms_lat_lon(text)
    if d:
        return d
    m = PLUS_RE.search(text)
    return plus_code_center(m.group(1), ref_lat, ref_lon) if m else None


# --- time --------------------------------------------------------------------------------------
def parse_ts(s):
    """Store.ts format 'yyyy-MM-dd'T'HH:mm:ss.SSSZ' (e.g. 2026-10-06T13:00:00.000+0400) -> epoch ms."""
    if not isinstance(s, str) or not s:
        return None
    for fmt in ("%Y-%m-%dT%H:%M:%S.%f%z", "%Y-%m-%dT%H:%M:%S%z"):
        try:
            return int(datetime.strptime(s, fmt).timestamp() * 1000)
        except ValueError:
            pass
    try:
        d = datetime.fromisoformat(s.replace("Z", "+00:00"))
        if d.tzinfo is None:
            d = d.replace(tzinfo=BAKU)
        return int(d.timestamp() * 1000)
    except ValueError:
        return None


def local(ms):
    return datetime.fromtimestamp(ms / 1000.0, BAKU)


def baku_day(ms):
    return local(ms).strftime("%Y-%m-%d")


def minute_of_day(ms):
    d = local(ms)
    return d.hour * 60 + d.minute + d.second / 60.0 + d.microsecond / 6e7


def block_of_ms(ms):
    return hour_block(minute_of_day(ms))


def day_start_ms(day):
    return int(datetime.strptime(day, "%Y-%m-%d").replace(tzinfo=BAKU).timestamp() * 1000)


def clock(ms):
    return local(ms).strftime("%H:%M")


def platform(pkg):
    p = (pkg or "").lower()
    if "wolt" in p:
        return "wolt"
    if "bolt" in p or "mtakso" in p:
        return "bolt"
    return p.rsplit(".", 1)[-1] or "unknown"


def num(v):
    if isinstance(v, bool) or v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v) if math.isfinite(v) else None
    try:
        f = float(v)
        return f if math.isfinite(f) else None
    except (TypeError, ValueError):
        return None


def ms_int(v):
    f = num(v)
    return int(f) if f is not None else None


# --- loading -----------------------------------------------------------------------------------
def read_files(path):
    """path -> {relative/posix/path: bytes}. Folder or ZIP; sizes are bounded."""
    out, total = {}, 0
    if os.path.isdir(path):
        for root, _, names in os.walk(path):
            for n in names:
                full = os.path.join(root, n)
                rel = os.path.relpath(full, path).replace(os.sep, "/")
                with open(full, "rb") as f:
                    data = f.read()
                total += len(data)
                if total > MAX_TOTAL_BYTES:
                    raise ValueError("export too large")
                out[rel] = data
        return out
    if zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as z:
            for info in z.infolist():
                if info.is_dir():
                    continue
                total += info.file_size
                if total > MAX_TOTAL_BYTES:
                    raise ValueError("export too large")
                out[info.filename.replace("\\", "/")] = z.read(info)
        return out
    raise ValueError("not a ZIP file or folder: %s" % path)


def classify(rel):
    """('day', day, name) | ('root', name) | None for a path inside an export."""
    parts = [p for p in rel.split("/") if p and p != "."]
    if not parts or any(p == ".." for p in parts):
        return None
    if len(parts) >= 2 and DAY_RE.match(parts[-2]) and parts[-1] in LOG_NAMES and len(parts) <= 3:
        return ("day", parts[-2], parts[-1])
    if parts[-1] in ROOT_NAMES and len(parts) <= 2:
        return ("root", parts[-1])
    return None


def export_root(rel, c):
    """The folder (posix, '' = top) that holds the export a classified path belongs to."""
    parts = [p for p in rel.split("/") if p and p != "."]
    return "/".join(parts[:-2] if c[0] == "day" else parts[:-1])


def merge_lines(a, b):
    seen, out = set(), []
    for line in a.splitlines() + b.splitlines():
        if line.strip() and line not in seen:
            seen.add(line)
            out.append(line)
    return "\n".join(out) + ("\n" if out else "")


class Export:
    """Merged files of one or more exports plus counters for what could not be read."""

    def __init__(self, files=None, source=""):
        self.source = source
        self.day_files = {}   # (day, name) -> text
        self.root_files = {}  # name -> text
        self.ignored = []
        self.stats = Counter()
        self.roots = set()      # export folders the read files came from ('' = top), see `anonymous`
        if files:
            self.add(files)

    def add(self, files):
        for rel, data in files.items():
            c = classify(rel)
            if c is None:
                self.ignored.append(rel)
                continue
            text = data.decode("utf-8", errors="replace") if isinstance(data, (bytes, bytearray)) else data
            self.roots.add(export_root(rel, c))
            if c[0] == "day":
                key = (c[1], c[2])
                self.day_files[key] = merge_lines(self.day_files.get(key, ""), text)
            elif c[1] == "restaurants.json":
                self.root_files[c[1]] = self._merge_restaurants(self.root_files.get(c[1]), text)
            else:
                self.root_files.setdefault(c[1], text)
        self._cache = {}

    def _merge_restaurants(self, old, new):
        try:
            cur = json.loads(old) if old else {}
            inc = json.loads(new)
        except ValueError:
            self.stats["damaged:restaurants.json"] += 1
            return old or "{}"
        for k, v in inc.items():
            if isinstance(v, dict) and (k not in cur or (v.get("n") or 0) > (cur[k].get("n") or 0)):
                cur[k] = v
        return json.dumps(cur, ensure_ascii=False)

    @property
    def marker(self):
        raw = self.root_files.get(MARKER)
        if raw is None:
            return None
        try:
            m = json.loads(raw)
        except ValueError:
            return None
        return m if isinstance(m, dict) and m.get("schema") == MARKER_SCHEMA else None

    @property
    def anonymous(self):
        """True only when a valid marker sits next to every file read: one export folder, one marker.

        Decide this per input BEFORE merging (run.py): a merged Export keeps the first marker it saw, and a
        folder or ZIP that holds an anonymous export beside a full one must not pass as anonymous.
        """
        return self.marker is not None and len(self.roots) == 1

    def files(self):
        """{relative path: text} of the readable day logs and root files, e.g. to merge one Export into another."""
        out = {"%s/%s" % key: text for key, text in self.day_files.items()}
        out.update(self.root_files)
        return out

    @property
    def days(self):
        return sorted({d for d, _ in self.day_files})

    def names(self):
        return sorted({n for _, n in self.day_files})

    def text(self, day, name):
        return self.day_files.get((day, name), "")

    def jsonl(self, name):
        """[(day, row)] over every day folder, damaged lines counted in stats."""
        if name in self._cache:
            return self._cache[name]
        out = []
        for day in self.days:
            for line in self.text(day, name).splitlines():
                if not line.strip():
                    continue
                try:
                    row = json.loads(line)
                except ValueError:
                    self.stats["damaged:" + name] += 1
                    continue
                if isinstance(row, dict):
                    out.append((day, row))
                else:
                    self.stats["damaged:" + name] += 1
        self._cache[name] = out
        return out

    def versioned(self, name, known, default=None):
        """Rows whose schemaVersion is in `known` (missing -> default); others are counted and skipped."""
        out = []
        for day, row in self.jsonl(name):
            v = row.get("schemaVersion", default)
            if v in known:
                out.append((day, v, row))
            else:
                self.stats["unknown_schema:%s:%s" % (name, v)] += 1
        return out


def load(paths):
    """Read and merge one or more exports. It does not gate anonymity: the merged Export keeps the first marker it
    sees, so a caller that needs anonymous data checks each input on its own first (run.prepare, Export.anonymous)."""
    if isinstance(paths, str):
        paths = [paths]
    exp = Export(source=", ".join(os.path.basename(os.path.normpath(p)) for p in paths))
    for p in paths:
        exp.add(read_files(p))
    return exp


def from_memory(files, source="memory"):
    return Export(files, source)


# --- normalised readers ------------------------------------------------------------------------
def gps(exp):
    """Sorted fixes [(ms, lat, lon, acc)] from every gps.csv; repeated headers and bad rows skipped."""
    rows = []
    for day in exp.days:
        text = exp.text(day, "gps.csv")
        if not text:
            continue
        for rec in csv.reader(io.StringIO(text)):
            if not rec or rec[0] == "t":
                continue
            try:
                t = parse_ts(rec[0]); lat = float(rec[1]); lon = float(rec[2])
            except (ValueError, IndexError):
                exp.stats["damaged:gps.csv"] += 1
                continue
            if t is None or not (-90 <= lat <= 90 and -180 <= lon <= 180):
                exp.stats["damaged:gps.csv"] += 1
                continue
            acc = num(rec[3]) if len(rec) > 3 else None
            rows.append((t, lat, lon, acc))
    rows.sort()
    dedup = []
    for r in rows:
        if not dedup or r[0] != dedup[-1][0]:
            dedup.append(r)
    return dedup


class Track:
    """Position at a time: nearest GPS fix within max_gap_ms, else an explicit fallback."""

    def __init__(self, fixes, max_gap_ms=5 * 60000):
        self.t = [f[0] for f in fixes]
        self.f = fixes
        self.max_gap = max_gap_ms

    def at(self, ms):
        if not self.t or ms is None:
            return None
        i = bisect.bisect_left(self.t, ms)
        best = None
        for j in (i - 1, i):
            if 0 <= j < len(self.t):
                d = abs(self.t[j] - ms)
                if d <= self.max_gap and (best is None or d < best[0]):
                    best = (d, self.f[j][1], self.f[j][2])
        return (best[1], best[2]) if best else None


def loc_of(row):
    loc = row.get("loc") if isinstance(row, dict) else None
    if isinstance(loc, dict):
        lat, lon = num(loc.get("lat")), num(loc.get("lon"))
        if lat is not None and lon is not None:
            return lat, lon
    return None


def observation(d):
    d = d if isinstance(d, dict) else {}
    lo, hi = num(d.get("min_lo")), num(d.get("min_hi"))
    appr, deliv = num(d.get("approachMin") or d.get("approach_min")), num(d.get("deliveryMin") or d.get("delivery_min"))
    if appr is not None and deliv is not None:
        shown_m, msrc = appr + deliv, "eta_sum"
    elif lo is not None and hi is not None:
        shown_m, msrc = (lo + hi) / 2.0, "range_midpoint"
    elif lo is not None or hi is not None:
        shown_m, msrc = (lo if lo is not None else hi), "single"
    else:
        shown_m, msrc = None, "missing"
    return {"gross": num(d.get("gross")), "km": num(d.get("km")), "min_lo": lo, "min_hi": hi, "shown_m": shown_m,
            "shown_m_source": msrc, "stops": num(d.get("stops")), "source": d.get("source"), "stage": d.get("stage"),
            "restaurant": d.get("restaurant"), "restaurant_address": d.get("restaurant_address"), "at": ms_int(d.get("at"))}


DECISION_OF = {  # SPEC 2.1.0 section 17.2 mapping from KuryeLog >= 0.11 core events
    ("accepted", "active_screen"): "accepted_observed",
    ("accepted", "active_screen_offer_missed"): "accepted_observed",
    ("rejected", "courier_declined_confirmed"): "declined_tap_observed",
    ("rejected", "courier_declined"): "declined_tap_observed",
    ("rejected", "courier_declined_banner"): "declined_banner_observed",
    ("offer_unconfirmed", "offer_not_seen"): "disappeared_unknown",
    ("offer_unconfirmed", "expired_inferred"): "timeout_inferred",
    ("offer_unconfirmed", "replaced_without_idle"): "replaced_by_new_offer",
    ("offer_unconfirmed", "manual_shift_stop"): "recording_stopped",
    ("offer_unconfirmed", "restart"): "recording_stopped",
}
DECLINE_KINDS = {"declined_tap_observed", "declined_banner_observed", "timeout_inferred", "disappeared_unknown"}


def decision_kind(typ, reason, first_at, at):
    if (typ, reason) in DECISION_OF:
        return DECISION_OF[(typ, reason)]
    if typ == "accepted":
        return "accepted_observed"
    if typ == "rejected" and reason == "offer_to_idle_map":
        # visible until (documented window - 2 s): the first frame without it came at/after that instant
        return "timeout_inferred" if first_at is not None and at is not None and at - first_at >= DOCUMENTED_WINDOW_MS - 2000 else "disappeared_unknown"
    if typ == "offer_unconfirmed":
        return "recording_stopped" if reason and ("restart" in reason or "stop" in reason) else "disappeared_unknown"
    if typ == "rejected":
        return "declined_tap_observed" if reason and "declin" in reason else "disappeared_unknown"
    return "disappeared_unknown"


def order_events(exp):
    """orders.jsonl event rows (record_snapshot rows excluded), sorted by time."""
    out = []
    for day, row in exp.jsonl("orders.jsonl"):
        typ = row.get("type")
        if not typ or typ == "record_snapshot":
            continue
        at = parse_ts(row.get("t"))
        if at is None:
            exp.stats["no_time:orders.jsonl"] += 1
            continue
        out.append({"day": day, "at": at, "type": typ, "pkg": row.get("pkg"), "platform": platform(row.get("pkg")),
                    "offer_id": row.get("offer_id"), "trip_id": row.get("id"), "reason": row.get("reason"),
                    "gross": num(row.get("gross")), "net": num(row.get("net")), "km": num(row.get("km")),
                    "loc": loc_of(row), "offer": row.get("offer") if isinstance(row.get("offer"), dict) else None,
                    "record": row.get("record") if isinstance(row.get("record"), dict) else None,
                    "session_id": row.get("session_id"), "total_min": num(row.get("total_min")), "job": row.get("job"),
                    "commission_pct": num(row.get("commission_pct"))})
    out.sort(key=lambda e: e["at"])
    return out


_PHASE_RANK = {"FINISHED": 3, "END_UNCONFIRMED": 2}


def legacy_record(e):
    """LegacyRecords.delivered: a `delivered` row without `record` (pre-0.7 logs) -> a FINISHED record dict.
    accepted = t - total_min. The anonymous export drops `commission_pct`; the row's `net` then gives it back."""
    mins = e.get("total_min")
    acc = e["at"] - int(mins * 60000) if mins is not None and mins >= 0 else e["at"]
    pkg = e.get("pkg") or "unknown"
    tid = "legacy-%s" % (e.get("job") or "%s-%d" % (pkg, e["at"]))
    c = e.get("commission_pct")
    if c is None and e.get("gross") and e.get("net") is not None:
        c = 100.0 * (1.0 - e["net"] / e["gross"])
    return {"id": tid, "pkg": pkg, "accepted_at": acc, "completed_at": e["at"], "phase": "FINISHED", "commission_pct": c or 0.0,
            "offer": {"id": "legacy-offer:" + tid, "pkg": pkg, "at": acc,
                      "data": {"gross": e.get("gross"), "km": e.get("km"), "source": "legacy_log"}}}


def trips(exp, events=None):
    """Latest structured record per trip id (event `record` and `record_snapshot` rows, legacy `delivered` rows)."""
    events = order_events(exp) if events is None else events
    best = {}
    def offer_rec(rec, at):
        tid = rec.get("id")
        if not tid or ms_int(rec.get("accepted_at")) is None:
            return
        key = (_PHASE_RANK.get(rec.get("phase"), 1), 1 if rec.get("completed_at") is not None else 0, at)
        if tid not in best or key >= best[tid][0]:
            best[tid] = (key, rec)
    for e in events:
        if e["record"]:
            offer_rec(e["record"], e["at"])
        elif e["type"] == "delivered":
            offer_rec(legacy_record(e), e["at"])
    for day, row in exp.jsonl("orders.jsonl"):
        if row.get("type") == "record_snapshot" and isinstance(row.get("record"), dict):
            offer_rec(row["record"], parse_ts(row.get("t")) or 0)
    last_event = {}
    for e in events:
        if e["trip_id"]:
            last_event[e["trip_id"]] = max(last_event.get(e["trip_id"], 0), e["at"])
    out = []
    for tid, (_, r) in best.items():
        offer = r.get("offer") if isinstance(r.get("offer"), dict) else None
        data = observation(offer.get("data")) if offer else observation({})
        gross = data["gross"]
        c = num(r.get("commission_pct")) or 0.0
        tip = num(r.get("tip")) or 0.0
        acc = ms_int(r.get("accepted_at"))
        comp = ms_int(r.get("completed_at"))
        pickups = [p for p in (r.get("pickups") or []) if isinstance(p, dict)]
        customers = [c_ for c_ in (r.get("customers") or []) if isinstance(c_, dict)]
        end = comp if comp is not None else max(last_event.get(tid, acc), acc)
        out.append({"id": tid, "pkg": r.get("pkg"), "platform": platform(r.get("pkg")), "accepted_at": acc,
                    "completed_at": comp, "end_at": end, "phase": r.get("phase"), "gross": gross,
                    "net": (gross * (1 - c / 100.0) + tip) if gross is not None else None, "commission_pct": c,
                    "km": (num(r.get("distance_m")) or 0.0) / 1000.0, "offer_id": offer.get("id") if offer else None,
                    "offer": offer, "data": data, "missed_offer": bool(r.get("missed_offer")),
                    "arrived_at": next((ms_int(p.get("arrived_at")) for p in pickups if p.get("arrived_at") is not None), None),
                    "picked_at": next((ms_int(p.get("picked_at")) for p in pickups if p.get("picked_at") is not None), None),
                    "customers": [{"lat": num(x.get("lat")), "lon": num(x.get("lon")), "delivered_at": ms_int(x.get("delivered_at"))}
                                  for x in customers],
                    # K34 (after 0.15.0): closed because another order reached the customer stage; an unverified delivery,
                    # phase END_UNCONFIRMED and no completed_at, never counted with the FINISHED ones.
                    "delivered_inferred": r.get("phase") == "END_UNCONFIRMED" and ms_int(r.get("delivered_inferred_at")) is not None,
                    "delivered_inferred_at": ms_int(r.get("delivered_inferred_at")),
                    "session_id": r.get("session_id"),
                    # K31: absent on records without an observed payment screen -> None + "not_observed".
                    "payment_method": r.get("payment_method"),
                    "payment_source": r.get("payment_source") or "not_observed",
                    "cash_amount_azn": num(r.get("cash_amount_azn"))})
    out.sort(key=lambda t: t["accepted_at"])
    return out


def offers(exp, events=None, trip_list=None):
    """Every presented offer with its outcome (orders.jsonl), enriched by economics-offers.jsonl v1 rows."""
    events = order_events(exp) if events is None else events
    trip_list = trips(exp, events) if trip_list is None else trip_list
    by_offer_trip = {t["offer_id"]: t for t in trip_list if t["offer_id"]}
    res = {}
    for e in events:
        if e["type"] == "offer" and e["offer_id"]:
            o = e["offer"] or {}
            first = ms_int(o.get("at")) or e["at"]
            lat, lon = num(o.get("lat")), num(o.get("lon"))
            res.setdefault(e["offer_id"], {"id": e["offer_id"], "pkg": e["pkg"], "platform": e["platform"], "at": first,
                                           "lat": lat, "lon": lon, "loc": (lat, lon) if lat is not None and lon is not None else e["loc"],
                                           "data": observation(o.get("data")), "decision": None, "decision_at": None,
                                           "reason": None, "trip_id": None, "v2": None, "session_id": o.get("session_id") or e["session_id"]})
    for e in events:
        oid = e["offer_id"]
        if not oid or e["type"] == "offer" or oid not in res:
            continue
        o = res[oid]
        if o["decision"] is None:
            o["decision"] = decision_kind(e["type"], e["reason"], o["at"], e["at"])
            o["decision_at"], o["reason"] = e["at"], (e["type"] + "/" + (e["reason"] or ""))
            if e["type"] == "accepted":
                o["trip_id"] = e["trip_id"]
    for oid, o in res.items():
        t = by_offer_trip.get(oid)
        if t:
            o["trip_id"] = t["id"]
            o["data"] = t["data"]  # the accepted trip keeps the enriched offer card
            if o["decision"] is None:
                o["decision"] = "accepted_observed"
    v2 = {}
    for day, ver, row in exp.versioned("economics-offers.jsonl", {1}):
        key = row.get("offerKey")
        if key:
            v2[key] = row
    by_pkg_time = {}
    for o in res.values():
        by_pkg_time[(o["pkg"], o["at"])] = o
    for key, row in v2.items():
        o = res.get(key) or by_pkg_time.get((row.get("pkg"), ms_int(row.get("firstSeenAtMs"))))
        if o is None:
            shown = row.get("shown") or {}
            o = {"id": key, "pkg": row.get("pkg"), "platform": row.get("platform") or platform(row.get("pkg")),
                 "at": ms_int(row.get("firstSeenAtMs")), "lat": None, "lon": None, "loc": None,
                 "data": observation({"gross": row.get("feeAzn"), "km": shown.get("km"), "min_lo": shown.get("minLo"),
                                      "min_hi": shown.get("minHi"), "approachMin": shown.get("approachMin"),
                                      "deliveryMin": shown.get("deliveryMin")}),
                 "decision": None, "decision_at": None, "reason": None, "trip_id": None, "v2": None, "session_id": None}
            if o["at"] is None:
                continue
            res[key] = o
        o["v2"] = row
        if row.get("decisionKind"):
            o["decision"] = row["decisionKind"]
            o["decision_at"] = ms_int(row.get("decisionAtMs")) or o["decision_at"]
        if o["data"]["gross"] is None and num(row.get("feeAzn")) is not None:
            o["data"]["gross"] = num(row.get("feeAzn"))
    out = sorted(res.values(), key=lambda o: o["at"])
    for o in out:
        if o["decision"] is None:
            o["decision"] = "open_or_unknown"
        v = o["v2"] or {}
        if "countsAsDecline" in v:
            o["counts_as_decline"] = bool(v["countsAsDecline"])
        else:
            o["counts_as_decline"] = o["decision"] in DECLINE_KINDS and not v.get("optional", False)
        shown = v.get("shown") if isinstance(v.get("shown"), dict) else {}
        o["boost"] = num(shown.get("boostPct"))
    return out


def sessions(exp):
    """online-sessions.jsonl schema 1 (counters only) and 2; one row per (pkg, start)."""
    best = {}
    for day, ver, row in exp.versioned("online-sessions.jsonl", {1, 2}, default=1):
        start, end = ms_int(row.get("startAtMs")), ms_int(row.get("endAtMs"))
        pkg = row.get("pkg")
        if start is None or end is None or not pkg or end < start:
            exp.stats["damaged:online-sessions.jsonl"] += 1
            continue
        s = {"pkg": pkg, "platform": platform(pkg), "start": start, "end": end, "schema": ver,
             "start_observed": bool(row.get("startObserved")), "end_observed": bool(row.get("endObserved")),
             "end_reason": row.get("endReason"), "start_evidence": row.get("startEvidence") if ver >= 2 else None,
             "offers": ms_int(row.get("offersSeen")) or 0, "accepted": ms_int(row.get("accepted")) or 0,
             "delivered": ms_int(row.get("delivered")) or 0, "rejected": ms_int(row.get("rejected")) or 0,
             "id": row.get("sessionId") or "%s-%d" % (platform(pkg), start)}
        k = (pkg, start)
        if k not in best or s["end"] > best[k]["end"]:
            best[k] = s
    return sorted(best.values(), key=lambda s: (s["start"], s["pkg"]))


def waits(exp):
    out = []
    for day, ver, row in exp.versioned("economics-waits.jsonl", {1, 2}, default=1):
        s, e = ms_int(row.get("startAtMs")), ms_int(row.get("endAtMs"))
        if s is None or e is None:
            continue
        r = dict(row)
        r.update({"start": s, "end": e, "schema": ver, "platform": platform(row.get("pkg"))})
        out.append(r)
    return out


def v1_rows(exp, name, time_key):
    out = []
    for day, ver, row in exp.versioned(name, {1}):
        r = dict(row)
        r["_day"] = day
        r["_at"] = ms_int(row.get(time_key))
        out.append(r)
    return out


def idle_spells(exp):
    return v1_rows(exp, "economics-idle.jsonl", "startAtMs")


def penalties(exp):
    return v1_rows(exp, "economics-penalties.jsonl", "observedAtMs")


def legs(exp):
    return v1_rows(exp, "economics-legs.jsonl", "acceptedAtMs")


def payouts(exp):
    return v1_rows(exp, "economics-payouts.jsonl", "enteredAtMs")


def cash_tips(exp):
    """K32 cash answers, newest row per trip id (append-only log; a corrected answer is a newer row), schemaVersion 1."""
    best = {}
    for r in v1_rows(exp, "economics-cash-tips.jsonl", "at"):
        tid = r.get("trip_id")
        if not tid or r.get("received_azn") is None:
            continue
        if tid not in best or (r["_at"] or 0) >= (best[tid]["_at"] or 0):
            best[tid] = r
    return best


def platform_history(exp):
    """K36 rows of the platforms' own earnings lists (Wolt Gross earnings, Bolt All deliveries), schemaVersion 1."""
    return v1_rows(exp, "economics-platform-history.jsonl", "observed_at")


def simple_rows(exp, name):
    """Rows with a 't' timestamp: events, platforms, screens, notifs."""
    out = []
    for day, row in exp.jsonl(name):
        at = parse_ts(row.get("t"))
        if at is None:
            exp.stats["no_time:" + name] += 1
            continue
        out.append((at, row))
    out.sort(key=lambda x: x[0])
    return out


def restaurants(exp):
    raw = exp.root_files.get("restaurants.json")
    try:
        return json.loads(raw) if raw else {}
    except ValueError:
        return {}


def norm_text(s):
    return re.sub(r"\s+", " ", (s or "").strip().lower())


_ACCEPT = {"accept", "accept order", "accept delivery", "qəbul", "qəbul et", "qəbul etmək", "kabul", "kabul et", "принять", "принять заказ"}


def is_accept_text(s):
    """OfferRules.isAccept / CourierParser.isAccept (label with an optional '(12 s)' timer, or a fee + minutes button)."""
    n = re.sub(r"(?:\s*\([0-9]+\s*(?:s|sec)?\)|\s*\[[0-9]+\s*(?:s|sec)?\])$", "", norm_text(s)).strip()
    if n in _ACCEPT:
        return True
    return bool(re.search(r"(₼|azn)\s*[0-9]|[0-9][.,][0-9]{2}\s*(₼|azn)", n)) and bool(re.search(r"(?<![0-9a-z])[0-9]+\s*(min|deq|dəq|dk)(?![a-z])", n))
