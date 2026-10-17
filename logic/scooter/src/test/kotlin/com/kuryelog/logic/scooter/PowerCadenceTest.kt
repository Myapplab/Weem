package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/**
 * NT `PowerCadenceTest.java` (S1, S2; 22 çağrı, 49 çalışma). Field-shaped G3 read cadence. Register values and timing come from the
 * 2026-10-07 0.2.3 log: replies arrive ~70 ms after a read, BMS 0x07 answers voltage/current/percent, BMS 0x22/0x23 never answer
 * (each cost a 1.2 s timeout on every pass, so power inputs refreshed only every ~4.6 s).
 */
class PowerCadenceTest : NinebotSuite() {
    class Run {
        val port = ProtocolTest.Port()
        lateinit var session: NinebotSession
        val requests = ArrayList<LongArray>()
        val power = ArrayList<LongArray>()
        var firstPowerMs = -1L
        fun times(board: Int, index: Int, from: Long): List<Long> {
            val out = ArrayList<Long>()
            for (r in requests) if (r[1] == board.toLong() && r[2] == index.toLong() && r[0] >= from) out.add(r[0])
            return out
        }

        /** Time after which every silent register has been dropped (last request to a dropped register + its timeout). */
        fun settled(): Long {
            var last = 0L
            for (r in requests) if (disabled(r[1].toInt(), r[2].toInt())) last = maxOf(last, r[0] + 1300)
            return last
        }

        fun disabled(board: Int, index: Int): Boolean = port.notes.any { it.startsWith("read_disabled:") && it.contains("board=$board; index=$index;") }
    }

    @Test fun powerCadenceAssertions() {
        val volt = NinebotMetrics.voltage(7); val curr = NinebotMetrics.battery(7)[1]; val pct = NinebotMetrics.battery(7)[2]
        check(NinebotMetrics.fast(volt) && NinebotMetrics.fast(curr) && !NinebotMetrics.fast(pct), "pack voltage and current are fast, pack percent is slow")
        check(NinebotMetrics.fast(NinebotMetrics.vehicle()[6]) && !NinebotMetrics.fast(NinebotMetrics.vehicle()[2]), "speed candidate fast, odometer slow")
        check(!NinebotMetrics.fast(NinebotMetrics.sweep(NinebotMetrics.Sweep(0x07, 0x8c, 0x8d))[0]), "a swept voltage register is never fast")
        check(NinebotMetrics.freshMs(curr) == NinebotMetrics.FAST_FRESH_MS && NinebotMetrics.freshMs(pct) == NinebotMetrics.SLOW_FRESH_MS, "freshness follows cadence")
        // Field values: 0x148b=52.59 V and 0xffec=-0.20 A give -10.5 W at standstill (official dashboard showed -0.2 A).
        val field = NinebotMetrics(); field.accept(volt, byteArrayOf(0x8b.toByte(), 0x14), 0); field.accept(curr, byteArrayOf(0xec.toByte(), 0xff.toByte()), 0)
        check(abs(NinebotMetrics.powerW(field.values(), 0, NinebotMetrics.FAST_FRESH_MS)!! - 52.59 * -0.20) < 1e-9, "field voltage × current")

        for (candidates in booleanArrayOf(false, true)) {
            val tag = if (candidates) " (candidates answer)" else " (candidates silent)"
            val run = run(candidates, 90000, 0, check = this::check)
            check(run.session.stage == NinebotSession.Stage.READING, "session keeps reading$tag")
            check(run.disabled(0x22, 0x8c) && run.disabled(0x23, 0x8c), "silent BMS 0x22/0x23 dropped$tag")
            check(!run.disabled(7, 0x8c) && !run.disabled(7, 0x8d) && !run.disabled(0x16, 0x55), "answering registers never dropped$tag")
            check(candidates != run.disabled(0x16, 0x57) && candidates != run.disabled(0x16, 0x68) && candidates != run.disabled(0x16, 0x6a), "silent candidates dropped only when silent$tag")
            check(run.times(0x22, 0x8c, 15000).isEmpty() && run.times(0x23, 0x8c, 15000).isEmpty(), "no reads to missing packs after warm-up$tag")
            check(run.firstPowerMs > 0 && run.firstPowerMs <= 6000, "power appears within the first passes$tag")
            val warm = 20000L
            // Silent slow candidates are probed once per slow rotation until dropped; until then a pass can take 1.7 s.
            val settled = run.settled(); check(settled <= 60000, "every silent register dropped within a minute$tag settled=$settled")
            check(maxGap(run.times(7, 0x8d, warm)) <= 2000, "current read within two seconds while silent registers are probed$tag")
            val current = run.times(7, 0x8d, settled); val voltage = run.times(7, 0x8c, settled)
            check(current.size >= 30 && maxGap(current) <= 1000, "current read at least once a second once settled$tag gap=${maxGap(current)}")
            check(maxGap(voltage) <= 1000, "voltage read at least once a second once settled$tag")
            if (candidates) check(maxGap(run.times(0x16, 0x57, settled)) <= 1000, "speed candidate read every pass")
            var steady = true; for (w in run.power) if (w[0] >= warm && w[1] != Math.round(52.59 * -0.20 * 1000)) steady = false
            check(steady, "power shown on every one-second sample after warm-up$tag")
            for (index in if (candidates) intArrayOf(0x55, 0x5f, 0x62, 0x64, 0x66, 0x6b, 0x68, 0x6a) else intArrayOf(0x55, 0x5f, 0x62, 0x64, 0x66, 0x6b)) { val t = run.times(0x16, index, warm); check(t.size >= 5 && maxGap(t) < NinebotMetrics.SLOW_FRESH_MS, "slow register 0x" + Integer.toHexString(index) + " still refreshed" + tag) }
            check(maxGap(run.times(7, 0x8f, warm)) < NinebotMetrics.SLOW_FRESH_MS, "pack percent still refreshed$tag")
        }
        // A register that answered once survives a short Bluetooth gap: missed replies never drop it and power recovers.
        val gap = run(false, 60000, 3, check = this::check)
        check(gap.session.stage == NinebotSession.Stage.READING && !gap.disabled(7, 0x8c), "answered voltage kept after three missed replies")
        check(gap.times(7, 0x8c, 45000).isNotEmpty() && gap.power[gap.power.size - 1][1] == Math.round(52.59 * -0.20 * 1000), "voltage polled again and power recovers")
        assertEquals("power cadence assertions passed; no physical scooter was used", 49, count)
    }

