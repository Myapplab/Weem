package com.kuryelog.app.map

import android.content.Context
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.settings.MapSettingsCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** `map` dosyasındaki `settings.v1` (MAP.md 14.2): tek örnek; harita ekranı ve Harita ayarları aynı değeri görür. */
object MapSettingsRepo {
    @Volatile private var flow: MutableStateFlow<MapSettings>? = null

    fun settings(context: Context): StateFlow<MapSettings> = flow ?: synchronized(this) {
        flow ?: MutableStateFlow(MapSettingsCodec.decode(MapPrefsStore(context).getString(MapSettings.KEY))).also { flow = it }
    }

    fun update(context: Context, change: (MapSettings) -> MapSettings) {
        val f = settings(context) as MutableStateFlow<MapSettings>
        val next = change(f.value)
        MapPrefsStore(context).setString(MapSettings.KEY, MapSettingsCodec.toJson(next).toString())
        f.value = next
    }
}
