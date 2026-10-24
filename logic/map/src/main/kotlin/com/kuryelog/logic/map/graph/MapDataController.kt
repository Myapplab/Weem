package com.kuryelog.logic.map.graph

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.Baku
import com.kuryelog.logic.map.settings.MapPrefs
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.zip.GZIPInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Uygulamayla gelen `assets/map/` dosyaları (Android `AssetManager`; testte bellek içi). Yoksa null. */
fun interface MapAssets {
    fun read(name: String): ByteArray?

    /** Akış olarak (S39: büyük dosya belleğe bütün alınmaz); Android `AssetManager.open`. Yoksa null. */
    fun open(name: String): InputStream? = read(name)?.inputStream()
}

/**
 * Harita verisi durumu (`MapDataController`, MAP.md 2.9): gömülü haritayı açar, diskteki grafı yükler (ağ açmaz), elle
 * "İndir / güncelle". Güncelleme sürerken ve başarısız olunca eski harita bellekte kalır. Android `MapData` bunu sarar.
 */
class MapDataController(
    /** `<filesDir>/mapdata`. */
    private val dir: File,
    private val assets: MapAssets,
    private val prefs: MapPrefs,
    private val download: MapDownload,
    private val lang: () -> Lang,
    /** Dosya okuma, gzip açma ve graf çözme. */
    private val work: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow(MapDataState(MapDataPhase.CHECKING))
    val state: StateFlow<MapDataState> = _state

    private var current: MapDataState
        get() = _state.value
        set(v) {
            _state.value = v
        }

    private fun t(m: MapMsg) = m.get(lang())

    /** Gömülü haritanın tarihi (UTC `YYYY-AA-GGTSS:DD`); yoksa null. */
    val bundledVersion: String? get() = prefs.getString(KEY_BUNDLED_AT)

    /** Diskteki hazır haritayı yükler; yoksa `missing`. Açılışta ve bölge değişince; ağ açmaz. */
    suspend fun load(region: String) {
        dir.mkdirs()
        installBundled(region)
        val file = graphFile(region)
        if (!file.exists()) {
            current = MapDataState(MapDataPhase.MISSING)
            return
        }
        try {
            val loaded = withContext(work) {
                Loaded(
                    readGraph(file),
                    ElevationModel.loadLocal(Baku.wideBox, File(dir, "dem")),
                    readPois(region),
                    file.lastModified(),
                    file.length(),
                )
            }
            current = MapDataState(
                MapDataPhase.READY,
                graph = loaded.graph,
                elevation = loaded.elevation,
                builtAtMs = loaded.builtAtMs,
                sizeBytes = loaded.size,
                pois = loaded.pois,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            current = MapDataState(MapDataPhase.MISSING, error = t(MapMsg.readFailed(MapMsg.of(e))))
        } catch (e: OutOfMemoryError) {
            // S39: bellek yetmezse uygulama düşmez; kurulum kartı nedeni söyler.
            current = MapDataState(MapDataPhase.MISSING, error = t(MapMsg.readFailed(MapMsg.of(e))))
        }
    }

    /**
     * Grafı dosyadan okur (S39): dosya bellek eşlemeli (Java yığınına ~60 MB kopya yok), diziler toplu okunur; önceden `readBytes` + tek tek
     * okuma telefonda hem yığını iki kez dolduruyor hem açılışı saniyelerce uzatıyordu.
     */
    private fun readGraph(file: File): RoadGraph =
        RandomAccessFile(file, "r").use { f -> RoadGraph.deserialize(f.channel.map(FileChannel.MapMode.READ_ONLY, 0, f.length())) }

    private class Loaded(val graph: RoadGraph, val elevation: ElevationModel, val pois: PoiSet?, val builtAtMs: Long, val size: Long)

    /**
     * Bölgeyi indirip grafı kurar ("İndir / güncelle"; yalnız elle). `building` sürerken ikinci çağrı yok sayılır. İndirilen graf
     * her zaman `wideBox`; [region] yalnız dosya adı.
     */
    suspend fun download(region: String) {
        if (current.phase == MapDataPhase.BUILDING) return
        dir.mkdirs()
        val old = current
        fun building(v: Double, m: MapMsg) =
            MapDataState(MapDataPhase.BUILDING, v, t(m), graph = old.graph, elevation = old.elevation, pois = old.pois)
        current = building(0.01, MapMsg.STARTING)
        var err: MapMsg? = null
        try {
            withContext(work) {
                download.run(Baku.wideBox, dir, graphName(region)) { v, m -> current = building(v, m) }
            }
        } catch (e: CancellationException) {
            current = old // iptal (ekran kapandı): eski durum geri
            throw e
        } catch (e: Exception) {
            err = MapMsg.of(e)
            current = MapDataState.updateFailed(t(err), old.graph, old.elevation, old.pois)
        }
        load(region)
        if (err == null && current.ready) fetchPoisLater(region)
        if (err != null) {
            val cur = current
            current = if (cur.ready) {
                MapDataState(
                    MapDataPhase.READY,
                    graph = cur.graph,
                    elevation = cur.elevation,
                    builtAtMs = cur.builtAtMs,
                    sizeBytes = cur.sizeBytes,
                    pois = cur.pois,
                    error = t(MapMsg.updateFailed(err)),
                )
            } else {
                MapDataState(MapDataPhase.ERROR, error = t(err))
            }
        }
    }

    /** POI'ler güncellemede tazelenir (`_fetchPoisLater`): yol grafı hazır olduktan sonra, hata sessiz (harita eski yerlerle çalışır). */
    private suspend fun fetchPoisLater(region: String) {
        val bytes = download.downloadPois(Baku.wideBox) ?: return
        val set = runCatching { withContext(work) { AtomicFile.write(File(dir, poiName(region)), bytes); PoiSet.decode(bytes) } }.getOrNull() ?: return
        current = current.withPois(set)
    }

    /** `mapdata/` klasörünü siler; faz `missing` (adına rağmen yeniden yüklemez; YolGo davranışı). */
    suspend fun deleteAndReload() {
        withContext(work) { dir.deleteRecursively() }
        current = MapDataState(MapDataPhase.MISSING)
    }

    /**
     * Uygulamayla gelen (CI'da sürümde taze üretilen) haritayı diske açar. Sürüm değiştiyse eskisinin üzerine yazar (elle indirilmiş
     * daha yeni dosya olsa bile; YolGo davranışı). Gömülü harita yoksa sessizce geçer.
     */
    private suspend fun installBundled(region: String) {
        val man = runCatching { JSONObject(String(assets.read("manifest.json")!!, Charsets.UTF_8)) }.getOrNull() ?: return
        val version = man.opt("version") as? String
        if (version.isNullOrEmpty()) return
        val key = "map.bundled.$region"
        val graphFile = graphFile(region)
        if (prefs.getString(key) == version && graphFile.exists()) return
        current = MapDataState(MapDataPhase.UNPACKING, 0.3, t(MapMsg.UNPACKING))
        withContext(work) {
            File(dir, "dem").mkdirs()
            // S39: akışla açılır (önceden gzip ve açılmış graf birlikte bellekteydi: telefonda ~200 MB tepe → bellek hatası, kurulum kartı "İndir" istiyordu).
            fun unpack(asset: String, to: File) {
                val input = assets.open(asset) ?: throw java.io.FileNotFoundException(asset)
                GZIPInputStream(input, 64 * 1024).use { AtomicFile.write(to, it) }
            }
            try {
                // Gömülü graf yalnız "geniş" olabilir (merkezi de kapsar; APK küçük kalsın).
                val regions = man.opt("regions")
                val src = if (regions is JSONArray && (0 until regions.length()).any { regions.opt(it) == region }) region else "wide"
                unpack("graph_$src.bin.gz", graphFile)
                for (tile in BUNDLED_DEM) runCatching { unpack("$tile.hgt.gz", File(dir, "dem/$tile.hgt")) }
                runCatching { unpack("pois_wide.json.gz", File(dir, poiName(region))) }
                prefs.setString(key, version)
                prefs.setString(KEY_BUNDLED_AT, version)
            } catch (_: Exception) {
                // gömülü dosya yoksa indirme ekranı açılır
            } catch (_: OutOfMemoryError) {
                // akışla açıldığı için beklenmez; yine de uygulama düşmez
            }
        }
    }

    /** POI dosyası (bozuksa ya da çözücü yoksa null; harita yine çalışır). */
    private fun readPois(region: String): PoiSet? {
        val f = File(dir, poiName(region))
        if (!f.exists()) return null
        return runCatching { PoiSet.decode(f.readBytes()) }.getOrNull()
    }

    private fun graphFile(region: String) = File(dir, graphName(region))

    companion object {
        const val KEY_BUNDLED_AT = "map.bundledAt"
        val BUNDLED_DEM = listOf("N40E049", "N40E050")
        fun graphName(region: String) = "graph_$region.bin"
        fun poiName(region: String) = "pois_$region.json"
    }
}
