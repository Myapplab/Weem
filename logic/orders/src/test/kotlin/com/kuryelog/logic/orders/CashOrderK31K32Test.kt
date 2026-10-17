package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * K31 (cash or card) and K32 (cash tip answer), docs/KURYELOG_ISTEKLER.md. Cash strings exactly as the user quoted Wolt's
 * delivery screen (8 Oct 2026; no screenshot in the repo): `Cash order`, `No change needed`, `Steps to complete the delivery`,
 * `Collect cash payment`, `I have collected the cash (AZN 20.40)`. Other Wolt frames as in WoltSliderSpinnerK33Test
 * (offer E5, pickup E8, waiting sheet E6, customer card); `Cash to collect 20 AZN` is corpus offer R06. The layout of the
 * steps screen (one node per line, or the amount in its own node) was not seen; both are tested. Names are synthetic.
 */
class CashOrderK31K32Test {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private val cashLines = listOf("Cash order", "No change needed", "Steps to complete the delivery", "Collect cash payment", "I have collected the cash (AZN 20.40)")
    private fun obs(pkg: String, at: Long, texts: List<String>, actions: List<String> = emptyList(), complete: Boolean = true) =
        CourierParser.parse(pkg, texts, actions, at, "accessibility", complete).let { CashPayment.attach(it, CashPayment.parse(pkg, texts)) }
    private fun woltWaiting(at: Long) = obs(wolt, at, listOf("Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 20% from 18:00"))
    private fun woltOffer(at: Long, fee: String = "₼3.94", restaurant: String = "Test Restoran") = obs(wolt, at, listOf("Decline", "Ready in 1 min", fee,
        "2 stops (4.2 km) • 11–19 min", restaurant, "Test küç. 1, Baku, AZ1025", "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept"),
        listOf("Decline", "Accept"))
    private fun woltPickup(at: Long, restaurant: String = "TEST RESTORAN") = obs(wolt, at, listOf("Pickup from", restaurant, "Test küç. 1, AZ1010 Baku", "Notes",
        "Test tarif", "Translate", "Collect order", "#014", "Test A.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?",
        "Cancel this delivery", "Collect orders"), listOf("Collect", "Cancel this delivery"))
    private fun cardTexts(name: String, slider: Boolean = true) = listOf("Dropoff to", name, "Test küçəsi 39k, Bakı", "INFO", "ITEMS",
        "Address details", "Order details", "From", "Punkap", "#408", "Difficulties with the delivery?", "Send a message") + (if (slider) listOf("Order delivered!") else emptyList())
    private fun woltCard(at: Long, name: String, extra: List<String> = emptyList(), slider: Boolean = true, complete: Boolean = true) =
        obs(wolt, at, cardTexts(name, slider) + extra, listOf("Send a message") + (if (slider) listOf("Order delivered!") else emptyList()), complete)

    private class Run(val core: DeliveryCore) {
        val events = ArrayList<CoreEvent>()
        fun see(o: CourierObservation) { events += core.observe(o) }
        fun tap(pkg: String, text: String, at: Long) { events += core.click(pkg, text, at) }
    }
    private fun run(): Run = Run(DeliveryCore().also { it.start() })
    private fun atCustomer(r: Run) {
        r.see(woltWaiting(0)); r.see(woltWaiting(1000))
        r.see(woltOffer(10_000)); r.see(woltOffer(11_000)); r.tap(wolt, "Accept", 14_000); r.see(woltPickup(15_000))
        r.see(woltCard(600_000, "Customer A")); r.see(woltCard(601_000, "Customer A"))
    }

    // ---- screen parsing (pure) ----

