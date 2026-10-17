"""Self-test of the offline analysis pipeline (python3 -I tools/analysis/selftest.py).

Builds a 30-day synthetic export with known answers (synth.py), checks the anonymizer on the raw export
(no customer string, rounded coordinates, unchanged fees/times/km, marker, unknown file excluded), runs
the pipeline and compares with the known answers: Theil-Sen slope within 5 %, Kaplan-Meier median within
one bin, offer rates within 10 %, midnight-crossing and two-app hours, H2/H4/H6 and data-quality counts,
no PII in any output file, and the whole run under 60 s. The anonymity gate is checked per input
(anonymous + full exports together). Exit code 0 only when every check passes.
"""
import contextlib
import io
import json
import math
import os
import re
import shutil
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.dont_write_bytecode = True  # keep the checkout clean (no __pycache__ next to the tool)
sys.path.insert(0, HERE)
import anonymize  # noqa: E402
import descriptive as D  # noqa: E402
import kl_export as K  # noqa: E402
import redact  # noqa: E402
import run  # noqa: E402
import synth  # noqa: E402

REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok), detail))
    print("%s  %s%s" % ("PASS" if ok else "FAIL", name, (" — " + detail) if detail else ""))
    return ok


def rel(a, b):
    return abs(a - b) / abs(b) if b else float("inf")


def strings(x, skip=("t", "posted")):
    if isinstance(x, dict):
        for k, v in x.items():
            if k in skip:
                continue
            yield from strings(v, skip)
    elif isinstance(x, list):
        for v in x:
            yield from strings(v, skip)
    elif isinstance(x, str):
        yield x


def latlon_ok(x):
    """Every lat/lon at any depth has at most 3 decimals; every loc is exactly {lat, lon, acc, age_s}."""
    if isinstance(x, dict):
        for k, v in x.items():
            if k in ("lat", "lon") and isinstance(v, (int, float)) and not isinstance(v, bool) and round(v, 3) != v:
                return False
            if k == "loc" and isinstance(v, dict) and set(v) != {"lat", "lon", "acc", "age_s"}:
                return False
            if not latlon_ok(v):
                return False
    elif isinstance(x, list):
        return all(latlon_ok(v) for v in x)
    return True


def pii_hits(texts, pii):
    """Planted strings found anywhere in texts (case-insensitive substring search)."""
    blob = "\n".join(texts).lower()
    hits = set()
    for p in set(pii):
        q = p.lower()
        if q in blob:
            hits.add(p)
    return hits


