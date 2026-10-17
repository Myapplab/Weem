"""K34 self-test of the offline analysis pipeline (python3 -I tools/analysis/selftest_k34.py).

An order that KuryePlus closed because the next order (either app) reached the customer stage is an unverified
delivery: `orders.jsonl` event `delivered_inferred`, record keys `delivered_inferred_at/_by/_reason`, phase
END_UNCONFIRMED, no `completed_at` (DeliveryCore K34, docs/KURYELOG_ISTEKLER.md). This checks on a small raw export
built like the app writes it (event rows with `record`, `record_snapshot` rows) that

* kl_export.trips flags it, and only it (a later manual confirmation is a real delivery again);
* it is neither a delivery (H7 net, done counts) nor a ghost order, and section 8 reports it on its own line;
* anonymize.py keeps the three keys and drops the customer.

Names and addresses are synthetic. Exit code 0 only when every check passes.
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
T0 = 1791450000000  # 2026-10-08 13:00 Baku
CUSTOMER = "Customer Zeynalova"
ADDRESS = "Test küçəsi 39k, Bakı"


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok), detail))
    print("%s  %s%s" % ("PASS" if ok else "FAIL", name, (" — " + detail) if detail else ""))
    return ok


def ts(ms):
    return K.local(ms).strftime("%Y-%m-%dT%H:%M:%S.") + "%03d+04:00" % (ms % 1000)


def record(tid, pkg, accepted, phase, gross, picked=None, completed=None, missed=False, inferred=None):
    """RecordJson.trip shape (the keys the readers use)."""
    r = {"id": tid, "pkg": pkg, "day": DAY, "accepted_at": accepted, "phase": phase, "completed_at": completed,
         "expected_orders": 1, "distance_m": 2100.0, "tip": 0.0, "commission_pct": 20.0, "price_is_net": False,
         "missed_offer": missed, "session_id": None,
         "offer": None if gross is None else {"id": "offer-%d-1" % (accepted - 4000), "pkg": pkg, "day": DAY, "at": accepted - 4000,
                                              "data": {"pkg": pkg, "at": accepted - 4000, "stage": "OFFER", "gross": gross, "km": 4.2,
                                                       "customer_address": ADDRESS, "raw": [CUSTOMER, ADDRESS]}},
         "pickups": [] if picked is None else [{"restaurant": "Test Restoran", "address": "Test küç. 1", "picked_at": picked}],
         "customers": [{"id": "name:customer zeynalova", "delivered_at": completed, "arrived_at": None,
                        "details": {"name": CUSTOMER, "address": ADDRESS, "note": "Qapı kodu 7788"}}]}
    if inferred:
        r["delivered_inferred_at"], r["delivered_inferred_by"], r["delivered_inferred_reason"] = inferred
    return r


def raw_export():
    rows = []

    def event(at, typ, tid, pkg, rec, reason=None):
        rows.append({"t": ts(at), "type": typ, "pkg": pkg, "offer_id": None, "id": tid, "reason": reason, "record": rec})

    # Wolt order 1: customer card with the slider, then the next Wolt order reached its customer (inferred delivery).
    w1 = ("trip-%d-1" % T0, WOLT, T0)
    w2 = ("trip-%d-2" % (T0 + 602000), WOLT, T0 + 602000)
    inferred_at = T0 + 1200000
    event(T0, "accepted", w1[0], WOLT, record(w1[0], WOLT, T0, "TO_RESTAURANT", 3.94))
    event(inferred_at, "delivered_inferred", w1[0], WOLT,
          record(w1[0], WOLT, T0, "END_UNCONFIRMED", 3.94, picked=T0 + 300000,
                 inferred=(inferred_at, w2[0], "next_order_at_customer_after_slider")), "next_order_at_customer_after_slider")
    # Wolt order 2: really delivered (slider, then the waiting map).
    event(w2[2], "accepted", w2[0], WOLT, record(w2[0], WOLT, w2[2], "TO_RESTAURANT", 2.91))
    event(T0 + 1500000, "delivered", w2[0], WOLT, record(w2[0], WOLT, w2[2], "FINISHED", 2.91, picked=T0 + 900000,
                                                          completed=T0 + 1500000), "delivery_slider_then_waiting")
    # A Wolt card seen without its offer or pickup (missed offer), closed the same way: no pickup, still not a ghost.
    w3 = "trip-%d-3" % (T0 + 1600000)
    event(T0 + 1600000, "accepted", w3, WOLT, record(w3, WOLT, T0 + 1600000, "TO_CUSTOMER", None, missed=True), "active_screen_offer_missed")
    event(T0 + 2400000, "delivered_inferred", w3, WOLT,
          record(w3, WOLT, T0 + 1600000, "END_UNCONFIRMED", None, missed=True,
                 inferred=(T0 + 2400000, "trip-%d-4" % (T0 + 2000000), "next_order_at_customer")), "next_order_at_customer")
    # Bolt order 4: its customer card closed order 3; then delivered.
    b4 = "trip-%d-4" % (T0 + 2000000)
    event(T0 + 2000000, "accepted", b4, BOLT, record(b4, BOLT, T0 + 2000000, "TO_RESTAURANT", 4.49))
    event(T0 + 3000000, "delivered", b4, BOLT, record(b4, BOLT, T0 + 2000000, "FINISHED", 4.49, picked=T0 + 2300000,
                                                       completed=T0 + 3000000), "platform_completed")
    # A ghost: opened without a tap, closed without a pickup by the waiting map.
    g5 = "trip-%d-5" % (T0 + 3100000)
    event(T0 + 3100000, "accepted", g5, WOLT, record(g5, WOLT, T0 + 3100000, "TO_RESTAURANT", None, missed=True), "active_screen_offer_missed")
    event(T0 + 3200000, "end_unconfirmed", g5, WOLT, record(g5, WOLT, T0 + 3100000, "END_UNCONFIRMED", None, missed=True),
          "waiting_map_before_pickup")
    # Later snapshot of order 1 (record_snapshot), same state.
    rows.append({"t": ts(inferred_at + 2000), "type": "record_snapshot",
                 "record": record(w1[0], WOLT, T0, "END_UNCONFIRMED", 3.94, picked=T0 + 300000,
                                  inferred=(inferred_at, w2[0], "next_order_at_customer_after_slider"))})
    text = "\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n"
    return {"KuryePlus/%s/orders.jsonl" % DAY: text.encode("utf-8")}, w1[0], w2[0], w3, b4, g5


def main():
    files, w1, w2, w3, b4, g5 = raw_export()
    raw = K.from_memory(files, "k34")
    trips = {t["id"]: t for t in K.trips(raw)}
    check("trips: the inferred orders are flagged",
          trips[w1]["delivered_inferred"] and trips[w3]["delivered_inferred"] and trips[w1]["delivered_inferred_at"] == T0 + 1200000,
          "%s %s" % (trips[w1]["delivered_inferred"], trips[w3]["delivered_inferred"]))
    check("trips: real deliveries and the ghost are not",
          not any(trips[i]["delivered_inferred"] for i in (w2, b4, g5)) and trips[w2]["phase"] == "FINISHED")
    check("trips: an inferred order has no completion time", trips[w1]["completed_at"] is None and trips[w1]["phase"] == "END_UNCONFIRMED")

    # A manual confirmation afterwards (courier_manual_correction) is a delivery again; the history keys stay in the record.
    confirmed = record(w1, WOLT, T0, "FINISHED", 3.94, picked=T0 + 300000, completed=T0 + 1300000,
                       inferred=(T0 + 1200000, w2, "next_order_at_customer_after_slider"))
    later = dict(files)
    later["KuryePlus/%s/orders.jsonl" % DAY] = files["KuryePlus/%s/orders.jsonl" % DAY] + (json.dumps(
        {"t": ts(T0 + 1300000), "type": "delivered", "pkg": WOLT, "id": w1, "reason": "courier_manual_correction", "record": confirmed}) + "\n").encode()
    t2 = {t["id"]: t for t in K.trips(K.from_memory(later, "k34-confirmed"))}
    check("trips: confirmed by hand -> delivered, no longer inferred", t2[w1]["phase"] == "FINISHED" and not t2[w1]["delivered_inferred"])

    an = D.Analysis(raw)
    q = an.quality()
    inf = q["inferred"]
    check("quality: inferred line counts both, gross only of the priced one",
          inf["n"] == 2 and inf["priced"] == 1 and abs(inf["gross"] - 3.94) < 1e-9 and inf["n_trips"] == 5, json.dumps(inf))
    ghosts = [t["id"] for t in an.trips if t["ghost_like"]]
    check("quality: an inferred order without pickup is not a ghost", ghosts == [g5], str(ghosts))
    done = {t["id"] for t in an.trips if t["completed_at"] is not None and t["phase"] == "FINISHED"}
    check("H7: only the real deliveries are done", done == {w2, b4}, str(sorted(done)))

    # anonymize.py keeps the flag and drops the customer.
    anon = anonymize.Anonymizer(b"k34-test-secret").export(raw)
    text = b"".join(v for k, v in anon.items() if k.endswith("orders.jsonl")).decode("utf-8")
    recs = [json.loads(line)["record"] for line in text.splitlines() if line.strip() and "record" in json.loads(line)]
    kept = [r for r in recs if r.get("id") == w1 and r.get("phase") == "END_UNCONFIRMED"]
    check("anonymize: keys kept on the inferred order",
          kept and all(r.get("delivered_inferred_at") == T0 + 1200000 and r.get("delivered_inferred_by") == w2
                       and r.get("delivered_inferred_reason") == "next_order_at_customer_after_slider" for r in kept), "%d rows" % len(kept))
    check("anonymize: event type and reason kept",
          any(json.loads(line).get("type") == "delivered_inferred" and json.loads(line).get("reason") == "next_order_at_customer"
              for line in text.splitlines() if line.strip()))
    check("anonymize: no customer string", CUSTOMER not in text and "Zeynalova" not in text and "39k" not in text and "7788" not in text)
    check("anonymize: other records keep their shape", all("delivered_inferred_at" not in r for r in recs if r.get("id") in (w2, b4, g5)))

    out = tempfile.mkdtemp(prefix="kl-k34-")
    try:
        exp = K.from_memory(anon, "k34-anon")
        report.build(D.analyse(exp), out)
        with open(os.path.join(out, "rapor.md"), encoding="utf-8") as f:
            md = f.read()
        line = next((ln for ln in md.splitlines() if "doğrulanmamış teslim" in ln), "")
        check("report: section 8 has its own line", "2 / 5" in line and "3.94" in line, line.strip()[:160])
        check("report: no customer string", CUSTOMER not in md and "Zeynalova" not in md)
    finally:
        shutil.rmtree(out, ignore_errors=True)

    failed = [r for r in RESULTS if not r[1]]
    print("\n%d/%d PASS" % (len(RESULTS) - len(failed), len(RESULTS)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
