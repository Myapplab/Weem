package com.kuryelog.logic.orders

import com.kuryelog.logic.core.*

// İskelet (A3.2): Rawstats `TripStages.feed/trip` (TripStages.kt:59-78 @ 6388c04) WEEM'de `logic/core` `TripStages` tipini üretir
// (ORDERS.md 13.2); bu üretici `:orders`'ta. Gövdeler A3.5'te.
object OrderStages {
    /** En çok 3 açık + son 10 dk'da biten en çok 3 sipariş; [closedAt]: kapanış olayının anı (kayıtta yoksa). */
    fun of(trips: Collection<TripRecord>, closedAt: (String) -> Long?, now: Long): TripStages = TODO("A3.5")
    fun trip(t: TripRecord, closedAt: Long?): TripStages.Trip = TODO("A3.5")
}