    @Test fun theQuotedCashScreenGivesCashAndTheCheckboxAmount() {
        val s = CashPayment.parse(wolt, cashLines)!!
        assertTrue(s.cash); assertEquals(20.40, s.amount!!, 1e-9); assertTrue(s.noChangeNeeded); assertTrue(s.steps)
    }
    @Test fun theAmountMayBeItsOwnNodeOrMissingTheSpace() {
        assertEquals(20.40, CashPayment.parse(wolt, listOf("Collect cash payment", "I have collected the cash", "(AZN 20.40)"))!!.amount!!, 1e-9)
        assertEquals(20.0, CashPayment.parse(wolt, listOf("I have collected the cash (AZN20)"))!!.amount!!, 1e-9)
        assertEquals(7.5, CashPayment.parse(wolt, listOf("i have collected the cash ( AZN 7.5 )"))!!.amount!!, 1e-9)
    }
    @Test fun aCashStepWithoutAReadableAmountIsStillCash() {
        // A comma decimal was never seen on Wolt: no amount is guessed from it.
        val s = CashPayment.parse(wolt, listOf("Cash order", "I have collected the cash (AZN 20,40)"))!!
        assertTrue(s.cash); assertNull(s.amount)
        assertNull(CashPayment.parse(wolt, listOf("Collect cash payment"))!!.amount)
    }
    @Test fun stepsWithoutACashStepAreNotCash() {
        val s = CashPayment.parse(wolt, listOf("Steps to complete the delivery", "Order delivered!"))!!
        assertFalse(s.cash); assertTrue(s.steps); assertNull(s.amount)
    }
    @Test fun otherFramesAndOtherAppsAreIgnored() {
        assertNull(CashPayment.parse(bolt, cashLines))
        assertNull(CashPayment.parse(wolt, cardTexts("Customer A")))
        // Corpus R06: the offer's cash note is not the delivery screen.
        assertNull(CashPayment.parse(wolt, listOf("Cash to collect 20 AZN", "₼2.31", "Accept")))
        assertNull(CashPayment.parse(wolt, listOf("Cash orders today: 3")))
    }

    // ---- trip (core) ----

