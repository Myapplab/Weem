package com.kuryelog.app.map

import android.content.Context
import android.os.SystemClock
import com.kuryelog.app.core.Store
import com.kuryelog.logic.orders.dataset.DailyLogSecret
import com.kuryelog.logic.orders.dataset.DatasetContract
import com.kuryelog.logic.orders.dataset.DatasetFiles
import com.kuryelog.logic.orders.dataset.DatasetManual
import com.kuryelog.logic.orders.dataset.DatasetPack
import com.kuryelog.logic.orders.dataset.DatasetRecorder
import com.kuryelog.logic.orders.dataset.DatasetShifts
import com.kuryelog.logic.orders.dataset.DayLogSnapshot
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Teklif veri seti Android çalışma zamanı (Y19; MAP.md 13.6–13.8, 13.11): kök `files/TeklifVeriSeti/`, gün toplama `:core` kilidi altında dondurulan gün klasörü
 * kopyasından ([DayLogSnapshot.copyPrefix]) `rawstats/` + `feed-manifest.json` + mühür + ZIP (`WEEM_teklif_<klasör>.zip`). Tek kilit; gün sınırı telefon saat dilimi.
 */
class AppDatasetRuntime(private val ctx: Context) : DatasetRuntime {
    private val app = ctx.applicationContext
    private val root = File(app.filesDir, "TeklifVeriSeti")
    private val zone: ZoneId get() = ZoneId.systemDefault()

    override suspend fun list(): List<DatasetDayInfo> = synchronized(LOCK) {
        root.mkdirs()
        DatasetPack.list(root, zone, System.currentTimeMillis()).map { info(it) }
    }

    override suspend fun collect(day: String) {
        synchronized(LOCK) {
            root.mkdirs()
            val frozen = File(app.cacheDir, "dataset-source-${System.nanoTime()}")
            try {
                freeze(day, frozen)
                val secret = DailyLogSecret(File(app.noBackupFilesDir, "economics-day-secrets-v3")).forDay(day)
                DatasetPack.collect(root, frozen, day, secret, versionName(), versionCode(), zone, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos())
            } finally {
                frozen.deleteRecursively()
            }
        }
    }

    /** Gün klasörünün izinli dosyalarının açık tanıtıcıları ve boyları kilit altında alınır; kopya kilit dışında yalnız o boy kadar (yarım satır, sonraki eklemeler yok). */
    private fun freeze(day: String, target: File) {
        DatasetContract.date(day)
        check(target.mkdirs())
        val frozen = ArrayList<Triple<String, FileInputStream, Long>>()
        try {
            Store.locked(app) {
                check(!Store.importing)
                val source = File(Store.logsRoot(app), day)
                for (f in source.listFiles().orEmpty()) {
                    if (f.isFile && f.name in DayLogSnapshot.names && f.canonicalFile.parentFile == source.canonicalFile) frozen.add(Triple(f.name, FileInputStream(f), f.length()))
                }
            }
            for ((name, input, length) in frozen) File(target, name).outputStream().use { DayLogSnapshot.copyPrefix(input, it, length) }
        } finally {
            frozen.forEach { runCatching { it.second.close() } }
        }
    }

    /** Paylaşmadan önce yeniden mühür (seçilen ekler ve canlı satırlarla hash'ler uyuşsun); ZIP dosyasını döner. */
    fun reseal(day: String): File = synchronized(LOCK) {
        DatasetContract.date(day)
        val dir = File(root, DatasetContract.folder(day))
        require(dir.isDirectory)
        File(DatasetPack.seal(root, dir, day, versionName(), versionCode(), zone, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos()).getString("zip"))
    }

    /** `shifts.jsonl`: vardiyanın değdiği her güne bir satır (açıkken bitiş null). */
    fun writeShift(startMs: Long, endMs: Long?) = synchronized(LOCK) {
        val row = DatasetShifts.row(startMs, endMs, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos()) ?: return@synchronized
        for (d in DatasetShifts.days(startMs, endMs, zone)) DatasetFiles.append(root, d, DatasetShifts.FILE, JSONObject(row.toString()))
    }

    /** Canlı teklif satırları (`yolgo-live.jsonl`). */
    fun appendLive(day: String, rows: List<JSONObject>) = synchronized(LOCK) {
        for (r in rows) DatasetFiles.append(root, day, DatasetRecorder.FILE, r)
    }

    /** Elle ekleme (MAP.md 13.9): yapılandırılmış satır ya da seçilen görüntünün opak kopyası + satırı. */
    fun addManual(day: String, row: JSONObject) = synchronized(LOCK) { DatasetFiles.append(root, day, DatasetManual.FILE, row) }

