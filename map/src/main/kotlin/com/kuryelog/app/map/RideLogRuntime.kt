package com.kuryelog.app.map

import android.content.Context
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.LogTime
import com.kuryelog.logic.map.logs.DayLogBridge
import com.kuryelog.logic.map.logs.RideLogWriter
import com.kuryelog.logic.map.logs.RouteRecording
import com.kuryelog.logic.map.nav.NavFix
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
import java.io.File

/**
 * Sürüş günlüğünün Android çalışma zamanı (Y18; MAP.md 12.2–12.5, 12.14): `files/map/logs/` yazıcısı ([RideLogWriter]), gün motoru adımları ([DayLogBridge]), konum fix'leri,
 * 1 sn'de bir `env` (barometre), 5 sn'de bir boşaltma, rota kaydı ([RouteRecording]; dosya `.tmp` + `rename`). Ayar `MapSettings.logging`.
 */
class RideLogRuntime private constructor(private val app: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings = MapSettingsRepo.settings(app)
    private val clock = Clock.Jvm

    val dir = File(app.filesDir, "map/logs")
    val writer = RideLogWriter(dir, MapPrefsStore(app), clock)
    private val bridge = DayLogBridge(writer)

    private val speedFlow = MutableStateFlow<Pair<Double?, Double?>>(null to null)

    /** Rota boyunca ortalama hız (km/s): (duruşlar dahil, yalnız hareketli); kayıt yokken (null, null). */
    val routeSpeed: StateFlow<Pair<Double?, Double?>> get() = speedFlow

    /** Navigasyon denetleyicisine takılan rota kaydı (`NavController.recording`). */
    val recording = RouteRecording(
        nowMs = { clock.wallMs() },
        loggingEnabled = { settings.value.logging },
        logEvent = { name, data -> writer.event(name, data) },
        writeRoute = ::writeRoute,
        onSpeed = { avg, moving -> speedFlow.value = avg to moving },
    )

    init {
        writer.enabled = settings.value.logging
        val day = DayRuntime.get(app)
        day.view.value.state.let { s -> if (s.active) s.startedAtMs?.let { writer.setDay("yolgo-$it") } }
        day.addStepListener { bridge.onStep(it) }
        scope.launch { settings.map { it.logging }.distinctUntilChanged().collect { writer.enabled = it } }
        scope.launch {
            var tick = 0
            while (isActive) {
                delay(1_000)
                if (writer.activeDayId != null) {
                    BatteryRuntime.get(app).pressureSample()?.let { (hpa, at) -> writer.env(hpa, at) }
                    if (++tick % 5 == 0) writer.flush()
                }
            }
        }
    }

    /** Konum akışının her fix'i (`NavLocation`): en sık ~1 sn'de bir yazılır. Yükseklik ve yön bilinmiyorsa 0. */
    fun onFix(f: NavFix) = writer.gps(f.atMs, f.lat, f.lon, f.accuracyM, f.speedMps ?: -1.0, f.headingDeg ?: 0.0, 0.0)

    private fun writeRoute(startedAtMs: Long, json: String) {
        scope.launch {
            runCatching {
                dir.mkdirs()
                val name = "${LogTime.day(startedAtMs, clock.zone())}_route_$startedAtMs.json"
                val tmp = File(dir, "$name.tmp")
                tmp.writeText(json)
                val out = File(dir, name)
                if (!tmp.renameTo(out)) {
                    out.delete()
                    tmp.renameTo(out)
                }
            }
        }
    }

    companion object {
        @Volatile private var instance: RideLogRuntime? = null

        fun get(ctx: Context): RideLogRuntime = instance ?: synchronized(this) { instance ?: RideLogRuntime(ctx.applicationContext).also { instance = it } }
    }
}