    @Test fun cashScreenAtTheCustomerMarksTheOrderAndChangesNothingElse() {
        val r = run(); atCustomer(r); val t = r.core.active(wolt)!!; val phase = t.phase
        r.see(woltCard(601_500, "Customer A", cashLines))
        assertEquals(CashPayment.CASH, t.paymentMethod); assertEquals(CashPayment.SOURCE_CASH_SCREEN, t.paymentSource)
        assertEquals(20.40, t.cashAmount!!, 1e-9); assertEquals(601_500L, t.paymentAt)
        val e = r.events.single { it.type == "payment_seen" }; assertEquals(t.id, e.tripId); assertEquals("cash", e.reason)
        assertEquals(phase, t.phase); assertTrue(t.active); assertNull(t.completedAt)
        // The steps screen on its own (`Collect cash payment` is no pickup) leaves the order where it was.
        r.see(obs(wolt, 601_800, cashLines)); assertEquals(phase, t.phase); assertTrue(t.active)
        assertEquals(1, r.events.count { it.type == "payment_seen" })
    }
    @Test fun cardIsOnlyInferredFromACompleteStepsFrameAndNeverReplacesCash() {
        val r = run(); atCustomer(r); val t = r.core.active(wolt)!!
        r.see(woltCard(601_200, "Customer A", listOf("Steps to complete the delivery"), complete = false))
        assertNull(t.paymentMethod); assertEquals(CashPayment.NOT_OBSERVED, t.paymentSourceOrNotObserved)
        r.see(woltCard(601_300, "Customer A", listOf("Steps to complete the delivery")))
        assertEquals(CashPayment.CARD_INFERRED, t.paymentMethod); assertEquals(CashPayment.SOURCE_STEPS_NO_CASH, t.paymentSource); assertNull(t.cashAmount)
        // A later frame showing the cash step wins (the first one may have been cut).
        r.see(woltCard(601_400, "Customer A", cashLines)); assertEquals(CashPayment.CASH, t.paymentMethod); assertEquals(20.40, t.cashAmount!!, 1e-9)
        r.see(woltCard(601_500, "Customer A", listOf("Steps to complete the delivery"))); assertEquals(CashPayment.CASH, t.paymentMethod)
        // A frame without the amount keeps the amount already read.
        r.see(woltCard(601_600, "Customer A", listOf("Collect cash payment"))); assertEquals(20.40, t.cashAmount!!, 1e-9)
    }
    @Test fun withoutAnOpenWoltOrderNothingIsMarked() {
        val r = run(); r.see(obs(wolt, 1000, cashLines)); assertTrue(r.events.none { it.type == "payment_seen" })
        val b = run(); b.see(obs(bolt, 1000, cashLines)); assertTrue(b.events.none { it.type == "payment_seen" })
    }
    @Test fun askedAfterARealDeliveryAndAfterAK34InferenceOnlyForCash() {
        val r = run(); atCustomer(r); val t = r.core.active(wolt)!!
        r.see(woltCard(601_500, "Customer A", cashLines)); assertFalse(CashTips.shouldAsk(t))
        r.see(woltWaiting(602_000)); r.see(woltWaiting(603_100))
        assertEquals("delivered", r.events.last { it.tripId == t.id && it.type.startsWith("delivered") }.type)
        assertTrue(CashTips.shouldAsk(t))

        val k = run(); atCustomer(k); val first = k.core.active(wolt)!!
        k.see(woltCard(601_500, "Customer A", cashLines))
        k.see(woltOffer(602_000, fee = "₼2.91", restaurant = "Other Doner")); k.tap(wolt, "Accept", 605_000)
        k.see(woltPickup(606_000, restaurant = "OTHER DONER")); k.see(woltCard(1_200_000, "Customer B"))
        assertTrue(k.events.any { it.type == "delivered_inferred" && it.tripId == first.id })
        assertTrue(CashTips.shouldAsk(first))
        assertFalse(CashTips.shouldAsk(k.core.active(wolt)!!))

        val c = run(); atCustomer(c); val card = c.core.active(wolt)!!
        c.see(woltCard(601_300, "Customer A", listOf("Steps to complete the delivery"))); c.see(woltWaiting(602_000)); c.see(woltWaiting(603_100))
        assertFalse(CashTips.shouldAsk(card))
    }
    @Test fun paymentFieldsAreWrittenOnlyWhenObservedAndReadBack() {
        val r = run(); atCustomer(r); val t = r.core.active(wolt)!!
        val before = RecordJson.trip(t)
        for (k in listOf("payment_method", "payment_source", "cash_amount_azn", "payment_at", "tip_source")) assertFalse(k, before.has(k))
        assertEquals(CashPayment.NOT_OBSERVED, RecordJson.trip(before).paymentSourceOrNotObserved)
        r.see(woltCard(601_500, "Customer A", cashLines)); t.tip = 4.6; t.tipSource = CashTips.SOURCE
        val back = RecordJson.trip(RecordJson.trip(t))
        assertEquals(CashPayment.CASH, back.paymentMethod); assertEquals(CashPayment.SOURCE_CASH_SCREEN, back.paymentSource)
        assertEquals(20.40, back.cashAmount!!, 1e-9); assertEquals(601_500L, back.paymentAt); assertEquals(CashTips.SOURCE, back.tipSource)
    }

    // ---- answer log (pure) ----

