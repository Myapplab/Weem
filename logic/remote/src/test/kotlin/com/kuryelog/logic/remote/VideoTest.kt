package com.kuryelog.logic.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTest {
    @Test fun geometryKeepsAspectAndMultiplesOf16() {
        // Pixel/Poco dikey 1080×2400 → 576×1280.
        assertEquals(Geometry(576, 1280, 2_211_840), CaptureGeometry.of(1080, 2400, 2400, 0))
        // Yatay aynı oran.
        assertEquals(Geometry(1280, 576, 2_211_840), CaptureGeometry.of(2400, 1080, 2400, 0))
        // İzleyenin ekranı küçükse onun kenarı (800'e kadar inen yedek dizisi).
        assertEquals(800, CaptureGeometry.of(1080, 2400, 800, 0)!!.height)
        assertEquals(640, CaptureGeometry.of(1080, 2400, 800, 1)!!.height)
        assertNull(CaptureGeometry.of(1080, 2400, 800, 2))
        // Yedekler: 1280 → 1024 → 800 → 640 → yok.
        assertEquals(listOf(1280, 1024, 800, 640), (0..3).map { CaptureGeometry.of(1080, 2400, 4000, it)!!.height })
        assertNull(CaptureGeometry.of(1080, 2400, 4000, 4))
        // Küçük ekran büyütülmez; her kenar 16'nın katı.
        val g = CaptureGeometry.of(500, 333, 1280, 0)!!
        assertEquals(496, g.width)
        assertEquals(320, g.height)
        assertNull(CaptureGeometry.of(0, 100, 1280, 0))
    }

    @Test fun bitrateIsClamped() {
        assertEquals(CaptureGeometry.MIN_BPS, CaptureGeometry.startBitrate(160, 160))
        assertEquals(CaptureGeometry.MAX_BPS, CaptureGeometry.startBitrate(4000, 4000))
    }

    @Test fun annexBSplitsUnitsAndParameterSets() {
        val sps = byteArrayOf(0x67, 1, 2, 3)
        val pps = byteArrayOf(0x68, 4)
        val idr = byteArrayOf(0x65, 9, 0, 0, 2)
        val csd = byteArrayOf(0, 0, 0, 1) + sps + byteArrayOf(0, 0, 1) + pps
        val units = AnnexB.units(csd + byteArrayOf(0, 0, 0, 1) + idr)
        assertEquals(3, units.size)
        assertArrayEquals(idr, units[2])
        assertEquals(AnnexB.IDR, AnnexB.type(units[2]))
        val (a, b) = AnnexB.parameterSets(csd)!!
        assertArrayEquals(byteArrayOf(0, 0, 0, 1) + sps, a)
        assertArrayEquals(byteArrayOf(0, 0, 0, 1) + pps, b)
        assertNull(AnnexB.parameterSets(byteArrayOf(0, 0, 0, 1) + sps))
    }

    @Test fun flowSendsKeyFirstThenDropsUnderCongestionUntilNextKey() {
        val f = FlowControl(2_000_000)
        f.reset(0)
        // Akış başı: P kare atılır, anahtar kare istenir.
        val p0 = f.admit(false, 1000, 0)
        assertFalse(p0.send)
        assertTrue(p0.requestKey)
        assertTrue(f.admit(true, 50_000, 10).send)
        assertTrue(f.admit(false, 1000, 20).send)
        // Onay gelmeden yolda bayt sınırı (256 KiB) aşılınca atılır; bit hızı düşer, anahtar kare istenir.
        var t = 30L
        var sent = 0
        while (true) {
            val d = f.admit(false, 20_000, t)
            if (!d.send) {
                assertTrue(d.requestKey)
                assertEquals(1_400_000, d.bitrate)
                break
            }
            sent++
            t += 10
        }
        assertEquals(10, sent)
        assertEquals(2L, f.dropped)
        assertEquals(251_000L, f.inFlight)
        // Onay gelse de anahtar kareye dek P kare gitmez; istek en çok 1 sn'de bir yinelenir.
        f.ack(251_000)
        assertEquals(0L, f.inFlight)
        val p = f.admit(false, 1000, t + 100)
        assertFalse(p.send)
        assertFalse(p.requestKey)
        assertTrue(f.admit(false, 1000, t + 1200).requestKey)
        assertTrue(f.admit(true, 30_000, t + 1300).send)
        assertTrue(f.admit(false, 1000, t + 1310).send)
    }

    @Test fun bitrateDecreasesAtMostOncePerSecondAndRecoversAfterThreeQuietSeconds() {
        val f = FlowControl(2_000_000)
        f.reset(0)
        f.admit(true, 300_000, 0) // yolda 300 KB, sınır 256 KiB
        assertEquals(1_400_000, f.admit(true, 10, 100).bitrate)
        assertNull(f.admit(true, 10, 500).bitrate)
        assertEquals(980_000, f.admit(true, 10, 1_100).bitrate)
        assertNull(f.tick(3_000))
        assertEquals(1_230_000, f.tick(4_200))
        assertNull(f.tick(5_000))
        assertEquals(1_480_000, f.tick(7_200))
        // Taban.
        val g = FlowControl(600_000)
        g.reset(0)
        g.admit(true, 300_000, 0)
        assertEquals(CaptureGeometry.MIN_BPS, g.admit(true, 10, 100).bitrate)
        assertNull(g.admit(true, 10, 2_000).bitrate)
    }

    @Test fun ackOutsideSentRangeIsIgnored() {
        val f = FlowControl(2_000_000)
        f.reset(0)
        f.admit(true, 1000, 0)
        f.ack(5000)
        assertEquals(1000L, f.inFlight)
        f.ack(600)
        f.ack(300)
        assertEquals(400L, f.inFlight)
    }
}
