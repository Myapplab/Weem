"""Synthetic KuryeLog export with known answers (selftest input only; NOT real data, no real people).

The generator simulates one courier with the row builders of KuryeLog 0.14.0 (orders.jsonl events with
RecordJson trip/offer/observation records, events/notifs/screens/platforms/errors rows, gps.csv,
online-sessions.jsonl schema 1 then 2) and, from `v2_from_day` on, the v2 logs of SPEC 2.1.0 section 17.2
(economics-offers/idle/legs/penalties). It plants customer data (names, phones, addresses, notes, door
codes, `name:` customer ids, panel lines inside OCR texts) so the anonymizer can be checked.

Known answers (`truth`): fee lines (Theil-Sen), latent idle-spell durations per zone x hour block (Kaplan-Meier),
free hours and offers per cell (rates), online hours per Baku day / platform / app combination, net per day,
H2 delays and shares, H4 far-by-road count, H6 penalty and banner counts, data-quality counts.

python3 -I synth.py OUT_DIR [--days 30] [--seed 7]      (day folders)
python3 -I synth.py OUT.zip --zip [--days 30] [--seed 7] (one ZIP like the app's full export)
"""
import bisect
import json
import math
import os
import random
import sys
import zipfile
from collections import defaultdict
from datetime import datetime, timedelta

sys.dont_write_bytecode = True  # keep the checkout clean (no __pycache__ next to the tool)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kl_export as K  # noqa: E402

PKG = {"wolt": "com.wolt.courierapp", "bolt": "com.bolt.deliverycourier"}
PANEL_NAME = {"wolt": "Wolt", "bolt": "Bolt Food"}
COMMISSION = {"wolt": 20.0, "bolt": 15.0}
# invented test lines with known answers for the Theil-Sen check; not a platform tariff, never used by the analysis
FEE = {"wolt": {"a": 1.05, "b": 0.41, "x": "km"}, "bolt": {"a": 0.80, "b": 0.12, "x": "min"}}
SPELL_MEDIAN_BASE = {"B0": 20.0, "B1": 14.0, "B2": 8.0, "B3": 12.0, "B4": 7.0, "B5": 12.0}
SPELL_ZONE_FACTOR = {"Z1": 1.0, "Z2": 1.25, "Z3": 1.8, "Z4": 2.5}
DECLINE_RATE = {"wolt": 2.4, "bolt": 1.6}   # per free hour x K.HOUR_BLOCK_MULT x K.ZONE_RATE_MULT
TRIP_OFFER_RATE = 1.0                         # per app per hour while on a trip (declined)
ROAD = 1.3
FAR_ROAD = 2.2
SPEED_KMH = 16.0
START = datetime(2026, 9, 1, tzinfo=K.BAKU)

FIRST = ["Aysel", "Kamran", "Leyla", "Rauf", "Nigar", "Tural", "Sevinc", "Elvin"]
LAST = ["Testova", "Sintetikov", "Numunova", "Uydurmayev", "Saxtayeva", "Yalanov", "Modelova", "Fiktivli"]
STREETS = ["Sintetik küçəsi", "Uydurma prospekti", "Nümunə döngəsi", "Saxta bulvarı"]
NOTES = ["Zəngi basmayın, uşaq yatır", "Qapının yanında qoyun", "Meet me at the blue gate", "Liftlə 6-cı mərtəbəyə qalxın"]
RESTAURANTS = [("Izmir Doner Tantuni", 0.9, 30), ("Test Kabab Evi", 1.6, 110), ("Nümunə Pizza", 2.2, 200), ("Sintetik Burger", 3.1, 290),
               ("Model Sushi", 1.2, 250), ("Plov Mərkəzi", 2.8, 60), ("Çay Evi Test", 3.8, 160), ("Dönər Lab", 0.5, 330),
               ("Fake Lahmacun", 4.2, 20), ("Demo Qutab", 2.0, 140), ("Sample Salad", 3.4, 230), ("Mock Kebab", 1.8, 300)]


def offset(lat, lon, km, bearing):
    b = math.radians(bearing)
    return lat + km / 111.32 * math.cos(b), lon + km / (111.32 * math.cos(math.radians(lat))) * math.sin(b)


def ts(ms):
    d = K.local(ms)
    return d.strftime("%Y-%m-%dT%H:%M:%S.") + "%03d" % (int(ms) % 1000) + "+0400"


def r2(x):
    return float("%.2f" % x)


def put(d, **kw):
    """Android org.json put(key, null) removes the key: never write nulls."""
    for k, v in kw.items():
        if v is not None:
            d[k] = v
    return d


