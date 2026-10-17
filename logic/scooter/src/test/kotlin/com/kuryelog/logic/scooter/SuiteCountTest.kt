package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * SCOOTER.md 13.1 çeviri kabulü (WEEM eki): her suitin `check(` çağrı yeri sayısı eski Java suitiyle aynı (`fun check(` tanımları
 * sayılmaz, Java'daki gibi). Çalışma sayıları her suitin kendi sonunda doğrulanır. 14 suit, 336 çağrı yeri (324 `check(` satırı).
 */
class SuiteCountTest {
    @Test fun callSitesMatchTheOriginalSuites() {
        val dir = File("src/test/kotlin/com/kuryelog/logic/scooter")
        for ((suite, calls) in CALL_SITES) {
            val sites = CALL.findAll(File(dir, "$suite.kt").readText()).count()
            assertEquals(suite, calls, sites)
        }
        assertEquals(336, CALL_SITES.values.sum())
    }

    private companion object {
        val CALL = Regex("""(?<!fun )\bcheck\(""")

        /** NT `grep -o 'check('` eksi tanım satırı (`rawstats` @ `6388c04`). */
        val CALL_SITES = linkedMapOf(
            "TimingTest" to 9, "ProtocolTest" to 55, "LinkTest" to 23, "PairFlowTest" to 26, "SweepTest" to 21,
            "PowerCadenceTest" to 22, "SpeedCadenceTest" to 22, "BatteryProbeTest" to 64, "DashboardTest" to 14, "EventQueueTest" to 6,
            "LogPolicyTest" to 13, "EnergyTest" to 20, "TripMeterTest" to 19, "FloatingTest" to 22,
        )
    }
}
