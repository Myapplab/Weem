package com.kuryelog.logic.map.weather

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Rapor kaynağı (Android `:map` `WeatherFetch`): [cached] ağ açmadan son raporu, [fetch] önbellek kuralıyla ya da [force] ile ağdan verir; hata fırlatır. */
interface WeatherSource {
    suspend fun cached(): String?
    suspend fun fetch(force: Boolean, dayId: String?): String
}

/** Hava görünümü: [failed] "Hava güncellenemedi" (önceki rapor kalır). */
data class WeatherState(val report: WeatherReport? = null, val busy: Boolean = false, val failed: Boolean = false)

/**
 * `LiveWeather` (`weather.dart:78–128`, MAP.md 10.7): açılışta bir yenileme (gün kapalıyken yalnız önbellek); önbellek < 20 dk ve ≥ 8 saatse ağ yok; < 8 saatse süreç başına bir kez
 * (gün kapalı da olsa) ağdan tazelenir. Hata → önceki rapor kalır. Zamanlayıcı (gün açık + uygulama önde: 10 dk) Android `WeatherRuntime`'da; [scopeChanged] gün açılıp kapanınca.
 */
class LiveWeather(private val source: WeatherSource, private val nowMs: () -> Long) {
    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> get() = _state
    private var loading = false
    private var shortFetched = false
    private var revision = 0

    /** Gün açıldı/kapandı: süren yenileme sonucu atılır; kapanınca rapor kalır, hata ve meşgul sıfırlanır. */
    @Synchronized
    fun scopeChanged(active: Boolean) {
        revision++
        if (!active) _state.value = WeatherState(report = _state.value.report)
    }

    /** [dayId] gün kimliği (gün açıkken); [cacheOnly] gün kapalıyken. */
    suspend fun refresh(force: Boolean = false, cacheOnly: Boolean = false, dayId: String? = null) {
        val rev: Int
        val previous: WeatherReport?
        synchronized(this) {
            if (loading) return
            loading = true
            rev = revision
            previous = _state.value.report
            _state.value = WeatherState(report = previous, busy = true)
        }
        try {
            var raw = source.cached()
            var fresh = false
            var short = raw == null
            if (raw != null) {
                try {
                    val cached = WeatherReport.parse(raw, nowMs())
                    fresh = (nowMs() - cached.fetchedAtMs) / 60_000 < 20
                    // Saat ilerledikçe eski önbellekte 8'den az saat kalır; gün başlamamış olsa da bir kez internetten tazelenir.
                    short = cached.hours.size < WeatherReport.DETAIL_HOURS
                } catch (_: Exception) {
                    short = true
                }
            }
            if (rev != revision) return
            val shortOnce = cacheOnly && short && !shortFetched
            if (shortOnce) shortFetched = true
            if ((!cacheOnly || shortOnce) && (force || !fresh || short)) raw = source.fetch(force, if (!force && !cacheOnly) dayId else null)
            if (raw == null) throw IllegalStateException("No weather data")
            val report = WeatherReport.parse(raw, nowMs())
            if (rev == revision) _state.value = WeatherState(report = report)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            if (rev == revision) _state.value = WeatherState(report = previous, failed = true)
        } finally {
            synchronized(this) {
                loading = false
                // Gün kapsamı yarıda değiştiyse sonuç atılır; meşgul işareti takılı kalmasın.
                if (rev != revision && _state.value.busy) _state.value = _state.value.copy(busy = false)
            }
        }
    }
}
