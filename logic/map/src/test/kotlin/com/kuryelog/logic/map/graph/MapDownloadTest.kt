package com.kuryelog.logic.map.graph

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.geo.Baku
import com.kuryelog.logic.map.net.FakeHttp
import com.kuryelog.logic.map.net.HttpRequest
import com.kuryelog.logic.map.net.HttpResponse
import com.kuryelog.logic.map.net.UserAgent
import com.kuryelog.logic.map.route.TestGraphs
import java.io.File
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** İndirme akışı (MAP.md 2.7–2.9): Overpass aynaları, kaldığı yerden devam, hazır veri sunucusu, gömülü harita ve durum. */
class MapDownloadTest {
    private val dir: File = Files.createTempDirectory("weem-mapdata-").toFile()
    private val ua = UserAgent.of("1.0")
    private val delays = ArrayList<Long>()
    private val noDelay: suspend (Long) -> Unit = { delays += it }
    private val tiles = Baku.wideBox.split(MapDownload.TILE_DEG)

    @After
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun tileOf(r: HttpRequest): Int {
        val q = URLDecoder.decode(r.body!!.removePrefix("data="), "UTF-8")
        return tiles.indexOfFirst { q.contains("(${it.overpass})") }
    }

    /** Karo 0'a iki düğümlü bir yol koyar; öteki karolar boş. */
    private fun overpassBody(tile: Int): String {
        if (tile != 0) return """{"version":0.6,"elements":[]}"""
        val t = tiles[0]
        val la = t.south + 0.001
        val lo = t.west + 0.001
        return """{"version":0.6,"elements":[{"type":"way","id":7,"nodes":[1,2],"tags":{"highway":"residential","name":"Test"}},
            {"type":"node","id":1,"lat":$la,"lon":$lo},{"type":"node","id":2,"lat":${la + 0.001},"lon":$lo}]}"""
    }

    private fun isOverpass(r: HttpRequest) = r.url.endsWith("/interpreter")

    @Test
    fun `aynalar sırayla denenir, son başarılı ayna öne geçer, hepsi düşerse 15 ve 30 sn bekleyip hata`() = runBlocking {
        val hosts = ArrayList<String>()
        var ok = false
        val http = FakeHttp { r ->
            hosts += java.net.URI(r.url).host
            assertEquals("POST", r.method)
            assertEquals(ua, r.headers["User-Agent"])
            assertEquals(120_000L, r.timeoutMs)
            when {
                !ok -> HttpResponse(503)
                hosts.size == 3 -> HttpResponse(200, "{\"elements\":[],\n\"remark\": \"runtime error: out of memory\"}")
                hosts.size == 4 -> throw SocketTimeoutException()
                else -> HttpResponse(200, """{"elements":[]}""")
            }
        }
        val f = OverpassFetcher(http, ua, noDelay)
        val log = ArrayList<String>()
        try {
            f.fetch(tiles[0], log = { log += it.tr })
            fail("hata bekleniyordu")
        } catch (e: MapDataException) {
            assertEquals("Yol verisi indirilemedi (overpass.kumi.systems: HTTP 503)", e.msg.tr)
            assertEquals("Yol məlumatı yüklənmədi (overpass.kumi.systems: HTTP 503)", e.msg.az)
        }
        assertEquals(12, hosts.size)
        assertEquals(listOf(15_000L, 30_000L), delays)
        assertEquals("overpass-api.de meşgul (503), başka sunucu deneniyor", log[0])
        assertEquals("Tüm sunucular meşgul, 15 sn sonra tekrar", log[4])

        hosts.clear()
        ok = true
        hosts.addAll(listOf("x", "y")) // sayaç: sonraki istek 3. (remark notu), 4. (zaman aşımı), 5. başarılı
        val log2 = ArrayList<String>()
        val bytes = f.fetch(tiles[0], log = { log2 += it.tr })
        assertEquals("""{"elements":[]}""", String(bytes))
        assertEquals(
            listOf(
                "overpass-api.de eksik veri verdi, başka sunucu deneniyor",
                "overpass.private.coffee yanıt vermedi, başka sunucu deneniyor",
            ),
            log2,
        )
        // Son başarılı ayna (mail.ru) sonraki karoda ilk denenir.
        hosts.clear()
        f.fetch(tiles[1])
        assertEquals(listOf("maps.mail.ru"), hosts)
    }

