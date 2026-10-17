"""Markdown + CSV report (Turkish headings, narrow tables for a phone screen) from descriptive.analyse().

Only aggregates leave this module: platforms, days, hour blocks, coarse zones, counts, AZN, minutes, km.
No restaurant names, addresses, ids or free text are written.
"""
import csv
import os

import kl_export as K

AZ = "az veri"
PLAT = {"wolt": "Wolt", "bolt": "Bolt"}
DECISION_TR = {"accepted_observed": "kabul", "declined_tap_observed": "red (dokunuş)", "declined_banner_observed": "red (uyarı şeridi)",
               "timeout_inferred": "süre doldu", "disappeared_unknown": "kayboldu (bilinmiyor)", "replaced_by_new_offer": "yeni teklifle değişti",
               "superseded_by_offline": "çevrimdışı oldu", "recording_stopped": "kayıt durdu", "open_or_unknown": "sonuç yok"}
KIND_TR = {"banner": "red uyarı şeridi", "offline_for_declines": "red yüzünden çevrimdışı", "performance_snapshot": "performans ekranı"}


def f(x, d=2):
    if x is None:
        return "–"
    if isinstance(x, bool):
        return "evet" if x else "hayır"
    if isinstance(x, int):
        return str(x)
    return ("%." + str(d) + "f") % x


def plat(p):
    return PLAT.get(p, p)


class Report:
    def __init__(self, out):
        self.out = out
        os.makedirs(out, exist_ok=True)
        self.md = []
        self.files = []

    def h(self, level, text):
        self.md.append("\n" + "#" * level + " " + text + "\n")

    def p(self, text):
        self.md.append(text + "\n")

    def table(self, header, rows):
        if not rows:
            self.p("_Veri yok (n=0)._")
            return
        self.md.append("| " + " | ".join(header) + " |")
        self.md.append("|" + "|".join("---" for _ in header) + "|")
        for r in rows:
            self.md.append("| " + " | ".join(str(c) for c in r) + " |")
        self.md.append("")

    def csv(self, name, header, rows):
        path = os.path.join(self.out, name)
        with open(path, "w", newline="", encoding="utf-8") as fh:
            w = csv.writer(fh)
            w.writerow(header)
            for r in rows:
                w.writerow(["" if c is None else c for c in r])
        self.files.append(name)

    def save(self):
        path = os.path.join(self.out, "rapor.md")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write("\n".join(self.md).strip() + "\n")
        self.files.insert(0, "rapor.md")
        return path


def mark(az):
    return " ⚠" if az else ""


