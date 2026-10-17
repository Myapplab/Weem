package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test

/** NT `SpeedCadenceTest.java` (S1, S2; 22 çağrı, 61 çalışma): hız aralığı, arka plan/ön plan istek sayısı, harici paket, GPS temposu. */
class SpeedCadenceTest : NinebotSuite() {
    /** Field-shaped replies (70 ms) with the external pack of 2026-10-09 on board 6, known from an earlier probe: largest gaps of
     *  the internal and external pack current and of the battery percent tile, after warm-up. */
    private fun externalPack(shown: Boolean): LongArray {
        val port = BatteryProbeTest.Port(); val s = NinebotSession(port, String(ProtocolTest.NAME, Charsets.US_ASCII), ProtocolTest.KEY, false, 0)
        s.batteryBoards(intArrayOf(6), IntArray(0)); ProtocolTest.challenge(s); val peer = ProtocolTest.sn(); s.receive(peer.encrypt(ProtocolTest.reply(4, 0x5d, 1, ByteArray(0))), 200); s.panelVisible(shown)
        val known = BatteryProbeTest.known(); var reply: ByteArray? = null; var replyAt = 0L; val internal = ArrayList<Long>(); val external = ArrayList<Long>(); val percent = ArrayList<Long>()
        var now = 300L
        while (now <= 120000) {
            if (reply != null && now >= replyAt) { s.receive(reply, now); reply = null }; val before = port.inner.sent.size; s.tick(now)
            if (port.inner.sent.size > before) {
                val r = ProtocolTest.lastRead(port.inner, known)!!; if (now >= 30000) { if (r.board == 7 && r.index == 0x8d) internal.add(now); if (r.board == 6 && r.index == 0x8d) external.add(now); if (r.board == 0x16 && r.index == 0x55) percent.add(now) }
                val data = BatteryProbeTest.answer(r.board, r.index, 6, -1, true); if (data != null) { reply = peer.encrypt(ProtocolTest.reply(r.board, 4, r.index, data)); replyAt = now + 70 }
            }
            now += 10
        }
        check(s.stage == NinebotSession.Stage.READING && internal.size > 50 && external.size > 50, "external pack read on every pass")
        return longArrayOf(PowerCadenceTest.maxGap(internal), PowerCadenceTest.maxGap(external), PowerCadenceTest.maxGap(percent))
    }

