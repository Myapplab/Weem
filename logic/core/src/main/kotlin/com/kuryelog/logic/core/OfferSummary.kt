package com.kuryelog.logic.core

import com.kuryelog.logic.core.Msg.putN
import org.json.JSONObject

/**
 * `OfferSummary` v1 (plan 4.4; bugünkü feed v2 teklifi, `X_protocol` P1–P2): `:orders` → `:map`, `:economics`, `:link` (P → R).
 * Alan adları feed v2 `v2` nesnesiyle aynı. **Yok:** müşteri adı, telefonu, notları. Müşteri adres metni ve çözülmüş nokta var (S5).
 */
data class OfferSummary(
    val offerKey: String,
    val platform: CourierPlatform,
    val firstSeenAtMs: Long,
    val lastSeenAtMs: Long,
    val visibleNow: Boolean,
    val expiresAtMs: Long? = null,
    val displayedFeeAzn: Double? = null,
    val platformDistanceKm: Double? = null,
    val restaurant: String? = null,
    val pickupZone: Zone = Zone.UNKNOWN,
    val dropoffZone: Zone = Zone.UNKNOWN,
    val dropoffKnown: Boolean = false,
    val shown: Shown = Shown(),
    /** R4 teklif puanı 1–10. */
    val score: Int? = null,
    val netAzn: Double? = null,
    val dropoffAddress: String? = null,
    val dropoffLat: Double? = null,
    val dropoffLon: Double? = null,
    /** N5 menzil uyarısı (A10.4e, MAP.md 10.6): v2 `BATTERY_ESCAPE_NOT_MET` doğduysa bırakış → ev sürüş km'si; yoksa null. */
    val rangeWarnKm: Double? = null,
) {
    /** Kartta görünen değerler (feed v2 `shown`; `kmScope`, `demandLabel` mesajda yok). */
    data class Shown(
        val minLo: Double? = null, val minHi: Double? = null, val km: Double? = null, val stops: Int? = null, val readyInMin: Double? = null,
        val approachMin: Double? = null, val deliveryMin: Double? = null, val totalMin: Double? = null, val multiplier: Double? = null,
        val boostPct: Double? = null,
    ) {
        fun toJson(): JSONObject = JSONObject().putN("minLo", minLo).putN("minHi", minHi).putN("km", km).putN("stops", stops)
            .putN("readyInMin", readyInMin).putN("approachMin", approachMin).putN("deliveryMin", deliveryMin).putN("totalMin", totalMin)
            .putN("multiplier", multiplier).putN("boostPct", boostPct)

        companion object {
            fun parse(j: JSONObject?): Shown = if (j == null) Shown() else Shown(Msg.num(j, "minLo"), Msg.num(j, "minHi"), Msg.num(j, "km"),
                Msg.int(j, "stops"), Msg.num(j, "readyInMin"), Msg.num(j, "approachMin"), Msg.num(j, "deliveryMin"), Msg.num(j, "totalMin"),
                Msg.num(j, "multiplier"), Msg.num(j, "boostPct"))
        }
    }

    /** Adres metninden kaba bölge (feed v2 `pickupZone`/`dropoffZone`). */
    enum class Zone(val key: String) {
        Z1("Z1"), Z2("Z2"), Z3("Z3"), Z4("Z4"), UNKNOWN("unknown");
        companion object { fun of(key: String?): Zone = entries.firstOrNull { it.key == key } ?: UNKNOWN }
    }

    fun toJson(): JSONObject = JSONObject().put("schemaVersion", SCHEMA).put("offerKey", offerKey).put("platform", platform.key)
        .put("firstSeenAtMs", firstSeenAtMs).put("lastSeenAtMs", lastSeenAtMs).put("visibleNow", visibleNow).putN("expiresAtMs", expiresAtMs)
        .putN("displayedFeeAzn", displayedFeeAzn).putN("platformDistanceKm", platformDistanceKm).putN("restaurant", restaurant?.take(MAX_RESTAURANT))
        .put("pickupZone", pickupZone.key).put("dropoffZone", dropoffZone.key).put("dropoffKnown", dropoffKnown).put("shown", shown.toJson())
        .putN("score", score?.takeIf { it in 1..10 }).putN("netAzn", netAzn).putN("dropoffAddress", dropoffAddress?.take(MAX_ADDRESS))
        .putN("dropoffLat", dropoffLat).putN("dropoffLon", dropoffLon).putN("rangeWarnKm", rangeWarnKm)

    companion object {
        const val SCHEMA = 1
        const val MAX_RESTAURANT = 200
        const val MAX_ADDRESS = 1000

        fun parse(raw: String?): OfferSummary? = Msg.obj(raw)?.let(::parse)
        fun parse(j: JSONObject): OfferSummary? {
            if (Msg.version(j) != SCHEMA) return null
            return read(j, j)
        }

        /** Bugünkü feed v2 teklif nesnesi (v1 alanları + `v2`): `v2` yoksa ya da platform wolt/bolt değilse null. */
        fun fromFeedV2(offer: JSONObject): OfferSummary? {
            val v2 = offer.optJSONObject("v2") ?: return null
            return read(v2, offer)
        }

        /** [main]: `offerKey`… alanları; [fees]: ücret, mesafe, restoran, adres (mesajda aynı nesne, feed'de v1 kökü). */
        private fun read(main: JSONObject, fees: JSONObject): OfferSummary? {
            val key = Msg.str(main, "offerKey")?.takeIf { it.isNotEmpty() } ?: return null
            val platform = CourierPlatform.of(Msg.str(main, "platform")) ?: return null
            val first = Msg.long(main, "firstSeenAtMs") ?: return null
            val last = Msg.long(main, "lastSeenAtMs") ?: return null
            val lat = Msg.num(main, "dropoffLat")?.takeIf { it in -90.0..90.0 }
            val lon = Msg.num(main, "dropoffLon")?.takeIf { it in -180.0..180.0 }
            return OfferSummary(
                offerKey = key, platform = platform, firstSeenAtMs = first, lastSeenAtMs = last,
                visibleNow = Msg.bool(main, "visibleNow") ?: false, expiresAtMs = Msg.long(main, "expiresAtMs"),
                displayedFeeAzn = Msg.num(fees, "displayedFeeAzn"), platformDistanceKm = Msg.num(fees, "platformDistanceKm"),
                restaurant = Msg.str(fees, "restaurant")?.take(MAX_RESTAURANT),
                pickupZone = Zone.of(Msg.str(main, "pickupZone")), dropoffZone = Zone.of(Msg.str(main, "dropoffZone")),
                dropoffKnown = Msg.bool(main, "dropoffKnown") ?: false, shown = Shown.parse(main.optJSONObject("shown")),
                score = Msg.int(main, "score")?.takeIf { it in 1..10 }, netAzn = Msg.num(main, "netAzn"),
                dropoffAddress = Msg.str(fees, "dropoffAddress")?.take(MAX_ADDRESS),
                dropoffLat = if (lat != null && lon != null) lat else null, dropoffLon = if (lat != null && lon != null) lon else null,
                rangeWarnKm = Msg.num(main, "rangeWarnKm")?.takeIf { it >= 0 },
            )
        }
    }
}