    fun copyScreenshot(day: String, kind: String, uri: android.net.Uri) {
        DatasetContract.date(day)
        require(kind in DatasetManual.SCREENSHOT_KINDS)
        val mime = app.contentResolver.getType(uri)
        require(mime?.startsWith("image/") == true)
        synchronized(LOCK) {
            val dir = DatasetFiles.day(root, day)
            val path = DatasetManual.attachmentPath(kind, java.util.UUID.randomUUID().toString(), mime)
            val temp = File(dir, ".image-${java.util.UUID.randomUUID()}.tmp")
            try {
                app.contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().buffered().use { input.copyTo(it) } }
                val file = File(dir, path)
                file.parentFile!!.mkdirs()
                check(temp.renameTo(file))
                DatasetFiles.append(root, day, DatasetManual.FILE, DatasetManual.screenshotRow(kind, path, mime!!, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos()))
            } finally {
                temp.delete()
            }
        }
    }

    private fun info(j: JSONObject): DatasetDayInfo {
        val offers = j.getJSONObject("offers")
        val m = j.getJSONObject("matching")
        fun ratio(k: String) = if (j.isNull(k)) null else j.getDouble(k)
        val missing = j.getJSONArray("missingManual")
        return DatasetDayInfo(
            day = j.getString("day"), folder = j.getString("folder"), wolt = offers.optInt("wolt"), bolt = offers.optInt("bolt"),
            matched = m.optInt("matched"), onlyDayLog = m.optInt("onlyRawstats"), onlyLive = m.optInt("onlyYolgo"),
            routeComputedRatio = ratio("routeComputedRatio"), emptyFieldRatio = ratio("emptyFieldRatio"), scooterCoverageRatio = ratio("scooterCoverageRatio"),
            collectionComplete = j.optBoolean("collectionComplete"), missingManual = (0 until missing.length()).map { missing.getString(it) },
        )
    }

    private fun versionName(): String = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty()

    @Suppress("DEPRECATION")
    private fun versionCode(): Long = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() } }.getOrDefault(0L)

    companion object {
        private val LOCK = Any()
    }
}

/**
 * Teklif veri seti kaydı (Y19; MAP.md 13.3–13.5, 13.11): vardiya açıkken saniyelik tik teklif durumunu [DatasetRecorder]'a verir (okuma/yoklama yok), satırlar
 * `yolgo-live.jsonl`'a eklenir; vardiya açılınca/kapanınca `shifts.jsonl` satırları, kapanışta her gün için toplama ve mühür (hata ötekini durdurmaz).
 */
object DatasetRecording {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    @Volatile private var started = false

    fun start(app: Context) {
        if (started) return
        started = true
        val rt = AppDatasetRuntime(app)
        val recorder = com.kuryelog.logic.orders.dataset.DatasetRecorder({ ZoneId.systemDefault() }, { MapSettingsRepo.settings(app).value.region })
        scope.launch {
            var openFor: Long? = null
            com.kuryelog.app.core.ShiftHub.state.collect { s ->
                val start = s.startedAtMs
                if (s.active && start != null && openFor != start) {
                    openFor = start
                    runCatching { rt.writeShift(start, null) }
                }
                if (!s.active) openFor = null
            }
        }
        scope.launch {
            while (isActive) {
                delay(1_000)
                if (!com.kuryelog.app.core.ShiftHub.state.value.active) continue
                runCatching {
                    val now = System.currentTimeMillis()
                    val offers = com.kuryelog.app.orders.Orders.flow(app).exportableOffers(now)
                    val fix = NavRuntime.get(app).lastFix?.takeIf { f -> now - f.atMs in -5_000..30_000 && f.accuracyM in 0.0..65.0 }
                    val courier = fix?.let { f -> com.kuryelog.logic.orders.dataset.DatasetContract.point(f.lat, f.lon) }
                    val (day, rows) = recorder.rows(offers, now, SystemClock.elapsedRealtimeNanos(), SystemClock.elapsedRealtime(), courier, com.kuryelog.app.orders.Orders.weather.current())
                    if (rows.isNotEmpty()) rt.appendLive(day, rows)
                }
            }
        }
    }

    /** Vardiya bitti (`ShiftMachine.Hooks.onEnd`): kapanış satırı her güne, sonra her gün için toplama (ayrı iş parçacığı; hata ötekini durdurmaz). */
    fun hooks(app: Context): com.kuryelog.logic.core.ShiftMachine.Hooks = object : com.kuryelog.logic.core.ShiftMachine.Hooks {
        override fun onEnd(startMs: Long, nowMs: Long, reason: String) {
            val rt = AppDatasetRuntime(app)
            Thread({
                runCatching { rt.writeShift(startMs, nowMs) }
                for (d in com.kuryelog.logic.orders.dataset.DatasetShifts.days(startMs, nowMs, ZoneId.systemDefault())) {
                    runCatching { kotlinx.coroutines.runBlocking { rt.collect(d) } }
                }
            }, "weem-dataset-end").start()
        }
    }
}