    @Test fun speedAndGpsCadenceAssertions() {
        val port = ProtocolTest.Port(); val waiting = NinebotSession(port, "G3", null, false, 0)
        check(waiting.nextDelayMs(100) == 1000L, "PRE_COMM does not need a 100 ms poll")
        for (driven in booleanArrayOf(false, true)) {
            val run = PowerCadenceTest.run(true, 90000, 0, driven)
            val settled = run.settled(); val speed = run.times(0x16, 0x57, settled)
            val gap = PowerCadenceTest.maxGap(speed)
            check(speed.size > 60 && gap <= (if (driven) 400 else 600), "known speed priority; gap=$gap")
            for (i in intArrayOf(0x55, 0x5f, 0x62, 0x64, 0x66, 0x6b, 0x68, 0x6a)) check(PowerCadenceTest.maxGap(run.times(0x16, i, settled)) < NinebotMetrics.SLOW_FRESH_MS, "slow reads not starved: $i")
            check(PowerCadenceTest.maxGap(run.times(7, 0x8d, settled)) <= 1000, "power remains fresh")
            check(run.session.stage == NinebotSession.Stage.READING, "no spurious reconnect")
            println("Simulated 70 ms replies; event-driven=$driven; settled speed max gap=$gap ms")
        }
        // 0.3.0 live-panel cadence: speed every ≤260 ms (0.2.13: 300 ms) and panel tiles (temperature, battery, range, pack %)
        // every ≤3 s (0.2.13: 4.5 s); lifetime counters move to the rare lane but stay fresh.
        val live = PowerCadenceTest.run(true, 90000, 0, true, true); val ready = live.settled()
        check(PowerCadenceTest.maxGap(live.times(0x16, 0x57, ready)) <= 260, "live speed gap ≤260 ms")
        check(PowerCadenceTest.maxGap(live.times(7, 0x8d, ready)) <= 600 && PowerCadenceTest.maxGap(live.times(7, 0x8c, ready)) <= 600, "live power inputs ≤600 ms")
        for (r in arrayOf(intArrayOf(0x16, 0x6b), intArrayOf(0x16, 0x55), intArrayOf(0x16, 0x5f), intArrayOf(7, 0x8f))) check(PowerCadenceTest.maxGap(live.times(r[0], r[1], ready)) <= 3000, "panel tile register ≤3 s: " + Integer.toHexString(r[1]))
        for (i in intArrayOf(0x62, 0x64, 0x66)) { val t = live.times(0x16, i, ready); check(t.size >= 5 && PowerCadenceTest.maxGap(t) < NinebotMetrics.SLOW_FRESH_MS, "rare counter still fresh: " + Integer.toHexString(i)) }
        check(NinebotMetrics.rare(NinebotMetrics.Read(0x16, 0x62, 4, "odometer_km", "km", false, .1, 0.0, 1e6)) && !NinebotMetrics.rare(NinebotMetrics.Read(0x16, 0x6b, 2, "vehicle_temperature_c", "°C", false, .1, 0.0, 120.0)), "odometer rare, temperature on the panel lane")
        val background = PowerCadenceTest.run(true, 90000, 0, true, false); val foreground = PowerCadenceTest.run(true, 90000, 0, true, true)
        val warm = maxOf(background.settled(), foreground.settled())
        check(background.requests.count { it[0] >= warm } < foreground.requests.count { it[0] >= warm }, "background reduces Bluetooth requests")
        // 0.17.0 (screen off during a KuryePlus shift): 20 ms after a reply, speed every 400 ms, so every 1 Hz energy sample has
        // speed and power inputs younger than a second (0.3.0 background: speed ≤600 ms, current ≤900 ms, ≤1350 ms with a second pack).
        check(PowerCadenceTest.maxGap(background.times(0x16, 0x57, warm)) <= 500, "background speed ≤500 ms")
        check(PowerCadenceTest.maxGap(background.times(7, 0x8d, warm)) <= 600 && PowerCadenceTest.maxGap(background.times(7, 0x8c, warm)) <= 600, "background power inputs ≤600 ms")
        for (shown in booleanArrayOf(false, true)) { val gaps = externalPack(shown); check(gaps[0] <= 800 && gaps[1] <= 800 && gaps[2] <= 5000, "external pack, panel shown=$shown: both currents ≤800 ms, battery % ≤5 s; " + gaps.contentToString()) }
        println("Background/foreground settled requests: " + background.requests.count { it[0] >= warm } + "/" + foreground.requests.count { it[0] >= warm })
        val gps = NinebotGpsPolicy()
        var t = 0L
        while (t < 15000) { check(gps.interval(t, 0.0, 5.0, .2) == 1000L, "wait for sustained stationary fixes"); t += 1000 }
        check(gps.interval(15000, 0.0, 5.0, .2) == 3000L, "stationary saves GPS callbacks")
        check(gps.interval(18000, 2.0, 5.0, .2) == 1000L, "movement restores 1 Hz immediately")
        check(gps.interval(19000, 0.0, 50.0, .2) == 1000L, "poor location cannot reduce cadence")
        check(gps.interval(20000, 0.0, 5.0, null) == 1000L, "unknown speed uncertainty keeps accuracy mode")
        check(gps.interval(40000, 0.0, 5.0, .2) == 1000L, "a lost fix resets stationary timer")
        gps.reset(); check(gps.interval(50000, 0.0, 5.0, .2) == 1000L, "GPS re-enabled starts fresh")
        assertEquals("speed and GPS cadence assertions passed; no physical scooter was used", 61, count)
    }
}
