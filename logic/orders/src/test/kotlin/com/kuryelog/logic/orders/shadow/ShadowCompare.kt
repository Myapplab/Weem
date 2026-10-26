package com.kuryelog.logic.orders.shadow

import com.kuryelog.logic.capture.CourierParser
import com.kuryelog.logic.capture.ScreenStage
import com.kuryelog.logic.core.LogTime
import com.kuryelog.logic.orders.OrderHistory
import com.kuryelog.logic.orders.PlatformHistory
import com.kuryelog.logic.orders.RecordJson
import com.kuryelog.logic.orders.TripRecord
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * Rawstats eşitlik karşılaştırıcısı (A3.9; S42'den beri yalnız test: uygulamadaki gölge mod anahtarı ve `shadowReport` aracı kalktı,
 * [ShadowSahaTest] saha karelerinde kullanır): aynı vardiyanın Rawstats ve WEEM gün klasörlerini gün gün, dosya dosya karşılaştırır.
 * Kurallar ORDERS.md 14.3 (eşleme; sert/yumuşak/beklenen), CAPTURE.md 6.5 (ayrıştırıcı eşitliği), ORDERS.md 16 ve CAPTURE.md 9
 * (bilerek farklar, [ShadowRules]). JSON ayrıştırılmış değerle karşılaştırılır, bayt bayt değil (alan sırası, kaçış, `null` ile
 * yazılmamış alan aynı sayılır; CORE.md 5.2). [zone] yalnız rapordaki saatleri yazmak için.
 */
class ShadowCompare(private val rs: ShadowExport, private val weem: ShadowExport, private val zone: ZoneId = ZoneId.of("Asia/Baku")) {
    private val diffs = ArrayList<Diff>()

    fun run(): ShadowReport {
        val rsOrders = orders(rs); val weemOrders = orders(weem)
        val trips = match(rsOrders.trips.values.sortedBy { it.t.acceptedAt }, weemOrders.trips.values.toList(), ::sameTrip) { it.t.acceptedAt }
        val offers = match(rsOrders.offers.values.sortedBy { it.firstAt }, weemOrders.offers.values.toList(), ::sameOffer) { it.firstAt }
        val days = (rs.days.keys + weem.days.keys).toSortedSet().map { day ->
            files(day)
            val dayTrips = trips.filter { (a, b) -> (a?.day ?: b!!.day) == day }
            val dayOffers = offers.filter { (a, b) -> (a?.day ?: b!!.day) == day }
            dayTrips.forEach { (a, b) -> trip(day, a, b) }
            dayOffers.forEach { (a, b) -> offer(day, a, b) }
            val rsSide = side(day, rsOrders, rs); val weemSide = side(day, weemOrders, weem)
            totals(day, rsSide, weemSide)
            val parser = screens(day)
            notifs(day)
            val flagged = events(day)
            counts(day)
            rowSets(day)
            DayReport(day, rsSide, weemSide, parser, dayTrips.count { it.first != null && it.second != null },
                dayOffers.count { it.first != null && it.second != null }, flagged)
        }
        root()
        return ShadowReport(rs.name, weem.name, days, diffs)
    }

    private fun add(level: Level, day: String, file: String, rule: String, key: String, rawstats: Any? = null, weem: Any? = null, deltaMs: Long? = null) {
        diffs += Diff(level, day, file, rule, key, rawstats?.let(::show), weem?.let(::show), deltaMs)
    }
    /** Aynı (düzey, gün, dosya, kural, anahtar) farkı tek satırda sayar. */
    private fun count(level: Level, day: String, file: String, rule: String, key: String, n: Int = 1) {
        if (n <= 0) return
        val i = diffs.indexOfFirst { it.level == level && it.day == day && it.file == file && it.rule == rule && it.key == key }
        if (i >= 0) diffs[i] = diffs[i].copy(count = diffs[i].count + n) else diffs += Diff(level, day, file, rule, key, count = n)
    }
    private fun show(v: Any): String = when (v) {
        is Double -> money(v)
        is JSONObject, is JSONArray -> canon(v)
        else -> v.toString()
    }
    private fun money(v: Double) = BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString()
    private fun hm(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime().withNano(0).toString() } ?: "-"
    private fun pkgShort(pkg: String) = when {
        pkg.contains("wolt", true) -> "Wolt"
        pkg.contains("bolt", true) || pkg.contains("mtakso", true) -> "Bolt"
        else -> pkg.substringAfterLast('.')
    }

