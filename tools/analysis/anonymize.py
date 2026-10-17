"""Python twin of the WP3 anonymous analysis export (AnalysisExport allowlist, SPEC 2.1.0 section 18.1).

Used by run.py only with --redact, on a full ("share all records") export: everything not on the
allowlist is dropped, every lat/lon at any depth is rounded to 3 decimals, every `loc` becomes
{lat, lon, acc, age_s}, ids are keyed pseudonyms (one random 32-byte secret per run, never stored),
free text goes through redact.py (a copy of docs/ekonomi-v2/redact.py), KuryePlus's own panel
lines are removed. The result is held in memory; the raw copy is never written anywhere.

Two deliberate extras over the WP3 table (they only remove more, never keep more):
* panel lines are removed BEFORE Redactor.lines, so a panel line can never shift its label state
  (e.g. be taken as the address line while the real address passes as free text);
* customer strings that the export itself names (trip/offer `details`, `customer`,
  `customer_address`) are scrubbed from every free text that survives (click and notification-hint
  texts, actions, error messages, redacted screen lines), e.g. a chat notification titled with the
  customer's name.
"""
import csv
import io
import json
import re
import secrets
import time
import unicodedata
from collections import Counter

import kl_export as K
from redact import Redactor

PANEL_RES = [  # private copy of the WP5 OverlayText formats (WP9 switches the Kotlin side to OverlayText.isOwn)
    re.compile(r"^[●•·]\s*[0-9]+(?:[.,][0-9]+)?\s*km$", re.I),
    re.compile(r"^(?:wolt|bolt(?:\s*food)?)\s*[·•●]\s*\S.*$", re.I),
    re.compile(r"^(?:bugün|bugun|bu\s*gün|bu\s*gun)\s*:\s*[0-9]+\s*(?:sipariş|siparis|sifariş|sifaris)$", re.I),
]
OBS_KEEP = ["pkg", "at", "stage", "complete", "source", "gross", "km", "min_lo", "min_hi", "stops", "quantity", "restaurant",
            "restaurant_address", "ready_in", "pickup_clock", "dropoff_clock", "travel_eta", "arrived", "ready", "delivered",
            "deliver_control"]
OFFER_KEEP = ["id", "pkg", "day", "at", "battery_pct", "lat", "lon", "gps_accuracy_m", "gps_age_ms", "scooter_battery_pct",
              "scooter_observed_at", "scooter_source", "session_id", "last_seen_at", "decline_click_at",
              "boost_value", "boost_unit", "boost_source", "boost_seen_at",
              "weather_source", "weather_fetched_at", "weather_temp_c", "weather_feels_c", "weather_rain_mm", "weather_rain_pct",
              "weather_wind_kmh", "weather_gust_kmh", "weather_code",
              "fee_at", "fee_gap_ms", "fee_ocr_ms", "notified_at"]
TRIP_KEEP = ["id", "pkg", "day", "accepted_at", "phase", "completed_at", "expected_orders", "distance_m", "used_wh", "recovered_wh",
             "energy_covered_ms", "energy_shared", "tip", "commission_pct", "price_is_net", "missed_offer", "pickup_intent_at",
             "finish_intent_at", "deliver_seen_at", "session_id",
             "delivered_inferred_at", "delivered_inferred_by", "delivered_inferred_reason", "deliver_spinner_at",
             "payment_method", "payment_source", "cash_amount_azn", "payment_at", "tip_source", "cash_no_change", "cash_change_azn", "cash_checked_at",
             "verified_by", "verified_at", "cash_collected_at", "cash_collected_azn", "extra_wait_azn", "platform_tip_azn", "energy_partial_ms"]
PICKUP_KEEP = ["restaurant", "address", "ready_at", "arrived_at", "picked_at", "arrival_source", "ready_observed_at", "ready_hint_at",
               "preparing_at", "reminder_at"]
CUSTOMER_KEEP = ["arrived_at", "delivered_at", "coordinate_source", "reminder_at", "details_at", "deadline_clock", "deadline_at", "lat", "lon", "arrival_source"]
ORDER_EVENT_KEEP = ["t", "type", "pkg", "offer_id", "id", "reason", "session_id", "gross", "net", "total_min", "wait_min", "km",
                    "restaurant", "phone_battery_pct", "loc"]
SCREEN_KEEP = ["t", "pkg", "window", "bounds", "source", "offerLike", "stage", "phone_battery_pct", "loc"]
NOTIF_KEEP = ["t", "posted", "ev", "pkg", "ongoing", "channel", "loc"]
ALL_KEYS = {"platforms.jsonl", "online-sessions.jsonl"}
DETAIL_KEYS = ["name", "address", "note", "instructions", "building", "entrance", "floor", "apartment", "door_code", "address_details", "phone"]


