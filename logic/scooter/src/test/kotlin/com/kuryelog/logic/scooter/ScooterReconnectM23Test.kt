package com.kuryelog.logic.scooter

import org.junit.Assert.*
import org.junit.Test

/**
 * Ekim planı madde 23 (16 Ekim, dördüncü tur): kurye scooter'dan uzaklaşıp dönünce Bluetooth yeniden bağlanıyor ama verinin gelip
 * gelmediği bilinmiyor. `ninebot-logger/` dışarıya BLE bağlantı anını vermiyor; Rawstats veri akışından çıkarır: canlı bağlantı
 * açıkken ≥30 sn kare gelmezse "koptu"; kare yeniden gelince "Scooter verileri alınıyor"; koptuktan sonra kurye sürüyorsa
 * (≥10 km/s, 20 sn) ve 30 sn içinde kare gelmezse "Scooter verileri alınamıyor".
 */
class ScooterReconnectM23Test {
    private val R = ScooterReconnect.Notice.RECEIVING
    private val N = ScooterReconnect.Notice.NOT_RECEIVING

    /** Saniyede bir tik; [frame] o saniyede kare geldiyse, [kmh] GPS hızı. Çıkan bildirimler sırayla. */
    private fun run(r: ScooterReconnect, from: Int, to: Int, frame: (Int) -> Boolean, kmh: (Int) -> Double? = { 0.0 },
                    active: Boolean = true, last: LongArray): List<Pair<Int, ScooterReconnect.Notice>> {
        val out = ArrayList<Pair<Int, ScooterReconnect.Notice>>()
        for (s in from..to) {
            if (frame(s)) last[0] = s * 1000L
            r.tick(s * 1000L, active, last[0].takeIf { it >= 0 }, kmh(s))?.let { out += s to it }
        }
        return out
    }

    @Test fun steadyFramesAndTheFirstConnectionGiveNoNotice() {
        val r = ScooterReconnect(); val last = longArrayOf(-1)
        assertTrue(run(r, 0, 10, { false }, last = last).isEmpty())
        assertTrue(run(r, 11, 300, { true }, last = last).isEmpty())
        assertTrue("10 sn'lik boşluk kopma değil", run(r, 301, 400, { it !in 320..330 }, last = last).isEmpty())
    }

    @Test fun framesBackAfterALossSayReceivingOnce() {
        val r = ScooterReconnect(); val last = longArrayOf(-1)
        run(r, 0, 100, { true }, last = last)
        // Kurye scooter'dan uzaklaştı: 3 dk kare yok, sonra döndü.
        assertTrue(run(r, 101, 280, { false }, last = last).isEmpty())
        assertEquals(listOf(281 to R), run(r, 281, 400, { true }, last = last))
    }

    @Test fun ridingWithoutFramesAfterALossSaysNotReceiving() {
        val r = ScooterReconnect(); val last = longArrayOf(-1)
        run(r, 0, 100, { true }, last = last)
        run(r, 101, 200, { false }, last = last)
        // 201'de sürmeye başladı (20 km/s), kare yok: sürüşün 30. saniyesinde "alınamıyor", bir kez.
        assertEquals(listOf(231 to N), run(r, 201, 300, { false }, kmh = { 20.0 }, last = last))
        assertEquals(listOf(301 to R), run(r, 301, 320, { true }, kmh = { 20.0 }, last = last))
    }

    @Test fun aClosedLinkSaysNothing() {
        val r = ScooterReconnect(); val last = longArrayOf(-1)
        run(r, 0, 100, { true }, last = last)
        assertTrue(run(r, 101, 300, { false }, kmh = { 20.0 }, active = false, last = last).isEmpty())
        assertTrue("bağlantı yeniden açılınca ilk kare ilk bağlantıdır", run(r, 301, 320, { true }, last = last).isEmpty())
    }
}