def unit_tests():
    curve, med = D.kaplan_meier([1, 2, 3, 4, 5, 6], [True, False, True, True, False, True])
    # S: t=1 5/6; t=3 5/6*3/4=0.625; t=4 0.625*2/3=0.4167 -> median 4; t=6 -> 0
    check("Kaplan-Meier hand example", med == 4 and abs(D.surv_at(curve, 3) - 0.625) < 1e-12 and D.surv_at(curve, 6) == 0.0,
          "median=%s" % med)
    fit = D.theil_sen([0, 1, 2, 3, 4, 5, 6], [1, 3, 5, 7, 9, 30, 13])
    check("Theil-Sen exact line with one outlier", abs(fit["b"] - 2.0) < 1e-12 and abs(fit["a"] - 1.0) < 1e-12, str(fit))
    free = D.subtract([[0, 100]], [[10, 20], [50, 60]])
    check("free time = online minus trips", free == [(0, 10, True), (20, 50, True), (60, 100, False)], str(free))
    parts = D.split_blocks(K.day_start_ms("2026-09-05") + 23 * 3600000, K.day_start_ms("2026-09-06") + 3600000)
    check("split at Baku midnight and block edges", [(p[0], p[1]) for p in parts] == [("2026-09-05", "B5"), ("2026-09-06", "B0")], str(parts))
    vec = os.path.join(REPO, "docs", "ekonomi-v2", "vektorler", "offer_geo_vectors.json")
    if os.path.exists(vec):
        rows, bad = json.load(open(vec, encoding="utf-8"))["rows"], []
        for r in rows:
            k = r["kind"]
            if k == "plus_short":
                full = K.olc_recover(r["text"], *r["ref"]); c = K.olc_center(full)
                ok = full == r["full"] and all(abs(a - b) <= 1e-7 for a, b in zip(c, r["center"]))
            elif k == "plus_full":
                ok = all(abs(a - b) <= 1e-7 for a, b in zip(K.olc_center(r["text"]), r["center"]))
            elif k == "encode":
                ok = K.olc_encode(*r["latLng"]) == r["full10"] and K.olc_encode(*r["latLng"], length=11) == r["full11"]
            elif k in ("dms", "card"):
                c = K.dms_lat_lon(r["text"]) if k == "dms" else K.from_card_text(r["text"], *(r.get("ref") or [None, None]))
                e = r.get("latLng")
                ok = (c is None and e is None) or (c is not None and e is not None and all(abs(a - b) <= 1e-7 for a, b in zip(c, e)))
            elif k == "zone":
                ok = K.zone_of(*r["latLng"]) == r["zone"]
            elif k == "hourBlock":
                ok = K.hour_block(r["minuteOfDay"]) == r["block"]
            else:
                ok = True
            if not ok:
                bad.append(r)
        check("geo helpers vs offer_geo_vectors.json (WP2 vectors)", not bad, "%d rows, %d bad" % (len(rows), len(bad)))
    src = os.path.join(REPO, "docs", "ekonomi-v2", "redact.py")
    if os.path.exists(src):
        same = open(src, "rb").read() == open(os.path.join(HERE, "redact.py"), "rb").read()
        check("redact.py is an exact copy of docs/ekonomi-v2/redact.py", same)
    buf = io.StringIO()
    try:
        with contextlib.redirect_stdout(buf):
            redact.selftest()
        ok = True
    except AssertionError as e:
        ok = False
        buf.write(str(e))
    check("redact.py selftest", ok, buf.getvalue().splitlines()[0] if buf.getvalue() else "")
    check("panel lines recognised", all(anonymize.is_panel_line(s) for s in ["● 12.3 km", "Wolt · 3.94₼ · Izmir Doner", "Bolt Food · Aysel T. · Müşteriye gidiliyor",
                                                                              "Wolt · Sipariş bekleniyor", "Bugün: 7 sipariş", "Bu gün: 3 sifariş",
                                                                              "Wolt · net 3.15₼ · Test"])
          and not any(anonymize.is_panel_line(s) for s in ["2 stops (2.8 km) • 8–16 min", "Wolt", "₼2.31", "Order details"]))
    check("notification hint", anonymize.notif_hint(["Order is ready for pickup"]) == "ready" and anonymize.notif_hint(["New order", "₼3.20"]) == "offer"
          and anonymize.notif_hint(["Aysel", "Qapıdayam"]) == "other")
    # pre-0.7 `delivered` row without `record` (LegacyRecords.delivered), raw and after the anonymizer
    legacy = json.dumps({"t": "2026-03-02T13:30:00.000+0400", "type": "delivered", "pkg": "com.wolt.courierapp", "gross": 5.0, "net": 4.0,
                         "commission_pct": 20.0, "total_min": 30.0, "km": 4.2, "restaurant": "Test Kafe", "rest_addr": "Nizami 1",
                         "cust_addr": "Gizli küçə 9, mənzil 12", "note": "Qapı kodu 4455", "job": "J1"}) + "\n"
    raw_l = K.from_memory({"2026-03-02/orders.jsonl": legacy.encode("utf-8")})
    anon_l = K.from_memory(anonymize.Anonymizer(b"k" * 32).export(raw_l))
    got = []
    for e in (raw_l, anon_l):
        ts = K.trips(e)
        got.append((len(ts), ts[0]["phase"], round(ts[0]["net"], 6), ts[0]["completed_at"] - ts[0]["accepted_at"]) if ts else None)
    check("legacy delivered row (no record) read like LegacyRecords.delivered, raw and anonymous",
          got[0] == got[1] == (1, "FINISHED", 4.0, 30 * 60000) and "Gizli" not in json.dumps([r for _, r in anon_l.jsonl("orders.jsonl")]),
          str(got))


