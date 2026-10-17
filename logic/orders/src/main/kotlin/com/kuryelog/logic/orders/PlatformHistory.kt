package com.kuryelog.logic.orders

import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats PlatformHistory.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * K36: the platforms' own earnings lists read as verified data (reconciliation). Pure: no Android.
 *
 * Wolt: Finances → Gross earnings → Deliveries / Daily details. A row is the restaurant, its clock (`12:40`, the offer/accept
 * time), `Task offer (1.68 km)` and `AZN 2.91`. Bolt Food: All deliveries. A day header `8 October 2026`, the day total
 * `10,90 ₼` and `Deliveries: 3`, then rows: restaurant, `10/08, 13:52, #G55BB` (month/day, clock, order code), `4,72 ₼`.
 *
 * Only the row fields are kept: never the courier's name, phone, balance or cash. Whether Bolt's amount is gross or net is
 * not known, so it is stored as it is printed ([Row.amountText]) with `amount_basis = "unknown"`. Each row is written once
 * ([Row.key]); the match to a KuryePlus order is part of that row (raw order records are never rewritten).
 *
 * D15/D23 (Wolt 2.202.6, saha 11–12 Oct): the day header reads `11 0CT 2026`; OCR may put the fee before `Task offer`
 * (`18:04 · AZN 2.77 · Task offer (2.5 km)`); `Extra waiting time` and `Tip` below a row belong to that row, their amount
 * before or after the label. One delivery read again with an OCR slip in the name or the clock is written once ([sameDelivery]).
 */
object PlatformHistory {
    const val FILE = "economics-platform-history.jsonl"
    const val SCHEMA_VERSION = 1
    const val SOURCE = "platform_history"
    /** The list clock and the KuryePlus clock of the same order differ by at most this much. */
    const val MATCH_WINDOW_MS = 120_000L
    /** D23: with the same restaurant and fee, the list clock may be this far from the order's accept time (bundles). */
    const val FEE_MATCH_WINDOW_MS = 15 * 60_000L
    /** An order code match still has to be on the same day (long multi-hour gaps are another order). */
    private const val CODE_WINDOW_MS = 12 * 3_600_000L

    data class Row(val platform: String, val restaurant: String?, val clock: String, val day: String?, val daySource: String,
                   val amount: Double, val amountText: String, val distanceKm: Double?, val orderCode: String?,
                   val dayTotalText: String?, val dayDeliveries: Int?, val extraWait: Double? = null, val tip: Double? = null) {
        /** Same row in another frame or after a scroll. The order code is not part of it (it is never written). */
        fun key(effectiveDay: String): String = listOf(platform, effectiveDay, clock, norm(restaurant ?: ""), amountText.replace(Regex("[\\s\\p{Z}]+"), "")).joinToString("|")
    }

    data class Match(val tripId: String, val by: String, val dtS: Long, val state: String, val tripGross: Double?)

    // ------------------------------------------------------------------ parsing

    private val SP = "[\\s\\p{Z}]"
    private val TIME = Regex("^([0-9]{1,2}):([0-9]{2})$")
    private val WOLT_TASK = Regex("^Task offer$SP*\\($SP*([0-9]+(?:[.,][0-9]+)?)$SP*km$SP*\\)$", RegexOption.IGNORE_CASE)
    private val WOLT_AMOUNT = Regex("^AZN$SP*([0-9]+[.,][0-9]{2})$")
    private val BOLT_META = Regex("^([0-9]{2})/([0-9]{2}),$SP*([0-9]{1,2}):([0-9]{2}),$SP*(#[A-Za-z0-9]{3,})$")
    private val BOLT_AMOUNT = Regex("^([0-9]+[.,][0-9]{2})$SP*₼$")
    private val BOLT_COUNT = Regex("^Deliveries:$SP*([0-9]+)$", RegexOption.IGNORE_CASE)
    private val MONTHS = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    /** Full month names and Wolt's `OCT`; OCR reads its O as a zero (`11 0CT 2026`). */
    private val DATE = Regex("^([0-9]{1,2})$SP+([A-Za-z0]{3,9})$SP+([0-9]{4})$")
    /** Labels seen on these screens (K36 request text); never a restaurant. */
    private val LABELS = setOf("finances", "gross earnings", "cash pending", "deliveries", "daily details", "all deliveries", "balance", "cash", "task offer",
        "extra waiting time", "tip")

