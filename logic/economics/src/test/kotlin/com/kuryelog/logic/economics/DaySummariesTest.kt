package com.kuryelog.logic.economics

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

/** A4.2: ayar + gün dosyası → rapor kartı; ayar değişince kart yeni değeri gösterir. */
class DaySummariesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun readsIdleEnergyFromTheDayFileAndFollowsSettings() {
        val log = LogStore(tmp.newFolder("logs"))
        log.append(EnergySegments.FILE, """{"kind":"energy_segment","segment":"idle","spellId":"s1","energy":{"wh":1000.0}}""", "2026-10-18")
        log.append(EnergySegments.FILE, "bozuk satır", "2026-10-18")
        val prefs = KuryelogPrefs.Memory()
        val sums = DaySummaries(log, prefs)
        val day = OrderHistory.Day("2026-10-18", emptyList())
        var s = sums.summary(day, today = false) { tr, _ -> tr }
        assertEquals(0.0, s.energyCostAzn, 0.0)   // fiyat yok → maliyet yok sayılır
        assertTrue((s.card.blocks.filterIsInstance<Block.Line>()).any { it.text.contains("kWh fiyatı girilmediği") })
        prefs.put(mapOf(NetSettings.KWH_PRICE to 0.1f, NetSettings.CHARGE_EFFICIENCY to 100f))
        s = sums.summary(day, today = false) { tr, _ -> tr }
        assertEquals(0.1, s.energyCostAzn, 1e-9)   // 1 kWh × 0.1 ₼ / %100
        assertEquals(0, sums.energyRows("2026-10-17").size)
    }
}