    @Test
    fun `sunucu yoksa Overpass karoları indirilir, graf atomik yazılır, parçalar silinir`() = runBlocking {
        val http = FakeHttp { r ->
            when {
                isOverpass(r) -> HttpResponse(200, overpassBody(tileOf(r)))
                else -> HttpResponse(404) // hazır veri sunucusu ve SRTM yok
            }
        }
        val progress = ArrayList<Pair<Double, String>>()
        MapDownload(http, ua, noDelay).run(Baku.wideBox, dir, "graph_central.bin") { v, m -> progress += v to m.tr }
        val g = RoadGraph.deserialize(File(dir, "graph_central.bin").readBytes())
        assertEquals(1, g.edgeCount)
        assertEquals("Test", g.nameOf(0))
        assertFalse(File(dir, "parts_graph_central").exists())
        assertFalse(File(dir, "graph_central.bin.tmp").exists())
        assertEquals(91, http.requests.count { isOverpass(it) })
        val texts = progress.map { it.second }
        assertEquals("Yükseklik verisi (SRTM)", texts[0])
        assertTrue(texts.contains("Yükseklik: N40E049 indiriliyor"))
        assertTrue(texts.contains("Yükseklik: N40E050 indirilemedi, düz kabul edilecek"))
        assertTrue(texts.contains("Hazır yol verisi aranıyor"))
        assertTrue(progress.contains(0.15 to "Yol verisi 1/91"))
        assertTrue(texts.contains("Yol ağı kuruluyor (1 yol)"))
        assertEquals(listOf(0.95 to "Kaydediliyor", 1.0 to "Hazır"), progress.takeLast(2))
        // SRTM iki adresi de denendi (karo başına 2).
        assertEquals(4, http.requests.count { it.url.endsWith(".hgt.gz") })
    }

    @Test
    fun `yarıda kalan indirme kaydedilen parçadan devam eder`() = runBlocking {
        var failFrom = 4
        val http = FakeHttp { r ->
            when {
                isOverpass(r) -> if (tileOf(r) >= failFrom) HttpResponse(500) else HttpResponse(200, overpassBody(tileOf(r)))
                else -> HttpResponse(404)
            }
        }
        val d = MapDownload(http, ua, noDelay)
        try {
            d.run(Baku.wideBox, dir, "graph_wide.bin") { _, _ -> }
            fail("hata bekleniyordu")
        } catch (e: MapDataException) {
            assertEquals(
                "Yol verisi indirilemedi (overpass.kumi.systems: HTTP 500)\n4/91 parça kaydedildi. Tekrar \"İndir\"e bas, kaldığı yerden devam eder.",
                e.msg.tr,
            )
            assertTrue(e.msg.az.endsWith("\n4/91 hissə saxlanıldı. Yenidən \"Yüklə\" bas, qaldığı yerdən davam edəcək."))
        }
        assertEquals(4, File(dir, "parts_graph_wide").listFiles()!!.size)
        assertFalse(File(dir, "graph_wide.bin").exists())

        http.requests.clear()
        failFrom = Int.MAX_VALUE
        d.run(Baku.wideBox, dir, "graph_wide.bin") { _, _ -> }
        assertEquals(87, http.requests.count { isOverpass(it) }) // ilk 4 karo diskten
        assertEquals(1, RoadGraph.deserialize(File(dir, "graph_wide.bin").readBytes()).edgeCount)
    }

    @Test
    fun `iki günden eski ve bozuk parçalar yeniden indirilir`() = runBlocking {
        val parts = File(dir, "parts_graph_wide").apply { mkdirs() }
        val now = 10L * 24 * 3600 * 1000
        val old = File(parts, MapDownload.partName(tiles[0])).apply { writeBytes(MapDownload.gzip("""{"elements":[]}""".toByteArray())) }
        old.setLastModified(now - MapDownload.STALE_MS - 1000)
        File(parts, MapDownload.partName(tiles[1])).apply { writeBytes(byteArrayOf(1, 2, 3)); setLastModified(now) }
        val http = FakeHttp { r -> if (isOverpass(r)) HttpResponse(200, overpassBody(tileOf(r))) else HttpResponse(404) }
        MapDownload(http, ua, noDelay, nowMs = { now }).run(Baku.wideBox, dir, "graph_wide.bin") { _, _ -> }
        assertEquals(91, http.requests.count { isOverpass(it) })
    }

