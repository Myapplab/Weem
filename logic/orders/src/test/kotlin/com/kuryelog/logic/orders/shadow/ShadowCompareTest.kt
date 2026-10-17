package com.kuryelog.logic.orders.shadow

import com.kuryelog.logic.capture.NoteFields
import com.kuryelog.logic.core.LogTime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A3.9 karşılaştırma kuralları (ORDERS.md 14.3, 16; CAPTURE.md 6.3, 6.5, 9): bilinen farklar (5104, 10000+, `click.text` redact,
 * "(WEEM)" metinleri, ekonomi dosyaları, YolGo alanları, hava, scooter) "beklenen" sayılır; sert ve yumuşak farklar ayrı ayrı yakalanır;
 * JSON ayrıştırılmış değerle karşılaştırılır.
 */
class ShadowCompareTest {
    private val baku = ZoneId.of("Asia/Baku")
    private val day = "2026-10-11"
    private val wolt = "com.wolt.courierapp"
    private val bolt = "com.bolt.deliverycourier"
    /** 2026-10-11 16:00:00 Bakü. */
    private val t0 = 1791720000000L

    private fun ts(ms: Long) = LogTime.ts(ms, baku)
    private fun export(name: String, files: Map<String, List<Any>>) =
        ShadowExport(name, files.mapValues { (_, rows) -> rows.joinToString("\n", postfix = "\n") })
    private fun compare(rs: Map<String, List<Any>>, weem: Map<String, List<Any>>) = ShadowCompare(export("rs", rs), export("weem", weem), baku).run()
    private fun ShadowReport.rules(level: Level) = diffs.filter { it.level == level }.map { it.rule }.toSet()

    private fun offerJson(id: String, pkg: String, at: Long, gross: Double?, restaurant: String?, extra: JSONObject.() -> Unit = {}) = JSONObject()
        .put("id", id).put("pkg", pkg).put("at", at)
        .put("data", JSONObject().put("pkg", pkg).put("at", at).put("stage", "OFFER").put("complete", true).put("source", "ocr").put("gross", gross).put("restaurant", restaurant))
        .apply(extra)
    private fun record(id: String, pkg: String, accepted: Long, gross: Double?, restaurant: String?, phase: String = "FINISHED", completed: Long? = accepted + 900_000,
                       extra: JSONObject.() -> Unit = {}, offerExtra: JSONObject.() -> Unit = {}) = JSONObject()
        .put("id", id).put("pkg", pkg).put("accepted_at", accepted).put("phase", phase).put("completed_at", completed).put("commission_pct", 0.0)
        .put("offer", offerJson("o-$id", pkg, accepted - 20_000, gross, restaurant, offerExtra))
        .put("pickups", JSONArray().put(JSONObject().put("restaurant", restaurant).put("arrived_at", accepted + 300_000).put("picked_at", accepted + 400_000)))
        .put("customers", JSONArray().put(JSONObject().put("id", "c1").put("details", JSONObject()).put("delivered_at", completed)))
        .apply(extra)
    /** Bir siparişin orders.jsonl satırları: teklif, kabul, teslim (kayıtla). */
    private fun orderRows(rec: JSONObject): List<JSONObject> {
        val id = rec.getString("id"); val pkg = rec.getString("pkg"); val acc = rec.getLong("accepted_at"); val offer = rec.getJSONObject("offer")
        return listOf(
            JSONObject().put("t", ts(offer.getLong("at"))).put("type", "offer").put("pkg", pkg).put("offer_id", offer.getString("id")).put("offer", offer),
            JSONObject().put("t", ts(acc)).put("type", "accepted").put("pkg", pkg).put("offer_id", offer.getString("id")).put("id", id).put("reason", "active_screen").put("record", rec),
            JSONObject().put("t", ts(rec.optLong("completed_at", acc + 900_000))).put("type", "delivered").put("pkg", pkg).put("id", id).put("record", rec))
    }
    private fun orders(vararg recs: JSONObject) = mapOf("$day/orders.jsonl" to recs.flatMap(::orderRows))

