package com.kuryelog.logic.map.surface

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.calibration.RoadCalibrationReferences
import com.kuryelog.logic.map.input.tl
import java.util.Locale

/** Yol puanı satırlarının modeli: [lines] sırayla gösterilir; [prompt] doluysa dokununca [promptBand] bandında kalibrasyon açılır. */
class RoadScoreView(val lines: List<String>, val prompt: String?, val promptBand: Int?)

/**
 * Yüzey ve kalibrasyon ekranlarının metinleri (YolGo `surface_screen.dart` etiketleri, `road_score.dart` `RoadScoreDisplay`, TR/AZ). Çizim `:app`'te;
 * metin ve sıra burada (Android'siz test edilir).
 */
object SurfaceText {
    fun measurementLabel(state: MeasurementState, lang: Lang): String = when (state) {
        MeasurementState.CLOSED -> tl(lang, "Sipariş etabı dışında", "Sifariş mərhələsindən kənar")
        MeasurementState.RIDING -> tl(lang, "Yol ölçülüyor", "Yol ölçülür")
        MeasurementState.STOPPED -> tl(lang, "Durdu · ölçüm bekliyor", "Dayandı · ölçmə gözləyir")
        MeasurementState.WALKING -> tl(lang, "Yürüyüş · ölçüm durdu", "Piyada · ölçmə dayandı")
        MeasurementState.UNCERTAIN -> tl(lang, "Hareket doğrulanıyor", "Hərəkət yoxlanılır")
        MeasurementState.MANUAL_PAUSED -> tl(lang, "Ölçüm elle duraklatıldı", "Ölçmə əl ilə dayandırıldı")
    }

    fun surfaceLabel(label: SurfaceLabel, lang: Lang): String = when (label) {
        SurfaceLabel.UNKNOWN -> tl(lang, "Türü bilinmiyor", "Növü bilinmir")
        SurfaceLabel.SMOOTH -> tl(lang, "Düzgün yüzey", "Hamar səth")
        SurfaceLabel.ROUGH -> tl(lang, "Bozuk yüzey", "Nahamar səth")
        SurfaceLabel.POTHOLE -> tl(lang, "Çukur", "Çuxur")
        SurfaceLabel.PATCH -> tl(lang, "Yama", "Yamaq")
        SurfaceLabel.RAISED_ASPHALT -> tl(lang, "Kabarmış asfalt", "Qabarıq asfalt")
        SurfaceLabel.DIRT -> tl(lang, "Toprak", "Torpaq")
        SurfaceLabel.GRAVEL -> tl(lang, "Çakıl", "Çınqıl")
        SurfaceLabel.HUMP -> tl(lang, "Kasis", "Sürət maneəsi")
        SurfaceLabel.OBSTACLE -> tl(lang, "Engel", "Maneə")
    }

    fun passability(access: Passability, lang: Lang): String = when (access) {
        Passability.UNKNOWN -> tl(lang, "Geçiş bilinmiyor", "Keçid bilinmir")
        Passability.PASSABLE -> tl(lang, "Geçilebilir", "Keçmək olar")
        Passability.CLOSED -> tl(lang, "Kapalı · rota kullanmasın", "Bağlı · marşrut istifadə etməsin")
    }

    /** Hız aralıkları (profil bandı 0–3) ve doğrulama durumu. */
    val SPEED_BINS = listOf("2.5–8 km/h", "8–15 km/h", "15–22 km/h", "22+ km/h")

    fun qualityIntro(lang: Lang) = tl(
        lang,
        "Sokak puanı, sokağın 50 m parçalarının uzunluk ağırlıklı ortancasıdır (1–10, aynı hız bandındaki kalibrasyon referanslarından). " +
            "Puanlı ölçüm 3 parçadan ya da 200 m'den azsa \"ölçüm az\" yazar. Sokağa dokun: 50 m parçalar. Bu puan IRI değildir.",
        "Küçə balı küçənin 50 m hissələrinin uzunluğa görə çəkili medianıdır (1–10, eyni sürət aralığındakı kalibrasiya referanslarından). " +
            "Balı olan ölçmə 3 hissədən və ya 200 m-dən azdırsa \"ölçmə az\" yazılır. Küçəyə toxun: 50 m hissələr. Bu bal IRI deyil.",
    )

    fun km(m: Double): String = if (m < 1000) "${Math.round(m)} m" else "${String.format(Locale.US, "%.1f", m / 1000)} km"

    fun streetSubtitle(pieces: Int, meters: Double, lang: Lang) = "$pieces ${tl(lang, "parça", "hissə")} · ${km(meters)}"

