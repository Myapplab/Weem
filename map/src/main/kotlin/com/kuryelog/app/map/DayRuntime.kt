package com.kuryelog.app.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.kuryelog.app.core.Home
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.OrderEvents
import com.kuryelog.logic.core.TripStages
import com.kuryelog.logic.map.day.DayController
import com.kuryelog.logic.map.day.DayEvent
import com.kuryelog.logic.map.day.DayFix
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.DayStep
import com.kuryelog.logic.map.day.DayToast
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.day.StageFeed
import com.kuryelog.logic.map.geo.GeoPoint
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Gün motorunun Android tarafı (MAP.md 11.14, 11.17; YolGo `ShiftController`): tek [DayController] — her girdi ana iş parçacığında sırayla çalışır (denetleyici ve
 * `DayState` iş parçacığı güvenli değildir; ekran aynı nesneyi okur).
 *
 * Girdiler: `ShiftHub.state` (gün = vardiya, 11.2) → `onShift`; konum (vardiya sürerken GPS 1 Hz) → `onFix`; sipariş aşamaları ([bindOrders]; `OrderEvents.stages`,
 * her değişimde, 5 sn tıkta ve eksik okumadan 2 sn sonra) → `onStages`; harita verisi → yol bilgisi; `map` ayarları (`lightMaxM`); ev (`:core` `Home`).
 * Çıktılar: [view] (gün kartı, ışık sayacı), [toasts] (şerit yalnız Harita sekmesi görünürken toplar; kaçan toast sonradan gösterilmez), [events]
 * (sürüş günlüğü A6.9), [dayEnded] (gün raporu A6.9). A6.6'nın `NavLocation`'ı gelince fix beslemesi oraya taşınır ([onFix] açık).
 */
object DayRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private var app: Context? = null
    private var controllerInstance: DayController? = null
    private var orders: OrderEvents? = null
    private var lastPoint: GeoPoint? = null
    private var recheck = false
    private var gps: LocationListener? = null

    private val viewFlow = MutableStateFlow<DayView?>(null)
    private val toastFlow = MutableSharedFlow<DayToast>(extraBufferCapacity = 16)
    private val eventFlow = MutableSharedFlow<DayEvent>(extraBufferCapacity = 64)
    private val endedFlow = MutableSharedFlow<DayState>(extraBufferCapacity = 4)

    /** Güncel gün görünümü (null: henüz başlamadı). */
    val view: StateFlow<DayView?> = viewFlow.asStateFlow()

    /** Toast şeridi isteği; ses/titreşim şiddeti [DayToast.haptic]. */
    val toasts: SharedFlow<DayToast> = toastFlow.asSharedFlow()

    /** Her adımın olayları (`shift_<tür>` günlük satırları A6.9'da; `light_answer` ve `stop_surface_override` önek almaz). */
    val events: SharedFlow<DayEvent> = eventFlow.asSharedFlow()

    /** Bir gün kapandı (`shift.last` yazıldı): gün raporu A6.9'da bu durumdan yazılır. */
    val dayEnded: SharedFlow<DayState> = endedFlow.asSharedFlow()

    /** Tek örnek; süreç başında [start] çağrılmış olmalı. */
    fun controller(context: Context): DayController =
        controllerInstance ?: synchronized(this) { controllerInstance ?: create(context.applicationContext).also { controllerInstance = it } }

    private fun create(ctx: Context): DayController {
        val core = ctx.getSharedPreferences("kuryelog", Context.MODE_PRIVATE)
        return DayController(MapPrefsStore(ctx)) { Lang.resolve(core.getString(Lang.PREF_KEY, null), Locale.getDefault().language) }
    }

    /** Vardiya, harita verisi ve ayarları dinler; ikinci çağrı etkisizdir. `WeemApplication.onCreate`'ten çağrılır. */
    fun start(context: Context) {
        val ctx = context.applicationContext
        synchronized(this) { if (started) return; started = true; app = ctx }
        val c = controller(ctx)
        viewFlow.value = c.view
        scope.launch {
            ShiftHub.state.collect { s ->
                publish(c.onShift(s, System.currentTimeMillis()), c)
                syncGps(ctx, c.view.state.active)
            }
        }
        scope.launch { MapData.controller(ctx).state.collect { d -> c.bindMapData(d.pois, d.graph) } }
        scope.launch { MapSettingsRepo.settings(ctx).collect { s -> c.lightMaxM = s.lightMaxM } }
        scope.launch { tick(c, ctx) }
    }

    /** `OrderEvents.stages` bağlanır (tek telefonda `WeemApplication`, `Orders.bind`'dan sonra). */
    fun bindOrders(events: OrderEvents) {
        orders = events
        val ctx = app ?: return
        scope.launch { events.stages.collect { readStages(controller(ctx)) } }
    }

    private fun readStages(c: DayController) {
        val ev = orders ?: return
        val step = c.onStages(StageFeed.from(ev.stages.value), System.currentTimeMillis(), lastPoint)
        publish(step, c)
        if (c.awaitingSecondRead && !recheck) {
            recheck = true
            scope.launch { delay(2_000); recheck = false; readStages(c) }
        }
    }

    /** 5 sn tık (yalnız gün açıkken): kalıcılık, görünüm, aşama okuması (yedek), ev konumu; GPS dinleyicisi gün açılıp kapanınca bağlanır. */
    private suspend fun tick(c: DayController, ctx: Context) {
        while (true) {
            delay(5_000)
            val active = c.view.state.active
            syncGps(ctx, active)
            if (!active) continue
            c.home = Home.point(ctx)?.let { GeoPoint(it.lat, it.lon) }
            publish(c.onTick(System.currentTimeMillis()), c)
            readStages(c)
        }
    }

    // ---------------------------------------------------------------- Konum

    @SuppressLint("MissingPermission")
    private fun syncGps(ctx: Context, active: Boolean) {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        if (!active) {
            gps?.let { runCatching { lm.removeUpdates(it) } }
            gps = null
            return
        }
        if (gps != null || ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val l = LocationListener { loc -> onLocation(loc) }
        gps = l
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, l, Looper.getMainLooper()) }.onFailure { gps = null }
    }

    private fun onLocation(loc: Location) {
        val ctx = app ?: return
        lastPoint = GeoPoint(loc.latitude, loc.longitude)
        onFix(controller(ctx), loc.toDayFix())
    }

    /** Bir konum okuması (A6.6'nın `NavLocation`'ı da buraya besler). */
    fun onFix(c: DayController, fix: DayFix) {
        publish(c.onFix(fix, System.currentTimeMillis()), c)
    }

    private fun Location.toDayFix() = DayFix(
        atMs = time, lat = latitude, lon = longitude,
        speedMps = if (hasSpeed()) speed.toDouble() else -1.0,
        accuracy = if (hasAccuracy()) accuracy.toDouble() else Double.POSITIVE_INFINITY,
    )

    // ---------------------------------------------------------------- Komutlar

    /** Elle ışık var/yok işareti (uzun basış 16–17). */
    fun markLight(lat: Double, lon: Double, exists: Boolean) = command { it.markLight(lat, lon, exists, now()) }

    /** Elle kaldırım işareti ya da kaldırma (uzun basış 5–6). */
    fun markSidewalk(point: GeoPoint, remove: Boolean) = command { it.markSidewalk(point, remove, now()) }

    /** Navigasyonun hedef bağı (A6.6; MAP.md 7.3). */
    fun setDestination(point: GeoPoint, restaurant: Boolean, name: String? = null) = command { it.setDestination(point, now(), restaurant, name) }

    /** "Eve dön" (A6.6, 7.3) eve dönüş ölçümünü başlatır. */
    fun startHomeReturn() = command { it.startHomeReturn(now()) }

    /** Gün sonu ışık cevabı: [day] biten günün durumu (gün raporundan; silinmiş gün için çağrılmaz). */
    fun reviewLight(day: DayState, stopStartMs: Long, isLight: Boolean) = command { it.reviewLight(day, stopStartMs, isLight, now()) }

    private fun command(block: (DayController) -> DayStep) {
        val ctx = app ?: return
        val c = controller(ctx)
        scope.launch { publish(block(c), c) }
    }

    private fun now() = System.currentTimeMillis()

    private fun publish(step: DayStep, c: DayController) {
        viewFlow.value = step.view
        for (t in step.toasts) toastFlow.tryEmit(t)
        for (e in step.events) eventFlow.tryEmit(e)
        if (step.events.any { it.type == "day_end" }) c.lastDay?.let { endedFlow.tryEmit(it) }
    }

    /** Işık katmanı için öğrenilmiş ışık yerleri (harita işaretleri). */
    fun lightSpots(context: Context) = controller(context).lightSpots

    /** Şu anki sipariş aşamaları (`OrderEvents` bağlıysa). */
    internal fun stages(): TripStages? = orders?.stages?.value
}
