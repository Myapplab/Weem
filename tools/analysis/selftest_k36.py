"""K36 self-test of the offline analysis pipeline (python3 -I tools/analysis/selftest_k36.py).

`economics-platform-history.jsonl` (schemaVersion 1, PlatformHistory.kt) holds the rows of the platforms' own earnings
lists (Wolt Gross earnings, Bolt All deliveries) with the KuryePlus order each one matched, or `missed_by_kuryeplus`.
This checks that kl_export reads it, that section 8 reports the miss rate per platform (rows of unknown day/clock are
counted on their own, never as missed), that anonymize.py keeps the rows and that the report shows the line.

Restaurant names are synthetic. Exit code 0 only when every check passes.
"""
import json
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.dont_write_bytecode = True  # keep the checkout clean (no __pycache__ next to the tool)
sys.path.insert(0, HERE)
import anonymize  # noqa: E402
import descriptive as D  # noqa: E402
import kl_export as K  # noqa: E402
import report  # noqa: E402

RESULTS = []
WOLT, BOLT = "com.wolt.courierapp", "com.bolt.food.courier"
DAY = "2026-10-08"
T0 = 1791450000000
NAME = "economics-platform-history.jsonl"


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok), detail))
    print("%s  %s%s" % ("PASS" if ok else "FAIL", name, (" — " + detail) if detail else ""))
    return ok


def row(platform, clock, restaurant, amount_text, missed, trip=None, state=None, day=DAY, match_day="screen", inferred=False):
    r = {"schemaVersion": 1, "source": "platform_history", "verified": True, "platform": platform,
         "pkg": WOLT if platform == "wolt" else BOLT, "observed_at": T0, "capture_source": "accessibility",
         "day": day, "day_source": "screen_header" if day else "not_on_screen", "match_day": match_day, "clock": clock,
         "listed_at": None if missed is None else T0, "restaurant": restaurant,
         "amount": float(amount_text.replace("AZN ", "").replace(" ₼", "").replace(",", ".")), "amount_text": amount_text,
         "currency": "AZN", "amount_basis": "gross_earnings_list" if platform == "wolt" else "unknown", "distance_km": None,
         "has_order_code": platform == "bolt", "day_total_text": None, "day_deliveries": None,
         "key": "%s|%s|%s|%s" % (platform, DAY, clock, restaurant), "missed_by_kuryeplus": missed,
         "confirms_inferred_delivery": inferred,
         "match": None if trip is None else {"trip_id": trip, "by": "restaurant_time", "dt_s": 60, "trip_state": state, "trip_gross": None}}
    return r


def export():
    rows = [row("wolt", "12:40", "Test Burger", "AZN 2.91", False, "trip-1", "delivered_inferred", None, "capture_day", True),
            row("wolt", "13:22", "Pizza Testo", "AZN 3.96", True, day=None, match_day="capture_day"),
            row("wolt", "14:05", "Test Kafe", "AZN 2.40", True, day=None, match_day="capture_day"),
            row("wolt", "21:10", "Gec Test", "AZN 3.10", None, day=None, match_day="capture_day"),
            row("bolt", "13:52", "Test Doner", "4,72 ₼", False, "trip-2", "delivered"),
            row("bolt", "15:10", "Cay Evi Test", "3,46 ₼", False, "trip-3", "delivered"),
            row("bolt", "16:45", "Test Plov", "2,72 ₼", True),
            {"schemaVersion": 7, "platform": "bolt", "missed_by_kuryeplus": True}]
    text = "\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n"
    return {"KuryePlus/%s/%s" % (DAY, NAME): text.encode("utf-8")}


def main():
    files = export()
    raw = K.from_memory(files, "k36")
    check("kl_export: the log is a known file", NAME in K.LOG_NAMES and not raw.ignored, str(raw.ignored))
    check("kl_export: schema 1 rows read, other schemas counted", len(K.platform_history(raw)) == 7
          and raw.stats.get("unknown_schema:%s:7" % NAME) == 1, str(dict(raw.stats)))
    h = D.Analysis(raw).quality()["platform_history"]
    w, b = h.get("wolt", {}), h.get("bolt", {})
    check("quality: Wolt miss rate excludes the unknown row", w.get("missed") == 2 and w.get("matched") == 1 and w.get("unknown") == 1
          and abs(w.get("miss_rate") - 2 / 3) < 1e-9 and w.get("day_assumed") == 4 and w.get("confirms_inferred") == 1, json.dumps(w))
    check("quality: Bolt miss rate", b.get("n") == 3 and b.get("missed") == 1 and abs(b.get("miss_rate") - 1 / 3) < 1e-9, json.dumps(b))

    anon = anonymize.Anonymizer(b"k36-test-secret").export(raw)
    text = b"".join(v for k, v in anon.items() if k.endswith(NAME)).decode("utf-8")
    kept = [json.loads(line) for line in text.splitlines() if line.strip()]
    check("anonymize: rows kept with the miss flag", sum(1 for r in kept if r.get("missed_by_kuryeplus") is True) >= 3, "%d rows" % len(kept))

    out = tempfile.mkdtemp(prefix="kl-k36-")
    try:
        report.build(D.analyse(K.from_memory(anon, "k36-anon")), out)
        with open(os.path.join(out, "rapor.md"), encoding="utf-8") as f:
            md = f.read()
        lines = [ln for ln in md.splitlines() if "kaçırdığı sipariş" in ln]
        check("report: one line per platform", len(lines) == 2 and any("2 / 3" in ln and "67%" in ln and "belirsiz: 1" in ln for ln in lines),
              " | ".join(ln.strip()[:120] for ln in lines))
    finally:
        shutil.rmtree(out, ignore_errors=True)

    failed = [r for r in RESULTS if not r[1]]
    print("\n%d/%d PASS" % (len(RESULTS) - len(failed), len(RESULTS)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
