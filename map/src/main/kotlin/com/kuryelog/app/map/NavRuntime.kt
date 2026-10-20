package com.kuryelog.app.map

import android.content.ClipboardManager
import android.content.Context
import com.kuryelog.app.core.Home
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.NavKind
import com.kuryelog.logic.core.NavOrigin
import com.kuryelog.logic.core.NavPort
import com.kuryelog.logic.core.NavTarget
import com.kuryelog.logic.core.RouteStatus
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.input.ClipboardHash
import com.kuryelog.logic.map.input.AddressParser
import com.kuryelog.logic.map.input.DestinationResolver
import com.kuryelog.logic.map.input.ResolvedDestination
import com.kuryelog.logic.map.input.FallbackGeocoder
import com.kuryelog.logic.map.input.GeocodeHit
import com.kuryelog.logic.map.input.Geocoder
import com.kuryelog.logic.map.input.IncomingLink
import com.kuryelog.logic.map.input.PlaceSearch
import com.kuryelog.logic.map.nav.NavController
import com.kuryelog.logic.map.nav.NavFix
import com.kuryelog.logic.map.nav.NavOnlyController
import com.kuryelog.logic.map.nav.NavText
import com.kuryelog.logic.map.nav.Stop
import com.kuryelog.logic.map.net.UserAgent
import com.kuryelog.logic.map.route.BikewayMarksStore
import com.kuryelog.logic.map.route.RouteEngine
import com.kuryelog.logic.map.route.StreetPrefsCodec
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Navigasyonun Android çalışma zamanı (MAP.md 7.16, 8.12): tek [NavController] (rota, takip, yayın), yer arama [search], konum kaynağı, gelen bağlantılar ve pano,
 * `NavPort` ([port]). Harita verisi, Harita ayarları, sokak kararları ve vardiya akışlarını denetleyiciye bağlar; saati saniyede bir ilerletir.
 * Gün motoru ve POI (A6.8), sürüş günlüğü ve rota kaydı, görevler ve hatırlatıcılar (A6.9) bağlı; yol öğrenme (A6.11) kendi görevinde bağlanır.
 */