def truth_hours(truth):
    """Per Baku day union hours and per app-combination hours from the generator's sessions."""
    sess = truth["sessions"]
    union = D.merge([[s, e] for _, s, e in sess])
    day = {}
    for s, e in union:
        for d, _, a, b in D.split_blocks(s, e):
            day[d] = day.get(d, 0.0) + (b - a) / 3600000.0
    online = {}
    for app, s, e in sess:
        online.setdefault(app, []).append([s, e])
    online = {a: D.merge(v) for a, v in online.items()}
    cuts = sorted({x for v in online.values() for iv in v for x in iv})
    combo = {}
    for a, b in zip(cuts, cuts[1:]):
        mid = (a + b) / 2.0
        on = tuple(sorted(p for p, v in online.items() if any(s <= mid < e for s, e in v)))
        if on:
            combo[on] = combo.get(on, 0.0) + (b - a) / 3600000.0
    return day, combo


def anonymizer_tests(raw, files, truth):
    marker = json.loads(files.get(K.MARKER, b"null"))
    check("marker ANALIZ_ANONIM.json", isinstance(marker, dict) and marker.get("schema") == K.MARKER_SCHEMA and marker.get("app") == "com.kuryelog.app"
          and marker.get("redaction") == {"locDecimals": 3, "secretStored": False, "panelLinesRemoved": True} and marker.get("days")
          and isinstance(marker.get("createdAtMs"), int), json.dumps(marker)[:160] if marker else "missing")
    texts, bad_json, geo_bad, gps_bad, dropped_keys, panel_left = [], 0, 0, 0, set(), 0
    count = {}
    for name, data in files.items():
        if name == K.MARKER:
            continue
        txt = data.decode("utf-8")
        base = name.rsplit("/", 1)[-1]
        if base == "gps.csv":
            lines = txt.splitlines()
            if lines[0] != K.GPS_HEADER:
                gps_bad += 1
            for line in lines[1:]:
                p = line.split(",")
                texts.append(line)
                if any(len(v.split(".")[1]) > 3 for v in p[1:3] if "." in v):
                    gps_bad += 1
            count[base] = count.get(base, 0) + len(lines) - 1
            continue
        if base == "restaurants.json":
            texts.extend(strings(json.loads(txt)))
            continue
        n = 0
        for line in txt.splitlines():
            if not line.strip():
                continue
            n += 1
            try:
                row = json.loads(line)
            except ValueError:
                bad_json += 1
                continue
            texts.extend(strings(row))
            if not latlon_ok(row):
                geo_bad += 1
            dump = json.dumps(row)
            for k in ("nodes", "raw", "customer", "customer_address", "details", "title", "big", "sub", "key", "phone", "door_code"):
                if k == "customer" and base == "events.jsonl":
                    continue  # customer_coordinate_unavailable keeps a pseudonym there
                if re.search(r'"%s":' % k, dump):
                    dropped_keys.add(k)
            if base == "screens.jsonl":
                panel_left += sum(1 for s in row.get("texts", []) if anonymize.is_panel_line(s))
        count[base] = count.get(base, 0) + n
    hits = pii_hits(texts, sorted(truth["pii"]))
    check("anonymizer: no customer string (names, phones, addresses, notes, door codes, apartments)", not hits,
          "%d planted strings, leaked: %s" % (len(truth["pii"]), sorted(hits)[:8]))
    check("anonymizer: valid JSON lines", bad_json == 0, "bad=%d" % bad_json)
    check("anonymizer: every lat/lon <= 3 decimals and loc = {lat, lon, acc, age_s}", geo_bad == 0 and gps_bad == 0, "rows=%d gps=%d" % (geo_bad, gps_bad))
    check("anonymizer: dropped keys absent (nodes, raw, customer, customer_address, details, notif texts)", not dropped_keys, str(sorted(dropped_keys)))
    check("anonymizer: KuryePlus panel lines removed", panel_left == 0, "left=%d" % panel_left)
    check("anonymizer: unknown file excluded", not any("unknown-file" in k for k in files) and K.MARKER in files)
    check("anonymizer: marker row counts", marker and all(marker["files"].get(k) == v for k, v in count.items()), str(marker["files"] if marker else ""))
    # fees, times, km and restaurants unchanged
    anon = K.from_memory(files)
    ra = [r for _, r in raw.jsonl("orders.jsonl")]
    an = [r for _, r in anon.jsonl("orders.jsonl")]
    same = len(ra) == len(an)
    for a, b in zip(ra, an):
        for k in ("t", "type", "gross", "net", "km", "total_min", "wait_min", "restaurant", "offer_id", "id", "reason"):
            same &= a.get(k) == b.get(k)
        for part in ("record",):
            x, y = a.get(part) or {}, b.get(part) or {}
            for k in ("accepted_at", "completed_at", "distance_m", "tip", "commission_pct", "phase"):
                same &= x.get(k) == y.get(k)
            for p, q in zip(x.get("pickups", []), y.get("pickups", [])):
                for k in ("restaurant", "address", "arrived_at", "picked_at"):
                    same &= p.get(k) == q.get(k)
        oa, ob = (a.get("offer") or {}).get("data") or {}, (b.get("offer") or {}).get("data") or {}
        for k in ("gross", "km", "min_lo", "min_hi", "restaurant", "restaurant_address", "at"):
            same &= oa.get(k) == ob.get(k)
    check("anonymizer: fees, times, km and restaurant data unchanged", same, "%d order rows" % len(ra))
    cust = [c.get("id") for _, r in anon.jsonl("orders.jsonl") for c in ((r.get("record") or {}).get("customers") or [])]
    check("anonymizer: customer ids pseudonymised (incl. name:<customer>)", cust and not any(str(c).startswith("name:") for c in cust)
          and len(set(cust)) > 1, "%d ids" % len(cust))


