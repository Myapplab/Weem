package com.kuryelog.logic.orders

/** WEEM (ORDERS.md 13.3): Rawstats `EconomicsLogs.platformOf` ve `EconomicsLogs.stage` (EconomicsLogs.kt:39-44, 155-160 @ 6388c04) aynen; `:economics` buradan kullanır. */
object Platforms {
    /** "wolt" / "bolt" (the schemas know no other platform), else null. */
    fun of(pkg: String): String? = when {
        pkg.contains("wolt", true) -> "wolt"
        pkg.contains("bolt", true) || pkg.contains("mtakso", true) -> "bolt"
        else -> null
    }

    /** Feed v2 stage of an open order ([TripStages] uses the same names); null once it ended. */
    fun stage(p: TripPhase): String? = when (p) {
        TripPhase.TO_RESTAURANT -> "to_pickup"; TripPhase.WAITING -> "at_pickup"
        TripPhase.TO_CUSTOMER -> "to_dropoff"; TripPhase.AT_CUSTOMER -> "at_dropoff"
        else -> null
    }
}
