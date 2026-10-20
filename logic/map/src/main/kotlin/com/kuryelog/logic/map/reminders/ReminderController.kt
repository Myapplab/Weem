package com.kuryelog.logic.map.reminders

import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.map.settings.MapPrefs
import org.json.JSONObject

/** Denetleyicinin dış dünyaya istekleri (Android `ReminderRuntime` uygular): bildirim 7100 + tür sırası, sürüş günlüğü olayı, titreşim. */
interface ReminderEvents {
    /** [dayId] önizlemede null. */
    fun show(item: ReminderItem, dayId: String?, preview: Boolean) {}
    fun cancel(kind: ReminderKind) {}
    fun logEvent(name: String, data: Map<String, Any?>) {}

    object None : ReminderEvents
}

/**
 * Hatırlatıcı ayarı + çalışan denetleyici (Dart `ReminderSettingsController`, `ReminderController`, MAP.md 13.16). Zamanlayıcıları (20 sn yoklama, 30 sn
 * gizleme) Android çağırır: [check], [hideIfExpired]. Durum: [active] (kart), [settings].
 */
class ReminderController(private val prefs: MapPrefs, private val clock: Clock, var events: ReminderEvents = ReminderEvents.None) {
    var settings: ReminderSettings = loadSettings()
        private set

    /** Ekranda gösterilen kart; null = yok. */
    var active: ActiveReminder? = null
        private set

    private var dayId: String? = null
    private var shiftStartMs: Long? = null
    private val last = HashMap<ReminderKind, Long>()
    private val snooze = HashMap<ReminderKind, Long>()

    init {
        for (s in prefs.getStringList(LAST_KEY).orEmpty()) {
            val i = s.indexOf(':')
            if (i <= 0) continue
            val ms = s.substring(i + 1).toLongOrNull() ?: continue
            ReminderKind.values().firstOrNull { it.key == s.substring(0, i) }?.let { last[it] = ms }
        }
    }

    private fun loadSettings(): ReminderSettings {
        var s = ReminderSettings.defaults
        prefs.getString(KEY)?.let { raw -> runCatching { s = ReminderSettings.fromJson(JSONObject(raw)) } }
        // Sayaç başlangıcı yoksa şimdi başlasın (açılır açılmaz hatırlatmasın).
        if (s.items.any { it.sinceMs == 0L }) {
            val now = clock.wallMs()
            s = ReminderSettings(s.items.map { if (it.sinceMs == 0L) it.copy(sinceMs = now) else it })
        }
        return s
    }

    /** Kapalıdan açığa geçişte ya da aralık/gün içinde/saatli değişince sayaç baştan başlar. */
    fun updateItem(kind: ReminderKind, change: (ReminderItem) -> ReminderItem) {
        val now = clock.wallMs()
        settings = ReminderSettings(
            settings.items.map { old ->
                if (old.kind != kind) old else change(old).let { n ->
                    val restart = (!old.enabled && n.enabled) || old.intervalMin != n.intervalMin || old.onlyShift != n.onlyShift || old.useTimes != n.useTimes
                    if (restart) n.copy(sinceMs = now) else n
                }
            },
        )
        prefs.setString(KEY, settings.toJson().toString())
    }

    /** Gün sinyali (gün motorundan): gün kimliği ve başlangıcı; gün kapanınca kart kalkar ve bildirimler iptal olur. */
    fun activeDay(id: String?, startedAtMs: Long?) {
        if (dayId == id) {
            shiftStartMs = startedAtMs
            return
        }
        dayId = id
        shiftStartMs = startedAtMs
        snooze.clear()
        if (id == null) {
            active = null
            ReminderKind.values().forEach { events.cancel(it) }
        }
    }

    /** Gün yeni açıldıysa ve açık hatırlatıcı varsa bildirim izni istenir (Android). */
    val wantsPermission: Boolean get() = settings.anyEnabled

    /** Periyodik kontrol (20 sn'de bir): bir denetimde en çok bir hatırlatıcı (su, kontrol, yemek sırasıyla). */
    fun check(nowMs: Long = clock.wallMs()) {
        if (active != null || dayId == null) return // ekrandaki kapanmadan yenisi gelmesin
        for (it in settings.items) {
            if (ReminderDue.due(it, nowMs, clock.zone(), last[it.kind], shiftStartMs, shiftActive = true, snoozeUntilMs = snooze[it.kind])) {
                fire(it, nowMs, record = true)
                return
            }
        }
    }

    private fun fire(it: ReminderItem, nowMs: Long, record: Boolean) {
        val token = dayId
        if (record && token == null) return
        if (record) {
            last[it.kind] = nowMs
            snooze.remove(it.kind)
            prefs.setStringList(LAST_KEY, last.map { (k, v) -> "${k.key}:$v" })
        }
        active = ActiveReminder(it, nowMs)
        events.show(it, if (record) token else null, preview = !record)
        events.logEvent("reminder", if (record) mapOf("k" to it.kind.key) else mapOf("k" to it.kind.key, "preview" to true))
    }

    /** 30 sn sonra (aynı gösterim hâlâ ekrandaysa) kart kalkar, bildirim iptal olur. */
    fun hideIfExpired(nowMs: Long = clock.wallMs()) {
        val a = active ?: return
        if (nowMs - a.atMs >= ReminderTiming.SHOW_FOR_MS) {
            active = null
            events.cancel(a.item.kind)
        }
    }

    /** "Tamam". */
    fun done() {
        val a = active ?: return
        active = null
        events.cancel(a.item.kind)
    }

    /** Kodda var, düğmesi yok. */
    fun snooze(minutes: Int = 10) {
        val a = active ?: return
        snooze[a.item.kind] = clock.wallMs() + minutes * 60_000L
        done()
    }

    /** Ayarlar → "Dene": hemen göster (zamanlamayı bozmaz). */
    fun preview(kind: ReminderKind) = fire(settings.of(kind), clock.wallMs(), record = false)

    companion object {
        const val KEY = "reminders.v1"
        const val LAST_KEY = "reminders.last.v1"
    }
}
