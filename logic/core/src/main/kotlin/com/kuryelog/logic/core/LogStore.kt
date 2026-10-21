package com.kuryelog.logic.core

import org.json.JSONObject
import java.io.File
import java.time.ZoneId

/**
 * Gün klasörlerine satır yazma (CORE.md 5.2, RS `Store.kt:28-88`): `logs/YYYY-MM-DD/<ad>`. Bütün dosyalar için **tek kilit**;
 * yazma hatası fırlatmaz (false, [lastError]). Android'deki `Store` bunun tek örneğini tutar.
 *
 * @param writeErrorPrefix "Kayıt yazılamadı: " / "Qeyd yazılmadı: " (kullanıcının dili).
 * @param dayWritten bir gün dosyasına satır yazıldı (gün, ad); WEEM'de `:map` Y19 dinler (A6), Rawstats'ta YolGo v3 duyurusuydu.
 */
class LogStore(
    val logsRoot: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val writeErrorPrefix: () -> String = { "Kayıt yazılamadı: " },
    private val dayWritten: (day: String, name: String) -> Unit = { _, _ -> },
) : FailureSink {
    /** `events.jsonl` satırlarındaki son konum (`loc`); vardiya servisi yazar (A2.4). [speedMs]: fix'in kendi hızı (m/s; Rawstats
     *  `Location.hasSpeed()`/`speed`, ekonomi `courierState.speedKmh`), yoksa null. */
    data class Loc(val lat: Double, val lon: Double, val accM: Double, val atMs: Long, val speedMs: Double? = null)

    @Volatile var lastError: String? = null
    @Volatile var lastLocation: Loc? = null

    fun ts(t: Long = now()): String = LogTime.ts(t, zone())
    fun day(t: Long = now()): String = LogTime.day(t, zone())
    fun dayDir(day: String = day()): File = File(logsRoot, LogTime.checkedDay(day))

    /**
     * [line] + `\n` [day] (verilmezse bugünün) klasörüne eklenir; yeni kayıt günü ve dosyayı arşivden çıkarır (`.archived`,
     * `.archived-<ad>` silinir). Gün geçersizse ya da yazılamazsa false.
     */
    @Synchronized fun append(name: String, line: String, day: String? = null): Boolean = try {
        val dir = dayDir(day ?: day()).apply { mkdirs() }
        File(dir, name).appendText(line + "\n")
        File(dir, ".archived").delete()
        File(dir, ".archived-$name").delete()
        try { dayWritten(dir.name, name) } catch (_: Exception) {}
        true
    } catch (e: Exception) {
        lastError = writeErrorPrefix() + e.message
        false
    }

    /** `events.jsonl`: [extra] + `t`, `type` ve son konum varsa `loc {lat, lon, acc, age_s}`. */
    fun event(type: String, extra: JSONObject? = null): Boolean {
        val o = extra ?: JSONObject()
        o.put("t", ts())
        o.put("type", type)
        locJson()?.let { o.put("loc", it) }
        return append("events.jsonl", o.toString())
    }

    fun locJson(): JSONObject? {
        val l = lastLocation ?: return null
        return JSONObject().put("lat", l.lat).put("lon", l.lon).put("acc", l.accM).put("age_s", (now() - l.atMs) / 1000)
    }

    /**
     * Çökmeyen hata: [lastError] ve `errors.jsonl` satırı `{t, source, error}` (CORE.md 4.2). `errors.jsonl` tam yedeğe girer: mesaj
     * [Phones.safeMessage] ile kısaltılır ve telefonu gizlenir (A10.2).
     */
    override fun failure(source: String, e: Throwable) {
        val error = "$source: ${e.javaClass.simpleName}: ${Phones.safeMessage(e.message)}"
        lastError = error
        append("errors.jsonl", JSONObject().put("t", ts()).put("source", source).put("error", error).toString())
    }
}
