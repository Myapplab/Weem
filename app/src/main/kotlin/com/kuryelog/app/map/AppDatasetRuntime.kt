package com.kuryelog.app.map

import android.content.Context
import android.os.SystemClock
import com.kuryelog.app.core.Store
import com.kuryelog.logic.orders.dataset.DailyLogSecret
import com.kuryelog.logic.orders.dataset.DatasetContract
import com.kuryelog.logic.orders.dataset.DatasetPack
import com.kuryelog.logic.orders.dataset.DayLogSnapshot
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.time.ZoneId

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