def is_panel_line(s):
    t = re.sub(r"\s+", " ", (s or "").strip())
    return any(r.match(t) for r in PANEL_RES)


def parser_norm(s):
    """CourierParser.norm: lowercase, ə->e, ı->i, ё->е, strip combining marks, collapse spaces."""
    s = (s or "").lower().replace("ə", "e").replace("ı", "i").replace("ё", "е")
    s = "".join(ch for ch in unicodedata.normalize("NFD", s) if not unicodedata.combining(ch))
    return re.sub(r"\s+", " ", s).strip()


READY_RES = [re.compile(r"ready in\s*([0-9]+)\s*min"), re.compile(r"ready for pickup|order is ready|is ready|hazirdir")]
MONEY_RE = re.compile(r"₼\s*[0-9]|[0-9]+(?:[.,][0-9]{1,2})?\s*(?:₼|azn)(?![a-z])|(?<![a-z])azn\s*[0-9]")
OFFER_WORDS = re.compile(r"new (?:order|delivery|offer|task|request)|yeni (?:sifaris|siparis|teklif|sorgu)|incoming (?:order|delivery)|"
                         r"delivery request|order request|offer|teklif|новый заказ")


def notif_hint(texts):
    n = parser_norm("\n".join(t for t in texts if isinstance(t, str)))
    if any(r.search(n) for r in READY_RES):
        return "ready"
    if MONEY_RE.search(n) or OFFER_WORDS.search(n):
        return "offer"
    return "other"


