package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.NavOrigin
import com.kuryelog.logic.map.geo.GeoPoint

/** [TASK]: günlük iş (Y20, S9); siparişin restoran/müşteri hedefi sayılmaz. */
enum class StopPurpose { MANUAL, ORDER, HOME, TASK }

enum class StopConfidence { HIGH, MEDIUM, LOW }

/**
 * Rota durağı (MAP.md 7.2): [restaurant] true/false/null = bilinmiyor; [unitNote] kapı notu; [requestedAtMs] hedefin istendiği an,
 * [sourcePackage] isteği gönderen uygulama (Wolt/Bolt).
 */
data class Stop(
    val point: GeoPoint,
    val label: String,
    val unitNote: String? = null,
    val confidence: StopConfidence = StopConfidence.HIGH,
    val restaurant: Boolean? = null,
    val orderId: Int? = null,
    val purpose: StopPurpose = StopPurpose.MANUAL,
    val origin: NavOrigin = NavOrigin.MAP,
    val requestedAtMs: Long? = null,
    val sourcePackage: String? = null,
) {
    /** Aynı durak sipariş hedefi olur: `purpose = ORDER`, `orderId = n`, `restaurant = toRestaurant`; kaynak, saat ve gönderen korunur. */
    fun forOrder(n: Int, toRestaurant: Boolean?): Stop = copy(purpose = StopPurpose.ORDER, orderId = n, restaurant = toRestaurant)
}
