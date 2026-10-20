package com.kuryelog.logic.map.weather

import com.kuryelog.logic.core.NotifyPolicy

/**
 * Günde bir hava bildirimi (`WeatherWorker.doWork`, MAP.md 10.8): hava kapalıysa, gün kabul edilmiyorsa, bugün zaten bildirildiyse ([NotifyPolicy.weatherDue]) ya da
 * bildirim izni yoksa bir şey yapmaz. İstek (önbellek kuralıyla) → metin → gün/ayar yeniden denetlenir → bildirim → `notifiedDay`/`notified`.
 *
 * @param permitted bildirim izni var ve bildirimler açık
 * @param az Azerice mi
 * @param post (başlık, kısa metin, büyük metin)
 */
class WeatherNotifier(
    private val source: WeatherSource,
    private val prefs: WeatherPrefs,
    private val accepts: (String) -> Boolean,
    private val permitted: () -> Boolean,
    private val post: (String, String, String) -> Unit,
) {
    enum class Result { SKIPPED, POSTED, FAILED }

    /** [day] gün kimliği. [Result.FAILED] gün hâlâ açıkken hata oldu (çağıran sınırlı sayıda yeniden dener). */
    suspend fun run(day: String, az: Boolean, nowMs: Long): Result {
        if (!accepts(day) || !prefs.enabled) return Result.SKIPPED
        if (!NotifyPolicy.weatherDue(prefs.notifiedDay, day, prefs.notified, nowMs)) return Result.SKIPPED
        if (!permitted()) return Result.SKIPPED
        return try {
            val text = WeatherNotifyText.text(source.fetch(false, day), az, nowMs)
            // İndirme sürerken kullanıcı kapattıysa ya da gün bittiyse bildirim gönderme.
            if (!prefs.enabled || !accepts(day)) return Result.SKIPPED
            post(WeatherNotifyText.title(az), text.lineSequence().drop(1).first(), text)
            prefs.notifiedDay = day
            prefs.notified = nowMs
            Result.POSTED
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            if (!accepts(day)) Result.SKIPPED else Result.FAILED
        }
    }

    companion object {
        /** Eski WorkManager: 2 saatte bir deneme, hatada en çok 3 kez yeniden. */
        const val PERIOD_MS = 2 * 3_600_000L
        const val MAX_ATTEMPTS = 3
    }
}
