package com.kuryelog.app.map

import android.content.Context
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.stats.DayDeletionPlan
import com.kuryelog.logic.map.stats.DayReportFile
import com.kuryelog.logic.map.stats.DeletionStep
import java.io.File

/**
 * Gün silme (`DayDeletion.delete`; MAP.md 12.9): aynı anda bir kez; açık gün o tarihteyse kapatılır (vardiya ve Siparişler sürer), rota temizlenir, sürüş kayıtları
 * boşaltılır, Y12 gün katkıları (A6.11: [deleteContributions]) silinir, silinen gün kimlikleri yazılır, o tarihin kayıtları silinir. Siparişler'in gün klasörü ve
 * kazanç kayıtları silinmez.
 */
class DayDeletionRuntime private constructor(private val app: Context) {
    private val clock = Clock.Jvm

    /** Y12 gün katkılarını siler, silinen gün kimliklerini döner (A6.11'de `RoadsRuntime` bağlanır). */
    @Volatile var deleteContributions: (String) -> Set<String> = { emptySet() }

    /** Y13 (A10.4b): o tarihin yüzey ölçümlerini siler, silinen gün kimliklerini döner (`NavRuntime.surface`). */
    @Volatile var deleteSurface: (String) -> Set<String> = { emptySet() }

    private var running = false

    /** Açık gün [date] tarihindeyse silme onayında "aktif gün durdurulacak" uyarısı gerekir mi. */
    fun activeOn(date: String): Boolean = DayRuntime.get(app).activeDay()?.startedAtMs?.let { com.kuryelog.logic.core.LogTime.day(it, clock.zone()) == date } == true

    @Synchronized
    fun delete(date: String) {
        if (running) return
        running = true
        try {
            val day = DayRuntime.get(app)
            val reports = reportsOf(date)
            val log = RideLogRuntime.get(app).writer
            var extra = emptySet<String>()
            for (step in DayDeletionPlan.plan(date, day.activeDay(), reports, clock.zone())) {
                when (step) {
                    is DeletionStep.EndActiveDay -> {
                        day.endDay()
                        NavRuntime.get(app).controller.clearRoute()
                    }
                    DeletionStep.FlushLogs -> log.flush()
                    is DeletionStep.DeleteContributions -> extra = runCatching { deleteContributions(step.date) }.getOrDefault(emptySet()) + runCatching { deleteSurface(step.date) }.getOrDefault(emptySet())
                    is DeletionStep.ForgetDay -> {
                        val writer = DayReportWriter.get(app)
                        val last = day.lastDay()
                        val lastIds = last?.takeIf { it.startedAtMs != null && com.kuryelog.logic.core.LogTime.day(it.startedAtMs!!, clock.zone()) == step.date }
                            ?.let { DayDeletionPlan.reportId(it) }
                        writer.forget(step.reportDayIds + extra + listOfNotNull(lastIds))
                        if (lastIds != null) day.forgetLastDay()
                    }
                    is DeletionStep.DeleteLogDay -> log.deleteDay(step.date)
                }
            }
        } finally {
            running = false
        }
    }

    private fun reportsOf(date: String): List<DayState> =
        File(app.filesDir, "map/logs").listFiles { f -> f.name.startsWith("${date}_") || f.name == "$date.gun.json" }.orEmpty()
            .filter { it.name.endsWith(".gun.json") }.mapNotNull { f -> runCatching { DayReportFile.decode(f.readText()) }.getOrNull() }

    companion object {
        @Volatile private var instance: DayDeletionRuntime? = null

        fun get(ctx: Context): DayDeletionRuntime = instance ?: synchronized(this) { instance ?: DayDeletionRuntime(ctx.applicationContext).also { instance = it } }
    }
}
