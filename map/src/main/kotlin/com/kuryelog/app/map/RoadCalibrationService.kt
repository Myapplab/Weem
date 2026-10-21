package com.kuryelog.app.map

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.map.calibration.CALIBRATION_OFFSET_WAIT_MS
import com.kuryelog.logic.map.calibration.CalibrationConfig
import com.kuryelog.logic.map.calibration.CalibrationFix
import com.kuryelog.logic.map.calibration.CalibrationPhase
import com.kuryelog.logic.map.calibration.CalibrationState
import com.kuryelog.logic.map.calibration.RoadCalibration
import com.kuryelog.logic.map.calibration.RoadCalibrationRoadRms
import com.kuryelog.logic.map.calibration.RoadCalibrationStore
import com.kuryelog.logic.map.calibration.calibrationCountdownDeadline
import com.kuryelog.logic.map.calibration.calibrationCountdownRemaining
import com.kuryelog.logic.map.calibration.calibrationQuality
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Y14 yol kalibrasyonu: kullanıcının başlattığı yerel konum ön plan servisi (YolGo `RoadCalibrationService`; bildirim 7435). Yüzen panel
 * ([RoadCalibrationOverlay]) Wolt/Bolt'un üstünde sabit hızla sürerken geri sayım, süre ve sonucu gösterir. Wolt/Bolt'a hiçbir şeye basmaz. İki hesap
 * ayrı yürür: PR #23 ham RMS ([RoadCalibration], HP 2 Hz, GPS hızından boylamsal ivme ve ±%10 hız kapısı) ve E10 yol RMS'i ([RoadCalibrationRoadRms]; `SurfaceSignal`
 * pencereleri, metre ağırlıklı). 3-2-1, yol RMS ofseti (GPS + ≥2 sn duruş) hazır olunca (en çok 15 sn) başlar. Kayıtlar `files/road-calibration-v1/`. Cihazda denenmedi.
 */
