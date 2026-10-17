package com.kuryelog.logic.capture

import java.time.OffsetDateTime
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import com.kuryelog.logic.capture.SahaFrames.WOLT
import com.kuryelog.logic.capture.SahaFrames.frames
import com.kuryelog.logic.capture.SahaFrames.expected
import com.kuryelog.logic.capture.SahaFrames.parse
import com.kuryelog.logic.capture.SahaFrames.isIdleFrame
import com.kuryelog.logic.capture.SahaFrames.isSplitFrame
import com.kuryelog.logic.capture.SahaFrames.cardEarning
import com.kuryelog.logic.capture.SahaFrames.changeAmount

/**
 * Saha tekrar testi (13 Ekim 2026 planı, F0.6). 11–12 Ekim'in 6.481 gerçek ekran karesi (Wolt 2.202.6, Bolt) parser'dan
 * geçirilir. Kareler kişisel veriden temizlendi (docs/SAHA_ANALIZI_2026-10-13.md §0); doğru cevaplar
 * `saha/saha_beklenen_2026-10-11_12.json`. Eski Wolt testleri eski metinlerle ("₼3.94") yazıldığı için saha bozulurken
 * yeşil kaldı; bu test sahanın kendisidir.
 *
 * Hedef testler ilgili madde düzelene kadar @Ignore'dur; madde bitince @Ignore kaldırılır (plan: docs/DUZELTME_PLANI_2026-10-13.md).
 */
class SahaTekrarTest {
    @Test fun karelerYuklenirVeKisiselVeriYok() {
        assertEquals(6481, frames.size)
        val phone = Regex("(\\+?994[\\s-]?\\(?\\d{2}\\)?[\\s-]?\\d{3}[\\s-]?\\d{2}[\\s-]?\\d{2})|(\\b0\\d{2}[\\s-]?\\d{3}[\\s-]?\\d{2}[\\s-]?\\d{2}\\b)")
        for (f in frames) for (t in f.texts + f.actions) {
            assertFalse("telefon: ${f.day} ${f.at}", phone.containsMatchIn(t))
            if (t.trim().endsWith(", Online")) assertEquals("Kurye K., Online", t.trim())
        }
    }

    private val baku = java.util.TimeZone.getTimeZone("Asia/Baku")
    private fun hm(at: Long?) = at?.let { java.time.Instant.ofEpochMilli(it).atOffset(java.time.ZoneOffset.ofHours(4)).toLocalTime().withNano(0).toString() } ?: "-"

    /** D22: teklif kartı ve restorana gidişte "Pickup N min" = N dk sonra hazır, "now" = süre doldu (0), tek "Ready" = hazır. */
    @Test fun d22HazirEtiketleri() {
        var n = 0; var ready = 0
        for (f in frames.filter { it.wolt }) {
            val o = parse(f); if (o.stage != ScreenStage.OFFER && o.stage != ScreenStage.PICKUP) continue
            val label = f.texts.firstNotNullOfOrNull { Regex("Pickup\\s+(\\d{1,3})\\s*min").find(it)?.groupValues?.get(1)?.toInt() }
            if (label != null) { assertEquals("${f.day} ${hm(f.at)}", label, o.readyIn); assertNull(o.pickupClock); n++ }
            if (f.texts.any { it.trim() == "Ready" }) { assertTrue("${f.day} ${hm(f.at)}", o.ready); ready++ }
        }
        assertTrue("etiketli kare $n, Ready $ready", n > 200 && ready > 20)
        // Durum çubuğundaki "📍 3 min" çipi hazır süresi değildir; bekleme ekranındaki "NOW" da değildir.
        assertNull(CourierParser.parse(WOLT, listOf("3 min", "Pickup from", "Test Restoran", "Test küç. 1, AZ1000 Baku"), emptyList(), 0L, "ocr").readyIn)
    }

    @Test fun d19BeklemeKarelerininEnAz95iTaninir() {
        val idle = frames.filter(::isIdleFrame)
        assertEquals(2715, idle.size)
        val ok = idle.count { parse(it).stage in setOf(ScreenStage.IDLE, ScreenStage.OFFLINE) }
        assertTrue("bekleme $ok/${idle.size}", ok >= 2580)
    }

    @Test fun d5BolunmusEkranKarelerininHepsiBekleme() {
        val split = frames.filter(::isSplitFrame)
        assertEquals(225, split.size)
        val bad = split.filter { parse(it).stage != ScreenStage.IDLE }
        assertTrue("bölünmüş tanınmayan ${bad.size}", bad.isEmpty())
    }

    @Test fun d1KartKazanciOkunurParaUstuAsla() {
        val cards = frames.filter { it.wolt && it.joined.contains("Expected earn") }
        for (f in cards) {
            val g = parse(f).gross
            changeAmount(f)?.let { assertNotEquals("para üstü kazanç sanıldı ${f.at}", it, g ?: -1.0, 0.001) }
            cardEarning(f)?.let { assertEquals("kart ${f.day} ${f.at}", it, g ?: -1.0, 0.001) }
        }
        // Beklenen dosyasındaki her teklifin tutarı (Araz hariç tek eksik kare) en az bir karede okunur.
        val days = expected.getJSONObject("gunler")
        for (day in days.keys()) {
            val offers = days.getJSONObject(day).getJSONArray("teklifler")
            for (i in 0 until offers.length()) {
                val o = offers.getJSONObject(i); if (o.isNull("azn")) continue
                val want = o.getDouble("azn"); val start = OffsetDateTime.parse("${day}T${o.getString("ilk_kare")}+04:00").toInstant().toEpochMilli()
                val got = cards.filter { it.day == day && it.at in start..start + 180_000 }.mapNotNull { parse(it).gross }
                assertTrue("$day ${o.getString("ilk_kare")} ${o.optString("restoran")} $want ∉ $got", got.any { kotlin.math.abs(it - want) < 0.001 })
            }
        }
    }

    @Test fun d21ExpectedEarningsTekBasinaTeklifIsareti() {
        val cards = frames.filter { it.wolt && it.joined.contains("Expected earnings for the full") }
        val bad = cards.filter { parse(it).stage != ScreenStage.OFFER }
        assertTrue("teklif sayılmayan kart ${bad.size}/${cards.size}", bad.isEmpty())
    }
}