    @Test
    fun `hazır veri sunucusu bütün bölgeyi verirse Overpass açılmaz, yol yoksa hata`() = runBlocking {
        val b = Baku.wideBox
        val manifest = """{"format":1,"regions":{"baku":{"box":[${b.south},${b.west},${b.north},${b.east}],"parts":["p1.json.gz","p2.json"]}}}"""
        var p1 = overpassBody(0)
        val http = FakeHttp { r ->
            when {
                r.url.endsWith("/manifest.json") -> HttpResponse(200, manifest)
                r.url.endsWith("/p1.json.gz") -> HttpResponse(200, bytes = MapDownload.gzip(p1.toByteArray()))
                r.url.endsWith("/p2.json") -> HttpResponse(200, """{"elements":[]}""")
                else -> HttpResponse(404)
            }
        }
        val progress = ArrayList<String>()
        MapDownload(http, ua, noDelay).run(b, dir, "graph_wide.bin") { _, m -> progress += m.tr }
        assertEquals(0, http.requests.count { isOverpass(it) })
        assertTrue(progress.contains("Yol verisi 2/2 (hazır veri sunucusu)"))
        assertTrue(http.requests.first { it.url.endsWith("/manifest.json") }.url.startsWith(MapDownload.PREBUILT_BASE))

        p1 = """{"elements":[]}"""
        try {
            MapDownload(http, ua, noDelay).run(b, dir, "graph_wide.bin") { _, _ -> }
            fail("hata bekleniyordu")
        } catch (e: MapDataException) {
            assertEquals("Bu bölgede yol bulunamadı", e.msg.tr) // parça sayısı satırı eklenmez (Overpass kullanılmadı)
            assertEquals("Bu ərazidə yol tapılmadı", e.msg.az)
        }
    }

    // ---------------- MapDataController ----------------

    private fun graphGz() = MapDownload.gzip(TestGraphs.parallel().serialize())

    private class Assets(val files: MutableMap<String, ByteArray>) : MapAssets {
        val reads = ArrayList<String>()
        override fun read(name: String): ByteArray? {
            reads += name
            return files[name]
        }
    }

    private fun controller(assets: MapAssets, prefs: MemoryMapPrefs, http: FakeHttp, lang: Lang = Lang.TR) =
        MapDataController(dir, assets, prefs, MapDownload(http, ua, noDelay), { lang }, Dispatchers.Unconfined)

    @Test
    fun `gömülü graf akışla açılır, bütün dosya belleğe okunmaz`() = runBlocking {
        // S39: telefonda gzip + açılmış graf birlikte bellekte bellek hatası veriyordu; büyük dosyalar yalnız open() ile.
        val files = mutableMapOf(
            "manifest.json" to """{"version":"2026-10-01T00:00","format":2,"regions":["wide"]}""".toByteArray(),
            "graph_wide.bin.gz" to graphGz(),
        )
        val opened = ArrayList<String>()
        val assets = object : MapAssets {
            override fun read(name: String): ByteArray? {
                if (name.startsWith("graph_")) error("graf belleğe okunmamalı")
                return files[name]
            }

            override fun open(name: String) = files[name]?.inputStream().also { if (it != null) opened += name }
        }
        val c = controller(assets, MemoryMapPrefs(), FakeHttp { HttpResponse(500) })
        c.load("wide")
        assertTrue(c.state.value.ready)
        assertTrue("graph_wide.bin.gz" in opened)
        assertEquals(TestGraphs.parallel().edgeCount, c.state.value.graph!!.edgeCount)
    }

    @Test
    fun `gömülü harita bir kez açılır, sürüm değişince yeniden, ağ açılmaz`() = runBlocking {
        val assets = Assets(
            mutableMapOf(
                "manifest.json" to """{"version":"2026-10-01T00:00","format":2,"regions":["wide"]}""".toByteArray(),
                "graph_wide.bin.gz" to graphGz(),
            ),
        )
        val prefs = MemoryMapPrefs()
        val http = FakeHttp { HttpResponse(500) }
        val c = controller(assets, prefs, http)
        assertEquals(MapDataPhase.CHECKING, c.state.value.phase)
        c.load("central")
        assertTrue(c.state.value.ready)
        assertTrue(File(dir, "graph_central.bin").exists()) // bölge listede yoksa "wide" kaynağı bölge adıyla açılır
        assertEquals("2026-10-01T00:00", prefs.getString("map.bundled.central"))
        assertEquals("2026-10-01T00:00", c.bundledVersion)
        assertNull(c.state.value.pois)
        assertEquals(TestGraphs.parallel().edgeCount, c.state.value.graph!!.edgeCount)
        assertTrue(http.requests.isEmpty())

        assets.reads.clear()
        c.load("central")
        assertEquals(listOf("manifest.json"), assets.reads) // aynı sürüm: açılmaz
        assets.files["manifest.json"] = """{"version":"2026-11-01T00:00","regions":["wide"]}""".toByteArray()
        c.load("central")
        assertTrue(assets.reads.contains("graph_wide.bin.gz"))
        assertEquals("2026-11-01T00:00", prefs.getString("map.bundledAt"))
    }

