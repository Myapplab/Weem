package com.kuryelog.logic.orders

import com.kuryelog.logic.core.CourierPlatform
import com.kuryelog.logic.core.DaySummary

/**
 * WEEM `OrderEvents.day` (plan 4.4 `DaySummary` v1; LINK.md 4.2): bugünün siparişlerinden platform başına sipariş, brüt, net — sayım
 * Siparişler sekmesiyle aynı ([OrderHistory.Day]: doğrulanmamış teslimler dahil). Çevrimiçi dakika ve günün nakdi burada ölçülmez: null.
 */
object OrderDaySummary {
    fun of(day: String, trips: Collection<TripRecord>): DaySummary {
        val platforms = trips.groupBy { CourierPlatform.ofPackage(it.pkg) }.mapNotNull { (p, list) ->
            val d = OrderHistory.Day(day, list)
            if (p == null || d.delivered.isEmpty()) null
            else p to DaySummary.PlatformDay(d.orders, d.gross.takeIf { d.unpriced < d.delivered.size }, d.net.takeIf { d.delivered.any { it.net != null } })
        }.toMap()
        return DaySummary(day, platforms)
    }
}
