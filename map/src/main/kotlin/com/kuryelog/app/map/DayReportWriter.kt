package com.kuryelog.app.map

import android.content.Context
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.DayStep
import com.kuryelog.logic.map.stats.DayHistory
import com.kuryelog.logic.map.stats.DayReportFile
import java.io.File
import java.util.concurrent.Executors

/**
 * Gün raporu (`.gun.json`; MAP.md 12.6): `files/map/logs/<gün>_<HHmm>.gun.json`; gün sonunda ve ışık cevabında sıralı yazılır (silinmiş günün raporu yazılmaz), geçmiş
 * en yeni 60 rapor + süren gün + son gün. Yazım `.tmp` + `rename`; hata sessiz.
 */
class DayReportWriter private constructor(private val app: Context) {
    private val dir = File(app.filesDir, "map/logs")
    private val prefs = MapPrefsStore(app)
    private val clock = Clock.Jvm
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "weem-day-report").also { it.isDaemon = true } }

    init {
        DayRuntime.get(app).addStepListener(::onStep)
    }

    /** Silinen günlerin kimlikleri (`shift.deleted.days.v2`). */
    fun deleted(): Set<String> = prefs.getStringList(DELETED_KEY).orEmpty().toSet()

    /** Gün silme: kimlikler eklenir. */
    fun forget(ids: Set<String>) {
        if (ids.isEmpty()) return
        prefs.setStringList(DELETED_KEY, (deleted() + ids).toList())
        waitIdle()
    }

    private fun onStep(step: DayStep) {
        val types = step.events.map { it.type }
        val day = DayRuntime.get(app)
        if ("day_end" in types) day.lastDay()?.let { write(it) }
        if ("light_answer" in types) {
            day.lastDay()?.let { write(it) }
            day.activeDay()?.let { write(it) }
        }
    }

    /** Raporu sıralı yazar; gün silinmişse (başta ve dosya hazırlandıktan sonra) yazılmaz. */
    fun write(day: DayState) {
        val snapshot = DayState.fromJson(day.toJson())
        val learned = DayRuntime.get(app).lightsConfirmed()
        io.execute {
            runCatching {
                val id = snapshot.startedAtMs?.let { "yolgo-$it" }
                if (id != null && id in deleted()) return@execute
                dir.mkdirs()
                val now = clock.wallMs()
                val out = File(dir, DayReportFile.name(snapshot.startedAtMs, now, clock.zone()))
                val tmp = File(dir, out.name + ".tmp")
                tmp.writeText(DayReportFile.encode(snapshot, now, learned))
                if (id != null && id in deleted()) {
                    tmp.delete()
                    return@execute
                }
                if (!tmp.renameTo(out)) {
                    out.delete()
                    tmp.renameTo(out)
                }
            }
        }
    }

    /** Raporun dosyası (paylaşmadan önce [write] ve [waitIdle]). */
    fun file(day: DayState): File = File(dir, DayReportFile.name(day.startedAtMs, clock.wallMs(), clock.zone()))

    /** Bekleyen yazımlar bitene dek bekler. */
    fun waitIdle() {
        runCatching { io.submit {}.get() }
    }

    /** `logs/`'taki en yeni 60 rapor (ada göre azalan; okunamayan atlanır). */
    fun files(): List<DayState> {
        waitIdle()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".gun.json") }.orEmpty().sortedByDescending { it.name }.take(DayHistory.MAX_FILES)
        return files.mapNotNull { f -> runCatching { DayReportFile.decode(f.readText()) }.getOrNull() }
    }

    /** Gün kayıtları (süren gün + en yeni 60 rapor + son gün), en yenisi önce. */
    fun history(): List<DayState> {
        val day = DayRuntime.get(app)
        return DayHistory.merge(day.activeDay(), files(), day.lastDay())
    }

    companion object {
        const val DELETED_KEY = "shift.deleted.days.v2"

        @Volatile private var instance: DayReportWriter? = null

        fun get(ctx: Context): DayReportWriter = instance ?: synchronized(this) { instance ?: DayReportWriter(ctx.applicationContext).also { instance = it } }
    }
}
