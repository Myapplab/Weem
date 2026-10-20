package com.kuryelog.app.map

import android.content.Context
import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.Poi
import com.kuryelog.logic.map.graph.PoiCorrections
import com.kuryelog.logic.map.graph.PoiSet

/**
 * Yer noktaları (MAP.md 5.3): harita verisinin [base] (düzeltilmemiş; Y11 ışık/AVM kararı bunu kullanır) ve elle düzeltmeli [all] (harita, arama, uzun basış,
 * hedefin restoran sayılması). Düzeltme ya da harita değişince önbellek yenilenir.
 */
object MapPois {
    @Volatile private var corrections: PoiCorrections? = null
    @Volatile private var cacheBase: PoiSet? = null
    @Volatile private var cacheRev = -1
    @Volatile private var cacheSet: PoiSet? = null
    @Volatile private var rev = 0

    private fun corrections(ctx: Context): PoiCorrections =
        corrections ?: synchronized(this) { corrections ?: PoiCorrections(MapPrefsStore(ctx)).also { corrections = it } }

    /** Harita verisinin düzeltilmemiş yerleri (yoksa null). */
    fun base(ctx: Context): PoiSet? = MapData.controller(ctx).state.value.pois

    /** Düzeltilmiş küme (harita yerleri + düzeltmeler); harita verisi yokken de düzeltmeler. */
    fun corrected(ctx: Context): PoiSet {
        val b = base(ctx)
        val cached = cacheSet
        if (cached != null && cacheBase === b && cacheRev == rev) return cached
        val set = PoiSet(corrections(ctx).apply(b?.items.orEmpty()))
        synchronized(this) { cacheBase = b; cacheRev = rev; cacheSet = set }
        return set
    }

    fun all(ctx: Context): List<Poi> = corrected(ctx).items

    /** İşletme ekle / ad düzelt (silme yok); [original] düzenlenen görünür nokta ya da yeni işletmede null. */
    fun save(ctx: Context, poi: Poi, original: Poi?) {
        corrections(ctx).save(poi, original)
        rev++
    }

    /** [p] noktasının [maxM] içinde restoran/fast food/kafe var mı (hedefin restoran sayılması, 8 m). */
    fun foodNear(ctx: Context, p: GeoPoint, maxM: Double = 8.0): Boolean {
        val set = corrected(ctx)
        return listOf('r', 'f', 'c').any { set.nearest(it, p.lat, p.lon, maxM) != null }
    }

    /** Uzun basış menüsü: 30 m içinde `r, f, c, t, m` sırayla, her bulunanda yarıçap o uzaklığa iner → en yakın işletme (MAP.md 5.3). */
    fun nearestBusiness(ctx: Context, p: GeoPoint, maxM: Double = 30.0): Poi? {
        val set = corrected(ctx)
        var best: Poi? = null
        var radius = maxM
        for (k in charArrayOf('r', 'f', 'c', 't', 'm')) {
            val hit = set.nearest(k, p.lat, p.lon, radius) ?: continue
            best = hit
            radius = Geo.haversine(p.lat, p.lon, hit.lat, hit.lon)
        }
        return best
    }
}
