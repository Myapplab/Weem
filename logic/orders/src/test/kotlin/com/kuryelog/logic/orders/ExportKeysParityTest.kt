package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.RepoSource
import org.junit.Assert.*
import org.junit.Test

/**
 * Rawstats ExportKeysParityTest.kt @ 6388c04 (1 test). Faz 2 hata taraması (14 Ekim): the anonymous export's allowlists ([AnalysisExport])
 * equal those of its Python twin tools/analysis/anonymize.py. WEEM: both files in this repository (`tools/analysis` = Rawstats @ 6388c04).
 */
class ExportKeysParityTest {
    private val kotlin by lazy { RepoSource.text("logic/orders/src/main/kotlin/com/kuryelog/logic/orders/AnalysisExport.kt") }
    private val python by lazy { RepoSource.text("tools/analysis/anonymize.py") }
    private val quoted = Regex("\"([^\"]*)\"")
    private fun strings(s: String) = quoted.findAll(s).map { it.groupValues[1] }.toList()
    private fun kotlinList(name: String) = Regex("val $name = listOf\\((.*?)\\)", RegexOption.DOT_MATCHES_ALL).find(kotlin)
        ?.let { strings(it.groupValues[1]) } ?: error("$name AnalysisExport.kt'de yok")
    private fun pythonList(name: String) = Regex("^$name = \\[(.*?)]", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)).find(python)
        ?.let { strings(it.groupValues[1]) } ?: error("$name anonymize.py'de yok")

    @Test fun allowlistsAreTheSame() {
        val pairs = listOf("OBSERVATION_KEYS" to "OBS_KEEP", "OFFER_KEYS" to "OFFER_KEEP", "TRIP_KEYS" to "TRIP_KEEP", "PICKUP_KEYS" to "PICKUP_KEEP",
            "CUSTOMER_KEYS" to "CUSTOMER_KEEP", "ORDER_KEYS" to "ORDER_EVENT_KEEP", "SCREEN_KEYS" to "SCREEN_KEEP", "NOTIF_KEYS" to "NOTIF_KEEP")
        for ((k, p) in pairs) {
            val kotlinKeys = kotlinList(k)
            assertTrue(k, kotlinKeys.size >= 5)
            assertEquals("$k / $p", kotlinKeys, pythonList(p))
        }
        for (f in listOf("verified_by", "cash_collected_at", "extra_wait_azn", "energy_partial_ms")) assertTrue(f, f in kotlinList("TRIP_KEYS"))
        assertTrue("fee_ocr_ms" in kotlinList("OFFER_KEYS"))
    }
}
