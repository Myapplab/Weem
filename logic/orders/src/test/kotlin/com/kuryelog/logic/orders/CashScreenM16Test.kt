package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Ekim planı madde 16 (16 Ekim, dördüncü tur): Wolt nakit teslim ekranından sipariş kartına nakit tutarı, para üstü ("No change
 * needed" ya da tutar) ve kutunun işaretli görüldüğü an; o gün alınan nakit toplanır. Ekli ekranın satır düzeni (sayılar
 * değiştirildi, ad/restoran yok). Kutunun işareti yalnız erişilebilirlik ağacından okunur (OCR göremez → null = görülmedi).
 */
class CashScreenM16Test {
    private val wolt = "com.wolt.courierapp"
    private val steps = listOf("From", "Test Pizza", "#348", "Cash order", "No change needed", "Steps to complete the", "delivery",
        "Collect cash payment", "I have collected the cash (AZN 12.70)", "Difficulties with the delivery?", "Send a message",
        "Contact support", "Delivery timeline", "14:33", "Pickup from Test Pizza", "14:42", "Dropoff to Test A.")
    private fun obs(at: Long, texts: List<String>, actions: List<String> = emptyList(), checked: Boolean? = null) =
        CourierParser.parse(wolt, texts, actions, at, "accessibility").let { CashPayment.attach(it, CashPayment.parse(wolt, texts, checked)) }
    private fun woltOffer(at: Long, fee: String = "₼3.94", restaurant: String = "Test Restoran", extra: List<String> = emptyList()) = obs(at,
        listOf("Decline", "Ready in 1 min", fee, "2 stops (4.2 km) • 11–19 min", restaurant, "Test küç. 1, Baku, AZ1025", "Customer drop-off",
            "40°22'00.0\"N 49°50'00.0\"E, Baku") + extra + listOf("Expected earnings for the full delivery", "Accept"), listOf("Decline", "Accept"))
    private fun woltPickup(at: Long, restaurant: String = "TEST RESTORAN") = obs(at, listOf("Pickup from", restaurant, "Test küç. 1, AZ1010 Baku",
        "Collect order", "#014", "Test A.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?", "Cancel this delivery"),
        listOf("Collect", "Cancel this delivery"))
    private fun woltCard(at: Long, name: String = "Test A.") = obs(at, listOf("Dropoff to", name, "Test küçəsi 39k, Bakı", "INFO", "ITEMS",
        "Address details", "Order details", "From", "Test Restoran", "#408", "Difficulties with the delivery?", "Send a message"))
    private val tr = { a: String, _: String -> a }
    private fun clock(at: Long) = "%02d:%02d".format((at / 3_600_000) % 24, (at / 60_000) % 60)

    private class Run(val core: DeliveryCore) {
        val events = ArrayList<CoreEvent>()
        fun see(o: CourierObservation) { events += core.observe(o) }
        fun tap(pkg: String, text: String, at: Long) { events += core.click(pkg, text, at) }
    }
    private fun atCustomer(offerExtra: List<String> = emptyList()): Run {
        val r = Run(DeliveryCore().also { it.start() })
        r.see(woltOffer(10_000, extra = offerExtra)); r.tap(wolt, "Accept", 14_000); r.see(woltPickup(15_000))
        r.see(woltCard(600_000)); r.see(woltCard(601_000))
        return r
    }

    @Test fun screenGivesAmountNoChangeAndTheTick() {
        val s = CashPayment.parse(wolt, steps, checked = true)!!
        assertTrue(s.cash); assertEquals(12.70, s.amount!!, 1e-9); assertTrue(s.noChangeNeeded)
        assertEquals(0.0, s.changeAzn!!, 1e-9); assertEquals(true, s.checked)
        assertNull("OCR kutunun işaretini göremez", CashPayment.parse(wolt, steps)!!.checked)
        val change = CashPayment.parse(wolt, steps - "No change needed" + listOf("Total change needed", "AZN 7.30"))!!
        assertFalse(change.noChangeNeeded); assertEquals(7.30, change.changeAzn!!, 1e-9)
        val o = obs(650_000, steps, checked = false)
        assertEquals(false, o.cashChecked); assertEquals(0.0, o.cashChangeAzn!!, 1e-9)
    }