    @Test fun sameDataNoDifference() {
        val a = orders(record("trip-1", wolt, t0, 3.94, "ZOO 28"), record("trip-2", bolt, t0 + 3_600_000, 2.5, "Shanson Restaurant"))
        val r = compare(a, a)
        assertEquals(0, r.hard); assertEquals(0, r.soft); assertEquals(0, r.expected)
        assertEquals(2, r.days.single().matchedTrips); assertEquals(2, r.days.single().matchedOffers)
    }

    /** Kimlikler farklı, kabul anı ± 60 sn, restoran OCR kayması ("ZO0 28" = "ZOO 28"): eşlenir; süre farkı yumuşak. */
    @Test fun matchingByPlatformRestaurantAndTime() {
        val r = compare(orders(record("trip-1791720000000-1", wolt, t0, 3.94, "ZOO 28")), orders(record("trip-1791720020345-7", wolt, t0 + 20_345, 3.94, "ZO0 28")))
        assertEquals(r.diffs.toString(), 0, r.hard)
        assertEquals(1, r.days.single().matchedTrips)
        val accepted = r.diffs.single { it.rule == "order.accepted_at" }
        assertEquals(Level.SOFT, accepted.level); assertEquals(20_345L, accepted.deltaMs)
        assertTrue(r.rules(Level.SOFT).containsAll(setOf("order.accepted_at", "order.completed_at", "order.arrived_at", "order.picked_at", "offer.first_seen")))
    }

    @Test fun outsideTheWindowOrOtherRestaurantIsAHardDifference() {
        val late = compare(orders(record("a", wolt, t0, 3.94, "ZOO 28")), orders(record("b", wolt, t0 + 61_000, 3.94, "ZOO 28")))
        assertEquals(2, late.diffs.count { it.rule == "order.unmatched" })
        val other = compare(orders(record("a", wolt, t0, 3.94, "ZOO 28")), orders(record("b", wolt, t0, 3.94, "Bonbon PATISSERIE")))
        assertTrue(other.rules(Level.HARD).contains("order.unmatched"))
        val platform = compare(orders(record("a", wolt, t0, 3.94, "ZOO 28")), orders(record("b", bolt, t0, 3.94, "ZOO 28")))
        assertTrue(platform.rules(Level.HARD).contains("order.unmatched"))
    }

    @Test fun hardDifferencesOfAnOrder() {
        val rs = orders(record("a", wolt, t0, 3.94, "ZOO 28", extra = { put("payment_method", "cash").put("verified_by", "wolt_cash_collection") }),
            record("b", wolt, t0 + 7_200_000, 2.0, "Mix Point Cafe"))
        val weem = orders(record("a", wolt, t0, 3.84, "ZOO 28", phase = "END_UNCONFIRMED", completed = null, extra = { put("payment_method", "card") }))
        val r = compare(rs, weem)
        assertTrue(r.rules(Level.HARD).containsAll(setOf("order.outcome", "order.fee", "order.net", "order.payment", "order.verified_by", "order.unmatched",
            "day.trips", "day.orders", "day.delivered", "day.unconfirmed", "day.gross", "day.net", "offer.unmatched")))
        val fee = r.diffs.single { it.rule == "order.fee" }
        assertEquals("3.94", fee.rawstats); assertEquals("3.84", fee.weem)
        assertTrue(r.text().contains("SERT FARK VAR"))
    }

