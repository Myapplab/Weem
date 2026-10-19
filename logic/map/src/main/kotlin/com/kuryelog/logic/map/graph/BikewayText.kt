package com.kuryelog.logic.map.graph

import com.kuryelog.logic.core.Lang

/** Y15 bisiklet metinleri (MAP.md 5.1): eski harita uyarısı (`lib/routing/bikeways.dart:54–55`). */
object BikewayText {
    /** Harita dosyası E13 öncesindense (bisiklet etiketleri okunmadan kurulmuş) açık uyarı; değilse null. */
    fun mapWarning(g: RoadGraph?, lang: Lang): String? {
        if (g == null || g.hasBikewayData) return null
        return if (lang == Lang.AZ) {
            "Yaddaşdakı xəritədə velosiped yolu məlumatı yoxdur (köhnə xəritə). Tənzimləmələr → Əlavə tənzimləmələr → Xəritə məlumatları → Yüklə / yenilə."
        } else {
            "Kayıtlı harita bisiklet yolu bilgisi içermiyor (eski harita). Ayarlar → İlave ayarlar → Harita verisi → İndir / güncelle."
        }
    }
}
