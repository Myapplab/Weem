package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.geo.Baku
import com.kuryelog.logic.map.net.HttpPort
import com.kuryelog.logic.map.net.UrlHttp
import com.kuryelog.logic.map.net.UserAgent
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * CI gömülü harita aracı (`tool/build_map.dart`, MAP.md 2.11): Bakü yol ağı + yükseklik + yerler → `assets/map`
 * (`graph_wide.bin.gz`, `N40E04x.hgt.gz`, `pois_*.json.gz`, `manifest.json`). Yol/yer dosyaları verilirse (Geofabrik'ten
 * `ci/osm_extract.py` ile hazırlanmış) Overpass'a gidilmez. Yalnız `apk.yml`'de çalışır (`ci/build_map.sh`).
 */
class MapBuild(
    private val http: HttpPort,
    private val userAgent: String,
    private val work: File,
    private val log: (String) -> Unit,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(ZoneOffset.UTC) },
) {
    suspend fun run(out: File, roadsFile: File?, poisFile: File?) {
        out.mkdirs()
        val wide = Baku.wideBox
        // 1) Yükseklik (SRTM) → .hgt.gz (Geniş Bakü ± 0,02° dışı 0)
        val demDir = File(work, "dem")
        val dem = ElevationModel.ensure(wide, demDir, http, userAgent) { log(it.tr) }
        if (!dem.available) throw IllegalStateException("yükseklik verisi yok")
        for (f in demDir.listFiles().orEmpty().sortedBy { it.name }) {
            if (!f.name.endsWith(".hgt")) continue
            File(out, "${f.name}.gz").writeBytes(MapDownload.gzip(maskOutside(f.readBytes(), f.name, wide)))
        }

        // 2) Yollar: hazır dosya (Geofabrik) ya da Overpass (0,1° parçalar)
        val raw = OsmRaw()
        val fetcher = OverpassFetcher(http, userAgent)
        if (roadsFile != null && roadsFile.exists()) {
            raw.addOverpassJson(JSONObject(roadsFile.readText()))
        } else {
            val tiles = wide.split(0.1)
            for (i in tiles.indices) {
                log("yol ${i + 1}/${tiles.size}")
                raw.addOverpassJson(MapDownload.decodeJson(fetcher.fetch(tiles[i], log = { log(it.tr) })))
            }
        }
        log("${raw.ways.size} yol, ${raw.lat.size} düğüm")

        // 3) Graf: geniş = hepsi; Merkez Bakü de bunu kullanır (geniş merkezi kapsar → APK küçük).
        val g = GraphBuilder.buildRoadGraph(raw, dem::at)
        if (g.edgeCount < MIN_EDGES) throw IllegalStateException("wide: çok az yol (${g.edgeCount})")
        File(out, "graph_wide.bin.gz").writeBytes(MapDownload.gzip(g.serialize()))
        log("wide: ${g.edgeCount} kenar")

        // 4) Yerler: restoran, AVM, metro, trafik ışığı (PoiSet A6.8'de; o zamana dek atlanır, manifest `pois` boş).
        val kinds = JSONObject()
        try {
            writePois(out, poisFile, fetcher, wide, kinds)
        } catch (e: NotImplementedError) {
            log("yerler: atlandı (${e.message})")
        }

        val version = now().withZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
        val manifest = JSONObject()
            .put("version", version)
            .put("format", RoadGraph.FORMAT_VERSION)
            .put("regions", JSONArray().put("wide"))
            .put("pois", kinds)
        File(out, "manifest.json").writeText(manifest.toString())
        log("tamam: $version")
    }

    private suspend fun writePois(out: File, poisFile: File?, fetcher: OverpassFetcher, wide: BBox, kinds: JSONObject) {
        val json = if (poisFile != null && poisFile.exists()) {
            JSONObject(poisFile.readText())
        } else {
            MapDownload.decodeJson(fetcher.fetch(wide, query = Pois.query(wide), log = { log(it.tr) }))
        }
        val pois = PoiSet(Pois.fromOverpass(json))
        File(out, "pois_wide.json.gz").writeBytes(MapDownload.gzip(pois.encode()))
        val cb = Baku.centralBox
        val central = PoiSet(pois.within(cb.south - 0.01, cb.west - 0.01, cb.north + 0.01, cb.east + 0.01))
        File(out, "pois_central.json.gz").writeBytes(MapDownload.gzip(central.encode()))
        for (p in pois.items) kinds.put(p.kind.toString(), kinds.optInt(p.kind.toString()) + 1)
        log("yerler: $kinds")
    }

    companion object {
        const val MIN_EDGES = 1000
        private val TILE_NAME = Regex("N(\\d+)E(\\d+)")

        /**
         * SRTM karosunda [box] (± 0,02°) dışını **0** yapar (boşluk değil): dosya biçimi aynı kalır, gzip o kısmı neredeyse
         * sıfıra indirir (~7 MB yerine ~0,5 MB).
         */
        fun maskOutside(raw: ByteArray, name: String, box: BBox): ByteArray {
            val m = TILE_NAME.find(name) ?: return raw
            val lat0 = m.groupValues[1].toInt()
            val lon0 = m.groupValues[2].toInt()
            var side = 1
            while (side.toLong() * side * 2 < raw.size) side++
            if (side * side * 2 != raw.size) return raw
            val out = raw.copyOf()
            val pad = 0.02
            val step = 1.0 / (side - 1)
            for (r in 0 until side) {
                val lat = lat0 + 1 - r * step
                val rowIn = lat >= box.south - pad && lat <= box.north + pad
                for (c in 0 until side) {
                    val lon = lon0 + c * step
                    if (rowIn && lon >= box.west - pad && lon <= box.east + pad) continue
                    val k = (r * side + c) * 2
                    out[k] = 0
                    out[k + 1] = 0
                }
            }
            return out
        }
    }
}

/** Kullanım: `MapBuildKt <çıktı> [yollar.json yerler.json]` (`./gradlew -p logic :map:buildMap --args=…`). */
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "map/src/main/assets/map" })
    val roads = args.getOrNull(1)?.let(::File)
    val pois = args.getOrNull(2)?.let(::File)
    val work = File(System.getProperty("java.io.tmpdir"), "weem_map").apply { mkdirs() }
    val start = System.nanoTime()
    runBlocking {
        MapBuild(UrlHttp(), UserAgent.of(System.getenv("WEEM_VERSION") ?: "ci"), work, { println("[harita] $it") }).run(out, roads, pois)
    }
    println("[harita] ${(System.nanoTime() - start) / 1_000_000_000} sn")
}
