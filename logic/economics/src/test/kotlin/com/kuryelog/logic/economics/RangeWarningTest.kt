package com.kuryelog.logic.economics

import com.kuryelog.logic.capture.CourierParser
import com.kuryelog.logic.capture.OwnLines
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.LogStore
import com.kuryelog.logic.core.RangeBattery
import com.kuryelog.logic.core.RangeLeg
import com.kuryelog.logic.core.RangePort
import com.kuryelog.logic.orders.DeliveryCore
import com.kuryelog.logic.orders.OfferSummaries
import com.kuryelog.logic.orders.OnlineSessions
import com.kuryelog.logic.orders.OverlayText
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

/**
 * N5 menzil uyarısı (A10.4e; MAP.md 10.6, T1-1 a): v2 `BATTERY_ESCAPE_NOT_MET` kuralı bırakış → ev bacağı ve bataryayla beslenir; engel
 * doğarsa rozette tek satır ve teklif özetinde km. Bırakış yalnız karttaki Plus Code/DMS'den; ev yoksa, ayar kapalıysa girdi yok.
 */
class RangeWarningTest {
    @get:Rule val tmp = TemporaryFolder()
    private val baku = ZoneId.of("Asia/Baku")
    private val wolt = "com.wolt.courierapp"
    private val t0 = 1791295200000L
    private var now = t0
    private val tr = { a: String, _: String -> a }

    private class FakeRange(var wh: Double, val leg: RangeLeg?) : RangePort {
        val asked = ArrayList<List<Double>>()
        override fun battery() = RangeBattery(wh / 5.97, wh, RangeBattery.ESTIMATOR, 0L)
        override suspend fun rideTo(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RangeLeg? {
            asked += listOf(fromLat, fromLon, toLat, toLon); return leg
        }
    }

    private inner class Rig(val port: FakeRange, home: Boolean = true, on: Boolean? = null) {
        val prefs = KuryelogPrefs.Memory().apply {
            if (home) put(mapOf(KuryelogPrefs.HOME_LAT to "40.4093", KuryelogPrefs.HOME_LON to "49.8671"))
            if (on != null) put(mapOf(KuryelogPrefs.RANGE_WARN to on))
            put(mapOf("commission" to 20f))
        }
        val log = LogStore(File(tmp.root, "logs-${System.nanoTime()}"), now = { now }, zone = { baku })
        val obs = EconomicsObserver(object : EconomicsObserver.Env {
            override val log = this@Rig.log
            override val prefs = this@Rig.prefs
            override val clock = object : Clock { override fun wallMs() = now; override fun monoMs() = now; override fun zone() = baku }
            override val range: RangePort = port
        })
        val core = DeliveryCore().apply { start() }
        val sessions = OnlineSessions()

        fun frame(dropoff: String) {
            val o = CourierParser.parse(wolt, listOf("Ready in 3 min", "Decline", "₼2.31", "2 stops (2.8 km) • 8–16 min", "Izmir Doner",
                "Neftchilar ave. 1", "Customer drop-off", dropoff, "Accept"), listOf("Accept", "Decline"), now, "accessibility")
            sessions.observe(o)
            val before = core.pending.values.associateBy { it.id }
            val ev = core.observe(o)
            obs.frame(o, ev, before, core, sessions.openSessions(), now)
        }
        val offer get() = core.pending.getValue(wolt)
        fun badge() = OverlayText.badge(core, sessions, 0, false, { 20.0 }, { "Wolt" }, tr, obs::rangeWarnKm)
    }

    /** 40°22'40"N 49°53'31"E — a drop-off point written on the card. */
    private val dms = "40°22'40\"N 49°53'31\"E"

    @Test fun lowBatteryWarnsOnTheBadgeAndTheSummary() {
        val r = Rig(FakeRange(wh = 40.0, leg = RangeLeg(6.4, 21.0, 90.0)))
        r.frame(dms)
        assertEquals(listOf(40.37778, 49.89194, 40.4093, 49.8671), r.port.asked.single().map { Math.round(it * 1e5) / 1e5 })
        assertEquals(6.4, r.obs.rangeWarnKm(r.offer)!!, 1e-9)
        val line = r.badge().lines().single { it.startsWith("Batarya") }
        assertEquals("Batarya yetmeyebilir · eve ~6.4 km", line)
        assertTrue("the parser must drop our own line", OwnLines.isOwn(line))
        assertTrue(OwnLines.isOwn("Batareya çatmaya bilər · evə ~12,0 km"))
        assertTrue(OwnLines.isOwn("Batarya yetmeyebilir eve ~6.4km"))
        assertEquals(6.4, OfferSummaries.of(true, r.core.pending.values, now, annotator = r.obs).single().rangeWarnKm!!, 1e-9)
    }

    @Test fun enoughBatteryNoRouteNoHomeOrSwitchedOffMeansNoWarning() {
        Rig(FakeRange(wh = 590.0, leg = RangeLeg(6.4, 21.0, 90.0))).apply { frame(dms); assertNull(obs.rangeWarnKm(offer)); assertFalse(badge().contains("Batarya")) }
        Rig(FakeRange(wh = 40.0, leg = null)).apply { frame(dms); assertNull(obs.rangeWarnKm(offer)) }
        Rig(FakeRange(wh = 40.0, leg = RangeLeg(6.4, 21.0, 90.0)), home = false).apply { frame(dms); assertTrue(port.asked.isEmpty()); assertNull(obs.rangeWarnKm(offer)) }
        Rig(FakeRange(wh = 40.0, leg = RangeLeg(6.4, 21.0, 90.0)), on = false).apply { frame(dms); assertTrue(port.asked.isEmpty()); assertNull(obs.rangeWarnKm(offer)) }
        // No point on the card (address text only): silent, nothing is looked up.
        Rig(FakeRange(wh = 40.0, leg = RangeLeg(6.4, 21.0, 90.0))).apply { frame("Test 2, Bakı"); assertTrue(port.asked.isEmpty()); assertNull(obs.rangeWarnKm(offer)) }
    }

    @Test fun legsArrivingLaterReEvaluateOnTheNextFrame() {
        val port = FakeRange(wh = 40.0, leg = RangeLeg(6.4, 21.0, 90.0))
        val jobs = ArrayList<() -> Unit>()
        val range = OfferRange({ port }) { jobs += it }
        val r = Rig(port)
        r.frame(dms)
        val offer = r.offer
        val home = OfferGeo.LatLon(40.4093, 49.8671)
        val first = range.inputs(offer, home, null)!!
        assertNull(first.destToHomeKm); assertEquals(40.0, first.estimatorWh!!, 1e-9); assertFalse(range.key(offer.id))
        assertNull(range.inputs(offer, home, null)!!.destToHomeKm); assertEquals("asked once", 1, jobs.size)
        jobs.single()()
        assertTrue(range.key(offer.id))
        assertEquals(6.4, range.inputs(offer, home, null)!!.destToHomeKm!!, 1e-9)
    }
}
