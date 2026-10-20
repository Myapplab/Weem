package com.kuryelog.logic.app

import com.kuryelog.logic.capture.CourierObservation
import com.kuryelog.logic.capture.DeliveryDetails
import com.kuryelog.logic.capture.ScreenStage
import com.kuryelog.logic.link.LinkPayloads
import com.kuryelog.logic.orders.OfferState
import com.kuryelog.logic.orders.OfferSummaries
import com.kuryelog.logic.orders.OrderStages
import com.kuryelog.logic.orders.TripRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S5 (plan 4.4, LINK.md 4.8): sipariş akışının ürettiği teklif ve aşama mesajları 2 telefon yüküne dönünce müşteri adı, telefonu,
 * notları ve talimatları baytlarda yoktur; adres metni vardır.
 */
class TwoPhonePrivacyTest {
    private val wolt = "com.wolt.courierapp"
    private val t0 = 1_791_295_200_000L
    private val secrets = listOf("Leyla Məmmədova", "+994501234567", "501234567", "qapını döymə", "zəng edin", "4512#")

    private fun observation() = CourierObservation(
        wolt, t0, ScreenStage.OFFER, true, "ocr", emptyList(), emptyList(), gross = 4.2, restaurant = "Kafe Araz",
        customerAddress = "Nizami küç. 5, giriş 2",
        customer = DeliveryDetails(name = "Leyla Məmmədova", address = "Nizami küç. 5", note = "qapını döymə", instructions = "zəng edin",
            doorCode = "4512#", phone = "+994501234567"),
    )

    @Test
    fun customerDataStaysOnTheProvider() {
        val offer = OfferState("o-1", wolt, t0, observation())
        val summary = OfferSummaries.summary(offer, t0 + 1_000)!!
        val offers = String(LinkPayloads.offers(listOf(summary)))
        val trip = TripRecord("t-1", wolt, offer, t0 + 5_000)
        val stages = String(LinkPayloads.stages(OrderStages.of(listOf(trip), { null }, t0 + 6_000)))
        for (s in secrets) {
            assertFalse(s, offers.contains(s))
            assertFalse(s, stages.contains(s))
        }
        assertTrue(offers.contains("Nizami küç. 5, giriş 2"))
        assertTrue(offers.contains("Kafe Araz"))
        assertTrue(stages.contains("t-1"))
    }
}