    // ------------------------------------------------------------------ dosyalar

    private fun files(day: String) {
        val names = (rs.days[day]?.keys.orEmpty() + weem.days[day]?.keys.orEmpty()).toSortedSet()
        for (f in names) {
            val rule = when (f) { in ShadowRules.notYetWritten -> "file.not_yet_written"; in ShadowRules.answers -> "file.user_answers"; else -> continue }
            count(Level.EXPECTED, day, f, rule, if (rs.has(day, f) && weem.has(day, f)) "iki tarafta" else if (rs.has(day, f)) "yalnız Rawstats" else "yalnız WEEM",
                maxOf(1, rs.rows(day, f).size + weem.rows(day, f).size))
        }
    }

    // ------------------------------------------------------------------ siparişler ve teklifler (orders.jsonl)

    private class Ev(val type: String, val at: Long, val reason: String?)
    private class Trip(val id: String, val day: String, val rec: JSONObject, val t: TripRecord, val events: MutableList<Ev> = ArrayList(), var snapshots: Int = 0)
    private class Offer(val id: String, val pkg: String, val day: String, val firstAt: Long, val gross: Double?, val rec: JSONObject, var decision: Ev? = null)
    private class Orders(val trips: Map<String, Trip>, val offers: Map<String, Offer>)

    /** Kayıt seçimi Rawstats `kl_export.trips` gibi: önce faz (FINISHED > END_UNCONFIRMED > diğer), sonra bitiş anı, sonra satır anı. */
    private fun rank(rec: JSONObject, at: Long) = Triple(when (rec.optString("phase")) { "FINISHED" -> 3; "END_UNCONFIRMED" -> 2; else -> 1 },
        if (rec.has("completed_at") && !rec.isNull("completed_at")) 1 else 0, at)

    private fun orders(x: ShadowExport): Orders {
        val best = HashMap<String, Pair<Triple<Int, Int, Long>, JSONObject>>(); val firstDay = HashMap<String, String>()
        val events = HashMap<String, MutableList<Ev>>(); val snapshots = HashMap<String, Int>()
        val offers = LinkedHashMap<String, Offer>()
        for (day in x.days.keys) for (row in x.rows(day, ORDERS)) {
            val type = row.optString("type"); val at = LogTime.parseTs(row.optString("t"))
            row.optJSONObject("record")?.let { rec ->
                val id = rec.optString("id").ifEmpty { null } ?: return@let
                firstDay.putIfAbsent(id, day)
                val k = rank(rec, at)
                val old = best[id]
                if (old == null || compareValuesBy(k, old.first, { it.first }, { it.second }, { it.third }) >= 0) best[id] = k to rec
                if (type == "record_snapshot") snapshots.merge(id, 1, Int::plus)
            }
            if (type.isEmpty() || type == "record_snapshot") continue
            val ev = Ev(type, at, row.optString("reason").ifEmpty { null }.takeUnless { row.isNull("reason") })
            row.optString("id").takeIf { it.isNotEmpty() && !row.isNull("id") }?.let { events.getOrPut(it) { ArrayList() } += ev }
            val oid = row.optString("offer_id").takeIf { it.isNotEmpty() && !row.isNull("offer_id") } ?: continue
            val o = row.optJSONObject("offer")
            if (type == "offer" && o != null) offers.putIfAbsent(oid, Offer(oid, row.optString("pkg"), day, o.optLong("at", at),
                o.optJSONObject("data")?.let { d -> if (d.has("gross") && !d.isNull("gross")) d.optDouble("gross") else null }, o))
            else offers[oid]?.takeIf { it.decision == null && type != "offer" }?.decision = ev
        }
        val trips = LinkedHashMap<String, Trip>()
        for ((id, b) in best) {
            val t = try { RecordJson.trip(b.second) } catch (_: Exception) { x.damaged.merge("${firstDay[id]}/$ORDERS", 1, Int::plus); continue }
            trips[id] = Trip(id, firstDay.getValue(id), b.second, t, events[id] ?: ArrayList(), snapshots[id] ?: 0)
        }
        return Orders(trips, offers)
    }

