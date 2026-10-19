package com.kuryelog.logic.map.roads

import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.route.StreetPrefsStore
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.ui.Toasts

/** `files/map/roads/<anahtar>.json` okuma/yazma (MAP.md 9.10): atomik yazım Android `:map`'te, testte bellek içi. */
interface RoadFiles {
    fun read(name: String): String?
    fun write(name: String, text: String)
}

/** Açık "Ters yönde sorun yaşadın mı?" sorusu. */
class WwQuestion(val pass: WwPass)

class RoadState(
    val k: RoadKnowledge,
    val question: WwQuestion? = null,
    /** Onaylı sokak kümesi her değiştiğinde artar (harita katmanı yenilemesi). */
    val approvedRev: Int = 0,
    /** Elle puan girilince artar (yol kalitesi katmanı yenilemesi); otomatik kayıt artırmaz. */
    val statsRev: Int = 0,
)

/**
 * `RoadController`'ın Riverpod'suz çekirdeği (MAP.md 9.12): vardiya kapsamı, `onFix`, soru ve cevap, elle eylemler, kayıt politikası
 * (30 sn kısıtı, "aynıysa yazma", `onPause` zorla kayıt). İskelet (A6.2a): gövde A6.11'de.
 */
class RoadBook(
    private val files: RoadFiles,
    private val streets: StreetPrefsStore,
    private val toasts: Toasts,
    private val settings: () -> MapSettings,
    private val clock: Clock,
    private val lang: () -> Lang = { Lang.TR },
) {
    val state: RoadState get() = TODO("A6.11")

    /** Vardiya açıldı: öğrenme kapsamı gün kimliğine (`yolgo-<startedAtMs>`) bağlanır. */
    fun startDay(dayId: String): Unit = TODO("A6.11")

    fun onFix(f: LearnFix): Unit = TODO("A6.11")

    /** Ses+ = "evet, sorun" ([problem] true), Ses− = "hayır". Soru açıksa true. */
    fun answer(problem: Boolean): Boolean = TODO("A6.11")

    /** Elle kalite puanı (%): yol kalitesi katmanını yeniler ve `roads.rate.v1.json`'a yazar. */
    fun rate(way: Long, percent: Int, name: String = ""): Unit = TODO("A6.11")

    /** Uygulama arka plana geçti: zorunlu kayıt (katman sayacını artırmaz). */
    fun onPause(): Unit = TODO("A6.11")
}

/** Ses tuşu dağıtımı (MAP.md 9.5.2): yalnız A6.2a testlerinin gerektirdiği yüzey (soru açıkken Ses+/Ses−). */
class VolumeDispatch(private val book: RoadBook, private val clock: Clock) {
    fun onKey(up: Boolean): Unit = TODO("A6.11")
}

/** "Ters yönde sorun yaşadın mı?" kartının metinleri (TR/AZ birebir; MAP.md 9.6). İskelet (A6.2a). */
object WrongWayCardText {
    fun title(lang: Lang): String = TODO("A6.11")
    fun yes(lang: Lang): String = TODO("A6.11")
    fun no(lang: Lang): String = TODO("A6.11")
}
