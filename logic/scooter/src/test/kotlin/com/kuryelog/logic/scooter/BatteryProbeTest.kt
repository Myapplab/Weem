package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * NT `BatteryProbeTest.java` (S5; 64 çağrı, 854 çalışma). External pack probe on field-shaped G3 timing (2026-10-09, session 26):
 * replies ~70 ms after a read, internal BMS 0x07 answers, BMS 0x22/0x23 never answer even with the external pack attached. The
 * external pack's board is unknown, so the simulated pack is placed on candidate boards and the probe must find it without
 * disturbing the normal reads.
 */
class BatteryProbeTest : NinebotSuite() {
    /** Delegates to the shared test port (for lastRead) and records the persistence callbacks. */
    class Port : NinebotSession.Port {
        val inner = ProtocolTest.Port()
        val found = ArrayList<Int>()
        val done = ArrayList<Boolean>()
        val states = ArrayList<String>()
        var faults = 0
        override fun available(profile: Int) = inner.available(profile)
        override fun send(profile: Int, wire: ByteArray) = inner.send(profile, wire)
        override fun state(state: String) { states.add(state) }
        override fun note(kind: String, detail: String) = inner.note(kind, detail)
        override fun fault(reason: String) { faults++ }
        override fun key(key: ByteArray, confirmed: Boolean) = inner.key(key, confirmed)
        override fun reading(value: NinebotMetrics.Value, bytes: ByteArray) = inner.reading(value, bytes)
        override fun batteryFound(board: Int) { found.add(board) }
        override fun batteryProbeDone(found: Boolean) { done.add(found) }
        fun noted(kind: String, board: Int): Boolean = inner.notes.any { it.startsWith("$kind:") && it.contains("board=$board;") }
        fun count(kind: String): Long = inner.notes.count { it.startsWith("$kind:") }.toLong()
    }

    class Run {
        val port = Port()
        lateinit var session: NinebotSession
        val requests = ArrayList<LongArray>()
        val power = ArrayList<LongArray>()
        fun times(board: Int, index: Int, from: Long): List<Long> {
            val out = ArrayList<Long>()
            for (r in requests) if (r[1] == board.toLong() && r[2] == index.toLong() && r[0] >= from) out.add(r[0])
            return out
        }

        fun boardTimes(board: Int, from: Long): List<Long> {
            val out = ArrayList<Long>()
            for (r in requests) if (r[1] == board.toLong() && r[0] >= from) out.add(r[0])
            return out
        }
    }

    /** detachAt>0 silences the external pack from that moment (pack unplugged mid-ride). reattachAt>0: the external pack answers
     *  again from that moment (plugged back, or a flaky pack waking up). */
    private fun run(knownBoards: IntArray, probe: Boolean, externalBoard: Int, oddBoard: Int, externalPercent: Boolean, until: Long, detachAt: Long, reattachAt: Long = -1): Run {
        val run = Run(); val s = NinebotSession(run.port, String(ProtocolTest.NAME, Charsets.US_ASCII), ProtocolTest.KEY, false, 0); run.session = s
        s.batteryBoards(knownBoards, if (probe) NinebotMetrics.EXTERNAL_BMS_CANDIDATES else IntArray(0))
        ProtocolTest.challenge(s); val peer = ProtocolTest.sn(); s.receive(peer.encrypt(ProtocolTest.reply(4, 0x5d, 1, ByteArray(0))), 200)
        check(s.stage == NinebotSession.Stage.READING, "stored key authenticates"); s.panelVisible(true)
        val known = known(); var reply: ByteArray? = null; var replyAt = 0L
        var now = 300L
        while (now <= until) {
            if (reply != null && now >= replyAt) { s.receive(reply, now); reply = null }
            val before = run.port.inner.sent.size; s.tick(now)
            if (run.port.inner.sent.size > before) {
                val r = ProtocolTest.lastRead(run.port.inner, known)!!; run.requests.add(longArrayOf(now, r.board.toLong(), r.index.toLong()))
                val ext = if (detachAt > 0 && now >= detachAt && !(reattachAt > 0 && now >= reattachAt)) -1 else externalBoard; val data = answer(r.board, r.index, ext, oddBoard, externalPercent)
                if (data != null) { reply = peer.encrypt(ProtocolTest.reply(r.board, 4, r.index, data)); replyAt = now + 70 }
            }
            if (now % 1000 == 0L) { val w = NinebotMetrics.powerW(s.metrics.values(), now, NinebotMetrics.FAST_FRESH_MS); run.power.add(longArrayOf(now, if (w == null) Long.MIN_VALUE else Math.round(w * 100))) }
            now += 10
        }
        return run
    }

