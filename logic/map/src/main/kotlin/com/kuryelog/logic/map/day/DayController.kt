package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.settings.MapPrefs

/** Toast şeridi isteği (MAP.md 11.14): [text] seçili dilde. */
data class DayToast(val text: String)

/** Bir girdinin sonucu (MAP.md 11.14): olaylar, kaydedilecek mi, toast'lar, titreşim, güncel görünüm. */
class DayStep(val events: List<DayEvent>, val save: Boolean, val toasts: List<DayToast>, val haptic: Boolean, val view: DayView)

/** `:app`'e giden görünüm: gün durumu + ışık sayacı. */
class DayView(val state: DayState, val lightSinceMs: Long?)

/**
 * Saf gün durum makinesi (MAP.md 11.14, 11.17): girdiler fix, aşama okuması, vardiya, tık; komutlar `startDay`, `markLight`, `setDestination`…; yan etkiler (tercihler,
 * günlük, rapor, toast şeridi) Android `DayRuntime`'dadır. İskelet (A6.2b): gövdeler A6.8'de.
 */
class DayController(private val prefs: MapPrefs, private val lang: () -> Lang) {
    val view: DayView get() = TODO("A6.8")

    fun startDay(atMs: Long, reason: String = "manual"): DayStep = TODO("A6.8")

    /** Aşama okuması ([feed] null = okuma yok); [lastPoint] son konum. Aşama izleyicinin durumu `courier.follow.v1`'e yazılır (restoran adı yok). */
    fun onStages(feed: FollowerFeed?, nowMs: Long, lastPoint: GeoPoint?): DayStep = TODO("A6.8")
}
