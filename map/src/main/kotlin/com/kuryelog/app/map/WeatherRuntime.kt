package com.kuryelog.app.map

import android.annotation.SuppressLint
import android.content.Context
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.logic.core.GpsFix
import com.kuryelog.logic.core.ShiftMachine
import com.kuryelog.logic.core.WeatherNow
import com.kuryelog.logic.core.WeatherPort
import com.kuryelog.logic.map.weather.WeatherReport
import com.kuryelog.logic.map.weather.WeatherSchedule
import com.kuryelog.logic.map.weather.WeatherSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hava çalışma zamanı (YolGo `LiveWeather`, `weatherRuntimeProvider`; MAP.md 10.7–10.10): ayarlar (`weather.enabled.v1`, `weather.strip.v1`), harita ekranı görünürken
 * gün açıkken 10 dk'da bir yenileme, vardiya fix'lerinden 2 dk'da bir hava konumu, 2 saatlik bildirim işi ([WeatherWork]) ve `WeatherPort` (R15). Her telefon kendi havasını çeker (S6).
 */
// Yalnız uygulama bağlamı tutulur (Activity değil): sızıntı yok.
@SuppressLint("StaticFieldLeak")
object WeatherRuntime {
    /** Ekranın gördüğü durum: [error] son yenileme başarısız (rapor eski kalır), [busy] yenileme sürüyor. */
    class State(val report: WeatherReport? = null, val error: Boolean = false, val busy: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled
    private val _strip = MutableStateFlow(true)
    val strip: StateFlow<Boolean> = _strip

    @Volatile private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loading = AtomicBoolean(false)
    @Volatile private var revision = 0
    @Volatile private var shortFetched = false
    @Volatile private var foreground = 0
    private var timer: Job? = null
    @Volatile private var lastLocationMs = 0L

    /** `WeemApplication.onCreate`: ayarları okur, vardiya değişimini izler, hava konumunu vardiya fix'lerinden alır, bildirim işini kurar. */
    fun bind(ctx: Context) {
        val a = ctx.applicationContext
        app = a
        val prefs = MapPrefsStore(a)
        _enabled.value = prefs.getBool(WeatherSchedule.KEY_ENABLED) ?: true
        _strip.value = prefs.getBool(WeatherSchedule.KEY_STRIP) ?: true
        ShiftHub.listeners.add("weather", LocationHook)
        scope.launch {
            ShiftHub.state.map { it.active }.distinctUntilChanged().collect { active ->
                revision++
                if (!active) _state.value = State(_state.value.report)
                WeatherWork.configure(a, active, _enabled.value)
                scope()
                if (active && foreground > 0) refresh()
            }
        }
    }

    fun setEnabled(on: Boolean) {
        val a = app ?: return
        MapPrefsStore(a).setBool(WeatherSchedule.KEY_ENABLED, on)
        _enabled.value = on
        WeatherWork.configure(a, ShiftHub.state.value.active, on)
    }

    fun setStrip(on: Boolean) {
        val a = app ?: return
        MapPrefsStore(a).setBool(WeatherSchedule.KEY_STRIP, on)
        _strip.value = on
    }

    /** Harita ekranı görünür: ilk girişte bir yenileme (gün kapalıyken yalnız önbellek); görünürken gün açıksa 10 dk'da bir. */
    fun enter() {
        if (foreground++ == 0) {
            scope()
            scope.launch { refresh(cacheOnly = !ShiftHub.state.value.active) }
        }
    }

    fun leave() {
        if (foreground > 0 && --foreground == 0) scope()
    }

    private fun scope() {
        timer?.cancel()
        timer = null
        if (foreground > 0 && ShiftHub.state.value.active) {
            timer = scope.launch {
                while (true) {
                    delay(WeatherSchedule.FOREGROUND_REFRESH_MS)
                    if (foreground > 0 && ShiftHub.state.value.active) refresh()
                }
            }
        }
    }

    /** `LiveWeather.refresh`: [cacheOnly] gün kapalıyken (ağ yalnız kısa önbellekte süreç başına bir kez), [force] "Yenile" (önbelleği yok sayar). */
    suspend fun refresh(force: Boolean = false, cacheOnly: Boolean = false) {
        val ctx = app ?: return
        if (!loading.compareAndSet(false, true)) return
        val rev = revision
        val previous = _state.value.report
        _state.value = State(previous, busy = true)
        try {
            val now = System.currentTimeMillis()
            val cachedRaw = WeatherFetch.cached(ctx)
            val plan = WeatherSchedule.plan(WeatherFetch.parse(cachedRaw, now), now, force, cacheOnly, shortFetched)
            if (plan.usesShortFetch) shortFetched = true
            val dayId = if (!force && !cacheOnly) WeatherFetch.dayId() else null
            val raw = if (plan.network) WeatherFetch.fetch(ctx, dayId, force) else cachedRaw
            val report = WeatherReport.parse(raw ?: throw java.io.IOException("No weather data"), System.currentTimeMillis())
            if (rev == revision) _state.value = State(report)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (rev == revision) _state.value = State(previous, error = true)
        } finally {
            loading.set(false)
        }
    }

    /** "8 saatlik tahmini göster" (Harita ayarları): ağdan zorla; hata null. */
    suspend fun forecast(): WeatherReport? {
        val ctx = app ?: return null
        return runCatching {
            val raw = WeatherFetch.fetch(ctx, null, force = true)
            WeatherReport.parse(raw, System.currentTimeMillis())
        }.getOrNull()
    }

    /** Hava konumu (YolGo `updateWeatherLocation`): doğruluk 0–65 m, yaş −5…+30 sn, en çok 2 dk'da bir. */
    internal fun onFix(fix: GpsFix, nowMs: Long) {
        val a = app ?: return
        if (!_enabled.value) return
        val acc = if (fix.hasAccuracy) fix.accuracy.toDouble() else return
        if (!WeatherSchedule.locationAccepted(acc, nowMs - fix.timeMs)) return
        if (nowMs - lastLocationMs < WeatherSchedule.LOCATION_EVERY_MS) return
        if (fix.lat !in -90.0..90.0 || fix.lon !in -180.0..180.0) return
        lastLocationMs = nowMs
        val p = MapPrefsStore(a)
        p.setString(WeatherFetch.KEY_LAT, fix.lat.toString())
        p.setString(WeatherFetch.KEY_LON, fix.lon.toString())
        p.setLong(WeatherFetch.KEY_LOCATION_AT, nowMs)
    }

    private object LocationHook : ShiftMachine.Hooks {
        override fun onFix(fix: GpsFix, speedMps: Double?, nowMs: Long) = WeatherRuntime.onFix(fix, nowMs)
    }

    /** `WeatherPort` (R15): son rapordan `WeatherNow` (≤ 90 dk taze); rapor yok ya da bozuksa null. */
    val port: WeatherPort = object : WeatherPort {
        override fun current(): WeatherNow? {
            val ctx = app ?: return null
            val now = System.currentTimeMillis()
            return WeatherSummary.summary(WeatherFetch.cached(ctx))?.takeIf { it.fresh(now) }
        }
    }
}
