package com.kuryelog.logic.economics

import org.json.JSONArray
import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats OfferScore.kt @ 6388c04, satır satır; farklar ECONOMICS.md "WEEM" satırlarında.

/**
 * E4 (Faz 4): "Teklif puanı", a 1–10 score of every offer from EconomicsV2 (SPEC 2.1.0), next to the courier's own 1–10.
 *
 * Mapping (user, 13 Oct): fee ≤ `feePoorBelow` → 1–3, between `feePoorBelow` and `feeGoodFrom` → 4–7 linearly, ≥ `feeGoodFrom`
 * → 8–10 by the surplus. The outer bands step every [STEP_AZN]: 8/9/10 by the pessimistic surplus `S_lo` (the GOOD rule,
 * `S_lo ≥ 0`), 3/2/1 by how far the optimistic surplus is below the decline cost (`−(S_hi + κ)`, the POOR rule). With an
 * unknown fee basis the engine's conservative keep share is used, like its fee thresholds (`altIfParkCut`).
 *
 * The score is the engine's evaluation of the offer card while it was on screen ([ShadowMode], before the decision), written
 * once to [FILE] when the offer resolves, with the engine and mapping versions; readers keep the first row of an offer, so a
 * later engine or a personal g never changes it. No evaluation → no score, with the reason. The courier's own score is a
 * separate row in [RATINGS] (newest per offer wins); neither overwrites the other. Both files hold ids and numbers only (no
 * customer data) and are economics-* logs, so the anonymous export keeps them whole. Pure.
 */
object OfferScore {
    const val FILE = "economics-offer-scores.jsonl"
    const val RATINGS = "economics-offer-ratings.jsonl"
    const val SCHEMA = 1
    const val ENGINE = "EconomicsV2"
    /** Version of this 1–10 mapping; the engine's own version is stored as `spec_version`. */
    const val MAPPING_VERSION = 1
    const val STEP_AZN = 0.5
    const val NOT_COMPUTED = "not_computed"
    const val ENGINE_ERROR = "engine_error"
    const val VIA_DAY = "day_screen"
    const val VIA_CARD = "order_card"

    /** [value] 1–10, or null with [reason] (a SPEC NO_VERDICT code, [NOT_COMPUTED] or [ENGINE_ERROR]). [band]: poor/middle/good. */
    data class Score(val value: Int?, val band: String?, val reason: String?)

    private fun num(j: JSONObject?, k: String): Double? = j?.opt(k)?.let { it as? Number }?.toDouble()?.takeIf { it.isFinite() }

    /** The score of an engine [result] (EconomicsV2.evaluate output) for the card's [fee]. */
    fun of(result: JSONObject?, fee: Double?): Score {
        if (result == null) return Score(null, null, NOT_COMPUTED)
        val calc = result.optJSONObject("calculation")
        if (result.optString("verdict") == "NO_VERDICT" || calc == null)
            return Score(null, null, result.optJSONArray("noVerdictReasons")?.optString(0)?.takeIf { it.isNotEmpty() } ?: "NO_VERDICT")
        val a = fee ?: return Score(null, null, "FEE_UNREADABLE")
        val poor = num(calc, "feePoorBelow"); val good = num(calc, "feeGoodFrom")
        val s = calc.optJSONObject("surplus"); val alt = s?.optJSONObject("altIfParkCut")
        val lo = listOfNotNull(num(s, "lo"), num(alt, "lo")).minOrNull()
        val hi = listOfNotNull(num(s, "hi"), num(alt, "hi")).minOrNull()
        val kappa = num(calc, "kappa") ?: 0.0
        if (poor == null || good == null || lo == null || hi == null) return Score(null, null, ENGINE_ERROR)
        return when {
            a <= poor -> { val d = -(hi + kappa); Score(if (d < STEP_AZN) 3 else if (d < 2 * STEP_AZN) 2 else 1, "poor", null) }
            a >= good -> Score(if (lo < STEP_AZN) 8 else if (lo < 2 * STEP_AZN) 9 else 10, "good", null)
            else -> Score(4 + minOf(3, Math.floor(4 * (a - poor) / (good - poor)).toInt()), "middle", null)
        }
    }

    private fun r6(x: Double?): Any = x?.let { Math.round(it * 1e6) / 1e6 } ?: JSONObject.NULL

