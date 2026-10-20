package com.kuryelog.app.map

import android.content.Context
import com.kuryelog.app.core.Home
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.OrderEvents
import com.kuryelog.logic.map.day.DayController
import com.kuryelog.logic.map.day.DayFix
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.DayStep
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.day.RoadInfoBuilder
import com.kuryelog.logic.map.day.StageFeed
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.NavFix
import com.kuryelog.logic.map.nav.NavShift
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Gün motorunun Android çalışma zamanı (MAP.md 11.14, 11.17): `NavLocation` fix'leri → [DayController.onFix]; `ShiftPort.state` (→ vardiyayı izleyen gün) ve 5 sn tık;
 * `OrderEvents.stages` ([bindOrders]) → aşama izleyici (+ 2 sn sonra ikinci okuma); `map` tercihleri (`shift.v1`, `shift.last`, `courier.follow.v1`, `lights.v1`,
 * `sidewalk.zones.v1`, `day.shift.handled.v1`) denetleyicinin içinde. Sürüş günlüğü, gün raporu ve görev sinyalleri A6.9: [onStep] ile bağlanır.
 */
class DayRuntime private constructor(private val app: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val core = app.getSharedPreferences("kuryelog", Context.MODE_PRIVATE)
    private val lang: () -> Lang = { Lang.resolve(core.getString(Lang.PREF_KEY, null), Locale.getDefault().language) }
    private val settings = MapSettingsRepo.settings(app)
    private val lock = Any()
    private val clock = Clock.Jvm
    private val controller = DayController(MapPrefsStore(app), lang)
    private var lastPoint: GeoPoint? = null
    private var stagesJob: Job? = null
    private var recheck: Job? = null
    @Volatile private var events: OrderEvents = OrderEvents.NONE

    private val viewFlow = MutableStateFlow(controller.view)
    val view: StateFlow<DayView> get() = viewFlow

    private val stepFlow = MutableSharedFlow<DayStep>(extraBufferCapacity = 16)

    /** Her girdinin sonucu (toast ve titreşim için ekran; sürüş günlüğü A6.9). Toast yalnız Harita sekmesi görünürken gösterilir. */
    val steps: SharedFlow<DayStep> get() = stepFlow.asSharedFlow()

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(DayStep) -> Unit>()

    /** A6.9: sürüş günlüğü, gün raporu, görev ve hatırlatıcı sinyalleri bu kancaya bağlanır (olaylar, gün durumu); her adımda sırayla çağrılır. */
    fun addStepListener(l: (DayStep) -> Unit) { listeners += l }

    /** Navigasyonun hedef bağı (`NavController.shift`): hedef restoran/müşteri olarak saklanır; eve dönüş ölçümü başlar. */
    val navShift: NavShift = object : NavShift {
        override fun setDestination(point: GeoPoint, restaurant: Boolean, name: String) {
            publish(synchronized(lock) { controller.setDestination(point, clock.wallMs(), restaurant, name.ifBlank { null }) })
        }

        override fun startHomeReturn() {
            publish(synchronized(lock) { controller.startHomeReturn(clock.wallMs()) })
        }
    }

    init {
        controller.roads = RoadInfoBuilder(
            pois = { MapPois.base(app) },
            graph = { MapData.controller(app).state.value.graph },
            lights = controller.lights,
            sidewalks = controller.sidewalks,
            lightMaxM = { settings.value.lightMaxM },
        )::at
        scope.launch { ShiftHub.state.collect { s -> publish(synchronized(lock) { controller.onShift(s, clock.wallMs()) }) } }
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                publish(
                    synchronized(lock) {
                        controller.home = Home.point(app)?.let { GeoPoint(it.lat, it.lon) }
                        controller.onShift(ShiftHub.state.value, clock.wallMs())
                        controller.onTick(clock.wallMs())
                    },
                )
                readStages()
            }
        }
    }

    /** Konum akışı (`NavRuntime`): motor + ışık ölçer (fix yaşı −5…+30 sn dışındaysa yok sayılır). */
    fun onFix(f: NavFix) {
        val step = synchronized(lock) {
            lastPoint = GeoPoint(f.lat, f.lon)
            controller.onFix(DayFix(f.atMs, f.lat, f.lon, f.speedMps ?: -1.0, f.accuracyM), clock.wallMs())
        }
        publish(step)
    }

    /** Sipariş aşamaları kaynağı (`:orders`); bağlanmadıysa `NONE` (okuma yok: hiçbir şey değişmez). */
    fun bindOrders(source: OrderEvents) {
        events = source
        stagesJob?.cancel()
        stagesJob = scope.launch { source.stages.collect { readStages() } }
    }

    private fun readStages() {
        val feed = StageFeed.from(events.stages.value.takeIf { events !== OrderEvents.NONE })
        val step = synchronized(lock) { controller.onStages(feed, clock.wallMs(), lastPoint) }
        publish(step)
        if (synchronized(lock) { controller.awaitingSecondRead }) {
            recheck?.cancel()
            recheck = scope.launch {
                delay(RECHECK_MS)
                val feed2 = StageFeed.from(events.stages.value.takeIf { events !== OrderEvents.NONE })
                publish(synchronized(lock) { controller.onStages(feed2, clock.wallMs(), lastPoint) })
            }
        }
    }

    /** Elle ışık işareti (uzun basış 16/17); gün kapalıyken de çalışır. */
    fun markLight(lat: Double, lon: Double, exists: Boolean) = publish(synchronized(lock) { controller.markLight(lat, lon, exists) })

    /** Elle kaldırım işareti (uzun basış 5/6). */
    fun markSidewalk(point: GeoPoint, remove: Boolean) = publish(synchronized(lock) { controller.markSidewalk(point, remove, clock.wallMs()) })

    /** Günü elle bitir ("Günü bitir"): vardiya sürer, gün bu vardiyada yeniden açılmaz. */
    fun endDay() = publish(synchronized(lock) { controller.endDay(clock.wallMs(), "manual") })

    /** Gün sonu ışık cevabı (İstatistik, A6.9): duruş [stopStartMs] ile bulunur. */
    fun reviewLight(stopStartMs: Long, isLight: Boolean) = publish(synchronized(lock) { controller.reviewLight(stopStartMs, isLight, clock.wallMs()) })

    /** Elle işaretlenmiş ışık yerleri (harita katmanı). */
    fun lightSpots() = synchronized(lock) { controller.lights.spots }

    /** Biten son gün (`shift.last`); gün raporu ve İstatistik geçmişi için. */
    fun lastDay() = synchronized(lock) { controller.lastDay }

    /** Süren gün durumunun anlık kopyası (İstatistik, gün raporu); gün kapalıysa null. */
    fun activeDay(): DayState? = synchronized(lock) { controller.view.state.takeIf { it.active }?.let { DayState.fromJson(it.toJson()) } }

    /** Onaylanmış ışık sayısı (`LightsDb.confirmed`; gün raporunda `lightsLearned`). */
    fun lightsConfirmed() = synchronized(lock) { controller.lights.confirmed }

    /** Gün silme (MAP.md 12.9): son gün o tarihteyse unutulur. */
    fun forgetLastDay() = synchronized(lock) { controller.forgetLastDay() }

    private fun publish(step: DayStep) {
        viewFlow.value = step.view
        if (step.events.isNotEmpty() || step.toasts.isNotEmpty()) stepFlow.tryEmit(step)
        for (l in listeners) runCatching { l(step) }
    }

    companion object {
        private const val TICK_MS = 5_000L
        private const val RECHECK_MS = 2_000L

        @Volatile private var instance: DayRuntime? = null

        fun get(ctx: Context): DayRuntime = instance ?: synchronized(this) { instance ?: DayRuntime(ctx.applicationContext).also { instance = it } }
    }
}
