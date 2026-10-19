package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.net.HttpPort
import com.kuryelog.logic.map.net.HttpRequest
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min

/** `lib/data/overpass.dart` (MAP.md 2.8). Ağ `HttpPort` ile. */
object Overpass {
    /** Scooter için anlamlı yol tipleri (park ve yaya alanı dahil). Özel/erişimsiz yollar dışarıda. Metin aynen YolGo'nun. */
    fun query(b: BBox): String {
        val bb = b.overpass
        return "[out:json][timeout:90];\n" +
            "(\n" +
            "  way[\"highway\"~\"^(trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified|" +
            "residential|living_street|service|road|track|cycleway|path|footway|pedestrian|steps)$\"][\"area\"!=\"yes\"]" +
            "[\"access\"!~\"^(private|no)$\"][\"service\"!~\"^(parking_aisle|driveway)$\"]($bb);\n" +
            "  way[\"highway\"=\"pedestrian\"][\"area\"=\"yes\"]($bb);\n" +
            "  way[\"leisure\"~\"^(park|garden)$\"]($bb);\n" +
            "  way[\"landuse\"=\"recreation_ground\"]($bb);\n" +
            ");\n" +
            "out body qt;\n" +
            ">;\n" +
            "out skel qt;\n"
    }

    val MIRRORS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    )

    private val REMARK = Regex("\"remark\"\\s*:\\s*\"([^\"]*)")

    /**
     * Sunucu 200 dönse bile cevabın sonuna "runtime error" notu düşebilir (zaman aşımı, bellek): o zaman yollar eksiktir.
     * Sorun yoksa null; varsa metin ("boş cevap", "JSON değil" ya da `remark` değeri).
     */
    fun problem(body: ByteArray): String? {
        if (body.isEmpty()) return "boş cevap"
        val head = String(body, 0, min(body.size, 64), Charsets.UTF_8).trimStart()
        if (!head.startsWith("{")) return "JSON değil"
        val from = max(0, body.size - 1500)
        val tail = String(body, from, body.size - from, Charsets.UTF_8)
        val remark = REMARK.find(tail)?.groupValues?.get(1)
        return if (remark != null && remark.contains("error")) remark else null
    }
}

/**
 * Overpass aynalarını dolaşır: 3 tur × 4 ayna, en son cevap veren ayna sonraki karoda ilk denenir; turlar arası 15 ve 30 sn.
 * Hepsi düşerse [MapDataException] `Yol verisi indirilemedi (<son hata>)`.
 */
class OverpassFetcher(
    private val http: HttpPort,
    private val userAgent: String,
    private val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    private var preferred = 0

    /** Karonun ham JSON baytları (diske olduğu gibi saklanabilsin diye). [query] verilmezse yol sorgusu. */
    suspend fun fetch(tile: BBox, log: ((MapMsg) -> Unit)? = null, query: String? = null): ByteArray {
        val q = query ?: Overpass.query(tile)
        val form = "data=" + URLEncoder.encode(q, "UTF-8")
        var lastError: MapMsg? = null
        for (round in 0 until ROUNDS) {
            for (k in Overpass.MIRRORS.indices) {
                val idx = (preferred + k) % Overpass.MIRRORS.size
                val url = Overpass.MIRRORS[idx]
                val host = URI(url).host
                try {
                    val res = http.send(
                        HttpRequest(
                            method = "POST",
                            url = url,
                            headers = mapOf("User-Agent" to userAgent, "Content-Type" to "application/x-www-form-urlencoded; charset=utf-8"),
                            body = form,
                            timeoutMs = 120_000,
                            binary = true,
                        ),
                    )
                    if (res.status == 200) {
                        val bytes = res.raw
                        val problem = Overpass.problem(bytes)
                        if (problem == null) {
                            preferred = idx
                            return bytes
                        }
                        lastError = MapMsg.mirrorProblem(host, problem)
                        log?.invoke(MapMsg.incomplete(host))
                    } else {
                        lastError = MapMsg.mirrorHttp(host, res.status)
                        log?.invoke(MapMsg.busy(host, res.status))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SocketTimeoutException) {
                    lastError = MapMsg.mirrorNoAnswer(host)
                    log?.invoke(MapMsg.noAnswer(host))
                } catch (e: IOException) {
                    lastError = MapMsg.mirrorProblem(host, e.message ?: e.toString())
                    log?.invoke(MapMsg.connError(host))
                }
            }
            if (round < ROUNDS - 1) {
                val wait = 15 * (round + 1)
                log?.invoke(MapMsg.allBusy(wait))
                delay(wait * 1000L)
            }
        }
        throw MapDataException(MapMsg.downloadFailed(lastError))
    }

    private companion object {
        const val ROUNDS = 3
    }
}
