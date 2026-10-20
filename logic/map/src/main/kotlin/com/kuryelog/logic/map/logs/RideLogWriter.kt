package com.kuryelog.logic.map.logs

import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.map.settings.MapPrefs
import java.io.File

/**
 * Sürüş günlüğü yazıcısı (`RideLogger`, MAP.md 12.2–12.3): satırlar tamponlanır, [flush] ile `<dayDate>.jsonl` ve açık sürüş dosyasına eklenir; sürüş
 * olaylara göre açılıp kapanır (`ride.active.v1`); gün gece yarısı bölünmez (bütün satırlar günün başladığı takvim gününün dosyasına gider). İskelet (A6.2b).
 */
class RideLogWriter(private val dir: File, private val prefs: MapPrefs, private val clock: Clock) {
    var enabled: Boolean
        get() = TODO("A6.9")
        set(@Suppress("UNUSED_PARAMETER") value) = TODO("A6.9")

    /** [dayId] `yolgo-<ms>`; null = gün kapalı (açık sürüş kapanır, tampon boşalır). */
    fun setDay(dayId: String?): Unit = TODO("A6.9")

    /** `{k: "ev", e: ad, …veri}`; `route` önce açık sürüşü kapatır, sürüş açan/kapatan olay kümeleri 12.3. */
    fun event(name: String, data: Map<String, Any?> = emptyMap()): Unit = TODO("A6.9")

    /** Eşzamanlı boşaltmalar sırayı korur; hata olursa metin tamponun başına geri konur. */
    fun flush(): Unit = TODO("A6.9")

    fun records(): List<RideLogEntry> = TODO("A6.9")

    /** Etkin sürüş ve gün dosyası silinemez (`IllegalStateException("Aktif kayıt silinemez")`). */
    fun delete(entry: RideLogEntry): Unit = TODO("A6.9")
}