    @Test fun tipIsReceivedMinusCashAndANegativeDifferenceIsKeptAndFlagged() {
        assertEquals(4.60, CashTips.tip(25.0, 20.40)!!, 1e-9)
        assertEquals(0.0, CashTips.tip(20.40, 20.40)!!, 1e-9)
        assertEquals(-0.40, CashTips.tip(20.0, 20.40)!!, 1e-9)
        assertNull(CashTips.tip(25.0, null))
        val t = TripRecord("w1", wolt, null, 1_000L).also { it.paymentMethod = CashPayment.CASH; it.paymentSource = CashPayment.SOURCE_CASH_SCREEN; it.cashAmount = 20.40; it.completedAt = 9_000L }
        val neg = CashTips.record(t, 20.0, 10_000L, CashTips.VIA_NOTIFICATION)
        assertEquals(-0.40, neg.getDouble("tip_azn"), 1e-9); assertTrue(neg.getBoolean("negative_tip")); assertEquals(CashTips.SOURCE, neg.getString("tip_source"))
        val pos = CashTips.record(t, 25.0, 10_000L, CashTips.VIA_CARD)
        assertEquals(4.60, pos.getDouble("tip_azn"), 1e-9); assertFalse(pos.getBoolean("negative_tip")); assertEquals("delivered", pos.getString("delivery"))
    }
    @Test fun withoutACashAmountTheAnswerIsKeptButNoTipIsInvented() {
        val t = TripRecord("w2", wolt, null, 1_000L).also { it.paymentMethod = CashPayment.CASH; it.paymentSource = CashPayment.SOURCE_CASH_SCREEN }
        val j = CashTips.record(t, 30.0, 5_000L, CashTips.VIA_CARD)
        assertTrue(j.isNull("tip_azn")); assertTrue(j.isNull("tip_source")); assertTrue(j.isNull("cash_amount_azn"))
        assertEquals(CashPayment.NOT_OBSERVED, j.getString("cash_amount_source")); assertFalse(j.getBoolean("negative_tip"))
        assertEquals("delivered_inferred", j.getString("delivery"))
    }
    @Test fun theRowHoldsNoCustomerData() {
        val t = TripRecord("w3", wolt, null, 1_000L).also { it.paymentMethod = CashPayment.CASH; it.cashAmount = 20.40; it.paymentSource = CashPayment.SOURCE_CASH_SCREEN }
        t.customers["c"] = CustomerRecord("c", DeliveryDetails(name = "Test Musteri", phone = "+994500000000"))
        val j = CashTips.record(t, 25.0, 2_000L, CashTips.VIA_NOTIFICATION)
        val keys = j.keys().asSequence().toSet()
        assertEquals(setOf("schemaVersion", "kind", "trip_id", "pkg", "platform", "at", "via", "received_azn", "cash_amount_azn", "cash_amount_source",
            "tip_azn", "tip_source", "negative_tip", "delivery"), keys)
        assertFalse(j.toString().contains("Test Musteri")); assertFalse(j.toString().contains("994500000000"))
        assertEquals(1, j.getInt("schemaVersion")); assertEquals("wolt", j.getString("platform"))
    }
    @Test fun typedAmounts() {
        assertEquals(25.0, CashTips.parseInput("25")!!, 1e-9)
        assertEquals(25.5, CashTips.parseInput(" 25,50 ")!!, 1e-9)
        assertEquals(25.5, CashTips.parseInput("25.5 ₼")!!, 1e-9)
        assertEquals(25.0, CashTips.parseInput("AZN 25")!!, 1e-9)
        assertEquals(0.0, CashTips.parseInput("0")!!, 1e-9)
        for (bad in listOf(null, "", "abc", "-5", "25.555", "1.2.3", "12345", "25..5")) assertNull(bad.toString(), CashTips.parseInput(bad))
    }
    @Test fun theNewestAnswerPerTripWinsAndOtherSchemasAreSkipped() {
        val t = TripRecord("w4", wolt, null, 1_000L).also { it.paymentMethod = CashPayment.CASH; it.cashAmount = 20.40; it.completedAt = 3_000L }
        val lines = sequenceOf(
            CashTips.record(t, 25.0, 10_000L, CashTips.VIA_NOTIFICATION).toString(),
            "not json",
            JSONObject().put("schemaVersion", 2).put("trip_id", "w4").put("received_azn", 99.0).put("at", 99_000L).toString(),
            CashTips.record(t, 22.0, 20_000L, CashTips.VIA_CARD).toString(),
            CashTips.record(t, 30.0, 15_000L, CashTips.VIA_CARD).toString(),
            CashTips.record(TripRecord("w5", wolt, null, 1_000L).also { it.paymentMethod = CashPayment.CASH; it.cashAmount = 10.0 }, 10.0, 5_000L, CashTips.VIA_CARD).toString())
        val m = CashTips.latest(lines)
        assertEquals(setOf("w4", "w5"), m.keys)
        assertEquals(22.0, m.getValue("w4").getDouble("received_azn"), 1e-9); assertEquals(1.60, m.getValue("w4").getDouble("tip_azn"), 1e-9)
        assertEquals(0.0, m.getValue("w5").getDouble("tip_azn"), 1e-9)
    }
    @Test fun theLogIsBackedUpAndExportedAnonymously() {
        assertTrue(CashTips.FILE in BackupFiles.logNames)
        assertEquals(AnalysisExport.Kind.KEEP_ALL, AnalysisExport.kindOf("2026-10-08/" + CashTips.FILE))
    }
}