    @Test fun offerOutcomeKindIsHardReasonIsSoft() {
        fun rows(type: String, reason: String, gross: Double = 4.2) = mapOf("$day/orders.jsonl" to listOf(
            JSONObject().put("t", ts(t0)).put("type", "offer").put("pkg", wolt).put("offer_id", "o1").put("offer", offerJson("o1", wolt, t0, gross, "ZOO 28")),
            JSONObject().put("t", ts(t0 + 30_000)).put("type", type).put("pkg", wolt).put("offer_id", "o1").put("reason", reason)))
        val kind = compare(rows("rejected", "courier_declined"), rows("offer_unconfirmed", "expired_inferred"))
        assertEquals(setOf("offer.outcome"), kind.rules(Level.HARD))
        val reason = compare(rows("rejected", "courier_declined"), rows("rejected", "offer_to_idle_map"))
        assertEquals(0, reason.hard); assertEquals(setOf("offer.reason"), reason.rules(Level.SOFT))
        val fee = compare(rows("rejected", "courier_declined"), rows("rejected", "courier_declined", gross = 4.3))
        assertEquals(2, fee.diffs.count { it.rule == "offer.unmatched" })
    }

    /** Ekonomi dosyaları A4.1'e kadar yalnız Rawstats'ta; nakit bahşiş ve cep nakdi cevapları gölge modda bir uygulamada. */
    @Test fun economicsFilesAndAnswersAreExpected() {
        val row = JSONObject().put("at", t0).put("pkg", wolt)
        val rs = mapOf("$day/economics-exposure.jsonl" to listOf(row, row), "$day/economics-offers.jsonl" to listOf(row), "$day/economics-energy.jsonl" to listOf(row),
            "$day/scooter.jsonl" to listOf(row), "$day/economics-cash-tips.jsonl" to listOf(row), "$day/economics-pocket-cash.jsonl" to listOf(row))
        val r = compare(rs, emptyMap())
        assertEquals(0, r.hard); assertEquals(0, r.soft)
        assertEquals(setOf("file.not_yet_written", "file.user_answers"), r.rules(Level.EXPECTED))
        assertEquals(7, r.expected)
    }

    /** Bildirim numaraları: ekran okuma uyarısı 5103 → 5104, nakit bahşiş 4000+ → 10000+; uid ve `.dev` paketi önemsiz; uygulama adı WEEM. */
    @Test fun ownNotificationIdsAndNamesAreExpected() {
        fun n(key: String, title: String) = JSONObject().put("t", ts(t0)).put("posted", t0).put("ev", "posted").put("pkg", key.split('|')[1]).put("key", key).put("title", title)
        val rs = mapOf("$day/notifs.jsonl" to listOf(n("0|com.kuryelog.app|5103|null|10123", "Rawstats ekran kaydı kapalı"), n("0|com.kuryelog.app|4017|null|10123", "Nakit bahşiş")))
        val weem = mapOf("$day/notifs.jsonl" to listOf(n("0|com.kuryelog.app.dev|5104|null|10456", "WEEM ekran kaydı kapalı"), n("0|com.kuryelog.app.dev|10017|null|10456", "Nakit bahşiş")))
        val r = compare(rs, weem)
        assertEquals(r.diffs.toString(), 0, r.hard + r.soft)
        assertEquals(setOf("notif.own_id", "text.renamed"), r.rules(Level.EXPECTED))
        assertEquals(5104, ShadowRules.notificationId(5103)); assertEquals(10000, ShadowRules.notificationId(4000)); assertEquals(14095, ShadowRules.notificationId(8095))
        assertEquals(8096, ShadowRules.notificationId(8096)); assertEquals(12, ShadowRules.notificationId(12))
        // Başka uygulamanın bildirim numarası çevrilmez: Wolt 5103 ile WEEM 5104 eşleşmez.
        val other = compare(mapOf("$day/notifs.jsonl" to listOf(n("0|$wolt|5103|null|10999", "x"))), mapOf("$day/notifs.jsonl" to listOf(n("0|$wolt|5104|null|10999", "x"))))
        assertEquals(setOf("notif.unmatched"), other.rules(Level.SOFT))
    }

