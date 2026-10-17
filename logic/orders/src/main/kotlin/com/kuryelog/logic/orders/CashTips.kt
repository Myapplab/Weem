package com.kuryelog.logic.orders

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats CashTips.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * K31: cash or card, read from Wolt's delivery steps screen (docs/KURYELOG_ISTEKLER.md K31). Strings as the user saw them
 * (8 Oct 2026): `Cash order`, `No change needed`, `Steps to complete the delivery`, `Collect cash payment` and the checkbox
 * `I have collected the cash (AZN 20.40)`. The checkbox has to be ticked before the order can be delivered, so its amount is
 * the reliable cash amount. The change-needed variant and Bolt Food's cash screen were never seen and are not recognised.
 */
object CashPayment {
    const val CASH = "cash"
    const val CARD_INFERRED = "card_inferred"
    const val SOURCE_CASH_SCREEN = "wolt_cash_screen"
    /** The steps screen was seen without any cash step: card is an inference, not an observation. */
    const val SOURCE_STEPS_NO_CASH = "wolt_delivery_steps_no_cash"
    const val NOT_OBSERVED = "not_observed"

    /** What one Wolt frame says about payment. [amount] is null when the checkbox line was not read (or unreadable). M16: [changeAzn]
     *  0.0 for "No change needed", the amount next to "Total change needed", else null; [checked] the checkbox's tick from the
     *  accessibility tree (null: not known, e.g. OCR). */
    data class Screen(val cash: Boolean, val amount: Double?, val noChangeNeeded: Boolean, val steps: Boolean,
                      val changeAzn: Double? = null, val checked: Boolean? = null)

    private const val SP = "[\\s\\p{Z}]"
    private val CASH_MARKS = listOf(Regex("^cash order$"), Regex("^collect cash payment$"), Regex("^i have collected the cash(?![\\p{L}\\p{N}_])"))
    private val STEPS = Regex("^steps to complete the delivery$")
    private val NO_CHANGE = Regex("^no change needed$")
    /** `I have collected the cash (AZN 20.40)`; the amount may sit in its own node. Dot decimals only (the only form seen on Wolt). */
    private val AMOUNT = Regex("i have collected the cash$SP*\\($SP*(?:azn|₼)$SP*([0-9]{1,5}(?:\\.[0-9]{1,2})?)$SP*\\)")

    private val CHANGE = Regex("^(?:total )?change needed$")
    private val AZN = Regex("^-?(?:azn|₼)$SP*([0-9]{1,5}(?:\\.[0-9]{1,2})?)$")

    private fun norm(s: String) = s.lowercase().replace(Regex("$SP+"), " ").trim()

    /** Null for other packages and for frames with neither a cash step nor the steps header. [checked]: the checkbox's tick as the
     *  accessibility reader saw it (M16), null when unknown. */
    fun parse(pkg: String, texts: List<String>, checked: Boolean? = null): Screen? {
        if (pkg !in CourierParser.WOLT) return null
        val lines = texts.map(::norm).filter { it.isNotEmpty() }
        val cash = lines.any { l -> CASH_MARKS.any { it.containsMatchIn(l) } }
        val steps = lines.any { STEPS.matches(it) }
        if (!cash && !steps) return null
        val amount = AMOUNT.find(lines.joinToString(" "))?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        val noChange = lines.any { NO_CHANGE.matches(it) }
        val ci = lines.indexOfFirst { CHANGE.matches(it) }
        val change = if (noChange) 0.0 else if (ci < 0) null else listOfNotNull(lines.getOrNull(ci + 1), lines.getOrNull(ci - 1))
            .firstNotNullOfOrNull { AZN.find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
        return Screen(cash, amount, noChange, steps, change, checked?.takeIf { cash })
    }

    /** Copies [s] into the observation fields the core reads (the core keeps no reference to this file). */
    fun attach(o: CourierObservation, s: Screen?): CourierObservation =
        if (s == null) o else o.copy(cashOrder = s.cash, cashAmount = s.amount, cashStepsSeen = s.steps, cashChecked = s.checked, cashChangeAzn = s.changeAzn)

    private fun money(x: Double) = String.format(java.util.Locale.US, "%.2f", x) + "₼"

    /** M16: the order card's cash line, "Nakit 20.40₼ · para üstü yok · kutu ✓ 14:41"; null for an order not known to be cash. */
    fun cardLine(t: TripRecord, clock: (Long) -> String, tl: (String, String) -> String): String? {
        if (t.paymentMethod != CASH) return null
        val amount = t.cashAmount?.let { tl("Nakit ", "Nağd ") + money(it) } ?: tl("Nakit (tutar okunamadı)", "Nağd (məbləğ oxunmadı)")
        val change = when {
            t.cashNoChange == true -> tl("para üstü yok", "qalıq yoxdur")
            t.cashChangeAzn != null -> tl("para üstü ", "qalıq ") + money(t.cashChangeAzn!!)
            else -> null
        }
        val box = t.cashCheckedAt?.let { tl("kutu ✓ ", "qutu ✓ ") + clock(it) } ?: tl("kutu: görülmedi", "qutu: görünmədi")
        return listOfNotNull(amount, change, box).joinToString(" · ")
    }

    /** M16: the cash of the day's delivered cash orders (inferred deliveries included, like the day count). */
    fun dayTotal(trips: Collection<TripRecord>): Double =
        BigDecimal.valueOf(trips.filter { it.paymentMethod == CASH && it.countsAsDelivered }.sumOf { it.cashAmount ?: 0.0 })
            .setScale(2, RoundingMode.HALF_EVEN).toDouble()
}

/**
 * K32: after a cash order is delivered the courier says how much cash they received; tip = received − cash amount (K31).
 * Every answer is a new row of [FILE] (append-only, schemaVersion 1, no customer data); the newest row per trip wins,
 * so a corrected answer simply appends again. A negative difference is stored as it is and flagged, never clamped.
 */
object CashTips {
    const val FILE = "economics-cash-tips.jsonl"
    const val SCHEMA = 1
    const val SOURCE = "user_cash_input"
    const val VIA_NOTIFICATION = "notification"
    const val VIA_CARD = "card"
    /** F4.6–F4.9: a quick button (1/2/3/5 ₼) of the notification, and the small window opened by tapping it. */
    const val VIA_QUICK = "notification_quick"
    const val VIA_WINDOW = "tip_window"
    const val SOURCE_QUICK = "user_quick_tip"
    /** The cash amount typed in the window because Wolt's checkbox line was not read (F4.9). */
    const val CASH_SOURCE_MANUAL = "user_manual"
    val QUICK_TIPS = listOf(1.0, 2.0, 3.0, 5.0)
    /** RemoteInput result key and the extras of the reply intent. */
    const val KEY_RECEIVED = "cash_received"
    const val EXTRA_TRIP = "trip_id"
    /** F4.6: the quick button's tip (₼). */
    const val EXTRA_TIP = "tip_azn"