    fun pieceSubtitle(s: SurfaceSummary, lang: Lang) =
        "${tl(lang, "güven", "inam")} ${Math.round(s.confidence * 100)}% · ${s.passes} ${tl(lang, "geçiş", "keçid")} · ${s.impacts} ${tl(lang, "darbe", "zərbə")}"

    fun pieceTitle(roadName: String, s: SurfaceSummary) = "$roadName · ${if (s.direction == 0) "→" else "←"} ${s.segmentIndex + 1}"

    /**
     * Bir ölçümün yol puanı satırları (`RoadScoreDisplay`): puan ya da "Referans bekliyor"; çelişen referanslar; doğrulanmış darbe uyarısı; farklı hız
     * bandı; referans dışı hız; eksik seviyeler (dokununca [RoadScoreView.promptBand] bandında kalibrasyon); hız bilinmiyorsa uyarı.
     */
    fun roadScore(
        references: RoadCalibrationReferences, rms: Double?, speedKmh: Double?, lang: Lang, excludingId: String? = null,
        showMissingLevels: Boolean = true, confirmedShock: Boolean = false,
    ): RoadScoreView {
        val referenceBand = references.roadReferenceBand(speedKmh, excludingId)
        val score = if (rms == null) null else references.score(rms, speedKmh, excludingId)
        val band = RoadCalibrationReferences.speedBand(speedKmh)
        // Outside the supported range, the action opens exactly the displayed fallback.
        val promptBand = band ?: referenceBand
        val conflicts = if (referenceBand == null) emptyList() else references.conflicts(referenceBand, excludingId)
        val label = when {
            score == null -> tl(lang, "Referans bekliyor", "Referans gözlənilir")
            score.value != null -> "${score.label}/10"
            score.betterThan != null -> tl(lang, score.label, "${score.betterThan} səviyyəsindən yaxşı")
            else -> tl(lang, score.label, "${score.worseThan} səviyyəsindən pis")
        }
        val shock = tl(lang, "⚠ Doğrulanmış darbe", "⚠ Təsdiqlənmiş zərbə")
        val lines = ArrayList<String>()
        if (conflicts.isEmpty()) lines += "${tl(lang, "Yol puanı", "Yol balı")}: $label${if (confirmedShock) " · $shock" else ""}"
        for (c in conflicts) {
            val rel = if (c.lowerRms == c.upperRms) tl(lang, "eşit", "bərabərdir") else tl(lang, "ters sırada", "tərs sıradadır")
            val rmsText = "RMS ${c.lower}: ${String.format(Locale.US, "%.3f", c.lowerRms)}, ${c.upper}: ${String.format(Locale.US, "%.3f", c.upperRms)} m/s²"
            lines += tl(lang, "${c.lower} ve ${c.upper} $rel — birini tekrar kalibre et ($rmsText)", "${c.lower} və ${c.upper} $rel — birini yenidən kalibrasiya et ($rmsText)")
        }
        if (conflicts.isNotEmpty() && confirmedShock) lines += shock
        if (referenceBand != null && referenceBand != band) {
            lines += tl(lang, "Farklı hız bandı · en yakın $referenceBand km/h referansı kullanıldı", "Fərqli sürət aralığı · ən yaxın $referenceBand km/h referansı istifadə edildi")
        }
        if (speedKmh != null && band == null) lines += tl(lang, "Hız referans bantları dışında (12,5–32,5 km/h)", "Sürət referans aralıqlarından kənardadır (12,5–32,5 km/h)")
        var prompt: String? = null
        if (showMissingLevels && promptBand != null) prompt = missingLevelsPrompt(references, promptBand, lang)
        if (speedKmh == null) lines += tl(lang, "Ölçüm hızı bilinmiyor; hız bandı seçilemedi.", "Ölçmə sürəti bilinmir; sürət aralığı seçilmədi.")
        return RoadScoreView(lines, prompt, if (prompt != null) promptBand else null)
    }

    /** "Şu seviyeleri kalibre et: 1, 2 · 20 ±2,5 km/h"; eksik seviye yoksa null. */
    fun missingLevelsPrompt(references: RoadCalibrationReferences, band: Int, lang: Lang): String? {
        val levels = references.missingLevels(band)
        if (levels.isEmpty()) return null
        return "${tl(lang, "Şu seviyeleri kalibre et", "Bu səviyyələri kalibrasiya et")}: ${levels.joinToString(", ")} · $band ±2,5 km/h"
    }
}