    /** `click.text`: Rawstats `redact` etmeden yazar, WEEM eder (telefon → [tel]); başka metin farkı yumuşak. */
    @Test fun clickTextRedactIsExpected() {
        val phone = "Call +994 50 123 45 67"
        assertNotEquals(phone, NoteFields.redact(phone))
        fun click(text: String, at: Long = t0) = JSONObject().put("pkg", wolt).put("text", text).put("t", ts(at)).put("type", "click")
        val r = compare(mapOf("$day/events.jsonl" to listOf(click(phone), click("Accept"))),
            mapOf("$day/events.jsonl" to listOf(click(NoteFields.redact(phone)!!, t0 + 800), click("Accept", t0 + 100))))
        assertEquals(r.diffs.toString(), 0, r.hard + r.soft)
        assertEquals(setOf("click.redact"), r.rules(Level.EXPECTED))
        val other = compare(mapOf("$day/events.jsonl" to listOf(click("Accept"))), mapOf("$day/events.jsonl" to listOf(click("Decline"))))
        assertEquals(setOf("event.click"), other.rules(Level.SOFT))
    }

    /** "(YolGo)" → "(WEEM)", "Rawstats"/"KuryePlus" → "WEEM", "YolGo'dan hava gelmedi" → "hava bilgisi gelmedi" (ORDERS.md 16). */
    @Test fun weemNamesInTextsAreExpected() {
        assertEquals("Müşteriye 300 m (WEEM)", ShadowRules.renamed("Müşteriye 300 m (YolGo)"))
        assertEquals("— (hava bilgisi gelmedi)", ShadowRules.renamed("— (YolGo'dan hava gelmedi)"))
        assertEquals("— (hava məlumatı gəlmədi)", ShadowRules.renamed("— (YolGo-dan hava gəlmədi)"))
        assertEquals("WEEM · WEEM", ShadowRules.renamed("Rawstats · KuryePlus"))
        fun n(text: String) = JSONObject().put("t", ts(t0)).put("posted", t0).put("ev", "posted").put("pkg", "com.kuryelog.app").put("key", "0|com.kuryelog.app|12|null|1").put("big", text)
        val r = compare(mapOf("$day/notifs.jsonl" to listOf(n("Kalan 300 m (YolGo)"))), mapOf("$day/notifs.jsonl" to listOf(n("Kalan 300 m (WEEM)"))))
        assertEquals(0, r.hard + r.soft); assertEquals(setOf("text.renamed"), r.rules(Level.EXPECTED))
        val real = compare(mapOf("$day/notifs.jsonl" to listOf(n("Kalan 300 m"))), mapOf("$day/notifs.jsonl" to listOf(n("Kalan 200 m"))))
        assertEquals(setOf("notif.content"), real.rules(Level.SOFT))
    }

    /** YolGo kökenli alanlar (A6'ya kadar WEEM'de yok): `yolgo_venue` olayı, YolGo varışı; hava (A6) ve scooter pili/enerjisi (A5). */
    @Test fun yolgoWeatherAndScooterFieldsAreExpected() {
        val rsRec = record("a", wolt, t0, 3.94, "ZOO 28", extra = {
            getJSONArray("pickups").getJSONObject(0).put("arrived_at", t0 + 250_000).put("arrival_source", "nav_yolgo"); put("used_wh", 12.5)
        }, offerExtra = { put("weather_temp_c", 21.0).put("weather_source", "yolgo_open_meteo").put("scooter_battery_pct", 64.0) })
        val weemRec = record("b", wolt, t0, 3.94, "ZOO 28", extra = { getJSONArray("pickups").getJSONObject(0).put("arrival_source", "platform_status") })
        val venue = JSONObject().put("t", ts(t0)).put("type", "yolgo_venue").put("pkg", wolt)
        val r = compare(orders(rsRec) + mapOf("$day/events.jsonl" to listOf(venue)), orders(weemRec))
        assertEquals(r.diffs.toString(), 0, r.hard + r.soft)
        assertEquals(setOf("yolgo.arrival", "yolgo.event", "weather", "scooter"), r.rules(Level.EXPECTED))
        assertTrue(ShadowRules.yolgo("gps_dwell_yolgo_target")); assertTrue(ShadowRules.yolgo("gps_near_yolgo_target"))
        assertFalse(ShadowRules.yolgo("yolgo_open_meteo")); assertFalse(ShadowRules.yolgo("platform_status")); assertFalse(ShadowRules.yolgo(null))
        // YolGo'dan gelmeyen varış farkı yumuşak kalır.
        val soft = compare(orders(record("a", wolt, t0, 3.94, "ZOO 28")), orders(record("b", wolt, t0, 3.94, "ZOO 28", extra = {
            getJSONArray("pickups").getJSONObject(0).put("arrived_at", t0 + 310_000).put("arrival_source", "gps_dwell") })))
        assertEquals(setOf("order.arrived_at"), soft.rules(Level.SOFT)); assertEquals(0, soft.expected)
    }

