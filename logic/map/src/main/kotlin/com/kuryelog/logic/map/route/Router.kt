package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.geo.GeoPoint

/** Rota hatası; [message] kullanıcıya TR (dil çevirisiyle) gösterilir (MAP.md 3.12). */
class RoutingException(override val message: String) : Exception(message)

object Router {
    /** Tek bacak: A* (MAP.md 3.6–3.9). [heading] yalnız ilk bacakta. */
    fun routeLeg(m: CostModel, from: GeoPoint, to: GeoPoint, heading: Double? = null): RouteLeg = TODO("A6.4")
}
