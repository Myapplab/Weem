package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.NavOrigin
import com.kuryelog.logic.core.RouteStatus
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.input.DestinationResolver
import com.kuryelog.logic.map.route.BikewayMarks
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.route.RouteEngine
import com.kuryelog.logic.map.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Navigasyonun durumu (MAP.md 7.2, 7.16): [seq] her yeni rotada +1; [arrived] son varılan durak (kapı notu kartı, 90 sn);
 * [reached] varılan hedefin iğnesi (kart kapansa da kalır, [REACHED_KEEP_M] ötesine geçince kalkar); [pending] bekleyen yeni adres kartı.
 */
data class NavState(
    val stops: List<Stop> = emptyList(),
    val plans: Map<RideMode, Plan> = emptyMap(),
    val mode: RideMode = RideMode.BATTERY,
    val busy: Boolean = false,
    val busyText: String? = null,
    val error: String? = null,
    val seq: Int = 0,
    val pending: Stop? = null,
    val arrived: Stop? = null,
    val reached: Stop? = null,
    val homeSuggest: Boolean = false,
) {
    val hasRoute: Boolean get() = plans.isNotEmpty()

    companion object {
        const val REACHED_KEEP_M = 250.0
        const val ARRIVED_CARD_MS = 90_000L
    }
}

/**
 * Saf navigasyon durum makinesi (MAP.md 7.16). İskelet (A6.2a/A6.2b): testlerin gerektirdiği yüzey; gerisi A6.6'da.
 * Girdiler: hedefler (`goTo`, `addStop`, `onExternalText`, `search`), fix'ler ([onFix]), ayar/öğrenme, sipariş bağı ([setOrder]); saat [tick] ile ilerler.
 * Çıktılar: [state], canlı ölçüm [progress], yayın kapısından geçen [routeStatus] (`NavPort.route`).
 */
class NavController(
    private val engine: RouteEngine,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.Jvm,
    private val resolver: DestinationResolver? = null,
) {
    val state: StateFlow<NavState> get() = TODO("A6.6")

    /** Etkin bacağın canlı ölçümü (göstergeler ve yayın bunu kullanır; her fix'te tazelenir). */
    val progress: StateFlow<RouteMeasure?> get() = TODO("A6.6")

    /** `NavFeedGate` kuralıyla güncellenen yayın (`NavPort.route`); açılışta hedefsiz bir kez. */
    val routeStatus: StateFlow<RouteStatus?> get() = TODO("A6.6")

    fun setGraph(graph: RoadGraph?): Unit = TODO("A6.6")
    fun setOrigin(p: GeoPoint): Unit = TODO("A6.6")
    fun setSettings(s: MapSettings): Unit = TODO("A6.6")
    fun setBikewayMarks(m: BikewayMarks): Unit = TODO("A6.6")

    /** Gün açık mı ve açık siparişin numarası (hedef bağı, MAP.md 7.3 `_bindShiftTarget`). */
    fun setOrder(dayActive: Boolean, currentOrder: Int?): Unit = TODO("A6.6")

    /** Hedefe rota ister (`replan` tetikleri: `prefs`); tek durak, `arrived`/`reached`/`pending` silinir. */
    fun goTo(target: GeoPoint, label: String? = null, restaurant: Boolean? = null, origin: NavOrigin = NavOrigin.MAP): Unit = TODO("A6.6")

    /** "Durak olarak ekle": durak sona eklenir, `arrived` silinir (`reached` kalır). */
    fun addStop(p: GeoPoint, label: String? = null): Unit = TODO("A6.6")

    fun clearRoute(): Unit = TODO("A6.6")

    /** Varış kartını kapatır; iğne (`reached`) kalır. */
    fun dismissArrived(): Unit = TODO("A6.6")

    /** Dış metin (bağlantı, paylaşım, pano): ayrıştır, çöz, <60 m kuralı, durak yoksa rota, varsa bekleyen kart (MAP.md 8.7). */
    suspend fun onExternalText(text: String, source: String, origin: NavOrigin? = null, referrer: String? = null, requestedAtMs: Long? = null): Unit = TODO("A6.6")

    /** Arama çubuğu (Enter): `origin = SEARCH`, doğrudan yeni rota. */
    suspend fun search(text: String): Unit = TODO("A6.6")

    /** Her konum okumasında: yol eşleştirme, iğne, varış, sapma, canlı ölçüm. */
    fun onFix(fix: NavFix): Unit = TODO("A6.6")

    /** Saat ilerler: varış kartı 90 sn sonra kalkar, ters yön önerisi 15 sn sonra kalkar. */
    fun tick(): Unit = TODO("A6.6")

    /** Test kancası: durumu doğrudan kurar (Dart testlerinin `nav.state = …` karşılığı). */
    internal fun debugSetState(s: NavState): Unit = TODO("A6.6")
}