    /** Ayrıştırıcı eşitliği (CAPTURE.md 6.5): Rawstats'ın yazdığı aşama WEEM `CourierParser`'ınkinden farklıysa sert. */
    @Test fun parserStageMismatchIsHard() {
        val texts = JSONArray(listOf("Delivery demand l Low", "Go offline"))
        fun row(stage: String) = JSONObject().put("t", ts(t0)).put("pkg", wolt).put("source", "ocr").put("stage", stage).put("offerLike", stage == "OFFER").put("texts", texts).put("actions", JSONArray())
        val ok = compare(mapOf("$day/screens.jsonl" to listOf(row("IDLE"), JSONObject().put("t", ts(t0)).put("pkg", wolt).put("texts", texts))), emptyMap())
        assertEquals(0, ok.hard); assertEquals(ParserStats(2, 1, 1), ok.days.single().parser)
        val bad = compare(mapOf("$day/screens.jsonl" to listOf(row("OFFER"))), emptyMap())
        val d = bad.diffs.single { it.rule == "parser.stage" }
        assertEquals(Level.HARD, d.level); assertEquals("OFFER (offerLike)", d.rawstats); assertEquals("IDLE", d.weem)
        assertEquals(setOf("screens.rows"), bad.rules(Level.SOFT))
    }

    /** Boost/plan kümesi, platform toplamı (anahtarla son değer), liste satırı (anahtarla), CASH PENDING değerleri: sert; anlar yok sayılır. */
    @Test fun rowSetsAreHardAndIgnoreObservationTimes() {
        fun boost(v: Int?, at: Long) = JSONObject().put("schemaVersion", 1).put("kind", "plan").put("pkg", wolt).put("value", v ?: JSONObject.NULL).put("at", at).put("lat", JSONObject.NULL)
        fun total(e: Double, at: Long) = JSONObject().put("schemaVersion", 1).put("kind", "platform_total").put("platform", "wolt").put("period", "day:$day")
            .put("source_screen", "wolt_stats").put("earnings_azn", e).put("observedAtMs", at)
        fun list(amount: Double, trip: String) = JSONObject().put("key", "wolt|$day|16:30|king doner|AZN$amount").put("amount", amount).put("observed_at", t0)
            .put("match", JSONObject().put("trip_id", trip).put("by", "restaurant_fee").put("dt_s", 12))
        fun cash(p: Double, at: Long) = JSONObject().put("schemaVersion", 1).put("kind", "pending").put("pending_azn", p).put("observedAtMs", at)
        val rs = mapOf("$day/economics-boosts.jsonl" to listOf(boost(30, t0), boost(null, t0 + 1)), "$day/economics-platform-totals.jsonl" to listOf(total(4.65, t0), total(11.64, t0 + 5)),
            "$day/economics-platform-history.jsonl" to listOf(list(1.63, "trip-1-1")), "$day/economics-cash-pending.jsonl" to listOf(cash(8.96, t0)))
        val same = mapOf("$day/economics-boosts.jsonl" to listOf(boost(30, t0 + 900), boost(null, t0 + 901), boost(30, t0 + 5000)),
            "$day/economics-platform-totals.jsonl" to listOf(total(11.64, t0 + 9)), "$day/economics-platform-history.jsonl" to listOf(list(1.63, "trip-9-3")),
            "$day/economics-cash-pending.jsonl" to listOf(cash(8.96, t0 + 7)))
        assertEquals(compare(rs, same).diffs.toString(), 0, compare(rs, same).hard)
        val other = mapOf("$day/economics-boosts.jsonl" to listOf(boost(20, t0)), "$day/economics-platform-totals.jsonl" to listOf(total(11.0, t0)),
            "$day/economics-platform-history.jsonl" to listOf(list(1.64, "trip-1-1")), "$day/economics-cash-pending.jsonl" to listOf(cash(9.0, t0)))
        val r = compare(rs, other)
        assertEquals(setOf("boost.set", "platform_total.value", "platform_list.missing", "cash_pending"), r.rules(Level.HARD))
    }

