package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.Baku
import com.kuryelog.logic.map.net.FakeHttp
import com.kuryelog.logic.map.net.HttpResponse
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** CI gömülü harita aracı (`tool/build_map.dart`, MAP.md 2.11): ağsız (sahte SRTM, hazır yol dosyası). */
class MapBuildTest {
    private val tmp: File = Files.createTempDirectory("weem-mapbuild-").toFile()
    private val size = 1201

    @After
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun hgt(value: Int): ByteArray {
        val b = ByteBuffer.allocate(size * size * 2)
        for (i in 0 until size * size) b.putShort(i * 2, value.toShort())
        return b.array()
    }

    @Test
    fun `SRTM karosunda Geniş Bakü dışı sıfırlanır, içi aynı kalır`() {
        val masked = MapBuild.maskOutside(hgt(70), "N40E049.hgt", Baku.wideBox)
        val t = HgtTile(40, 49, masked)
        assertEquals(70.0, t.sample(40.4, 49.8)!!, 0.0) // içeride
        assertEquals(70.0, t.sample(40.27, 49.57)!!, 1e-9) // ± 0,02° payı içinde
        assertEquals(0.0, t.sample(40.1, 49.3)!!, 0.0) // dışarıda 0 (boşluk değil)
        assertEquals(0.0, t.sample(40.9, 49.8)!!, 0.0)
        val odd = ByteArray(10)
        assertTrue(MapBuild.maskOutside(odd, "N40E049.hgt", Baku.wideBox) === odd)
        assertTrue(MapBuild.maskOutside(odd, "x.hgt", Baku.wideBox) === odd)
    }

    /** `n` ayrık 2 düğümlü yol (Geofabrik özeti biçimi: düğümler yollardan önce). */
    private fun roads(n: Int): File {
        val nodes = StringBuilder()
        val ways = StringBuilder()
        for (i in 0 until n) {
            val la = 40.40 + (i / 40) * 0.001
            val lo = 49.80 + (i % 40) * 0.001
            nodes.append("""{"type":"node","id":${2 * i + 1},"lat":$la,"lon":$lo},{"type":"node","id":${2 * i + 2},"lat":${la + 0.0005},"lon":$lo},""")
            ways.append("""{"type":"way","id":${i + 1},"nodes":[${2 * i + 1},${2 * i + 2}],"tags":{"highway":"residential"}},""")
        }
        return File(tmp, "roads.json").apply { writeText("""{"elements":[$nodes${ways.trimEnd(',')}]}""") }
    }

    /** Geofabrik özeti biçimi yer dosyası: 2 yemek noktası, 1 ışık. */
    private fun pois(): File = File(tmp, "pois.json").apply {
        writeText(
            """{"elements":[
            {"type":"node","id":1,"lat":40.40,"lon":49.85,"tags":{"amenity":"restaurant","name":"Firuze"}},
            {"type":"node","id":2,"lat":40.41,"lon":49.86,"tags":{"amenity":"cafe","name":"Kafe"}},
            {"type":"node","id":3,"lat":40.42,"lon":49.87,"tags":{"highway":"traffic_signals"}}]}""",
        )
    }

    @Test
    fun `hazır yol dosyasından graf, yükseklik ve manifest üretilir`() = runBlocking {
        val gz = MapDownload.gzip(hgt(30))
        val http = FakeHttp { r -> if (r.url.contains("N40E049")) HttpResponse(200, bytes = gz) else HttpResponse(404) }
        val out = File(tmp, "assets/map")
        val log = ArrayList<String>()
        val clock = { ZonedDateTime.of(2026, 10, 19, 12, 30, 59, 0, ZoneOffset.ofHours(4)) }
        MapBuild(http, "WEEM/test (personal courier navigation)", File(tmp, "work"), { log += it }, clock).run(out, roads(1000), pois())

        val g = RoadGraph.deserialize(gunzip(File(out, "graph_wide.bin.gz").readBytes()))
        assertEquals(1000, g.edgeCount)
        assertTrue(g.hasBikewayData && g.hasParkData)
        assertEquals(30f, g.geomEle[0], 0f)
        assertTrue(File(out, "N40E049.hgt.gz").exists())
        assertFalse(File(out, "N40E050.hgt.gz").exists())
        val m = JSONObject(File(out, "manifest.json").readText())
        assertEquals("2026-10-19T08:30", m.getString("version")) // UTC, dakikaya kadar
        assertEquals(2, m.getInt("format"))
        assertEquals("wide", m.getJSONArray("regions").getString(0))
        assertEquals(1, m.getJSONObject("pois").getInt("r"))
        assertEquals(1, m.getJSONObject("pois").getInt("l"))
        val wide = PoiSet.decode(gunzip(File(out, "pois_wide.json.gz").readBytes()))
        assertEquals(3, wide.length)
        assertTrue(File(out, "pois_central.json.gz").exists())
        assertTrue(log.contains("1000 yol, 2000 düğüm"))
        assertTrue(log.contains("wide: 1000 kenar"))
        assertTrue(log.contains("Yükseklik: N40E050 indirilemedi, düz kabul edilecek"))
        assertTrue(http.requests.none { it.url.endsWith("/interpreter") })
    }

    @Test
    fun `çok az yol ve yükseklik yoksa hata`() = runBlocking {
        val gz = MapDownload.gzip(hgt(30))
        val ok = FakeHttp { r -> if (r.url.contains("N40E049")) HttpResponse(200, bytes = gz) else HttpResponse(404) }
        try {
            MapBuild(ok, "ua", File(tmp, "w1"), {}).run(File(tmp, "o1"), roads(999), null)
            fail("hata bekleniyordu")
        } catch (e: IllegalStateException) {
            assertEquals("wide: çok az yol (999)", e.message)
        }
        try {
            MapBuild(FakeHttp { HttpResponse(404) }, "ua", File(tmp, "w2"), {}).run(File(tmp, "o2"), roads(1000), null)
            fail("hata bekleniyordu")
        } catch (e: IllegalStateException) {
            assertEquals("yükseklik verisi yok", e.message)
        }
    }
}
