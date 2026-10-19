package com.kuryelog.logic.map.input

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.Poi
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.net.HttpPort
import com.kuryelog.logic.map.settings.MapPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Öneri satırı (MAP.md 8.8): [source] `google` | `ors` | `offline`; [point] Google önerisinde seçilene dek null. */
data class SearchItem(val id: String, val title: String, val address: String, val source: String, val point: GeoPoint? = null, val restaurant: Boolean = false)

/** Arama çubuğunun durumu: [offline] öneriler çevrimdışı dizinden/son aramalardan geliyor; [error] kısa metin (dil seçili). */
data class SearchView(val items: List<SearchItem> = emptyList(), val busy: Boolean = false, val offline: Boolean = false, val error: String? = null)

/** Çevrimdışı dizin (MAP.md 8.8): adı olan POI'ler + graf yolları; etiketler [lang]'e göre. */
object OfflineIndex {
    fun build(graph: RoadGraph?, pois: List<Poi>, lang: Lang): List<SearchItem> = TODO("A6.6")

    /** Küçük harf; `ı→i, ə→e, ş→s, ç→c, ğ→g, ö→o, ü→u`; kırpılmış. */
    fun normalizeSearch(s: String): String = TODO("A6.6")
}

/**
 * Yer arama durum makinesi (MAP.md 8.8). Debounce [delay] ile (test sanal saat verir); sıra numarası eski yanıtı atar;
 * her hata (Error dahil) çevrimdışına düşer, meşgul kalmaz. İskelet (A6.2b): gövdeler A6.6'da.
 */
class PlaceSearch(
    private val scope: CoroutineScope,
    private val http: HttpPort,
    /** Ayarlar'daki anahtar + APK paketi ve imzası; yoksa APK anahtarı; hiçbiri yoksa null (istek atılmaz). */
    private val credentials: suspend () -> GoogleCredentials?,
    private val pois: () -> List<Poi>,
    private val graph: () -> RoadGraph?,
    private val lang: () -> Lang,
    private val near: () -> GeoPoint?,
    private val prefs: MapPrefs,
    private val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    val view: StateFlow<SearchView> get() = TODO("A6.6")

    fun query(value: String): Unit = TODO("A6.6")

    /** Seçim: Google önerisi için ayrıntı çağrısı (nokta, gerçek adres, restoran); son aramalara yazılır (Google'da nokta yok). */
    suspend fun select(item: SearchItem): SearchItem? = TODO("A6.6")

    companion object {
        const val DEBOUNCE_MS = 350L
        const val RECENTS_KEY = "search.recents.v2"
    }
}
