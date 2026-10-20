package com.kuryelog.logic.map.stats

import com.kuryelog.logic.core.LogTime
import com.kuryelog.logic.map.day.DayState
import java.time.ZoneId

/** Gün silme adımı (MAP.md 12.9). */
sealed class DeletionStep {
    /** Açık gün bu tarihteyse: `DayController.endDay(reason = "manual")`, rota temizlenir. */
    data class EndActiveDay(val dayId: String?) : DeletionStep()

    /** Sürüş günlüğü tamponu boşaltılır. */
    object FlushLogs : DeletionStep()

    /** Y12 gün katkıları silinir (`RoadsRuntime.deleteContributions(date)`); dönen kimlikler [ForgetDay]'e eklenir. */
    data class DeleteContributions(val date: String) : DeletionStep()

    /** `forgetDay(date, ids)`: kimlikler silinen günlere eklenir, son gün o tarihteyse `shift.last` silinir. */
    data class ForgetDay(val date: String, val reportDayIds: Set<String>) : DeletionStep()

    /** O tarihin bütün sürüş kayıtları. */
    data class DeleteLogDay(val date: String) : DeletionStep()
}

/**
 * Gün silme sırası (`DayDeletion.delete`, MAP.md 12.9; WEEM: Y13 adımları yok, Siparişler'in gün klasörü ve kazanç kayıtları silinmez).
 */
object DayDeletionPlan {
    /** Rapor kimliği: `dayId`, yoksa `yolgo-<start>`; ikisi de yoksa null. */
    fun reportId(d: DayState): String? = d.startedAtMs?.let { "yolgo-$it" }

    /** [active] açık gün (varsa), [reports] o tarihin okunabilen rapor günleri. */
    fun plan(date: String, active: DayState?, reports: List<DayState>, zone: ZoneId): List<DeletionStep> {
        val steps = ArrayList<DeletionStep>()
        val activeStart = active?.startedAtMs
        if (active != null && active.active && activeStart != null && LogTime.day(activeStart, zone) == date) steps += DeletionStep.EndActiveDay(reportId(active))
        steps += DeletionStep.FlushLogs
        val ids = reports.mapNotNull { reportId(it) }.toSet()
        steps += DeletionStep.DeleteContributions(date)
        steps += DeletionStep.ForgetDay(date, ids)
        steps += DeletionStep.DeleteLogDay(date)
        return steps
    }
}