    @Test fun orderKeepsNoChangeAndTheFirstMomentTheBoxWasSeenTicked() {
        val r = atCustomer(); val t = r.core.active(wolt)!!
        r.see(obs(650_000, steps, checked = false)); assertNull(t.cashCheckedAt)
        r.see(obs(655_000, steps, checked = true)); r.see(obs(656_000, steps, checked = true)); r.see(obs(657_000, steps))
        assertEquals(655_000L, t.cashCheckedAt)
        assertEquals(true, t.cashNoChange); assertEquals(0.0, t.cashChangeAzn!!, 1e-9); assertEquals(12.70, t.cashAmount!!, 1e-9)
    }

    @Test fun offerCardsChangeFillsTheOrderWhenTheStepsScreenDoesNotSayIt() {
        val r = atCustomer(offerExtra = listOf("Cash order", "AZN 45.75", "Total change needed")); val t = r.core.active(wolt)!!
        assertEquals("cash", t.paymentMethod)
        assertEquals(false, t.cashNoChange); assertEquals(45.75, t.cashChangeAzn!!, 1e-9)
    }

    @Test fun newFieldsAreWrittenOnlyWhenSeenAndReadBack() {
        val r = atCustomer(); val t = r.core.active(wolt)!!
        val before = RecordJson.trip(t)
        for (k in listOf("cash_no_change", "cash_change_azn", "cash_checked_at")) assertFalse(k, before.has(k))
        r.see(obs(655_000, steps, checked = true))
        val back = RecordJson.trip(RecordJson.trip(t))
        assertEquals(true, back.cashNoChange); assertEquals(0.0, back.cashChangeAzn!!, 1e-9); assertEquals(655_000L, back.cashCheckedAt)
    }

    @Test fun cardLine() {
        val r = atCustomer(); val t = r.core.active(wolt)!!
        r.see(obs(650_000, steps))
        assertEquals("Nakit 12.70₼ · para üstü yok · kutu: görülmedi", CashPayment.cardLine(t, ::clock, tr))
        r.see(obs(655_000 + 41 * 60_000L, steps, checked = true))
        assertEquals("Nakit 12.70₼ · para üstü yok · kutu ✓ 00:51", CashPayment.cardLine(t, ::clock, tr))
        t.cashNoChange = false; t.cashChangeAzn = 7.3
        assertTrue(CashPayment.cardLine(t, ::clock, tr)!!.contains("para üstü 7.30₼"))
        t.paymentMethod = CashPayment.CARD_INFERRED
        assertNull(CashPayment.cardLine(t, ::clock, tr))
    }

    @Test fun dayTotalIsTheCashOfDeliveredCashOrdersOnly() {
        fun trip(id: String, cash: Double?, method: String?, done: Boolean) = TripRecord(id, wolt, null, 1_000L).also {
            it.paymentMethod = method; it.cashAmount = cash; if (done) { it.completedAt = 2_000L; it.phase = TripPhase.FINISHED }
        }
        val trips = listOf(trip("a", 12.70, "cash", true), trip("b", 20.40, "cash", true), trip("c", 9.0, "cash", false),
            trip("d", 5.0, "card_inferred", true), trip("e", null, "cash", true))
        assertEquals(33.10, CashPayment.dayTotal(trips), 1e-9)
    }

    @Test fun aTickedBoxIsAHandoverSignForTheNextOrdersRestaurant() {
        val r = atCustomer(); val a = r.core.active(wolt)!!
        r.see(obs(650_000, steps, checked = true))
        r.see(woltOffer(652_000, fee = "₼2.91", restaurant = "Other Doner")); r.tap(wolt, "Accept", 654_000)
        r.see(woltPickup(655_000, restaurant = "OTHER DONER"))
        assertFalse(a.active); assertTrue(a.countsAsDelivered)
        assertEquals("next_order_at_restaurant_after_cash_checked", a.deliveredInferredReason)
    }
}
