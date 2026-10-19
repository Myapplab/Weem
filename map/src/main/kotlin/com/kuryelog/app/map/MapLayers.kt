package com.kuryelog.app.map

import android.content.Context
import com.kuryelog.logic.map.graph.PoiCorrections
import com.kuryelog.logic.map.route.BikewayMarksStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Harita katmanlarının kalıcı verisi (MAP.md 5.1, 5.3): elle bisiklet yolu işaretleri (`streets.bikeway.v1`) ve işletme düzeltmeleri (`places.local.v1`); `map` tercih dosyasında.
 * Değişince [rev] artar (harita katmanı yeniden kurulur; açık rota işaret kümesi değişince yeniden hesaplanır, A6.6).
 */
object MapLayers {
    @Volatile private var bikeway: BikewayMarksStore? = null
    @Volatile private var corrections: PoiCorrections? = null
    private val revFlow = MutableStateFlow(0)

    /** Katman verisi her değiştiğinde artar. */
    val rev: StateFlow<Int> = revFlow.asStateFlow()

    fun bikeway(context: Context): BikewayMarksStore =
        bikeway ?: synchronized(this) { bikeway ?: BikewayMarksStore(MapPrefsStore(context)).also { bikeway = it } }

    fun corrections(context: Context): PoiCorrections =
        corrections ?: synchronized(this) { corrections ?: PoiCorrections(MapPrefsStore(context)).also { corrections = it } }

    /** Bir işaret ya da düzeltme yazıldıktan sonra çağrılır. */
    fun changed() {
        revFlow.value += 1
    }
}
