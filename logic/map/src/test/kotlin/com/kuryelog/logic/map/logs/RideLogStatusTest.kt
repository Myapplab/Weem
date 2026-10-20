package com.kuryelog.logic.map.logs

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * YolGo `ride_log_status_test.dart` saf test (1; döngülü widget testi `:app` Robolectric `RideLogStatusTextTest`; A6.2b, MAP.md 12.7, 12.13):
 * Ekim planı madde 3 (F2.2, H7): ana ekrandaki kayıt noktası kalktı; gerçek kayıt durumu (ayar + gün açık mı) Sürüş kayıtları'nda yazılır.
 */
class RideLogStatusTest {
    @Test
    fun `üç durum kayıt sürüyor, gün başlayınca, kapalı`() {
        assertEquals("● Kayıt sürüyor" to "● Qeyd gedir", RideLogStatus.texts(logging = true, dayActive = true))
        assertEquals("Gün başlayınca kaydedilir" to "Gün başlayanda qeyd olunur", RideLogStatus.texts(logging = true, dayActive = false))
        assertEquals("Kayıt kapalı" to "Qeyd bağlıdır", RideLogStatus.texts(logging = false, dayActive = true))
        assertEquals("Kayıt kapalı" to "Qeyd bağlıdır", RideLogStatus.texts(logging = false, dayActive = false))
    }
}
