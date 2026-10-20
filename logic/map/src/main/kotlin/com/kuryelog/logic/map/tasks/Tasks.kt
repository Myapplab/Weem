package com.kuryelog.logic.map.tasks

import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.settings.MapPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Günlük iş (Dart `DailyTask`, MAP.md 13.12): [dueMs] vakit; aynı takvim günü ve vakit geçince "uygun". */
data class DailyTask(val id: String, val title: String, val place: String, val point: GeoPoint, val dueMs: Long, val completedAtMs: Long? = null) {
    fun eligible(nowMs: Long, zone: ZoneId): Boolean =
        completedAtMs == null && nowMs >= dueMs && sameDay(nowMs, dueMs, zone)

    fun complete(nowMs: Long): DailyTask = copy(completedAtMs = nowMs)

    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("place", place)
        .put("point", JSONObject().put("lat", point.lat).put("lon", point.lon)).put("due", dueMs)
        .also { j -> completedAtMs?.let { j.put("completed", it) } }

    companion object {
        /** Geçersiz konum `IllegalArgumentException` (Dart `FormatException`): görev atlanır. */
        fun fromJson(j: JSONObject): DailyTask {
            val p = j.optJSONObject("point") ?: throw IllegalArgumentException("Invalid task location")
            val lat = p.optDouble("lat", Double.NaN)
            val lon = p.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite() || Math.abs(lat) > 90 || Math.abs(lon) > 180) throw IllegalArgumentException("Invalid task location")
            return DailyTask(
                id = j.getString("id"), title = j.getString("title"), place = j.getString("place"), point = GeoPoint(lat, lon), dueMs = j.getLong("due"),
                completedAtMs = if (j.has("completed") && !j.isNull("completed")) j.getLong("completed") else null,
            )
        }

        internal fun sameDay(a: Long, b: Long, zone: ZoneId): Boolean =
            Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
    }
}

/**
 * Konum tetikleyici (Dart `TaskTrigger`, MAP.md 13.12): 1000 m girer, 1200 m çıkar (histerezis); her teslimat ayrı fırsat; kapatma yalnız mevcut gösterimi
 * susturur. Fix geçerliliği: doğruluk 0–65 m, yaş −5…+30 sn.
 */
class TaskTrigger(private val zone: ZoneId) {
    private val inside = HashMap<String, Boolean>()
    private val dismissed = HashMap<String, Long>()
    private val queued = HashSet<String>()

    fun restoreDismissed(id: String, atMs: Long) { dismissed[id] = atMs }

    fun dismiss(id: String, nowMs: Long) {
        queued.remove(id)
        dismissed[id] = nowMs
    }

    fun remove(id: String) {
        inside.remove(id); dismissed.remove(id); queued.remove(id)
    }

    fun evaluate(
        tasks: List<DailyTask>,
        point: GeoPoint,
        nowMs: Long,
        fixAtMs: Long,
        accuracy: Double,
        deliveryFinished: Boolean,
        activeOrder: Boolean = false,
    ): List<DailyTask> {
        if (!validFix(point, accuracy, fixAtMs, nowMs)) return emptyList()
        val found = ArrayList<DailyTask>()
        for (task in tasks) {
            if (!task.eligible(nowMs, zone)) continue
            val distance = point.distanceTo(task.point)
            val before = inside[task.id]
            val now = if (distance <= ENTER_M) true else if (distance > EXIT_M) false else before ?: false
            inside[task.id] = now
            val closed = dismissed[task.id]
            val reentry = before == false && now && closed != null && DailyTask.sameDay(closed, nowMs, zone)
            if (distance <= ENTER_M && task.id !in queued && (deliveryFinished || (reentry && !activeOrder))) {
                queued += task.id
                found += task
            }
        }
        return found.sortedBy { it.dueMs }
    }

    companion object {
        const val ENTER_M = 1000.0
        const val EXIT_M = 1200.0

        fun validFix(p: GeoPoint, accuracy: Double, atMs: Long, nowMs: Long): Boolean {
            val ageS = (nowMs - atMs) / 1000
            return p.lat.isFinite() && p.lon.isFinite() && Math.abs(p.lat) <= 90 && Math.abs(p.lon) <= 180 &&
                accuracy.isFinite() && accuracy >= 0 && accuracy <= 65 && ageS >= -5 && ageS <= 30
        }
    }
}

/** "WEEM navigasyonu hedefi kabul etti mi" (S9: "Google Maps açıldı mı" yerine; sahte navigasyon kapısı testte). */
fun interface TaskNavGate {
    fun open(task: DailyTask): Boolean
}

/** Denetleyicinin dış dünyaya istekleri (Android `TaskRuntime` uygular): bildirim 7201 ve sürüş günlüğü olayı. */
interface TaskEvents {
    /** Yeni uyarılar: "Yakınında yapılacak iş var" (metin satırları "<başlık> · <yer>"). */
    fun alert(tasks: List<DailyTask>, dayId: String?) {}
    fun cancelAlert() {}
    fun logEvent(name: String) {}

    object None : TaskEvents
}

