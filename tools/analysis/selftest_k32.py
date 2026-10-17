"""K31/K32 self-test of the offline analysis pipeline (python3 -I tools/analysis/selftest_k32.py).

Trip records carry `payment_method` / `payment_source` / `cash_amount_azn` only when Wolt's delivery steps screen was seen
(absent = None + "not_observed"); `economics-cash-tips.jsonl` (schemaVersion 1, CashTips.kt) holds the courier's answers to
"how much cash did you receive?", append-only, the newest row per trip id wins. This checks that kl_export reads both and
joins them by trip id, that section 8 counts cash / card_inferred / not observed and the cash tips (negative ones flagged,
not clamped), that anonymize.py keeps the rows and that the report shows the line.

Exit code 0 only when every check passes.
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
WOLT = "com.wolt.courierapp"
DAY = "2026-10-08"
T0 = 1791450000000
NAME = "economics-cash-tips.jsonl"


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok), detail))
    print("%s  %s%s" % ("PASS" if ok else "FAIL", name, (" — " + detail) if detail else ""))
    return ok


def ts(i):
    return "2026-10-08T12:%02d:00.000+0400" % i


def record(tid, minute, payment=None, cash=None, tip=0.0):
    r = {"id": tid, "pkg": WOLT, "day": DAY, "accepted_at": T0 + minute * 60000, "phase": "FINISHED",
         "completed_at": T0 + (minute + 20) * 60000, "expected_orders": 1, "distance_m": 2000.0, "tip": tip,
         "commission_pct": 0.0, "pickups": [], "customers": []}
    if payment:
        r.update({"payment_method": payment, "payment_source": "wolt_cash_screen" if payment == "cash" else "wolt_delivery_steps_no_cash",
                  "cash_amount_azn": cash, "payment_at": T0 + (minute + 18) * 60000})
    return {"t": ts(minute), "type": "delivered", "pkg": WOLT, "id": tid, "record": r}


def answer(tid, at, received, cash, schema=1):
    tip = None if cash is None else round(received - cash, 2)
    return {"schemaVersion": schema, "kind": "cash_tip", "trip_id": tid, "pkg": WOLT, "platform": "wolt", "at": at, "via": "card",
            "received_azn": received, "cash_amount_azn": cash, "cash_amount_source": "wolt_cash_screen" if cash is not None else "not_observed",
            "tip_azn": tip, "tip_source": "user_cash_input" if tip is not None else None, "negative_tip": tip is not None and tip < 0,
            "delivery": "delivered"}


def export():
    orders = [record("t-cash", 1, "cash", 20.40, 4.60), record("t-neg", 2, "cash", 20.40, -0.40), record("t-unread", 3, "cash", None),
              record("t-card", 4, "card_inferred"), record("t-none", 5)]
    tips = [answer("t-cash", T0 + 30 * 60000, 30.0, 20.40),   # corrected below by a newer row
            answer("t-cash", T0 + 31 * 60000, 25.0, 20.40),
            answer("t-neg", T0 + 32 * 60000, 20.0, 20.40),
            answer("t-unread", T0 + 33 * 60000, 15.0, None),
            answer("t-gone", T0 + 34 * 60000, 10.0, 10.0),
            answer("t-card", T0 + 35 * 60000, 99.0, 1.0, schema=9)]
    j = lambda rows: ("\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n").encode("utf-8")
    return {"KuryePlus/%s/orders.jsonl" % DAY: j(orders), "KuryePlus/%s/%s" % (DAY, NAME): j(tips)}


def main():
    raw = K.from_memory(export(), "k32")
    check("kl_export: the log is a known file", NAME in K.LOG_NAMES and not raw.ignored, str(raw.ignored))
    trips = {t["id"]: t for t in K.trips(raw)}
    check("kl_export: payment fields, absent = None + not_observed",
          trips["t-cash"]["payment_method"] == "cash" and abs(trips["t-cash"]["cash_amount_azn"] - 20.40) < 1e-9
          and trips["t-card"]["payment_method"] == "card_inferred"
          and trips["t-none"]["payment_method"] is None and trips["t-none"]["payment_source"] == "not_observed",
          json.dumps({k: (v["payment_method"], v["payment_source"]) for k, v in trips.items()}))
    tips = K.cash_tips(raw)
    check("kl_export: newest answer per trip, other schemas counted", abs(tips["t-cash"]["received_azn"] - 25.0) < 1e-9
          and "t-card" not in tips and raw.stats.get("unknown_schema:%s:9" % NAME) == 1, str(dict(raw.stats)))
    q = D.Analysis(raw).quality()["cash"]
    check("quality: counts and the joined tips (negative kept)", q["cash"] == 3 and q["card_inferred"] == 1 and q["not_observed"] == 1
          and q["cash_amount_unread"] == 1 and q["answered"] == 3 and abs(q["tip_sum"] - 4.20) < 1e-9 and q["negative_tips"] == 1
          and q["answers_without_trip"] == 1, json.dumps(q))

    anon = anonymize.Anonymizer(b"k32-test-secret").export(raw)
    text = b"".join(v for k, v in anon.items() if k.endswith(NAME)).decode("utf-8")
    kept = [json.loads(line) for line in text.splitlines() if line.strip()]
    check("anonymize: answer rows kept", sum(1 for r in kept if r.get("kind") == "cash_tip") >= 5, "%d rows" % len(kept))
    anon_trips = {t["id"]: t for t in K.trips(K.from_memory(anon, "k32-anon"))}
    check("anonymize: payment fields kept on the trip records",
          sum(1 for t in anon_trips.values() if t["payment_method"] == "cash") == 3, json.dumps(sorted(str(t["payment_method"]) for t in anon_trips.values())))

    out = tempfile.mkdtemp(prefix="kl-k32-")
    try:
        report.build(D.analyse(K.from_memory(anon, "k32-anon")), out)
        with open(os.path.join(out, "rapor.md"), encoding="utf-8") as f:
            md = f.read()
        lines = [ln for ln in md.splitlines() if "Nakit / kart" in ln]
        check("report: one cash line", len(lines) == 1 and "nakit 3" in lines[0] and "eksi fark: 1" in lines[0], " | ".join(ln.strip()[:160] for ln in lines))
    finally:
        shutil.rmtree(out, ignore_errors=True)

    failed = [r for r in RESULTS if not r[1]]
    print("\n%d/%d PASS" % (len(RESULTS) - len(failed), len(RESULTS)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