    /**
     * The row of [FILE] for an offer that resolved: [offerRow] is its `economics-offers.jsonl` row (SPEC offerRecord), [entry] the
     * engine's last evaluation of the card (null when none ran: engine off, another platform, offer restored after a restart).
     */
    fun row(offerRow: JSONObject, entry: ShadowMode.Entry?, tripId: String?, writtenAt: Long): JSONObject {
        val result = entry?.result
        // The fee the engine evaluated (its surplus belongs to it); the offer row's when there was no evaluation.
        val fee = entry?.gross?.takeIf { it.isFinite() } ?: num(offerRow, "feeAzn")
        val sc = if (entry?.error != null) Score(null, null, ENGINE_ERROR) else of(result, fee)
        val calc = result?.optJSONObject("calculation")
        val s = calc?.optJSONObject("surplus"); val alt = s?.optJSONObject("altIfParkCut")
        fun worst(k: String) = listOfNotNull(num(s, k), num(alt, k)).minOrNull()
        return JSONObject().put("schemaVersion", SCHEMA).put("kind", "offer_score")
            .put("offer_id", offerRow.optString("offerKey")).put("trip_id", tripId ?: JSONObject.NULL)
            .put("pkg", offerRow.optString("pkg")).put("platform", offerRow.optString("platform"))
            .put("first_seen_at", offerRow.optLong("firstSeenAtMs")).put("decision", offerRow.optString("decisionKind"))
            .put("decision_at", offerRow.optLong("decisionAtMs")).put("computed_at", entry?.computedAtMs ?: JSONObject.NULL).put("written_at", writtenAt)
            .put("engine", ENGINE).put("spec_version", result?.optString("specVersion")?.takeIf { it.isNotEmpty() } ?: EconomicsV2.SPEC_VERSION)
            .put("mapping_version", MAPPING_VERSION)
            .put("score", sc.value ?: JSONObject.NULL).put("band", sc.band ?: JSONObject.NULL).put("reason", sc.reason ?: JSONObject.NULL)
            .put("verdict", result?.optString("verdict")?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
            .put("economic_verdict", calc?.optString("economicVerdict")?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
            .put("fee_azn", r6(fee)).put("fee_poor_below", r6(num(calc, "feePoorBelow"))).put("fee_good_from", r6(num(calc, "feeGoodFrom")))
            .put("min_fee_break_even", r6(num(calc, "minFeeBreakEven")))
            .put("surplus_lo", r6(worst("lo"))).put("surplus_mid", r6(worst("mid"))).put("surplus_hi", r6(worst("hi"))).put("kappa", r6(num(calc, "kappa")))
            .put("g", r6(num(calc, "g") ?: num(entry?.input?.optJSONObject("g"), "g")))
            .put("g_source", (calc?.opt("gSource") as? String) ?: (entry?.input?.optJSONObject("g")?.opt("source") as? String) ?: JSONObject.NULL)
            .put("calibrating", result?.optJSONObject("display")?.optBoolean("calibrating") ?: JSONObject.NULL)
            .put("blocks", result?.optJSONArray("blocks") ?: JSONArray()).put("no_verdict_reasons", result?.optJSONArray("noVerdictReasons") ?: JSONArray())
    }

    /** The courier's own score of an offer (1–10); a correction is just a newer row. */
    fun ratingRow(offerId: String, tripId: String?, pkg: String, rating: Int, at: Long, via: String): JSONObject {
        require(rating in 1..10) { "rating 1..10" }
        return JSONObject().put("schemaVersion", SCHEMA).put("kind", "offer_rating").put("offer_id", offerId).put("trip_id", tripId ?: JSONObject.NULL)
            .put("pkg", pkg).put("platform", if (pkg in CourierParser.WOLT) "wolt" else "bolt").put("at", at).put("rating", rating).put("via", via)
    }

    private fun rows(lines: Sequence<String>, kind: String): Sequence<JSONObject> = lines.mapNotNull { l -> runCatching { JSONObject(l) }.getOrNull() }
        .filter { it.optInt("schemaVersion", -1) == SCHEMA && it.optString("kind") == kind && it.optString("offer_id").isNotEmpty() }

    /** The first score row of each offer: the score given at offer time never changes. */
    fun scores(lines: Sequence<String>): Map<String, JSONObject> {
        val out = LinkedHashMap<String, JSONObject>()
        for (j in rows(lines, "offer_score")) out.putIfAbsent(j.getString("offer_id"), j)
        return out
    }

    /** The newest valid rating of each offer (equal times: the later line). */
    fun ratings(lines: Sequence<String>): Map<String, JSONObject> {
        val out = HashMap<String, JSONObject>()
        for (j in rows(lines, "offer_rating")) {
            if (j.optInt("rating", 0) !in 1..10) continue
            val prev = out[j.getString("offer_id")]
            if (prev == null || j.optLong("at") >= prev.optLong("at")) out[j.getString("offer_id")] = j
        }
        return out
    }

    fun scoreOf(row: JSONObject?): Int? = row?.opt("score")?.let { it as? Number }?.toInt()?.takeIf { it in 1..10 }
    fun ratingOf(row: JSONObject?): Int? = row?.opt("rating")?.let { it as? Number }?.toInt()?.takeIf { it in 1..10 }

    /** One order of the day screen with both scores; [diff] = app − mine. */
    data class Compared(val offerId: String, val app: Int, val mine: Int) { val diff get() = app - mine }
    /** [meanDiff]: average app − mine over the orders that have both; [biggest]: up to [top] largest |difference|, largest first. */
    data class Summary(val compared: Int, val meanDiff: Double?, val meanAbsDiff: Double?, val biggest: List<Compared>)

    fun summary(offerIds: List<String>, scores: Map<String, JSONObject>, ratings: Map<String, JSONObject>, top: Int = 3): Summary {
        val both = offerIds.mapNotNull { id -> val a = scoreOf(scores[id]); val m = ratingOf(ratings[id]); if (a != null && m != null) Compared(id, a, m) else null }
        if (both.isEmpty()) return Summary(0, null, null, emptyList())
        return Summary(both.size, both.map { it.diff.toDouble() }.average(), both.map { Math.abs(it.diff).toDouble() }.average(),
            both.filter { it.diff != 0 }.sortedByDescending { Math.abs(it.diff) }.take(top))
    }

    /** "+2", "0", "-1": app − mine. */
    fun signed(d: Int): String = if (d > 0) "+$d" else d.toString()
    fun signed(d: Double): String {
        val r = Math.round(d * 10) / 10.0
        return (if (r > 0) "+" else "") + String.format(java.util.Locale.US, "%.1f", if (r == 0.0) 0.0 else r)
    }

    /**
     * One order on the screens: the app's score ([app]: its [FILE] row, null when none was written) and mine ([mine]), with the
     * difference when both exist; without an app score its reason, without mine a dash.
     */
    fun pairText(app: JSONObject?, mine: JSONObject?, tl: (String, String) -> String): String {
        val a = scoreOf(app); val m = ratingOf(mine)
        val appText = a?.toString() ?: ("— (" + (if (app == null) tl("puan kaydı yok", "bal qeydi yoxdur") else reasonText(app.opt("reason") as? String, tl)) + ")")
        val diff = if (a != null && m != null) " (" + tl("fark", "fərq") + " " + signed(a - m) + ")" else ""
        return tl("Uygulama", "Tətbiq") + " " + appText + " · " + tl("Sen", "Sən") + " " + (m?.toString() ?: "—") + diff
    }

    /** Why there is no score, for the screens. */
    fun reasonText(code: String?, tl: (String, String) -> String): String = when (code) {
        "FEE_UNREADABLE" -> tl("ücret okunamadı", "məbləğ oxunmadı")
        "PLATFORM_MINUTES_UNREADABLE" -> tl("dakika okunamadı", "dəqiqə oxunmadı")
        "DISTANCE_UNREADABLE" -> tl("km okunamadı", "km oxunmadı")
        "OBSERVATION_STALE_OR_NOT_VISIBLE" -> tl("kart ekranda kalmadı", "kart ekranda qalmadı")
        "TOO_LATE_FOR_SAFE_DECISION" -> tl("süre dolmak üzereydi", "vaxt bitmək üzrə idi")
        "SCREEN_NOT_READABLE" -> tl("ekran okunamadı", "ekran oxunmadı")
        NOT_COMPUTED -> tl("hesaplanmadı (motor kapalı ya da uygulama yeniden başladı)", "hesablanmadı (mühərrik bağlı və ya tətbiq yenidən başladı)")
        ENGINE_ERROR -> tl("motor hatası", "mühərrik xətası")
        null -> "—"
        else -> code
    }
}
