package com.kuryelog.logic.map

import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.map.roads.RoadFiles
import java.time.ZoneId
import java.time.ZoneOffset

/** Sahte saat: duvar ve monoton saat elle ilerler; dilim Bakü (+04:00). */
class FakeClock(var nowMs: Long = 1_790_000_000_000L) : Clock {
    override fun wallMs() = nowMs
    override fun monoMs() = nowMs
    override fun zone(): ZoneId = ZoneOffset.ofHours(4)
    fun advance(ms: Long) { nowMs += ms }
}

/** Bellek içi `files/map/roads/` deposu. */
class MemoryRoadFiles : RoadFiles {
    val files = LinkedHashMap<String, String>()
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) { files[name] = text }
}