    @Test fun batteryProbeAssertions() {
        // Helpers: persisted boards, export allowlist, candidate list hygiene.
        check(NinebotMetrics.parseBoards("08, 0a,zz,07,16,04,08,").contentEquals(intArrayOf(8, 10)), "parse persisted boards, skip junk, reserved and duplicates")
        check(NinebotMetrics.addBoard("", 8) == "08" && NinebotMetrics.addBoard("0a", 8) == "08,0a", "persist boards as sorted hex")
        check(NinebotMetrics.exportable("bms_08_voltage_v") && NinebotMetrics.exportable("bms_07_reg8a_raw") && NinebotMetrics.exportable("scooter_speed_kmh"), "pack and vehicle keys exportable")
        check(!NinebotMetrics.exportable("sweep_16_10") && !NinebotMetrics.exportable("bms_08_raw"), "sweep and unknown keys never exported")
        val seen = HashSet<Int>(); for (b in NinebotMetrics.EXTERNAL_BMS_CANDIDATES) { check(seen.add(b), "candidate listed once: $b"); check(b != 0 && b != 2 && b != 4 && b != 7 && b != 0x16 && b != 0x22 && b != 0x23, "known G3 boards never probed: $b") }

        // Power total: internal alone until an external pack is confirmed by its percent register.
        val m = NinebotMetrics(); val own = NinebotMetrics.battery(7); val ex = NinebotMetrics.battery(8)
        m.accept(own[0], le(5096, 2), 1000); m.accept(own[1], le(-22, 2), 1000)
        check(Math.round(NinebotMetrics.powerW(m.values(), 1000, 3000)!! * 100) == internalCentiW, "internal pack alone")
        m.accept(ex[0], le(4370, 2), 1000); m.accept(ex[1], le(-570, 2), 1000)
        check(Math.round(NinebotMetrics.powerW(m.values(), 1000, 3000)!! * 100) == internalCentiW && NinebotMetrics.externalPowerW(m.values(), 1000, 3000) == null, "unconfirmed external pack not added")
        m.accept(ex[2], le(17, 2), 1000)
        check(Math.round(NinebotMetrics.powerW(m.values(), 1000, 3000)!! * 100) == totalCentiW, "confirmed external pack added to the total")
        check(Math.round(NinebotMetrics.packPowerW(m.values(), 1000, 3000, 7)!! * 100) == internalCentiW, "internal pack power kept separately")
        check(NinebotMetrics.powerW(m.values(), 9000, 3000) == null, "stale internal pack gives no total")
        val frame = NinebotDashboard().snapshot(m.values(), 1000)
        check(NinebotDashboard.oneDecimal(frame.voltageV) == "51.0" && NinebotDashboard.oneDecimal(frame.currentA) == "-0.2", "tiles stay on the internal pack")

        // 1) External pack on 0x08 (first candidate): found within seconds, persisted, probe stops, no reconnect.
        val hit = run(IntArray(0), true, 0x08, -1, true, 120000, 0)
        check(hit.port.found == listOf(8) && hit.port.done == listOf(true), "pack on 0x08 found and reported once")
        check(hit.port.noted("battery_probe_candidate", 8) && hit.port.noted("battery_probe_found", 8), "candidate then confirmation logged")
        var foundAt = -1L; for (r in hit.requests) if (r[1] == 8L && r[2] == 0x8fL) { foundAt = r[0]; break }
        check(foundAt > 0 && foundAt <= 30000, "confirmed within 30 s; t=$foundAt")
        for (b in NinebotMetrics.EXTERNAL_BMS_CANDIDATES) if (b != 8) check(hit.boardTimes(b, foundAt + 2000).isEmpty(), "probe stops after the pack is confirmed: $b")
        check(hit.session.stage == NinebotSession.Stage.READING && hit.port.faults == 0, "probe never drops the link")
        check(!hit.session.probing && hit.port.states.contains("G3 bağlı • harici batarya bulundu (kart 08)"), "result shown to the rider")
        val hitSettled = maxOf(foundAt, settled(hit, 8)); check(hitSettled <= 40000, "silent boards settled within 40 s; t=$hitSettled")
        check(maxGap(hit.times(8, 0x8d, hitSettled)) <= 1000 && maxGap(hit.times(7, 0x8d, hitSettled)) <= 1000, "both pack currents read every second; gaps=" + maxGap(hit.times(8, 0x8d, hitSettled)) + "/" + maxGap(hit.times(7, 0x8d, hitSettled)))
        check(maxGap(hit.times(0x16, 0x57, hitSettled)) <= 600, "speed priority kept with two packs; gap=" + maxGap(hit.times(0x16, 0x57, hitSettled)))
        var summed = true; for (w in hit.power) if (w[0] >= foundAt + 5000 && w[1] != totalCentiW) summed = false; check(summed, "power total includes the external pack once confirmed")

        // 2) No external pack: every candidate tried PROBE_TRIES times, never a reconnect, then normal cadence.
        val none = run(IntArray(0), true, -1, -1, true, 200000, 0)
        check(none.port.found.isEmpty() && none.port.done == listOf(false), "no pack: probe ends once with found=false")
        for (b in NinebotMetrics.EXTERNAL_BMS_CANDIDATES) check(none.times(b, 0x8c, 0).size == NinebotSession.PROBE_TRIES, "each candidate tried exactly " + NinebotSession.PROBE_TRIES + " times: " + b)
        var end = 0L; for (b in NinebotMetrics.EXTERNAL_BMS_CANDIDATES) end = maxOf(end, last(none.requests, b))
        check(end <= 150000, "probe finished within 150 s; t=$end")
        check(none.session.stage == NinebotSession.Stage.READING && none.port.faults == 0, "silent probe never counts as a lost link")
        var packsSettled = 0L; for (r in none.requests) if (r[1] == 0x22L || r[1] == 0x23L) packsSettled = maxOf(packsSettled, r[0] + 1300)
        check(maxGap(none.times(0x16, 0x57, packsSettled)) <= 1500, "speed keeps updating while probing; gap=" + maxGap(none.times(0x16, 0x57, packsSettled)))
        check(maxGap(none.times(0x16, 0x57, end + 5000)) <= 600, "speed cadence restored after the probe")
        check(none.port.states.contains("G3 bağlı • harici batarya bulunamadı"), "empty result shown to the rider")
        var internalOnly = true; for (w in none.power) if (w[0] >= 20000 && w[1] != internalCentiW) internalOnly = false; check(internalOnly, "power stays the internal pack without an external pack")

        // 3) A non-pack device answers 12 V: rejected, not persisted, probe continues and finds the pack later in the list.
        val odd = run(IntArray(0), true, 0x0a, 0x06, true, 150000, 0)
        check(odd.port.noted("battery_probe_reject", 6) && !odd.port.found.contains(6), "implausible voltage rejected")
        check(odd.port.found == listOf(10), "pack found after a rejected board")

        // 4) Voltage answers but percent never does: never persisted, never in the total, its reads are dropped again.
        val partial = run(IntArray(0), true, 0x08, -1, false, 200000, 0)
        check(partial.port.found.isEmpty() && partial.port.noted("battery_probe_partial", 8), "voltage-only board reported, not persisted")
        check(partial.boardTimes(8, partial.requests[partial.requests.size - 1][0] - 20000).isEmpty(), "voltage-only board no longer polled")
        // While probing, one pass can exceed the 3 s freshness window (no power shown for a second); never a wrong total.
        var clean = true; for (w in partial.power) if (w[0] >= 20000 && w[1] != Long.MIN_VALUE && w[1] != internalCentiW) clean = false; check(clean, "voltage-only board never enters the total")
        var partialEnd = 0L; for (b in NinebotMetrics.EXTERNAL_BMS_CANDIDATES) partialEnd = maxOf(partialEnd, last(partial.requests, b))
        var steady = true; for (w in partial.power) if (w[0] >= partialEnd + 5000 && w[1] != internalCentiW) steady = false; check(steady, "power shown every second after the probe ends")

        // 5) Known pack from an earlier probe: read from the first pass, no probe. D12: unplugged mid-ride it is not dropped but
        //    goes quiet: unknown values, only its voltage read once per QUIET_RETRY_MS (it came back only after a reconnect).
        val knownRun = run(intArrayOf(8), false, 0x08, -1, true, 90000, 40000)
        check(knownRun.port.done.isEmpty() && knownRun.port.found.isEmpty() && knownRun.port.count("battery_probe_timeout") == 0L, "known pack needs no probe")
        check(knownRun.times(8, 0x8c, 0).isNotEmpty() && knownRun.times(8, 0x8c, 0)[0] <= 1000, "known pack read in the first pass, before the silent 0x22/0x23; t=" + knownRun.times(8, 0x8c, 0))
        check(knownRun.port.noted("battery_quiet", 8) && !knownRun.port.noted("battery_lost", 8) && knownRun.session.quietPacks.contains(8), "silent external pack goes quiet, never dropped")
        val sparse = knownRun.boardTimes(8, 50000)
        check(sparse.isNotEmpty() && sparse.size <= ((90000 - 50000) / NinebotSession.QUIET_RETRY_MS).toInt() + 1, "quiet pack still read, sparsely: $sparse")
        for (t in sparse) check(knownRun.times(8, 0x8c, t)[0] == t, "only the quiet pack's voltage is read")
        for (i in 1 until sparse.size) check(sparse[i] - sparse[i - 1] >= NinebotSession.QUIET_RETRY_MS, "quiet reads at least QUIET_RETRY_MS apart: $sparse")
        check(knownRun.session.stage == NinebotSession.Stage.READING && knownRun.port.faults == 0, "unplugging the external pack keeps the link")
        // One 1.2 s reply window per QUIET_RETRY_MS is the price; between quiet reads the speed cadence is the normal one.
        val speed = knownRun.times(0x16, 0x57, 50000); var worst = 0L; for (i in 1 until speed.size) { val a = speed[i - 1]; val b = speed[i]; var quietBetween = false; for (t in sparse) if (t >= a && t < b) quietBetween = true; if (!quietBetween) worst = maxOf(worst, b - a) }
        check(worst <= 600, "speed cadence between quiet reads; gap=$worst")
        check(maxGap(speed) <= 1900, "a quiet read costs at most one reply window; gap=" + maxGap(speed))
        var back = true; for (w in knownRun.power) if (w[0] >= 50000 && w[1] != internalCentiW) back = false; check(back, "total falls back to the internal pack (external unknown)")

        // 5b) D12: the pack answers again (plugged back or awake): back on the next quiet read, all its registers, no reconnect.
        val again = run(intArrayOf(8), false, 0x08, -1, true, 120000, 40000, 70000)
        check(again.port.noted("battery_quiet", 8) && again.port.noted("battery_back", 8) && again.session.quietPacks.isEmpty(), "quiet pack back without a reconnect")
        var backAt = -1L; for (t in again.times(8, 0x8c, 70000)) { backAt = t; break }
        check(backAt > 0 && backAt <= 70000 + NinebotSession.QUIET_RETRY_MS, "back within one retry interval; t=$backAt")
        check(maxGap(again.times(8, 0x8d, backAt + 5000)) <= 1000 && again.times(8, 0x8f, backAt).isNotEmpty(), "full pack cadence again (current every second, percent read)")
        var summedAgain = true; for (w in again.power) if (w[0] >= backAt + 10000 && w[1] != totalCentiW) summedAgain = false; check(summedAgain, "total includes the pack again")
        check(again.session.stage == NinebotSession.Stage.READING && again.port.faults == 0, "no reconnect needed")

        // 5c) D12: "connected / lost" said only after the state held for 60 s (3 s flickers say nothing).
        val pp = PackPresence()
        check(pp.observe(false, 0) == null && pp.observe(false, 70000) == null, "a pack never seen is never announced lost")
        check(pp.observe(true, 71000) == null && pp.observe(false, 74000) == null && pp.observe(true, 80000) == null, "3 s flickers say nothing")
        check(PackPresence.CONNECTED == pp.observe(true, 140000), "connected after 60 s present")
        check(pp.observe(true, 150000) == null && pp.observe(false, 160000) == null && pp.observe(true, 163000) == null, "no repeat, a short gap says nothing")
        check(pp.observe(false, 170000) == null && PackPresence.LOST == pp.observe(false, 230000), "lost after 60 s absent")
        check(pp.observe(false, 300000) == null && pp.announced == false, "lost said once")
        // 5d) B3 (each connection): a link down for 60 s or longer makes the next frame a new connection; a short reconnect does not.
        val nc = PackPresence()
        check(nc.observe(true, 0) == null && PackPresence.CONNECTED == nc.observe(true, 60000), "first connection: said after 60 s")
        nc.linkDown(70000); check(nc.observe(true, 75000) == null && nc.observe(true, 140000) == null, "a 5 s reconnect is the same connection: no repeat")
        nc.linkDown(150000); check(nc.observe(true, 400000) == null && PackPresence.CONNECTED == nc.observe(true, 460000), "scooter back after 4 min: said again after 60 s")
        nc.linkDown(470000); check(nc.observe(false, 600000) == null && nc.observe(false, 700000) == null, "a new connection without the pack says nothing")

        // 6) Probe off and nothing known: requests only to the boards read before this change.
        val off = run(IntArray(0), false, 0x08, -1, true, 60000, 0)
        for (r in off.requests) check(r[1] == 0x16L || r[1] == 7L || r[1] == 0x22L || r[1] == 0x23L, "probe off reads only the previous boards: " + r[1])
        check(off.port.count("battery_boards") == 0L && off.port.done.isEmpty(), "probe off logs nothing new")
        assertEquals("battery probe assertions passed; no physical scooter was used", 854, count)
    }