class RoadCalibrationService : Service(), SensorEventListener {
    companion object {
        @Volatile private var instance: RoadCalibrationService? = null
        private val stateFlow = MutableStateFlow(CalibrationState())

        /** Panelin ve ekranın anlık durumu. */
        val state: StateFlow<CalibrationState> get() = stateFlow

        /** Kanal kimliği eski uygulamayla aynı (CORE.md 3.3). */
        private const val CHANNEL = "yolgo_calibration"

        fun permitted(c: Context) = c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        fun overlayPermitted(c: Context) = Settings.canDrawOverlays(c)

        fun store(c: Context): RoadCalibrationStore = synchronized(this) {
            RoadCalibrationStore(File(c.applicationContext.filesDir, RoadCalibrationStore.DIR))
        }

        /** Paneli açar ([start] true: seçili ayarla hemen geri sayımı da başlatır). */
        fun launch(c: Context, start: Boolean, profile: String, level: Int?, seconds: Int?, mode: String, signalProfile: JSONObject) {
            require(permitted(c)) { "Hassas konum izni gerekli" }
            require(overlayPermitted(c)) { "Diğer uygulamaların üzerinde gösterme izni gerekli" }
            val i = Intent(c, RoadCalibrationService::class.java).putExtra("signalProfile", signalProfile.toString()).putExtra("start", start)
                .putExtra("qualityLevel", level ?: 0).putExtra("seconds", seconds ?: 0).putExtra("mode", mode).putExtra("profile", profile)
            c.startForegroundService(i)
        }

        fun finish() { instance?.command("finish") }
        fun hide() { instance?.command("hide") }
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var thread: HandlerThread
    private lateinit var work: Handler
    private lateinit var sensors: SensorManager
    private var locations: LocationManager? = null
    private var overlay: RoadCalibrationOverlay? = null
    private var wake: PowerManager.WakeLock? = null
    private var gravity: List<Double>? = null
    private var gravityAt = 0L
    private var fallbackGravity = DoubleArray(3)
    private var fallbackSamples = 0
    private var hasGravity = false
    private var lastSensorNs = 0L
    private var lastFix: CalibrationFix? = null
    private var priorFix: CalibrationFix? = null
    private var engine: RoadCalibration? = null
    private var road = RoadCalibrationRoadRms()
    private var signalProfile = JSONObject()
    private var roadLinear = true
    private var roadUp: DoubleArray? = null
    private var roadOrientationAt = 0L
    private var roadGyro: Double? = null
    private var roadLocation: Location? = null
    private var roadDisplacementSpeed = 0.0
    private var roadWindowLocation: Location? = null
    private var roadWindowEnd = 0L
    private val roadRotation = FloatArray(9)
    private var inputsOn = false

    private val roadListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(roadRotation, e.values)
                    roadUp = doubleArrayOf(roadRotation[6].toDouble(), roadRotation[7].toDouble(), roadRotation[8].toDouble())
                    roadOrientationAt = e.timestamp
                }
                Sensor.TYPE_GRAVITY -> {
                    val n = sqrt(e.values.take(3).sumOf { it.toDouble() * it.toDouble() })
                    if (n > 8) { roadUp = DoubleArray(3) { e.values[it] / n }; roadOrientationAt = e.timestamp }
                }
                Sensor.TYPE_GYROSCOPE -> roadGyro = sqrt(e.values.take(3).sumOf { it.toDouble() * it.toDouble() })
                Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                    val fix = roadLocation ?: return
                    val fresh = e.timestamp - fix.elapsedRealtimeNanos in 0..3_000_000_000L
                    val stationary = fresh && fix.speed < .5f && roadDisplacementSpeed < .6
                    val up = roadUp.takeIf { e.timestamp - roadOrientationAt in 0..499_999_999L }
                    val w = road.feed(e.timestamp, DoubleArray(3) { e.values[it].toDouble() }, up, roadLinear, stationary, fix.speed.toDouble(), roadGyro) ?: return
                    val old = roadWindowLocation
                    val meters = if (old != null && roadWindowEnd == w.startNs && fresh) fix.distanceTo(old).toDouble() else 0.0
                    roadWindowEnd = w.endNs; roadWindowLocation = Location(fix)
                    road.window(w, meters)
                }
            }
        }
    }

    private val locationListener = LocationListener { l ->
        if (!l.hasSpeed() || !l.hasAccuracy()) return@LocationListener
        val f = CalibrationFix(l.elapsedRealtimeNanos, l.time, l.speed.toDouble(), l.latitude, l.longitude, l.accuracy.toDouble())
        if (SystemClock.elapsedRealtimeNanos() - f.ns !in 0..2_000_000_000L) return@LocationListener
        val oldRoad = roadLocation
        if (oldRoad == null || l.elapsedRealtimeNanos > oldRoad.elapsedRealtimeNanos) {
            roadDisplacementSpeed = if (oldRoad != null) l.distanceTo(oldRoad) / max(.01, (l.elapsedRealtimeNanos - oldRoad.elapsedRealtimeNanos) / 1e9) else 0.0
            roadLocation = Location(l)
        }
        priorFix = lastFix; lastFix = f; engine?.fix(f)
    }

    private var quality: String? = null
    private var level: Int? = null
    private var seconds = 0
    private var mode = "fixed"
    private var profile = "g3-default"
    private var phase = CalibrationPhase.IDLE
    private var deadline = 0L
    private var waitingOffset = false
    private var waitStartedAt = 0L
    private var startedAt = 0L
    private var runId = ""
    private var doneMessage = ""
    private var destroyed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        sensors = getSystemService(SensorManager::class.java)
        locations = getSystemService(LocationManager::class.java)
        thread = HandlerThread("weem-calibration").also { it.start() }
        work = Handler(thread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android: startForegroundService ile başlayan servis startForeground'a varmadan durursa uygulama kapanır (CrashGuardTest); bu yüzden her ret yolu önce buraya gelir.
        val nm = getSystemService(NotificationManager::class.java)
        val started = try {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Yol kalitesi kalibrasyonu", NotificationManager.IMPORTANCE_LOW))
            val open = packageManager.getLaunchIntentForPackage(packageName)?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
            val close = PendingIntent.getService(this, 1, Intent(this, RoadCalibrationService::class.java).putExtra("close", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            startForeground(
                NotificationIds.CALIBRATION_FGS,
                Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("WEEM · Yol kalitesi kalibrasyonu")
                    .setContentText("Yüzen panel hazır · dokunarak başlat").setContentIntent(open).setOngoing(true)
                    .addAction(Notification.Action.Builder(null, "Paneli kapat", close).build()).build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            true
        } catch (_: Exception) { false }
        if (!started) {
            stateFlow.value = CalibrationState(CalibrationPhase.ERROR, false, message = "Panel/ön plan servisi açılamadı; izinleri kontrol et")
            stopSelf(); return START_NOT_STICKY
        }
        if (intent == null || !permitted(this) || !overlayPermitted(this)) {
            stateFlow.value = CalibrationState(CalibrationPhase.ERROR, false, message = "Konum ve diğer uygulamaların üzerinde gösterme izni gerekli")
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
        }
        if (intent.getBooleanExtra("close", false)) { command("hide"); return START_NOT_STICKY }
        try {
            if (overlay == null) overlay = RoadCalibrationOverlay(this) { action, args -> command(action, args) }.also { it.show() }
        } catch (_: Exception) {
            stateFlow.value = CalibrationState(CalibrationPhase.ERROR, false, message = "Panel açılamadı; izinleri kontrol et")
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
        }
        val args = mapOf<String, Any?>("qualityLevel" to intent.getIntExtra("qualityLevel", 0), "seconds" to intent.getIntExtra("seconds", 0), "mode" to intent.getStringExtra("mode"), "profile" to intent.getStringExtra("profile"))
        val startNow = intent.getBooleanExtra("start", false)
        val sp = intent.getStringExtra("signalProfile")
        work.post {
            if (phase == CalibrationPhase.IDLE) {
                signalProfile = try { JSONObject(sp ?: "{}") } catch (_: Exception) { JSONObject() }
                configure(args)
            }
            if (startNow) start() else publish()
        }
        return START_NOT_STICKY
    }

    fun command(action: String, args: Map<String, Any?> = emptyMap()) {
        work.post {
            when (action) {
                "set" -> if (phase == CalibrationPhase.IDLE) { configure(args); publish() }
                "start" -> start()
                "finish" -> if (phase == CalibrationPhase.RECORDING || phase == CalibrationPhase.COUNTDOWN) finish(false)
                "hide" -> {
                    if (phase == CalibrationPhase.RECORDING || phase == CalibrationPhase.COUNTDOWN) finish(true)
                    main.post { stopSelf() }
                }
            }
        }
    }

    private fun configure(args: Map<String, Any?>) {
        (args["qualityLevel"] as? Number)?.toInt()?.takeIf { it in 1..10 }?.let { level = it; quality = calibrationQuality(it) }
        (args["seconds"] as? Number)?.toInt()?.takeIf { it in 1..600 }?.let { seconds = it }
        (args["mode"] as? String)?.takeIf { it == "fixed" || it == "variable" }?.let { mode = it }
        (args["profile"] as? String)?.takeIf { it.isNotBlank() }?.let { profile = it }
    }

    private fun start() {
        if (phase != CalibrationPhase.IDLE || level == null || quality == null || seconds !in 1..600) return
        if (sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null) { doneMessage = "İvmeölçer bulunamadı"; publish(); return }
        if (!inputsOn) startInputs()
        doneMessage = ""
        // E10c: faz "countdown", ama 3-2-1 yol RMS ofseti hazır olunca (en çok 15 sn) başlar.
        phase = CalibrationPhase.COUNTDOWN; waitingOffset = true; waitStartedAt = SystemClock.elapsedRealtime(); deadline = 0
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WEEM:calibration").apply { acquire((seconds + 20L) * 1000 + CALIBRATION_OFFSET_WAIT_MS) }
        publish(); work.removeCallbacks(tick); work.post(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (destroyed) return
            if (phase == CalibrationPhase.COUNTDOWN && waitingOffset) {
                calibrationCountdownDeadline(SystemClock.elapsedRealtime(), waitStartedAt, road.offsetReady)?.let { waitingOffset = false; deadline = it; vibrate() }
                publish(); work.postDelayed(this, 100); return
            }
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (phase == CalibrationPhase.COUNTDOWN) {
                if (remaining <= 0) {
                    phase = CalibrationPhase.RECORDING; startedAt = System.currentTimeMillis(); runId = UUID.randomUUID().toString()
                    val runStarted = SystemClock.elapsedRealtimeNanos()
                    engine = RoadCalibration(CalibrationConfig(quality!!, seconds, mode, profile, level), runStarted)
                    road.beginRun(runStarted)
                    priorFix?.let { engine?.fix(it) }; lastFix?.let { engine?.fix(it) }
                    deadline = SystemClock.elapsedRealtime() + seconds * 1000L
                } else vibrate()
            } else if (phase == CalibrationPhase.RECORDING && remaining <= 0) { finish(false); return }
            publish()
            if (phase == CalibrationPhase.RECORDING || phase == CalibrationPhase.COUNTDOWN) work.postDelayed(this, 100)
        }
    }

    private var lastVibrationSecond = -1

    private fun vibrate() {
        val number = ceil((deadline - SystemClock.elapsedRealtime()) / 1000.0).toInt()
        if (number == lastVibrationSecond) return
        lastVibrationSecond = number
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
        else { @Suppress("DEPRECATION") vibrator.vibrate(45) }
    }

    private fun finish(interrupted: Boolean) {
        work.removeCallbacks(tick); waitingOffset = false
        val run = engine ?: RoadCalibration(CalibrationConfig(quality!!, seconds, mode, profile, level), SystemClock.elapsedRealtimeNanos())
        val legacy = run.finish()
        val measured = road.finish(legacy.points, seconds)
        val result = legacy.copy(
            roadRms = measured.rms, roadRmsVersion = measured.version, roadValidSeconds = measured.seconds, roadMeters = measured.meters,
            roadWindows = measured.windows, roadRmsRejectionReason = measured.rejectionReason,
        )
        val elapsed = if (engine == null) 0.0 else min(seconds.toDouble(), (SystemClock.elapsedRealtimeNanos() - run.startedNs) / 1e9)
        engine = null; phase = CalibrationPhase.SAVING; publish()
        stopInputs()
        try {
            store(this).save(if (runId.isBlank()) UUID.randomUUID().toString() else runId, run.config, if (startedAt == 0L) System.currentTimeMillis() else startedAt, elapsed, result, interrupted)
            doneMessage = when {
                interrupted -> "Geçersiz kalibrasyon: kayıt kesildi"
                !result.valid -> "Geçersiz kalibrasyon: ${result.rejectionReason}"
                result.speedNotConstant -> "Hız sabit değildi · kırpıldı · RMS %.3f m/s²".format(java.util.Locale.US, result.rms)
                else -> "RMS %.3f m/s²".format(java.util.Locale.US, result.rms)
            }
            doneMessage += if (result.roadRms == null) " · ${result.roadRmsRejectionReason}" else " · Yol RMS %.3f m/s²".format(java.util.Locale.US, result.roadRms)
            phase = CalibrationPhase.DONE
        } catch (_: Exception) { phase = CalibrationPhase.ERROR; doneMessage = "Kayıt saklanamadı; depolamayı kontrol et" }
        wake?.let { if (it.isHeld) it.release() }; wake = null; publish()
        work.postDelayed({ phase = CalibrationPhase.IDLE; startedAt = 0; runId = ""; lastVibrationSecond = -1; publish() }, 3000)
    }

    private fun publish() {
        val remaining = calibrationCountdownRemaining(SystemClock.elapsedRealtime(), deadline.takeUnless { waitingOffset })
        val next = CalibrationState(phase, true, quality, seconds, level, mode, profile, remaining, phase == CalibrationPhase.COUNTDOWN && waitingOffset, doneMessage)
        stateFlow.value = next
        main.post { if (!destroyed) overlay?.render(next) }
    }

    @Suppress("MissingPermission")
    private fun startInputs() {
        road = RoadCalibrationRoadRms(signalProfile.optDouble("highPassHz", .7), signalProfile.optDouble("lowPassHz", 35.0), signalProfile.optDouble("impactThreshold", 6.0))
        roadUp = null; roadOrientationAt = 0; roadGyro = null; roadLocation = null; roadDisplacementSpeed = 0.0; roadWindowLocation = null; roadWindowEnd = 0
        val linear = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        roadLinear = linear != null
        (linear ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER))?.let { sensors.registerListener(roadListener, it, 10_000, 0, work) }
        (sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: sensors.getDefaultSensor(Sensor.TYPE_GRAVITY))?.let { sensors.registerListener(roadListener, it, 20_000, 0, work) }
        sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.registerListener(roadListener, it, 20_000, 0, work) }
        hasGravity = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY) != null
        sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sensors.registerListener(this, it, 20_000, 0, work) }
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(this, it, 20_000, 0, work) }
        val lm = locations
        if (lm == null || !runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, locationListener, thread.looper) }.isSuccess) {
            doneMessage = "GPS alınamadı; konum ayarlarını kontrol et"; publish()
        }
        inputsOn = true
    }

    private fun stopInputs() {
        sensors.unregisterListener(this); sensors.unregisterListener(roadListener)
        runCatching { locations?.removeUpdates(locationListener) }
        gravity = null; fallbackSamples = 0; lastSensorNs = 0; priorFix = null; lastFix = null; inputsOn = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_GRAVITY) { gravity = e.values.take(3).map { it.toDouble() }; gravityAt = e.timestamp; return }
        if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val a = e.values.take(3).map { it.toDouble() }
        if (!hasGravity) {
            val dt = if (lastSensorNs == 0L) .02 else (e.timestamp - lastSensorNs) / 1e9
            val alpha = exp(-max(.001, dt) / .8)
            for (i in 0..2) fallbackGravity[i] = if (fallbackSamples == 0) a[i] else alpha * fallbackGravity[i] + (1 - alpha) * a[i]
            fallbackSamples++
            if (fallbackSamples >= 50) { gravity = fallbackGravity.toList(); gravityAt = e.timestamp }
        }
        lastSensorNs = e.timestamp
        engine?.sample(e.timestamp, System.currentTimeMillis() - (SystemClock.elapsedRealtimeNanos() - e.timestamp) / 1_000_000, a, gravity.takeIf { e.timestamp - gravityAt in 0..500_000_000L })
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) { super.onConfigurationChanged(newConfig); overlay?.resize() }

    override fun onDestroy() {
        destroyed = true; instance = null; main.removeCallbacksAndMessages(null); overlay?.close(); overlay = null
        work.post {
            if (phase == CalibrationPhase.RECORDING || phase == CalibrationPhase.COUNTDOWN) finish(true)
            work.removeCallbacksAndMessages(null); stopInputs()
            wake?.let { if (it.isHeld) it.release() }; wake = null
            stateFlow.value = CalibrationState()
            thread.quitSafely()
        }
        super.onDestroy()
    }

}
