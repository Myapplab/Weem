package com.kuryelog.logic.map.roads

import com.kuryelog.logic.core.Lang
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** WEEM eki (MAP.md 9.8): Yollarım metinleri ve ters yön listesi sırası/durumu; Dart biçimleri (`fmtKm`, `fmtPct`). */
class RoadsTextTest {
    @Test
    fun `ters yön listesi orana göre azalan, eşitlikte geçişe göre, durum ve renk`() {
        val k = RoadKnowledge()
        k.wwStats(1).apply { passes = 4; problems = 2.5 } // %63 kaçınılıyor
        k.wwStats(2).apply { passes = 2; problems = 0.0 }
        k.wwStats(3).apply { passes = 5; problems = 0.0 }
        k.names[3] = "Üç"
        val rows = RoadsText.wwRows(Lang.TR, k, approved = setOf(1L, 3L, 4L), streetNames = mapOf(1L to "Bir"), thresholdPct = 50.0)
        assertEquals(listOf(1L, 3L, 2L, 4L), rows.map { it.way })
        assertEquals("4 geçiş · 2.5 sorun · kaçınılıyor", rows[0].subtitle)
        assertEquals("%63", rows[0].ratioText)
        assertEquals(WwTone.RED, rows[0].tone)
        assertEquals("Bir", rows[0].name)
        assertEquals("5 geçiş · 0 sorun · kullanılıyor", rows[1].subtitle)
        assertEquals("2 geçiş · 0 sorun · onay kaldırılmış", rows[2].subtitle)
        assertEquals("0 geçiş · 0 sorun · veri yok", rows[3].subtitle)
        assertEquals("—", rows[3].ratioText)
        assertEquals(WwTone.NONE, rows[3].tone)
        assertEquals("isimsiz yol", rows[3].name)
        assertEquals("0 keçid · 0 problem · məlumat yoxdur", RoadsText.wwRows(Lang.AZ, k, setOf(4L), emptyMap(), 50.0).last().subtitle)
    }

    @Test
    fun `uzunluk yüzde dur-kalk ve tarih biçimleri`() {
        assertEquals("999 m", RoadsText.km(999.4))
        assertEquals("2.0 km", RoadsText.km(1950.0))
        assertEquals("%78", RoadsText.pct(0.78))
        val st = WayStats().apply { m = 1500.0; stops = 3.0; passes = 2 }
        assertEquals("2.0 dur-kalk/km · 1.5 km sürüldü", RoadsText.rateSubtitle(Lang.TR, st))
        assertEquals("2.0 dur-qalx/km · silkələnmə ? · 1.5 km · 2 keçid", RoadsText.manualRow(Lang.AZ, st))
        assertEquals("Fiziksel ölçüm ayrıntıları Yollarım → Kalite bölümünde", RoadsText.rateSubtitle(Lang.TR, WayStats().apply { m = 40.0 }))
        val g = GhostMark(1, 0.0, 0.0, "", 1_790_000_000_000L, all = true)
        assertEquals("Sokağın tamamı · 21.09.2026", RoadsText.ghostRow(Lang.TR, g, ZoneOffset.ofHours(4)))
        assertEquals("Yol yok: isimsiz yol (tamamı) — rotaya girmez", RoadsText.ghostToast(Lang.TR, "", true))
        assertEquals(QualityTone.FAIR, RoadsText.tone(0.6))
    }
}
