package com.kuryelog.logic.orders

import java.math.BigDecimal
import java.math.RoundingMode
import com.kuryelog.logic.capture.OwnLines

// Rawstats OverlayText.kt:1-97 @ 6388c04 (biçimlendiriciler), satır satır. WEEM (ORDERS.md 6.3, CAPTURE.md 2.10): söz dağarcığı ve
// tanıma (`isOwn`, `withoutOwn`) `logic/capture` `OwnLines`'ta; burada yalnız biçimlendiriciler, sabitler oradan (tek kaynak).
// TripPhase → OwnLines.PHASES anahtarı enum adıdır.
/**
 * Every line WEEM draws over the courier apps (the overlay badge), formatted in one place.
 *
 * WatchService and OrderTracker build the badge only with the formatters below, and CourierParser drops every
 * line [OwnLines.isOwn] recognises before it parses a frame (accessibility, OCR or notification). An OCR crop that
 * contains the badge can therefore never turn "● 30.1 km" or "Wolt · 2.21₼ · Teklif" into the offer's km or fee
 * (field evidence N11). Formatters take `tl(tr, az)` (`I18n.tl` in the app). Pure Kotlin: no Android imports.
 */
object OverlayText {
    /** Separator between the parts of one panel line. */
    const val SEP = OwnLines.SEP
    private const val BULLET = OwnLines.BULLET
    private const val CURRENCY = OwnLines.CURRENCY
    private const val NET = OwnLines.NET
    private val OFFER = OwnLines.OFFER
    private val ORDER = OwnLines.ORDER
    private val WAITING = OwnLines.WAITING
    private val OFFLINE = OwnLines.OFFLINE
    private val TODAY = OwnLines.TODAY
    private val ORDERS = OwnLines.ORDERS
    private val PHASES: Map<TripPhase, Pair<String, String>> = TripPhase.values().associateWith { OwnLines.PHASES.getValue(it.name) }

    private fun pick(p: Pair<String, String>, tl: (String, String) -> String) = tl(p.first, p.second)
    private fun fixed(x: Double, n: Int) = BigDecimal(x).setScale(n, RoundingMode.HALF_EVEN).toPlainString()
    private fun money(x: Double) = fixed(x, 2) + CURRENCY

    /** `WatchService.updateBadge`: distance ridden this shift, "● 12.3 km". */
    fun distance(km: Double): String = "$BULLET ${fixed(km, 1)} km"

    /** A pending offer: "Wolt · 2.21₼ · <restaurant|Teklif>"; with [net] the WP9 line "Wolt · 3.94₼ · net 3.15₼ · <restaurant>". */
    fun offer(platform: String, gross: Double?, label: String?, net: Double? = null, tl: (String, String) -> String): String =
        listOfNotNull(platform, money(gross ?: 0.0), net?.let { "$NET ${money(it)}" }, label ?: pick(OFFER, tl)).joinToString(SEP)

    /**
     * Net of a shown gross fee for the offer line: [gross] less the fleet's [commissionPct] (`commission:<pkg>`), as
     * `TripRecord.net` computes it before a tip. Null when the fee was not read or no commission is set, so the badge never
     * shows a net it does not know. No colour and no verdict: the v2 verdict stays in shadow mode (SPEC 19).
     */
    fun net(gross: Double?, commissionPct: Double?): Double? =
        if (gross == null || commissionPct == null) null else gross * (1.0 - commissionPct / 100.0)

    /** An active trip: "Wolt · <restaurant|customer|Sipariş> · <phase>". */
    fun trip(platform: String, label: String?, phase: TripPhase, tl: (String, String) -> String): String =
        listOf(platform, label ?: pick(ORDER, tl), phase(phase, tl)).joinToString(SEP)

    /** K35 overlay badge: only the platform and the phase, "Wolt · Müşteriye gidiliyor" (no restaurant or customer name). */
    fun tripStatus(platform: String, phase: TripPhase, tl: (String, String) -> String): String = platform + SEP + phase(phase, tl)

    /** The phase of a trip as the badge and the order cards show it. */
    fun phase(p: TripPhase, tl: (String, String) -> String): String = pick(PHASES.getValue(p), tl)

    /** A platform with nothing open: "Wolt · Sipariş bekleniyor" while online, else "Wolt · Çevrimdışı". */
    fun status(platform: String, online: Boolean, tl: (String, String) -> String): String =
        platform + SEP + pick(if (online) WAITING else OFFLINE, tl)

    /** The day count: "Bugün: 7 sipariş" / "Bu gün: 7 sifariş". */
    fun today(delivered: Int, tl: (String, String) -> String): String =
        tl("${TODAY.first}: $delivered ${ORDERS.first}", "${TODAY.second}: $delivered ${ORDERS.second}")

    /**
     * The badge text below the distance line: open offers, open trips, then one status line per platform with nothing open,
     * then the day count. K30: after one of two offers is accepted and the other declined, the declined platform reads
     * "Sipariş bekleniyor" while it stays online and "Çevrimdışı" once the courier went offline there; one line per platform
     * even when it has several packages (online wins). [names] false for the overlay (K35).
     */
    fun badge(core: DeliveryCore, sessions: OnlineSessions, delivered: Int, names: Boolean, commission: (String) -> Double?,
              platform: (String) -> String, tl: (String, String) -> String): String {
        // A pending offer also shows its net after the fleet commission ("Wolt · 3.94₼ · net 3.15₼ · <restaurant>"), only when one is set.
        val lines = core.pending.values.map { offer(platform(it.pkg), it.data.gross, if (names) it.data.restaurant else null, net = net(it.data.gross, commission(it.pkg)), tl = tl) } +
            core.trips.values.filter { it.active }.map {
                if (names) trip(platform(it.pkg), it.pickups.lastOrNull()?.restaurant ?: it.customers[it.currentCustomer]?.details?.name, it.phase, tl)
                else tripStatus(platform(it.pkg), it.phase, tl) }
        // Online with nothing open: waiting for an order (never "to restaurant" while Wolt shows its waiting map).
        val busy = (core.pending.values.map { platform(it.pkg) } + core.trips.values.filter { it.active }.map { platform(it.pkg) }).toSet()
        val status = sessions.known().groupBy(platform).toSortedMap().mapNotNull { (name, pkgs) ->
            if (name in busy) null else status(name, pkgs.any { sessions.state(it) == "online" }, tl)
        }
        val all = lines + status
        val day = "\n" + today(delivered, tl)
        return if (all.isEmpty()) day else "\n" + all.joinToString("\n") + day
    }
}
