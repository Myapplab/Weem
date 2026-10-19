package com.kuryelog.app.map

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.logic.core.GpsFix
import com.kuryelog.logic.core.RangeBattery
import com.kuryelog.logic.core.RangeLeg
import com.kuryelog.logic.core.RangePort
import com.kuryelog.logic.core.ScooterBridge
import com.kuryelog.logic.core.ShiftMachine
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.ride.AltitudeSample
import com.kuryelog.logic.map.ride.AltitudeSource
import com.kuryelog.logic.map.ride.BaroAltitude
import com.kuryelog.logic.map.ride.BatteryEstimator
import com.kuryelog.logic.map.ride.BatteryState
import com.kuryelog.logic.map.route.PlanRequest
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.route.RouteEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Hız ve batarya çalışma zamanı (Y9, N5; MAP.md 10.3, 10.6, 10.13): vardiya fix'lerinden (`ShiftHub.listeners` `onFix`) enerji harcama tahmini ([BatteryEstimator]), barometre ya da DEM yüksekliği,
 * canlı scooter yüzdesi ([ScooterBridge.live]) çapa; N5 için [RangePort]. Tercihler `map` dosyasında (`battery.setPct`/`consumed`/`setAt`). Canlı değer yokken ve vardiya sürerken tahmin yürür.
 */
// Yalnız uygulama bağlamı tutulur (Activity değil): sızıntı yok.
@SuppressLint("StaticFieldLeak")
object RideRuntime {
    private val _battery = MutableStateFlow(BatteryState())
    val battery: StateFlow<BatteryState> = _battery

    @Volatile private var app: Context? = null
    @Volatile private var estimator: BatteryEstimator? = null
    @Volatile private var live: ScooterBridge = ScooterBridge.NONE
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: RouteEngine? = null
    private val baro = Baro()

    /** `WeemApplication.onCreate`: tahmini kurar, canlı scooter akışını çapa olarak bağlar, vardiya fix'lerini dinler. */
    fun bind(ctx: Context, scooter: ScooterBridge) {
        val a = ctx.applicationContext
        app = a
        live = scooter
        val capacity = MapSettingsRepo.settings(a).value.vehicle.capacityWh
        val est = BatteryEstimator(MapPrefsStore(a), capacity) { System.currentTimeMillis() }
        estimator = est
        _battery.value = est.state.value
        scope.launch { est.state.collect { _battery.value = it } }
        scope.launch { scooter.live.collect { l -> est.onLive(l?.takeIf { it.fresh }) } }
        ShiftHub.listeners.add("map_battery", BatteryHook)
    }

    private object BatteryHook : ShiftMachine.Hooks {
        override fun onRunning() { app?.let(baro::start) }

        override fun onFix(fix: GpsFix, speedMps: Double?, nowMs: Long) {
            val a = app ?: return
            val est = estimator ?: return
            val s = MapSettingsRepo.settings(a).value
            val point = GeoPoint(fix.lat, fix.lon)
            val altitude = baro.sample(nowMs) ?: dem(a, point)
            val accuracy = if (fix.hasAccuracy) fix.accuracy.toDouble() else Double.NaN
            est.onFix(point, fix.timeMs, accuracy, if (fix.hasSpeed) fix.speed.toDouble() else -1.0, s.flatWhPerM, s.climbWhPerM, altitude)
        }

        override fun onEnd(startMs: Long, nowMs: Long, reason: String) {
            baro.stop()
            estimator?.flush()
        }
    }

    private fun dem(a: Context, p: GeoPoint): AltitudeSample? =
        MapData.controller(a).state.value.elevation?.takeIf { it.available }?.let { AltitudeSample(it.at(p.lat, p.lon), AltitudeSource.DEM) }

    /** N5 (MAP.md 10.6): batarya taze BLE yüzdesi, yoksa Y9 tahmini; rota `RideMode.BATTERY` ile. Harita yoksa rota null. */
    val range: RangePort = object : RangePort {
        override fun battery(): RangeBattery? {
            val b = _battery.value
            val pct = b.percent ?: return null
            val now = System.currentTimeMillis()
            val wh = b.remainingWh ?: return null
            val fresh = live.live.value?.takeIf { it.fresh && it.scooter.internalPct != null }
            return if (fresh != null && b.live) RangeBattery(pct, wh, RangeBattery.BLE_FRESH, now) else RangeBattery(pct, wh, RangeBattery.ESTIMATOR, now)
        }

        override suspend fun rideTo(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RangeLeg? {
            val a = app ?: return null
            val graph = MapData.controller(a).state.value.graph ?: return null
            val settings = MapSettingsRepo.settings(a).value
            val eng = engine ?: synchronized(this@RideRuntime) { engine ?: RouteEngine().also { engine = it } }
            return runCatching {
                val request = PlanRequest(graph, GeoPoint(fromLat, fromLon), listOf(GeoPoint(toLat, toLon)), settings.paramsFor(_battery.value.percent), emptySet(), emptySet())
                val plan = eng.compute(request).plans[RideMode.BATTERY] ?: return@runCatching null
                RangeLeg(plan.rideM / 1000, plan.timeS / 60, plan.energyWh)
            }.getOrNull()
        }
    }

    /** Barometre (`SensorHub`): vardiya sürerken basınç örneği; yükseklik yalnız son 3 sn'de örnek varsa (mutlak değer kaba, farklar hassas). */
    private class Baro : SensorEventListener {
        @Volatile private var hPa: Double? = null
        @Volatile private var atMs: Long? = null
        private var manager: SensorManager? = null

        fun start(ctx: Context) {
            if (manager != null) return
            val m = ctx.getSystemService(SensorManager::class.java) ?: return
            val sensor = m.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return
            if (m.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)) manager = m
        }

        fun stop() {
            manager?.unregisterListener(this)
            manager = null
            hPa = null
            atMs = null
        }

        fun sample(nowMs: Long): AltitudeSample? = BaroAltitude.sample(hPa, atMs, nowMs)

        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.isEmpty()) return
            hPa = event.values[0].toDouble()
            atMs = System.currentTimeMillis()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}
