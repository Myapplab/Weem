package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Ekim planı madde 22 (16 Ekim, dördüncü tur): Wolt ana ekranında sipariş yokken rozet "Wolt · Müşteriye gidiliyor" diyor;
 * alış haritasında "Müşteriye gidiliyor" ile "Restorana gidiliyor" birlikte. Satır düzeni kullanıcının görüntülerinden
 * (ad, restoran, sokak yok; sayılar değiştirildi). Rawstats'ın kendi rozet satırları karede durur, ayrıştırıcı onları atar.
 */
class StuckCustomerM22Test {
    private val wolt = "com.wolt.courierapp"
    private fun obs(at: Long, vararg text: String, actions: List<String> = emptyList(), source: String = "ocr") =
        CourierParser.parse(wolt, text.toList(), actions, at, source)
    private fun woltOffer(at: Long, fee: String = "₼3.94", restaurant: String = "Test Restoran") = obs(at, "Decline", "Ready in 1 min", fee,
        "2 stops (4.2 km) • 11–19 min", restaurant, "Test küç. 1, Baku, AZ1025", "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept",
        actions = listOf("Decline", "Accept"))
    private fun woltPickup(at: Long, restaurant: String = "TEST RESTORAN") = obs(at, "Pickup from", restaurant, "Test küç. 1, AZ1010 Baku", "Notes",
        "Test tarif", "Translate", "Collect order", "#014", "Test A.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?",
        "Cancel this delivery", "Collect orders", actions = listOf("Collect", "Cancel this delivery"))
    private fun woltCard(at: Long, name: String = "Test A.") = obs(at, "Dropoff to", name, "Test küçəsi 39k, Bakı", "INFO", "ITEMS",
        "Address details", "Order details", "From", "Test Restoran", "#408", "Difficulties with the delivery?", "Send a message")

    /** Foto (a): Wolt ana ekranı, sipariş yok; üstte Rawstats rozeti (takılı durumla). */
    private val home = arrayOf("● 41.2 km", "Wolt · Müşteriye gidiliyor", "Bolt Food · Çevrimdışı", "Bugün: 5 sipariş",
        "Delivery demand", "Low", "NOW", "09:00", "15:00", "21:00", "EXPECTED DEMAND", "Now · Boost", "Hər sifariş üçün əlavə 40%",
        "19:00-20:00", "Quick links", "Go offline")
    /** Aynı ekranın kanıtsız bir okuması (yalnız araç çubuğu ve harita işaretleri; bekleme metni yok). */
    private val noEvidence = arrayOf("Menu", "Notifications", "Bridge Plaza", "Nizami")

    private class Run(val core: DeliveryCore) {
        val events = ArrayList<CoreEvent>()
        fun see(o: CourierObservation) { events += core.observe(o) }
        fun tap(pkg: String, text: String, at: Long) { events += core.click(pkg, text, at) }
    }
    private fun orderAtCustomer(): Run {
        val r = Run(DeliveryCore().also { it.start() })
        r.see(woltOffer(10_000)); r.see(woltOffer(11_000)); r.tap(wolt, "Accept", 14_000); r.see(woltPickup(15_000))
        r.see(woltCard(600_000)); r.see(woltCard(601_000))
        assertEquals(TripPhase.TO_CUSTOMER, r.core.active(wolt)!!.phase)
        return r
    }

    @Test fun homeScreenOfPhotoAIsTheWaitingMap() {
        val o = obs(700_000, *home)
        assertEquals(ScreenStage.IDLE, o.stage)
        assertFalse(o.raw.any { it.contains("Müşteriye") || it.contains("Bugün") })
        assertEquals(ScreenStage.UNKNOWN, obs(700_000, *noEvidence, source = "accessibility").stage)
    }

    @Test fun waitingMapClosesTheOrderEvenWhenReadsWithoutEvidenceComeBetween() {
        val r = orderAtCustomer()
        // Bekleme ekranı 20 sn açık; OCR okumaları arasına her 500 ms'de bir kanıtsız erişilebilirlik okuması giriyor.
        var t = 700_000L
        while (t <= 720_000L) {
            r.see(obs(t, *home))
            r.see(obs(t + 500, *noEvidence, source = "accessibility"))
            t += 1_000
        }
        assertNull("bekleme ekranında açık Wolt siparişi kalmaz", r.core.active(wolt))
    }

