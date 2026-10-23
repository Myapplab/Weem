package com.kuryelog.app.scooter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.scooter.NinebotDashboard
import com.kuryelog.logic.scooter.NinebotEventQueue
import com.kuryelog.logic.scooter.NinebotGpsPolicy
import com.kuryelog.logic.scooter.NinebotLogPolicy
import com.kuryelog.logic.scooter.ScooterStatusText
import com.kuryelog.logic.scooter.NinebotMetrics
import com.kuryelog.logic.scooter.NinebotMode
import com.kuryelog.logic.scooter.PackPresence
import com.kuryelog.logic.scooter.ScooterUiText
import com.kuryelog.logic.scooter.Timing
import com.kuryelog.logic.scooter.LogExport
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Scooter bağlantı ve kayıt servisi (SCOOTER.md 6.2, 7.1–7.3; NB `RecordingService.java` satır satır): GATT bağlantısını [BleLink] ile
 * çalıştırır, 1 Hz örneği enerji deposuna ([Scooter.energy]), sayaca ([Scooter.trip]) ve vardiya kaydına ([Scooter.sampleSink]) verir,
 * kayıt kipinde GPS + `ninebot-logger.db` yazar. Ön plan türü canlı kipte `connectedDevice`, kayıt kipinde `location|connectedDevice`.
 * Servis adı serbest (VERI_SOZLESMESI 2), tür aynı. `START_NOT_STICKY`: süreç ölürse kendini başlatmaz; vardiya sürerken
 * [ScooterShiftLink] yeniden açar. Cihazda ve scooter'da doğrulanmadı.
 */
