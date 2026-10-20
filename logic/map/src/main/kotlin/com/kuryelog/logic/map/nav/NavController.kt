package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.Bilingual
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.NavOrigin
import com.kuryelog.logic.core.RouteStatus
import com.kuryelog.logic.map.geo.Baku
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.input.AddressParser
import com.kuryelog.logic.map.input.DestinationResolver
import com.kuryelog.logic.map.input.GeoConfidence
import com.kuryelog.logic.map.input.LinkUri
import com.kuryelog.logic.map.input.ParsedDestination
import com.kuryelog.logic.map.input.ResolveException
import com.kuryelog.logic.map.route.BikewayMarks
import com.kuryelog.logic.map.route.LearnedQuality
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.PlanRequest
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.route.RouteEngine
import com.kuryelog.logic.map.route.RouteMsg
import com.kuryelog.logic.map.route.RouteTimeout
import com.kuryelog.logic.map.route.StreetPrefs
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.surface.SurfaceMark
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Navigasyonun durumu (MAP.md 7.2, 7.16): [seq] her yeni rotada +1; [arrived] son varılan durak (kapı notu kartı, 90 sn);
 * [reached] varılan hedefin iğnesi (kart kapansa da kalır, [REACHED_KEEP_M] ötesine geçince kalkar); [pending] bekleyen yeni adres kartı;
 * [suggestion] ters yön önerisi ("onay için Ses+", 15 sn).
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
    val suggestion: WrongWayEvent? = null,
) {
    val hasRoute: Boolean get() = plans.isNotEmpty()

    /** Seçili moddaki plan; yoksa ilk plan. */
    val plan: Plan? get() = plans[mode] ?: plans.values.firstOrNull()

    /** Etkin (ilk) durak: planın sırasına göre; plan yoksa ilk durak. */
    val activeStop: Stop?
        get() {
            val p = plan
            if (p == null || p.order.isEmpty() || stops.isEmpty()) return stops.firstOrNull()
            return stops.getOrNull(p.order.first()) ?: stops.first()
        }

    companion object {
        const val REACHED_KEEP_M = 250.0
        const val ARRIVED_CARD_MS = 90_000L
        const val SUGGESTION_MS = 15_000L
    }
}

/** Bildirim şeridinin tonu ve simgesi (`toast.dart`; renkler `:app`'te). */
enum class ToastTone { INFO, WARN, DANGER, WALK }

enum class ToastIcon { NONE, LINK_OFF, WARNING, ALT_ROUTE, SWAP, HOME, BATTERY, WALK }

/** Tek bildirim (yenisi eskisinin yerine geçer); [id] her bildirimde artar. */
data class NavToast(val id: Long, val text: String, val tone: ToastTone = ToastTone.INFO, val icon: ToastIcon = ToastIcon.NONE, val seconds: Int = 4)

enum class NavHaptic { SELECTION, HEAVY, VIBRATE }

/** Rota başlangıcı alınamadı (`LocationUnavailable`). */
class LocationUnavailable(val text: Bilingual) : Exception(text.tr)

/** Android konum kaynağı (`:map` `NavLocation`): izin, tek seferlik yüksek doğruluklu konum (≤8 sn), Android'in bilinen son konumu. */
interface NavLocator {
    suspend fun ensurePermission(): Boolean
    suspend fun currentFix(): NavFix?
    suspend fun lastKnown(): NavFix?
}

/** Gün motoru bağı (A6.8 `DayController`): hedefin restoran/müşteri olarak saklanması ve eve dönüş ölçümü (MAP.md 7.3, 11.13). */
interface NavShift {
    fun setDestination(point: GeoPoint, restaurant: Boolean, name: String)
    fun startHomeReturn()
}

/** Rota kaydı bağı (A6.9 `RouteRecorder`, MAP.md 12.5): yeni rota/yeniden hesap, her fix (rotaya uzaklık), bitiş nedeni. */
interface NavRecording {
    fun planned(reason: ReplanReason, plan: Plan, atMs: Long)
    fun fix(fix: NavFix, offRouteM: Double?)
    fun finish(reason: String, atMs: Long)
}

/** Rota maliyetine giren öğrenilmiş yol bilgisi (Y12, A6.11; MAP.md 9.9): değişince kendiliğinden yeniden hesap olmaz (YolGo gibi). */
data class RoadInputs(
    val ghost: Set<Int> = emptySet(),
    val learned: Map<Long, LearnedQuality> = emptyMap(),
    val marks: List<SurfaceMark> = emptyList(),
    val wwRatio: Map<Long, Double> = emptyMap(),
    val wwAvoid: Set<Long> = emptySet(),
    val wrongWayEdges: Set<Int> = emptySet(),
)

/**
 * Saf navigasyon durum makinesi (`nav_controller.dart`; MAP.md 7, 8.7). Girdiler: hedefler ([goTo], [addStop], [onExternalText], [search], [openTarget]),
 * fix'ler ([onFix]), ayar/sokak/öğrenme ([setSettings], [setStreetPrefs], [setBikewayMarks], [setRoadInputs]), batarya ([onBattery]), vardiya ([setOrder]);
 * saat [tick] ile ilerler (90 sn varış kartı, 15 sn ters yön önerisi, 5 sn saatli izin denetimi). Çıktılar: [state], canlı ölçüm [progress],
 * yayın kapısından geçen [routeStatus] (`NavPort.route`), bildirim [toast], titreşim [haptics], sürüş günlüğü [log].
 */
