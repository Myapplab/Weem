package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.net.HttpPort
import com.kuryelog.logic.map.net.HttpRequest
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/**
 * "İndir / güncelle" arka plan işi (`map_data_service.dart` `_buildEntry`, `_loadPrebuilt`; MAP.md 2.9): SRTM → hazır veri
 * sunucusu ya da Overpass karoları (kaldığı yerden) → `buildRoadGraph` → atomik yazım. Dosya ve ağ portlarla; saf JVM.
 */
class MapDownload(
    private val http: HttpPort,
    private val userAgent: String,
    private val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    private val nowMs: () -> Long = System::currentTimeMillis,
    /** Hazır veri sunucusu (MAP.md 17.1 #12 varsayılanı (a): zincir aynen). */
    private val prebuiltBase: String = PREBUILT_BASE,
) {
    /**
     * [box] bölgesini indirip grafı `<dir>/<fileName>`'e yazar. İlerleme [progress] (0–1, mesaj). Hata [MapDataException]
     * (`0 < kaydedilen < toplam` parçada "kaldığı yerden" satırıyla).
     */
    suspend fun run(box: BBox, dir: File, fileName: String, progress: (Double, MapMsg) -> Unit) {
        // İnen yol parçaları burada saklanır: indirme yarıda kalırsa tekrar basınca kaldığı yerden devam eder.
        val parts = File(dir, "parts_" + fileName.replace(".bin", ""))
        var saved = 0
        var total = 0
        try {
            progress(0.02, MapMsg.ELEVATION)
            val elevation = ElevationModel.ensure(box, File(dir, "dem"), http, userAgent) { progress(0.05, it) }

            val tiles = box.split(TILE_DEG)
            total = tiles.size
            val raw = OsmRaw()
            val fromServer = try {
                loadPrebuilt(box, raw) { v, s -> progress(0.15 + 0.65 * v, s) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false // sunucuya ulaşılamadı → Overpass
            }
            if (!fromServer) {
                parts.mkdirs()
                dropStaleParts(parts)
                val fetcher = OverpassFetcher(http, userAgent, delay)
                for (i in tiles.indices) {
                    val v = 0.15 + 0.65 * i / tiles.size
                    progress(v, MapMsg.tile(i + 1, tiles.size))
                    val part = File(parts, partName(tiles[i]))
                    var json: JSONObject? = null
                    if (part.exists()) {
                        try {
                            json = decodeJson(gunzip(part.readBytes()))
                        } catch (_: Exception) {
                            part.delete()
                        }
                    }
                    if (json == null) {
                        val bytes = fetcher.fetch(tiles[i], log = { progress(v, MapMsg.tileLog(i + 1, tiles.size, it)) })
                        json = decodeJson(bytes)
                        part.writeBytes(gzip(bytes))
                    }
                    raw.addOverpassJson(json)
                    saved = i + 1
                }
            }

            progress(0.82, MapMsg.buildingGraph(raw.ways.size))
            val graph = GraphBuilder.buildRoadGraph(raw, elevation::at)
            if (graph.edgeCount == 0) throw MapDataException(MapMsg.NO_ROADS)

            progress(0.95, MapMsg.SAVING)
            AtomicFile.write(File(dir, fileName), graph.serialize())
            runCatching { parts.deleteRecursively() }
            progress(1.0, MapMsg.DONE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            var msg = MapMsg.of(e)
            if (saved in 1 until total) msg = MapMsg.partsSaved(msg, saved, total)
            throw MapDataException(msg)
        }
    }

    /** true: bölgenin tamamı hazır veri sunucusundan geldi. [progress] 0..1 alır. */
    private suspend fun loadPrebuilt(box: BBox, raw: OsmRaw, progress: (Double, MapMsg) -> Unit): Boolean {
        progress(0.0, MapMsg.PREBUILT_SEARCH)
        val res = get("$prebuiltBase/manifest.json", 20_000)
        if (res.status != 200) return false
        val manifest = runCatching { JSONObject(String(res.raw, Charsets.UTF_8)) }.getOrNull() ?: return false
        if (manifest.opt("format") != 1) return false
        val regions = manifest.optJSONObject("regions") ?: return false
        var parts: List<String>? = null
        for (key in regions.keys()) {
            val r = regions.opt(key) as? JSONObject ?: continue
            val b = r.opt("box") as? JSONArray ?: continue
            if (b.length() == 4 && sameBox(b, box)) {
                val p = r.getJSONArray("parts")
                parts = List(p.length()) { p.get(it).toString() }
                break
            }
        }
        if (parts.isNullOrEmpty()) return false
        for (i in parts.indices) {
            progress(i.toDouble() / parts.size, MapMsg.prebuiltPart(i + 1, parts.size))
            val r = get("$prebuiltBase/${parts[i]}", 90_000)
            if (r.status != 200) return false
            val bytes = r.raw
            raw.addOverpassJson(decodeJson(if (isGzip(bytes)) gunzip(bytes) else bytes))
        }
        return true
    }

    private suspend fun get(url: String, timeoutMs: Long) =
        http.send(HttpRequest("GET", url, headers = mapOf("User-Agent" to userAgent), timeoutMs = timeoutMs, binary = true))

    /** İki günden eski parçalar kullanılmaz (aylık güncellemede taze veri insin). */
    private fun dropStaleParts(dir: File) {
        val cutoff = nowMs() - STALE_MS
        dir.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
    }

    companion object {
        /** Kendi veri sunucumuz (YolGo `prebuiltBase`); erişilemezse ≤ 20 sn sonra Overpass. */
        const val PREBUILT_BASE = "https://github.com/mehemmedsh/kurye_nav_data/releases/latest/download"

        /** Overpass karo boyu: `wideBox` → 7 × 13 = 91 karo. */
        const val TILE_DEG = 0.055
        const val STALE_MS = 2L * 24 * 60 * 60 * 1000

        /** Karo parça dosyası: `<s>_<w>_<n>_<e>.json.gz` (yalnız önbellek). */
        fun partName(tile: BBox) = tile.overpass.replace(',', '_') + ".json.gz"

        internal fun sameBox(b: JSONArray, box: BBox): Boolean {
            fun v(i: Int) = (b.opt(i) as? Number)?.toDouble() ?: Double.NaN
            val eps = 1e-6
            return abs(v(0) - box.south) < eps && abs(v(1) - box.west) < eps && abs(v(2) - box.north) < eps && abs(v(3) - box.east) < eps
        }

        internal fun decodeJson(bytes: ByteArray) = JSONObject(String(bytes, Charsets.UTF_8))

        internal fun gzip(bytes: ByteArray): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(bytes) }
            return out.toByteArray()
        }
    }
}