    /**
     * Rawstats öğelerini zaman sırasıyla en yakın uygun WEEM öğesine eşler (± [windowMs]); eşsizler (Rawstats, null) ya da
     * (null, WEEM). Kimlikler iki uygulamada farklı (ORDERS.md 14.3), eşleme yalnız içerikle.
     */
    private fun <T> match(a: List<T>, b: List<T>, same: (T, T) -> Boolean, at: (T) -> Long): List<Pair<T?, T?>> {
        val used = HashSet<Int>(); val out = ArrayList<Pair<T?, T?>>()
        for (x in a) {
            val j = b.indices.filter { it !in used && abs(at(b[it]) - at(x)) <= WINDOW_MS && same(x, b[it]) }.minByOrNull { abs(at(b[it]) - at(x)) }
            if (j == null) out += x to null else { used += j; out += x to b[j] }
        }
        b.indices.filter { it !in used }.forEach { out += null to b[it] }
        return out
    }

    /** Platform + restoran (`sameRestaurant`, OCR kaymalarına dayanıklı) + kabul anı ± 60 sn. */
    private fun sameTrip(a: Trip, b: Trip): Boolean {
        if (a.t.pkg != b.t.pkg) return false
        val x = OrderHistory.restaurant(a.t); val y = OrderHistory.restaurant(b.t)
        return (x == null && y == null) || PlatformHistory.sameRestaurant(x, y) || PlatformHistory.sameRestaurant(y, x)
    }
    /** Platform + ilk görülüş ± 60 sn + ücret. */
    private fun sameOffer(a: Offer, b: Offer) = a.pkg == b.pkg && sameMoney(a.gross, b.gross)
    private fun sameMoney(a: Double?, b: Double?) = (a == null && b == null) || (a != null && b != null && abs(a - b) < 0.005)

    private fun tripKey(x: Trip) = "${hm(x.t.acceptedAt)} ${pkgShort(x.t.pkg)} ${OrderHistory.restaurant(x.t) ?: "?"}"