class NavController(
    private val engine: RouteEngine,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.Jvm,
    private val resolver: DestinationResolver? = null,
    private val lang: () -> Lang = { Lang.TR },
    private val locator: NavLocator? = null,
) {
    private val lock = Any()
    private val stateFlow = MutableStateFlow(NavState())
    private val progressFlow = MutableStateFlow<RouteMeasure?>(null)
    private val statusFlow = MutableStateFlow<RouteStatus?>(null)
    private val toastFlow = MutableStateFlow<NavToast?>(null)
    private val hapticFlow = MutableSharedFlow<NavHaptic>(extraBufferCapacity = 8)

    val state: StateFlow<NavState> get() = stateFlow

    /** Etkin bacağın canlı ölçümü (göstergeler ve yayın bunu kullanır; her fix'te tazelenir). */
    val progress: StateFlow<RouteMeasure?> get() = progressFlow

    /** `NavFeedGate` kuralıyla güncellenen yayın (`NavPort.route`); açılışta hedefsiz bir kez. */
    val routeStatus: StateFlow<RouteStatus?> get() = statusFlow

    val toast: StateFlow<NavToast?> get() = toastFlow
    val haptics: SharedFlow<NavHaptic> get() = hapticFlow

    /** Sürüş günlüğü (Y18, MAP.md 7.14, 8.10): olay adı + alanlar; adres ve koordinat yazılmaz (yalnız `to`/`arrive` 4 hane). */
    @Volatile
    var log: (String, Map<String, Any?>) -> Unit = { _, _ -> }

    @Volatile
    var shift: NavShift? = null

    @Volatile
    var recording: NavRecording? = null

    /** Hedefin 8 m içinde restoran/fast food/kafe noktası var mı (Y16 POI, A6.8); yoksa null. */
    @Volatile
    var restaurantNear: ((GeoPoint) -> Boolean)? = null

    /** Her fix ve oturduğu kenar (Y12 öğrenme, A6.11). Kilit dışında çağrılır. */
    @Volatile
    var onMatchedFix: ((NavFix, MatchInfo?) -> Unit)? = null

    private var graph: RoadGraph? = null
    private var settings = MapSettings()
    private var bikeways = BikewayMarks()
    private var prefs = StreetPrefs()
    private var road = RoadInputs()
    private var battery: Double? = null
    private var dayActive = false
    private var currentOrder: Int? = null
    private var manualOrigin: GeoPoint? = null
    private var lastFix: NavFix? = null
    private var planSeq = 0
    private val reroute = RerouteGate()
    private var lastWarnKey: String? = null
    private var lowBatteryNotified = false
    private var matcher: MapMatcher? = null
    private var arrivedAtMs = 0L
    private var suggestionAtMs = 0L
    private var hoursAllowed: Set<Long> = emptySet()
    private var hoursCheckedAtMs = 0L
    private var toastSeq = 0L
    private val tracker = RouteProgressTracker()
    private val gate = RouteStatusGate()
    private var seenStop: Stop? = null
    private var seenAtMs = clock.wallMs()
    private var updatedAtMs = clock.wallMs()

    init {
        publish()
    }

    /** Son taze konum (≤30 sn, doğruluk ≤65 m): arama yakınlığı, geokod yönü. */
    fun here(): GeoPoint? = synchronized(lock) { lastPoint() }

    /** Son oturtulan kenar (uzun basış, Ses± sokak kararları). */
    val match: MatchInfo? get() = synchronized(lock) { matcher?.current }

    // ---------------------------------------------------------------- Girdiler

    fun setGraph(graph: RoadGraph?) {
        val replan = synchronized(lock) {
            val was = this.graph
            this.graph = graph
            if (matcher?.graph !== graph) matcher = null
            // Harita verisi sonradan hazır olursa (ör. soğuk açılışta gelen bağlantı) rotayı çiz.
            if (was == null && graph != null && stateFlow.value.stops.isNotEmpty()) {
                bindShiftTarget(stateFlow.value.stops.first(), null)
                true
            } else {
                false
            }
        }
        if (replan) replan(ReplanReason.ROUTE)
    }

    /** Konum yokken rota başlangıcı (testte sahte konum; uygulamada kullanılmaz). */
    fun setOrigin(p: GeoPoint) = synchronized(lock) { manualOrigin = p }

    /** Kalite toleransı, ters yön eşiği, dönüş cezası ya da bisiklet yolu ağırlığı değişince açık rota yeniden hesaplanır. */
    fun setSettings(s: MapSettings) {
        val replan = synchronized(lock) {
            val old = settings
            settings = s
            val changed = old.qualityTolerancePct != s.qualityTolerancePct || old.wwMaxProblemPct != s.wwMaxProblemPct ||
                old.turnPenaltyS != s.turnPenaltyS || old.bikewayCostPct != s.bikewayCostPct
            changed && stateFlow.value.hasRoute
        }
        if (replan) replan(ReplanReason.PREFS)
    }

    /** Elle bisiklet yolu işareti (E13) değişince açık rota yeniden hesaplanır. */
    fun setBikewayMarks(m: BikewayMarks) {
        val replan = synchronized(lock) {
            val changed = m.ways != bikeways.ways
            bikeways = m
            changed && stateFlow.value.hasRoute
        }
        if (replan) replan(ReplanReason.PREFS)
    }

    /** Sokak kararları (Y8): onaylı sokak değişince açık rota yeniden hesaplanır; engel/eğim kararını veren yer [requestReplan] çağırır. */
    fun setStreetPrefs(p: StreetPrefs) {
        val replan = synchronized(lock) {
            val changed = p.approved != prefs.approved
            prefs = p
            hoursAllowed = allowedHours(clock.wallMs())
            changed && stateFlow.value.hasRoute
        }
        if (replan) replan(ReplanReason.PREFS)
    }

    /**
     * Öğrenilmiş yol bilgisi; "yol yok" kümesi değişince ya da [replan] (elle puan, ww sayacı, kötü nokta, "Sorun yok" onayı; MAP.md 9.3.3)
     * istenince açık rota yeniden hesaplanır. Otomatik öğrenme (geçiş sayaçları) yalnız sonraki plana girer.
     */
    fun setRoadInputs(r: RoadInputs, replan: Boolean = false) {
        val again = synchronized(lock) {
            val changed = replan || r.ghost != road.ghost || r.wrongWayEdges != road.wrongWayEdges
            road = r
            changed && stateFlow.value.hasRoute
        }
        if (again) replan(ReplanReason.PREFS)
    }

    /** Ters yön önerisi Ses+ ile onaylandı (MAP.md 9.5.2): öneri düşer. */
    fun clearSuggestion() = synchronized(lock) { if (stateFlow.value.suggestion != null) set(stateFlow.value.copy(suggestion = null)) }

    /** Açık rota varsa yeniden hesapla (`replan('prefs')`; Ses± sokak kararları, kalite kartı). */
    fun replanIfRoute() {
        if (stateFlow.value.hasRoute) replan(ReplanReason.PREFS)
    }

    /** Gün açık mı ve açık siparişin numarası (hedef bağı, MAP.md 7.3 `_bindShiftTarget`). */
    fun setOrder(dayActive: Boolean, currentOrder: Int?) = synchronized(lock) {
        if (dayActive && !this.dayActive) {
            hoursAllowed = allowedHours(clock.wallMs())
            hoursCheckedAtMs = clock.wallMs()
        }
        this.dayActive = dayActive
        this.currentOrder = currentOrder
    }

    /**
     * Seçili sipariş değişti (MAP.md 7.3 "Sipariş değişimi"): hedef yoksa rota silinir ve "hedef konumu gir"; varsa restorandan çıkılmadıysa restorana,
     * çıkıldıysa müşteriye rota (`origin = ORDER`).
     */
    fun onOrderSwitch(order: Int?, target: GeoPoint?, toRestaurant: Boolean, restaurantName: String?) {
        if (target == null) {
            clearRoute()
            if (order != null) showToast(tl("Sipariş #$order seçildi · hedef konumu gir", "Sifariş #$order seçildi · ünvanı daxil et"), seconds = 6)
            return
        }
        goTo(target, if (toRestaurant) restaurantName else null, toRestaurant, NavOrigin.ORDER)
    }

    /** Batarya yüzdesi (Y9, MAP.md 7.8): eşik altına inişte bir kez uyarı ve yeniden hesap; eve dönüş önerisi. */
    fun onBattery(pct: Double?) {
        if (pct == null) return
        var replan = false
        synchronized(lock) {
            val prev = battery
            battery = pct
            val s = settings
            if (prev != null && prev >= s.lowBatteryPct && pct < s.lowBatteryPct && !lowBatteryNotified) {
                lowBatteryNotified = true
                showToast(
                    tl("Batarya ~%${pct.roundToInt()} — eğim limiti %${s.lowBatteryGradePct.roundToInt()}", "Batareya ~%${pct.roundToInt()} — yoxuş limiti %${s.lowBatteryGradePct.roundToInt()}"),
                    ToastTone.WARN, ToastIcon.BATTERY, 6,
                )
                replan = stateFlow.value.hasRoute
            }
            if (pct >= s.lowBatteryPct) lowBatteryNotified = false
            val st = stateFlow.value
            if (pct <= s.homeReturnPct && !st.homeSuggest && st.stops.isEmpty()) set(st.copy(homeSuggest = true))
        }
        if (replan) replan(ReplanReason.BATTERY)
    }

    // ---------------------------------------------------------------- Hedefler

    /** Hedefe rota ister; tek durak (`label` yoksa "Harita noktası"), `arrived`/`reached`/`pending` silinir. */
    fun goTo(target: GeoPoint, label: String? = null, restaurant: Boolean? = null, origin: NavOrigin = NavOrigin.MAP, purpose: StopPurpose = StopPurpose.MANUAL) {
        synchronized(lock) {
            val stop = Stop(target, label ?: NavText.MAP_POINT.get(lang()), restaurant = restaurant, purpose = purpose, origin = origin, requestedAtMs = clock.wallMs())
            set(stateFlow.value.copy(stops = listOf(stop), arrived = null, reached = null, pending = null))
            bindShiftTarget(stop, restaurant)
        }
        replan(ReplanReason.ROUTE)
    }

    /**
     * Başka modülden ya da öteki telefondan gelen hedef (`NavPort.open`, MAP.md 8.12): [goTo] + Stop alanları; konuma 60 m'den yakın ve restoran değilse
     * rota çizilmez (8.7), durak varken bekleyen kart.
     */
    fun openTarget(stop: Stop) {
        synchronized(lock) {
            val me = lastPoint()
            val dm = me?.distanceTo(stop.point)
            if (dm != null && dm < NEAR_M && !restaurantTarget(stop, null)) {
                set(stateFlow.value.copy(busy = false, busyText = null, error = nearError(dm, stop.label)))
                haptic(NavHaptic.VIBRATE)
                return
            }
            val st = stateFlow.value
            if (st.stops.isNotEmpty()) {
                set(st.copy(pending = stop))
                return
            }
            set(st.copy(stops = listOf(stop), arrived = null, reached = null, pending = null))
            bindShiftTarget(stop, stop.restaurant)
        }
        replan(ReplanReason.ROUTE)
    }

    /** "Durak olarak ekle": durak sona eklenir, `arrived` silinir (`reached` kalır). */
    fun addStop(p: GeoPoint, label: String? = null) {
        synchronized(lock) {
            val st = stateFlow.value
            set(st.copy(stops = st.stops + Stop(p, label ?: NavText.MAP_POINT.get(lang()), requestedAtMs = clock.wallMs()), arrived = null))
        }
        replan(ReplanReason.ROUTE)
    }

    /** Rota varken gelen yeni adres: [addStop] true → çifte sipariş (iğne kalır), false → yeni rota. */
    fun acceptPending(addStop: Boolean) {
        synchronized(lock) {
            val st = stateFlow.value
            val p = st.pending ?: return
            set(st.copy(stops = if (addStop) st.stops + p else listOf(p), pending = null, arrived = null, reached = if (addStop) st.reached else null))
            bindShiftTarget(p, null)
        }
        replan(ReplanReason.ROUTE)
    }

    fun dismissPending() = synchronized(lock) { set(stateFlow.value.copy(pending = null)) }

    /** Eve dön (MAP.md 7.3): yalnız gün açıkken; ev yoksa uyarı; eve dönüş ölçümü başlar. */
    fun goHome(home: GeoPoint?) {
        synchronized(lock) {
            if (!dayActive) return
            if (home == null) {
                showToast(com.kuryelog.logic.map.ui.MapText.HOME_MISSING.get(lang()), ToastTone.WARN, ToastIcon.HOME, 6)
                return
            }
            set(stateFlow.value.copy(homeSuggest = false))
            shift?.startHomeReturn()
            // Eve dönüş müşteri adresini ya da sipariş adımını değiştirmez.
            val stop = Stop(home, NavText.HOME.get(lang()), restaurant = false, purpose = StopPurpose.HOME, requestedAtMs = clock.wallMs())
            set(stateFlow.value.copy(stops = listOf(stop), arrived = null, reached = null, pending = null))
        }
        replan(ReplanReason.ROUTE)
    }

    fun dismissHomeSuggest() = synchronized(lock) { set(stateFlow.value.copy(homeSuggest = false)) }

    fun clearRoute() = synchronized(lock) {
        recording?.finish("clear", clock.wallMs())
        log("route_clear", emptyMap())
        planSeq++
        set(stateFlow.value.copy(stops = emptyList(), plans = emptyMap(), busy = false, busyText = null, error = null, pending = null, reached = null))
    }

    fun setMode(m: RideMode) = synchronized(lock) {
        if (m == stateFlow.value.mode) return
        set(stateFlow.value.copy(mode = m))
        log("mode", mapOf("m" to m.name.lowercase()))
        haptic(NavHaptic.SELECTION)
    }

    /** Varış kartını kapatır; iğne (`reached`) kalır. */
    fun dismissArrived() = synchronized(lock) { set(stateFlow.value.copy(arrived = null)) }

    /** Hata satırındaki "Tekrar" ve sokak/öğrenme kararları (Ses±, uzun basış: `PREFS`). */
    fun requestReplan(reason: ReplanReason = ReplanReason.ROUTE) {
        replan(reason)
    }

    /** Dış metin (bağlantı, paylaşım, pano): ayrıştır, çöz, <60 m kuralı, durak yoksa rota, varsa bekleyen kart (MAP.md 8.7). */
    /**
     * 2 telefon, Provider (LINK.md 4.5): çözülen dış hedef (kurye uygulaması, pano) öteki telefona gider; `true` = gitti, bu telefonda
     * rota açılmaz. Null ya da `false`: olağan akış (Receiver'a ulaşılamazsa rota burada açılır).
     */
    @Volatile var forward: ((Stop) -> Boolean)? = null

    suspend fun onExternalText(text: String, source: String, origin: NavOrigin? = null, referrer: String? = null, requestedAtMs: Long? = null) {
        val asked = requestedAtMs ?: clock.wallMs()
        val uri = LinkUri.tryParse(text.trim())
        if (source == "intent") log("intent", mapOf("scheme" to (uri?.scheme ?: ""), "host" to (uri?.host ?: ""))) // adres yazılmaz
        val parsed = AddressParser.parse(text)
        if (parsed == null) {
            if (source != "clipboard") {
                val scheme = uri?.scheme ?: ""
                showToast(NavText.NO_LINK_LOCATION.get(lang()) + if (scheme.isEmpty()) "" else " ($scheme)", ToastTone.WARN, ToastIcon.LINK_OFF)
            }
            return
        }
        log("external", mapOf("src" to source, "kind" to kindOf(parsed)))
        haptic(NavHaptic.SELECTION)
        val me = synchronized(lock) {
            set(stateFlow.value.copy(busy = true, busyText = NavText.RESOLVING.get(lang()), error = null))
            lastPoint()
        }
        try {
            val r = (resolver ?: throw IllegalStateException("resolver")).resolve(parsed, me ?: Baku.center)
            val dm = me?.distanceTo(r.point)
            log("resolved", buildMap {
                put("src", source)
                put("by", r.source)
                put("conf", r.confidence.name.lowercase())
                if (dm != null) put("dm", dm.roundToInt())
            })
            val stop = Stop(
                r.point, r.label, r.unitNote, confidenceOf(r.confidence), r.restaurant,
                origin = origin ?: if (source == "clipboard") NavOrigin.CLIPBOARD else NavOrigin.INTENT, requestedAtMs = asked, sourcePackage = referrer,
            )
            val near = dm != null && dm < NEAR_M && !restaurantTarget(stop, null)
            if (!near && forward?.invoke(stop) == true) {
                synchronized(lock) { set(stateFlow.value.copy(busy = false, busyText = null)) }
                showToast(NavText.FORWARDED.get(lang()), ToastTone.INFO, ToastIcon.SWAP)
                return
            }
            val replan = synchronized(lock) {
                // Restoran önündeyken gelen gerçek restoran hedefi hatalı adres sayılmaz.
                if (near) {
                    set(stateFlow.value.copy(busy = false, busyText = null, error = nearError(dm, text)))
                    haptic(NavHaptic.VIBRATE)
                    false
                } else if (stateFlow.value.stops.isEmpty()) {
                    set(stateFlow.value.copy(stops = listOf(stop), busy = false, busyText = null, arrived = null, reached = null))
                    bindShiftTarget(stop, r.restaurant)
                    true
                } else {
                    set(stateFlow.value.copy(pending = stop, busy = false, busyText = null))
                    false
                }
            }
            if (replan) replan(ReplanReason.ROUTE)?.join()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(lock) { set(stateFlow.value.copy(busy = false, busyText = null, error = humanize(e))) }
            haptic(NavHaptic.VIBRATE)
        }
    }

    /** Arama çubuğu (Enter): `origin = SEARCH`, doğrudan yeni rota (bekleyen kart ve <60 m denetimi yok). */
    suspend fun search(text: String) {
        val parsed = AddressParser.parseExplicit(text.trim())
        if (parsed == null) {
            showToast(NavText.SEARCH_HINT.get(lang()))
            return
        }
        val (id, me) = synchronized(lock) {
            set(stateFlow.value.copy(busy = true, busyText = NavText.SEARCHING.get(lang()), error = null))
            ++planSeq to lastPoint()
        }
        val asked = clock.wallMs()
        try {
            val d = (resolver ?: throw IllegalStateException("resolver")).resolve(parsed, me ?: Baku.center)
            val replan = synchronized(lock) {
                if (id != planSeq) return
                val stop = Stop(d.point, d.label, d.unitNote, confidenceOf(d.confidence), d.restaurant, origin = NavOrigin.SEARCH, requestedAtMs = asked)
                set(stateFlow.value.copy(busy = false, busyText = null, stops = listOf(stop), arrived = null, reached = null, pending = null))
                bindShiftTarget(stop, null)
                true
            }
            if (replan) replan(ReplanReason.ROUTE)?.join()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(lock) { if (id == planSeq) set(stateFlow.value.copy(busy = false, busyText = null, error = humanize(e))) }
        }
    }

    // ---------------------------------------------------------------- Rota

    /** Eş zamanlı kısım kilit altında (sıra, plan silme, graf ve durak denetimi); hesap [scope]'ta. Hesap başlamadıysa null. */
    private fun replan(reason: ReplanReason): Job? {
        val id: Int
        synchronized(lock) {
            id = ++planSeq
            // Saat/yasak değiştiyse hesap başarısız olsa bile artık izinsiz rota ekranda etkin kalmasın.
            if (reason == ReplanReason.PREFS || reason == ReplanReason.HOURS) set(stateFlow.value.copy(plans = emptyMap()))
            if (graph == null) {
                set(stateFlow.value.copy(error = NavText.NO_MAP.get(lang())))
                return null
            }
            if (stateFlow.value.stops.isEmpty()) {
                set(stateFlow.value.copy(plans = emptyMap(), busy = false, busyText = null))
                return null
            }
            set(stateFlow.value.copy(busy = true, busyText = NavText.ROUTING.get(lang()), error = null))
        }
        return scope.launch { compute(id, reason) }
    }

    private suspend fun compute(id: Int, reason: ReplanReason) {
        var origin: GeoPoint? = null
        try {
            origin = currentOrigin()
            val req = synchronized(lock) {
                if (id != planSeq) return
                val g = graph ?: return
                val pos = lastFix
                val heading = pos?.headingDeg?.takeIf { (pos.speedMps ?: -1.0) >= 2 && it >= 0 }
                PlanRequest(
                    graph = g,
                    origin = origin,
                    stops = stateFlow.value.stops.map { it.point },
                    params = settings.paramsFor(battery),
                    approved = prefs.approved,
                    blocked = prefs.blocked,
                    steep = prefs.steep,
                    heading = heading,
                    ghost = road.ghost,
                    learned = road.learned,
                    marks = road.marks,
                    wwRatio = road.wwRatio,
                    wwAvoid = road.wwAvoid,
                    reverseHours = prefs.reverseHours,
                    wrongWayEdges = road.wrongWayEdges,
                    bikeways = bikeways.ways,
                    departure = Instant.ofEpochMilli(clock.wallMs()),
                )
            }
            val result = engine.compute(req)
            synchronized(lock) {
                if (id != planSeq) return
                val st = stateFlow.value
                if (result.plans.isEmpty()) {
                    val g = req.graph
                    log("routing_failure", mapOf(
                        "reason" to result.error?.tr,
                        "originInMap" to g.contains(origin.lat, origin.lon),
                        "targets" to st.stops.map { s ->
                            mapOf("inside" to g.contains(s.point.lat, s.point.lon), "nearestM" to g.nearestEdge(s.point.lat, s.point.lon, maxDist = 500.0)?.dist?.roundToInt())
                        },
                    ))
                    if (keepPlanOnFailure(reason) && st.plans.isNotEmpty()) {
                        // H3: eski rota kalır; 10 sn ya da 30 m sonra yeniden denenir.
                        reroute.failed(clock.wallMs(), origin)
                        set(st.copy(busy = false, busyText = null))
                        showToast(NavText.KEEP_OLD_ROUTE.get(lang()), ToastTone.WARN, ToastIcon.ALT_ROUTE)
                        return
                    }
                    set(st.copy(busy = false, busyText = null, plans = emptyMap(), error = (result.error ?: RouteMsg.NO_ROUTE).get(lang())))
                    haptic(NavHaptic.VIBRATE)
                    return
                }
                reroute.reset()
                set(st.copy(plans = result.plans, busy = false, busyText = null, error = null, seq = if (reason == ReplanReason.ROUTE) st.seq + 1 else st.seq))
                val now = stateFlow.value
                val p = now.plan!!
                recording?.planned(reason, p, clock.wallMs())
                log(if (reason == ReplanReason.ROUTE) "route" else "reroute", mapOf(
                    "mode" to p.mode.name.lowercase(),
                    "m" to p.rideM.roundToInt(),
                    "walk" to p.walkM.roundToInt(),
                    "s" to p.timeS.roundToInt(),
                    "wh" to Math.round(p.energyWh * 10) / 10.0,
                    "up" to p.climbM.roundToInt(),
                    "stops" to now.stops.size,
                    "to" to now.stops.map { listOf(Math.round(it.point.lat * 1e4) / 1e4, Math.round(it.point.lon * 1e4) / 1e4) },
                ))
                if (reason == ReplanReason.ROUTE) haptic(NavHaptic.HEAVY)
                val warn = p.warnings
                if (warn.isNotEmpty()) {
                    val first = now.stops.first().point
                    val key = "${first.lat},${first.lon}|${warn.first().tr}"
                    if (shouldShowRouteWarning(reason, key, lastWarnKey)) showToast(warn.first().get(lang()), ToastTone.WARN, ToastIcon.WARNING)
                    lastWarnKey = key
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(lock) {
                if (id != planSeq) return
                val st = stateFlow.value
                if (keepPlanOnFailure(reason) && st.plans.isNotEmpty()) {
                    reroute.failed(clock.wallMs(), null)
                    set(st.copy(busy = false, busyText = null))
                    return
                }
                set(st.copy(busy = false, busyText = null, error = humanize(e)))
            }
            haptic(NavHaptic.VIBRATE)
        }
    }

    /** Rota başlangıcı (`LocationService.current`, MAP.md 7.6): taze (≤20 sn) fix; yoksa tek seferlik konum; o da yoksa son kabul edilebilir konum. */
    private suspend fun currentOrigin(): GeoPoint {
        val now = clock.wallMs()
        val cached = synchronized(lock) { lastFix }
        if (fresh(cached, now, 20_000)) return GeoPoint(cached!!.lat, cached.lon)
        val loc = locator ?: return synchronized(lock) { manualOrigin } ?: RouteOrigin.routeOrigin(cached, null, now)
            ?: throw LocationUnavailable(NavText.NO_SIGNAL)
        if (!loc.ensurePermission()) throw LocationUnavailable(NavText.NO_PERMISSION)
        try {
            val p = loc.currentFix()
            if (!fresh(p, clock.wallMs(), 20_000)) throw LocationUnavailable(NavText.STALE_FIX)
            return GeoPoint(p!!.lat, p.lon)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Haritadaki nokta hâlâ görünürken "GPS sinyali alınamadı" demek yanlış: son bilinen konum yeterince yeniyse rota oradan başlar.
            val known = try { loc.lastKnown() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            return RouteOrigin.routeOrigin(synchronized(lock) { lastFix }, known, clock.wallMs()) ?: throw LocationUnavailable(NavText.NO_SIGNAL)
        }
    }

    // ---------------------------------------------------------------- Takip

    /** Her konum okumasında (MAP.md 7.7): yol eşleştirme ve ters yön, öğrenme bağı, rota kaydı, iğne, varış, sapma, canlı ölçüm. */
    fun onFix(fix: NavFix) {
        var reroute = false
        var info: MatchInfo? = null
        synchronized(lock) {
            lastFix = fix
            val g = graph
            if (g != null) {
                val m = matcher?.takeIf { it.graph === g } ?: MapMatcher(g).also { matcher = it }
                val ghost = road.ghost
                val (i, ev) = m.update(fix, prefs.approved, if (ghost.isEmpty()) null else { e -> e !in ghost }, road.wrongWayEdges)
                info = i
                if (ev != null) wrongWay(ev)
            }
            val st0 = stateFlow.value
            recording?.fix(fix, st0.plan?.legs?.firstOrNull()?.takeIf { !st0.busy }?.let { RouteLine.distanceTo(fix.lat, fix.lon, it.ride) })

            val reached = st0.reached
            if (reached != null && fix.accuracyM <= 30 && reached.point.distanceTo(GeoPoint(fix.lat, fix.lon)) > NavState.REACHED_KEEP_M) {
                set(st0.copy(reached = null))
            }
            tracker.onFix(fix)
            progressFlow.value = tracker.measure

            val st = stateFlow.value
            val plan = st.plan
            if (plan != null && plan.legs.isNotEmpty() && !st.busy && fix.accuracyM <= 30) {
                val here = GeoPoint(fix.lat, fix.lon)
                val leg = plan.legs.first()
                // Varış: kapıya geldin, ya da park noktasında durdun (yürüme kısmı telefonsuz yapılır → park noktası = varış).
                val atDoor = here.distanceTo(leg.destination) <= 35
                val atPark = leg.walk.isNotEmpty() && here.distanceTo(leg.parkPoint) <= 20 && (fix.speedMps ?: 0.0) < 2
                if (atDoor || atPark) {
                    arrive(plan)
                    if (atPark && !atDoor) {
                        val w = leg.walkM.roundToInt()
                        showToast(tl("Park et · $w m yürü", "Saxla · $w m piyada"), ToastTone.WALK, ToastIcon.WALK, 10)
                    }
                } else {
                    // Başta yaya yolu yürüyüşü varsa onun üstünde olmak sapma değildir.
                    val off = if (leg.startWalk.size >= 2) {
                        min(RouteLine.distanceTo(here.lat, here.lon, leg.ride), RouteLine.distanceTo(here.lat, here.lon, leg.startWalk))
                    } else {
                        RouteLine.distanceTo(here.lat, here.lon, leg.ride)
                    }
                    reroute = this.reroute.feed(off, fix.accuracyM, fix.atMs, clock.wallMs(), here)
                }
            }
            publish()
        }
        onMatchedFix?.invoke(fix, info)
        if (reroute) replan(ReplanReason.REROUTE)
    }

    private fun wrongWay(ev: WrongWayEvent) {
        log("wrongway", mapOf("way" to ev.osmId, "main" to ev.mainRoad))
        if (ev.mainRoad) {
            showToast(NavText.MAIN_ROAD_WRONG_WAY.get(lang()), ToastTone.DANGER, ToastIcon.WARNING)
            haptic(NavHaptic.VIBRATE)
        } else {
            val name = ev.name.ifEmpty { NavText.UNNAMED_ROAD.get(lang()) }
            set(stateFlow.value.copy(suggestion = ev))
            suggestionAtMs = clock.wallMs()
            showToast(tl("Ters yön: $name — onay için Ses+", "Əks istiqamət: $name — təsdiq üçün Səs+"), ToastTone.WARN, ToastIcon.SWAP, 10)
        }
    }

    /** Kilit altında çağrılır. */
    private fun arrive(plan: Plan) {
        val st = stateFlow.value
        val idx = plan.order.firstOrNull() ?: 0
        if (idx >= st.stops.size) return
        val stop = st.stops[idx]
        val rest = st.stops.toMutableList().also { it.removeAt(idx) }
        recording?.finish("arrive", clock.wallMs())
        log("arrive", mapOf("la" to Math.round(stop.point.lat * 1e4) / 1e4, "lo" to Math.round(stop.point.lon * 1e4) / 1e4))
        haptic(NavHaptic.HEAVY)
        arrivedAtMs = clock.wallMs()
        // Eski planın sırası artık geçersiz; kalan durak varsa yenisi hesaplanır.
        set(st.copy(stops = rest, plans = emptyMap(), arrived = stop, reached = stop))
        if (rest.isNotEmpty()) replan(ReplanReason.ROUTE)
        val pct = battery
        if (rest.isEmpty() && pct != null && pct <= settings.homeReturnPct) set(stateFlow.value.copy(homeSuggest = true))
    }

    /** Saat ilerler: varış kartı 90 sn sonra kalkar, ters yön önerisi 15 sn sonra kalkar, gün açıkken saatli izinler 5 sn'de bir denetlenir. */
    fun tick() {
        var hours = false
        synchronized(lock) {
            val now = clock.wallMs()
            val st = stateFlow.value
            if (st.arrived != null && now - arrivedAtMs >= NavState.ARRIVED_CARD_MS) set(st.copy(arrived = null))
            if (stateFlow.value.suggestion != null && now - suggestionAtMs >= NavState.SUGGESTION_MS) set(stateFlow.value.copy(suggestion = null))
            if (dayActive && now - hoursCheckedAtMs >= HOURS_CHECK_MS) {
                hoursCheckedAtMs = now
                val allowed = allowedHours(now)
                val changed = allowed != hoursAllowed
                hoursAllowed = allowed
                hours = changed && stateFlow.value.stops.isNotEmpty()
            }
        }
        if (hours) replan(ReplanReason.HOURS)
    }

    /** Test kancası: durumu doğrudan kurar (Dart testlerinin `nav.state = …` karşılığı). */
    internal fun debugSetState(s: NavState) = synchronized(lock) { set(s) }

    // ---------------------------------------------------------------- Yardımcılar

    /** Kilit altında: durumu yazar, plan değiştiyse canlı ölçümü kurar ve yayın kapısını dener. */
    private fun set(s: NavState) {
        val old = stateFlow.value
        stateFlow.value = s
        if (old.plan !== s.plan) {
            tracker.setPlan(s.plan)
            progressFlow.value = tracker.measure
        }
        publish()
    }

    private fun publish() {
        val now = clock.wallMs()
        val stop = stateFlow.value.activeStop
        if (stop !== seenStop) {
            seenStop = stop
            seenAtMs = now
        }
        val target = RouteStatusBuilder.target(stop, seenAtMs)
        val route = tracker.measure
        if (!gate.due(target, route, now)) return
        if (gate.changed(target, route)) updatedAtMs = now
        gate.sent(target, route, now)
        statusFlow.value = RouteStatusBuilder.status(target, route, updatedAtMs)
    }

    /** Kilit altında: gün açıksa hedef gün motoruna bağlanır; açık sipariş varsa aynı durak sipariş hedefi olur. */
    private fun bindShiftTarget(stop: Stop, restaurant: Boolean?) {
        if (!dayActive || stop.purpose == StopPurpose.HOME || stop.purpose == StopPurpose.TASK) return
        val rest = restaurantTarget(stop, restaurant)
        shift?.setDestination(stop.point, rest, stop.label)
        val order = currentOrder ?: return
        val st = stateFlow.value
        set(st.copy(stops = st.stops.map { if (it === stop) it.forOrder(order, rest) else it }))
    }

    /** Restoran hedefi mi: çözümün bayrağı; bilinmiyorsa ve kapı notu yoksa 8 m içinde restoran/fast food/kafe noktası. */
    private fun restaurantTarget(stop: Stop, restaurant: Boolean?): Boolean {
        val r = restaurant ?: stop.restaurant
        if (r != null) return r
        if (stop.unitNote != null) return false
        return restaurantNear?.invoke(stop.point) == true
    }

    private fun allowedHours(now: Long): Set<Long> {
        val at = Instant.ofEpochMilli(now)
        return prefs.reverseHours.filterValues { it.allows(at) }.keys
    }

    /** Son taze konum (≤30 sn); yoksa sahte başlangıç (test). */
    private fun lastPoint(): GeoPoint? {
        val f = lastFix
        if (fresh(f, clock.wallMs(), 30_000)) return GeoPoint(f!!.lat, f.lon)
        return manualOrigin
    }

    private fun nearError(dm: Double, text: String): String {
        val m = dm.roundToInt()
        val s = snippet(text)
        return tl(
            "Adres bulunduğun yere çıktı ($m m), yanlış olabilir.\nGelen metin: «$s»\nDoğru yeri haritada uzun basıp \"Buraya git\" ile seç.",
            "Ünvan olduğun yerə düşdü ($m m), səhv ola bilər.\nGələn mətn: «$s»\nDüzgün yeri xəritədə barmağını saxlayıb \"Bura get\" ilə seç.",
        )
    }

    private fun humanize(e: Exception): String = when (e) {
        is ResolveException -> e.text(lang())
        is LocationUnavailable -> e.text.get(lang())
        is RouteTimeout -> e.msg.get(lang())
        is SocketTimeoutException -> NavText.NET_TIMEOUT.get(lang())
        is IOException -> NavText.NO_INTERNET.get(lang())
        is IllegalStateException -> RouteMsg.ENGINE_FAILED.get(lang())
        else -> e.message ?: e.toString()
    }

    private fun showToast(text: String, tone: ToastTone = ToastTone.INFO, icon: ToastIcon = ToastIcon.NONE, seconds: Int = 4) {
        synchronized(lock) { toastFlow.value = NavToast(++toastSeq, text, tone, icon, seconds) }
    }

    private fun haptic(h: NavHaptic) {
        hapticFlow.tryEmit(h)
    }

    private fun log(name: String, data: Map<String, Any?>) = try {
        log.invoke(name, data)
    } catch (_: Exception) {
    }

    private fun tl(tr: String, az: String) = if (lang() == Lang.AZ) az else tr

    companion object {
        /** Bu kadar yakına düşen dış hedef (restoran değilse) yanlış adres sayılır. */
        const val NEAR_M = 60.0
        const val HOURS_CHECK_MS = 5_000L

        /** `_snippet`: boşluklar teke, 80 karakterden uzunsa ilk 80 + `…`. */
        fun snippet(text: String): String {
            val t = text.replace(Regex("[\\s\\p{Z}]+"), " ").trim()
            return if (t.length <= 80) t else t.substring(0, 80) + "…"
        }

        private fun kindOf(p: ParsedDestination) = when (p) {
            is ParsedDestination.Coordinate -> "coord"
            is ParsedDestination.ShortLink -> "link"
            is ParsedDestination.Address -> "address"
        }

        private fun confidenceOf(c: GeoConfidence) = when (c) {
            GeoConfidence.HIGH -> StopConfidence.HIGH
            GeoConfidence.MEDIUM -> StopConfidence.MEDIUM
            GeoConfidence.LOW -> StopConfidence.LOW
        }

        /** `LocationService._fresh`: doğruluk 0–65 m, geçerli koordinat, yaş −5 sn…[maxAgeMs]. */
        fun fresh(p: NavFix?, nowMs: Long, maxAgeMs: Long): Boolean {
            if (p == null || !p.accuracyM.isFinite() || p.accuracyM > 65 || p.accuracyM < 0) return false
            if (!p.lat.isFinite() || !p.lon.isFinite() || kotlin.math.abs(p.lat) > 90 || kotlin.math.abs(p.lon) > 180) return false
            val dt = nowMs - p.atMs
            return dt >= -5000 && dt <= maxAgeMs
        }
    }
}
