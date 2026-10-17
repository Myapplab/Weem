"""Descriptive analysis of a KuryeLog export (user questions H1-H7 + data quality). No verdicts.

Every table row carries its n; rows below the minimum sample are flagged `az_veri`.
Estimators follow SPEC 2.1.0 section 16 (Gamma-Poisson shrinkage, Theil-Sen) and add Kaplan-Meier
for right-censored idle spells. Times are Asia/Baku (UTC+4); a trip belongs to the day and hour block
in which it was accepted (like the app's record `day`); online time is split at midnight and at block edges.
"""
import bisect
import math
import re
from collections import Counter, defaultdict

import kl_export as K

MIN_N = {"trips": 10, "hours": 3.0, "offers": 20, "fit": 15, "spells": 20, "pairs": 10}
KM_BIN_MIN = 5.0
KM_POINTS = (5, 10, 15, 20, 30, 45, 60)
FAR_RATIO = 1.6
COMBO_LABEL = {("wolt",): "yalnız Wolt", ("bolt",): "yalnız Bolt", ("bolt", "wolt"): "Wolt + Bolt"}
PENALTY_TEXT = re.compile(r"(?i)(offline|oflayn|çevrimdışı).{0,80}(declin|imtina|reddet)|(declin|imtina|reddet).{0,80}(offline|oflayn|çevrimdışı)")