    @Test
    fun `gömülü harita yoksa missing, bozuk dosya okunamadı hatası`() = runBlocking {
        val prefs = MemoryMapPrefs()
        val placeholder = Assets(mutableMapOf("manifest.json" to """{"version":"","regions":[]}""".toByteArray()))
        val c = controller(placeholder, prefs, FakeHttp { HttpResponse(500) }, Lang.AZ)
        c.load("wide")
        assertEquals(MapDataPhase.MISSING, c.state.value.phase)
        assertNull(c.state.value.error)
        File(dir, "graph_wide.bin").writeBytes(ByteArray(64))
        c.load("wide")
        assertEquals(MapDataPhase.MISSING, c.state.value.phase)
        assertEquals("Xəritə faylı oxunmadı, yenidən yüklə (Xəritə faylı köhnə versiyadır)", c.state.value.error)
    }

    @Test
    fun `güncelleme başarısız olunca eski harita hazır kalır ve hata görünür, harita yoksa error`() = runBlocking {
        val assets = Assets(mutableMapOf())
        val prefs = MemoryMapPrefs()
        val http = FakeHttp { r -> if (isOverpass(r)) throw java.io.IOException("ağ yok") else HttpResponse(404) }
        val c = controller(assets, prefs, http)
        c.download("wide")
        assertEquals(MapDataPhase.ERROR, c.state.value.phase)
        assertEquals("Yol verisi indirilemedi (overpass.kumi.systems: ağ yok)", c.state.value.error)

        File(dir, "graph_wide.bin").writeBytes(TestGraphs.parallel().serialize())
        c.load("wide")
        val before = c.state.value.graph
        c.download("wide")
        val s = c.state.value
        assertEquals(MapDataPhase.READY, s.phase)
        assertTrue(s.ready)
        assertEquals("Güncelleme başarısız: Yol verisi indirilemedi (overpass.kumi.systems: ağ yok)", s.error)
        assertEquals(before!!.edgeCount, s.graph!!.edgeCount)
    }

    @Test
    fun `indirme sürerken eski graf bellekte, ikinci çağrı yok sayılır, sonunda yeni graf yüklenir`() = runBlocking {
        File(dir, "graph_wide.bin").writeBytes(TestGraphs.parallel().serialize())
        lateinit var c: MapDataController
        val during = ArrayList<MapDataState>()
        val http = FakeHttp { r ->
            if (isOverpass(r)) {
                if (tileOf(r) == 0) {
                    during += c.state.value
                    c.download("wide") // building: yok sayılır
                }
                HttpResponse(200, overpassBody(tileOf(r)))
            } else {
                HttpResponse(404)
            }
        }
        c = controller(Assets(mutableMapOf()), MemoryMapPrefs(), http)
        c.load("wide")
        val old = c.state.value.graph
        c.download("wide")
        assertEquals(MapDataPhase.BUILDING, during.single().phase)
        assertSame(old, during.single().graph)
        assertEquals("Yol verisi 1/91", during.single().stage)
        assertEquals(91 + 1, http.requests.count { isOverpass(it) }) // 91 yol parçası + güncelleme sonrası yer sorgusu (`_fetchPoisLater`)
        assertTrue(c.state.value.ready)
        assertEquals(1, c.state.value.graph!!.edgeCount)
        assertNull(c.state.value.error)

        c.deleteAndReload()
        assertEquals(MapDataPhase.MISSING, c.state.value.phase)
        assertFalse(dir.exists())
    }

    @Test
    fun `bbox karşılaştırması 1e-6 içinde`() {
        val b = BBox(1.0, 2.0, 3.0, 4.0)
        assertTrue(MapDownload.sameBox(org.json.JSONArray("[1.0000001,2,3,4]"), b))
        assertFalse(MapDownload.sameBox(org.json.JSONArray("[1.00001,2,3,4]"), b))
        assertFalse(MapDownload.sameBox(org.json.JSONArray("[\"1\",2,3,4]"), b))
    }
}
