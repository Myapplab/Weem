package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.TripStages
import com.kuryelog.logic.core.TripStages.Stage

/** `courier_stage_follow_test`/`courier_stages_e8_test` ortak girdileri: feed v2 JSON'u yerine tipli [FollowerFeed] (S7: `TripStages` zaten tipli). */
object StageFixtures {
    const val WOLT = "com.wolt.courierapp"
    const val BOLT = "com.bolt.food.courier"
    const val TRIP_A = "trip-1791295203000-1"
    const val TRIP_B = "trip-1791295260000-2"
    const val NOW0 = 1791295200000L

    fun ms(s: Int) = NOW0 + s * 1000L

    fun trip(id: String, stage: Stage?, pkg: String = WOLT) = FollowerTrip(id, pkg, stage)

    fun feed(vararg trips: FollowerTrip) = FollowerFeed(trips.toList())

    fun ev(stage: Stage, s: Int, pickup: Int? = null, source: String? = null) = TripStages.Event(stage, ms(s), pickup, source)

    /** Rawstats `TripStages.trip`: açık sipariş (outcome null) ya da biten (stage null, sonuç ve saat). */
    fun line(
        id: String,
        events: List<TripStages.Event>,
        stage: Stage = Stage.TO_PICKUP,
        outcome: TripStages.Outcome? = null,
        endedS: Int? = null,
        pkg: String = WOLT,
    ) = TripStages.Trip(id, pkg, active = outcome == null, stage = if (outcome == null) stage else null, outcome = outcome, endedAtMs = endedS?.let(::ms), events = events)
}