# --- small statistics --------------------------------------------------------------------------
def median(xs):
    s = sorted(xs)
    n = len(s)
    if not n:
        return None
    return s[n // 2] if n % 2 else (s[n // 2 - 1] + s[n // 2]) / 2.0


def quantile(xs, q):
    s = sorted(xs)
    if not s:
        return None
    pos = (len(s) - 1) * q
    lo = int(math.floor(pos))
    hi = min(lo + 1, len(s) - 1)
    return s[lo] + (s[hi] - s[lo]) * (pos - lo)


def theil_sen(xs, ys, cap=1200):
    """ref.theil_sen (median pairwise slope, median intercept); above `cap` points an evenly spaced subsample."""
    pts = list(zip(xs, ys))
    if len(pts) > cap:
        step = len(pts) / float(cap)
        pts = [pts[int(i * step)] for i in range(cap)]
    sl = sorted((pts[j][1] - pts[i][1]) / (pts[j][0] - pts[i][0])
                for i in range(len(pts)) for j in range(i + 1, len(pts)) if pts[j][0] != pts[i][0])
    if not sl:
        return None
    b = median(sl)
    a = median([y - b * x for x, y in zip(xs, ys)])
    gate = len(xs) >= 15 and (max(xs) - min(xs)) >= 2.0
    return {"a": a, "b": b, "n": len(xs), "gatePassed": gate}


def kaplan_meier(durations, events):
    """Product-limit survival for right-censored data. Returns ([(t, S)], median or None)."""
    data = sorted(zip(durations, events))
    n = len(data)
    S, curve, i = 1.0, [], 0
    med = None
    while i < n:
        t = data[i][0]
        d = c = 0
        while i < n and data[i][0] == t:
            if data[i][1]:
                d += 1
            else:
                c += 1
            i += 1
        at_risk = n - (i - d - c)
        if d:
            S *= 1.0 - d / float(at_risk)
            curve.append((t, S))
            if med is None and S <= 0.5:
                med = t
    return curve, med


def surv_at(curve, t):
    s = 1.0
    for x, v in curve:
        if x <= t:
            s = v
        else:
            break
    return s


def merge(iv):
    out = []
    for s, e in sorted(iv):
        if e <= s:
            continue
        if out and s <= out[-1][1]:
            out[-1][1] = max(out[-1][1], e)
        else:
            out.append([s, e])
    return out


def subtract(base, cut):
    """base minus cut (both merged) -> [(s, e, ended_by_cut_start)]."""
    out, j = [], 0
    cut_starts = {c[0] for c in cut}
    for s, e in base:
        cur = s
        while j < len(cut) and cut[j][1] <= cur:
            j += 1
        k = j
        while cur < e:
            if k < len(cut) and cut[k][0] < e:
                cs, ce = cut[k]
                if cs > cur:
                    out.append((cur, cs, cs in cut_starts))
                cur = max(cur, ce)
                k += 1
            else:
                out.append((cur, e, False))
                cur = e
    return out


def inside(iv_starts, iv, t):
    i = bisect.bisect_right(iv_starts, t) - 1
    return i >= 0 and iv[i][0] <= t < iv[i][1]


def split_blocks(s, e):
    """[s, e) -> [(day, block, s, e)] cut at Baku midnight and at hour-block edges."""
    out, t = [], s
    while t < e:
        day = K.baku_day(t)
        d0 = K.day_start_ms(day)
        m = (t - d0) / 60000.0
        nxt = d0 + 1440 * 60000
        blk = "B0"
        for bid, lo, hi in K.HOUR_BLOCKS:
            if lo <= m < hi:
                nxt, blk = d0 + hi * 60000, bid
                break
        nxt = min(nxt, e)
        out.append((day, blk, t, nxt))
        t = nxt
    return out


def hours(ms):
    return ms / 3600000.0


def flag(n, key):
    return n < MIN_N[key]


# --- analysis ----------------------------------------------------------------------------------
class Analysis:
    def __init__(self, exp, hub=None):
        self.exp = exp
        self.events = K.order_events(exp)
        self.trips = K.trips(exp, self.events)
        self.offers = K.offers(exp, self.events, self.trips)
        self.sessions = K.sessions(exp)
        self.fixes = K.gps(exp)
        self.track = K.Track(self.fixes)
        self.clicks = defaultdict(list)
        self.click_rows = 0
        for at, row in K.simple_rows(exp, "events.jsonl"):
            if row.get("type") == "click":
                self.click_rows += 1
                if K.is_accept_text(row.get("text")):
                    self.clicks[row.get("pkg")].append(at)
        self.hub, self.hub_source = self._hub(hub)
        self._mark_trips()
        self.online = {p: merge([[s["start"], s["end"]] for s in self.sessions if s["platform"] == p])
                       for p in sorted({s["platform"] for s in self.sessions})}
        self.union = merge([iv[:] for p in self.online for iv in self.online[p]])
        self.union_starts = [iv[0] for iv in self.union]
        self.busy = merge([[t["accepted_at"], max(t["end_at"], t["accepted_at"])] for t in self.trips if not t["ghost_like"]])
        self.busy_starts = [iv[0] for iv in self.busy]
        self.free = subtract(self.union, self.busy)
        for o in self.offers:
            o["during_trip"] = inside(self.busy_starts, self.busy, o["at"])
            o["online"] = inside(self.union_starts, self.union, o["at"])
            pos = self.track.at(o["at"]) or o.get("loc")
            o["pos"] = pos
            o["zone"] = K.zone_of(pos[0], pos[1], self.hub) if pos else "unknown"
            o["block"] = K.block_of_ms(o["at"])

    def _hub(self, hub):
        if hub is not None:
            return tuple(hub), "verilen"
        pts = []
        pick = {e["trip_id"]: e for e in self.events if e["type"] in ("picked_up", "restaurant_arrived") and e["trip_id"]}
        for t in self.trips:
            when = t["picked_at"] or t["arrived_at"]
            p = self.track.at(when) if when else None
            if p is None and t["id"] in pick:
                p = pick[t["id"]]["loc"]
            if p:
                pts.append(p)
        if len(pts) >= 30:
            return (median([p[0] for p in pts]), median([p[1] for p in pts])), "teslim alma medyanı (n=%d)" % len(pts)
        return K.HUB_DEFAULT, "varsayılan Bakı merkezi (teslim alma < 30)"

    def zone_at(self, t):
        p = self.track.at(t)
        return K.zone_of(p[0], p[1], self.hub) if p else "unknown"

    def _mark_trips(self):
        has_clicks = self.click_rows > 0
        for t in self.trips:
            taps = self.clicks.get(t["pkg"], [])
            i = bisect.bisect_left(taps, t["accepted_at"] - 120000)
            t["accept_tap"] = (i < len(taps) and taps[i] <= t["accepted_at"] + 5000) if has_clicks else None
            t["no_tap"] = t["missed_offer"] or (t["accept_tap"] is False)
            # A K34 inferred delivery reached a customer card: a real order, not a ghost.
            t["ghost_like"] = t["no_tap"] and t["phase"] == "END_UNCONFIRMED" and t["picked_at"] is None and not t.get("delivered_inferred")

    # (1) H7: net per online hour --------------------------------------------------------------
    def net_per_hour(self):
        day_h, blk_h, plat_h, combo_h = defaultdict(float), defaultdict(float), defaultdict(float), defaultdict(float)
        for s, e in self.union:
            for day, blk, a, b in split_blocks(s, e):
                day_h[day] += hours(b - a)
                blk_h[blk] += hours(b - a)
        for p, iv in self.online.items():
            for s, e in iv:
                plat_h[p] += hours(e - s)
        cuts = sorted({x for p in self.online for iv in self.online[p] for x in iv})
        segs = []
        for a, b in zip(cuts, cuts[1:]):
            mid = (a + b) / 2.0
            on = tuple(sorted(p for p, iv in self.online.items() if inside([x[0] for x in iv], iv, mid)))
            if on:
                segs.append((a, b, on))
                combo_h[on] += hours(b - a)
        seg_starts = [s[0] for s in segs]
        done = [t for t in self.trips if t["completed_at"] is not None and t["phase"] == "FINISHED"]
        rows = {"day": defaultdict(lambda: [0, 0.0, 0]), "block": defaultdict(lambda: [0, 0.0, 0]),
                "platform": defaultdict(lambda: [0, 0.0, 0]), "combo": defaultdict(lambda: [0, 0.0, 0])}
        for t in done:
            keys = {"day": K.baku_day(t["accepted_at"]), "block": K.block_of_ms(t["accepted_at"]), "platform": t["platform"]}
            i = bisect.bisect_right(seg_starts, t["accepted_at"]) - 1
            keys["combo"] = segs[i][2] if i >= 0 and segs[i][0] <= t["accepted_at"] < segs[i][1] else ("çevrimdışı",)
            for k, v in keys.items():
                r = rows[k][v]
                if t["net"] is None:
                    r[2] += 1
                else:
                    r[0] += 1
                    r[1] += t["net"]

        def table(kind, hrs, label=lambda k: k):
            out = []
            for k in sorted(set(hrs) | set(rows[kind]), key=lambda x: str(x)):
                n, net, unk = rows[kind].get(k, [0, 0.0, 0])
                h = hrs.get(k, 0.0)
                out.append({"key": label(k), "online_h": h, "trips": n, "net": net, "net_per_h": (net / h) if h > 0 else None,
                            "unknown_fee_trips": unk, "az_veri": flag(n, "trips") or h < MIN_N["hours"]})
            return out
        total_h = sum(day_h.values())
        total_net = sum(t["net"] for t in done if t["net"] is not None)
        return {"by_day": table("day", day_h), "by_block": table("block", blk_h, lambda k: "%s %s" % (k, K.BLOCK_LABEL.get(k, ""))),
                "by_platform": table("platform", plat_h),
                "by_combo": table("combo", combo_h, lambda k: COMBO_LABEL.get(k, " + ".join(k))),
                "total": {"online_h": total_h, "net": total_net, "trips": sum(1 for t in done if t["net"] is not None),
                          "net_per_h": total_net / total_h if total_h else None},
                "commission_note": sorted({(t["platform"], t["commission_pct"]) for t in done})}

    # (2) H1: offers per hour by zone x hour block -------------------------------------------
    def offer_rates(self):
        free_h, on_h = defaultdict(float), defaultdict(float)
        for s, e, _ in self.free:
            for day, blk, a, b in split_blocks(s, e):
                t = a
                while t < b:
                    u = min(b, t + 5 * 60000)
                    free_h[(self.zone_at((t + u) / 2.0), blk)] += hours(u - t)
                    t = u
        for s, e in self.union:
            for day, blk, a, b in split_blocks(s, e):
                t = a
                while t < b:
                    u = min(b, t + 5 * 60000)
                    on_h[(self.zone_at((t + u) / 2.0), blk)] += hours(u - t)
                    t = u
        n_free, n_on = Counter(), Counter()
        for o in self.offers:
            if not o["online"]:
                continue
            n_on[(o["zone"], o["block"])] += 1
            if not o["during_trip"]:
                n_free[(o["zone"], o["block"])] += 1
        rows = []
        cells = sorted(set(free_h) | set(on_h) | set(n_on), key=lambda c: (c[0], c[1]))
        for z, b in cells:
            H, N = free_h.get((z, b), 0.0), n_free.get((z, b), 0)
            lam0 = K.lambda_prior(z, b)
            a0 = K.GAMMA_PRIOR_SHAPE
            b0 = a0 / lam0
            post = (a0 + N) / (b0 + H)
            status = "personal" if (N >= 20 and H >= 5.0) else ("blend" if N > 0 else "prior")
            oh, on = on_h.get((z, b), 0.0), n_on.get((z, b), 0)
            rows.append({"zone": z, "block": b, "free_h": H, "offers_free": N, "raw_per_free_h": N / H if H > 0 else None,
                         "prior_per_h": lam0, "posterior_per_h": post, "status": status, "online_h": oh, "offers_online": on,
                         "per_online_h": on / oh if oh > 0 else None, "az_veri": N < MIN_N["offers"] or H < 5.0,
                         "zone_prior_note": z == "unknown"})
        v2 = K.idle_spells(self.exp)
        v2_h = sum(hours(max(0, (K.ms_int(r.get("endAtMs")) or 0) - (K.ms_int(r.get("startAtMs")) or 0))) for r in v2)
        v2_days = sorted({K.baku_day(r["_at"]) for r in v2 if r["_at"]})
        recon_h = sum(hours(e - s) for s, e, _ in self.free if K.baku_day(s) in set(v2_days))
        return {"cells": rows, "hub": self.hub, "hub_source": self.hub_source,
                "v2_check": {"days": len(v2_days), "idle_rows": len(v2), "idle_h": v2_h, "reconstructed_free_h": recon_h}}

    # (3) H3: fee fit ----------------------------------------------------------------------------
    def fee_fit(self):
        out = []
        for plat in sorted({o["platform"] for o in self.offers}):
            os_ = [o for o in self.offers if o["platform"] == plat and o["data"]["gross"] is not None and not (o.get("boost") or 0) > 0]
            for xname, key in (("km", "km"), ("gösterilen dk", "shown_m")):
                pts = [(o["data"][key], o["data"]["gross"], o) for o in os_ if o["data"][key] is not None]
                if len(pts) < 3:
                    out.append({"platform": plat, "x": xname, "n": len(pts), "fit": None, "az_veri": True})
                    continue
                xs, ys = [p[0] for p in pts], [p[1] for p in pts]
                fit = theil_sen(xs, ys)
                if fit is None:
                    out.append({"platform": plat, "x": xname, "n": len(pts), "fit": None, "az_veri": True})
                    continue
                res = [y - (fit["a"] + fit["b"] * x) for x, y in zip(xs, ys)]
                mres = median(res)
                mad = median([abs(r - mres) for r in res])
                big = sorted(zip(res, pts), key=lambda rp: -abs(rp[0]))[:5]
                out.append({"platform": plat, "x": xname, "n": len(pts), "fit": fit, "x_range": (min(xs), max(xs)),
                            "mad": mad, "sd_robust": 1.4826 * mad,
                            "largest": [{"when": "%s %s" % (K.baku_day(p[2]["at"]), K.clock(p[2]["at"])), "x": p[0], "fee": p[1],
                                         "fitted": fit["a"] + fit["b"] * p[0], "residual": r, "zone": p[2]["zone"]} for r, p in big],
                            "az_veri": not fit["gatePassed"]})
        excluded = sum(1 for o in self.offers if (o.get("boost") or 0) > 0)
        return {"fits": out, "boost_excluded": excluded}

    # (4) H2: back-to-back -----------------------------------------------------------------------
    def back_to_back(self):
        offer_t = [o["at"] for o in self.offers]
        delays, censored = [], 0
        for t in self.trips:
            if t["completed_at"] is None or t["phase"] != "FINISHED":
                continue
            d = t["completed_at"]
            i = bisect.bisect_right(offer_t, d)
            j = bisect.bisect_right(self.union_starts, d) - 1
            seg_end = self.union[j][1] if j >= 0 and self.union[j][0] <= d < self.union[j][1] else None
            if seg_end is None:
                censored += 1
            elif i < len(offer_t) and offer_t[i] < seg_end:
                delays.append((offer_t[i] - d) / 60000.0)
            else:
                censored += 1
        on = [o for o in self.offers if o["online"]]
        by_plat = {}
        for p in sorted({o["platform"] for o in on}):
            xs = [o for o in on if o["platform"] == p]
            by_plat[p] = {"n": len(xs), "during": sum(1 for o in xs if o["during_trip"])}
        dist = []
        real = [t for t in self.trips if not t["ghost_like"]]
        for a, b in zip(real, real[1:]):
            if a["completed_at"] is None or b["accepted_at"] < a["completed_at"] - 10 * 60000:
                continue
            j = bisect.bisect_right(self.union_starts, a["completed_at"]) - 1
            if not (j >= 0 and self.union[j][0] <= a["completed_at"] < self.union[j][1] and b["accepted_at"] < self.union[j][1]):
                continue
            p1 = self.track.at(a["completed_at"])
            when = b["picked_at"] or b["arrived_at"]
            p2 = self.track.at(when) if when else None
            if p1 and p2:
                dist.append(K.haversine_km(p1[0], p1[1], p2[0], p2[1]))
        return {"delay_min": {"n": len(delays), "censored": censored, "median": median(delays), "p25": quantile(delays, 0.25),
                              "p75": quantile(delays, 0.75), "az_veri": flag(len(delays), "trips")},
                "during_trip": {"n": len(on), "during": sum(1 for o in on if o["during_trip"]), "by_platform": by_plat},
                "drop_to_pickup_km": {"n": len(dist), "median": median(dist), "p25": quantile(dist, 0.25), "p75": quantile(dist, 0.75),
                                      "az_veri": flag(len(dist), "pairs")}}

    # (5) H4: near by air, far by road -----------------------------------------------------------
    def restaurant_pos(self, o, trip=None):
        txt = o["data"].get("restaurant_address")
        ref = o.get("pos") or K.HUB_DEFAULT
        p = K.from_card_text(txt, ref[0], ref[1]) if txt else None
        if p is None and trip is not None:
            when = trip["picked_at"] or trip["arrived_at"]
            p = self.track.at(when) if when else None
        return p

    def air_vs_road(self):
        trip_of = {t["id"]: t for t in self.trips}
        approach, full = [], []
        for o in self.offers:
            km = o["data"]["km"]
            if km is None or o["pos"] is None:
                continue
            trip = trip_of.get(o["trip_id"]) if o["trip_id"] else None
            rp = self.restaurant_pos(o, trip)
            if rp is None:
                continue
            air_a = K.haversine_km(o["pos"][0], o["pos"][1], rp[0], rp[1])
            if air_a >= 0.2:
                approach.append((km / air_a, o, K.zone_of(o["pos"][0], o["pos"][1], self.hub), K.zone_of(rp[0], rp[1], self.hub), None))
            if trip and trip["completed_at"]:
                cp = self.track.at(trip["completed_at"])
                if cp:
                    air = air_a + K.haversine_km(rp[0], rp[1], cp[0], cp[1])
                    if air >= 0.3:
                        full.append((km / air, o, K.zone_of(o["pos"][0], o["pos"][1], self.hub), K.zone_of(rp[0], rp[1], self.hub),
                                     K.zone_of(cp[0], cp[1], self.hub)))

        def summary(rows):
            rs = [r[0] for r in rows]
            bins = Counter("<1.0" if r < 1.0 else "1.0-1.3" if r < 1.3 else "1.3-1.6" if r < FAR_RATIO else ">1.6" for r in rs)
            pairs = Counter((r[2], r[3]) + ((r[4],) if r[4] else ()) for r in rows if r[0] > FAR_RATIO)
            pr = defaultdict(list)
            for r in rows:
                if r[0] > FAR_RATIO:
                    pr[(r[2], r[3]) + ((r[4],) if r[4] else ())].append(r[0])
            return {"n": len(rs), "p10": quantile(rs, 0.1), "median": median(rs), "p90": quantile(rs, 0.9), "bins": dict(bins),
                    "far": sum(1 for r in rs if r > FAR_RATIO),
                    "pairs": [{"zones": k, "n": v, "median_ratio": median(pr[k])} for k, v in pairs.most_common()],
                    "az_veri": flag(len(rs), "pairs")}
        return {"approach_only": summary(approach), "full_route": summary(full)}

    # (6) H5: idle spells, Kaplan-Meier ----------------------------------------------------------
    def idle_spells(self):
        v2 = K.idle_spells(self.exp)
        v2_days = {K.baku_day(r["_at"]) for r in v2 if r["_at"]}
        spells = []
        for s, e, ev in self.free:
            if K.baku_day(s) in v2_days:
                continue
            spells.append((self.zone_at(s), K.block_of_ms(s), (e - s) / 60000.0, bool(ev), "yeniden kuruldu"))
        for r in v2:
            s, e = K.ms_int(r.get("startAtMs")), K.ms_int(r.get("endAtMs"))
            if s is None or e is None or e < s:
                continue
            z = r.get("zoneStart") if r.get("zoneStart") in K.ZONE_RATE_MULT else "unknown"
            b = r.get("hourBlockStart") if r.get("hourBlockStart") in K.HOUR_BLOCK_MULT else K.block_of_ms(s)
            ev = r.get("endEvent") == "offer_accepted" and not r.get("censored")
            spells.append((z, b, (e - s) / 60000.0, ev, "economics-idle v1"))
        cells = defaultdict(list)
        for z, b, d, ev, src in spells:
            cells[(z, b)].append((d, ev))
            cells[("tümü", b)].append((d, ev))
        rows = []
        for (z, b), xs in sorted(cells.items(), key=lambda kv: (kv[0][0] == "tümü", kv[0])):
            curve, med = kaplan_meier([x[0] for x in xs], [x[1] for x in xs])
            rows.append({"zone": z, "block": b, "n": len(xs), "events": sum(1 for x in xs if x[1]),
                         "censored": sum(1 for x in xs if not x[1]), "median_min": med,
                         "surv": {t: surv_at(curve, t) for t in KM_POINTS}, "az_veri": flag(len(xs), "spells")})
        return {"cells": rows, "sources": Counter(s[4] for s in spells), "bin_min": KM_BIN_MIN}

    # (7) H6: declines, banners, penalties, performance -----------------------------------------
    def _rate(self, plat, a, b):
        iv = self.online.get(plat, [])
        h = sum(hours(max(0, min(e, b) - max(s, a))) for s, e in iv)
        n = sum(1 for o in self.offers if o["platform"] == plat and a <= o["at"] < b)
        return n, h

    def declines(self):
        kinds = Counter((o["platform"], o["decision"]) for o in self.offers)
        per_day = defaultdict(Counter)
        for o in self.offers:
            if o["counts_as_decline"]:
                per_day[K.baku_day(o["at"])][o["platform"]] += 1
        events = []
        for o in self.offers:
            if o["decision"] == "declined_banner_observed":
                events.append({"at": o["decision_at"] or o["at"], "platform": o["platform"], "kind": "banner", "until": None})
        for e in self.events:
            if e["type"] == "penalty_offline":
                events.append({"at": e["at"], "platform": e["platform"], "kind": "offline_for_declines", "until": None})
        perf = []
        for r in K.penalties(self.exp):
            plat = r.get("platform") or "?"
            if r.get("kind") == "offline_for_declines":
                events.append({"at": r["_at"], "platform": plat, "kind": "offline_for_declines", "until": K.ms_int(r.get("offlineUntilMs"))})
            elif r.get("kind") == "declines_banner":
                events.append({"at": r["_at"], "platform": plat, "kind": "banner", "until": None})
            elif r.get("kind") == "performance_snapshot":
                perf.append(r)
                events.append({"at": r["_at"], "platform": plat, "kind": "performance_snapshot", "until": None})
            elif r.get("kind"):
                events.append({"at": r["_at"], "platform": plat, "kind": r["kind"], "until": K.ms_int(r.get("offlineUntilMs"))})
        for at, row in K.simple_rows(self.exp, "screens.jsonl"):
            texts = row.get("texts") if isinstance(row.get("texts"), list) else []
            if any(PENALTY_TEXT.search(t) for t in texts if isinstance(t, str)):
                events.append({"at": at, "platform": K.platform(row.get("pkg")), "kind": "offline_for_declines", "until": None})
        events = [e for e in events if e["at"]]
        events.sort(key=lambda e: (e["at"], e["kind"]))
        dedup = []
        for e in events:
            if any(d["kind"] == e["kind"] and d["platform"] == e["platform"] and abs(d["at"] - e["at"]) <= 10 * 60000 for d in dedup[-6:]):
                for d in dedup[-6:]:
                    if d["kind"] == e["kind"] and d["platform"] == e["platform"] and e["until"] and not d["until"]:
                        d["until"] = e["until"]
                continue
            dedup.append(dict(e))
        rows = []
        pooled = defaultdict(lambda: [0, 0.0, 0, 0.0])
        for e in dedup:
            after0 = e["until"] or self._back_online(e["platform"], e["at"]) if e["kind"] == "offline_for_declines" else e["at"]
            nb, hb = self._rate(e["platform"], e["at"] - 3600000, e["at"])
            na, ha = self._rate(e["platform"], after0, after0 + 3600000)
            rows.append({"when": "%s %s" % (K.baku_day(e["at"]), K.clock(e["at"])), "platform": e["platform"], "kind": e["kind"],
                         "before_n": nb, "before_h": hb, "after_n": na, "after_h": ha,
                         "before_rate": nb / hb if hb > 0 else None, "after_rate": na / ha if ha > 0 else None})
            p = pooled[e["kind"]]
            p[0] += nb; p[1] += hb; p[2] += na; p[3] += ha
        perf_rows = [{"when": "%s %s" % (K.baku_day(r["_at"]), K.clock(r["_at"])) if r["_at"] else "?", "platform": r.get("platform"),
                      "acceptanceRate": r.get("acceptanceRate"), "completionRate": r.get("completionRate"), "rating": r.get("rating"),
                      "tasksDone": r.get("tasksDone"), "periodLabel": r.get("periodLabel")} for r in perf]
        return {"kinds": [{"platform": p, "decision": d, "n": n} for (p, d), n in sorted(kinds.items())],
                "per_day": {d: dict(c) for d, c in sorted(per_day.items())},
                "events": rows, "pooled": {k: {"before_n": v[0], "before_h": v[1], "after_n": v[2], "after_h": v[3],
                                               "before_rate": v[0] / v[1] if v[1] else None, "after_rate": v[2] / v[3] if v[3] else None}
                                           for k, v in pooled.items()},
                "performance": perf_rows, "counts": Counter(e["kind"] for e in dedup)}

    def _back_online(self, plat, t):
        for s, e in self.online.get(plat, []):
            if s > t:
                return s
        return t

    # (8) data quality ---------------------------------------------------------------------------
    def quality(self):
        fee = defaultdict(lambda: [0, 0])
        for o in self.offers:
            f = fee[o["platform"]]
            f[0] += 1
            f[1] += int(o["data"]["gross"] is None)
        src = Counter()
        for at, row in K.simple_rows(self.exp, "screens.jsonl"):
            src[row.get("source") or "?"] += 1
        obs_src = Counter(o["data"].get("source") or "?" for o in self.offers)
        no_tap = [t for t in self.trips if t["no_tap"]]
        ghosts = [{"when": "%s %s" % (K.baku_day(t["accepted_at"]), K.clock(t["accepted_at"])), "platform": t["platform"],
                   "phase": t["phase"], "reason": "teklif görülmedi" if t["missed_offer"] else "kabul dokunuşu yok",
                   "picked": t["picked_at"] is not None} for t in no_tap]
        inferred = [t for t in self.trips if t.get("delivered_inferred")]
        # K36: rows of the platform's own list that no KuryePlus order matched (miss rate); unknown = no day on screen and a
        # clock after the capture, never counted either way.
        hist = defaultdict(lambda: {"n": 0, "matched": 0, "missed": 0, "unknown": 0, "day_assumed": 0, "confirms_inferred": 0})
        for r in K.platform_history(self.exp):
            h = hist[r.get("platform") or "?"]
            h["n"] += 1
            m = r.get("missed_by_kuryeplus")
            h["missed" if m is True else "matched" if m is False else "unknown"] += 1
            h["day_assumed"] += int(r.get("match_day") == "capture_day")
            h["confirms_inferred"] += int(r.get("confirms_inferred_delivery") is True)
        for h in hist.values():
            known = h["matched"] + h["missed"]
            h["miss_rate"] = h["missed"] / known if known else None
        # K31/K32: payment per trip (record fields) joined with the newest cash answer per trip id.
        answers = K.cash_tips(self.exp)
        cash = {"n_trips": len(self.trips), "cash": 0, "card_inferred": 0, "not_observed": 0, "cash_amount_unread": 0,
                "answered": 0, "tip_sum": 0.0, "negative_tips": 0, "answers_without_trip": 0}
        ids = set()
        for t in self.trips:
            ids.add(t["id"])
            m = t.get("payment_method")
            cash["cash" if m == "cash" else "card_inferred" if m == "card_inferred" else "not_observed"] += 1
            if m == "cash" and t.get("cash_amount_azn") is None:
                cash["cash_amount_unread"] += 1
            a = answers.get(t["id"])
            if a is not None:
                cash["answered"] += 1
                tip = K.num(a.get("tip_azn"))
                if tip is not None:
                    cash["tip_sum"] += tip
                    cash["negative_tips"] += int(tip < 0)
        cash["answers_without_trip"] = sum(1 for tid in answers if tid not in ids)
        sess = {"n": len(self.sessions), "schema1": sum(1 for s in self.sessions if s["schema"] == 1),
                "task_screen_only": sum(1 for s in self.sessions if s["start_evidence"] == "task_screen"),
                "no_evidence": sum(1 for s in self.sessions if s["schema"] == 1 or not s["start_evidence"] or s["start_evidence"] == "task_screen"),
                "neither_observed": sum(1 for s in self.sessions if not s["start_observed"] and not s["end_observed"])}
        return {"fees": {p: {"n": v[0], "unreadable": v[1]} for p, v in sorted(fee.items())},
                "screens": dict(src), "offer_sources": dict(obs_src),
                "ghost": {"n_trips": len(self.trips), "no_tap": len(no_tap), "ghost_like": sum(1 for t in self.trips if t["ghost_like"]),
                          "tap_logged": self.click_rows > 0, "rows": ghosts[:30]},
                "inferred": {"n_trips": len(self.trips), "n": len(inferred), "priced": sum(1 for t in inferred if t["gross"] is not None),
                             "gross": sum(t["gross"] for t in inferred if t["gross"] is not None)},
                "platform_history": {p: dict(v) for p, v in sorted(hist.items())},
                "cash": cash,
                "sessions": sess, "stats": dict(self.exp.stats), "days": self.exp.days, "files": self.exp.names(),
                "ignored_files": len(self.exp.ignored), "gps_fixes": len(self.fixes)}

    def run(self):
        return {"net": self.net_per_hour(), "rates": self.offer_rates(), "fees": self.fee_fit(), "h2": self.back_to_back(),
                "h4": self.air_vs_road(), "h5": self.idle_spells(), "h6": self.declines(), "quality": self.quality(),
                "meta": {"source": self.exp.source, "days": self.exp.days, "marker": self.exp.marker, "hub": self.hub,
                         "hub_source": self.hub_source, "n_offers": len(self.offers), "n_trips": len(self.trips),
                         "n_sessions": len(self.sessions)}}


def analyse(exp, hub=None):
    return Analysis(exp, hub).run()
