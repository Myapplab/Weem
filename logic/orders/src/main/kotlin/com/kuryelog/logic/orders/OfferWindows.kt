package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.CourierParser

/**
 * WEEM (ORDERS.md 2.13): Rawstats bu iki değeri `EconomicsV2.PRIORS`'tan okuyordu (`wolt_K_support.window_min`,
 * `documented_window_ms`; EconomicsV2.kt:85, 103 @ 6388c04). `:orders` `:economics`'i görmediği için sabit; `:economics`
 * (A4.1) testi `PRIORS` ile eşitliğini denetler, öncül değişirse iki yer birlikte değişir.
 */
object OfferWindows {
    /** PRIOR `wolt_K_support.window_min` = 60. */
    const val DECLINE_WINDOW_MIN = 60.0
    /** PRIOR `documented_window_ms`: UPPER BOUND, not verified for Wolt AZ (Wolt 30-60 s, Bolt "one minute"). */
    val DOCUMENTED_WINDOW_MS: Map<String, Long> = mapOf("wolt" to 60_000L, "bolt" to 60_000L)

    val declineWindowMs: Long get() = (DECLINE_WINDOW_MIN * 60_000).toLong()

    /** Rawstats `DeliveryCore.offerWindowMs` platform seçimi aynen: Wolt paketleri, adında "bolt" geçen; öbürü null. */
    fun offerWindowMs(pkg: String): Long? {
        val platform = if (pkg in CourierParser.WOLT) "wolt" else if (pkg.contains("bolt", true)) "bolt" else return null
        return DOCUMENTED_WINDOW_MS[platform]
    }
}
