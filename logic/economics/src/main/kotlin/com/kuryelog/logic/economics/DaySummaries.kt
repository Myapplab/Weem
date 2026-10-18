package com.kuryelog.logic.economics

import org.json.JSONObject
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*
import java.io.File

/**
 * A4.2: gün sonu raporunu ayarlardan ve gün dosyalarından kurar (Siparişler sekmesi `OrdersSource.daySummary`, vardiya sonu
 * bildirimi). Her çağrıda ayar ve `economics-energy.jsonl` yeniden okunur (ayar değişince rapor hemen güncellenir). Saf: Android yok.
 */
class DaySummaries(private val log: LogStore, private val prefs: KuryelogPrefs.Values) {
    fun report(day: OrderHistory.Day): DayReport = DayReport.build(day, energyRows(day.date), NetSettings.read(prefs))

    fun summary(day: OrderHistory.Day, today: Boolean, tl: (String, String) -> String): DayNetSummary {
        val r = report(day)
        return DayNetSummary(DayReportCard.card(r, today, tl), r.energyCost)
    }

    /** O günün `economics-energy.jsonl` satırları; dosya yoksa ya da bozuk satırsa boş/atlanır, hata yutulur (rapor çizilmeli). */
    fun energyRows(date: String): List<JSONObject> = try {
        val f = File(log.dayDir(date), EnergySegments.FILE)
        if (!f.exists()) emptyList() else f.useLines { s -> s.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }.toList() }
    } catch (e: Exception) { emptyList() }
}
