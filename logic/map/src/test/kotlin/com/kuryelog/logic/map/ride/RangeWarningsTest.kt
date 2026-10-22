package com.kuryelog.logic.map.ride

import com.kuryelog.logic.core.CourierPlatform
import com.kuryelog.logic.core.OfferSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** N5 haritası (A10.4e): uyarılı ve görünen teklif "eve dön?" kartını bir kez açar. */
class RangeWarningsTest {
    private fun offer(key: String, km: Double?, visible: Boolean = true) =
        OfferSummary(key, CourierPlatform.WOLT, 1L, 2L, visible, rangeWarnKm = km)

    @Test fun opensOncePerWarnedVisibleOffer() {
        val w = RangeWarnings()
        assertFalse(w.fresh(listOf(offer("a", null))))
        assertFalse(w.fresh(listOf(offer("b", 6.4, visible = false))))
        assertTrue(w.fresh(listOf(offer("a", null), offer("b", 6.4))))
        assertFalse("same offer: not again", w.fresh(listOf(offer("b", 6.4))))
        assertTrue(w.fresh(listOf(offer("b", 6.4), offer("c", 3.0))))
        assertFalse(w.fresh(emptyList()))
    }
}