    private fun trip(day: String, a: Trip?, b: Trip?) {
        if (a == null || b == null) {
            val x = a ?: b!!
            add(Level.HARD, day, ORDERS, "order.unmatched", tripKey(x), a?.let { summary(it.t) }, b?.let { summary(it.t) })
            return
        }
        val key = tripKey(a)
        fun hard(rule: String, x: Any?, y: Any?) { if (x != y) add(Level.HARD, day, ORDERS, rule, key, x ?: "-", y ?: "-") }
        hard("order.outcome", PlatformHistory.state(a.t), PlatformHistory.state(b.t))
        if (!sameMoney(a.t.gross, b.t.gross)) add(Level.HARD, day, ORDERS, "order.fee", key, a.t.gross ?: "-", b.t.gross ?: "-")
        if (!sameMoney(a.t.net, b.t.net)) {
            // A3.11: tek taraflı bahşiş cevabı (gölge modda soruyu yalnız Rawstats sorar); cevap düşülünce eşitse fark beklenendir.
            val x = a.t.net; val y = b.t.net
            if (x != null && y != null && sameMoney(x - ShadowRules.answeredTip(a.t), y - ShadowRules.answeredTip(b.t))) count(Level.EXPECTED, day, ORDERS, "order.tip_answer", "bahşiş cevabı")
            else add(Level.HARD, day, ORDERS, "order.net", key, a.t.net ?: "-", b.t.net ?: "-")
        }
        if (!sameMoney(a.t.extraWaitAzn, b.t.extraWaitAzn)) add(Level.HARD, day, ORDERS, "order.extra_wait", key, a.t.extraWaitAzn, b.t.extraWaitAzn)
        hard("order.orders", a.t.expectedOrders, b.t.expectedOrders)
        hard("order.payment", a.t.paymentMethod, b.t.paymentMethod)
        hard("order.verified_by", a.t.verifiedBy, b.t.verifiedBy)

        fun soft(rule: String, x: Long?, y: Long?) {
            if (x == y) return
            if (x != null && y != null) add(Level.SOFT, day, ORDERS, rule, key, hm(x), hm(y), y - x) else add(Level.SOFT, day, ORDERS, rule, key, hm(x), hm(y))
        }
        soft("order.accepted_at", a.t.acceptedAt, b.t.acceptedAt)
        soft("order.completed_at", a.t.completedAt, b.t.completedAt)
        soft("order.delivered_inferred_at", a.t.deliveredInferredAt, b.t.deliveredInferredAt)
        soft("order.cash_collected_at", a.t.cashCollectedAt, b.t.cashCollectedAt)
        for (i in 0 until maxOf(a.t.pickups.size, b.t.pickups.size)) {
            val p = a.t.pickups.getOrNull(i); val q = b.t.pickups.getOrNull(i)
            arrival(day, key, "pickup[$i]", p?.arrivedAt, q?.arrivedAt, p?.arrivalSource, q?.arrivalSource)
            soft("order.picked_at", p?.pickedAt, q?.pickedAt)
        }
        val ac = a.t.customers.values.toList(); val bc = b.t.customers.values.toList()
        for (i in 0 until maxOf(ac.size, bc.size)) {
            arrival(day, key, "customer[$i]", ac.getOrNull(i)?.arrivedAt, bc.getOrNull(i)?.arrivedAt, ac.getOrNull(i)?.arrivalSource, bc.getOrNull(i)?.arrivalSource)
            soft("order.customer_delivered_at", ac.getOrNull(i)?.deliveredAt, bc.getOrNull(i)?.deliveredAt)
        }
        val ao = a.t.offer; val bo = b.t.offer
        soft("order.fee_at", ao?.feeAt, bo?.feeAt)
        if (ao?.feeGapMs != bo?.feeGapMs) add(Level.SOFT, day, ORDERS, "order.fee_gap_ms", key, ao?.feeGapMs ?: "-", bo?.feeGapMs ?: "-")
        if (ao?.feeOcrMs != bo?.feeOcrMs) add(Level.SOFT, day, ORDERS, "order.fee_ocr_ms", key, ao?.feeOcrMs ?: "-", bo?.feeOcrMs ?: "-")
        if (a.snapshots != b.snapshots) add(Level.SOFT, day, ORDERS, "order.snapshots", key, a.snapshots, b.snapshots)
        val ae = a.events.map { it.type }.sorted(); val be = b.events.map { it.type }.sorted()
        if (ae != be) add(Level.SOFT, day, ORDERS, "order.events", key, ae.joinToString(","), be.joinToString(","))
        extras(day, a.rec.optJSONObject("offer"), b.rec.optJSONObject("offer"))
        if (a.t.usedWh > 0 && b.t.usedWh == 0.0) count(Level.EXPECTED, day, ORDERS, "scooter", "sipariş enerjisi (A5)")
    }

    /** Varış anı ve kaynağı (D9 kalışı fix'lere bağlı: yumuşak); Rawstats'ın varışı YolGo'dan geldiyse beklenen. */
    private fun arrival(day: String, key: String, what: String, x: Long?, y: Long?, xs: String?, ys: String?) {
        if (x == y && xs == ys) return
        if (ShadowRules.yolgo(xs) && !ShadowRules.yolgo(ys)) { count(Level.EXPECTED, day, ORDERS, "yolgo.arrival", "YolGo varışı (A6)"); return }
        add(Level.SOFT, day, ORDERS, "order.arrived_at", "$key $what", "${hm(x)} ${xs ?: ""}".trim(), "${hm(y)} ${ys ?: ""}".trim(), if (x != null && y != null) y - x else null)
    }

    /** Teklif anı eklentileri: hava (A6'ya kadar WEEM'de yok), scooter pili (A5'e kadar yok). */
    private fun extras(day: String, a: JSONObject?, b: JSONObject?) {
        if (a == null) return
        if (a.keySet().any { it.startsWith("weather_") && !a.isNull(it) } && b?.keySet()?.none { it.startsWith("weather_") && !b.isNull(it) } != false)
            count(Level.EXPECTED, day, ORDERS, "weather", "teklif havası (A6)")
        if (a.has("scooter_battery_pct") && !a.isNull("scooter_battery_pct") && (b == null || b.isNull("scooter_battery_pct") || !b.has("scooter_battery_pct")))
            count(Level.EXPECTED, day, ORDERS, "scooter", "teklif anı scooter pili (A5)")
    }

