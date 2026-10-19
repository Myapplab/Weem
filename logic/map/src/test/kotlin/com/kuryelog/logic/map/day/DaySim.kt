package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.GeoPoint
import java.time.Instant

/** Ortak test girdileri (YolGo `shift_engine_test.dart` `_Sim` ve diğer gün testlerinin yardımcıları). */
object DayFixtures {
    const val LAT0 = 40.40
    const val LON0 = 49.85
    fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    /** `DateTime(2026, 10, 1, 12)` yerel saat yerine sabit an. */
    val SIM_START = ms("2026-10-01T12:00:00Z")
}

/** Kuzeye doğru düz bir yolda saniyede bir GPS fix'i üretir (Dart `_Sim`). */
class DaySim(var info: RoadInfo = RoadInfo(onRoad = true, junctionM = 10.0), startAtMs: Long = DayFixtures.SIM_START) {
    val e = ShiftEngine()
    var t = startAtMs
    var north = 0.0
    val events = ArrayList<DayEvent>()

    init {
        events += e.startDay(t)
    }

    fun road(lat: Double, lon: Double): RoadInfo = info

    fun ride(seconds: Int, kmh: Double): DaySim {
        for (i in 0 until seconds) {
            t += 1000
            north += kmh / 3.6
            events += e.feed(DayFix(t, DayFixtures.LAT0 + north / 111320, DayFixtures.LON0, speedMps = kmh / 3.6), ::road)
        }
        return this
    }

    fun stop(seconds: Int) = ride(seconds, 0.0)
    fun saw(type: String) = events.any { it.type == type }
    fun point(northM: Double) = GeoPoint(DayFixtures.LAT0 + northM / 111320, DayFixtures.LON0)
}