def analysis_tests(res, truth, an):
    fits = {(f["platform"], f["x"]): f for f in res["fees"]["fits"]}
    w, b = fits.get(("wolt", "km")), fits.get(("bolt", "gösterilen dk"))
    check("Theil-Sen Wolt fee vs km slope within 5 %", w and w["fit"] and rel(w["fit"]["b"], synth.FEE["wolt"]["b"]) <= 0.05,
          "b=%.4f true %.2f n=%d" % (w["fit"]["b"], synth.FEE["wolt"]["b"], w["n"]) if w and w["fit"] else "no fit")
    check("Theil-Sen Bolt fee vs shown minutes slope within 5 %", b and b["fit"] and rel(b["fit"]["b"], synth.FEE["bolt"]["b"]) <= 0.05,
          "b=%.4f true %.2f n=%d" % (b["fit"]["b"], synth.FEE["bolt"]["b"], b["n"]) if b and b["fit"] else "no fit")
    # Kaplan-Meier medians vs the latent (uncensored) spell durations
    cells = {}
    for z, blk, d_true, d_obs, ev in truth["spells"]:
        cells.setdefault((z, blk), []).append(d_true)
    km = {(c["zone"], c["block"]): c for c in res["h5"]["cells"]}
    tested, worst = 0, 0.0
    ok = True
    for cell, ds in cells.items():
        if len(ds) < 40:
            continue
        tested += 1
        est = km.get(cell, {}).get("median_min")
        diff = abs(est - D.median(ds)) if est is not None else float("inf")
        worst = max(worst, diff)
        ok &= diff <= D.KM_BIN_MIN
    check("Kaplan-Meier median within one bin (%.0f min) of the latent median" % D.KM_BIN_MIN, ok and tested >= 3,
          "%d cells, worst %.2f min" % (tested, worst))
    check("idle spells from both sources (reconstructed + economics-idle v1)", len(res["h5"]["sources"]) == 2, str(dict(res["h5"]["sources"])))
    rates = {(r["zone"], r["block"]): r for r in res["rates"]["cells"]}
    tested, worst, ok = 0, 0.0, True
    for cell, (h, n) in truth["free"].items():
        if n < 40:
            continue
        tested += 1
        got = rates.get(cell, {}).get("raw_per_free_h")
        e = rel(got, n / h) if got is not None else float("inf")
        worst = max(worst, e)
        ok &= e <= 0.10
    check("offer rates per free hour within 10 % (cells with >= 40 offers)", ok and tested >= 3, "%d cells, worst %.1f %%" % (tested, 100 * worst))
    st = [r for r in res["rates"]["cells"] if r["offers_free"] >= 20 and r["free_h"] >= 5]
    check("Gamma-Poisson status personal for gated cells", st and all(r["status"] == "personal" for r in st), "%d cells" % len(st))
    day_t, combo_t = truth_hours(truth)
    day_p = {r["key"]: r["online_h"] for r in res["net"]["by_day"]}
    dd = max(abs(day_p.get(d, 0.0) - h) for d, h in day_t.items())
    mid = [d for d in day_t if day_t[d] < 7 and d in day_p]
    check("midnight crossing: online hours split per Baku day", dd < 0.01 and len(mid) >= 3, "max diff %.4f h, short days %s" % (dd, mid[:3]))
    tr = an.trips
    ids = [t["id"] for t in tr]
    mids = sum(1 for t in tr if t["completed_at"] and K.baku_day(t["accepted_at"]) != K.baku_day(t["completed_at"]))
    check("midnight crossing: trips counted once, on the accept day", len(ids) == len(set(ids)) and mids == truth["midnight_trips"] >= 1
          and sum(r["trips"] + r["unknown_fee_trips"] for r in res["net"]["by_day"]) == sum(truth["trips_by_day"].values()),
          "%d trips across midnight, %d delivered" % (mids, sum(r["trips"] + r["unknown_fee_trips"] for r in res["net"]["by_day"])))
    combo_p = {r["key"]: r["online_h"] for r in res["net"]["by_combo"]}
    cm = max(abs(combo_p.get(D.COMBO_LABEL[k], 0.0) - v) for k, v in combo_t.items())
    check("two apps: hours per apps-online combination", cm < 0.01 and "Wolt + Bolt" in combo_p and "yalnız Wolt" in combo_p,
          "max diff %.4f h; %s" % (cm, {k: round(v, 1) for k, v in combo_p.items()}))
    tnet = sum(truth["net_by_day"].values())
    check("net total equals the generator's", abs(res["net"]["total"]["net"] - tnet) < 0.01, "%.2f vs %.2f" % (res["net"]["total"]["net"], tnet))
    h2 = res["h2"]
    share = h2["during_trip"]["during"] / float(h2["during_trip"]["n"])
    tshare = truth["offers_during_trip"] / float(truth["offers_total"])
    check("H2 share of offers during a trip", abs(share - tshare) < 0.005, "%.3f vs %.3f" % (share, tshare))
    tmed = D.median(truth["delivered_to_next_offer"])
    check("H2 delivered -> next offer median", abs(h2["delay_min"]["median"] - tmed) < 0.5, "%.2f vs %.2f min" % (h2["delay_min"]["median"], tmed))
    dmed = D.median(truth["drop_to_pickup_km"])
    check("H2 last drop -> next pickup distance median within 10 %", rel(h2["drop_to_pickup_km"]["median"], dmed) <= 0.10,
          "%.2f vs %.2f km (n %d vs %d)" % (h2["drop_to_pickup_km"]["median"], dmed, h2["drop_to_pickup_km"]["n"], len(truth["drop_to_pickup_km"])))
    check("H4 far-by-road pairs (ratio > 1.6, full route)", res["h4"]["full_route"]["far"] == truth["far_by_road"] > 0,
          "%d vs %d" % (res["h4"]["full_route"]["far"], truth["far_by_road"]))
    c6 = res["h6"]["counts"]
    check("H6 offline-for-declines, banners, performance snapshots", c6.get("offline_for_declines") == truth["penalties"] == 3
          and c6.get("banner") == truth["banners"] and c6.get("performance_snapshot") == truth["performance"], str(dict(c6)))
    pooled = res["h6"]["pooled"].get("offline_for_declines", {})
    check("H6 offers/hour before and after, with n", pooled.get("before_n", 0) > 0 and pooled.get("after_n", 0) > 0, str(pooled)[:120])
    q = res["quality"]
    check("data quality: unreadable fees per platform", all(q["fees"][p]["unreadable"] == truth["unreadable"].get(p, 0) for p in q["fees"]),
          str({p: v["unreadable"] for p, v in q["fees"].items()}))
    check("data quality: OCR share", q["screens"].get("ocr", 0) == truth["ocr_screens"] and sum(q["screens"].values()) == truth["screens"],
          "%d / %d" % (q["screens"].get("ocr", 0), sum(q["screens"].values())))
    check("data quality: trips without an accept tap (hayalet)", q["ghost"]["no_tap"] == truth["ghost"] + truth["noclick"]
          and q["ghost"]["ghost_like"] == truth["ghost"], "%d (%d ghost-like)" % (q["ghost"]["no_tap"], q["ghost"]["ghost_like"]))
    check("schema dispatch: online-sessions schema 1 + 2 read, unknown version skipped and counted",
          q["sessions"]["schema1"] > 0 and q["stats"].get("unknown_schema:online-sessions.jsonl:99") == truth["unknown_schema_rows"],
          "schema1=%d stats=%s" % (q["sessions"]["schema1"], q["stats"]))


