package com.kuryelog.logic.map.input

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.Baku
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.Poi
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.net.HttpPort
import com.kuryelog.logic.map.net.HttpRequest
import com.kuryelog.logic.map.settings.MapPrefs
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Öneri satırı (MAP.md 8.8): [source] `google` | `ors` | `offline`; [point] Google önerisinde seçilene dek null. */
data class SearchItem(val id: String, val title: String, val address: String, val source: String, val point: GeoPoint? = null, val restaurant: Boolean = false) {
    val offline: Boolean get() = source == "offline"

    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("address", address).put("source", source)
        .put("point", point?.toJson() ?: JSONObject.NULL).put("restaurant", restaurant)

    companion object {
        fun fromJson(j: JSONObject) = SearchItem(
            j.opt("id").toString(), j.opt("title").toString(), j.opt("address")?.takeIf { it != JSONObject.NULL }?.toString() ?: "",
            j.opt("source").toString(), GeoPoint.fromJson(j.opt("point")), j.opt("restaurant") == true,
        )
    }
}

/** Arama çubuğunun durumu: [offline] öneriler çevrimdışı dizinden/son aramalardan geliyor; [error] kısa metin (dil seçili). */
data class SearchView(val items: List<SearchItem> = emptyList(), val busy: Boolean = false, val offline: Boolean = false, val error: String? = null)

/** Çevrimdışı dizin (MAP.md 8.8): adı olan POI'ler + graf yolları (OSM yolu başına bir); etiketler [lang]'e göre. */
object OfflineIndex {
    fun build(graph: RoadGraph?, pois: List<Poi>, lang: Lang): List<SearchItem> {
        val venue = tl(lang, "Bakü · kayıtlı mekân", "Bakı · qeydə alınmış obyekt")
        val street = tl(lang, "Bakü · sokak", "Bakı · küçə")
        val out = ArrayList<SearchItem>()
        for (p in pois) {
            if (p.name.isEmpty()) continue
            out += SearchItem(p.osmRef.ifEmpty { "${num(p.lat)},${num(p.lon)}" }, p.name, venue, "offline", GeoPoint(p.lat, p.lon), p.kind in "rfc")
        }
        if (graph != null) {
            val ways = HashSet<Long>()
            for (e in 0 until graph.edgeCount) {
                val name = graph.nameOf(e)
                val id = graph.osmIdOf(e)
                if (name.isEmpty() || !ways.add(id)) continue
                val node = graph.edgeFrom[e]
                out += SearchItem("way/$id", name, street, "offline", GeoPoint(graph.nodeLat[node], graph.nodeLon[node]))
            }
        }
        return out
    }

    /** Dart `'$double'` yazımı (tam sayıda `.0`). */
    private fun num(d: Double) = d.toString()

    /** Küçük harf; `ı→i, ə→e, ş→s, ç→c, ğ→g, ö→o, ü→u`; kırpılmış. */
    fun normalizeSearch(s: String): String = s.lowercase(Locale.ROOT).replace('ı', 'i').replace('ə', 'e').replace('ş', 's').replace('ç', 'c')
        .replace('ğ', 'g').replace('ö', 'o').replace('ü', 'u').trim()

    /** Sorgu sözcüklerinin hepsi `başlık + adres`'te; başlığı sorguyla başlayan önce, sonra [near]'a uzaklık; id tekil; ilk 6. */
    fun match(all: List<SearchItem>, query: String, near: GeoPoint): List<SearchItem> {
        val normalized = normalizeSearch(query)
        val words = normalized.split(' ')
        val matched = all.filter { p -> normalizeSearch("${p.title} ${p.address}").let { t -> words.all { t.contains(it) } } }
            .sortedWith(
                compareBy<SearchItem> { if (normalizeSearch(it.title).startsWith(normalized)) 0 else 1 }
                    .thenBy { it.point?.distanceTo(near) ?: Double.POSITIVE_INFINITY },
            )
        val ids = HashSet<String>()
        return matched.filter { ids.add(it.id) }.take(6)
    }
}

