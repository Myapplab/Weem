package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.geo.Baku
import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.route.TestGraphs
import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Yeni" karakterizasyon testleri (MAP.md 2.13): KNG1 bayt düzeni, `buildRoadGraph` sayıları, HGT örnekleme, karo bölme,
 * `nearestEdge`/`contains`. Beklenen değerler şartnameden (Dart kodu satır satır); bu ortamda Dart yok, YolGo'nun ürettiği dosyayla
 * bayt bayt kıyas doğrulanmadı.
 */
class GraphCharacterizationTest {
    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun pad8(n: Int) = (n + 7) / 8 * 8

    @Test
    fun `KNG1 başlığı, dizi hizaları, u8-u16 alanlar ve ad bölümü little-endian`() {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(10.0), lon(0.0))
        raw.addNode(3, lat(20.0), lon(0.0))
        raw.addWay(OsmWay(5_000_000_001, listOf(1, 2), mapOf("highway" to "footway", "name" to "Ğ", "oneway" to "yes")))
        raw.addWay(OsmWay(7, listOf(2, 3), mapOf("highway" to "steps", "surface" to "sett", "bicycle" to "designated")))
        val g = GraphBuilder.buildRoadGraph(raw) { _, _ -> 0.0 }
        // 3 düğüm (2 kez kullanılan orta düğüm + uçlar), 2 kenar, 10 m parçalar sıklaşmaz → 4 geometri noktası, 2 way, 1 ad.
        val b = g.serialize()
        val h = le(b)
        assertEquals(0x4B4E4731, h.getInt(0))
        assertEquals(listOf('1'.code, 'G'.code, 'N'.code, 'K'.code), (0 until 4).map { b[it].toInt() }) // 'KNG1' LE
        assertEquals(listOf(2, 3, 2, 4, 2, 1, 0, 2, 3), (1..9).map { h.getInt(it * 4) }) // sürüm N E G W Zs Z adBaytı özellik
        var off = 40
        off += 8 * 3 * 2 // nodeLat, nodeLon (24 bayt: dolgu yok)
        assertEquals(g.nodeLat[2], h.getDouble(40 + 16), 0.0)
        val eFrom = off
        off += pad8(2 * 4) * 5 // i32 kenar dizileri: 8 bayt, dolgu yok
        off += pad8(2 * 4) * 7 // f32 kenar dizileri
        assertEquals(1, h.getInt(eFrom + 4)) // edgeFrom[1] = orta düğüm
        off += pad8(4 * 8) * 2 + pad8(4 * 4) // geomLat, geomLon, geomEle
        val wayId = off
        assertEquals(5_000_000_001L, h.getLong(wayId))
        off += pad8(2 * 8)
        assertEquals(RoadClass.FOOTWAY.ordinal, b[off].toInt())
        assertEquals(RoadClass.STEPS.ordinal, b[off + 1].toInt())
        assertEquals(0, b[off + 2].toInt()) // u8 dizisi 8 bayta sıfır dolgulu
        off += 8
        assertEquals(WayFlag.ONEWAY_FWD, h.getShort(off).toInt() and 0xFFFF)
        assertEquals(WayFlag.ROUGH or WayFlag.BIKE_OK or WayFlag.BIKEWAY, h.getShort(off + 2).toInt() and 0xFFFF)
        off += 8
        assertEquals(listOf(0, -1), listOf(h.getInt(off), h.getInt(off + 4))) // wayName: ikinci yol adsız
        off += 8
        assertEquals(0, h.getInt(off)) // zoneStart = [0]
        off += 8 // Z = 0 → zoneLat/zoneLon boş
        assertArrayEquals("Ğ".toByteArray(Charsets.UTF_8), b.copyOfRange(off, b.size)) // adlar dolgusuz, sonda
        assertEquals(off + 2, b.size)

