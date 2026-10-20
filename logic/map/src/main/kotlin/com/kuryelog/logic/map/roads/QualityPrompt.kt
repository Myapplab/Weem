package com.kuryelog.logic.map.roads

import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.input.tl
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Açık "Bu yolun kalitesi?" kartı: [pct] 10–100, [touchedAtMs] son dokunuş (monoton ms). */
data class QualityAsk(val way: Long, val name: String, val pct: Int, val touchedAtMs: Long)

/**
 * "Bu yolun kalitesi yüzde kaç?" (`quality_prompt.dart`; MAP.md 9.4.1): Ses+ iki kez (sürerken) ya da ekrandan açılır. Açıkken Ses+ / Ses−
 * ±10 (10–100); her dokunuş sayacı yeniden kurar; [AUTO_SAVE_S] sn dokunulmazsa kaydedilir ([due], Android 1 sn'de bir çağırır).
 * Başlangıç `round(qualityOf(way) × 10) × 10` (grafsız: puan/istatistik yoksa 80). Kayıt: `rate` + orta titreşim + [onSaved] (yeniden rota).
 */
class QualityPrompt(private val book: RoadBook, private val clock: Clock) {
    private val flow = MutableStateFlow<QualityAsk?>(null)
    val state: StateFlow<QualityAsk?> get() = flow
    val current: QualityAsk? get() = flow.value

    @Volatile
    var onSaved: () -> Unit = {}

    @Synchronized
    fun open(way: Long, name: String, pct: Int? = null) {
        val start = pct ?: ((book.qualityOf(way) * 10).roundToInt() * 10)
        flow.value = QualityAsk(way, name, start.coerceIn(10, 100), clock.monoMs())
        book.haptic(RoadHaptic.HEAVY)
    }

    /** Ses tuşları: true → işlendi (kart açık). */
    @Synchronized
    fun onVolume(up: Boolean): Boolean {
        val p = flow.value ?: return false
        flow.value = p.copy(pct = (p.pct + if (up) 10 else -10).coerceIn(10, 100), touchedAtMs = clock.monoMs())
        book.haptic(RoadHaptic.SELECTION)
        return true
    }

    /** Çip dokunuşu (%20 · %40 · %60 · %80 · %100). */
    @Synchronized
    fun set(pct: Int) {
        val p = flow.value ?: return
        flow.value = p.copy(pct = pct.coerceIn(10, 100), touchedAtMs = clock.monoMs())
    }

    @Synchronized
    fun due() {
        val p = flow.value ?: return
        if (clock.monoMs() - p.touchedAtMs >= AUTO_SAVE_S * 1000L) save()
    }

    @Synchronized
    fun save() {
        val p = flow.value ?: return
        flow.value = null
        book.rate(p.way, p.pct, p.name)
        book.haptic(RoadHaptic.MEDIUM)
        onSaved()
    }

    @Synchronized
    fun cancel() {
        flow.value = null
    }

    companion object {
        const val AUTO_SAVE_S = 6
        val CHIPS = listOf(20, 40, 60, 80, 100)

        fun title(lang: Lang) = tl(lang, "Bu yolun kalitesi?", "Bu yolun keyfiyyəti?")
        fun hint(lang: Lang) = tl(lang, "Ses+ artır · Ses− azalt · 6 sn sonra kendiliğinden kaydedilir", "Səs+ artır · Səs− azalt · 6 san sonra özü yadda saxlayır")
        fun saveLabel(lang: Lang) = tl(lang, "Kaydet", "Yadda saxla")
        fun cancelLabel(lang: Lang) = tl(lang, "Vazgeç", "Ləğv et")
    }
}