    companion object {
        fun le(v: Long, n: Int): ByteArray {
            val d = ByteArray(n)
            for (i in 0 until n) d[i] = (v ushr (8 * i)).toByte()
            return d
        }

        /** Scooter model; null = no reply. Candidate dashboard registers are unverified on a real G3, so both cases run. */
        fun answer(board: Int, index: Int, candidates: Boolean): ByteArray? {
            if (board == 0x16) return when (index) {
                0x55 -> le(95, 2)
                0x5f -> le(3845, 2)
                0x62 -> le(43731, 4)
                0x64 -> le(3177393, 4)
                0x66 -> le(955840, 4)
                0x6b -> le(220, 2)
                0x57 -> if (candidates) le(0, 2) else null
                0x68 -> if (candidates) le(12, 2) else null
                0x6a -> if (candidates) le(3, 2) else null
                else -> null
            }
            if (board == 7) return when (index) {
                0x8c -> le(5259, 2)
                0x8d -> le(-20, 2)
                0x8f -> le(95, 2)
                else -> null
            }
            return null
        }

        fun maxGap(t: List<Long>): Long {
            var max = 0L
            for (i in 1 until t.size) max = maxOf(max, t[i] - t[i - 1])
            return max
        }

        /** Drives the session with the 100 ms pulse of BleCapture; dropV7 silently skips that many voltage replies after 30 s. [check]
         *  counts in the calling suite only when it is that suite's own (the original's static counter belonged to this suite). */
        fun run(candidates: Boolean, until: Long, dropV7: Int, eventDriven: Boolean = false, visible: Boolean = true, check: (Boolean, String) -> Unit = NinebotSuite.ASSERT_ONLY): Run {
            val run = Run(); val s = NinebotSession(run.port, String(ProtocolTest.NAME, Charsets.US_ASCII), ProtocolTest.KEY, false, 0); run.session = s
            ProtocolTest.challenge(s); val peer = ProtocolTest.sn(); s.receive(peer.encrypt(ProtocolTest.reply(4, 0x5d, 1, ByteArray(0))), 200)
            check(s.stage == NinebotSession.Stage.READING, "stored key authenticates"); s.panelVisible(visible)
            val known = ProtocolTest.known(null); var reply: ByteArray? = null; var replyAt = 0L; var dropped = 0
            var now = 300L
            while (now <= until) {
                if (reply != null && now >= replyAt) { s.receive(reply, now); reply = null }
                if (eventDriven || now % 100 == 0L) {
                    val before = run.port.sent.size; s.tick(now)
                    if (run.port.sent.size > before) {
                        val r = ProtocolTest.lastRead(run.port, known)!!; run.requests.add(longArrayOf(now, r.board.toLong(), r.index.toLong())); var data = answer(r.board, r.index, candidates)
                        if (data != null && r.board == 7 && r.index == 0x8c && now >= 30000 && dropped < dropV7) { dropped++; data = null }
                        if (data != null) { reply = peer.encrypt(ProtocolTest.reply(r.board, 4, r.index, data)); replyAt = now + 70 }
                    }
                }
                if (now % 1000 == 0L) { val w = NinebotMetrics.powerW(s.metrics.values(), now, NinebotMetrics.FAST_FRESH_MS); if (w != null && run.firstPowerMs < 0) run.firstPowerMs = now; run.power.add(longArrayOf(now, if (w == null) Long.MIN_VALUE else Math.round(w * 1000))) }
                now += 10
            }
            return run
        }
    }
}