        val back = RoadGraph.deserialize(b)
        assertArrayEquals(b, back.serialize())
        assertEquals("Ğ", back.nameOf(0))
        assertEquals("", back.nameOf(1))
        assertEquals(RoadClass.STEPS, back.classOf(1))
    }

    @Test
    fun `iki ad U+0000 ile birleşir, adsız dosyada ad bölümü boş`() {
        val raw = OsmRaw()
        for (i in 0..3) raw.addNode(i + 1L, lat(i * 10.0), lon(0.0))
        raw.addWay(OsmWay(1, listOf(1, 2), mapOf("highway" to "residential", "name" to "A")))
        raw.addWay(OsmWay(2, listOf(2, 3), mapOf("highway" to "residential", "name" to "B")))
        raw.addWay(OsmWay(3, listOf(3, 4), mapOf("highway" to "residential", "name" to "A")))
        val g = GraphBuilder.buildRoadGraph(raw) { _, _ -> 0.0 }
        assertEquals(listOf("A", "B"), g.names)
        assertEquals(listOf("A", "B", "A"), (0 until 3).map { g.nameOf(it) })
        val b = g.serialize()
        assertArrayEquals(byteArrayOf('A'.code.toByte(), 0, 'B'.code.toByte()), b.copyOfRange(b.size - 3, b.size))
        val unnamed = GraphBuilder.buildRoadGraph(OsmRaw().apply {
            addNode(1, lat(0.0), lon(0.0)); addNode(2, lat(10.0), lon(0.0))
            addWay(OsmWay(1, listOf(1, 2), TestGraphs.RESIDENTIAL))
        }) { _, _ -> 0.0 }
        assertEquals(0, le(unnamed.serialize()).getInt(32))
        assertEquals(emptyList<String>(), RoadGraph.deserialize(unnamed.serialize()).names)
    }

    @Test
    fun `sıklaştırma, 3'lü ortalama, minGradeRun ve tırmanış sayıları`() {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(60.0), lon(0.0))
        raw.addWay(OsmWay(1, listOf(1, 2), TestGraphs.RESIDENTIAL))
        // Yükseklik kuzeye doğru metre başına 0.1 m (6 m tırmanış).
        val g = GraphBuilder.buildRoadGraph(raw) { la, _ -> (la - TestGraphs.LAT0) * 111320 * 0.1 }
        val d = Geo.haversine(lat(0.0), lon(0.0), lat(60.0), lon(0.0))
        assertEquals(60.0, d, 0.1) // test ızgarası 111320 m/derece, haversine R = 6 371 km
        // 60 m → ceil(60/25) = 3 adım: 2 ara nokta, 4 geometri noktası.
        assertEquals(4, g.edgeGeomCount[0])
        assertEquals(lat(20.0), g.geomLat[1], 1e-9)
        assertEquals(lat(40.0), g.geomLat[2], 1e-9)
        // İç noktalar 3'lü ortalama (doğrusal yükselişte değişmez); uçlar ham.
        assertEquals(0.0, g.geomEle[0].toDouble(), 1e-3)
        assertEquals(2.0, g.geomEle[1].toDouble(), 1e-3)
        assertEquals(6.0, g.geomEle[3].toDouble(), 1e-3)
        assertEquals(d.toFloat(), g.edgeLen[0], 1e-3f)
        assertEquals(d.toFloat(), g.upLenF[0], 1e-3f)
        assertEquals(6.0f, g.climbF[0], 1e-3f)
        assertEquals(0f, g.climbB[0], 0f)
        // Parça ≈ 20 m ≥ minGradeRun: eğim ≈ 2/20 = 0.1.
        assertEquals(0.1f, g.maxGradeF[0], 1e-3f)

        // Kısa parça (10 m) eğimi 20 m'ye bölünür: 1 m / 20 = 0.05 (0.1 değil).
        val short = GraphBuilder.buildRoadGraph(OsmRaw().apply {
            addNode(1, lat(0.0), lon(0.0)); addNode(2, lat(10.0), lon(0.0))
            addWay(OsmWay(1, listOf(1, 2), TestGraphs.RESIDENTIAL))
        }) { la, _ -> (la - TestGraphs.LAT0) * 111320 * 0.1 }
        assertEquals(2, short.edgeGeomCount[0])
        assertEquals(0.05f, short.maxGradeF[0], 1e-4f)
        assertEquals(0.0f, short.maxGradeB[0], 0f)
        assertEquals(short.edgeLen[0], short.upLenF[0], 0f)

        // Tepe: 3'lü ortalama ham tepeyi yayar (iki iç nokta: 0, 9, 9, 0 → 3, 6 değil: (0+9+9)/3 = 6, (9+9+0)/3 = 6).
        val peak = GraphBuilder.buildRoadGraph(raw) { la, _ -> if ((la - TestGraphs.LAT0) * 111320 in 10.0..50.0) 9.0 else 0.0 }
        assertEquals(listOf(0.0, 6.0, 6.0, 0.0), (0 until 4).map { peak.geomEle[it].toDouble() })
        assertEquals(6.0f, peak.climbF[0], 1e-4f)
        assertEquals(6.0f, peak.climbB[0], 1e-4f)
    }

    @Test
    fun `ad önceliği name, name-az, name-en ve boş name adsız`() {
        fun nameOf(tags: Map<String, String>): String {
            val raw = OsmRaw()
            raw.addNode(1, lat(0.0), lon(0.0)); raw.addNode(2, lat(10.0), lon(0.0))
            raw.addWay(OsmWay(1, listOf(1, 2), TestGraphs.RESIDENTIAL + tags))
            return GraphBuilder.buildRoadGraph(raw) { _, _ -> 0.0 }.nameOf(0)
        }
        assertEquals("N", nameOf(mapOf("name" to "N", "name:az" to "A", "name:en" to "E")))
        assertEquals("A", nameOf(mapOf("name:az" to "A", "name:en" to "E")))
        assertEquals("E", nameOf(mapOf("name:en" to "E")))
        assertEquals("", nameOf(mapOf("name" to "", "name:az" to "A"))) // Dart `??` yalnız null'a bakar
    }

    @Test
    fun `düğüm seçimi, kapalı yol döngüsü, atlanan servis yolları ve yaya bölgesi`() {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(0.0), lon(30.0))
        raw.addNode(3, lat(30.0), lon(30.0))
        raw.addNode(4, lat(30.0), lon(0.0))
        raw.addNode(9, lat(100.0), lon(100.0)) // hiçbir yolda değil → graf düğümü değil
        raw.addWay(OsmWay(1, listOf(1, 2, 3, 4, 1), TestGraphs.RESIDENTIAL)) // kapalı, iç kavşak yok
        raw.addWay(OsmWay(2, listOf(2, 3), mapOf("highway" to "service", "service" to "drive-through")))
        raw.addWay(OsmWay(3, listOf(1, 99), TestGraphs.RESIDENTIAL)) // bilinmeyen düğüm → atlanır
        raw.addWay(OsmWay(4, listOf(1, 2, 3, 1), mapOf("highway" to "pedestrian", "area" to "yes")))
        raw.addWay(OsmWay(5, listOf(1, 2), mapOf("highway" to "bridleway")))
        val g = GraphBuilder.buildRoadGraph(raw) { _, _ -> 0.0 }
        // Kapalı yolun başı/sonu aynı düğüm iki kez kullanılır → tek graf düğümü; from == to döngü kenarı.
        assertEquals(1, g.nodeCount)
        assertEquals(1, g.edgeCount)
        assertEquals(0, g.edgeFrom[0])
        assertEquals(0, g.edgeTo[0])
        assertEquals(2, g.degree(0)) // döngü kenarı 2 sayılır
        assertEquals(1, g.zoneCount)
        assertTrue(g.inPedestrianZone(lat(10.0), lon(20.0)))
        assertFalse(g.inPedestrianZone(lat(25.0), lon(5.0)))
        assertEquals(listOf(1L), g.wayOsmId.toList())
    }

    @Test
    fun `komşuluk kenar sırasıyla, adjDir uç bilgisini taşır`() {
        val g = TestGraphs.directAndDetour()
        // Düğüm sırası ham iç indeks: A(1) 0, B(3) 1, E(4) 2, F(5) 3. Kenarlar: 0 A-B, 1 A-E, 2 E-F, 3 F-B.
        assertEquals(listOf(0, 1), (g.adjStart[0] until g.adjStart[1]).map { g.adjEdge[it] })
        assertEquals(listOf(0, 0), (g.adjStart[0] until g.adjStart[1]).map { g.adjDir[it].toInt() })
        assertEquals(listOf(0, 3), (g.adjStart[1] until g.adjStart[2]).map { g.adjEdge[it] })
        assertEquals(listOf(1, 1), (g.adjStart[1] until g.adjStart[2]).map { g.adjDir[it].toInt() })
        assertEquals(2, g.degree(2))
    }

    @Test
    fun `geometri aralığı uçları ara değerle verir, ters sıra`() {
        val g = TestGraphs.directAndDetour(hill = 10.0)
        val e = TestGraphs.edgeOf(g, 100)
        val full = g.geometry(e)
        assertEquals(g.edgeGeomCount[e], full.size + 0) // f0=0, f1=1: uçlar ham noktalar, aradakiler dahil
        val half = g.geometry(e, 0.75, 0.25)
        assertEquals(lat(100.0), half.first().lat, 1e-6)
        assertEquals(lat(300.0), half.last().lat, 1e-6)
        val rev = g.geometry(e, 0.25, 0.75, reverse = true)
        assertEquals(half.reversed(), rev)
        assertEquals(10.0 * 0.5, rev.first().ele, 0.2) // tepe 200 m'de 10 m; 300 m'de ~5 m
    }

    @Test
    fun `HGT big-endian örnekleme, boşluk ortalaması ve kenar`() {
        val size = 1201
        val buf = ByteBuffer.allocate(size * size * 2).order(ByteOrder.BIG_ENDIAN)
        fun put(r: Int, c: Int, v: Int) = buf.putShort((r * size + c) * 2, v.toShort())
        put(0, 0, 100); put(0, 1, 200); put(1, 0, 300); put(1, 1, 400)
        val t = HgtTile(40, 49, buf.array())
        assertEquals(1201, t.size)
        val step = 1.0 / (size - 1)
        // Kuzeybatı köşesi tam 100; çift doğrusal orta nokta 250.
        assertEquals(100.0, t.sample(41.0, 49.0)!!, 1e-9)
        assertEquals(250.0, t.sample(41.0 - step / 2, 49.0 + step / 2)!!, 1e-6)
        // Bir köşe boşluk → geçerli köşelerin ağırlıksız ortalaması (100 + 200 + 300) / 3 = 200.
        put(1, 1, -32768)
        assertEquals(200.0, t.sample(41.0 - step / 4, 49.0 + step / 4)!!, 1e-9)
        put(0, 0, -32768); put(0, 1, -32768); put(1, 0, -32768)
        assertNull(t.sample(41.0 - step / 4, 49.0 + step / 4))
        // Karo dışı null; güney-doğu kenarı (fy = size−1) son hücreye kenetlenir.
        assertNull(t.sample(41.0001, 49.5))
        assertNull(t.sample(40.5, 48.9999))
        assertNotNull(t.sample(40.0, 50.0))
        assertEquals("N40E049", HgtTile.nameFor(40, 49))
        assertEquals("S05W003", HgtTile.nameFor(-5, -3))
        try {
            HgtTile(40, 49, ByteArray(10))
            throw AssertionError("FormatException bekleniyordu")
        } catch (e: FormatException) {
            assertEquals("Beklenmeyen HGT boyutu: 10", e.message)
        }
        // Model: veri yoksa 0 (düz); karo yoksa 0.
        val m = ElevationModel(listOf(HgtTile(40, 49, ByteBuffer.allocate(size * size * 2).also {
            for (i in 0 until size * size) it.putShort(i * 2, 50)
        }.array())))
        assertEquals(50.0, m.at(40.5, 49.5), 0.0)
        assertEquals(0.0, m.at(40.5, 50.5), 0.0)
        assertEquals(listOf(40 to 49, 40 to 50), ElevationModel.tilesFor(Baku.wideBox))
    }

    @Test
    fun `wideBox 91 ve 28 karoya bölünür, sıra güneyden kuzeye ve batıdan doğuya`() {
        val tiles = Baku.wideBox.split(0.055)
        assertEquals(91, tiles.size)
        assertEquals(28, Baku.wideBox.split(0.1).size)
        assertEquals(Baku.wideBox.south, tiles[0].south, 0.0)
        assertEquals(Baku.wideBox.west, tiles[0].west, 0.0)
        assertEquals(tiles[0].south, tiles[12].south, 0.0) // 13 sütun aynı satırda
        assertTrue(tiles[13].south > tiles[12].south)
        assertEquals(Baku.wideBox.east, tiles[90].east, 1e-12)
        assertEquals(1, BBox(0.0, 0.0, 0.0, 0.0).split(0.1).size)
        assertEquals("40.28,49.58,40.65,50.25", Baku.wideBox.overpass)
        assertEquals("40.28_49.58_40.332857142857144_49.63153846153846.json.gz", MapDownload.partName(tiles[0]))
    }

    @Test
    fun `nearestEdge yön süzgeci ve contains payı`() {
        val g = TestGraphs.directAndDetour()
        val direct = TestGraphs.edgeOf(g, 100) // kuzey-güney
        val ae = TestGraphs.edgeOf(g, 201) // doğu-batı
        // (30 doğu, 30 kuzey): doğrudan yola 30 m, A-E yoluna 30 m: eşitlikte ilk taranan; yönle ayrılır.
        assertEquals(direct, g.nearestEdge(lat(40.0), lon(30.0))!!.edge)
        assertEquals(ae, g.nearestEdge(lat(40.0), lon(30.0), heading = 90.0)!!.edge)
        assertEquals(ae, g.nearestEdge(lat(40.0), lon(30.0), heading = 270.0)!!.edge) // ters yön de kabul
        assertEquals(direct, g.nearestEdge(lat(40.0), lon(30.0), heading = 180.0)!!.edge)
        assertNull(g.nearestEdge(lat(40.0), lon(30.0), heading = 45.0, headingTol = 30.0))
        val hit = g.nearestEdge(lat(100.0), lon(5.0))!!
        assertEquals(0.25, hit.fraction, 1e-3)
        assertEquals(5.0, hit.dist, 0.05)
        assertEquals(0.0, hit.segBearing, 0.01)
        assertNull(g.nearestEdge(lat(100.0), lon(100.0), maxDist = 60.0)) // 100 m uzak
        assertEquals(listOf(direct, TestGraphs.edgeOf(g, 202)), g.nearestEdges(lat(200.0), lon(100.0), maxDist = 120.0).map { it.edge }.sorted())
        // contains: ızgara sınırı + 100 m pay (enlem 1/111320, boylam 1/85000 derece/m).
        assertTrue(g.contains(lat(-90.0), lon(0.0)))
        assertFalse(g.contains(lat(-110.0), lon(0.0)))
        assertFalse(g.contains(lat(0.0), TestGraphs.LON0 - 101.0 / 85000))
        assertTrue(g.contains(lat(0.0), TestGraphs.LON0 - 99.0 / 85000))
        assertFalse(GraphBuilder.buildRoadGraph(OsmRaw()) { _, _ -> 0.0 }.contains(0.0, 0.0))
        assertNull(GraphBuilder.buildRoadGraph(OsmRaw()) { _, _ -> 0.0 }.nearestEdge(0.0, 0.0))
    }

    @Test
    fun `kısa ya da bozuk dosya FormatException`() {
        for (b in listOf(ByteArray(0), ByteArray(12), TestGraphs.parallel().serialize().copyOf(100))) {
            try {
                RoadGraph.deserialize(b)
                throw AssertionError("FormatException bekleniyordu (${b.size})")
            } catch (_: FormatException) {
            }
        }
    }

    @Test
    fun `Overpass sorgusu YolGo metniyle aynı`() {
        val q = Overpass.query(BBox(1.0, 2.0, 3.0, 4.0))
        val expected = """
            [out:json][timeout:90];
            (
              way["highway"~"^(trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|service|road|track|cycleway|path|footway|pedestrian|steps)${'$'}"]["area"!="yes"]["access"!~"^(private|no)${'$'}"]["service"!~"^(parking_aisle|driveway)${'$'}"](1.0,2.0,3.0,4.0);
              way["highway"="pedestrian"]["area"="yes"](1.0,2.0,3.0,4.0);
              way["leisure"~"^(park|garden)${'$'}"](1.0,2.0,3.0,4.0);
              way["landuse"="recreation_ground"](1.0,2.0,3.0,4.0);
            );
            out body qt;
            >;
            out skel qt;
        """.trimIndent() + "\n"
        assertEquals(expected, q)
    }
}