    private fun r2(x: Double) = BigDecimal.valueOf(x).setScale(2, RoundingMode.HALF_EVEN).toDouble()

    /** Received − cash, rounded to the qəpik; null when the cash amount is unknown. */
    fun tip(received: Double, cash: Double?): Double? =
        cash?.let { BigDecimal.valueOf(received).subtract(BigDecimal.valueOf(it)).setScale(2, RoundingMode.HALF_EVEN).toDouble() }

    private val INPUT = Regex("^[0-9]{1,4}(?:\\.[0-9]{1,2})?$")
    /** The typed amount (`25`, `25.5`, `25,50`, `25 ₼`, `AZN 25`); null for anything else. */
    fun parseInput(s: String?): Double? {
        val t = (s ?: return null).lowercase().replace("azn", "").replace("₼", "").replace(Regex("[\\s\\p{Z}]+"), "").replace(',', '.')
        return if (INPUT.matches(t)) t.toDoubleOrNull()?.takeIf { it.isFinite() } else null
    }

    /** F4.6: a quick button adds the tip to the cash amount; without a read amount there is nothing to add to. */
    fun receivedFor(tip: Double, cash: Double?): Double? = cash?.let { BigDecimal.valueOf(it).add(BigDecimal.valueOf(tip)).setScale(2, RoundingMode.HALF_EVEN).toDouble() }
    fun quickAllowed(cash: Double?) = cash != null

    /** F4.7: the window's live tip while typing; null until the text is an amount and the cash amount is known. */
    fun preview(receivedText: String?, cash: Double?): Double? = parseInput(receivedText)?.let { tip(it, cash) }

    /** Asked once a cash order is delivered, really or as a K34 inference. */
    fun shouldAsk(t: TripRecord): Boolean = t.paymentMethod == CashPayment.CASH && (t.completedAt != null || t.deliveredInferred)

    /** [cashOverride]: typed in the window when no amount was read (F4.9); a read amount always wins. */
    fun record(t: TripRecord, received: Double, at: Long, via: String, cashOverride: Double? = null, tipSource: String = SOURCE): JSONObject {
        val manual = t.cashAmount == null && cashOverride != null
        val cash = t.cashAmount ?: cashOverride
        val tip = tip(received, cash)
        return JSONObject().put("schemaVersion", SCHEMA).put("kind", "cash_tip").put("trip_id", t.id).put("pkg", t.pkg)
            .put("platform", if (t.pkg in CourierParser.WOLT) "wolt" else "bolt").put("at", at).put("via", via)
            .put("received_azn", r2(received))
            .put("cash_amount_azn", cash?.let(::r2) ?: JSONObject.NULL)
            .put("cash_amount_source", when { manual -> CASH_SOURCE_MANUAL; t.cashAmount != null -> t.paymentSource ?: CashPayment.NOT_OBSERVED; else -> CashPayment.NOT_OBSERVED })
            .put("tip_azn", tip ?: JSONObject.NULL).put("tip_source", if (tip != null) tipSource else JSONObject.NULL)
            .put("negative_tip", tip != null && tip < 0)
            .put("delivery", if (t.completedAt != null) "delivered" else "delivered_inferred")
    }

    /** Newest valid schemaVersion-1 row per trip id (equal times: the later line wins). */
    fun latest(lines: Sequence<String>): Map<String, JSONObject> {
        val out = HashMap<String, JSONObject>()
        for (line in lines) {
            val j = runCatching { JSONObject(line) }.getOrNull() ?: continue
            if (j.optInt("schemaVersion", -1) != SCHEMA) continue
            val id = j.optString("trip_id", "").ifEmpty { null } ?: continue
            if (!j.has("received_azn") || j.isNull("received_azn")) continue
            val prev = out[id]
            if (prev == null || j.optLong("at") >= prev.optLong("at")) out[id] = j
        }
        return out
    }

    fun num(j: JSONObject, k: String): Double? = if (j.has(k) && !j.isNull(k)) j.optDouble(k).takeIf { it.isFinite() } else null
}
