package com.kuryelog.logic.economics

import com.kuryelog.logic.orders.OfferWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ORDERS.md 2.13 ve 15.3: `:orders` `OfferWindows` sabitleri Rawstats'ta `EconomicsV2.PRIORS`'tan okunuyordu (DeliveryCore.kt:865-872
 * @ 6388c04). Öncül değişirse iki yer birlikte değişsin diye eşitlik burada denetlenir.
 */
class OfferWindowsTest {
    @Test fun windowsEqualThePriors() {
        val min = EconomicsV2.PRIORS.optJSONObject("wolt_K_support")?.optDouble("window_min", 60.0) ?: 60.0
        assertEquals(min, OfferWindows.DECLINE_WINDOW_MIN, 0.0)
        assertEquals((min * 60_000).toLong(), OfferWindows.declineWindowMs)
        val w = EconomicsV2.PRIORS.getJSONObject("documented_window_ms")
        // `label` öncülün açıklaması; sayılar platform anahtarlarında.
        assertEquals(w.keySet() - "label", OfferWindows.DOCUMENTED_WINDOW_MS.keys)
        for (k in w.keySet() - "label") assertEquals(k, w.getLong(k), OfferWindows.DOCUMENTED_WINDOW_MS.getValue(k))
        // Rawstats DeliveryCore.offerWindowMs: platform seçimi aynen.
        assertEquals(w.getLong("wolt"), OfferWindows.offerWindowMs("com.wolt.courierapp"))
        assertEquals(w.getLong("bolt"), OfferWindows.offerWindowMs("com.bolt.food.courier"))
        assertNull(OfferWindows.offerWindowMs("com.example"))
    }
}