def build(res, out):
    R = Report(out)
    m = res["meta"]
    R.h(1, "KuryePlus analiz raporu")
    days = m["days"]
    R.p("Kaynak: %s · gün: %d (%s – %s) · teklif n=%d · sipariş n=%d · çevrimiçi oturum n=%d" % (
        "anonim dışa aktarım" if m["marker"] else "dışa aktarım", len(days), days[0] if days else "–", days[-1] if days else "–",
        m["n_offers"], m["n_trips"], m["n_sessions"]))
    R.p("Merkez (bölge halkaları için): %.4f, %.4f — %s. Bölgeler: Z1 < 2.5 km, Z2 < 6 km, Z3 < 12 km, Z4 ≥ 12 km." % (
        m["hub"][0], m["hub"][1], m["hub_source"]))
    R.p("Saat blokları (Bakü saati): " + ", ".join("%s %s" % (b, K.BLOCK_LABEL[b]) for b, _, _ in K.HOUR_BLOCKS) + ".")
    R.p("Bu rapor yalnızca betimler; teklif kabul/ret önerisi veya karar değildir. Her sayının yanında n vardır; "
        "⚠ = **%s** (küçük örnek, dikkatle okuyun)." % AZ)

    # 1 -----------------------------------------------------------------------------------------
    net = res["net"]
    R.h(2, "1. Çevrimiçi saat başına net kazanç (H7)")
    t = net["total"]
    R.p("Toplam: %s çevrimiçi saat (en az bir uygulama açık), %d teslim edilen sipariş, net %s ₼ → **%s ₼/saat**." % (
        f(t["online_h"], 1), t["trips"], f(t["net"]), f(t["net_per_h"])))
    R.p("Net = gösterilen ücret × (1 − komisyon) + bahşiş; komisyon kayıttaki değer: " +
        ", ".join("%s %%%s" % (plat(p), f(c, 0)) for p, c in net["commission_note"]) + ". Sipariş, kabul edildiği güne ve bloğa yazılır.")
    for key, title, fname in (("by_day", "Güne göre", "1_net_gun.csv"), ("by_block", "Saat bloğuna göre", "1_net_blok.csv"),
                              ("by_platform", "Platforma göre (o platformun çevrimiçi saatleri)", "1_net_platform.csv"),
                              ("by_combo", "Açık uygulamalara göre", "1_net_uygulama.csv")):
        rows = net[key]
        R.h(3, title)
        R.table(["", "saat", "sipariş n", "net ₼", "₼/saat"],
                [[plat(r["key"]) if key == "by_platform" else r["key"], f(r["online_h"], 1), r["trips"], f(r["net"]),
                  f(r["net_per_h"]) + mark(r["az_veri"])] for r in rows])
        R.csv(fname, ["anahtar", "cevrimici_saat", "siparis_n", "net_azn", "azn_saat", "ucreti_okunamayan_siparis", "az_veri"],
              [[r["key"], round(r["online_h"], 3), r["trips"], round(r["net"], 2), None if r["net_per_h"] is None else round(r["net_per_h"], 3),
                r["unknown_fee_trips"], r["az_veri"]] for r in rows])

    # 2 -----------------------------------------------------------------------------------------
    rt = res["rates"]
    R.h(2, "2. Boştayken saat başına teklif — bölge × saat bloğu (H1)")
    R.p("Boş = en az bir uygulama çevrimiçi ve aktif sipariş yok. Ham oran = n teklif ÷ boş saat. Daraltılmış oran = Gamma–Poisson "
        "(SPEC §16.1: öncül 6/saat × bölge × blok çarpanı, a₀ = 4; durum: personal ≥ 20 teklif ve ≥ 5 saat, blend > 0 teklif, prior).")
    R.table(["bölge·blok", "boş saat", "teklif n", "ham/saat", "daraltılmış", "çevrimiçi/saat (n)"],
            [["%s·%s" % (r["zone"], r["block"]), f(r["free_h"], 1), r["offers_free"], f(r["raw_per_free_h"]),
              "%s (%s)%s" % (f(r["posterior_per_h"]), r["status"], mark(r["az_veri"])),
              "%s (%d)" % (f(r["per_online_h"]), r["offers_online"])] for r in rt["cells"]])
    R.p("Son sütun: sipariş sürerken gelenler dahil, çevrimiçi saat başına tüm teklifler (n = teklif sayısı).")
    R.csv("2_teklif_orani.csv", ["bolge", "blok", "bos_saat", "teklif_bosta_n", "ham_saat_basi", "oncul_saat_basi", "sonsal_saat_basi", "durum",
                                 "cevrimici_saat", "teklif_cevrimici_n", "cevrimici_saat_basi", "az_veri"],
          [[r["zone"], r["block"], round(r["free_h"], 3), r["offers_free"], r["raw_per_free_h"] and round(r["raw_per_free_h"], 3),
            round(r["prior_per_h"], 3), round(r["posterior_per_h"], 3), r["status"], round(r["online_h"], 3), r["offers_online"],
            r["per_online_h"] and round(r["per_online_h"], 3), r["az_veri"]] for r in rt["cells"]])
    v = rt["v2_check"]
    if v["idle_rows"]:
        R.p("Kontrol: economics-idle.jsonl %d gün, %d satır, %s boş saat; aynı günlerde yeniden kurulan boş süre %s saat." % (
            v["days"], v["idle_rows"], f(v["idle_h"], 1), f(v["reconstructed_free_h"], 1)))
    if any(r["zone"] == "unknown" for r in rt["cells"]):
        R.p("`unknown` bölge: o anda GPS konumu yok (öncül için Z2 çarpanı 1.0 kullanıldı).")

    # 3 -----------------------------------------------------------------------------------------
    fe = res["fees"]
    R.h(2, "3. Ücret uyumu — Theil–Sen (H3)")
    R.p("ücret ≈ a + b·x (eğimlerin medyanı; uç değerlere dayanıklı). Kapı: n ≥ 15 ve x aralığı ≥ 2. Artık MAD = |artık − medyan| medyanı. "
        "Ücret tahmin etmek için değil; tarifeyi ve sapmaları görmek için. Bonuslu olduğu bilinen teklifler çıkarıldı (n=%d)." % fe["boost_excluded"])
    rows, big = [], []
    for r in fe["fits"]:
        if r["fit"] is None:
            rows.append([plat(r["platform"]), r["x"], r["n"], "–", "–"])
            continue
        rows.append([plat(r["platform"]), r["x"], r["n"], "%s + %s·x%s" % (f(r["fit"]["a"]), f(r["fit"]["b"], 3), mark(r["az_veri"])), f(r["mad"])])
        for b in r["largest"]:
            big.append([plat(r["platform"]), r["x"], b["when"], f(b["x"], 1), f(b["fee"]), f(b["fitted"]), f(b["residual"]), b["zone"]])
    R.table(["platform", "x", "n", "uyum", "MAD ₼"], rows)
    R.csv("3_ucret_uyumu.csv", ["platform", "x", "n", "a", "b", "x_min", "x_max", "artik_mad", "artik_sd_dayanikli", "kapi_gecti"],
          [[r["platform"], r["x"], r["n"]] + ([round(r["fit"]["a"], 4), round(r["fit"]["b"], 4), r["x_range"][0], r["x_range"][1],
                                               round(r["mad"], 4), round(r["sd_robust"], 4), r["fit"]["gatePassed"]] if r["fit"] else [None] * 7)
           for r in fe["fits"]])
    if big:
        R.h(3, "En büyük artıklar (her uyum için 5)")
        R.table(["zaman", "x", "ücret", "uyum", "artık"], [["%s %s" % (b[2], b[0]), "%s %s" % (b[1], b[3]), b[4], b[5], b[6]] for b in big])
        R.csv("3_ucret_artiklar.csv", ["platform", "x_turu", "zaman", "x", "ucret", "uyum", "artik", "bolge"], big)

    # 4 -----------------------------------------------------------------------------------------
    h2 = res["h2"]
    R.h(2, "4. Art arda teklifler (H2)")
    d = h2["delay_min"]
    R.p("Teslimden sonraki ilk teklife kadar: medyan **%s dk** (çeyrekler %s–%s), n=%d; teklif gelmeden çevrimdışı/vardiya sonu n=%d.%s" % (
        f(d["median"], 1), f(d["p25"], 1), f(d["p75"], 1), d["n"], d["censored"], " ⚠ " + AZ if d["az_veri"] else ""))
    dt = h2["during_trip"]
    share = dt["during"] / float(dt["n"]) if dt["n"] else None
    R.p("Sipariş sürerken gelen teklifler: %d / %d = **%s**." % (dt["during"], dt["n"], ("%%%.0f" % (100 * share)) if share is not None else "–"))
    R.table(["platform", "teklif n", "sipariş sırasında", "pay"],
            [[plat(p), v["n"], v["during"], "%%%.0f" % (100.0 * v["during"] / v["n"]) if v["n"] else "–"] for p, v in sorted(dt["by_platform"].items())])
    dd = h2["drop_to_pickup_km"]
    R.p("Son teslim noktası → sonraki restoran (kuş uçuşu): medyan **%s km** (çeyrekler %s–%s), n=%d.%s" % (
        f(dd["median"]), f(dd["p25"]), f(dd["p75"]), dd["n"], " ⚠ " + AZ if dd["az_veri"] else ""))
    R.csv("4_ardisik.csv", ["olcu", "n", "medyan", "p25", "p75", "sansurlu_veya_ek"],
          [["teslim_sonrasi_ilk_teklif_dk", d["n"], d["median"], d["p25"], d["p75"], d["censored"]],
           ["siparis_sirasinda_teklif", dt["n"], share, None, None, dt["during"]],
           ["son_teslim_sonraki_restoran_km", dd["n"], dd["median"], dd["p25"], dd["p75"], None]])

    # 5 -----------------------------------------------------------------------------------------
    h4 = res["h4"]
    R.h(2, "5. Havadan yakın, yoldan uzak (H4)")
    R.p("Oran = platformun gösterdiği km ÷ kuş uçuşu km. Tam rota: kurye (teklif anı) → restoran → teslim noktası (yalnız kabul edilen ve "
        "teslim edilen siparişler). Yalnız yaklaşma: kurye → restoran (gösterilen km teslimi de içeriyorsa oran doğal olarak büyüktür). "
        "Restoran konumu karttaki Plus Code/DMS'den veya teslim alma anındaki GPS'ten; adres geocode edilmez. Yalnız bölgeler gösterilir.")
    rows = []
    for key, name in (("full_route", "tam rota"), ("approach_only", "yalnız yaklaşma")):
        s = h4[key]
        rows.append([name, s["n"], f(s["median"]), "%s–%s" % (f(s["p10"]), f(s["p90"])), "%d%s" % (s["far"], mark(s["az_veri"]))])
    R.table(["ölçü", "n", "medyan", "P10–P90", "> 1.6"], rows)
    pr = h4["full_route"]["pairs"]
    if pr:
        R.h(3, "Tam rotada oran > 1.6 olan bölge çiftleri (kurye → restoran → teslim)")
        R.table(["bölgeler", "n", "medyan oran"], [[" → ".join(p["zones"]), p["n"], f(p["median_ratio"])] for p in pr[:15]])
    R.csv("5_hava_yol.csv", ["olcu", "n", "p10", "medyan", "p90", "oran_gt_1_6", "dagilim"],
          [[k, h4[k]["n"], h4[k]["p10"], h4[k]["median"], h4[k]["p90"], h4[k]["far"],
            "; ".join("%s:%d" % kv for kv in sorted(h4[k]["bins"].items()))] for k in ("full_route", "approach_only")] +
          [["cift " + " > ".join(p["zones"]), p["n"], None, p["median_ratio"], None, p["n"], ""] for p in pr])

    # 6 -----------------------------------------------------------------------------------------
    h5 = res["h5"]
    R.h(2, "6. Boşta bekleme — Kaplan–Meier (H5)")
    R.p("Boş süre = teslimden (veya çevrimiçi olmaktan) bir sonraki kabule kadar. Kabulle bitmeyenler (çevrimdışı, vardiya sonu) "
        "sağdan sansürlü sayılır, atılmaz. S(t) = t dakikadan uzun bekleme olasılığı. Kaynak: " +
        ", ".join("%s n=%d" % kv for kv in sorted(h5["sources"].items())) + ".")
    R.table(["bölge·blok", "n (sansür)", "medyan dk", "S(10)", "S(20)"],
            [["%s·%s" % (c["zone"], c["block"]), "%d (%d)" % (c["n"], c["censored"]), f(c["median_min"], 1) + mark(c["az_veri"]),
              f(c["surv"][10]), f(c["surv"][20])] for c in h5["cells"]])
    R.csv("6_bosta_km.csv", ["bolge", "blok", "n", "olay", "sansurlu", "medyan_dk"] + ["S_%d" % t for t in sorted(h5["cells"][0]["surv"])] + ["az_veri"]
          if h5["cells"] else ["bolge"],
          [[c["zone"], c["block"], c["n"], c["events"], c["censored"], c["median_min"]] + [round(c["surv"][t], 4) for t in sorted(c["surv"])] +
           [c["az_veri"]] for c in h5["cells"]])

    # 7 -----------------------------------------------------------------------------------------
    h6 = res["h6"]
    R.h(2, "7. Reddetmeler, uyarılar, performans (H6)")
    R.p("Teklif sonuçları (SPEC §17.2 eşlemesi; 'reddetme sayılır' = dokunuş, uyarı şeridi, süre dolması, bilinmeyen kaybolma):")
    R.table(["platform", "sonuç", "n"], [[plat(k["platform"]), DECISION_TR.get(k["decision"], k["decision"]), k["n"]] for k in h6["kinds"]])
    R.csv("7_teklif_sonuclari.csv", ["platform", "sonuc", "n"], [[k["platform"], k["decision"], k["n"]] for k in h6["kinds"]])
    R.p("Olaylar: " + (", ".join("%s n=%d" % (KIND_TR.get(k, k), v) for k, v in sorted(h6["counts"].items())) or "yok (n=0)") +
        ". Önce = olaydan önceki 60 dk, sonra = tekrar çevrimiçi olduktan (veya olaydan) sonraki 60 dk; o platformun teklif/saat değeri.")
    R.table(["olay", "önce /saat (n)", "sonra /saat (n)"],
            [[KIND_TR.get(k, k), "%s (%d)" % (f(v["before_rate"]), v["before_n"]), "%s (%d)" % (f(v["after_rate"]), v["after_n"])]
             for k, v in sorted(h6["pooled"].items())])
    ev = [e for e in h6["events"] if e["kind"] != "performance_snapshot"]
    if ev:
        R.h(3, "Olay listesi")
        R.table(["zaman", "olay", "önce (n)", "sonra (n)"],
                [["%s %s" % (e["when"], plat(e["platform"])), KIND_TR.get(e["kind"], e["kind"]), "%s (%d)" % (f(e["before_rate"]), e["before_n"]),
                  "%s (%d)" % (f(e["after_rate"]), e["after_n"])] for e in ev[:40]])
    R.csv("7_olaylar.csv", ["zaman", "platform", "olay", "once_n", "once_saat", "once_saat_basi", "sonra_n", "sonra_saat", "sonra_saat_basi"],
          [[e["when"], e["platform"], e["kind"], e["before_n"], round(e["before_h"], 3), e["before_rate"], e["after_n"], round(e["after_h"], 3),
            e["after_rate"]] for e in h6["events"]])
    if h6["performance"]:
        R.h(3, "Performans ekranları")
        R.table(["zaman", "kabul", "tamamlama", "puan"],
                [["%s %s" % (p["when"], plat(p["platform"])), f(p["acceptanceRate"]), f(p["completionRate"]), f(p["rating"], 1)] for p in h6["performance"]])
        R.csv("7_performans.csv", ["zaman", "platform", "kabul_orani", "tamamlama_orani", "puan", "is_sayisi", "donem"],
              [[p["when"], p["platform"], p["acceptanceRate"], p["completionRate"], p["rating"], p["tasksDone"], p["periodLabel"]] for p in h6["performance"]])
    if h6["per_day"]:
        R.csv("7_gunluk_redler.csv", ["gun", "platform", "reddetme_sayilan"],
              [[d, p, n] for d, c in h6["per_day"].items() for p, n in sorted(c.items())])

    # 8 -----------------------------------------------------------------------------------------
    q = res["quality"]
    R.h(2, "8. Veri kalitesi")
    rows = [["okunamayan ücret " + plat(p), "%d / %d" % (v["unreadable"], v["n"])] for p, v in q["fees"].items()]
    scr = sum(q["screens"].values())
    rows.append(["OCR ekran satırı", "%d / %d" % (q["screens"].get("ocr", 0), scr)])
    g = q["ghost"]
    rows.append(["kabul dokunuşu olmadan açılan sipariş (hayalet)",
                 ("%d / %d" % (g["no_tap"], g["n_trips"])) + ("" if g["tap_logged"] else " (dokunuş kaydı yok; yalnız 'teklif görülmedi')")])
    rows.append(["  bunlardan teslim almadan sonuçsuz kapanan", "%d" % g["ghost_like"]])
    inf = q.get("inferred")
    if inf:
        rows.append(["doğrulanmamış teslim (sonraki sipariş müşteri aşamasına geçti; net toplamlarında yok)",
                     "%d / %d" % (inf["n"], inf["n_trips"]) + (" · brüt %.2f ₼ (fiyatı okunan %d)" % (inf["gross"], inf["priced"]) if inf["n"] else "")])
    cs = q.get("cash") or {}
    if cs.get("cash") or cs.get("card_inferred") or cs.get("answered"):
        rows.append(["Nakit / kart (K31, K32)",
                     "nakit %d · kart (tahmin) %d · görülmedi %d · nakit cevabı %d · nakit bahşiş toplamı %.2f ₼"
                     % (cs.get("cash", 0), cs.get("card_inferred", 0), cs.get("not_observed", 0), cs.get("answered", 0), cs.get("tip_sum", 0.0))
                     + (" · nakit tutarı okunamadı: %d" % cs["cash_amount_unread"] if cs.get("cash_amount_unread") else "")
                     + (" · eksi fark: %d" % cs["negative_tips"] if cs.get("negative_tips") else "")])
    for p, h in sorted((q.get("platform_history") or {}).items()):
        rate = "—" if h["miss_rate"] is None else "%.0f%%" % (100 * h["miss_rate"])
        rows.append(["KuryePlus'ın kaçırdığı sipariş (platform listesi) " + plat(p),
                     "%d / %d · oran %s" % (h["missed"], h["matched"] + h["missed"], rate)
                     + (" · gün ekranda yok, kayıt günü varsayıldı: %d" % h["day_assumed"] if h["day_assumed"] else "")
                     + (" · belirsiz: %d" % h["unknown"] if h["unknown"] else "")
                     + (" · doğrulanmamış teslimi onaylayan: %d" % h["confirms_inferred"] if h["confirms_inferred"] else "")])
    s = q["sessions"]
    rows.append(["kanıtsız oturum (şema 1 veya yalnız görev ekranı)", "%d / %d" % (s["no_evidence"], s["n"])])
    rows.append(["başı da sonu da görülmeyen oturum", "%d / %d" % (s["neither_observed"], s["n"])])
    rows.append(["GPS noktası", str(q["gps_fixes"])])
    for k, v in sorted(q["stats"].items()):
        rows.append(["okunamayan/atlanan: " + k, str(v)])
    if q["ignored_files"]:
        rows.append(["tanınmayan dosya (okunmadı)", str(q["ignored_files"])])
    R.table(["ölçü", "değer"], rows)
    R.csv("8_veri_kalitesi.csv", ["olcu", "deger"], rows)
    if g["rows"]:
        R.h(3, "Hayalet sipariş listesi (en çok 30)")
        R.table(["zaman", "durum", "neden"], [["%s %s" % (r["when"], plat(r["platform"])), r["phase"], r["reason"]] for r in g["rows"]])
    R.p("\n_Rapor yalnız kayıt dosyalarından üretilir; ekran okuma ve OCR hataları kayıtlara olduğu gibi yansır._")
    R.save()
    return R.files