    private fun summary(t: TripRecord) = "${hm(t.acceptedAt)} ${PlatformHistory.state(t)} ücret=${t.gross?.let(::money) ?: "-"} doğrulama=${t.verifiedBy ?: "-"}"

    /** Teklif sonucunun türü (ORDERS.md 14.3): kabul / ret / zaman aşımı / belirsiz / açık. */
    private fun kind(e: Ev?): String = when {
        e == null -> "açık"
        e.type == "accepted" -> "kabul"
        e.type == "rejected" -> "ret"
        e.type == "offer_unconfirmed" && e.reason == "expired_inferred" -> "zaman_aşımı"
        e.type == "offer_unconfirmed" -> "belirsiz"
        else -> e.type
    }

    private fun offer(day: String, a: Offer?, b: Offer?) {
        val x = a ?: b!!
        val key = "${hm(x.firstAt)} ${pkgShort(x.pkg)} ${x.gross?.let(::money) ?: "?"} AZN"
        if (a == null || b == null) { add(Level.HARD, day, ORDERS, "offer.unmatched", key, a?.let { kind(it.decision) }, b?.let { kind(it.decision) }); return }
        if (kind(a.decision) != kind(b.decision)) add(Level.HARD, day, ORDERS, "offer.outcome", key, kind(a.decision), kind(b.decision))
        else if (a.decision?.reason != b.decision?.reason) add(Level.SOFT, day, ORDERS, "offer.reason", key, a.decision?.reason ?: "-", b.decision?.reason ?: "-")
        if (a.firstAt != b.firstAt) add(Level.SOFT, day, ORDERS, "offer.first_seen", key, hm(a.firstAt), hm(b.firstAt), b.firstAt - a.firstAt)
        val da = a.decision?.at; val db = b.decision?.at
        if (da != null && db != null && da != db) add(Level.SOFT, day, ORDERS, "offer.decided_at", key, hm(da), hm(db), db - da)
        if (a.decision == null || b.decision == null || a.decision?.type != "accepted") extras(day, a.rec, b.rec)
    }

    private fun side(day: String, o: Orders, x: ShadowExport): Side {
        val trips = o.trips.values.filter { it.day == day }.map { it.t }
        val d = OrderHistory.Day(day, trips)
        val states = trips.groupingBy { PlatformHistory.state(it) }.eachCount()
        return Side(trips.size, d.orders, states["delivered"] ?: 0, states["delivered_inferred"] ?: 0, states["unconfirmed"] ?: 0, states["active"] ?: 0,
            d.gross, d.net, o.offers.values.count { it.day == day }, x.rows(day, SCREENS).size,
            d.delivered.sumOf { ShadowRules.answeredTip(it) })
    }

    /** Gün başına sipariş sayısı, teslim/çıkarım/doğrulanamayan, brüt ve net (sert). */
    private fun totals(day: String, a: Side, b: Side) {
        fun hard(rule: String, x: Any, y: Any) { if (x != y) add(Level.HARD, day, ORDERS, rule, "gün", x, y) }
        hard("day.trips", a.trips, b.trips); hard("day.orders", a.orders, b.orders); hard("day.delivered", a.delivered, b.delivered)
        hard("day.delivered_inferred", a.inferred, b.inferred); hard("day.unconfirmed", a.unconfirmed, b.unconfirmed)
        if (!sameMoney(a.gross, b.gross)) add(Level.HARD, day, ORDERS, "day.gross", "gün", a.gross, b.gross)
        if (!sameMoney(a.net, b.net)) {
            if (sameMoney(a.net - a.answeredTips, b.net - b.answeredTips)) count(Level.EXPECTED, day, ORDERS, "day.tip_answer", "gün")
            else add(Level.HARD, day, ORDERS, "day.net", "gün", a.net, b.net)
        }
        if (a.active != b.active) add(Level.SOFT, day, ORDERS, "day.active", "gün", a.active, b.active)
    }

    // ------------------------------------------------------------------ ayrıştırıcı eşitliği (screens.jsonl; CAPTURE.md 6.5)

