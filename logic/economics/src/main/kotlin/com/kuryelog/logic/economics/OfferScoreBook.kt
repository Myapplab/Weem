package com.kuryelog.logic.economics

import com.kuryelog.logic.orders.TripRecord
import org.json.JSONObject

/**
 * E4 okuyucusu (Rawstats `MainActivity.offerScoreOf/pickRating`, MainActivity.kt:849-867 @ 6388c04): bir siparişin teklifinin
 * uygulama puanı ve benim puanım, teklifin ilk görüldüğü günün dosyalarından; gün başına en çok 10 sn'de bir yeniden okunur, puan
 * verince önbellek temizlenir. Siparişler sekmesi ve sipariş kartı aynı nesneyi kullanır. Saf.
 */
class OfferScoreBook(private val econ: EconomicsObserver, private val day: (Long) -> String, private val now: () -> Long) {
    private val cache = HashMap<String, Pair<Long, Pair<Map<String, JSONObject>, Map<String, JSONObject>>>>()

    /** Uygulamanın puan satırı ve benim son puan satırım (teklifsiz sipariş: ikisi de null). */
    @Synchronized fun of(trip: TripRecord): Pair<JSONObject?, JSONObject?> {
        val o = trip.offer ?: return null to null
        val d = day(o.firstAt); val t = now()
        val rows = cache[d]?.takeIf { t - it.first in 0 until CACHE_MS }?.second
            ?: (econ.offerScores(d) to econ.offerRatings(d)).also { cache[d] = t to it }
        return rows.first[o.id] to rows.second[o.id]
    }

    /** Benim 1–10 puanım (yeni satır; düzeltme de yeni satır). false: kaydedilemedi. */
    @Synchronized fun rate(trip: TripRecord, rating: Int, via: String): Boolean =
        econ.rateOffer(trip, rating, via).also { if (it) cache.clear() }

    /** Sipariş kartı, "Teklif ayrıntıları"nın ilk satırı (RS MainActivity.kt:1093); teklifsiz siparişte null. */
    fun cardLine(trip: TripRecord, tl: (String, String) -> String): String? =
        trip.offer?.let { of(trip) }?.let { (a, m) -> OfferScorePage.cardLine(a, m, tl) }

    companion object { const val CACHE_MS = 10_000L }
}