class ScooterService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var bleThread: HandlerThread
    private lateinit var worker: Handler
    private lateinit var bleWorker: Handler
    private val main = Handler(Looper.getMainLooper())

    private var store: LogStore? = null
    private var ble: BleLink? = null
    private var attention: PairAttention? = null
    private var locations: LocationManager? = null
    private var last: Location? = null
    private var session = -1L
    private var startedNs = 0L
    private var fixes = 0

    @Volatile private var packets = 0

    @Volatile private var bleState = "Bluetooth bekleniyor"
    private var ending = false
    private var finished = false
    private var destroyed = false
    private var address: String? = null
    private var noticeSec = -1L
    private lateinit var eventQueue: NinebotEventQueue
    private var logPolicy: NinebotLogPolicy? = null
    private val droppedBleEvents = AtomicLong()
    private var reportedDrops = 0L
    private val gpsPolicy = NinebotGpsPolicy()
    private var gpsInterval = 1000L
    private var wake: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("logger-writer").also { it.start() }
        worker = Handler(thread.looper)
        eventQueue = NinebotEventQueue({ task -> worker.post(task) }, QUEUE_LIMIT)
        bleThread = HandlerThread("scooter-link").also { it.start() }
        bleWorker = Handler(bleThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && STOP == intent.action) {
            worker.post { finish() }
            return START_NOT_STICKY
        }
        // A10.4f (S18 b): "Şimdi dene" — boşta kalan bağlantıyı hemen yeniden açar; servis çalışmıyorsa bir şey yapmaz.
        if (intent != null && RETRY == intent.action) {
            val link = ble
            if (Scooter.active && link != null) bleWorker.post { link.retryNow() } else if (!Scooter.active) stopSelf()
            return START_NOT_STICKY
        }
        if (intent != null && RECORD == intent.action) {
            if (Scooter.active && !Scooter.recording && !Scooter.maintenance) worker.post { upgrade() } else if (!Scooter.active) stopSelf()
            return START_NOT_STICKY
        }
        val live = intent != null && intent.getBooleanExtra("live_only", false)
        // Every start below came from startForegroundService: it must reach startForeground even when refused, or Android closes
        // the whole app (CORE.md 4.1, CrashGuardTest). Callers already check these, so the refusals are only a safety net.
        if (Scooter.active) { refuseStart(running = true); return START_NOT_STICKY }
        // S11 (A8.3): 2 telefon modunda Provider rolünde scooter BLE hiç açılmaz (scooter Receiver telefonunda).
        if (ScooterShiftLink.providerRole()) { refuseStart(running = false); return START_NOT_STICKY }
        if (Scooter.maintenance && !live) { refuseStart(running = false); return START_NOT_STICKY }
        if (intent == null || !permissions(this, live)) { refuseStart(running = false); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        val lang = Store.lang(this)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, ScooterUiText.CHANNEL.get(lang), NotificationManager.IMPORTANCE_LOW))
        // Live-panel-only mode uses no location, so it runs as a connected-device service only.
        fgsType = if (live) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        startForeground(
            NotificationIds.SCOOTER_FGS, notification((if (live) ScooterUiText.N_CONNECTING_LIVE else ScooterUiText.N_STARTING).get(lang), live), fgsType,
        )
        // Both flags before the worker runs, so the toolbar never shows a new recording as a live panel.
        Scooter.active = true
        Scooter.recording = !live
        val device = intent.getStringExtra("device")
        val name = intent.getStringExtra("device_name")
        val pair = intent.getBooleanExtra("pair", false)
        val sweep = intent.getBooleanExtra("sweep", false)
        val sweepBoard = intent.getIntExtra("sweep_board", 0x16)
        val sweepFrom = intent.getIntExtra("sweep_from", 0x00)
        val sweepTo = intent.getIntExtra("sweep_to", 0xc0)
        worker.post { begin(device, name, pair, sweep, sweepBoard, sweepFrom, sweepTo, live) }
        return START_NOT_STICKY
    }

    private var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

    /**
     * A refused foreground start still calls startForeground: a running link re-posts its own notification with its type; a refused
     * fresh start shows the channel's notification for a moment and removes it. A failure here is only logged (no permission at all).
     */
    private fun refuseStart(running: Boolean) {
        val lang = Store.lang(this)
        try {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, ScooterUiText.CHANNEL.get(lang), NotificationManager.IMPORTANCE_LOW))
            startForeground(
                NotificationIds.SCOOTER_FGS, notification(ScooterUiText.N_CONNECTING_LIVE.get(lang), !Scooter.recording),
                if (running) fgsType else ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (e: RuntimeException) {
            Store.failure(this, "scooter_refused_start", e)
        }
        // The next tick re-posts the live text over this placeholder.
        lastNotice = null
        if (!running) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private fun begin(address: String?, name: String?, pair: Boolean, sweep: Boolean, sweepBoard: Int, sweepFrom: Int, sweepTo: Int, live: Boolean) {
        try {
            Scooter.warning = ""
            Scooter.hostLogging = false
            this.address = address
            startedNs = SystemClock.elapsedRealtimeNanos()
            Scooter.trip.reset("", System.currentTimeMillis())
            if (!live) openStorage()
            val mode = NinebotMode.GEN2_FW
            var sweepSpec: NinebotMetrics.Sweep? = null
            if (sweep) {
                try {
                    sweepSpec = NinebotMetrics.Sweep(sweepBoard, sweepFrom, sweepTo)
                } catch (e: RuntimeException) {
                    event("sweep_config_rejected", json("error", e.javaClass.simpleName, "board", sweepBoard, "from", sweepFrom, "to", sweepTo))
                }
            }
            attention = PairAttention(this) { kind, data -> event(kind, data) }
            event(
                "session_start",
                json(
                    "sdk", Build.VERSION.SDK_INT, "device_model", Build.MODEL, "version", LogExport.VERSION, "decoder_available", true, "device_verified", false,
                    "crypto_mode", mode.setting, "profile", 0, "sweep", sweepSpec != null, "sweep_board", sweepBoard, "sweep_from", sweepFrom, "sweep_to", sweepTo,
                    "wire_transcript_sensitive", true,
                ),
            )
            if (ending) return
            val pm = getSystemService(PowerManager::class.java)
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NinebotLogger:Recording").also { it.acquire(WAKE_MS) }
            if (!live) startGps()
            if (ending) return
            if (!address.isNullOrEmpty()) {
                val link = BleLink(
                    this, bleWorker,
                    object : BleLink.Sink {
                        override fun event(kind: String, data: JSONObject) = queueBleEvent(kind, data)
                        override fun frequent(kind: String, data: () -> JSONObject) = queueFrequent(kind, data)
                        override fun state(text: String) { bleState = text }
                        override fun warning(text: String) { Scooter.warning = text }
                        override fun dropped(on: Boolean) = ScooterShiftLink.droppedNotice(this@ScooterService, on)
                        override fun pairPrompt(visible: Boolean) { worker.post { if (!ending) attention?.set(visible) } }
                        override fun dashboard(frame: NinebotDashboard.Snapshot?) = publishFrame(frame)
                    },
                    Scooter.Viewers,
                )
                ble = link
                val selectedSweep = sweepSpec
                bleWorker.post { link.start(address, name, pair, mode, selectedSweep) }
            } else {
                bleState = "GPS kaydı • scooter seçilmedi"
            }
            if (!ending) worker.post(tick)
        } catch (e: RuntimeException) {
            fatal(e)
        }
    }

    /** SQLite session for a recording; the live-panel-only mode never opens it. */
    private fun openStorage() {
        val s = LogStore(this)
        store = s
        session = s.start(address)
        startedNs = SystemClock.elapsedRealtimeNanos()
        logPolicy = NinebotLogPolicy(LoggerPreferences.get(this).getBoolean("detailed_diagnostics", false), startedNs / 1_000_000L)
        Scooter.recording = true
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        val lm = getSystemService(LocationManager::class.java)
        locations = lm
        requestGps(1000)
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) event("gps_state", json("state", "GPS kapalı"))
    }

    /** Live panel → recording without dropping the scooter link: open storage and GPS, then add the location service type. */
    private fun upgrade() {
        if (ending || Scooter.recording) return
        try {
            openStorage()
            event(
                "session_start",
                json(
                    "sdk", Build.VERSION.SDK_INT, "device_model", Build.MODEL, "version", LogExport.VERSION, "decoder_available", true, "device_verified", false,
                    "upgraded_from_live_panel", true, "wire_transcript_sensitive", true,
                ),
            )
            startGps()
        } catch (e: RuntimeException) {
            fatal(e)
            return
        }
        main.post {
            if (!Scooter.active || ending) return@post
            try {
                startForeground(
                    NotificationIds.SCOOTER_FGS, notification("Kayıt açık", false),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } catch (e: RuntimeException) {
                Scooter.warning = "Kayıt açık, ancak arka planda konum izni verilmedi; GPS uygulama açıkken kaydedilir"
            }
        }
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (ending) return
            fixes++
            val now = SystemClock.elapsedRealtimeNanos()
            event("gps_fix", locationJson(location, now))
            val prev = last
            if (prev != null && location.elapsedRealtimeNanos < prev.elapsedRealtimeNanos) return
            val l = Location(location)
            last = l
            val age = Timing.ageMs(now, l.elapsedRealtimeNanos)
            val interval = if (age == null || age > 5000) 1000L else gpsPolicy.interval(
                now / 1_000_000L, if (l.hasSpeed()) l.speed.toDouble() else null, if (l.hasAccuracy()) l.accuracy.toDouble() else null,
                if (l.hasSpeedAccuracy()) l.speedAccuracyMetersPerSecond.toDouble() else null,
            )
            if (interval != gpsInterval) requestGps(interval)
        }

        override fun onProviderEnabled(p: String) {
            gpsPolicy.reset()
            if (gpsInterval != 1000L) requestGps(1000)
            event("gps_state", json("state", "enabled", "provider", p))
        }

        override fun onProviderDisabled(p: String) {
            last = null
            gpsPolicy.reset()
            event("gps_state", json("state", "disabled", "provider", p))
        }

        @Deprecated("Deprecated in Java")
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(p: String?, s: Int, extras: Bundle?) {}
    }

    private fun locationJson(l: Location, now: Long): JSONObject = json(
        "fix_wall_ms", l.time, "fix_mono_ns", l.elapsedRealtimeNanos, "gps_age_ms", Timing.ageMs(now, l.elapsedRealtimeNanos), "lat", l.latitude, "lon", l.longitude,
        "accuracy_m", if (l.hasAccuracy()) l.accuracy else null, "gps_speed_kmh", if (l.hasSpeed()) l.speed * 3.6 else null,
        "speed_accuracy_mps", if (l.hasSpeedAccuracy()) l.speedAccuracyMetersPerSecond else null,
        "bearing_accuracy_deg", if (l.hasBearingAccuracy()) l.bearingAccuracyDegrees else null, "bearing_deg", if (l.hasBearing()) l.bearing else null,
        "altitude_m", if (l.hasAltitude()) l.altitude else null, "mock", @Suppress("DEPRECATION") l.isFromMockProvider,
    )

    private val tick = object : Runnable {
        override fun run() {
            if (ending) return
            try {
                val link = ble
                if (link != null) bleWorker.post { link.updatePriority() }
                val dropped = droppedBleEvents.get()
                if (dropped > reportedDrops) {
                    reportedDrops = dropped
                    Scooter.warning = "Teşhis kuyruğu doldu: $dropped BLE ayrıntısı atlandı; GPS ve sürüş kaydı sürüyor"
                    event("log_backpressure", json("dropped_ble_events", dropped))
                }
                val now = SystemClock.elapsedRealtimeNanos()
                val lastFix = last
                val age = Timing.ageMs(now, lastFix?.elapsedRealtimeNanos)
                if (link != null) {
                    val sample = link.sample()
                    Scooter.energy.add(sample)
                    Scooter.trip.add(sample)
                    val sink = Scooter.sampleSink
                    if (sink != null && sample != null && !sample.empty()) {
                        var stored = false
                        try { stored = sink(sample) } catch (ignored: RuntimeException) { /* the host's log failure must not stop the scooter link */ }
                        Scooter.hostLogging = stored
                    }
                }
                Scooter.publishLive()
                // Live panel only (a WEEM shift): no SQLite session, so the per-second row with its telemetry JSON is not even built.
                if (store != null && session >= 0) {
                    event(
                        "sample",
                        json(
                            "elapsed_ms", (now - startedNs) / 1_000_000L, "gps", lastFix?.let { locationJson(it, now) }, "gps_fresh", age != null && age <= 5000,
                            "gps_request_interval_ms", gpsInterval, "ble_state", bleState, "scooter_telemetry", link?.telemetry(),
                        ),
                    )
                }
                if (Scooter.summaryWatched) {
                    val gps = if (lastFix == null) "GPS bekleniyor" else String.format(
                        Locale.US, "GPS %.5f, %.5f • ±%.0f m • %d sn önce", lastFix.latitude, lastFix.longitude, if (lastFix.hasAccuracy()) lastFix.accuracy else 0f,
                        if (age == null) -1 else age / 1000,
                    )
                    val minutes = (now - startedNs) / 60_000_000_000L
                    Scooter.summary = if (Scooter.recording) {
                        String.format(
                            Locale.US, "KAYIT AÇIK • %d dk\n%s\n%s\nGPS: %d ölçüm • BLE: %d paket\n%s", minutes, gps, bleState, fixes, packets,
                            if (link == null) "GPS kaydı • scooter seçilmedi" else link.measurements(),
                        )
                    } else {
                        String.format(
                            Locale.US, "CANLI PANEL • %s • %d dk\n%s\nBLE: %d paket\n%s", if (Scooter.hostLogging) "WEEM vardiyası kaydediyor" else "kayıt yok", minutes,
                            bleState, packets, if (link == null) "Scooter seçilmedi" else link.measurements(),
                        )
                    }
                }
                if (ending) return
                // Notification panel: live values every 2 s while the scooter answers, otherwise the link state every 10 s.
                val sec = (now - startedNs) / 1_000_000_000L
                val linked = Scooter.frame != null
                if (noticeSec < 0 || sec - noticeSec >= (if (linked) 2 else 10)) {
                    noticeSec = sec
                    val text = if (Scooter.warning.isNotEmpty()) Scooter.warning else if (linked) liveText()
                    else if (Scooter.recording) "GPS $fixes • BLE $packets • $bleState" else bleState
                    val liveMode = !Scooter.recording
                    main.post { postNotice(text, liveMode) }
                }
                val w = wake
                if (w != null && !w.isHeld) w.acquire(WAKE_MS)
                worker.postDelayed(this, 1000)
            } catch (e: RuntimeException) {
                fatal(e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestGps(interval: Long) {
        locations?.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval, 0f, listener, thread.looper)
        gpsInterval = interval
        event("gps_cadence", json("requested_interval_ms", interval))
    }

    private fun queueBleEvent(kind: String, payload: JSONObject) {
        val wall = System.currentTimeMillis()
        val mono = SystemClock.elapsedRealtimeNanos()
        if (kind == "ble_raw") packets++
        if (!Scooter.recording) return
        // Handshake TX (PRE_COMM/PAIR/AUTH) is rare and needed for pairing diagnosis; only READING TX is per-packet.
        val policyKind = if (kind == "protocol_tx" && !payload.optString("detail").contains("stage=READING")) "handshake_tx" else kind
        val policy = logPolicy
        if (policy != null && !policy.keep(policyKind, mono / 1_000_000L)) return
        // Only diagnostics are lossy. GPS/sample jobs use the writer directly and remain active.
        if (!eventQueue.post { appendEvent(wall, mono, kind, payload) }) droppedBleEvents.incrementAndGet()
    }

    /** Same accounting as [queueBleEvent] for per-packet kinds, without building rows the policy drops (normal mode). */
    private fun queueFrequent(kind: String, data: () -> JSONObject) {
        val wall = System.currentTimeMillis()
        val mono = SystemClock.elapsedRealtimeNanos()
        if (kind == "ble_raw") packets++
        if (!Scooter.recording) return
        val policy = logPolicy
        if (policy != null && !policy.keep(kind, mono / 1_000_000L)) return
        val payload = data()
        if (!eventQueue.post { appendEvent(wall, mono, kind, payload) }) droppedBleEvents.incrementAndGet()
    }

    private fun appendEvent(wall: Long, mono: Long, kind: String, payload: JSONObject) {
        val s = store
        if (s == null || session < 0) return
        try { s.event(session, wall, mono, kind, payload) } catch (e: RuntimeException) { fatal(e) }
    }

    private fun event(kind: String, payload: JSONObject) {
        if (ending || store == null || session < 0) return
        appendEvent(System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos(), kind, payload)
    }

    private fun fatal(e: RuntimeException) {
        Scooter.warning = "Kayıt durdu: depolama veya servis hatası. Ayarlar bölümünü kontrol et."
        Scooter.summary = "Kayıt durdu: " + e.javaClass.simpleName + ". Kaydedilen veriler korunur."
        finish()
    }

    private fun finish() {
        if (ending) return
        ending = true
        worker.removeCallbacks(tick)
        attention?.set(false)
        locations?.removeUpdates(listener)
        // Close on the link thread, then queue finalization behind every event it already emitted.
        val link = ble
        if (link != null) bleWorker.post { link.close(); worker.post { finishStorage() } } else finishStorage()
    }

    private fun finishStorage() {
        if (finished) return
        finished = true
        val s = store
        if (s != null) {
            try {
                if (session >= 0) {
                    s.event(
                        session, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos(), "log_summary",
                        json("dropped_ble_events", droppedBleEvents.get(), "omitted_diagnostic_events", logPolicy?.omitted ?: 0L),
                    )
                    s.end(session, if (Scooter.summary.startsWith("Kayıt durdu:")) "failed" else if (droppedBleEvents.get() > 0) "stopped_with_log_gaps" else "stopped")
                }
            } catch (ignored: RuntimeException) {
            }
            s.close()
        }
        val w = wake
        if (w != null && w.isHeld) w.release()
        publishFrame(null)
        Scooter.hostLogging = false
        if (!Scooter.summary.startsWith("Kayıt durdu:")) {
            Scooter.summary = if (store == null) "Canlı panel kapatıldı • kayıt yapılmadı" else "Kayıt tamamlandı • GPS $fixes • BLE $packets"
        }
        bleThread.quitSafely()
        if (destroyed) thread.quitSafely()
        // Clear the flag only after stopSelf: a quick restart must reach a fresh service instance, not this one whose link thread just quit.
        main.post {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Scooter.recording = false
            Scooter.active = false
            Scooter.publishLive()
        }
    }

    /** "18.6 km/h • −783 W • 12.4 Wh • ort. 15.2 km/h": the notification's small gauge line. */
    private fun liveText(): String {
        val d = Scooter.frame
        val t = Scooter.trip.stats()
        val b = StringBuilder()
        if (d != null) b.append(NinebotDashboard.speed(d.speedKmh)).append(" km/h • ").append(NinebotDashboard.watts(d.powerW)).append(" W")
        if (t.wh != null) b.append(String.format(Locale.US, " • %.1f Wh", t.wh))
        if (t.avgMovingKmh != null) b.append(String.format(Locale.US, " • %s %.1f km/h", ScooterUiText.AVG_SHORT.get(Store.lang(this)), t.avgMovingKmh))
        if (d != null && d.batteryPct != null) b.append(" • %").append(NinebotDashboard.whole(d.batteryPct))
        return b.toString()
    }

    /** Main thread: repost the ongoing notification only when its text or mode changed (each post is a system call). */
    private var lastNotice: String? = null

    private fun postNotice(text: String, live: Boolean) {
        if (!Scooter.active) return
        val key = "$live|${Scooter.hostLogging}|$text"
        if (key == lastNotice) return
        lastNotice = key
        getSystemService(NotificationManager::class.java).notify(NotificationIds.SCOOTER_FGS, notification(text, live))
    }

    /** Built once per service instance (main thread): PendingIntent lookups are system calls, the notification is rebuilt every 2 s. */
    private var openIntent: PendingIntent? = null
    private var stopIntent: PendingIntent? = null

    private fun notification(raw: String, live: Boolean): Notification {
        if (stopIntent == null) {
            openIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            stopIntent = PendingIntent.getService(this, 1, Intent(this, ScooterService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val lang = Store.lang(this)
        // Durum metni kayıtta Türkçe kalır; bildirimde dil ayarına göre (G3, `ScooterStatusText`).
        val text = ScooterStatusText.localize(raw, lang)
        val title = (if (!live) ScooterUiText.N_RECORDING else if (Scooter.hostLogging) ScooterUiText.N_HOST else ScooterUiText.N_LIVE).get(lang)
        val b = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(openIntent).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setCategory(Notification.CATEGORY_NAVIGATION)
        // S37: bildirimde "Kayda geç" yok; kayıt yalnız vardiyayla başlar.
        return b.addAction(Notification.Action.Builder(null, ScooterUiText.N_STOP.get(lang), stopIntent).build()).build()
    }

    // ---- Kare yayını ve ikinci batarya duyurusu ----

    /** 0.22.0: say when the second (external) pack is connected; D12: only after the new state held for 60 s ([PackPresence]). */
    private fun announceSecondPack(frame: NinebotDashboard.Snapshot?) {
        if (frame == null) {
            synchronized(extPresence) { extPresence.linkDown(SystemClock.elapsedRealtime()) }
            return
        }
        val change = synchronized(extPresence) { extPresence.observe(frame.extBatteryPct != null, SystemClock.elapsedRealtime()) } ?: return
        val lang = Store.lang(this)
        val text = if (PackPresence.CONNECTED == change) ScooterUiText.PACK_ON.get(lang).format(Math.round(frame.extBatteryPct!!).toString()) else ScooterUiText.PACK_OFF.get(lang)
        main.post { try { Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show() } catch (ignored: RuntimeException) { } }
    }

    private fun publishFrame(frame: NinebotDashboard.Snapshot?) {
        announceSecondPack(frame)
        Scooter.frame = frame
        if (Scooter.frameListeners.isEmpty()) return
        main.post {
            if (Scooter.frame !== frame) return@post
            for (l in Scooter.frameListeners) l(frame)
        }
    }

    override fun onDestroy() {
        worker.post {
            destroyed = true
            finish()
            if (finished) thread.quitSafely()
        }
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null

    companion object {
        const val STOP = "com.kuryelog.app.scooter.STOP"
        const val RECORD = "com.kuryelog.app.scooter.RECORD"
        const val RETRY = "com.kuryelog.app.scooter.RETRY"
        const val CHANNEL = "recording"
        private const val QUEUE_LIMIT = 2048
        private const val WAKE_MS = 24 * 60 * 60 * 1000L
        private val extPresence = PackPresence()

        /** Start intent for the selected scooter: pair=explicit G3 pairing, live=panel only, nothing stored (S37: pairing no longer records). */
        fun startIntent(c: Context, pair: Boolean, live: Boolean): Intent {
            val p = LoggerPreferences.get(c)
            val name = p.getString("ble_name", (p.getString("device_name", "") ?: "").split(" • ")[0])
            return Intent(c, ScooterService::class.java).putExtra("device", p.getString("device", "")).putExtra("device_name", name).putExtra("pair", pair)
                .putExtra("live_only", live).putExtra("crypto_mode", NinebotMode.GEN2_FW.setting).putExtra("sweep", p.getBoolean("sweep", false))
                .putExtra("sweep_board", 0x16).putExtra("sweep_from", 0x00).putExtra("sweep_to", 0xc0)
        }

        /** A recording needs location and Bluetooth; the live panel only Bluetooth (location still gates BLE before Android 12). */
        @SuppressLint("ObsoleteSdkInt")
        fun permissions(c: Context, live: Boolean): Boolean {
            val location = c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val bluetooth = Build.VERSION.SDK_INT < 31 || c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            return bluetooth && (location || live && Build.VERSION.SDK_INT >= 31)
        }
    }
}
