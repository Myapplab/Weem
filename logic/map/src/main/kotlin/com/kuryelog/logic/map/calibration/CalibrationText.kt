package com.kuryelog.logic.map.calibration

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.input.tl
import com.kuryelog.logic.map.surface.BakuTime
import com.kuryelog.logic.map.surface.RoadScoreView
import com.kuryelog.logic.map.surface.SurfaceText
import java.util.Locale

/** Bir kalibrasyon kaydı kartı: [title], [lines] sırayla; [score] doluysa (aynı profil, E10 kullanılabilir) yol puanı satırları da gösterilir. */
class RecordCardView(val title: String, val lines: List<String>, val score: RoadScoreView?)

/** Bir hız bandındaki bir kalite seviyesi satırı (tamamlanma işareti, başlık, medyan). */
class LevelRowView(val complete: Boolean, val title: String, val subtitle: String)

/**
 * Kalibrasyon ekranının metinleri (YolGo `road_calibration_screen.dart`; ekran metinleri Türkçe, yalnız "E10 için yeniden kalibre et" iki dilli).
 * Çizim `:app`; sıra ve içerik burada (Android'siz test edilir).
 */
object CalibrationText {
    private fun f3(v: Double?) = v?.let { String.format(Locale.US, "%.3f", it) } ?: "—"
    private fun f1(v: Double?) = v?.let { String.format(Locale.US, "%.1f", it) } ?: "—"

    fun modeLabel(m: CalibrationSpeedMode) = if (m == CalibrationSpeedMode.FIXED) "SABİT HIZ" else "DEĞİŞKEN HIZ"
    fun bandLabel(band: Int) = "$band ±2,5 km/h"

    fun recordCard(r: RoadCalibrationRecord, refs: RoadCalibrationReferences, activeProfile: String, lang: Lang): RecordCardView {
        val sameProfile = r.profile == activeProfile
        val comparison = sameProfile && r.roadUsable
        val title = "${if (r.isLegacy) "Eski kayıt" else "${r.level}/10"} · ${r.quality.label} · ${modeLabel(r.mode)}"
        val head = "${BakuTime.stamp(r.at)}\n${f1(r.elapsedSeconds)} / ${r.requestedSeconds} sn · geçerli veri ${f1(r.validSeconds)} sn\n" +
            "${f1(r.averageKmh)} km/h · RMS ${f3(r.rms)} m/s² · ${if (r.usable) "GEÇERLİ" else "GEÇERSİZ"}" +
            (if (r.speedNotConstant) "\nHız sabit değildi${if (r.isLegacy) "" else " · ±%10 dışı bölümler kırpıldı"}" else "") +
            (r.rejectionReason?.let { "\n$it" } ?: "") + (if (r.interrupted) "\nKayıt kesildi" else "") + (if (!sameProfile) "\nBaşka montaj profili" else "")
        val lines = ArrayList<String>()
        lines += head
        if (!r.roadUsable) lines += tl(lang, "E10 için yeniden kalibre et", "E10 üçün yenidən kalibrasiya et")
        r.roadRmsRejectionReason?.let { lines += it }
        if (r.roadRms != null) lines += "Yol RMS ${f3(r.roadRms)} m/s² · ${f1(r.roadValidSeconds)} sn"
        val score = if (comparison) SurfaceText.roadScore(refs, r.roadRms, r.averageKmh, lang, excludingId = r.id, showMissingLevels = false) else null
        return RecordCardView(title, lines, score)
    }

    fun levelRow(q: RoadQuality, refs: RoadCalibrationReferences, band: Int): LevelRowView = LevelRowView(
        refs.complete(q, band),
        "${q.label} · ${refs.count(q, band)}/3 kalibrasyon, sabit hız: ${if (refs.hasFixed(q, band)) "var" else "yok"}",
        "$band km/h RMS medyanı: ${f3(refs.median(q, band))} m/s²",
    )

    /** Sıralı referanslar oluşunca sınıf eşikleri; değilse açıklama. */
    fun thresholds(refs: RoadCalibrationReferences, band: Int): String =
        refs.thresholds(band)?.let { limits -> "$band km/h RMS eşikleri: ${limits.joinToString(" · ") { String.format(Locale.US, "%.2f", it) }} m/s²" }
            ?: "Aynı hız bandında Çok iyi < İyi < Orta < Kötü < Çok kötü referans sırası oluşunca sınıf eşikleri belirlenir."

    fun phaseTitle(s: CalibrationState): String = when (s.phase) {
        CalibrationPhase.COUNTDOWN -> if (s.waitingOffset) "Dur · GPS bekleniyor" else "${s.remaining} · Hazırlan"
        CalibrationPhase.RECORDING -> "Kalibrasyon başladı"
        CalibrationPhase.SAVING -> "Kalibrasyon kaydediliyor"
        else -> "Kalibrasyon bitti"
    }

    const val HOW_TO = "Sabit hızla sür. Başlatınca geri sayım sırasında GPS açıkken en az 2 saniye dur; yol RMS durağan ofseti hazırlanır. " +
        "Telefon gidonda sabit olmalı. Hızın koşunun medyan hızına göre ±%10 içinde kalan bölümü sayılır."
    const val LEVELS_HELP = "9–10 Çok iyi · 7–8 İyi · 5–6 Orta · 3–4 Kötü · 1–2 Çok kötü"
    const val TRIM_HELP = "Kırpılmış geçerli koşular sayılır; hız sabit değildi uyarılı kayıt sabit hız gerekliliğini karşılamaz."

    const val TITLE = "Yol Kalitesi Kalibrasyonu"
    const val FINISHED = "BİTMİŞ KALİBRASYONLAR"
    const val EMPTY = "Henüz bitmiş kalibrasyon yok."
    const val LEGACY_TITLE = "ESKİ KAYITLAR"
    const val LEGACY_HELP = "Yeni ölçeğe çevrilmez; 1–10 referanslarına katılmaz. Eski kayıtlar korunur."
    const val ADD = "KALİBRASYON EKLE"
    const val CUSTOM = "Özel süre"
    const val CUSTOM_RANGE = "1–600 saniye"
    const val PICK = "Seç"
    const val CANCEL = "Vazgeç"
    const val FINISH = "BİTİR"
    const val START = "BAŞLA"
    const val SHOW_BUBBLE = "Yüzen bubble göster"
    const val HIDE_BUBBLE = "Bubble kapat"
    const val DELETE = "Sil"

    fun profileLine(name: String) = "Profil: $name. Referanslar bu telefon/montaj profili için hesaplanır."
    fun levelLine(level: Int) = "$level/10 · ${RoadQuality.fromLevel(level).label}"
    fun secondsLabel(n: Int) = "$n sn"

    /** Hazır süreler (15/30/60/90 sn) dışındaki seçim "Özel: N sn" olarak gösterilir; seçilmemişse "Özel süre". */
    fun customLabel(seconds: Int?) = if (seconds != null && seconds !in PRESET_SECONDS) "Özel: $seconds sn" else CUSTOM
    fun remaining(seconds: Int) = "$seconds sn kaldı"
    fun failed(message: String?) = "İşlem tamamlanamadı: $message"

    val PRESET_SECONDS = listOf(15, 30, 60, 90)
}