/**
 * Yer arama durum makinesi (MAP.md 8.8; `place_search.dart`). Debounce [delay] ile (test sanal saat verir); sıra numarası eski yanıtı atar;
 * her hata (Error dahil) çevrimdışına düşer, meşgul kalmaz. Google öneri/ayrıntı içeriği kalıcı önbelleğe yazılmaz (yalnız ORS, 60 sn).
 */
class PlaceSearch(
    private val scope: CoroutineScope,
    private val http: HttpPort,
    /** Ayarlar'daki anahtar + APK paketi ve imzası; yoksa APK anahtarı; hiçbiri yoksa null (istek atılmaz). */
    private val credentials: suspend () -> GoogleCredentials?,
    private val pois: () -> List<Poi>,
    private val graph: () -> RoadGraph?,
    private val lang: () -> Lang,
    private val near: () -> GeoPoint?,
    private val prefs: MapPrefs,
    private val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    /** ORS anahtarı: yalnız Ayarlar'daki Google anahtarı boşken verilir (o zaman Google yerine ORS önerileri). */
    private val orsKey: () -> String = { "" },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val state = MutableStateFlow(SearchView())
    val view: StateFlow<SearchView> get() = state

    private val lock = Any()
    private var job: Job? = null
    private var session = ""
    private var current = ""
    private var sequence = 0
    private var index: List<SearchItem>? = null
    private var indexKey: Any? = null
    private val cache = LinkedHashMap<String, Pair<Long, List<SearchItem>>>()

    fun clear() = synchronized(lock) {
        current = ""
        sequence++
        job?.cancel()
        session = ""
        state.value = SearchView()
    }

    fun query(value: String): Unit = synchronized(lock) {
        val q = value.trim()
        if (q == current && q.isNotEmpty()) return
        current = q
        job?.cancel()
        sequence++
        if (q.length < 2) {
            state.value = SearchView(items = if (q.isEmpty()) recents() else emptyList(), offline = true)
            return
        }
        if (session.isEmpty()) session = PlacesRest.newSessionToken()
        val token = sequence
        state.value = SearchView(busy = true)
        // UNDISPATCHED: bekleme bu çağrıda başlar (sanal saatli test ve sıra için).
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            delay(DEBOUNCE_MS)
            search(q, token)
        }
    }

    private fun live(token: Int, q: String? = null) = synchronized(lock) { token == sequence && (q == null || q == current) }

    private suspend fun search(query: String, token: Int) {
        val near = near() ?: Baku.center
        val lang = lang()
        var items: List<SearchItem> = emptyList()
        var error: String? = null
        try {
            val ors = orsKey()
            if (ors.isNotEmpty()) {
                items = ors(ors, query, near)
            } else {
                val creds = credentials()
                if (!live(token)) return
                items = PlacesRest.autocomplete(http, creds, query, session, near, lang)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlacesException) {
            error = placesFailureShort(e.failure, lang)
        } catch (_: Throwable) {
            error = tl(lang, "Arama tamamlanamadı · çevrimdışı sonuçlar", "Axtarış alınmadı · oflayn nəticələr")
        }
        val offline = error != null
        if (!live(token, query)) return
        if (offline) {
            items = try {
                OfflineIndex.match(recents() + index(lang), query, near)
            } catch (_: Exception) {
                recents()
            }
        }
        synchronized(lock) {
            if (token != sequence || query != current) return
            state.value = SearchView(items = items, error = error, offline = offline)
        }
    }

    private suspend fun ors(key: String, query: String, near: GeoPoint): List<SearchItem> {
        val cacheKey = "$key/$query/${"%.2f".format(Locale.ROOT, near.lat)},${"%.2f".format(Locale.ROOT, near.lon)}"
        synchronized(cache) { cache[cacheKey] }?.let { (at, items) -> if (now() - at < 60_000) return items }
        val url = "https://api.openrouteservice.org/geocode/autocomplete?" + listOf(
            "api_key" to key, "text" to query, "focus.point.lat" to near.lat.toString(), "focus.point.lon" to near.lon.toString(), "size" to "6",
        ).joinToString("&") { (k, v) -> "${PlacesRest.enc(k)}=${PlacesRest.enc(v)}" }
        val res = http.send(HttpRequest("GET", url, timeoutMs = 6_000))
        if (res.status != 200) throw IllegalStateException("Provider unavailable")
        val features = JSONObject(res.body).optJSONArray("features") ?: JSONArray()
        val items = (0 until features.length()).map { pelias(features.getJSONObject(it)) }
        synchronized(cache) {
            if (cache.size >= 32) cache.remove(cache.keys.first())
            cache[cacheKey] = now() to items
        }
        return items
    }

    private fun pelias(f: JSONObject): SearchItem {
        val p = f.getJSONObject("properties")
        val c = f.getJSONObject("geometry").getJSONArray("coordinates")
        val cats = p.optJSONArray("category")
        val restaurant = cats != null && (0 until cats.length()).any { cats.opt(it).toString() in setOf("restaurant", "food", "cafe") }
        return SearchItem(
            (p.opt("gid") ?: p.opt("id")).toString(), (p.opt("name") ?: p.opt("label")).toString(),
            p.opt("label")?.takeIf { it != JSONObject.NULL }?.toString() ?: "", "ors", GeoPoint(c.getDouble(1), c.getDouble(0)), restaurant,
        )
    }

    /** Graf ya da POI listesi değişince yeniden kurulur; etiketler dile göre. */
    private fun index(lang: Lang): List<SearchItem> {
        val g = graph()
        val p = pois()
        val key = Triple(g, p, lang)
        synchronized(lock) { if (indexKey == key) index?.let { return it } }
        val built = OfflineIndex.build(g, p, lang)
        synchronized(lock) {
            index = built
            indexKey = key
        }
        return built
    }

    private fun recents(): List<SearchItem> = try {
        val a = JSONArray(prefs.getString(RECENTS_KEY) ?: "[]")
        (0 until a.length()).map { SearchItem.fromJson(a.getJSONObject(it)) }
    } catch (_: Exception) {
        emptyList()
    }

    /** Seçim: Google önerisi için ayrıntı çağrısı (nokta, gerçek adres, restoran); son aramalara yazılır (Google'da nokta yok). */
    suspend fun select(item: SearchItem): SearchItem? {
        val (token, selectedSession, typed) = synchronized(lock) {
            if (session.isEmpty() && item.source == "google") session = PlacesRest.newSessionToken()
            job?.cancel()
            state.value = SearchView(items = state.value.items, busy = true)
            Triple(++sequence, session, current)
        }
        val lang = lang()
        return try {
            var selected = item
            if (item.source == "google") {
                val creds = credentials()
                if (!live(token)) return null
                val d = PlacesRest.details(http, creds, item.id, selectedSession, lang)
                selected = SearchItem(item.id, item.title, d.address?.ifEmpty { null } ?: item.address, "google", d.point, d.restaurant)
            }
            if (!live(token)) return null
            // Çevrimdışı/ORS seçimi yerelde saklanır; Google'da yalnız yer kimliği ve kullanıcının yazdığı başlık (ayrıntı asla).
            val recent = if (item.source == "google") {
                SearchItem(item.id, typed, tl(lang, "Son arama · bağlantı gerekir", "Son axtarış · internet lazımdır"), "google")
            } else {
                selected
            }
            val list = (listOf(recent) + recents().filter { it.id != recent.id }).take(12)
            prefs.setString(RECENTS_KEY, JSONArray(list.map { it.toJson() }).toString())
            synchronized(lock) {
                if (token != sequence) return null
                session = ""
                state.value = SearchView()
            }
            selected
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            synchronized(lock) {
                if (token == sequence) {
                    state.value = SearchView(items = state.value.items, error = tl(lang, "Mekânın konumu alınamadı · tekrar dene", "Obyektin yeri alınmadı · yenidən yoxla"))
                }
            }
            null
        }
    }

    companion object {
        const val DEBOUNCE_MS = 350L
        const val RECENTS_KEY = "search.recents.v2"
    }
}