    private fun screens(day: String): ParserStats {
        var rows = 0; var checked = 0; var same = 0
        for (row in rs.rows(day, SCREENS)) {
            rows++
            val stage = row.optString("stage").takeIf { it.isNotEmpty() && !row.isNull("stage") } ?: continue
            checked++
            val pkg = row.optString("pkg"); val src = row.optString("source", "accessibility")
            val o = CourierParser.parse(pkg, strings(row.optJSONArray("texts")), strings(row.optJSONArray("actions")), LogTime.parseTs(row.optString("t")), src)
            val offerLike = row.optBoolean("offerLike", stage == ScreenStage.OFFER.name)
            if (o.stage.name == stage && (o.stage == ScreenStage.OFFER) == offerLike) same++
            else add(Level.HARD, day, SCREENS, "parser.stage", "${row.optString("t").drop(11).take(12)} ${pkgShort(pkg)} $src",
                "$stage${if (offerLike) " (offerLike)" else ""}", "${o.stage.name}${if (o.stage == ScreenStage.OFFER) " (offerLike)" else ""}")
        }
        val w = weem.rows(day, SCREENS).size
        if (rows != w) add(Level.SOFT, day, SCREENS, "screens.rows", "satır", rows, w)
        return ParserStats(rows, checked, same)
    }
    private fun strings(a: JSONArray?) = a?.let { List(it.length()) { i -> it.optString(i) } } ?: emptyList()

    // ------------------------------------------------------------------ bildirimler (notifs.jsonl)

    private fun notifs(day: String) {
        fun key(r: JSONObject, fromRs: Boolean): String {
            val pkg = r.optString("pkg").let { if (it in ShadowRules.ownPackages) "com.kuryelog.app" else it }
            return listOf(r.optString("ev"), pkg, ShadowRules.notificationKey(r.optString("key"), fromRs), r.optString("posted")).joinToString("·")
        }
        val pool = weem.rows(day, NOTIFS).groupByTo(HashMap()) { key(it, false) }
        var unmatchedRs = 0
        for (r in rs.rows(day, NOTIFS)) {
            val k = key(r, true)
            val w = pool[k]?.removeFirstOrNull()
            if (w == null) { unmatchedRs++; continue }
            if (r.optString("key") != w.optString("key")) count(Level.EXPECTED, day, NOTIFS, "notif.own_id", "bildirim numarası (5103→5104, 4000+→10000+)")
            for (f in listOf("title", "text", "big", "sub", "channel", "ongoing")) {
                val x = r.opt(f)?.takeUnless { it == JSONObject.NULL }?.toString(); val y = w.opt(f)?.takeUnless { it == JSONObject.NULL }?.toString()
                if (x == y) continue
                if (x != null && y != null && ShadowRules.renamed(x) == y) count(Level.EXPECTED, day, NOTIFS, "text.renamed", "uygulama adı WEEM")
                else add(Level.SOFT, day, NOTIFS, "notif.content", "${r.optString("ev")} ${r.optString("key")} $f", x ?: "-", y ?: "-")
            }
        }
        val unmatchedWeem = pool.values.sumOf { it.size }
        if (unmatchedRs > 0) count(Level.SOFT, day, NOTIFS, "notif.unmatched", "yalnız Rawstats", unmatchedRs)
        if (unmatchedWeem > 0) count(Level.SOFT, day, NOTIFS, "notif.unmatched", "yalnız WEEM", unmatchedWeem)
    }

    // ------------------------------------------------------------------ olaylar (events.jsonl)

