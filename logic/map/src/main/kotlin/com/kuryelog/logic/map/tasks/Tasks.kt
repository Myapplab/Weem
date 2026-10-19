package com.kuryelog.logic.map.tasks

import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.settings.MapPrefs
import org.json.JSONObject
import java.time.ZoneId

/** Günlük iş (Dart `DailyTask`, MAP.md 13.12): [dueMs] vakit; aynı takvim günü ve vakit geçince "uygun". */
data class DailyTask(val id: String, val title: String, val place: String, val point: GeoPoint, val dueMs: Long, val completedAtMs: Long? = null) {
    fun eligible(nowMs: Long, zone: ZoneId): Boolean = TODO("A6.9")
    fun complete(nowMs: Long): DailyTask = copy(completedAtMs = nowMs)

    fun toJson(): JSONObject = TODO("A6.9")

    companion object {
        /** Geçersiz konum `FormatException`. */
        fun fromJson(j: JSONObject): DailyTask = TODO("A6.9")
    }
}

/**
 * Konum tetikleyici (Dart `TaskTrigger`, MAP.md 13.14): 1000 m girer, 1200 m çıkar (histerezis); her teslimat ayrı fırsat; kapatma yalnız mevcut gösterimi
 * susturur. Fix geçerliliği: doğruluk 0–65 m, yaş −5…+30 sn. İskelet (A6.2b): gövdeler A6.9'da.
 */
class TaskTrigger(private val zone: ZoneId) {
    fun restoreDismissed(id: String, atMs: Long): Unit = TODO("A6.9")
    fun dismiss(id: String, nowMs: Long): Unit = TODO("A6.9")
    fun remove(id: String): Unit = TODO("A6.9")

    fun evaluate(
        tasks: List<DailyTask>,
        point: GeoPoint,
        nowMs: Long,
        fixAtMs: Long,
        accuracy: Double,
        deliveryFinished: Boolean,
        activeOrder: Boolean = false,
    ): List<DailyTask> = TODO("A6.9")

    companion object {
        const val ENTER_M = 1000.0
        const val EXIT_M = 1200.0
    }
}

/** "WEEM navigasyonu hedefi kabul etti mi" (S9: "Google Maps açıldı mı" yerine; sahte navigasyon kapısı testte). */
fun interface TaskNavGate {
    fun open(task: DailyTask): Boolean
}

/** Görev listesi + bildirimler (Dart `TaskController`): `tasks.v1` tercihi; rota açılırsa görev tamamlanır, açılmazsa bekler. İskelet (A6.2b). */
class TaskController(private val prefs: MapPrefs, private val clock: Clock, private val gate: TaskNavGate) {
    val tasks: List<DailyTask> get() = TODO("A6.9")

    /** Konum tetiğinin ürettiği, ekranda bekleyen görev uyarıları. */
    val alerts: List<DailyTask> get() = TODO("A6.9")

    fun add(task: DailyTask): Unit = TODO("A6.9")

    /** Hedef kabul edilirse görev tamamlanır ve true döner. */
    fun openRoute(task: DailyTask): Boolean = TODO("A6.9")

    /** Teslimat bitince (konum son fix): yakındaki uygun görevler uyarı olur (Y20). */
    fun deliveryFinished(point: GeoPoint, accuracy: Double, fixAtMs: Long): Unit = TODO("A6.9")
}
