package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** G3 (A7.2): Sürüş Paneli'nden Türkçe gelen durum metinleri Azərbaycanca seçiliyken çevrilerek gösterilir; kayıt içi metin değişmez. */
class ScooterStatusTextTest {
    private fun az(s: String) = ScooterStatusText.localize(s, Lang.AZ)

    @Test fun turkishStaysAsItIs() {
        val s = "G3 kimlik doğrulanıyor • deneme 2/3"
        assertEquals(s, ScooterStatusText.localize(s, Lang.TR))
        assertEquals("", az(""))
        assertEquals("MAX G3 • 12 km/h", az("MAX G3 • 12 km/h"))
    }

    @Test fun phrasesWithNumbersAreTranslatedAroundTheNumbers() {
        assertEquals("G3 kimliyi yoxlanılır • cəhd 2/3", az("G3 kimlik doğrulanıyor • deneme 2/3"))
        assertEquals("Skuter cavab verdi • cütləşmə təsdiqi gözlənilir (42 san). Güc düyməsinə bir dəfə qısa bas",
            az("Scooter yanıt verdi • eşleştirme onayı bekleniyor (42 sn). Güç düğmesine bir kez kısa bas"))
        assertEquals("Bağlantı kəsildi (8); GPS qeydi davam edir", az("Bağlantı koptu (8); GPS kaydı sürüyor"))
        assertEquals("Qeyd dayandı: IOException. Qeyd olunan məlumatlar qorunur.", az("Kayıt durdu: IOException. Kaydedilen veriler korunur."))
        assertEquals("GPS 3 • BLE 9 • Skuterə qoşulur", az("GPS 3 • BLE 9 • Scooter'a bağlanıyor"))
    }

    @Test fun longerPhraseWinsOverItsPrefix() {
        assertEquals("Qeyd açıqdır", az("Kayıt açık"))
        assertEquals("Qeyd açıqdır, amma arxa planda məkan icazəsi verilməyib; GPS tətbiq açıq olanda qeyd olunur",
            az("Kayıt açık, ancak arka planda konum izni verilmedi; GPS uygulama açıkken kaydedilir"))
    }

    @Test fun summaryAndMeasurementsReadInAzerbaijani() {
        val summary = "KAYIT AÇIK • 5 dk\nGPS 40.40000, 49.80000 • ±5 m • 2 sn önce\nG3 bağlı • deneysel ölçüm okuma\nGPS: 300 ölçüm • BLE: 900 paket\n" +
            ScooterTelemetry.measurements(metricsAt1000(), 1500)
        val out = az(summary)
        assertTrue(out, out.startsWith("QEYD AÇIQDIR • 5 dəq\nGPS 40.40000, 49.80000 • ±5 m • 2 san əvvəl\nG3 qoşulub • eksperimental ölçmə oxunur\nGPS: 300 ölçmə • BLE: 900 paket\n"))
        assertTrue(out, out.contains("G3 ölçmələri • sahədə müqayisə edilməlidir\nGüc: 167 W (V×A • yoxlanılmayıb)"))
        assertTrue(out, out.contains("\nSürət (namizəd): 8.50 km/h"))
        assertTrue(out, out.contains("\nBatareya: 94.00 %"))
        assertTrue(out, out.contains("\nBatareya [07] • Gərginlik: 52.99 V"))
        assertTrue(out, out.contains("\nÜmumi km: "))
        for (tr in ScooterStatusText.LABELS.keys) assertFalse("$tr kaldı: $out", out.contains("\n$tr:") || out.contains("] • $tr:"))
    }

    /** Kaynak taraması: ekrana giden her Türkçe durum dizgisi tabloda karşılık bulur (yeni metin eklenip çevrilmezse test düşer). */
    @Test fun everyTurkishStatusLiteralHasAnAzerbaijaniForm() {
        val files = listOf(
            "logic/scooter/src/main/kotlin/com/kuryelog/logic/scooter/NinebotSession.kt",
            "logic/scooter/src/main/kotlin/com/kuryelog/logic/scooter/ScooterTelemetry.kt",
            "scooter/src/main/kotlin/com/kuryelog/app/scooter/BleLink.kt",
            "scooter/src/main/kotlin/com/kuryelog/app/scooter/ScooterService.kt",
            "scooter/src/main/kotlin/com/kuryelog/app/scooter/Scooter.kt",
        ).map { File(RepoSource.root, it) }
        val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        val turkish = Regex("[çğıöşüÇĞİÖŞÜ]|bekleniyor")
        // Ekrana gitmeyenler: olay yükü (`pair_first_reply` notu, `gps_state`) ve önek denetimi.
        val logOnly = setOf("remaining_seconds=\${remaining(now)}; prompt=ŞİMDİ güç düğmesine bir kez kısa bas", "GPS kapalı", "Kayıt durdu:")
        val missing = mutableListOf<String>()
        for (f in files) RepoSource.code(f).forEach { line ->
            for (m in literal.findAll(line)) {
                val s = m.groupValues[1].replace("\\n", "\n")
                if (!turkish.containsMatchIn(s) || s in logOnly || s in ScooterStatusText.LABELS.keys) continue
                if (az(s) == s) missing += "${f.name}: $s"
            }
        }
        assertEquals(emptyList<String>(), missing)
    }

    private fun metricsAt1000(): Collection<NinebotMetrics.Value> {
        val m = NinebotMetrics(); val pack = NinebotMetrics.battery(0x07)
        fun read(key: String) = NinebotMetrics.vehicle().first { it.key == key }
        m.accept(read("scooter_speed_kmh"), byteArrayOf(0x55, 0x00), 1000)
        m.accept(pack[0], byteArrayOf(0xb3.toByte(), 0x14), 1000)
        m.accept(pack[1], byteArrayOf(0x3c, 0x01), 1000)
        m.accept(read("battery_percent"), byteArrayOf(0x5e, 0x00), 1000)
        m.accept(read("odometer_km"), byteArrayOf(0x10, 0x27, 0x00, 0x00), 1000)
        return m.values()
    }
}