class NavRuntime private constructor(private val app: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val core = app.getSharedPreferences("kuryelog", Context.MODE_PRIVATE)
    private val mapPrefs = MapPrefsStore(app)
    private val settings = MapSettingsRepo.settings(app)
    private val userAgent = UserAgent.of(BuildConfigVersion.name(app))
    val lang: () -> Lang = { Lang.resolve(core.getString(Lang.PREF_KEY, null), Locale.getDefault().language) }

    /** Geokod zinciri her çözümde güncel anahtarlarla kurulur (Google → ORS → Nominatim; MAP.md 8.6). */
    private val geocoder = object : Geocoder {
        override suspend fun search(query: String, near: GeoPoint?): GeocodeHit? {
            val s = settings.value
            return FallbackGeocoder.of(AndroidHttp, s.googleKey, s.orsKey, userAgent).search(query, near)
        }
    }

    /** Son konum okuması (veri seti kurye noktası, MAP.md 13.11). */
    @Volatile var lastFix: NavFix? = null
        private set

    private val location: NavLocation = NavLocation(app) { f ->
        lastFix = f
        speedKmh = (f.speedMps ?: 0.0).coerceAtLeast(0.0) * 3.6
        controller.onFix(f)
        day.onFix(f)
        RideLogRuntime.get(app).onFix(f)
        TaskRuntime.get(app).onFix(f)
        WeatherRuntime.get(app).onFix(f)
        BatteryRuntime.get(app).onFix(f)
    }

    /** Gün motoru (MAP.md 11): fix'ler, vardiya, aşama okuması; hedef bağı [controller]'a aşağıda takılır. */
    val day: DayRuntime = DayRuntime.get(app)

    /** Son fix'in hızı (km/s; yaklaşma zoom'u, MAP.md 7.10). */
    @Volatile var speedKmh = 0.0
        private set
    private val resolver = DestinationResolver(AndroidHttp, geocoder)
    val controller: NavController = NavController(RouteEngine(), scope, Clock.Jvm, resolver, lang, location)

    /** Mekân adı, koordinat ya da Maps bağlantısı → nokta (görev düzenleyici, MAP.md 13.14); çözülemezse null. */
    suspend fun resolvePlace(text: String): ResolvedDestination? {
        val parsed = AddressParser.parseExplicit(text) ?: return null
        return runCatching { resolver.resolve(parsed, controller.here()) }.getOrNull()
    }

    val search = PlaceSearch(
        scope = scope,
        http = AndroidHttp,
        credentials = { ApkGoogleConfig.credentials(app, settings.value.googleKey) },
        pois = { MapPois.all(app) },
        graph = { MapData.controller(app).state.value.graph },
        lang = lang,
        near = { controller.here() },
        prefs = mapPrefs,
        orsKey = { settings.value.let { if (it.googleKey.isEmpty()) it.orsKey else "" } },
    )

    private val navOnly = NavOnlyController(Clock.Jvm) { on ->
        NavOnlyService.set(app, on)
        refreshLocation()
    }

    /** Harita sekmesi görünür mü (konum ön planda). */
    @Volatile private var visible = false

    /** Gelen bağlantı haritayı açar: her bağlantıda artar (`:app` sekmeyi değiştirir). */
    private val openMapFlow = MutableStateFlow(0)
    val openMap: StateFlow<Int> get() = openMapFlow

    private var clipboardBusy = false

    val port: NavPort = object : NavPort {
        override val route: StateFlow<RouteStatus?> get() = controller.routeStatus
        override fun open(target: NavTarget) {
            controller.openTarget(
                Stop(
                    GeoPoint(target.lat, target.lon), target.label ?: NavText.MAP_POINT.get(lang()),
                    restaurant = when (target.kind) {
                        NavKind.RESTAURANT -> true
                        NavKind.CUSTOMER -> false
                        NavKind.UNKNOWN -> null
                    },
                    origin = target.origin, requestedAtMs = target.requestedAtMs, sourcePackage = target.sourcePackage,
                ),
            )
        }
    }

    /** Elle bisiklet yolu işaretleri (`streets.bikeway.v1`); değişince açık rota yeniden hesaplanır (MAP.md 5.1). */
    val bikeway = BikewayMarksStore(mapPrefs)

    init {
        controller.setBikewayMarks(bikeway.marks)
        controller.shift = day.navShift
        RideLogRuntime.get(app).let { log ->
            controller.log = { name, data -> log.writer.event(name, data) }
            controller.recording = log.recording
        }
        DayReportWriter.get(app)
        TaskRuntime.get(app)
        ReminderRuntime.get(app)
        controller.restaurantNear = { MapPois.foodNear(app, it) }
        controller.setStreetPrefs(StreetPrefsCodec.load(mapPrefs))
        scope.launch { MapData.controller(app).state.map { it.graph }.distinctUntilChanged().collect { controller.setGraph(it) } }
        scope.launch { settings.collect { controller.setSettings(it) } }
        scope.launch {
            ShiftHub.state.collect {
                controller.setOrder(it.active, null)
                navOnly.update(it.active, controller.state.value.hasRoute)
                refreshLocation()
            }
        }
        scope.launch {
            controller.state.map { it.hasRoute }.distinctUntilChanged().collect {
                navOnly.update(ShiftHub.state.value.active, it)
                refreshLocation()
            }
        }
        scope.launch {
            while (isActive) {
                delay(1_000)
                controller.tick()
                navOnly.update(ShiftHub.state.value.active, controller.state.value.hasRoute)
            }
        }
    }

    /** Konum: harita önde, gün açık ya da yalnız-nav iken (MAP.md 7.6 kapsam). */
    private fun refreshLocation() {
        val on = visible || ShiftHub.state.value.active || navOnly.on
        location.setActive(on)
        BatteryRuntime.get(app).setActive(on)
    }

    fun setVisible(on: Boolean) {
        visible = on
        WeatherRuntime.get(app).setVisible(on)
        refreshLocation()
    }

    /** Eve dön (MAP.md 7.3, 7.8): ev `:core`'da; yoksa denetleyici uyarır. */
    fun goHome() = controller.goHome(Home.point(app)?.let { GeoPoint(it.lat, it.lon) })

    /** Bisiklet işaretleri değişti: denetleyici yeni kümeyle sürer, açık rota yeniden hesaplanır. */
    fun reloadBikeway() = controller.setBikewayMarks(bikeway.marks)

    /** Sokak kararları değişti (uzun basış, Ses±): denetleyici yeni listeyle sürer. */
    fun reloadStreetPrefs() = controller.setStreetPrefs(StreetPrefsCodec.load(mapPrefs))

    /** Kurye uygulamasının navigasyon isteği ya da paylaşılan metin (MAP.md 8.2): VIEW → `intent`, SEND → `clipboard`. */
    fun onLink(link: IncomingLink) {
        openMapFlow.value++
        scope.launch {
            controller.onExternalText(link.text, "intent", if (link.view) NavOrigin.INTENT else NavOrigin.CLIPBOARD, link.referrer, link.at)
        }
    }

    /**
     * Pano (MAP.md 8.3): uygulama öne gelince ve odak alınca; ilk okuma boşsa 400 ms sonra bir kez daha; aynı metin (FNV-1a) ikinci kez işlenmez.
     * Ayrıştırılamayan pano metni sessizdir. Android 10+ panoyu yalnız öndeki uygulamaya verir.
     */
    fun checkClipboard(ctx: Context) {
        synchronized(this) {
            if (clipboardBusy) return
            clipboardBusy = true
        }
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        fun read(): String = runCatching { cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString() }.getOrNull()?.trim().orEmpty()
        scope.launch(Dispatchers.Main) {
            try {
                var text = read()
                if (text.isEmpty()) {
                    delay(ClipboardHash.RETRY_MS)
                    text = read()
                }
                if (text.isEmpty()) return@launch
                val h = ClipboardHash.fnv1a(text)
                if (mapPrefs.getLong(ClipboardHash.KEY) == h) return@launch
                mapPrefs.setLong(ClipboardHash.KEY, h)
                scope.launch { controller.onExternalText(text, "clipboard") }
            } finally {
                synchronized(this@NavRuntime) { clipboardBusy = false }
            }
        }
    }

    /** Harita sekmesine geçiş isteği (görev "Rota Oluştur", MAP.md 13.13). */
    fun showMap() {
        openMapFlow.value++
    }

    fun launch(block: suspend CoroutineScope.() -> Unit) = scope.launch(block = block)

    companion object {
        @Volatile private var instance: NavRuntime? = null

        fun get(ctx: Context): NavRuntime = instance ?: synchronized(this) { instance ?: NavRuntime(ctx.applicationContext).also { instance = it } }
    }
}

internal object BuildConfigVersion {
    fun name(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
}