class Anonymizer:
    def __init__(self, secret=None):
        self._secret = secret if secret is not None else secrets.token_bytes(32)
        self.r = Redactor(self._secret)
        self.counts = Counter()
        self.known = []  # customer strings named by this export (scrubbed from surviving free text)
        self._index = None

    # --- pseudonyms and text --------------------------------------------------------------
    def pseudo_id(self, v):
        if not isinstance(v, str):
            return None
        if v.startswith("#"):
            return "#" + self.r.shape("id", v[1:])
        return self.r.shape("id", v)

    def pseudo_customer(self, v):
        """One pseudonym per customer key; order-id-like keys keep the id shape so they join with `ids`."""
        if not isinstance(v, str) or not v:
            return None
        if re.fullmatch(r"#?[A-Za-z0-9]{3,12}", v):
            return self.pseudo_id(v)
        return "cust-" + self.r.h("customer", v, 8)

    def learn(self, values):
        for v in values:
            if not isinstance(v, str):
                continue
            for part in [v] + re.split(r"[\n,;]+", v):
                p = part.strip()
                if len(p) >= 4 and not re.fullmatch(r"[0-9\s.+()-]{0,6}", p):
                    self.known.append(p)
        self._index = None

    def scrub(self, s):
        """Replace every known customer string (case-insensitive, anywhere in the text) by [pii]."""
        if not isinstance(s, str) or not self.known:
            return s
        if self._index is None:
            self._index = {}
            for k in sorted(set(self.known), key=len, reverse=True):
                self._index.setdefault(k.lower()[:4], []).append(k)
        low = s.lower()
        cands = []
        for i in range(len(low) - 3):
            hit = self._index.get(low[i:i + 4])
            if hit:
                cands.extend(hit)
        if not cands:
            return s
        out = s
        for k in sorted(set(cands), key=len, reverse=True):
            out = re.sub(re.escape(k), "[pii]", out, flags=re.I)
        if out != s:
            self.counts["known_pii_scrubbed"] += 1
        return out

    def free(self, s):
        # scrub before free() too: masking a door code inside a note must not hide the rest of the note
        return self.scrub(self.r.free(self.scrub(s))) if isinstance(s, str) else s

    # --- geometry -------------------------------------------------------------------------
    def geo(self, x):
        if isinstance(x, dict):
            out = {}
            for k, v in x.items():
                if k == "loc" and isinstance(v, dict):
                    out[k] = self.loc(v)
                elif k in ("lat", "lon") and K.num(v) is not None:
                    out[k] = round(K.num(v), 3)
                else:
                    out[k] = self.geo(v)
            return out
        if isinstance(x, list):
            return [self.geo(v) for v in x]
        return x

    def loc(self, v):
        lat, lon, acc, age = K.num(v.get("lat")), K.num(v.get("lon")), K.num(v.get("acc")), K.num(v.get("age_s"))
        return {"lat": round(lat, 3) if lat is not None else None, "lon": round(lon, 3) if lon is not None else None,
                "acc": int(round(acc)) if acc is not None else None, "age_s": int(round(age)) if age is not None else None}

    # --- record rules (RecordJson) ---------------------------------------------------------
    def observation(self, d):
        if not isinstance(d, dict):
            return None
        out = {k: d[k] for k in OBS_KEEP if k in d}
        if isinstance(d.get("ids"), list):
            out["ids"] = [self.pseudo_id(i) for i in d["ids"] if isinstance(i, str)]
        return out

    def offer(self, o):
        if not isinstance(o, dict):
            return None
        out = {k: o[k] for k in OFFER_KEEP if k in o}
        if "data" in o:
            out["data"] = self.observation(o.get("data"))
        return out

    def trip(self, t):
        if not isinstance(t, dict):
            return None
        out = {k: t[k] for k in TRIP_KEEP if k in t}
        if "offer" in t:
            out["offer"] = self.offer(t.get("offer"))
        if "latest" in t:
            out["latest"] = self.observation(t.get("latest"))
        for k in ("current_customer", "deliver_seen_for"):
            if t.get(k) is not None:
                out[k] = self.pseudo_customer(t[k])
        if isinstance(t.get("pickups"), list):
            ps = []
            for p in t["pickups"]:
                if not isinstance(p, dict):
                    continue
                q = {k: p[k] for k in PICKUP_KEEP if k in p}
                if p.get("note") is not None:
                    q["note"] = self.free(p["note"])
                if p.get("code") is not None:
                    q["code"] = self.r.shape("code", str(p["code"]))
                if isinstance(p.get("items"), list):
                    q["itemsCount"] = len(p["items"])
                ps.append(q)
            out["pickups"] = ps
        if isinstance(t.get("customers"), list):
            cs = []
            for c in t["customers"]:
                if not isinstance(c, dict):
                    continue
                q = {k: c[k] for k in CUSTOMER_KEEP if k in c}
                if c.get("id") is not None:
                    q["id"] = self.pseudo_customer(c["id"])
                cs.append(q)
            out["customers"] = cs
        return out

    # --- per-file rules -------------------------------------------------------------------
    def screens(self, row):
        out = {k: row[k] for k in SCREEN_KEEP if k in row}
        if isinstance(row.get("texts"), list):
            texts = [t for t in row["texts"] if isinstance(t, str)]
            own = [t for t in texts if is_panel_line(t)]
            self.counts["panel_lines_removed"] += len(own)
            kept = [t for t in texts if not is_panel_line(t)]
            red = self.r.lines(row.get("pkg") or "", kept)
            out["texts"] = [self.scrub(t) for t in red if not is_panel_line(t)]
        if isinstance(row.get("actions"), list):
            out["actions"] = [self.free(a) for a in row["actions"] if isinstance(a, str) and not is_panel_line(a)]
        return out

    def events(self, row):
        typ = row.get("type")
        out = {k: row[k] for k in ("t", "type", "loc") if k in row}
        if typ == "click":
            out.update({k: row[k] for k in ("pkg",) if k in row})
            if "text" in row:
                out["text"] = self.free(row["text"])
        elif typ == "notification_hint":
            out.update({k: row[k] for k in ("pkg", "observed_at") if k in row})
            if "text" in row:
                out["text"] = self.free(row["text"])
        elif typ == "customer_coordinate_unavailable":
            if "trip" in row:
                out["trip"] = row["trip"]
            if row.get("customer") is not None:
                out["customer"] = self.pseudo_customer(row["customer"])
        elif typ == "cancellation_user_reported":
            out.update({k: row[k] for k in ("pkg", "platformActionPerformed") if k in row})
        elif isinstance(typ, str) and (typ.startswith("shift") or typ.startswith("gps")):
            out.update({k: v for k, v in row.items() if k not in out and isinstance(v, (int, float, bool))})
        else:
            out.update({k: row[k] for k in ("pkg",) if k in row})
        return out

    def notifs(self, row):
        out = {k: row[k] for k in NOTIF_KEEP if k in row}
        out["hint"] = notif_hint([row.get(k) for k in ("title", "text", "big", "sub")])
        return out

    def orders(self, row):
        if row.get("type") == "record_snapshot":
            out = {k: row[k] for k in ("t", "type") if k in row}
            out["record"] = self.trip(row.get("record"))
            return out
        out = {k: row[k] for k in ORDER_EVENT_KEEP if k in row}
        if "offer" in row:
            out["offer"] = self.offer(row.get("offer"))
        if "record" in row:
            out["record"] = self.trip(row.get("record"))
        return out

    def errors(self, row):
        out = {k: row[k] for k in ("t", "source") if k in row}
        if "error" in row:
            e = self.free(row["error"]) if isinstance(row["error"], str) else None
            out["error"] = e[:200] if e is not None else None
        return out

    def rule(self, name):
        if name == "screens.jsonl":
            return self.screens
        if name == "events.jsonl":
            return self.events
        if name == "notifs.jsonl":
            return self.notifs
        if name == "orders.jsonl":
            return self.orders
        if name == "errors.jsonl":
            return self.errors
        if name in ALL_KEYS or name.startswith("economics-") or name.startswith("scooter"):
            return lambda row: dict(row)
        return None

    def gps_csv(self, text):
        out = [K.GPS_HEADER]
        for rec in csv.reader(io.StringIO(text)):
            if not rec or rec[0] == "t":
                continue
            try:
                lat, lon = round(float(rec[1]), 3), round(float(rec[2]), 3)
            except (ValueError, IndexError):
                self.counts["gps_rows_dropped"] += 1
                continue
            out.append(",".join([rec[0], repr(lat), repr(lon)] + rec[3:]))
        return out

    # --- whole export ---------------------------------------------------------------------
    def collect_known(self, exp):
        vals = []
        def details(d):
            if isinstance(d, dict):
                vals.extend(d.get(k) for k in DETAIL_KEYS)
        def obs(o):
            if isinstance(o, dict):
                details(o.get("customer"))
                vals.append(o.get("customer_address"))
        def trip(t):
            if not isinstance(t, dict):
                return
            for c in t.get("customers") or []:
                if isinstance(c, dict):
                    details(c.get("details"))
                    cid = c.get("id")
                    if isinstance(cid, str) and cid.startswith("name:"):
                        vals.append(cid[5:])
            obs(t.get("latest"))
            if isinstance(t.get("offer"), dict):
                obs(t["offer"].get("data"))
        for _, row in exp.jsonl("orders.jsonl"):
            trip(row.get("record"))
            if isinstance(row.get("offer"), dict):
                obs(row["offer"].get("data"))
        self.learn(vals)

    def export(self, exp, version_name=None, now_ms=None):
        """Raw K.Export -> {path: bytes} of the anonymous copy, with ANALIZ_ANONIM.json."""
        self.collect_known(exp)
        files, counts, days = {}, Counter(), set()
        for (day, name), text in sorted(exp.day_files.items()):
            if name == "gps.csv":
                lines = self.gps_csv(text)
                rows = len(lines) - 1
            else:
                fn = self.rule(name)
                if fn is None:
                    self.counts["files_excluded"] += 1
                    continue
                lines = []
                for line in text.splitlines():
                    if not line.strip():
                        continue
                    try:
                        row = json.loads(line)
                    except ValueError:
                        self.counts["damaged_lines_dropped"] += 1
                        continue
                    if not isinstance(row, dict):
                        continue
                    lines.append(json.dumps(self.geo(fn(row)), ensure_ascii=False, separators=(",", ":")))
                rows = len(lines)
            files["%s/%s" % (day, name)] = ("\n".join(lines) + "\n").encode("utf-8")
            counts[name] += rows
            days.add(day)
        if "restaurants.json" in exp.root_files:
            try:
                files["restaurants.json"] = json.dumps(self.geo(json.loads(exp.root_files["restaurants.json"])), ensure_ascii=False).encode("utf-8")
                counts["restaurants.json"] = 1
            except ValueError:
                self.counts["files_excluded"] += 1
        self.counts["files_excluded"] += len(exp.ignored)
        marker = {"schema": K.MARKER_SCHEMA, "app": "com.kuryelog.app", "versionName": version_name,
                  "createdAtMs": int(now_ms if now_ms is not None else time.time() * 1000), "days": sorted(days),
                  "files": dict(sorted(counts.items())),
                  "redaction": {"locDecimals": 3, "secretStored": False, "panelLinesRemoved": True},
                  "producer": "tools/analysis/anonymize.py"}
        files[K.MARKER] = json.dumps(marker, ensure_ascii=False, indent=1).encode("utf-8")
        return files


def anonymize(exp, version_name=None):
    """Convenience: raw export -> (anonymous K.Export, counters). The secret dies with the Anonymizer."""
    a = Anonymizer()
    files = a.export(exp, version_name)
    return K.from_memory(files, "anon:" + exp.source), dict(a.counts), files


def write_zip(files, path):
    import zipfile
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            z.writestr(name, files[name])