def mixed_input_tests(tmp):
    """The anonymity gate is per input: a marker in one input never lets another, full export through.

    Two small exports (3 days each, other seeds) keep this fast: A anonymised as by the app, B full.
    """
    files_a, truth_a = synth.generate(days=3, seed=5)
    anon_files = anonymize.Anonymizer().export(K.from_memory(files_a), version_name="selftest")
    anon_zip = os.path.join(tmp, "KuryePlus_analiz_a.zip")
    anonymize.write_zip(anon_files, anon_zip)
    files_b, truth_b = synth.generate(days=3, seed=11)
    raw_b = synth.write(files_b, os.path.join(tmp, "KuryePlus_all_logs_b.zip"), as_zip=True)
    pii = sorted(truth_a["pii"] | truth_b["pii"])
    raw_b_exp = K.load(raw_b)
    planted = pii_hits(list(raw_b_exp.files().values()), sorted(truth_b["pii"]))
    check("mixed inputs: the second full export carries planted customer strings", len(planted) >= 10 and not raw_b_exp.anonymous,
          "%d planted strings present" % len(planted))
    side = os.path.join(tmp, "side_by_side")
    synth.write(dict([("anonim/" + k, v) for k, v in anon_files.items()] + [("tam/" + k, v) for k, v in files_b.items()]), side)
    refused = []
    for i, inputs in enumerate(([anon_zip, raw_b], [raw_b, anon_zip], [side])):
        out = os.path.join(tmp, "out_mixed_refused_%d" % i)
        with contextlib.redirect_stdout(io.StringIO()):
            code = run.main(inputs + ["--out", out])
        refused.append((code, os.path.exists(os.path.join(out, "rapor.md"))))
    check("anonymous + full inputs refused without --redact (both orders, and side by side in one folder); no rapor.md",
          all(r == (3, False) for r in refused), str(refused))
    logs = []
    code, exp, note = run.prepare([anon_zip, raw_b], True, log=logs.append)
    anon_n = len(K.from_memory(anon_files).jsonl("orders.jsonl"))
    raw_n = len(raw_b_exp.jsonl("orders.jsonl"))
    rows_n = len(exp.jsonl("orders.jsonl")) if exp else 0
    hits = pii_hits(list(exp.files().values()), pii) if exp else {"(no export)"}
    check("anonymous + full inputs with --redact: full part anonymised before the merge, no planted string in the analysed rows",
          code == 0 and not hits and exp.anonymous and rows_n == anon_n + raw_n > anon_n and note and note.get("known_pii_scrubbed", 0) > 0
          and set(raw_b_exp.days) <= set(exp.days) and not any("gerekmiyor" in m for m in logs),
          "exit %d, order rows %d = %d + %d, leaked: %s" % (code, rows_n, anon_n, raw_n, sorted(hits)[:5]))
    out = os.path.join(tmp, "out_mixed")
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        code = run.main([anon_zip, raw_b, "--out", out, "--redact", "--hub", "default"])
    texts = []
    if os.path.isdir(out):
        for n in os.listdir(out):
            with open(os.path.join(out, n), encoding="utf-8") as fh:
                texts.append(fh.read())
    hits = pii_hits(texts, pii)
    check("run.py anonymous + full --redact: report written, no PII in outputs", code == 0 and texts and not hits
          and "gerekmiyor" not in buf.getvalue(), "exit %d, leaked: %s" % (code, sorted(hits)[:5]))


