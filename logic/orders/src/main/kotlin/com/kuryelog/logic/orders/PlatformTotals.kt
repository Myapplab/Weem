package com.kuryelog.logic.orders

import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats PlatformTotals.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * F8 (Ekim planı madde 13): the platforms' own totals for a period, to compare with Rawstats's. Pure: no Android.
 *
 * Sources: Wolt "Your stats" (deliveries, distance, earnings, earnings without tips, tips; F8.1 [CourierParser.statsPeriod]),
 * Wolt Gross earnings → Daily details day totals (`11/10/2026` · `AZN 18.24`), the cash collected per day (Finances
 * "Cash collection" rows, [PlatformHistory.cashRows]) and Bolt All deliveries day headers (`8 October 2026` · `10,90 ₼` ·
 * `Deliveries: 3`). Only numbers are kept: never a name, phone or balance. Ekim planı madde 17 changed the F8 rule for one number: the Finances "CASH
 * PENDING" is now kept, in its own file ([CashPending.FILE]), not here. Whether Bolt's amount is gross or
 * net is not known ([Total.amountBasis] "unknown").
 */
object PlatformTotals {
    /** F8.3: one row per total seen; per platform, period and source screen the newest row wins ([latest]). */
    const val FILE = "economics-platform-totals.jsonl"
    const val SCHEMA_VERSION = 1
    const val WOLT_STATS = "wolt_stats"
    const val WOLT_DAILY = "wolt_daily_details"
    const val WOLT_CASH = "wolt_cash_collection"
    const val BOLT_DAYS = "bolt_all_deliveries"

    /** [period]: "day:2026-10-16", "week:2026-10-11..2026-10-16" or "month:2026-10". */
    data class Total(val platform: String, val period: String, val source: String, val deliveries: Int? = null, val earnings: Double? = null,
                     val earningsNoTips: Double? = null, val tips: Double? = null, val distanceKm: Double? = null, val cash: Double? = null,
                     val amountBasis: String? = null)

    private const val SP = "[\\s\\p{Z}]"
    private val WOLT_DAY = Regex("^([0-9]{2})/([0-9]{2})/([0-9]{4})$")
    private val WOLT_DAY_AMOUNT = Regex("^([0-9]{2})/([0-9]{2})/([0-9]{4})$SP+AZN$SP*([0-9]+[.,][0-9]{2})$")
    private val WOLT_AMOUNT = Regex("^AZN$SP*([0-9]+[.,][0-9]{2})$")
    private val BOLT_AMOUNT = Regex("^([0-9]+[.,][0-9]{2})$SP*₼$")
    private val BOLT_COUNT = Regex("^Deliveries:$SP*([0-9]+)$", RegexOption.IGNORE_CASE)
    private val BOLT_META = Regex("^[0-9]{2}/[0-9]{2},$SP*[0-9]{1,2}:[0-9]{2},")

    private fun clean(texts: List<String>) = texts.flatMap { it.split('\n') }.map { it.replace(Regex("$SP+"), " ").trim() }.filter { it.isNotEmpty() }
    private fun num(s: String) = s.replace(',', '.').toDouble()
    private fun round2(v: Double) = Math.round(v * 100) / 100.0

