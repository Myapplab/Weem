package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.settings.MapPrefs

/** Kullanıcının sokak kararları (OSM way id; MAP.md 4.2). */
data class StreetPrefs(
    val approved: Set<Long> = emptySet(),
    val blocked: Set<Long> = emptySet(),
    val steep: Set<Long> = emptySet(),
    val names: Map<Long, String> = emptyMap(),
    val reverseHours: Map<Long, ReverseHours> = emptyMap(),
)

enum class StreetList { APPROVED, BLOCKED, STEEP }

/** `streets.*` anahtarlarının kodeği (MAP.md 4.2 tablo). İskelet (A6.2a): gövde A6.4'te. */
object StreetPrefsCodec {
    fun load(prefs: MapPrefs): StreetPrefs = TODO("A6.4")
}
