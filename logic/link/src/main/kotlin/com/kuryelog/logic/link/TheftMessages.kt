package com.kuryelog.logic.link

import com.kuryelog.logic.link.TheftGuard.ArmedBy
import com.kuryelog.logic.link.TheftGuard.Fix
import com.kuryelog.logic.link.TheftGuard.State
import com.kuryelog.logic.link.TheftGuard.Trigger
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** `TheftState` v1 (R → P, son durum; LINK.md 5.2): Receiver'daki [TheftGuard.Status]. Konum yalnız koordinat (S5'e uygun). */
data class TheftState(val status: TheftGuard.Status, val atMs: Long) {
    fun toJson(): JSONObject = JSONObject().put("schemaVersion", SCHEMA).put("state", status.state.key)
        .put("armedBy", status.armedBy?.key ?: JSONObject.NULL).put("sinceMs", status.sinceMs)
        .put("trigger", status.trigger?.key ?: JSONObject.NULL).put("atMs", atMs).apply {
            status.fix?.let { put("lat", it.lat).put("lon", it.lon).put("accuracyM", it.accuracyM).put("fixAtMs", it.atMs) }
        }

    companion object {
        const val SCHEMA = 1

        fun parse(j: JSONObject): TheftState? {
            if (j.opt("schemaVersion") != SCHEMA) return null
            val state = State.of(j.opt("state") as? String) ?: return null
            val since = (j.opt("sinceMs") as? Number)?.toLong() ?: return null
            val at = (j.opt("atMs") as? Number)?.toLong() ?: return null
            val lat = (j.opt("lat") as? Number)?.toDouble()
            val lon = (j.opt("lon") as? Number)?.toDouble()
            val acc = (j.opt("accuracyM") as? Number)?.toDouble()
            val fixAt = (j.opt("fixAtMs") as? Number)?.toLong()
            val fix = if (lat != null && lon != null && acc != null && fixAt != null && lat in -90.0..90.0 && lon in -180.0..180.0) Fix(lat, lon, acc, fixAt) else null
            return TheftState(TheftGuard.Status(state, ArmedBy.of(j.opt("armedBy") as? String), since, Trigger.of(j.opt("trigger") as? String), fix), at)
        }
    }
}

/** `TheftCommand` v1 (P → R, teslim zorunlu; LINK.md 5.2): Pixel'den "Park" ya da kapatma. Receiver aynı [commandId]'yi bir kez işler. */
data class TheftCommand(val commandId: String, val action: Action, val atMs: Long) {
    enum class Action(val key: String) {
        ARM("arm"), DISARM("disarm");
        companion object { fun of(k: String?): Action? = entries.firstOrNull { it.key == k } }
    }

    fun toJson(): JSONObject = JSONObject().put("schemaVersion", SCHEMA).put("commandId", commandId).put("action", action.key).put("atMs", atMs)

    companion object {
        const val SCHEMA = 1

        fun parse(j: JSONObject): TheftCommand? {
            if (j.opt("schemaVersion") != SCHEMA) return null
            val id = (j.opt("commandId") as? String)?.takeIf { it.isNotEmpty() } ?: return null
            val action = Action.of(j.opt("action") as? String) ?: return null
            val at = (j.opt("atMs") as? Number)?.toLong() ?: return null
            return TheftCommand(id, action, at)
        }
    }
}

/**
 * Poco'nun SMS zamanlayıcısı (LINK.md 5.3). Saf: [due] gönderilecek metni döndürür ve gönderilmiş sayar (gönderim hatasını Android
 * katmanı kaydeder). Numara burada yok; yalnız Poco ayarında.
 */
class TheftSms(private val zone: ZoneId = ZoneId.systemDefault(), private val cfg: Config = Config()) {
    data class Config(
        val linkDownMs: Long = 15_000,
        val movedM: Double = 50.0,
        val movedEveryMs: Long = 3 * 60_000L,
        val stillEveryMs: Long = 10 * 60_000L,
        val maxPerAlarm: Int = 20,
    )

    private var alarmSince: Long? = null
    private var linkDownSince: Long? = null
    private var sent = 0
    private var lastAt = 0L
    private var lastFix: Fix? = null

    val sentThisAlarm: Int get() = sent

    fun due(now: Long, s: TheftGuard.Status, linked: Boolean): String? {
        linkDownSince = if (linked) null else linkDownSince ?: now
        if (s.state != State.ALARM) {
            val closing = alarmSince != null && sent > 0
            alarmSince = null; sent = 0; lastFix = null
            return if (closing) CLOSED else null
        }
        if (alarmSince != s.sinceMs) { alarmSince = s.sinceMs; sent = 0; lastFix = null }
        if (sent >= cfg.maxPerAlarm) return null
        val down = linkDownSince ?: return null
        if (sent == 0) {
            if (now - down < cfg.linkDownMs) return null
        } else {
            val moved = s.fix != null && (lastFix == null || TheftGuard.metres(lastFix!!, s.fix) >= cfg.movedM)
            val every = if (moved) cfg.movedEveryMs else cfg.stillEveryMs
            if (now - lastAt < every) return null
        }
        sent++; lastAt = now; lastFix = s.fix ?: lastFix
        return text(s.fix)
    }

    fun text(f: Fix?): String {
        if (f == null) return "$PREFIX scooter hareket etti. Konum yok."
        val hm = DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(Instant.ofEpochMilli(f.atMs))
        val ll = String.format(Locale.US, "%.6f,%.6f", f.lat, f.lon)
        return "$PREFIX scooter hareket etti. Konum: https://maps.google.com/?q=$ll (±${f.accuracyM.toInt()} m, $hm)"
    }

    companion object {
        const val PREFIX = "WEEM ALARM:"
        const val CLOSED = "WEEM: alarm kapatıldı."
    }
}
