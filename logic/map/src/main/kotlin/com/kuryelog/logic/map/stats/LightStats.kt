package com.kuryelog.logic.map.stats

import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.StopRec

/** Gün sayfasının "TRAFİK IŞIKLARI" kutuları (MAP.md 12.7): sayı, toplam bekleme, ortalama (tam sayı bölmesi), en uzun. */
class LightStats(val lights: List<StopRec>) {
    val count: Int get() = lights.size
    val totalS: Int get() = lights.sumOf { it.durS }
    val avgS: Int get() = if (lights.isEmpty()) 0 else totalS / lights.size
    val maxS: Int get() = lights.maxOfOrNull { it.durS } ?: 0

    companion object {
        fun of(d: DayState) = LightStats(d.lights)
    }
}
