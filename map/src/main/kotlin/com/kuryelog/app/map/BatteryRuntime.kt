package com.kuryelog.app.map

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.kuryelog.logic.core.RangeBattery
import com.kuryelog.logic.core.RangeLeg
import com.kuryelog.logic.core.RangePort
import com.kuryelog.logic.core.ScooterBridge
import com.kuryelog.logic.core.ScooterLive
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.NavFix
import com.kuryelog.logic.map.ride.AltitudeSample
import com.kuryelog.logic.map.ride.AltitudeSource
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Batarya tahmini çalışma zamanı (Y9, MAP.md 10.3, 10.13): [BatteryEstimator] + fix akışı (`NavLocation`), barometre (varsa) ya da DEM yüksekliği ve canlı scooter değeri
 * (`:app` [bind] ile `ScooterBridge.live`; taze değilse null). Tahmin canlıyken yürümez, çapa canlı değerdir. Elle % girişi yok.
 */
class BatteryRuntime private constructor(private val app: Context) : SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settings = MapSettingsRepo.settings(app)
    val estimator = BatteryEstimator(MapPrefsStore(app), settings.value.vehicle.capacityWh, System::currentTimeMillis)
    val state: StateFlow<BatteryState> get() = estimator.state

    private val liveFlow = MutableStateFlow<ScooterLive?>(null)

    /** Taze canlı scooter değeri (`ScooterLive.fresh`), yoksa null: sol batarya kutusu ve değer penceresi. */
    val live: StateFlow<ScooterLive?> get() = liveFlow

    private val sensors = app.getSystemService(SensorManager::class.java)
    private var listening = false

    @Volatile private var baroHpa: Float? = null
    @Volatile private var baroAtMs = 0L

    init {
        scope.launch { settings.map { it.vehicle.capacityWh }.collect { estimator.setCapacity(it) } }
    }

    /** `:app` açılışta: canlı scooter değeri tahminin çapası olur. */
    fun bind(bridge: ScooterBridge) {
        scope.launch {
            bridge.live.map { it?.takeIf { l -> l.fresh } }.collect {
                liveFlow.value = it
                estimator.onLive(it)
            }
        }
    }

    fun onFix(f: NavFix) {
        val s = settings.value
        val altitude = baroHpa?.let { AltitudeSample(SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it).toDouble(), AltitudeSource.BARO) }
            ?: MapData.controller(app).state.value.elevation?.takeIf { it.available }?.let { AltitudeSample(it.at(f.lat, f.lon), AltitudeSource.DEM) }
        estimator.onFix(GeoPoint(f.lat, f.lon), f.atMs, f.accuracyM, f.speedMps ?: -1.0, s.flatWhPerM, s.climbWhPerM, altitude)
    }

    /** Barometre yalnız konum açıkken dinlenir (gün açık, harita önde ya da yalnız-nav). */
    @Synchronized
    fun setActive(on: Boolean) {
        if (on == listening) return
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (!on) {
            if (sensor != null) runCatching { sensors?.unregisterListener(this) }
            listening = false
            baroHpa = null
            return
        }
        listening = true
        if (sensor != null) runCatching { sensors?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onSensorChanged(e: SensorEvent) {
        val p = e.values.firstOrNull() ?: return
        // Gürültüyü bastır: üstel ortalama (YolGo `SensorHub` baro yumuşatması).
        baroHpa = baroHpa?.let { it + 0.2f * (p - it) } ?: p
        baroAtMs = System.currentTimeMillis()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Son barometre okuması (hPa, ms); sensör yoksa ya da dinlenmiyorsa null (sürüş günlüğü `env` satırı, MAP.md 12.2). */
    fun pressureSample(): Pair<Double, Long>? = baroHpa?.let { it.toDouble() to baroAtMs }

    /** N5 (MAP.md 10.6): ekonomi v2'ye menzil girdileri; batarya canlıysa `ble_fresh`, değilse Y9 tahmini; rota `RideMode.BATTERY` ile. */
    val range: RangePort = object : RangePort {
        private val engine = RouteEngine()

        override fun battery(): RangeBattery? {
            val s = estimator.state.value
            val pct = s.percent ?: return null
            val now = System.currentTimeMillis()
            return if (s.live) RangeBattery(pct, s.capacityWh * pct / 100, RangeBattery.BLE_FRESH, now)
            else RangeBattery(pct, s.remainingWh ?: return null, RangeBattery.ESTIMATOR, now)
        }

        override suspend fun rideTo(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RangeLeg? {
            val graph = MapData.controller(app).state.value.graph ?: return null
            val params = settings.value.paramsFor(estimator.state.value.percent)
            val result = runCatching {
                engine.compute(PlanRequest(graph, GeoPoint(fromLat, fromLon), listOf(GeoPoint(toLat, toLon)), params, emptySet(), emptySet()))
            }.getOrNull() ?: return null
            val plan = result.plans[RideMode.BATTERY] ?: result.plans.values.firstOrNull() ?: return null
            return RangeLeg((plan.rideM + plan.walkM) / 1000, plan.timeS / 60, plan.energyWh)
        }
    }

    companion object {
        @Volatile private var instance: BatteryRuntime? = null

        fun get(ctx: Context): BatteryRuntime = instance ?: synchronized(this) { instance ?: BatteryRuntime(ctx.applicationContext).also { instance = it } }
    }
}