    companion object {
        fun le(v: Long, n: Int): ByteArray {
            val d = ByteArray(n)
            for (i in 0 until n) d[i] = (v ushr (8 * i)).toByte()
            return d
        }

        /** Field values: internal 50.96 V / -0.22 A / 77 % / 9810 mAh, external 43.70 V / -5.70 A / 17 % / 1700 mAh. */
        fun answer(board: Int, index: Int, externalBoard: Int, oddBoard: Int, externalPercent: Boolean): ByteArray? {
            if (board == 0x16) return when (index) {
                0x55 -> le(77, 2)
                0x5f -> le(3287, 2)
                0x62 -> le(44507, 4)
                0x64 -> le(3219134, 4)
                0x66 -> le(971912, 4)
                0x6b -> le(290, 2)
                0x57 -> le(0, 2)
                0x68 -> le(1, 2)
                0x6a -> le(26, 2)
                else -> null
            }
            if (board == 7) return when (index) {
                0x8c -> le(5096, 2)
                0x8d -> le(-22, 2)
                0x8f -> le(77, 2)
                0x8a -> le(9810, 2)
                else -> null
            }
            if (board == externalBoard) return when (index) {
                0x8c -> le(4370, 2)
                0x8d -> le(-570, 2)
                0x8f -> if (externalPercent) le(17, 2) else null
                0x8a -> le(1700, 2)
                else -> null
            }
            if (board == oddBoard && index == 0x8c) return le(1200, 2) // 12.00 V: a device answers, but it is not a 48 V-class pack
            return null
        }

        fun known(): List<NinebotMetrics.Read> {
            val list = ArrayList(NinebotMetrics.vehicle())
            for (b in 1 until 256) if (b != 0x16) list.addAll(NinebotMetrics.battery(b))
            return list
        }

        /** After the silent 0x22/0x23 packs (and any silent probe board) have used up their reply windows. */
        fun settled(run: Run, foundBoard: Int): Long {
            var t = 0L
            for (r in run.requests) if (r[1] == 0x22L || r[1] == 0x23L || (r[1] != 7L && r[1] != 0x16L && r[1] != foundBoard.toLong())) t = maxOf(t, r[0] + 1300)
            return t
        }

        fun maxGap(t: List<Long>): Long {
            var max = 0L
            for (i in 1 until t.size) max = maxOf(max, t[i] - t[i - 1])
            return max
        }

        fun last(rows: List<LongArray>, board: Int): Long {
            var t = -1L
            for (r in rows) if (r[1] == board.toLong()) t = maxOf(t, r[0])
            return t
        }

        val internalCentiW: Long = Math.round(50.96 * -0.22 * 100)
        val totalCentiW: Long = Math.round((50.96 * -0.22 + 43.70 * -5.70) * 100)
    }
}