    private fun f(p: String, vararg a: Any): String = String.format(java.util.Locale.US, p, *a)
    private fun clean(s: String) = s.replace(Regex("$SP+"), " ").trim()
    fun norm(s: String) = CourierParser.norm(s).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun num(s: String) = s.replace(',', '.').toDouble()

    /** `8 October 2026`, `11 OCT 2026`, `11 0CT 2026` → `2026-10-08` / `2026-10-11`, else null. */
    fun dateHeader(s: String): String? {
        val m = DATE.find(clean(s)) ?: return null
        val name = m.groupValues[2].lowercase().replace('0', 'o')
        val month = MONTHS.indexOfFirst { it == name || name.length == 3 && it.startsWith(name) } + 1
        val d = m.groupValues[1].toInt()
        if (month == 0 || d !in 1..31) return null
        return f("%s-%02d-%02d", m.groupValues[3], month, d)
    }

    private fun isAmount(s: String) = WOLT_AMOUNT.matches(s) || BOLT_AMOUNT.matches(s)
    private fun candidate(s: String): Boolean {
        if (s.isEmpty() || TIME.matches(s) || isAmount(s) || WOLT_TASK.matches(s) || BOLT_META.matches(s) || BOLT_COUNT.matches(s) || dateHeader(s) != null) return false
        if (norm(s) in LABELS) return false
        // A phone number or a bare number is never a restaurant (and is never written).
        if (s.count(Char::isDigit) >= 7 || s.none { it.isLetter() }) return false
        return true
    }