def main():
    t_start = time.time()
    unit_tests()
    tmp = tempfile.mkdtemp(prefix="kl-analysis-")
    try:
        files, truth = synth.generate(days=30, seed=7)
        raw_zip = synth.write(files, os.path.join(tmp, "KuryePlus_all_logs.zip"), as_zip=True)
        raw = K.load(raw_zip)
        check("synthetic raw export has no marker", raw.marker is None and len(raw.days) == 31, "%d days" % len(raw.days))
        out_refused = os.path.join(tmp, "out_refused")
        with contextlib.redirect_stdout(io.StringIO()):
            code = run.main([raw_zip, "--out", out_refused])
        check("raw export refused without --redact", code == 3 and not os.path.exists(os.path.join(out_refused, "rapor.md")), "exit %d" % code)
        a = anonymize.Anonymizer()
        anon_files = a.export(raw, version_name="selftest")
        anonymizer_tests(raw, anon_files, truth)
        anon_exp = K.from_memory(anon_files, "anon")
        t0 = time.time()
        an = D.Analysis(anon_exp, hub=K.HUB_DEFAULT)
        res = an.run()
        analysis_tests(res, truth, an)
        auto = D.Analysis(anon_exp)
        check("hub: coordinate-wise median of pickups after >= 30 pickups", auto.hub_source.startswith("teslim alma medyanı")
              and K.haversine_km(auto.hub[0], auto.hub[1], *K.HUB_DEFAULT) < 3.0, "%s %s" % (auto.hub_source, auto.hub))
        out = os.path.join(tmp, "out")
        t1 = time.time()
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            code = run.main([raw_zip, "--out", out, "--redact", "--hub", "default"])
        dt = time.time() - t1
        check("run.py --redact on 30 synthetic days", code == 0 and os.path.exists(os.path.join(out, "rapor.md")), buf.getvalue().strip()[:160])
        check("runtime < 60 s on 30 synthetic days", dt < 60.0, "%.1f s" % dt)
        texts = []
        for n in os.listdir(out):
            with open(os.path.join(out, n), encoding="utf-8") as fh:
                texts.append(fh.read())
        hits = pii_hits(texts, sorted(truth["pii"]))
        rests = [r[0] for r in synth.RESTAURANTS]
        check("no PII in outputs (and no restaurant names, ids or addresses)", not hits and not pii_hits(texts, rests)
              and not re.search(r"(offer|trip)-[0-9]{10,}", "\n".join(texts)), "leaked: %s" % sorted(hits)[:5])
        md = open(os.path.join(out, "rapor.md"), encoding="utf-8").read()
        heads = ["1. Çevrimiçi saat başına net kazanç", "2. Boştayken saat başına teklif", "3. Ücret uyumu", "4. Art arda teklifler",
                 "5. Havadan yakın, yoldan uzak", "6. Boşta bekleme", "7. Reddetmeler", "8. Veri kalitesi"]
        check("report: eight Turkish sections, n stated, small samples marked", all(h in md for h in heads) and "n=" in md and "⚠" in md
              and "az veri" in md)
        anon_zip = os.path.join(tmp, "KuryePlus_analiz.zip")
        anonymize.write_zip(anon_files, anon_zip)
        out2 = os.path.join(tmp, "out2")
        with contextlib.redirect_stdout(io.StringIO()):
            code = run.main([anon_zip, "--out", out2, "--hub", "default"])
        same = code == 0 and open(os.path.join(out2, "rapor.md"), encoding="utf-8").read() == md
        check("anonymous ZIP accepted without --redact; same report as --redact (pseudonyms do not matter)", same, "exit %d" % code)
        mixed_input_tests(tmp)
        print("analysis %.1f s, total %.1f s" % (time.time() - t0, time.time() - t_start))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    failed = [r for r in RESULTS if not r[1]]
    print("\nselftest: %d/%d checks passed" % (len(RESULTS) - len(failed), len(RESULTS)))
    if failed:
        print("FAILED: " + "; ".join(r[0] for r in failed))
        return 1
    print("selftest OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
