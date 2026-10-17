package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats CustomerCard.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * E16 (Faz 4): when the customer card opens and how big it is. It opens on whichever comes first: [STRAIGHT_M] to the address in
 * a straight line, [ROUTE_M] left on YolGo's route, or the last [LAST_TURNS] turns of that route. The route comes from YolGo's
 * navigation payload (E8, [RouteStatus]) and counts only when it is this customer's ([routeFor]). It replaced the 1,000 m route /
 * 700 m straight trigger that read only Google Maps/Waze notifications ([NavDistance]). Default size [DEFAULT_SIZE_PCT] % of
 * the old card (15 % smaller), chosen from [SIZE_CHOICES] under [SIZE_KEY]. Pure.
 */
object CustomerCard {
    const val STRAIGHT_M = 300.0
    const val ROUTE_M = 300.0
    const val LAST_TURNS = 2
    /** A navigation request this long before Collect (or the previous handover) may still be this customer's ("Navigate" first). */
    const val REQUEST_SLACK_MS = 60_000L
    const val FIX_MAX_AGE_MS = 15_000L
    const val FIX_MAX_ACCURACY_M = 40.0
    const val SIZE_KEY = "customerCardSizePct"
    const val DEFAULT_SIZE_PCT = 85
    val SIZE_CHOICES = listOf(70, 85, 100, 115)
    /** The card's height as a share of the screen at 100 % (closed / "Büyüt"). */
    private const val HEIGHT = 0.40
    private const val HEIGHT_EXPANDED = 0.62

    enum class Why { STRAIGHT, ROUTE, TURNS }

    /**
     * YolGo's route when it leads to this customer: measured within [RouteStatus.ROUTE_MAX_AGE_MS], its target not YolGo's guess of
     * a restaurant, and requested after [since] (the order's Collect, or the previous customer's handover) less
     * [REQUEST_SLACK_MS]. Otherwise null (YolGo absent, old, or still on the way to the restaurant or the previous customer).
     */
    fun routeFor(state: RouteStatus?, since: Long?, now: Long): RouteStatus.Route? {
        val r = state?.route ?: return null
        val t = state.target ?: return null
        if (now - r.measuredAtMs !in -5_000L..RouteStatus.ROUTE_MAX_AGE_MS || t.kind == NavKind.RESTAURANT) return null
        if (since != null && t.requestedAtMs < since - REQUEST_SLACK_MS) return null
        return r
    }

    /** Straight-line metres from a fresh ([FIX_MAX_AGE_MS]), accurate ([FIX_MAX_ACCURACY_M]) fix to the address, or null. */
    fun straight(fixAt: Long, lat: Double, lon: Double, accuracyM: Double?, toLat: Double?, toLon: Double?, now: Long): Double? {
        if (toLat == null || toLon == null || accuracyM == null || accuracyM > FIX_MAX_ACCURACY_M || now - fixAt !in 0..FIX_MAX_AGE_MS) return null
        return VenueArrival.metres(lat, lon, toLat, toLon)
    }

    /** Why the card opens now (the first of the three that holds), or null. Unknown turns never trigger by themselves. */
    fun trigger(straightM: Double?, route: RouteStatus.Route?): Why? = when {
        straightM != null && straightM <= STRAIGHT_M -> Why.STRAIGHT
        route != null && route.remainingM <= ROUTE_M -> Why.ROUTE
        else -> route?.remainingTurns?.takeIf { it <= LAST_TURNS }?.let { Why.TURNS }
    }

    private fun dist(m: Double) = if (m >= 1000) String.format(java.util.Locale.US, "%.1f km", m / 1000) else "${m.toInt()} m"

    /** The card's second line when it opens. */
    fun reason(why: Why, straightM: Double?, route: RouteStatus.Route?, tl: (String, String) -> String): String = when (why) {
        Why.STRAIGHT -> tl("Adrese yaklaşık ${straightM?.toInt()} m (kuş uçuşu)", "Ünvana təxminən ${straightM?.toInt()} m (düz xətt)")
        Why.ROUTE -> tl("Rotada yaklaşık ${route?.remainingM?.toInt()} m kaldı (WEEM)", "Marşrutda təxminən ${route?.remainingM?.toInt()} m qalıb (WEEM)")
        Why.TURNS -> tl("Son ${route?.remainingTurns} dönüş (WEEM, ${route?.remainingM?.let(::dist)})", "Son ${route?.remainingTurns} dönmə (WEEM, ${route?.remainingM?.let(::dist)})")
    }

    /** Live line while the card is open: YolGo's remaining route and turns. */
    fun live(r: RouteStatus.Route, tl: (String, String) -> String): String =
        tl("Kalan: ", "Qalıb: ") + dist(r.remainingM) + (r.remainingTurns?.let { tl(" · $it dönüş", " · $it dönmə") } ?: "") + " (WEEM)"

    /** The stored size choice, or the default for a value that is not one of [SIZE_CHOICES]. */
    fun sizePct(stored: Int): Int = stored.takeIf { it in SIZE_CHOICES } ?: DEFAULT_SIZE_PCT
    fun scale(pct: Int): Float = pct / 100f
    fun heightShare(pct: Int, expanded: Boolean): Double = (if (expanded) HEIGHT_EXPANDED else HEIGHT) * pct / 100.0
}