    /** The rows of an earnings list frame, or null when [texts] is not such a list (then the frame goes to the normal parser). */
    fun parse(pkg: String, texts: List<String>, at: Long, tz: TimeZone): List<Row>? {
        val lines = texts.flatMap { it.split('\n') }.map(::clean).filter { it.isNotEmpty() }
        return when (Platforms.of(pkg)) {
            "wolt" -> wolt(lines).takeIf { it.isNotEmpty() }
            "bolt" -> bolt(lines, at, tz).takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    private fun restaurantBefore(lines: List<String>, from: Int, until: Int): String? {
        for (i in until - 1 downTo maxOf(from, until - 3)) if (candidate(lines[i])) return lines[i]
        return null
    }

    private fun wolt(lines: List<String>): List<Row> {
        val out = ArrayList<Row>()
        var boundary = -1
        var header: String? = null
        for ((i, s) in lines.withIndex()) {
            dateHeader(s)?.let { header = it }
            val task = WOLT_TASK.find(s) ?: continue
            val next = (i + 1 until lines.size).firstOrNull { WOLT_TASK.matches(lines[it]) } ?: lines.size
            val timeAt = (maxOf(boundary + 1, i - 3) until i).lastOrNull { TIME.matches(lines[it]) } ?: continue
            // The fee follows "Task offer"; OCR sometimes reads it between the clock and "Task offer".
            val amountAt = (timeAt + 1 until i).firstOrNull { WOLT_AMOUNT.matches(lines[it]) }
                ?: (i + 1 until minOf(next, i + 4)).firstOrNull { WOLT_AMOUNT.matches(lines[it]) } ?: continue
            val restaurant = restaurantBefore(lines, boundary + 1, timeAt)
            val amountText = lines[amountAt]
            val time = clock(lines[timeAt]) ?: continue
            // D15: "Extra waiting time" / "Tip" lines right below belong to this row, the amount after or before the label.
            var end = maxOf(i, amountAt); var extra: Double? = null; var tip: Double? = null
            var j = end + 1
            while (j < next && j <= end + 4) {
                val label = norm(lines[j])
                if (label == "extra waiting time" || label == "tip") {
                    val after = (j + 1).takeIf { it < next && WOLT_AMOUNT.matches(lines[it]) }
                    val before = (j - 1).takeIf { it > end && WOLT_AMOUNT.matches(lines[it]) }
                    val at = after ?: before ?: break
                    val v = num(WOLT_AMOUNT.find(lines[at])!!.groupValues[1])
                    if (label == "tip") tip = v else extra = v
                    end = maxOf(j, at); j = end + 1
                } else if (WOLT_AMOUNT.matches(lines[j])) j++ else break
            }
            out.add(Row("wolt", restaurant, time, header, if (header != null) "screen_header" else "not_on_screen",
                num(WOLT_AMOUNT.find(amountText)!!.groupValues[1]), amountText, num(task.groupValues[1]), null, null, null, extra, tip))
            boundary = end
        }
        return out
    }

    private fun bolt(lines: List<String>, at: Long, tz: TimeZone): List<Row> {
        val out = ArrayList<Row>()
        var boundary = -1
        var header: String? = null; var total: String? = null; var count: Int? = null
        for ((i, s) in lines.withIndex()) {
            val h = dateHeader(s)
            if (h != null) {
                header = h; total = null; count = null
                lines.getOrNull(i + 1)?.takeIf { BOLT_AMOUNT.matches(it) }?.let { total = it }
                continue
            }
            BOLT_COUNT.find(s)?.let { count = it.groupValues[1].toInt() }
            val m = BOLT_META.find(s) ?: continue
            val mm = m.groupValues[1].toInt(); val dd = m.groupValues[2].toInt()
            val hh = m.groupValues[3].toInt(); val mi = m.groupValues[4].toInt()
            if (mm !in 1..12 || dd !in 1..31 || hh > 23 || mi > 59) continue
            val next = (i + 1 until lines.size).firstOrNull { BOLT_META.matches(lines[it]) || dateHeader(lines[it]) != null } ?: lines.size
            val amountAt = (i + 1 until minOf(next, i + 3)).firstOrNull { BOLT_AMOUNT.matches(lines[it]) } ?: continue
            val md = f("%02d-%02d", mm, dd)
            val hdr = header
            val (day, source) = if (hdr != null && hdr.endsWith(md)) "${hdr.substring(0, 4)}-$md" to "row_date_header_year" else {
                val c = Calendar.getInstance(tz).apply { timeInMillis = at }
                val y = c.get(Calendar.YEAR)
                val nowMd = f("%02d-%02d", c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
                "${if (md <= nowMd) y else y - 1}-$md" to "row_date_capture_year"
            }
            val amountText = lines[amountAt]
            val sameDay = hdr == day
            out.add(Row("bolt", restaurantBefore(lines, boundary + 1, i), f("%02d:%02d", hh, mi), day, source,
                num(BOLT_AMOUNT.find(amountText)!!.groupValues[1]), amountText, null, m.groupValues[5].uppercase(),
                if (sameDay) total else null, if (sameDay) count else null))
            boundary = amountAt
        }
        return out
    }

    private fun clock(s: String): String? {
        val m = TIME.find(s) ?: return null
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        return if (h > 23 || mi > 59) null else f("%02d:%02d", h, mi)
    }

    /** `2026-10-08` + `12:40` in [tz] → epoch ms. */
    fun toMs(day: String, clock: String, tz: TimeZone): Long {
        val d = day.split('-').map(String::toInt); val c = clock.split(':').map(String::toInt)
        return Calendar.getInstance(tz).apply { clear(); set(d[0], d[1] - 1, d[2], c[0], c[1], 0) }.timeInMillis
    }

    fun dayOf(at: Long, tz: TimeZone): String {
        val c = Calendar.getInstance(tz).apply { timeInMillis = at }
        return f("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    // ------------------------------------------------------------------ matching

    /** D2/D23: OCR confuses 0/O and 1/l/i/| ("ZO0 28" = "ZOO 28", "Beauty ls You" = "Beauty Is You"), like [OfferMatch]. */
    private fun tokens(s: String) = norm(s.replace("…", " ").replace("...", " "))
        .map { when (it) { '0' -> 'o'; '1', 'i', '|' -> 'l'; else -> it } }.joinToString("").split(" ").filter { it.isNotEmpty() }
    private fun tokenEq(a: String, b: String) = a == b || (minOf(a.length, b.length) >= 5 && CustomerMatch.distance(a, b) <= 1)

    /** Same restaurant despite OCR slips, a truncated list name or a missing branch word (like [CustomerMatch.sameName]). */
    fun sameRestaurant(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        return b.split(" + ").any { part ->
            val x = tokens(a); val y = tokens(part)
            if (x.isEmpty() || y.isEmpty()) return@any false
            val (short, long) = if (x.size <= y.size) x to y else y to x
            tokenEq(short[0], long[0]) && short.all { s -> long.any { tokenEq(s, it) || (s.length >= 3 && it.startsWith(s) && s == short.last()) } }
        }
    }

    /** Delivery state of the KuryePlus order: `delivered`, `delivered_inferred` (K34), `unconfirmed` or `active`. */
    fun state(t: TripRecord): String = when {
        t.phase == TripPhase.FINISHED && t.completedAt != null -> "delivered"
        t.deliveredInferred -> "delivered_inferred"
        t.phase == TripPhase.END_UNCONFIRMED -> "unconfirmed"
        else -> "active"
    }

    private fun codes(t: TripRecord): Set<String> =
        (t.customers.keys + t.customers.values.map { it.id } + (t.offer?.data?.orderIds ?: emptyList()))
            .filter { it.startsWith("#") }.map { it.uppercase() }.toSet()

    /** The KuryePlus order of [row] (listed at [rowAt]) among [trips] of the same day, [taken] excluded. Closest clock wins. */
    fun match(row: Row, rowAt: Long, trips: Collection<TripRecord>, taken: Set<String>): Match? {
        val name = if (row.platform == "wolt") "Wolt" else "Bolt Food"
        val mine = trips.filter { it.id !in taken && Platforms.of(it.pkg) == row.platform }
        fun dt(t: TripRecord) = Math.abs(OrderHistory.listTime(t, name) - rowAt)
        fun m(t: TripRecord, by: String) = Match(t.id, by, dt(t) / 1000, state(t), t.gross)
        row.orderCode?.let { code -> mine.filter { code in codes(it) && dt(it) <= CODE_WINDOW_MS }.minByOrNull(::dt)?.let { return m(it, "order_code") } }
        val near = mine.filter { dt(it) <= MATCH_WINDOW_MS }
        near.filter { sameRestaurant(row.restaurant, OrderHistory.restaurant(it)) }.minByOrNull(::dt)?.let { return m(it, "restaurant_time") }
        // D23: a bundle is listed minutes after its acceptance ("Mix Point Cafe (2 deliveries)" 12:46, accepted 12:41 on 12 Oct):
        // the same restaurant and the same fee name the order within [FEE_MATCH_WINDOW_MS].
        mine.filter { t -> dt(t) <= FEE_MATCH_WINDOW_MS && t.gross?.let { Math.abs(it - row.amount) < 0.011 } == true && sameRestaurant(row.restaurant, OrderHistory.restaurant(t)) }
            .minByOrNull(::dt)?.let { return m(it, "restaurant_fee") }
        // The order's restaurant was never read: the clock alone, only when it names a single order.
        val unnamed = near.filter { OrderHistory.restaurant(it) == null }
        return if (unnamed.size == 1 && near.size == 1) m(unnamed[0], "time_only_restaurant_unknown") else null
    }

    // ------------------------------------------------------------------ D3(d) cash collections

    /** One Wolt Finances "Cash collection" row: the day header above it, its clock (the handover time), restaurant and amount. */
    data class CashRow(val day: String, val clock: String, val restaurant: String?, val amount: Double)

    private val CASH_AMOUNT = Regex("^-?AZN$SP*([0-9]+)[.,:]([0-9]{2})$")
    private val LOOSE_DATE = Regex("^([0-9]{1,2})$SP*([A-Za-z]+)$SP+([0-9]{4})$")
    /** OCR may drop the space of the day header (`8October 2026`). */
    private fun looseDate(s: String): String? = dateHeader(s) ?: LOOSE_DATE.find(clean(s))?.let { dateHeader(it.groupValues[1] + " " + it.groupValues[2] + " " + it.groupValues[3]) }

    /**
     * Wolt Finances → Recent transactions (saha 11–12 Oct): `11 October 2026` · `Cash collection` · `AZN 54.25` ·
     * `Bonbon PATISSERIE` · `17:45`; a long restaurant name wraps around the amount (`McDonald's & McCafe Grand` · `AZN 18.69`
     * · `Hayat` · `20:26`). `Cash offload` rows ([offloadRows], M17) and rows above the first day header are skipped. Only these
     * row fields are read: never the payout or the courier. The cash pending number is [CashPending.pending] (M17).
     */
    fun cashRows(pkg: String, texts: List<String>): List<CashRow> = financeRows(pkg, texts, "cash collection")

    /** M17 (16 Oct): the `Cash offload | 14:50 | -AZN 50.00` rows of the same list (no restaurant; [CashRow.amount] positive). */
    fun offloadRows(pkg: String, texts: List<String>): List<CashRow> = financeRows(pkg, texts, "cash offload").map { it.copy(restaurant = null) }

    private fun financeRows(pkg: String, texts: List<String>, label: String): List<CashRow> {
        // Runs on every frame (OrderTracker.onScreen): a frame without "cash" anywhere is not normalised line by line.
        if (Platforms.of(pkg) != "wolt" || texts.none { it.contains("cash", ignoreCase = true) }) return emptyList()
        val lines = texts.flatMap { it.split('\n') }.map(::clean).filter { it.isNotEmpty() }
        if (lines.none { norm(it) == label }) return emptyList()
        val out = ArrayList<CashRow>()
        var day: String? = null
        var i = 0
        while (i < lines.size) {
            val s = lines[i++]
            val header = looseDate(s)
            if (header != null) { day = header; continue }
            if (norm(s) != label) continue
            var amount: Double? = null; var clock: String? = null; val name = ArrayList<String>()
            // M17: the amount may also come after the clock (Finances' right column read after the left one).
            while (i < lines.size && (clock == null || amount == null) && name.size <= 3) {
                val x = lines[i]
                if (looseDate(x) != null || norm(x).startsWith("cash ")) break
                i++
                val m = CASH_AMOUNT.find(x)
                if (TIME.matches(x)) clock = clock(x)
                else if (m != null) amount = "${m.groupValues[1]}.${m.groupValues[2]}".toDouble()
                else name.add(x)
            }
            val d = day
            if (d != null && clock != null && amount != null) out.add(CashRow(d, clock, name.joinToString(" ").ifEmpty { null }, amount))
        }
        return out
    }

    /** A cash row's clock is at most this long after the acceptance of its order. */
    private const val CASH_WINDOW_MS = 3 * 3_600_000L

    /**
     * D3(d): the Wolt order of cash row [row] (clock [at], start of its minute) among [trips]: same restaurant, accepted before
     * the clock and at most [CASH_WINDOW_MS] earlier, past its pickup (or counted delivered), not matched to a row before;
     * the one whose recorded end is nearest to the clock (the same restaurant twice a day).
     */
    fun cashMatch(row: CashRow, at: Long, trips: Collection<TripRecord>): TripRecord? = trips.filter { t ->
        Platforms.of(t.pkg) == "wolt" && t.cashCollectedAt == null && t.acceptedAt <= at + 60_000 && at - t.acceptedAt <= CASH_WINDOW_MS &&
            (t.countsAsDelivered || t.pickups.any { it.pickedAt != null }) && sameRestaurant(row.restaurant, OrderHistory.restaurant(t))
    }.minByOrNull { t -> Math.abs((t.completedAt ?: t.deliveredInferredAt ?: t.latest?.at ?: t.acceptedAt) - at) }

    // ------------------------------------------------------------------ D15 duplicates

    private fun minutes(clock: String) = clock.split(':').let { it[0].toInt() * 60 + it[1].toInt() }
    /** Clock difference of two rows read as the same delivery: OCR misread 14:54 as 14:57 (saha 12 Oct). */
    const val SAME_DELIVERY_MIN = 5
    /**
     * D15: [a] and [b] are one delivery read in two frames: same platform, fee and km, restaurant alike despite OCR
     * ([OfferMatch.similar]: `ZO0 28` / `ZOO 28`, `Beauty ls You` / `Beauty Is You`) and clocks at most [SAME_DELIVERY_MIN] apart.
     * Rows without a km (Bolt) are never merged this way.
     */
    fun sameDelivery(a: Row, b: Row): Boolean = a.platform == b.platform && a.distanceKm != null && b.distanceKm != null &&
        Math.abs(a.amount - b.amount) < 0.001 && Math.abs(a.distanceKm - b.distanceKm) < 0.001 &&
        Math.abs(minutes(a.clock) - minutes(b.clock)) <= SAME_DELIVERY_MIN && OfferMatch.similar(a.restaurant, b.restaurant)
    /** A written log row as a [Row] (for [sameDelivery]); null for another schema or a broken row. */
    fun rowOf(j: JSONObject): Row? {
        if (j.optInt("schemaVersion", -1) != SCHEMA_VERSION) return null
        return runCatching {
            Row(j.getString("platform"), if (j.isNull("restaurant")) null else j.optString("restaurant").takeIf { it.isNotEmpty() }, j.getString("clock"),
                if (j.isNull("day")) null else j.optString("day").takeIf { it.isNotEmpty() }, j.optString("day_source"), j.getDouble("amount"),
                j.getString("amount_text"), if (j.isNull("distance_km")) null else j.optDouble("distance_km"), null, null, null)
        }.getOrNull()
    }
    /** The KuryePlus order of [r] (on [day]) through the row already written for it, by key or as the same delivery ([sameDelivery]). */
    fun owner(r: Row, day: String, written: List<JSONObject>): String? {
        val key = r.key(day)
        val j = written.firstOrNull { it.optString("key") == key } ?: written.firstOrNull { w -> rowOf(w)?.let { sameDelivery(it, r) } == true } ?: return null
        return j.optJSONObject("match")?.optString("trip_id", "")?.takeIf { it.isNotEmpty() }
    }

    // ------------------------------------------------------------------ records

    /**
     * New log rows for one frame: rows already in [keys] (this or an earlier frame of the visit, or an earlier visit) are
     * skipped; [keys] and [taken] (trip ids already matched on that day) are updated. [tripsOn] gives the orders of a day.
     * A row without a date on screen is matched on the capture day (`day_source = not_on_screen`, `match_day = capture_day`);
     * a clock later than the capture time is then not matched at all (`missed_by_kuryeplus = null`, unknown).
     * D15: [written] (the rows already logged per day, this call's included) makes an OCR variant of a logged row the same
     * row: it is written only when this frame shows more such rows than were logged (two deliveries side by side).
     */
    fun records(rows: List<Row>, at: Long, captureSource: String, pkg: String, tz: TimeZone, keys: (String) -> MutableSet<String>,
                taken: (String) -> MutableSet<String>, tripsOn: (String) -> Collection<TripRecord>,
                written: ((String) -> MutableList<JSONObject>)? = null): List<Pair<String, JSONObject>> {
        val out = ArrayList<Pair<String, JSONObject>>()
        for (r in rows) {
            val day = r.day ?: dayOf(at, tz)
            val key = r.key(day)
            if (key in keys(day)) continue
            if (written != null) {
                // Only rows with a km compare as the same delivery; a Bolt row has none and is deduplicated by its key alone.
                val same = rows.count { sameDelivery(it, r) }
                if (same > 0 && written(day).count { w -> rowOf(w)?.let { sameDelivery(it, r) } == true } >= same) continue
            }
            keys(day).add(key)
            val rowAt = toMs(day, r.clock, tz)
            val future = r.day == null && rowAt > at + MATCH_WINDOW_MS
            val match = if (future) null else match(r, rowAt, tripsOn(day), taken(day))
            match?.let { taken(day).add(it.tripId) }
            val j = JSONObject().put("schemaVersion", SCHEMA_VERSION).put("source", SOURCE).put("verified", true)
                .put("platform", r.platform).put("pkg", pkg).put("observed_at", at).put("capture_source", captureSource)
                .put("day", r.day ?: JSONObject.NULL).put("day_source", r.daySource).put("match_day", if (r.day != null) "screen" else "capture_day")
                .put("clock", r.clock).put("listed_at", if (future) JSONObject.NULL else rowAt)
                .put("restaurant", r.restaurant ?: JSONObject.NULL)
                .put("amount", r.amount).put("amount_text", r.amountText).put("currency", "AZN")
                .put("amount_basis", if (r.platform == "wolt") "gross_earnings_list" else "unknown")
                .put("distance_km", r.distanceKm ?: JSONObject.NULL).put("has_order_code", r.orderCode != null)
                .put("day_total_text", r.dayTotalText ?: JSONObject.NULL).put("day_deliveries", r.dayDeliveries ?: JSONObject.NULL)
                .put("extra_wait_azn", r.extraWait ?: JSONObject.NULL).put("tip_azn", r.tip ?: JSONObject.NULL)
                .put("key", key)
            if (match != null) {
                j.put("match", JSONObject().put("trip_id", match.tripId).put("by", match.by).put("dt_s", match.dtS).put("trip_state", match.state)
                    .put("trip_gross", match.tripGross ?: JSONObject.NULL))
                j.put("missed_by_kuryeplus", false).put("confirms_inferred_delivery", match.state == "delivered_inferred")
            } else {
                j.put("match", JSONObject.NULL).put("missed_by_kuryeplus", if (future) JSONObject.NULL else true).put("confirms_inferred_delivery", false)
            }
            written?.invoke(day)?.add(j)
            out.add(day to j)
        }
        return out
    }

    /** Keys, matched trip ids and the rows of a day's log (dedupe across visits and app restarts). Unknown schema rows are skipped. */
    fun loaded(lines: Sequence<String>): Triple<MutableSet<String>, MutableSet<String>, MutableList<JSONObject>> {
        val keys = HashSet<String>(); val taken = HashSet<String>(); val rows = ArrayList<JSONObject>()
        for (l in lines) {
            val j = runCatching { JSONObject(l) }.getOrNull() ?: continue
            if (j.optInt("schemaVersion", -1) != SCHEMA_VERSION) continue
            rows.add(j)
            j.optString("key", "").takeIf { it.isNotEmpty() }?.let { keys.add(it) }
            j.optJSONObject("match")?.optString("trip_id", "")?.takeIf { it.isNotEmpty() }?.let { taken.add(it) }
        }
        return Triple(keys, taken, rows)
    }

    /** For the order cards: trip id → its verified platform row; and the rows KuryePlus missed, in list order. */
    fun byTrip(rows: List<JSONObject>): Map<String, JSONObject> = rows.filter { it.optInt("schemaVersion", -1) == SCHEMA_VERSION }
        .mapNotNull { r -> r.optJSONObject("match")?.optString("trip_id", "")?.takeIf { it.isNotEmpty() }?.let { it to r } }.toMap()

    fun missed(rows: List<JSONObject>, platform: String?): List<JSONObject> = rows.filter {
        it.optInt("schemaVersion", -1) == SCHEMA_VERSION && it.opt("missed_by_kuryeplus") == true && (platform == null || it.optString("platform") == platform)
    }.sortedBy { it.optString("clock") }

    /** "wolt" / "bolt" for a Siparişler tab name ("Wolt", "Bolt Food"); null for the all-orders tab. */
    fun platformKey(tab: String): String? = when (tab) { "Wolt" -> "wolt"; "Bolt Food" -> "bolt"; else -> null }

    /** Card line of a verified row (amount as printed; Bolt's basis is unknown). The clock is the accept time (Wolt). */
    fun cardLine(r: JSONObject, tl: (String, String) -> String): String {
        val basis = if (r.optString("amount_basis") == "unknown") " " + tl("(brüt/net belirsiz)", "(ümumi/xalis bəlli deyil)") else ""
        val inferred = if (r.optBoolean("confirms_inferred_delivery", false)) " · " + tl("doğrulanmamış teslimi onayladı", "təsdiqlənməmiş çatdırılmanı təsdiqlədi") else ""
        val clock = if (r.optString("platform") == "wolt") tl("kabul ", "qəbul ") + r.optString("clock") else r.optString("clock")
        return tl("Platform listesi (doğrulandı): ", "Platforma siyahısı (təsdiqləndi): ") + r.optString("amount_text") + " · " + clock +
            " · " + tl("teslim edildi", "çatdırılıb") + basis + inferred
    }

    fun missedLine(r: JSONObject): String =
        listOfNotNull(r.optString("clock"), r.optString("restaurant", "").takeIf { it.isNotEmpty() && it != "null" }, r.optString("amount_text")).joinToString(" · ")
}