    /** JSON ayrıştırılmış değerle: alan sırası, `\/` kaçışı, 4 = 4.0 ve `null` ile yazılmamış alan aynı (CORE.md 5.2). */
    @Test fun jsonIsComparedByParsedValue() {
        val a = """{"source_screen":"wolt\/stats","period":"day:2026-10-11","platform":"wolt","earnings_azn":4.0,"deliveries":2,"distance_km":null,"kind":"platform_total"}"""
        val b = """{"kind":"platform_total","platform":"wolt","deliveries":2.0,"earnings_azn":4,"period":"day:2026-10-11","source_screen":"wolt/stats"}"""
        assertEquals(ShadowCompare.canon(JSONObject(a)), ShadowCompare.canon(JSONObject(b)))
        val r = ShadowCompare(ShadowExport("rs", mapOf("$day/economics-platform-totals.jsonl" to a)), ShadowExport("weem", mapOf("$day/economics-platform-totals.jsonl" to b))).run()
        assertEquals(0, r.hard + r.soft)
        assertNotEquals(ShadowCompare.canon(JSONObject(a)), ShadowCompare.canon(JSONObject(a.replace("4.0", "4.01"))))
    }

    /** ZIP: üst klasörlü yol (`logs/…`), tanınmayan girdi yok sayılır, aynı dosya iki kez gelirse satırlar birleşir; bozuk satır yumuşak. */
    @Test fun zipLayoutAndDamagedLines() {
        val bytes = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { z ->
            fun put(n: String, t: String) { z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
            put("logs/$day/errors.jsonl", "{\"source\":\"database\"}\nbozuk\n"); put("$day/errors.jsonl", "{\"source\":\"gps_orders\"}\n")
            put("$day/notes.txt", "x"); put("2026-02-30/orders.jsonl", "{}"); put("restaurants.json", "{\"zoo 28\":{\"n\":3}}")
        } }.toByteArray()
        val x = ShadowExport.zip("rs.zip", bytes.inputStream())
        assertEquals(listOf(day), x.days.keys.toList())
        assertEquals(listOf("$day/notes.txt", "2026-02-30/orders.jsonl"), x.ignored)
        assertEquals(2, x.rows(day, "errors.jsonl").size); assertEquals(mapOf("$day/errors.jsonl" to 1), x.damaged)
        val r = ShadowCompare(x, ShadowExport("weem", mapOf("$day/errors.jsonl" to "{\"source\":\"database\"}\n{\"source\":\"gps_orders\"}\n"))).run()
        assertEquals(0, r.hard)
        assertEquals(setOf("file.damaged_lines", "restaurants.keys"), r.rules(Level.SOFT))
        assertTrue(r.json().getJSONArray("diffs").length() >= 2)
    }
}
