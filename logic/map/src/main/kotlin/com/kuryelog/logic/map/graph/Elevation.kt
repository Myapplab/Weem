package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.net.HttpPort
import com.kuryelog.logic.map.net.HttpRequest
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.floor

/** SRTM (HGT) 1°×1° karosu (MAP.md 2.7). Veri **big-endian** int16, satır 0 = kuzey kenarı; −32768 = boşluk. */
class HgtTile(val lat0: Int, val lon0: Int, private val data: ByteArray) {
    /** 3601 (1") ya da 1201 (3"). */
    val size: Int = when (data.size) {
        3601 * 3601 * 2 -> 3601
        1201 * 1201 * 2 -> 1201
        else -> throw FormatException("Beklenmeyen HGT boyutu: ${data.size}")
    }

    private fun at(row: Int, col: Int): Int? {
        val i = (row * size + col) * 2
        val v = ((data[i].toInt() shl 8) or (data[i + 1].toInt() and 0xFF)).toShort().toInt()
        return if (v == -32768) null else v
    }

    /** Çift doğrusal örnekleme; köşelerden biri boşluksa geçerli köşelerin ağırlıksız ortalaması (hepsi boşsa null). */
    fun sample(lat: Double, lon: Double): Double? {
        val fy = (lat0 + 1 - lat) * (size - 1)
        val fx = (lon - lon0) * (size - 1)
        if (fy < 0 || fx < 0 || fy > size - 1 || fx > size - 1) return null
        val r = Geo.clampInt(floor(fy).toInt(), 0, size - 2)
        val c = Geo.clampInt(floor(fx).toInt(), 0, size - 2)
        val dy = fy - r
        val dx = fx - c
        val v00 = at(r, c)
        val v01 = at(r, c + 1)
        val v10 = at(r + 1, c)
        val v11 = at(r + 1, c + 1)
        if (v00 == null || v01 == null || v10 == null || v11 == null) {
            val ok = listOfNotNull(v00, v01, v10, v11)
            return if (ok.isEmpty()) null else ok.sum().toDouble() / ok.size
        }
        val top = v00 + (v01 - v00) * dx
        val bottom = v10 + (v11 - v10) * dx
        return top + (bottom - top) * dy
    }

    companion object {
        /** `N40E049` biçimi: enlem 2, boylam 3 hane. */
        fun nameFor(lat: Int, lon: Int): String {
            val ns = if (lat >= 0) 'N' else 'S'
            val ew = if (lon >= 0) 'E' else 'W'
            return "$ns${abs(lat).toString().padStart(2, '0')}$ew${abs(lon).toString().padStart(3, '0')}"
        }
    }
}

/** Bir ya da daha çok HGT karosundan yükseklik (MAP.md 2.7). */
class ElevationModel(val tiles: List<HgtTile>) {
    val available: Boolean get() = tiles.isNotEmpty()

    /** Karolar sırayla; veri yoksa 0 (düz kabul edilir). */
    fun at(lat: Double, lon: Double): Double {
        for (t in tiles) {
            if (lat >= t.lat0 && lat <= t.lat0 + 1 && lon >= t.lon0 && lon <= t.lon0 + 1) {
                val v = t.sample(lat, lon)
                if (v != null) return v
            }
        }
        return 0.0
    }

    companion object {
        val URLS = listOf(
            "https://s3.amazonaws.com/elevation-tiles-prod/skadi/{DIR}/{NAME}.hgt.gz",
            "https://elevation-tiles-prod.s3.amazonaws.com/skadi/{DIR}/{NAME}.hgt.gz",
        )

        /** Kutunun tam derece karoları, güneyden kuzeye, batıdan doğuya (Bakü: N40E049, N40E050). */
        fun tilesFor(box: BBox): List<Pair<Int, Int>> = buildList {
            for (lat in floor(box.south).toInt()..floor(box.north).toInt()) {
                for (lon in floor(box.west).toInt()..floor(box.east).toInt()) add(lat to lon)
            }
        }

        /**
         * Gerekli karoları [dir] altına indirir (yoksa) ve yükler. İndirilemeyen karo atlanır (model orada düz kalır);
         * okunamayan dosya silinir (sonraki sefer yeniden iner). Anahtar gerekmez.
         */
        suspend fun ensure(box: BBox, dir: File, http: HttpPort, userAgent: String, log: ((MapMsg) -> Unit)? = null): ElevationModel {
            val tiles = ArrayList<HgtTile>()
            dir.mkdirs()
            for ((lat, lon) in tilesFor(box)) {
                val name = HgtTile.nameFor(lat, lon)
                val file = File(dir, "$name.hgt")
                if (!file.exists()) {
                    var raw: ByteArray? = null
                    for (u in URLS) {
                        try {
                            val url = u.replace("{DIR}", name.substring(0, 3)).replace("{NAME}", name)
                            log?.invoke(MapMsg.demDownloading(name))
                            val res = http.send(
                                HttpRequest("GET", url, headers = mapOf("User-Agent" to userAgent), timeoutMs = 180_000, binary = true),
                            )
                            if (res.status == 200) {
                                raw = gunzip(res.raw)
                                break
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // sonraki adresi dene
                        }
                    }
                    if (raw == null) {
                        log?.invoke(MapMsg.demFailed(name))
                        continue
                    }
                    AtomicFile.write(file, raw)
                }
                try {
                    tiles += HgtTile(lat, lon, file.readBytes())
                } catch (_: Exception) {
                    file.delete()
                }
            }
            return ElevationModel(tiles)
        }

        /** Yalnız diskte olanları yükler (ağ yok). */
        fun loadLocal(box: BBox, dir: File): ElevationModel {
            val tiles = ArrayList<HgtTile>()
            for ((lat, lon) in tilesFor(box)) {
                val file = File(dir, "${HgtTile.nameFor(lat, lon)}.hgt")
                if (file.exists()) runCatching { tiles += HgtTile(lat, lon, file.readBytes()) }
            }
            return ElevationModel(tiles)
        }
    }
}

internal fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }

internal fun isGzip(bytes: ByteArray) = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