class Synth:
    def __init__(self, days=30, seed=7, v2_from_day=22, schema1_days=5):
        self.rng = random.Random(seed)
        self.days = days
        self.v2_from = v2_from_day
        self.schema1_days = schema1_days
        self.rows = []          # (write_ms, file, payload)
        self.seq = 0
        self.rest = []
        for name, km, bearing in RESTAURANTS:
            lat, lon = offset(K.HUB_DEFAULT[0], K.HUB_DEFAULT[1], km, bearing)
            short = K.olc_encode(lat, lon, 11)[4:]
            self.rest.append({"name": name, "lat": lat, "lon": lon, "addr": "%s Baku, Baku, AZ1000" % short})
        self.path = []          # (t0, t1, p0, p1) piecewise-linear true position, appended in time order
        self.path_t0 = []
        self.truth = {"fee": FEE, "spells": [], "free": defaultdict(lambda: [0.0, 0]), "offers_total": 0,
                      "offers_during_trip": 0, "ghost": 0, "noclick": 0, "unreadable": defaultdict(int),
                      "screens": 0, "ocr_screens": 0, "far_by_road": 0, "penalties": 0, "banners": 0,
                      "net_by_day": defaultdict(float), "trips_by_day": defaultdict(int), "midnight_trips": 0,
                      "delivered_to_next_offer": [], "drop_to_pickup_km": [], "pii": set(), "sessions": [],
                      "performance": 0, "unknown_schema_rows": 0}
        self.offers = []        # every presented offer (truth): (at, app, during_trip)
        self.trips = []

    # --- helpers ------------------------------------------------------------------------
    def emit(self, at, name, payload):
        self.rows.append((int(at), name, payload))

    def nid(self, kind, at):
        self.seq += 1
        return "%s-%d-%d" % (kind, int(at), self.seq)

    def pos_at(self, t):
        i = bisect.bisect_right(self.path_t0, t) - 1
        if i < 0:
            return self.path[0][2] if self.path else K.HUB_DEFAULT
        t0, t1, p0, p1 = self.path[i]
        if t >= t1 or t1 == t0:
            return p1 if t >= t1 else p0
        f = (t - t0) / (t1 - t0)
        return p0[0] + (p1[0] - p0[0]) * f, p0[1] + (p1[1] - p0[1]) * f

    def loc(self, t):
        lat, lon = self.pos_at(t)
        return {"lat": lat + self.rng.uniform(-1e-4, 1e-4), "lon": lon + self.rng.uniform(-1e-4, 1e-4), "acc": 8.0, "age_s": 3}

    def stay(self, t0, t1, p):
        self.move(t0, t1, p, p)

    def move(self, t0, t1, p0, p1):
        if self.path_t0 and t0 < self.path_t0[-1]:
            t0 = self.path_t0[-1]
        self.path.append((t0, max(t0, t1), p0, p1))
        self.path_t0.append(t0)

    def person(self):
        f, l = self.rng.choice(FIRST), self.rng.choice(LAST)
        street = self.rng.choice(STREETS)
        num = self.rng.randint(10, 99)
        apt = self.rng.randint(10, 90)
        code = self.rng.randint(1000, 9999)
        phone = "+994 55 %03d %02d %02d" % (self.rng.randint(100, 999), self.rng.randint(10, 99), self.rng.randint(10, 99))
        note = self.rng.choice(NOTES)
        p = {"name": "%s %s" % (f, l), "short": "%s %s." % (f, l[0]), "address": "%s %d, mənzil %d, Bakı" % (street, num, apt),
             "note": note + ". Domofon kodu: %d" % code, "phone": phone, "apartment": str(apt), "door_code": str(code),
             "floor": str(self.rng.randint(2, 12))}
        self.truth["pii"].update([p["name"], "%s %s." % (f, l[0]), l, "%s %d" % (street, num), note, phone, phone.replace(" ", ""),
                                  phone[5:], "kodu: %d" % code, "mənzil %d" % apt])
        return p

    # --- sessions -----------------------------------------------------------------------
    def shift_plan(self, d):
        base = int((START + timedelta(days=d)).timestamp() * 1000)
        H = 3600000
        if d % 5 == 4:   # evening shift crossing midnight, two apps
            sess = {"wolt": [[base + 18 * H, base + 25 * H + H // 2]], "bolt": [[base + 19 * H, base + 23 * H]]}
        else:            # day shift with a break; Bolt part time (two-app overlap)
            sess = {"wolt": [[base + 10 * H + H // 2, base + 15 * H + H // 2], [base + 16 * H + H // 2, base + 22 * H]],
                    "bolt": [[base + 12 * H, base + 14 * H + H // 2], [base + 18 * H, base + 21 * H]]}
            if d % 3 == 2:
                sess["bolt"] = []  # Wolt-only day
        return sess

    @staticmethod
    def online(sess, app, t):
        return any(s <= t < e for s, e in sess[app])

    def union_on(self, sess, t):
        return any(self.online(sess, a, t) for a in sess)

    @staticmethod
    def union_intervals(sess):
        iv = sorted([s, e] for a in sess for s, e in sess[a] if e > s)
        out = []
        for s, e in iv:
            if out and s <= out[-1][1]:
                out[-1][1] = max(out[-1][1], e)
            else:
                out.append([s, e])
        return out

    def union_off_after(self, sess, t):
        for s, e in self.union_intervals(sess):
            if s <= t < e:
                return e
        return t

    def union_next_on(self, sess, t):
        starts = [s for s, e in self.union_intervals(sess) if s >= t]
        return min(starts) if starts else None

    def extend(self, sess, app, t, until):
        for iv in sess[app]:
            if iv[0] <= t < iv[1] and iv[1] < until:
                iv[1] = until

    # --- screens / offers ---------------------------------------------------------------
    def screen(self, at, app, stage, texts, actions, panel=None):
        ocr = self.rng.random() < 0.2
        src = "ocr" if ocr else "accessibility"
        if ocr and panel:
            texts = list(texts) + panel
        row = {"t": ts(at), "pkg": PKG[app], "window": self.rng.randint(1, 900), "bounds": "0 0 1080 2340", "source": src,
               "offerLike": stage == "OFFER", "stage": stage, "texts": texts, "actions": actions}
        if not ocr:
            row["nodes"] = [{"text": t, "cls": "android.widget.TextView"} for t in texts[:5]]
        row["phone_battery_pct"] = 80
        row["loc"] = self.loc(at)
        self.emit(at, "screens.jsonl", row)
        self.truth["screens"] += 1
        self.truth["ocr_screens"] += int(ocr)

    def card(self, app, at, pos, far=False):
        """What the offer card shows (fee per the known fee line) plus the hidden customer."""
        rest = sorted(self.rest, key=lambda r: K.haversine_km(pos[0], pos[1], r["lat"], r["lon"]))[self.rng.randint(0, 2)]
        cust = None
        for _ in range(20):
            c = offset(rest["lat"], rest["lon"], self.rng.uniform(1.0, 5.0), self.rng.uniform(0, 360))
            if K.haversine_km(K.HUB_DEFAULT[0], K.HUB_DEFAULT[1], c[0], c[1]) < 9.0:
                cust = c
                break
        cust = cust or (rest["lat"], rest["lon"] + 0.015)
        air_a = K.haversine_km(pos[0], pos[1], rest["lat"], rest["lon"])
        air_d = K.haversine_km(rest["lat"], rest["lon"], cust[0], cust[1])
        f = FAR_ROAD if far else ROAD
        road_km = (air_a + air_d) * f
        noise = self.rng.gauss(0, 0.10)
        boost = self.rng.random() < 0.04
        if app == "wolt":
            km = round(road_km, 1)
            lo = int(round(4 + 2.6 * km))
            gross = FEE["wolt"]["a"] + FEE["wolt"]["b"] * km + noise
            shown = {"km": km, "min_lo": lo, "min_hi": lo + 8, "stops": 2}
        else:
            m = int(round(air_a * ROAD / 15.0 * 60 + 1)) + int(round(air_d * ROAD / 15.0 * 60 + 1))
            gross = FEE["bolt"]["a"] + FEE["bolt"]["b"] * m + noise
            shown = {"min_lo": m, "min_hi": m}
        if boost:
            gross *= 1.6
        gross = r2(gross)
        if self.rng.random() < 0.03:
            gross = None
            self.truth["unreadable"][app] += 1
        return {"rest": rest, "cust": cust, "gross": gross, "shown": shown, "road_km": road_km, "air_a": air_a, "air_d": air_d,
                "far": far, "boost": boost}

    def obs(self, app, at, stage, c, p=None, raw=None, ids=None, delivered=False):
        sh = c["shown"]
        d = {"pkg": PKG[app], "at": at, "stage": stage, "complete": True, "source": "accessibility",
             "raw": raw or [], "actions": ["Accept", "Decline"] if stage == "OFFER" else [], "ids": ids or [],
             "customer": put({}, name=p["name"] if p else None, address=p["address"] if p else None,
                             note=p["note"] if p else None, floor=p["floor"] if p else None,
                             apartment=p["apartment"] if p else None, door_code=p["door_code"] if p else None,
                             address_details=("Blok B, mənzil %s" % p["apartment"]) if p else None) if p else {},
             "arrived": False, "ready": False, "delivered": delivered, "deliver_control": False}
        put(d, gross=c["gross"], km=sh.get("km"), min_lo=sh.get("min_lo"), min_hi=sh.get("min_hi"), stops=sh.get("stops"),
            quantity=1, restaurant=c["rest"]["name"], restaurant_address=c["rest"]["addr"],
            customer_address=p["address"] if (p and app == "wolt") else None, ready_in=3 if stage == "OFFER" else None)
        return d

    def offer_texts(self, app, c, p):
        fee = ("₼%.2f" % c["gross"]) if c["gross"] is not None else "₼—"
        sh = c["shown"]
        if app == "wolt":
            return ["Ready in 3 min", "Decline", fee, "2 stops (%.1f km) • %d–%d min" % (sh["km"], sh["min_lo"], sh["min_hi"]),
                    c["rest"]["name"], c["rest"]["addr"], "Customer drop-off", p["address"], "Accept"]
        return ["New order", fee, "~%d min" % sh["min_lo"], c["rest"]["name"], c["rest"]["addr"], "Accept", "Decline"]

    def offer_record(self, oid, app, at, c, p, pos):
        o = {"id": oid, "pkg": PKG[app], "day": K.baku_day(at), "at": at, "battery_pct": 81,
             "data": self.obs(app, at, "OFFER", c, p, raw=self.offer_texts(app, c, p)),
             "lat": pos[0] + self.rng.uniform(-1e-4, 1e-4), "lon": pos[1] + self.rng.uniform(-1e-4, 1e-4), "gps_accuracy_m": 8.0,
             "gps_age_ms": 2000, "session_id": "%s-sess" % app, "last_seen_at": at + 5000}
        return o

    def order(self, at, typ, app, offer_id=None, trip=None, reason=None, offer=None):
        j = put({"t": ts(at), "type": typ, "pkg": PKG[app]}, offer_id=offer_id, id=trip["id"] if trip else None, reason=reason,
                session_id="%s-%d" % (app, at // 3600000))
        if trip:
            rec = self.trip_record(trip)
            j["record"] = rec
            g = trip["card"]["gross"] if trip["card"] else None
            put(j, gross=g, net=(g * (1 - COMMISSION[trip["app"]] / 100.0) + trip["tip"]) if g is not None else None,
                total_min=((trip["completed_at"] - trip["accepted_at"]) / 60000.0) if trip.get("completed_at") else None,
                wait_min=trip.get("wait_min", 0.0), km=trip.get("distance_m", 0.0) / 1000.0,
                restaurant=trip["card"]["rest"]["name"] if trip["card"] else trip.get("ghost_rest"))
        if offer:
            j["offer"] = offer
        j["loc"] = self.loc(at)
        j["phone_battery_pct"] = 77
        self.emit(at, "orders.jsonl", j)

    def trip_record(self, t):
        p, c = t["person"], t["card"]
        cust_id = t["cust_id"]
        customers = []
        if p:
            customers.append(put({"id": cust_id, "details": put({}, name=p["name"], address=p["address"], note=p["note"],
                                                                 floor=p["floor"], apartment=p["apartment"], door_code=p["door_code"],
                                                                 check_id=False, address_details="Blok B, mənzil %s" % p["apartment"]),
                                  "details_at": t["accepted_at"]},
                                 arrived_at=t.get("at_customer"), delivered_at=t.get("completed_at"),
                                 lat=c["cust"][0] if t.get("geocoded") else None, lon=c["cust"][1] if t.get("geocoded") else None,
                                 coordinate_source="geocoder" if t.get("geocoded") else None))
        pk = [put({"restaurant": c["rest"]["name"] if c else t.get("ghost_rest"), "address": c["rest"]["addr"] if c else None,
                   "items": ["1 x Lahmacun", "1 x Ayran"]}, arrived_at=t.get("arrived_at"), picked_at=t.get("picked_at"),
                  arrival_source="screen" if t.get("arrived_at") else None, note="Restoran həyətin içindədir" if c else None,
                  code=("%d" % (100 + self.seq % 900)) if c else None)]
        rec = put({"id": t["id"], "pkg": PKG[t["app"]], "day": K.baku_day(t["accepted_at"]), "accepted_at": t["accepted_at"],
                   "phase": t["phase"], "expected_orders": 1, "distance_m": t.get("distance_m", 0.0), "used_wh": 0.0,
                   "recovered_wh": 0.0, "energy_covered_ms": 0, "energy_shared": False, "tip": t["tip"],
                   "commission_pct": COMMISSION[t["app"]], "price_is_net": False, "missed_offer": t["offer"] is None,
                   "pickups": pk, "customers": customers},
                  offer=t["offer"], completed_at=t.get("completed_at"), current_customer=cust_id if p else None,
                  latest=self.obs(t["app"], t["accepted_at"], "DROPOFF", c, p, raw=["Dropoff to", p["name"], p["address"]],
                                  ids=[cust_id] if not cust_id.startswith("name:") else []) if (c and p) else None,
                  deliver_seen_for=cust_id if t.get("completed_at") else None, deliver_seen_at=t.get("completed_at"),
                  session_id="%s-sess" % t["app"])
        return rec

    def v2_offer(self, at, oid, app, first, decision, c, during, pos, counts):
        if K.baku_day(at) < self.v2_day:
            return
        sh = c["shown"]
        row = {"schemaVersion": 1, "offerKey": oid, "pkg": PKG[app], "platform": app, "firstSeenAtMs": first, "lastSeenAtMs": at,
               "decisionKind": decision, "decisionAtMs": at, "duringTripId": during, "feeAzn": c["gross"],
               "feeBasis": "gross_before_park", "shown": {k: v for k, v in [("km", sh.get("km")), ("minLo", sh.get("min_lo")),
                                                                            ("minHi", sh.get("min_hi"))] if v is not None},
               "pickupZone": K.zone_of(c["rest"]["lat"], c["rest"]["lon"]), "dropoffZone": "unknown", "optional": False,
               "expiresAtMs": first + 60000, "expirySource": "documented_upper_bound", "countsAsDecline": counts, "verdictShadow": None}
        self.emit(at, "economics-offers.jsonl", row)

    # --- offers -------------------------------------------------------------------------
    def declined_offer(self, sess, app, x, pos, during=None, day_idx=0):
        c = self.card(app, x, pos)
        p = self.person()
        oid = self.nid("offer", x)
        self.truth["offers_total"] += 1
        self.truth["offers_during_trip"] += int(during is not None)
        self.offers.append((x, app, during is not None))
        self.screen(x, app, "OFFER", self.offer_texts(app, c, p), ["Accept", "Decline"],
                    panel=["● %.1f km" % (self.rng.uniform(1, 40)), "%s · %.2f₼ · %s" % (PANEL_NAME[app], c["gross"] or 0.0, c["rest"]["name"])])
        self.order(x, "offer", app, offer_id=oid, offer=self.offer_record(oid, app, x, c, p, pos))
        u = self.rng.random()
        if u < 0.6:
            self.emit(x + 6000, "events.jsonl", {"t": ts(x + 6000), "type": "click", "pkg": PKG[app], "text": "Decline", "loc": self.loc(x)})
            if app == "wolt":
                self.emit(x + 8000, "events.jsonl", {"t": ts(x + 8000), "type": "click", "pkg": PKG[app], "text": "Decline", "loc": self.loc(x)})
                self.order(x + 8000, "rejected", app, offer_id=oid, reason="courier_declined_confirmed")
            else:
                self.order(x + 10000, "rejected", app, offer_id=oid, reason="courier_declined")
            self.v2_offer(x + 9000, oid, app, x, "declined_tap_observed", c, during, pos, True)
            end = x + 10000
        elif u < 0.85:
            self.order(x + 60000, "rejected", app, offer_id=oid, reason="offer_to_idle_map")
            self.v2_offer(x + 60000, oid, app, x, "timeout_inferred", c, during, pos, True)
            end = x + 60000
        elif u < 0.95:
            self.order(x + 180000, "offer_unconfirmed", app, offer_id=oid, reason="offer_not_seen")
            self.v2_offer(x + 180000, oid, app, x, "disappeared_unknown", c, during, pos, True)
            end = x + 180000
        else:
            self.order(x + 15000, "rejected", app, offer_id=oid, reason="courier_declined_banner")
            self.v2_offer(x + 15000, oid, app, x, "declined_banner_observed", c, during, pos, True)
            self.truth["banners"] += 1
            if K.baku_day(x) >= self.v2_day:
                self.emit(x + 15000, "economics-penalties.jsonl", {"schemaVersion": 1, "platform": app, "kind": "declines_banner",
                                                                   "observedAtMs": x + 15000, "declinedCountShown": 1,
                                                                   "rawReasonRedacted": "1 deliveries declined"})
            end = x + 15000
        return end

    def maybe_penalty(self, sess, app, x, day_idx, pos):
        if app != "wolt" or day_idx % 10 != 6 or self.penalty_done.get(day_idx) or K.minute_of_day(x) < 13 * 60:
            return False
        for iv in sess["wolt"]:
            if iv[0] <= x < iv[1] and iv[1] - x > 45 * 60000:
                t0 = x + 20000
                until = t0 + 30 * 60000
                sess["wolt"].append([until, iv[1]])
                iv[1] = t0
                sess["wolt"].sort()
                self.penalty_done[day_idx] = True
                self.truth["penalties"] += 1
                self.screen(t0, "wolt", "OFFLINE", ["You're offline", "You have been set offline until %s for declining too many deliveries"
                                                    % K.clock(until), "Go online"], ["Go online"])
                self.emit(t0, "platforms.jsonl", {"pkg": PKG["wolt"], "stage": "OFFLINE", "multipliers": [], "offline_until": K.clock(until),
                                                  "t": ts(t0)})
                if K.baku_day(t0) >= self.v2_day:
                    self.emit(t0, "economics-penalties.jsonl", {"schemaVersion": 1, "platform": "wolt", "kind": "offline_for_declines",
                                                                "observedAtMs": t0, "offlineUntilMs": until, "countInWindowAtTrigger": 5,
                                                                "maxCountWithoutTriggerSoFar": 4, "acceptanceRate": None,
                                                                "rawReasonRedacted": "set offline until %s for declining too many deliveries" % K.clock(until)})
                return True
        return False

    def run_trip(self, sess, app, acc_at, pos, offer_at, click=True, during=False):
        far = app == "wolt" and self.rng.random() < 0.08
        c = self.card(app, offer_at, pos, far=far)
        p = self.person()
        oid = self.nid("offer", offer_at)
        self.truth["offers_total"] += 1
        self.truth["offers_during_trip"] += int(during)
        self.offers.append((offer_at, app, during))
        offer = self.offer_record(oid, app, offer_at, c, p, pos)
        self.screen(offer_at, app, "OFFER", self.offer_texts(app, c, p), ["Accept", "Decline"],
                    panel=["● %.1f km" % self.rng.uniform(1, 40), "Bugün: %d sipariş" % self.rng.randint(0, 20)])
        self.order(offer_at, "offer", app, offer_id=oid, offer=offer)
        if click:
            self.emit(acc_at - 1000, "events.jsonl", {"t": ts(acc_at - 1000), "type": "click", "pkg": PKG[app], "text": "Accept", "loc": self.loc(acc_at)})
        else:
            self.truth["noclick"] += 1
        f = FAR_ROAD if far else ROAD
        a_min = c["air_a"] * f / SPEED_KMH * 60 + 1
        d_min = c["air_d"] * f / SPEED_KMH * 60 + 1
        arrived = acc_at + int(a_min * 60000)
        picked = arrived + int(self.rng.uniform(2, 8) * 60000)
        at_c = picked + int(d_min * 60000)
        done = at_c + 90000
        rest = (c["rest"]["lat"], c["rest"]["lon"])
        self.move(acc_at, arrived, pos, rest)
        self.stay(arrived, picked, rest)
        self.move(picked, at_c, rest, c["cust"])
        self.stay(at_c, done, c["cust"])
        cust_id = ("name:" + p["short"]) if app == "bolt" else "".join(self.rng.choice("ABCDEFGHJKLMNPQRSTUVWXYZ23456789") for _ in range(5))
        t = {"id": self.nid("trip", acc_at), "app": app, "offer": offer, "card": c, "person": p, "accepted_at": acc_at, "phase": "TO_RESTAURANT",
             "tip": 1.0 if self.rng.random() < 0.1 else 0.0, "cust_id": cust_id, "geocoded": self.rng.random() < 0.5}
        self.order(acc_at, "accepted", app, offer_id=oid, trip=t, reason="active_screen")
        self.v2_offer(acc_at, oid, app, offer_at, "accepted_observed", c, None, pos, False)
        t["arrived_at"] = arrived
        t["phase"] = "WAITING"
        self.screen(arrived, app, "PICKUP", ["Pickup from", c["rest"]["name"], c["rest"]["addr"], "Notes", "Restoran həyətin içindədir",
                                             "Translate", "Collect order", "#" + cust_id[-3:] if app == "wolt" else "#408", p["short"],
                                             "ORDER DETAILS", "1 x Lahmacun", "Collect"], ["Collect"],
                    panel=["%s · %s · Restoranda bekleniyor" % (PANEL_NAME[app], c["rest"]["name"])])
        self.order(arrived, "restaurant_arrived", app, trip=t)
        if self.rng.random() < 0.5:
            nt = arrived + 60000
            self.emit(nt, "notifs.jsonl", put({"t": ts(nt), "posted": ts(nt), "ev": "posted", "pkg": PKG[app], "key": "0|%s|%d" % (PKG[app], self.seq),
                                               "ongoing": False, "channel": "orders", "title": "Order is ready for pickup",
                                               "text": "%s: order is ready" % c["rest"]["name"]}, loc=self.loc(nt)))
            self.emit(nt, "events.jsonl", {"t": ts(nt), "type": "notification_hint", "pkg": PKG[app],
                                           "text": "Order is ready for pickup\n%s: order is ready" % c["rest"]["name"], "observed_at": nt, "loc": self.loc(nt)})
        t["picked_at"] = picked
        t["phase"] = "TO_CUSTOMER"
        t["wait_min"] = (picked - arrived) / 60000.0
        self.order(picked, "picked_up", app, trip=t)
        drop_texts = (["Dropoff to", p["name"], p["address"], "Notes", p["note"], "Mertebe %s, tel %s" % (p["floor"], p["phone"]), "Translate",
                       "Order details", "Slide to confirm"] if app == "wolt" else
                      ["Drop off in 3 min", "%s #%s" % (p["short"], "8P6D2"), "Items (1)", "Address", p["address"], "Instructions", p["note"],
                       "Floor", p["floor"], "Arrived", "Slide to confirm"])
        self.screen(picked + 30000, app, "DROPOFF", drop_texts, ["Call", "Slide to confirm"],
                    panel=["● %.1f km" % self.rng.uniform(1, 40), "%s · %s · Müşteriye gidiliyor" % (PANEL_NAME[app], p["short"])])
        if self.rng.random() < 0.3:
            ct = picked + 60000
            self.emit(ct, "events.jsonl", {"t": ts(ct), "type": "click", "pkg": PKG[app], "text": p["name"], "loc": self.loc(ct)})
            self.emit(ct + 1000, "events.jsonl", {"t": ts(ct + 1000), "type": "click", "pkg": PKG[app], "text": "Call", "loc": self.loc(ct)})
        if self.rng.random() < 0.2:
            nt = picked + 120000
            self.emit(nt, "notifs.jsonl", {"t": ts(nt), "posted": ts(nt), "ev": "posted", "pkg": PKG[app], "key": "chat|%d" % self.seq,
                                           "ongoing": False, "channel": "chat", "title": p["name"], "text": p["note"], "big": p["address"]})
            self.emit(nt, "events.jsonl", {"t": ts(nt), "type": "notification_hint", "pkg": PKG[app],
                                           "text": "%s\n%s" % (p["name"], p["note"]), "observed_at": nt})
        if self.rng.random() < 0.05:
            self.emit(at_c, "errors.jsonl", {"t": ts(at_c), "source": "geocoder",
                                             "error": "geocoder: IOException: no result for %s (%s)" % (p["address"], p["phone"])})
            self.emit(at_c, "events.jsonl", {"t": ts(at_c), "type": "customer_coordinate_unavailable", "trip": t["id"], "customer": cust_id})
        t["at_customer"] = at_c
        t["phase"] = "FINISHED"
        t["completed_at"] = done
        t["distance_m"] = (c["air_a"] + c["air_d"]) * f * 1000
        self.order(done, "delivered", app, trip=t, reason="slider_completed")
        self.emit(done + 2000, "orders.jsonl", {"t": ts(done + 2000), "type": "record_snapshot", "record": self.trip_record(t)})
        if K.baku_day(done) >= self.v2_day:
            self.emit(done, "economics-legs.jsonl", {"schemaVersion": 1, "tripId": t["id"], "offerKey": oid, "pkg": PKG[app],
                                                      "acceptedAtMs": acc_at, "pickedUpAtMs": picked, "deliveredAtMs": done,
                                                      "totalTaskMin": (done - acc_at) / 60000.0, "shownM": c["shown"].get("min_lo"),
                                                      "shownMSource": "wolt_range_midpoint" if app == "wolt" else "bolt_button_total",
                                                      "readyInMin": 3, "legs": [], "stackedWith": None, "outcome": "delivered"})
        self.extend(sess, app, acc_at, done)
        if c["gross"] is not None:
            self.truth["net_by_day"][K.baku_day(acc_at)] += c["gross"] * (1 - COMMISSION[app] / 100.0) + t["tip"]
        self.truth["trips_by_day"][K.baku_day(acc_at)] += 1
        if K.baku_day(acc_at) != K.baku_day(done):
            self.truth["midnight_trips"] += 1
        if far:
            self.truth["far_by_road"] += 1
        self.trips.append(t)
        # offers while on the trip (declined), possibly one accepted right after delivery (back-to-back)
        b2b = None
        for a in sess:
            x = acc_at
            while True:
                x += int(self.rng.uniform(0.5, 1.5) / TRIP_OFFER_RATE * 3600000)
                if x >= done - 60000:
                    break
                if self.online(sess, a, x):
                    self.declined_offer(sess, a, x, self.pos_at(x), during=t["id"])
        if self.rng.random() < 0.12:
            cands = [a for a in sess if self.online(sess, a, done + 20000)]
            if cands:
                b2b = (self.rng.choice(cands), done - int(self.rng.uniform(1, 3) * 60000))
        return t, done, c["cust"], b2b

    def ghost(self, sess, app, x, pos):
        t = {"id": self.nid("trip", x), "app": app, "offer": None, "card": None, "person": None, "accepted_at": x, "phase": "TO_RESTAURANT",
             "tip": 0.0, "cust_id": "", "ghost_rest": "Restoran"}
        self.order(x, "accepted", app, trip=t, reason="active_screen_offer_missed")
        t["phase"] = "END_UNCONFIRMED"
        self.order(x + 90000, "end_unconfirmed", app, trip=t, reason="waiting_map_before_pickup")
        self.truth["ghost"] += 1

    # --- shift simulation ---------------------------------------------------------------
    def spell_cell(self, pos, t):
        return K.zone_of(pos[0], pos[1]), K.block_of_ms(t)

    def add_free(self, z, s, e, offers_in):
        t = s
        while t < e:
            dstart = K.day_start_ms(K.baku_day(t))
            nxt = e
            for _, lo, hi in K.HOUR_BLOCKS:
                bnd = dstart + hi * 60000
                if bnd > t:
                    nxt = min(nxt, bnd)
                    break
            b = K.block_of_ms(t)
            self.truth["free"][(z, b)][0] += (nxt - t) / 3600000.0
            t = nxt
        for x in offers_in:
            self.truth["free"][(z, K.block_of_ms(x))][1] += 1

    def shift(self, d):
        sess = self.shift_plan(d)
        self.cur_sess = sess
        self.penalty_done = getattr(self, "penalty_done", {})
        starts = [s for a in sess for s, e in sess[a]]
        t = min(starts)
        pos = offset(K.HUB_DEFAULT[0], K.HUB_DEFAULT[1], 3.5, 200)
        shift_start = t - 5 * 60000
        self.emit(shift_start, "events.jsonl", {"t": ts(shift_start), "type": "shift_start", "auto": False})
        self.stay(shift_start, t, pos)
        last_drop = None
        pending_b2b = None
        while True:
            if not self.union_on(sess, t):
                nt = self.union_next_on(sess, t)
                if nt is None:
                    break
                self.stay(t, nt, pos)
                t = nt
                last_drop = None
            z, b = self.spell_cell(pos, t)
            s = t
            if pending_b2b:
                app, offer_at = pending_b2b
                pending_b2b = None
                D = 20000
                self.record_spell(z, b, s, s + D, D, True, [], pos)
                self.add_free(z, s, s + D, [])
                self.stay(s, s + D, pos)
                acc = s + D
                tr, done, drop, b2b = self.run_trip(sess, app, acc, self.pos_at(offer_at), offer_at, click=self.rng.random() > 0.03, during=True)
                if last_drop:
                    self.truth["drop_to_pickup_km"].append(K.haversine_km(last_drop[0], last_drop[1], tr["card"]["rest"]["lat"], tr["card"]["rest"]["lon"]))
                self.after_delivery(done)
                last_drop, pos, t, pending_b2b = drop, drop, done, b2b
                continue
            m = SPELL_MEDIAN_BASE[b] * SPELL_ZONE_FACTOR[z]
            D = int(self.rng.expovariate(math.log(2) / m) * 60000) + 10000  # >= 10 s: the accepted card is seen 8 s before
            e_plan = s + D
            procs = {a: s + int(self.rng.uniform(0.5, 1.5) / max(1e-6, DECLINE_RATE[a] * K.HOUR_BLOCK_MULT[b] * K.ZONE_RATE_MULT[z]) * 3600000)
                     for a in sess}
            free_offers = []
            free_apps = []
            busy_until = {a: 0 for a in sess}
            if self.rng.random() < 0.02 and D > 4 * 60000:
                gapp = next((a for a in sess if self.online(sess, a, s + 60000)), None)
                if gapp:
                    self.ghost(sess, gapp, s + 60000, pos)
            while True:
                uoff = self.union_off_after(sess, s) if self.union_on(sess, s) else s
                a, x = min(procs.items(), key=lambda kv: kv[1])
                if x >= min(e_plan - 8000, uoff):
                    break
                rate = DECLINE_RATE[a] * K.HOUR_BLOCK_MULT[K.block_of_ms(x)] * K.ZONE_RATE_MULT[z]
                procs[a] = x + int(self.rng.uniform(0.5, 1.5) / max(rate, 1e-6) * 3600000)
                if self.online(sess, a, x) and x >= busy_until[a]:
                    busy_until[a] = self.declined_offer(sess, a, x, pos, day_idx=d)
                    free_offers.append(x)
                    free_apps.append(a)
                    self.maybe_penalty(sess, a, x, d, pos)
            uoff = self.union_off_after(sess, s) if self.union_on(sess, s) else s
            if e_plan < uoff:
                cands = [a for a in sess if self.online(sess, a, e_plan - 8000) and self.online(sess, a, e_plan)]
                if not cands:
                    cands = [a for a in sess if self.online(sess, a, e_plan)]
                app = "wolt" if ("wolt" in cands and (len(cands) == 1 or self.rng.random() < 0.6)) else cands[0] if cands[0] != "wolt" else cands[-1]
                self.record_spell(z, b, s, e_plan, D, True, free_apps + [app], pos)
                self.add_free(z, s, e_plan, free_offers + [e_plan - 8000])
                self.stay(s, e_plan, pos)
                if last_drop is not None:
                    pass
                tr, done, drop, b2b = self.run_trip(sess, app, e_plan, pos, e_plan - 8000, click=self.rng.random() > 0.03)
                if last_drop:
                    self.truth["drop_to_pickup_km"].append(K.haversine_km(last_drop[0], last_drop[1], tr["card"]["rest"]["lat"], tr["card"]["rest"]["lon"]))
                self.after_delivery(done)
                last_drop, pos, t, pending_b2b = drop, drop, done, b2b
            else:
                if uoff > s:
                    self.record_spell(z, b, s, uoff, D, False, free_apps, pos, sess=sess)
                    self.add_free(z, s, uoff, free_offers)
                    self.stay(s, uoff, pos)
                t = uoff
                last_drop = None
                if self.union_next_on(sess, t) is None:
                    break
        end = max(e for a in sess for s_, e in sess[a])
        self.emit(end + 2 * 60000, "events.jsonl", {"t": ts(end + 2 * 60000), "type": "shift_end", "km": 42.0, "min": (end - shift_start) / 60000.0})
        self.stay(t, end + 2 * 60000, pos)
        self.write_sessions(d, sess)
        self.gps(shift_start, end + 2 * 60000)
        if d >= self.v2_from:
            pt = end - 10 * 60000
            self.emit(pt, "economics-penalties.jsonl", {"schemaVersion": 1, "platform": "bolt", "kind": "performance_snapshot",
                                                        "observedAtMs": pt, "acceptanceRate": 0.86, "completionRate": 0.98, "tasksDone": 41,
                                                        "earningsAzn": 180.5, "rating": 4.9, "periodLabel": "This week"})
            self.truth["performance"] += 1

    def record_spell(self, z, b, s, e, D, event, apps, pos, sess=None):
        self.truth["spells"].append((z, b, D / 60000.0, (e - s) / 60000.0, event))
        if K.baku_day(s) < self.v2_day:
            return
        seen = {}
        for a in apps:
            seen[PKG[a]] = seen.get(PKG[a], 0) + 1
        on = [{"pkg": PKG[a], "onlineMs": int(e - s)} for a in sorted(self.cur_sess) if self.online(self.cur_sess, a, s)]
        end_event = "offer_accepted" if event else "all_offline"
        self.emit(e, "economics-idle.jsonl", {"schemaVersion": 1, "spellId": "idle-%d" % s, "startAtMs": int(s), "endAtMs": int(e),
                                              "appsOnline": on, "endEvent": end_event, "offersSeen": seen, "zoneStart": z, "zoneEnd": z,
                                              "hourBlockStart": b, "kmRidden": 0.0, "repositioning": False, "censored": not event})

    def after_delivery(self, done):
        """Truth for H2: time from delivered to the next presented offer is computed after the run (needs all offers)."""
        self.deliveries.append(done)

    def write_sessions(self, d, sess):
        for app in sess:
            for s, e in sess[app]:
                if e <= s:
                    continue
                n_off = sum(1 for (x, a, _) in self.offers if a == app and s <= x < e)
                self.truth["sessions"].append((app, s, e))
                plat = self.rng.choice(["slider_go_offline", "waiting_text", "task_screen"])
                self.emit(s, "platforms.jsonl", {"pkg": PKG[app], "stage": "IDLE", "demand": "Medium", "multipliers": [], "t": ts(s)})
                if d < self.schema1_days:
                    row = {"schemaVersion": 1, "pkg": PKG[app], "startAtMs": s, "endAtMs": e, "durationMinutes": (e - s) / 60000.0,
                           "startObserved": True, "endObserved": True, "endReason": "offline_screen", "offersSeen": n_off,
                           "accepted": 0, "delivered": 0, "t": ts(e)}
                else:
                    row = {"schemaVersion": 2, "pkg": PKG[app], "sessionId": "%s-%d" % (app, s), "startAtMs": s, "endAtMs": e,
                           "durationMinutes": (e - s) / 60000.0, "startObserved": d % 4 != 1, "endObserved": True, "endReason": "offline_screen",
                           "lastOnlineSeenAtMs": e, "startEvidence": plat, "offersSeen": n_off, "accepted": 0, "rejected": 0, "delivered": 0,
                           "offerIds": [], "tripIds": [], "t": ts(e)}
                self.emit(e, "online-sessions.jsonl", row)
        if d == 7:
            self.emit(self.rows[-1][0], "online-sessions.jsonl", {"schemaVersion": 99, "pkg": PKG["wolt"], "startAtMs": 1, "endAtMs": 2})
            self.truth["unknown_schema_rows"] += 1

    def gps(self, s, e):
        t = s
        while t <= e:
            lat, lon = self.pos_at(t)
            lat += self.rng.uniform(-1e-4, 1e-4)
            lon += self.rng.uniform(-1e-4, 1e-4)
            self.emit(t, "gps.csv", "%s,%.6f,%.6f,%.1f,%.1f,0.0,30.0" % (ts(t), lat, lon, 6.0, 4.2))
            t += 30000

    def run(self):
        self.deliveries = []
        self.v2_day = (START + timedelta(days=self.v2_from)).strftime("%Y-%m-%d")
        for d in range(self.days):
            self.shift(d)
        # H2 truth: delivered -> next offer (any app) while some app stays online
        offers = sorted(x for x, _, _ in self.offers)
        union = []
        for app, s, e in sorted(self.truth["sessions"], key=lambda r: r[1]):
            if union and s <= union[-1][1]:
                union[-1][1] = max(union[-1][1], e)
            else:
                union.append([s, e])
        for done in self.deliveries:
            i = bisect.bisect_right(offers, done)
            seg = next((iv for iv in union if iv[0] <= done < iv[1]), None)
            if i < len(offers) and seg and offers[i] < seg[1]:
                self.truth["delivered_to_next_offer"].append((offers[i] - done) / 60000.0)
        return self

    # --- output -------------------------------------------------------------------------
    def files(self):
        out = defaultdict(list)
        gps_seen = set()
        for at, name, payload in sorted(self.rows, key=lambda r: r[0]):
            day = K.baku_day(at)
            if name == "gps.csv" and day not in gps_seen:
                gps_seen.add(day)
                out["%s/gps.csv" % day].append(K.GPS_HEADER)
            out["%s/%s" % (day, name)].append(payload if isinstance(payload, str) else json.dumps(payload, ensure_ascii=False))
        files = {k: ("\n".join(v) + "\n").encode("utf-8") for k, v in out.items()}
        files["restaurants.json"] = json.dumps({r["name"].lower(): {"n": 3, "name": r["name"], "wait_min": 4.5} for r in self.rest},
                                               ensure_ascii=False).encode("utf-8")
        files["2026-09-03/unknown-file.txt"] = "Aysel Testova +994 55 123 45 67\n".encode("utf-8")
        self.truth["pii"].update(["Aysel Testova"])
        return files


def generate(days=30, seed=7, v2_from_day=22):
    s = Synth(days, seed, v2_from_day).run()
    return s.files(), s.truth


def write(files, path, as_zip=False):
    if as_zip:
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
            for k in sorted(files):
                z.writestr(k, files[k])
        return path
    for k, v in files.items():
        p = os.path.join(path, *k.split("/"))
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "wb") as f:
            f.write(v)
    return path


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        sys.exit(2)
    out = args[0]
    days = int(args[args.index("--days") + 1]) if "--days" in args else 30
    seed = int(args[args.index("--seed") + 1]) if "--seed" in args else 7
    files, truth = generate(days, seed)
    if "--zip" in args:
        write(files, out, as_zip=True)
    else:
        os.makedirs(out, exist_ok=True)
        write(files, out)
    print("synthetic export written: %s (%d files, %d offers, %d spells)" % (out, len(files), truth["offers_total"], len(truth["spells"])))