    /** Olay türü sayıları (yumuşak), tıklama metinleri (`redact` farkı beklenen), YolGo olayları (beklenen); ekran görüntüsü alınamayan dakikalar. */
    private fun events(day: String): List<String> {
        val a = rs.rows(day, EVENTS); val b = weem.rows(day, EVENTS)
        val flagged = (a + b).filter { it.optString("type") == "screenshot_unavailable" }.map { it.optString("t").drop(11).take(5) }.distinct().sorted()
        val clicksB = b.filter { it.optString("type") == "click" }.toMutableList()
        var unmatched = 0
        for (c in a.filter { it.optString("type") == "click" }) {
            val at = LogTime.parseTs(c.optString("t")); val text = c.optString("text")
            val i = clicksB.indexOfFirst { it.optString("pkg") == c.optString("pkg") && abs(LogTime.parseTs(it.optString("t")) - at) <= CLICK_WINDOW_MS &&
                (it.optString("text") == text || it.optString("text") == ShadowRules.clickText(text)) }
            if (i < 0) { unmatched++; continue }
            if (clicksB.removeAt(i).optString("text") != text) count(Level.EXPECTED, day, EVENTS, "click.redact", "click.text redact (telefon → [tel])")
        }
        if (unmatched > 0) count(Level.SOFT, day, EVENTS, "event.click", "yalnız Rawstats", unmatched)
        if (clicksB.isNotEmpty()) count(Level.SOFT, day, EVENTS, "event.click", "yalnız WEEM", clicksB.size)
        val ta = a.groupingBy { it.optString("type") }.eachCount(); val tb = b.groupingBy { it.optString("type") }.eachCount()
        for (t in (ta.keys + tb.keys).toSortedSet()) {
            if (t == "click") continue
            val x = ta[t] ?: 0; val y = tb[t] ?: 0
            if (t in ShadowRules.yolgoEvents) { count(Level.EXPECTED, day, EVENTS, "yolgo.event", t, x + y); continue }
            if (x != y) add(Level.SOFT, day, EVENTS, "event.count", t, x, y)
        }
        return flagged
    }

    // ------------------------------------------------------------------ sayım karşılaştırmaları (yumuşak)

    private fun counts(day: String) {
        val ea = rs.rows(day, ERRORS).groupingBy { it.optString("source") }.eachCount(); val eb = weem.rows(day, ERRORS).groupingBy { it.optString("source") }.eachCount()
        for (s in (ea.keys + eb.keys).toSortedSet()) if ((ea[s] ?: 0) != (eb[s] ?: 0)) add(Level.SOFT, day, ERRORS, "error.count", s, ea[s] ?: 0, eb[s] ?: 0)
        fun gps(x: ShadowExport) = x.text(day, GPS).lineSequence().filter { it.isNotBlank() }.count().let { if (it > 0) it - 1 else 0 }
        if (gps(rs) != gps(weem)) add(Level.SOFT, day, GPS, "gps.rows", "satır", gps(rs), gps(weem))
        val sa = rs.rows(day, SESSIONS); val sb = weem.rows(day, SESSIONS)
        if (sa.size != sb.size) add(Level.SOFT, day, SESSIONS, "sessions.count", "oturum", sa.size, sb.size)
        val ma = sa.sumOf { it.optDouble("durationMinutes", 0.0) }; val mb = sb.sumOf { it.optDouble("durationMinutes", 0.0) }
        if (abs(ma - mb) >= 0.01) add(Level.SOFT, day, SESSIONS, "sessions.minutes", "çevrimiçi dakika", money(ma), money(mb))
        val pa = rs.rows(day, PLATFORMS).map { canon(it, setOf("t")) }; val pb = weem.rows(day, PLATFORMS).map { canon(it, setOf("t")) }
        val onlyA = pa.toSet() - pb.toSet(); val onlyB = pb.toSet() - pa.toSet()
        onlyA.forEach { add(Level.SOFT, day, PLATFORMS, "platform.state", "durum", it, null) }
        onlyB.forEach { add(Level.SOFT, day, PLATFORMS, "platform.state", "durum", null, it) }
    }

    // ------------------------------------------------------------------ satır kümeleri (sert): boost/plan, platform toplamları ve listesi, CASH PENDING

