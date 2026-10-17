package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** WEEM eki (SCOOTER.md 2.1, 4.1, 5.1): `BleCapture`'dan ayrılan saf kurallar; eski testte yoktu (Android'e gömülüydü). */
class LinkRulesTest {
    /** S6: 1, 2, 4, 8 s, then 15 s up to the 24th attempt, then 60 s; a reading link starts again at 1 s; the event logs the attempt
     *  before it is counted. */
    @Test fun reconnectDelaysAndReset() {
        val r = LinkRetry()
        val delays = (0 until 26).map { r.attempt.also { a -> assertEquals(it, a) }.let { r.next() } }
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L) + List(20) { 15000L } + listOf(60000L, 60000L), delays)
        r.reading()
        assertEquals(0, r.attempt)
        assertEquals(1000L, r.next())
        assertFalse(r.closed); r.close(); assertTrue(r.closed)
    }

    /** R-S4: one 300 s window for all connections of a pairing link; a confirmed key ends pairing. */
    @Test fun oneWindowAcrossReconnects() {
        val w = PairWindow(true)
        val first = w.begin(10_000)
        assertTrue(first.pair && first.window == 300_000L && !first.expired)
        val again = w.begin(250_000)
        assertTrue("a reconnect gets what is left", again.pair && again.window == 60_000L)
        val late = w.begin(310_000)
        assertTrue("window over: no session", late.expired && !late.pair)
        val ok = PairWindow(true); ok.begin(0); ok.confirmed()
        val next = ok.begin(1_000)
        assertTrue("after confirmation: normal AUTH", !next.pair && next.window == 0L && !next.expired && !ok.allowPair)
        val plain = PairWindow(false).begin(5_000)
        assertTrue(!plain.pair && plain.window == 0L && !plain.expired)
    }

    @Test fun gattLayoutAndLimits() {
        assertEquals("6e400001-b5a3-f393-e0a9-e50e24dcca9e", GattPlan.NORDIC)
        assertEquals("ninebot", String(Timing.unhex(GattPlan.CUSTOM.replace("-", "").takeLast(14)), Charsets.US_ASCII))
        assertTrue(GattPlan.kept(GattPlan.NORDIC) && GattPlan.kept(GattPlan.CUSTOM) && !GattPlan.kept("0000180a-0000-1000-8000-00805f9b34fb"))
        assertTrue(GattPlan.subscribed(GattPlan.NORDIC, GattPlan.NOTIFY, true))
        assertFalse(GattPlan.subscribed(GattPlan.CUSTOM, GattPlan.NOTIFY, true) || GattPlan.subscribed(GattPlan.NORDIC, GattPlan.WRITE, true) || GattPlan.subscribed(GattPlan.NORDIC, GattPlan.NOTIFY, false))
        assertEquals("NBScooter1234", GattPlan.deviceName("\u0000 NBScooter1234\u0000\u0001".toByteArray()))
        assertEquals("NBScooter\u00a0", GattPlan.deviceName("NBScooter\u00a0".toByteArray()))
        assertEquals("", GattPlan.deviceName(ByteArray(0)))
        GattPlan.checkWrite(244, 247, 3)
        assertEquals("MTU cannot carry complete frame", assertThrows(IllegalStateException::class.java) { GattPlan.checkWrite(21, GattPlan.DEFAULT_MTU, 0) }.message)
        assertEquals("write queue full", assertThrows(IllegalStateException::class.java) { GattPlan.checkWrite(13, 247, 4) }.message)
    }
}
