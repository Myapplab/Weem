package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.settings.MapPrefs

/** Sokak kararları deposu (Y8, `MAP.md` 4.2): onaylı, kapalı, aşırı eğimli way kümeleri. İskelet (A6.2a): gövdeler A6.4'te. */
class StreetPrefsStore(private val prefs: MapPrefs) {
    val approved: Set<Long> get() = TODO("A6.4")
    val blocked: Set<Long> get() = TODO("A6.4")
    val steep: Set<Long> get() = TODO("A6.4")
}