    /** `11/10/2026` (day/month/year, as Wolt prints it) → "day:2026-10-11", else null. */
    private fun woltDay(d: String, m: String, y: String): String? {
        val dd = d.toInt(); val mm = m.toInt()
        if (mm !in 1..12) return null
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { clear(); set(y.toInt(), mm - 1, 1) }
        if (dd !in 1..c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)) return null
        return String.format(java.util.Locale.US, "day:%s-%02d-%02d", y, mm, dd)
    }

    /** F8.2: Wolt Gross earnings → Daily details. The amount on the date's line, or beside it; OCR may put every amount
     *  before its date (then all of them are: the last date has none after it). A period total above the list is not a day. */
    fun woltDaily(pkg: String, texts: List<String>): List<Total> {
        if (Platforms.of(pkg) != "wolt") return emptyList()
        val lines = clean(texts)
        if (lines.none { PlatformHistory.norm(it) == "daily details" || PlatformHistory.norm(it) == "gross earnings" }) return emptyList()
        val lastDate = lines.indexOfLast { WOLT_DAY.matches(it) }
        val amountFirst = lastDate > 0 && WOLT_AMOUNT.matches(lines[lastDate - 1]) && lines.getOrNull(lastDate + 1)?.let { WOLT_AMOUNT.matches(it) } != true
        val out = LinkedHashMap<String, Total>()
        for ((i, s) in lines.withIndex()) {
            val inline = WOLT_DAY_AMOUNT.matchEntire(s)?.groupValues
            if (inline != null) {
                woltDay(inline[1], inline[2], inline[3])?.let { out.putIfAbsent(it, Total("wolt", it, WOLT_DAILY, earnings = num(inline[4]))) }
                continue
            }
            val m = WOLT_DAY.matchEntire(s) ?: continue
            val day = woltDay(m.groupValues[1], m.groupValues[2], m.groupValues[3]) ?: continue
            val amount = lines.getOrNull(if (amountFirst) i - 1 else i + 1)?.let { WOLT_AMOUNT.matchEntire(it) } ?: continue
            out.putIfAbsent(day, Total("wolt", day, WOLT_DAILY, earnings = num(amount.groupValues[1])))
        }
        return out.values.toList()
    }

    /** F8.2: the cash collected per day, from the Finances "Cash collection" rows seen (a row read twice counts once). */
    fun woltCash(rows: List<PlatformHistory.CashRow>): List<Total> =
        rows.distinct().groupBy { it.day }.map { (day, r) -> Total("wolt", "day:$day", WOLT_CASH, cash = round2(r.sumOf { it.amount })) }

    /** F8.2: Bolt All deliveries: each day header with its total right below it and its `Deliveries: N`. */
    fun boltDays(pkg: String, texts: List<String>): List<Total> {
        if (Platforms.of(pkg) != "bolt") return emptyList()
        val lines = clean(texts)
        val out = ArrayList<Total>()
        for ((i, s) in lines.withIndex()) {
            val day = PlatformHistory.dateHeader(s) ?: continue
            val total = lines.getOrNull(i + 1)?.let { BOLT_AMOUNT.matchEntire(it) } ?: continue
            // The count belongs to this day while no order row or other header came first.
            val count = (i + 2 until lines.size).asSequence().takeWhile { !BOLT_META.containsMatchIn(lines[it]) && PlatformHistory.dateHeader(lines[it]) == null }
                .firstNotNullOfOrNull { BOLT_COUNT.matchEntire(lines[it])?.groupValues?.get(1)?.toInt() }
            out.add(Total("bolt", "day:$day", BOLT_DAYS, deliveries = count, earnings = num(total.groupValues[1]), amountBasis = "unknown"))
        }
        return out
    }

    // ------------------------------------------------------------------ F8.3 record

    /** Wolt Stats with a known period ([CourierParser.statsPeriod]) and at least one value; else null. */
    fun fromStats(o: CourierObservation): Total? {
        if (Platforms.of(o.pkg) != "wolt") return null
        val period = o.statsPeriod ?: return null
        val t = Total("wolt", period, WOLT_STATS, o.statsTasksDone, o.statsEarningsAzn, o.statsEarningsNoTipsAzn, o.statsTipsAzn, o.statsDistanceKm)
        return t.takeIf { listOfNotNull(it.deliveries, it.earnings, it.earningsNoTips, it.tips, it.distanceKm).isNotEmpty() }
    }

    /** Same platform, period and source screen: the newer row replaces the older one. */
    fun key(t: Total) = "${t.platform}|${t.period}|${t.source}"

    /** The day folder of a day period ("day:2026-10-11" → "2026-10-11"); a week or month goes to the day it was seen. */
    fun dayOf(period: String): String? = period.removePrefix("day:").takeIf { period.startsWith("day:") }

    fun record(t: Total, observedAtMs: Long): JSONObject = JSONObject().put("schemaVersion", SCHEMA_VERSION).put("kind", "platform_total")
        .put("platform", t.platform).put("period", t.period).put("source_screen", t.source).put("observedAtMs", observedAtMs).apply {
            t.deliveries?.let { put("deliveries", it) }; t.earnings?.let { put("earnings_azn", it) }
            t.earningsNoTips?.let { put("earnings_no_tips_azn", it) }; t.tips?.let { put("tips_azn", it) }
            t.distanceKm?.let { put("distance_km", it) }; t.cash?.let { put("cash_azn", it) }; t.amountBasis?.let { put("amount_basis", it) }
        }

    fun parse(j: JSONObject): Total? {
        if (j.optString("kind") != "platform_total") return null
        fun d(k: String) = if (j.has(k)) j.optDouble(k).takeIf { !it.isNaN() } else null
        return Total(j.optString("platform").ifEmpty { return null }, j.optString("period").ifEmpty { return null }, j.optString("source_screen").ifEmpty { return null },
            if (j.has("deliveries")) j.optInt("deliveries") else null, d("earnings_azn"), d("earnings_no_tips_azn"), d("tips_azn"), d("distance_km"), d("cash_azn"),
            j.optString("amount_basis").ifEmpty { null })
    }

    /** The newest total per platform, period and source screen of the file's [lines]; unreadable lines are skipped. */
    fun latest(lines: Iterable<String>): List<Total> = lines.mapNotNull { l -> runCatching { JSONObject(l) }.getOrNull()?.let { j -> parse(j)?.let { it to j.optLong("observedAtMs") } } }
        .groupBy { key(it.first) }.values.map { rows -> rows.maxByOrNull { it.second }!!.first }

    // ------------------------------------------------------------------ F8.4 comparison

    /** Rawstats's side of one platform's day: delivered orders (unverified ones included), gross (fee + extra waiting),
     *  net, in-app tips of the platform list and the cash of cash orders. */
    data class Ours(val deliveries: Int, val gross: Double, val net: Double, val platformTips: Double, val cash: Double)

    fun ours(d: OrderHistory.Day): Ours = Ours(d.orders, d.gross, d.net, d.delivered.sumOf { it.platformTipAzn ?: 0.0 },
        d.delivered.filter { it.paymentMethod == "cash" }.sumOf { it.cashAmount ?: 0.0 })

    /** One compared value: [field] "deliveries", "earnings", "tips", "cash" or "distance"; [ours] null when Rawstats does not
     *  know it (then it is never red). */
    data class Check(val platform: String, val field: String, val source: String, val official: Double, val ours: Double?, val ok: Boolean)

    const val DISTANCE_SHARE = 0.10
    private fun sameMoney(a: Double, b: Double) = Math.abs(a - b) < 0.005

    /** The checks of [platform]'s day totals ([totals] of other platforms or of weeks/months are skipped). [shiftKm]:
     *  Rawstats's shift distance of that day, null when unknown. Wolt tips only from Stats: Finances shows them late. */
    fun compare(platform: String, totals: List<Total>, ours: Ours, shiftKm: Double?): List<Check> {
        val out = ArrayList<Check>()
        for (t in totals.filter { it.platform == platform && it.period.startsWith("day:") }) {
            fun money(field: String, official: Double?, mine: Double, alt: Double? = null) { official ?: return
                val ok = sameMoney(official, mine) || (alt != null && sameMoney(official, alt))
                // Bolt: the side that matched is shown (gross, else net).
                out += Check(platform, field, t.source, official, if (alt != null && !sameMoney(official, mine) && ok) alt else mine, ok) }
            fun count(official: Int?) { official ?: return; out += Check(platform, "deliveries", t.source, official.toDouble(), ours.deliveries.toDouble(), official == ours.deliveries) }
            when (t.source) {
                WOLT_STATS -> {
                    count(t.deliveries); money("earnings", t.earningsNoTips, ours.gross); money("tips", t.tips, ours.platformTips)
                    t.distanceKm?.let { km -> out += Check(platform, "distance", t.source, km, shiftKm, shiftKm == null || Math.abs(shiftKm - km) <= km * DISTANCE_SHARE) }
                }
                WOLT_DAILY -> money("earnings", t.earnings, ours.gross)
                WOLT_CASH -> money("cash", t.cash, ours.cash)
                BOLT_DAYS -> { count(t.deliveries); money("earnings", t.earnings, ours.gross, alt = ours.net) }
            }
        }
        return out
    }

    // ------------------------------------------------------------------ F8.5 day page

    private fun f(p: String, v: Double) = String.format(java.util.Locale.US, p, v)
    private fun name(platform: String) = if (platform == "bolt") "Bolt" else "Wolt"
    private fun value(field: String, v: Double) = when (field) { "deliveries" -> v.toInt().toString(); "distance" -> f("%.1f", v) + " km"; else -> f("%.2f", v) + " ₼" }

    /** "Gün toplamı (Finances) — Wolt: 18.24 ₼ · WEEM: 17.94 ₼" (WEEM "—" when it does not know the value). */
    fun line(c: Check, tl: (String, String) -> String): String {
        val label = when (c.field) {
            "deliveries" -> tl("Teslim", "Çatdırılma")
            "earnings" -> when (c.source) { WOLT_STATS -> tl("Kazanç (bahşişsiz, Stats)", "Qazanc (bəxşişsiz, Stats)")
                WOLT_DAILY -> tl("Gün toplamı (Finances)", "Gün cəmi (Finances)"); else -> tl("Gün toplamı", "Gün cəmi") }
            "tips" -> tl("Bahşiş (uygulama içi, Stats)", "Bəxşiş (tətbiqdaxili, Stats)")
            "cash" -> tl("Nakit toplama", "Nağd yığım")
            else -> tl("Mesafe", "Məsafə")
        }
        return "$label — ${name(c.platform)}: ${value(c.field, c.official)} · WEEM: ${c.ours?.let { value(c.field, it) } ?: "—"}"
    }

    /** K36 rows of [platform] matched to an order whose Rawstats gross differs from the list fee (Wolt only: Bolt's amount
     *  may be net). */
    fun feeDiffs(rows: List<JSONObject>, platform: String): List<JSONObject> = if (platform != "wolt") emptyList() else rows.filter { r ->
        val m = r.optJSONObject("match") ?: return@filter false
        r.optString("platform") == platform && !m.isNull("trip_gross") && r.has("amount") && !sameMoney(r.optDouble("amount"), m.optDouble("trip_gross"))
    }.sortedBy { it.optString("clock") }

    fun diffLine(r: JSONObject): String = listOfNotNull(r.optString("clock").takeIf { it.isNotEmpty() }, r.optString("restaurant", "").takeIf { it.isNotEmpty() && it != "null" },
        "${name(r.optString("platform"))}: ${f("%.2f", r.optDouble("amount"))} ₼ · WEEM: ${f("%.2f", r.getJSONObject("match").optDouble("trip_gross"))} ₼").joinToString(" · ")
}
