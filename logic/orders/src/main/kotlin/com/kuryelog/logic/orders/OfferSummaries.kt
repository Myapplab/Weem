package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.CourierObservation
import com.kuryelog.logic.core.CourierPlatform
import com.kuryelog.logic.core.OfferSummary

// Rawstats `OfferFeedContract.exportable/snapshotV2` (OfferFeed.kt:71-79, 98-116 @ 6388c04) + `EconomicsLogs.feedOfferV2/shown/boltTotal/
// expiresAt` (EconomicsLogs.kt:29, 79, 82-87, 89-91, 140-153), satır satır. WEEM (ORDERS.md 13.2): JSON yerine `logic/core` `OfferSummary`
// (`OrderEvents.offers`); feed, imza, değişim anahtarı, `feeBasis`, `producer`, `courierState` yok (S7). `restartedAtMs` global değil,
// parametre ([startedAtMs]). Bölge (`OfferGeo`), puan ve net `:economics`'in (A4.1): burada UNKNOWN/null, asla uydurulmaz.
object OfferSummaries {
    /** An offer not seen on screen for longer than this is not exported (K21: no ghost offers). */
    const val MAX_UNSEEN_MS = 90_000L
    /** SPEC 17.1: at most 8 offers in a v2 payload. */
    const val MAX_OFFERS = 8
    /** `EconomicsLogs.FRESH_MS`: last seen at most this long ago = still on screen (`visibleNow`). */
    const val FRESH_MS = 5_000L

    /** Offers last seen before [startedAtMs] come from before a process restart; they are not exported until seen again (K21). */
    fun exportable(o: OfferState, now: Long, startedAtMs: Long = Long.MIN_VALUE): Boolean =
        now - o.lastSeenAt <= MAX_UNSEEN_MS && o.lastSeenAt >= startedAtMs

    /** [active]: çekirdek çalışıyor; [startedAtMs]: süreç başlangıcı (öncesinde son görülen teklifler yeniden görülene dek verilmez). */
    fun of(active: Boolean, offers: Collection<OfferState>, now: Long, startedAtMs: Long = Long.MIN_VALUE,
           annotator: OfferAnnotator = OfferAnnotator.NONE): List<OfferSummary> {
        val rows = ArrayList<OfferSummary>()
        if (active) for (o in offers) {
            if (rows.size >= MAX_OFFERS) break
            if (!exportable(o, now, startedAtMs)) continue
            rows += annotator.annotate(o, summary(o, now) ?: continue)
        }
        return rows
    }

    /** Bolt's priced button ("22 min, 4,49 ₼"): its minutes are the total; Wolt shows a range instead. */
    private fun boltTotal(d: CourierObservation): Int? = if (Platforms.of(d.pkg) == "bolt" && d.minLo != null && d.minLo == d.minHi) d.minHi else null

    /** What the card showed (feed v2 `shown`; `kmScope` and `demandLabel` are not carried by `OfferSummary.Shown`). */
    fun shown(d: CourierObservation): OfferSummary.Shown = OfferSummary.Shown(
        minLo = d.minLo?.toDouble(), minHi = d.minHi?.toDouble(), km = d.km, stops = d.stops, readyInMin = d.readyIn?.toDouble(),
        approachMin = d.approachMin?.toDouble(), deliveryMin = d.deliveryMin?.toDouble(), totalMin = boltTotal(d)?.toDouble(),
        multiplier = d.multipliers.singleOrNull()?.toDouble(), boostPct = d.bonusPct?.toDouble())

    /** `documented_upper_bound` (first seen + the documented window, an UNVERIFIED prior); WEEM reads no screen timer. */
    fun expiresAt(o: OfferState): Long? = DeliveryCore.offerWindowMs(o.pkg)?.let { o.firstAt + it }

    /** The summary of one offer; null for a platform the schema does not know. */
    fun summary(o: OfferState, now: Long): OfferSummary? {
        val platform = CourierPlatform.of(Platforms.of(o.pkg)) ?: return null
        val d = o.data
        return OfferSummary(offerKey = o.id, platform = platform, firstSeenAtMs = o.firstAt,
            lastSeenAtMs = maxOf(o.lastSeenAt, o.firstAt), visibleNow = now - o.lastSeenAt <= FRESH_MS, expiresAtMs = expiresAt(o),
            displayedFeeAzn = d.gross, platformDistanceKm = d.km, restaurant = d.restaurant,
            // Bolt's card shows no drop-off (K8): the reader must not assume one.
            dropoffKnown = d.customerAddress != null, shown = shown(d), dropoffAddress = d.customerAddress)
    }
}