    private fun rowSets(day: String) {
        // K27/M21: boost ve plan satırlarının kümesi (gözlem anı ve konum hariç).
        val drop = setOf("at", "lat", "lon", "gps_accuracy_m", "gps_age_s", "loc", "first_seen")
        set(day, BOOSTS, "boost.set", rs.rows(day, BOOSTS).map { canon(it, drop) }.toSet(), weem.rows(day, BOOSTS).map { canon(it, drop) }.toSet())
        // F8.3: platform toplamı anahtarı (platform|dönem|ekran) başına son değer.
        fun totals(x: ShadowExport) = x.rows(day, TOTALS).associate { "${it.optString("platform")}|${it.optString("period")}|${it.optString("source_screen")}" to canon(it, setOf("observedAtMs")) }
        keyed(day, TOTALS, "platform_total", totals(rs), totals(weem))
        // K36/D15: liste satırları kendi anahtarıyla (gözlem anı ve uygulamaya özgü sipariş kimliği hariç).
        val listDrop = setOf("observed_at", "capture_source", "match.trip_id", "match.dt_s")
        fun list(x: ShadowExport) = x.rows(day, HISTORY).associate { it.optString("key") to canon(it, listDrop) }
        keyed(day, HISTORY, "platform_list", list(rs), list(weem))
        // M17: CASH PENDING, offload ve vardiya satırlarının değerleri (anlar yumuşak, burada yok).
        val cashDrop = setOf("observedAtMs", "shiftStartMs", "pendingObservedAtMs", "trip_id", "tripId")
        multiset(day, CASH, "cash_pending", rs.rows(day, CASH).map { canon(it, cashDrop) }, weem.rows(day, CASH).map { canon(it, cashDrop) })
    }
    private fun set(day: String, file: String, rule: String, a: Set<String>, b: Set<String>) {
        (a - b).forEach { add(Level.HARD, day, file, rule, "satır", it, null) }
        (b - a).forEach { add(Level.HARD, day, file, rule, "satır", null, it) }
    }
    private fun multiset(day: String, file: String, rule: String, a: List<String>, b: List<String>) {
        val ca = a.groupingBy { it }.eachCount(); val cb = b.groupingBy { it }.eachCount()
        for (k in (ca.keys + cb.keys).sorted()) if ((ca[k] ?: 0) != (cb[k] ?: 0)) add(Level.HARD, day, file, rule, k, ca[k] ?: 0, cb[k] ?: 0)
    }
    private fun keyed(day: String, file: String, rule: String, a: Map<String, String>, b: Map<String, String>) {
        for (k in (a.keys + b.keys).toSortedSet()) {
            val x = a[k]; val y = b[k]
            if (x == y) continue
            add(Level.HARD, day, file, if (x == null || y == null) "$rule.missing" else "$rule.value", k, x, y)
        }
    }

    // ------------------------------------------------------------------ kök (restaurants.json)

    private fun root() {
        fun keys(x: ShadowExport) = x.root[ShadowExport.RESTAURANTS]?.let { runCatching { JSONObject(it).keySet().toSortedSet() }.getOrNull() } ?: sortedSetOf()
        val a = keys(rs); val b = keys(weem)
        if (a != b) add(Level.SOFT, "-", ShadowExport.RESTAURANTS, "restaurants.keys", "restoran", (a - b).size, (b - a).size)
        for ((side, x) in listOf("Rawstats" to rs, "WEEM" to weem)) for ((p, n) in x.damaged)
            add(Level.SOFT, p.substringBefore('/'), p.substringAfter('/'), "file.damaged_lines", side, n, null)
    }

    companion object {
        const val WINDOW_MS = 60_000L
        const val CLICK_WINDOW_MS = 2_000L
        const val ORDERS = "orders.jsonl"
        const val SCREENS = "screens.jsonl"
        const val NOTIFS = "notifs.jsonl"
        const val EVENTS = "events.jsonl"
        const val ERRORS = "errors.jsonl"
        const val GPS = "gps.csv"
        const val SESSIONS = "online-sessions.jsonl"
        const val PLATFORMS = "platforms.jsonl"
        const val BOOSTS = "economics-boosts.jsonl"
        const val TOTALS = "economics-platform-totals.jsonl"
        const val HISTORY = "economics-platform-history.jsonl"
        const val CASH = "economics-cash-pending.jsonl"

        /**
         * Ayrıştırılmış değerin kanonik metni: anahtarlar sıralı, sayılar sayı olarak (4 = 4.0), `null` değerli alan yazılmamış sayılır.
         * [drop] üst düzey ya da `a.b` biçiminde iç içe alanlar.
         */
        fun canon(v: Any?, drop: Set<String> = emptySet(), prefix: String = ""): String = when (v) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> v.keySet().filter { "$prefix$it" !in drop && !v.isNull(it) }.sorted()
                .joinToString(",", "{", "}") { "\"$it\":" + canon(v.get(it), drop, "$prefix$it.") }
            is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canon(v.opt(it), drop, prefix) }
            is Number -> BigDecimal(v.toString()).stripTrailingZeros().let { if (it.signum() == 0) "0" else it.toPlainString() }
            is String -> JSONObject.quote(v)
            else -> v.toString()
        }
    }
}
