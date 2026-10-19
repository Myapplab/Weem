package com.kuryelog.logic.orders.dataset

import com.kuryelog.logic.core.Clock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * YolGo `dataset_days_test.dart` (2; A6.2b, MAP.md 13.5, 13.18): gece yarısını geçen vardiya iki takvim günü; telefonun saat dilimi (sahte [Clock] `Asia/Baku`) aynı Bakü gününü verir.
 * 2. test UYARLANIR: WEEM telefon saat dilimini kullanır; "bölgeden bağımsız" iddiası düşer.
 */
class DatasetDaysTest {
    private val clock = object : Clock {
        override fun wallMs() = 0L
        override fun monoMs() = 0L
        override fun zone(): ZoneId = ZoneId.of("Asia/Baku")
    }

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `gece yarısı vardiyanın iki takvim gününü toplar`() {
        assertEquals(listOf("2026-10-09", "2026-10-10"), DatasetDays.days(ms("2026-10-09T19:50:00Z"), ms("2026-10-09T20:10:00Z"), clock.zone()))
    }

    @Test
    fun `telefonun saat dilimi aynı Bakü gününü verir`() {
        // 2026-10-10 00:30 – 02:00 Bakü (+04:00)
        assertEquals(listOf("2026-10-10"), DatasetDays.days(ms("2026-10-09T20:30:00Z"), ms("2026-10-09T22:00:00Z"), clock.zone()))
    }
}
