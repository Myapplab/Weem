package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.ArrayDeque

/** NT `LogPolicyTest.java` (S3, S8; 13 çağrı; eski suit sayı basmaz): normal/ayrıntılı sınırlar, tavan, aşırı yükten sonra GPS. */
class LogPolicyTest : NinebotSuite() {
    @Test fun logPolicyAssertions() {
        val normal = NinebotLogPolicy(false, 0)
        for (kind in arrayOf("ble_raw", "ble_write", "ble_write_result", "protocol_tx", "ble_measurement")) check(!normal.keep(kind, 0), "normal mode omits per-packet rows")
        check(normal.keep("authenticated", 0), "connection events retained")
        val detailed = NinebotLogPolicy(true, 100)
        check(detailed.keep("ble_raw", 100), "explicit diagnostics retained")
        check(!detailed.keep("ble_raw", 100 + NinebotLogPolicy.DETAIL_MS), "diagnostics expire after ten minutes")
        for (i in 0 until NinebotLogPolicy.MAX_BLE_EVENTS) check(detailed.keep("protocol_rejected", 200), "admit within cap")
        check(!detailed.keep("protocol_rejected", 200), "even error spam bounded")
        val busy = NinebotLogPolicy(true, 0)
        for (i in 0 until NinebotLogPolicy.MAX_BLE_EVENTS) check(busy.keep("ble_raw", 1), "detailed packets admitted")
        check(!busy.keep("ble_raw", 2), "packet budget bounded")
        check(busy.keep("ble_state", 3), "disconnect still logged after packet budget is used")
        check(busy.keep("authenticated", 4), "AUTH still logged after packet budget is used")
        val writer = ArrayDeque<Runnable>(); val queue = NinebotEventQueue({ r -> writer.add(r); true }, 2)
        val saved = ArrayList<String>(); queue.post { saved.add("ble1") }; queue.post { saved.add("ble2") }
        check(!queue.post { saved.add("overflow") }, "reject full BLE queue"); writer.add { saved.add("gps") }
        while (!writer.isEmpty()) writer.remove().run(); check(saved == listOf("ble1", "ble2", "gps"), "GPS writer continues after diagnostic overload")
        check(queue.post {}, "queue recovers without a recording restart")
        // The original prints no count; this one follows from the loops (5 + 1 + 1 + 1 + 10000 + 1 + 10000 + 6).
        assertEquals("Log policy: normal/detail limits, cap and GPS after overload passed", 20015, count)
    }
}