    @Test fun aTaskFrameBetweenWaitingFramesStillRestartsTheWait() {
        val r = orderAtCustomer()
        r.see(obs(700_000, *home)); r.see(woltCard(701_000)); r.see(obs(702_500, *home)); r.see(obs(703_400, *home))
        assertNotNull("müşteri kartı araya girince 3 sn yeniden sayılır", r.core.active(wolt))
        r.see(obs(705_600, *home))
        assertNull(r.core.active(wolt))
    }

    /** Ekli Wolt nakit teslim ekranı (sayılar değiştirildi): kutu işaretli, kaydırıcıda yalnız dönen gösterge (etiket yok). */
    private fun cashSteps(at: Long, spinner: Boolean) = obs(at, "From", "Test Pizza", "#348", "Cash order", "No change needed",
        "Steps to complete the", "delivery", "Collect cash payment", "I have collected the cash (AZN 12.70)", "Difficulties with the delivery?",
        "Send a message", "Contact support", "Delivery timeline", "14:33", "Pickup from Test Pizza", "14:42", "Dropoff to Test A.",
        source = "accessibility").let { CashPayment.attach(it, CashPayment.parse(wolt, it.raw)) }.copy(deliverSpinner = spinner)

    @Test fun cashStepsScreenIsTheCustomerStage() {
        val o = cashSteps(650_000, spinner = true)
        assertEquals(ScreenStage.DROPOFF, o.stage)
        assertFalse(o.deliverControl)
        assertTrue(o.cashOrder); assertEquals(12.70, o.cashAmount!!, 1e-9)
    }

    @Test fun nextOrdersRestaurantClosesThePreviousOrderAfterTheSpinnerOnItsCashScreen() {
        val r = orderAtCustomer()
        val a = r.core.active(wolt)!!
        r.see(cashSteps(650_000, spinner = false)); r.see(cashSteps(651_000, spinner = true))
        // Foto (b): sonraki teklif hemen geldi, bekleme ekranı yok; restoran ekranı açık.
        r.see(woltOffer(652_000, fee = "₼2.91", restaurant = "Other Doner")); r.tap(wolt, "Accept", 654_000)
        r.see(woltPickup(655_000, restaurant = "OTHER DONER")); r.see(woltPickup(656_000, restaurant = "OTHER DONER"))
        assertFalse("önceki sipariş restoran ekranında açık kalmaz", a.active)
        assertTrue(a.countsAsDelivered)
        assertEquals("next_order_at_restaurant_after_spinner", a.deliveredInferredReason)
        val open = r.core.trips.values.filter { it.active }
        assertEquals(1, open.size); assertEquals(TripPhase.TO_RESTAURANT, open.single().phase)
    }

    @Test fun withoutAnyHandoverSignThePreviousOrderWaitsForTheNextCustomerStage() {
        // Toplu siparişte Wolt önce yeni restoranı gösterebilir (R1): kaydırıcı, dönen gösterge ya da kutu yoksa kapatılmaz.
        val r = orderAtCustomer()
        val a = r.core.active(wolt)!!
        r.see(woltOffer(652_000, fee = "₼2.91", restaurant = "Other Doner")); r.tap(wolt, "Accept", 654_000)
        r.see(woltPickup(655_000, restaurant = "OTHER DONER"))
        assertTrue(a.active)
        r.see(woltCard(900_000, "Test B.")); r.see(woltCard(901_000, "Test B."))
        assertFalse(a.active); assertTrue(a.countsAsDelivered)
    }

    @Test fun waitingMapCountsAPickedUpOrderAsAnUnverifiedDelivery() {
        val r = orderAtCustomer()
        val a = r.core.active(wolt)!!
        r.see(obs(700_000, *home)); r.see(obs(704_000, *home))
        assertFalse(a.active)
        assertTrue("bugün sayısına girer (doğrulanmamış teslim)", a.countsAsDelivered)
        assertEquals("waiting_map_at_customer", a.deliveredInferredReason)
        assertTrue(r.events.any { it.type == "delivered_inferred" && it.tripId == a.id && it.reason == "waiting_map_at_customer" })
    }

    @Test fun waitingMapBeforeThePickupIsStillNoDelivery() {
        val r = Run(DeliveryCore().also { it.start() })
        r.see(woltOffer(10_000)); r.tap(wolt, "Accept", 14_000); r.see(woltPickup(15_000))
        val a = r.core.active(wolt)!!
        r.see(obs(100_000, *home)); r.see(obs(104_000, *home))
        assertFalse(a.active); assertFalse(a.countsAsDelivered)
        assertTrue(r.events.any { it.type == "end_unconfirmed" && it.reason == "waiting_map_before_pickup" })
    }
}
