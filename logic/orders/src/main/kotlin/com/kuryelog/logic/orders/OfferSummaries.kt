package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.3): Rawstats `OfferFeedContract.snapshotV2` + `EconomicsLogs.feedOfferV2` (OfferFeed.kt:68-116, EconomicsLogs.kt:75-89, 140-153 @ 6388c04)
// WEEM'de `OrderEvents.offers` için `logic/core` `OfferSummary` listesini üretir (ORDERS.md 13.2); feed, imza ve değişim anahtarı yok (S7). Gövde A3.5'te.
object OfferSummaries {
    /** Bu süreden uzun görülmeyen teklif dışarı verilmez (K21: hayalet teklif yok). */
    const val MAX_UNSEEN_MS = 90_000L
    /** Listede en çok bu kadar teklif. */
    const val MAX_OFFERS = 8
    /** [active]: çekirdek çalışıyor; [startedAtMs]: süreç başlangıcı (öncesinde son görülen teklifler yeniden görülene dek verilmez). */
    fun of(active: Boolean, offers: Collection<OfferState>, now: Long, startedAtMs: Long = Long.MIN_VALUE): List<OfferSummary> = TODO("A3.5")
}