/** Görev listesi + uyarılar (Dart `TaskController`, MAP.md 13.13): `daily.tasks.v1` tercihi; rota açılırsa görev tamamlanır, açılmazsa bekler. */
class TaskController(private val prefs: MapPrefs, private val clock: Clock, private val gate: TaskNavGate) {
    var events: TaskEvents = TaskEvents.None
    private val trigger = TaskTrigger(clock.zone())
    private var taskList: List<DailyTask> = load()
    private var alertList: List<DailyTask> = emptyList()
    private var dayId: String? = null
    private var activeOrder = false

    /** "Rota Oluştur" sürerken ikinci basış yok sayılır. */
    var opening = false
        private set

    init {
        runCatching {
            val closed = JSONObject(prefs.getString(CLOSED_KEY) ?: "{}")
            for (id in closed.keys()) closed.optLong(id, -1).takeIf { it >= 0 }?.let { trigger.restoreDismissed(id, it) }
        }
    }

    val tasks: List<DailyTask> get() = taskList

    /** Konum tetiğinin ürettiği, ekranda bekleyen görev uyarıları. */
    val alerts: List<DailyTask> get() = alertList

    private fun load(): List<DailyTask> {
        val raw = prefs.getString(KEY) ?: return emptyList()
        val out = ArrayList<DailyTask>()
        runCatching {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) runCatching { out += DailyTask.fromJson(a.getJSONObject(i)) }
        }
        return out
    }

    private fun persist(list: List<DailyTask>) {
        prefs.setString(KEY, JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString())
    }

    /** Gün motorundan (11.14): gün kimliği; kapanınca uyarılar temizlenir. */
    fun activeDay(id: String?) {
        if (dayId == id) return
        dayId = id
        if (id == null) {
            alertList = emptyList()
            events.cancelAlert()
        }
    }

    fun activeOrder(value: Boolean) { activeOrder = value }

    /** Gün açıkken 30 sn'de bir (çağıran `TaskRuntime` gün kapalıyken çağırmaz): artık uygun olmayan uyarılar düşer. */
    fun expire() {
        val now = clock.wallMs()
        val keep = alertList.filter { it.eligible(now, clock.zone()) }
        if (keep.size == alertList.size) return
        alertList.filter { it !in keep }.forEach { trigger.remove(it.id) }
        alertList = keep
        if (keep.isEmpty()) events.cancelAlert()
    }

    /** Konum akışının her fix'i (teslimat dışı). */
    fun onFix(point: GeoPoint, accuracy: Double, fixAtMs: Long) = check(point, accuracy, fixAtMs, false)

    /** Teslimat bitince (konum son fix): yakındaki uygun görevler uyarı olur (Y20). */
    fun deliveryFinished(point: GeoPoint, accuracy: Double, fixAtMs: Long) = check(point, accuracy, fixAtMs, true)

    /** Gün kapalıyken çağrılmaz (`TaskRuntime` kapısı); [activeDay] yalnız uyarıları temizler ve bildirime gün kimliğini verir. */
    private fun check(point: GeoPoint, accuracy: Double, fixAtMs: Long, delivered: Boolean) {
        val now = clock.wallMs()
        val zone = clock.zone()
        val keep = alertList.filter { it.eligible(now, zone) }
        alertList.filter { !it.eligible(now, zone) }.forEach { trigger.remove(it.id) }
        val found = trigger.evaluate(taskList, point, now, fixAtMs, accuracy, delivered, activeOrder)
        if (found.isEmpty() && keep.size == alertList.size) return
        alertList = keep + found
        if (found.isNotEmpty()) events.alert(found, dayId)
    }

    fun add(task: DailyTask) {
        val next = (taskList + task).sortedBy { it.dueMs }
        persist(next)
        taskList = next
    }

    fun delete(id: String) {
        val next = taskList.filter { it.id != id }
        persist(next)
        trigger.remove(id)
        taskList = next
        alertList = alertList.filter { it.id != id }
    }

    /** "Tamam / Kapat": görev tamamlanmaz; sonraki teslimatta ya da bölgeye yeniden girince tekrar gösterilir. */
    fun dismiss(id: String) {
        val now = clock.wallMs()
        trigger.dismiss(id, now)
        alertList = alertList.filter { it.id != id }
        val closed = runCatching { JSONObject(prefs.getString(CLOSED_KEY) ?: "{}") }.getOrDefault(JSONObject())
        closed.put(id, now)
        prefs.setString(CLOSED_KEY, closed.toString())
        if (alertList.isEmpty()) events.cancelAlert()
    }

    /** Hedef kabul edilirse görev tamamlanır ve true döner. */
    fun openRoute(task: DailyTask): Boolean {
        if (opening) return false
        opening = true
        try {
            if (!gate.open(task)) return false
            val next = taskList.map { if (it.id == task.id) it.complete(clock.wallMs()) else it }
            persist(next)
            trigger.remove(task.id)
            taskList = next
            alertList = alertList.filter { it.id != task.id }
            events.logEvent("task_route_opened")
            if (alertList.isEmpty()) events.cancelAlert()
            return true
        } finally {
            opening = false
        }
    }

    companion object {
        const val KEY = "daily.tasks.v1"
        const val CLOSED_KEY = "daily.tasks.closed.v1"
    }
}
