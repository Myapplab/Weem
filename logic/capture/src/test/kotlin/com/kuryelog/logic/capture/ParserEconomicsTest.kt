package com.kuryelog.logic.capture

import org.junit.Assert.*
import org.junit.Test

/**
 * Offer-economics v2 parser fields (WP5). Screen texts are the exact strings of the research corpora:
 * golden_corpus.json parser rows P01–P18 / R01–R06 and the field corpus (sonuc-2026-10-06/saha) E1, E4, E5, E8 and
 * the OCR frame with KuryePlus's own badge. Rows marked "synthetic" are not from a real screen.
 */
class ParserEconomicsTest {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, vararg text: String, actions: List<String> = emptyList(), source: String = "accessibility") =
        CourierParser.parse(pkg, text.toList(), actions, 0L, source)
    private val tr = { t: String, _: String -> t }
    private val az = { _: String, a: String -> a }

    // Field corpus
    private val e1 = arrayOf("Baku Central", "Delivery demand", "Low", "NOW", "09:00", "15:00", "21:00", "03:00", "EXPECTED DEMAND", "EARN EXTRA",
        "Starts in 4 hours", "Boost", "Hər sifariş üçün əlavə 20%", "18:00-20:00", "Quick links", "Previous task", "Scheduled offline", "Support", "Go offline")
    private val e5 = arrayOf("Decline", "Ready in 1 min", "₼3.94", "2 stops (4.2 km) • 11–19 min", "Test Restoran", "Test küç. 1, Baku, AZ1025",
        "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept")
    private val badge = arrayOf("● 30.1 km", "Wolt · 2.21₼ · Teklif", "Bugün: 7 sipariş")
    private val e8 = arrayOf("Pickup from", "TEST RESTORAN", "Test küç. 1, AZ1010 Baku", "Notes", "Test tarif", "Translate", "Collect order", "#014",
        "Test A.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?", "Cancel this delivery", "Collect orders")

    // ------------------------------------------------------------------ P02 / G02: Bolt legs
    @Test fun boltCardSplitsApproachAndDeliveryMinutes() {
        val o = obs(bolt, "Decline", "Cook Box by Merkez Lahmacun Nizami", "Mehemmed Naxcivani 15", "~8 min", "~14 min", "22 min, 4,49 ₼",
            actions = listOf("22 min, 4,49 ₼", "Decline"))
        assertEquals(ScreenStage.OFFER, o.stage); assertEquals(4.49, o.gross!!, 1e-9)
        assertEquals(8, o.approachMin); assertEquals(14, o.deliveryMin)
        assertEquals(22, o.minLo); assertEquals(22, o.minHi)
        assertEquals("Cook Box by Merkez Lahmacun Nizami", o.restaurant); assertTrue(o.multipliers.isEmpty())
    }

    @Test fun boltLegsNeedBothValues() {
        // synthetic: one leg missing, and two equal legs
        val one = obs(bolt, "Decline", "Test Restoran", "Test 1", "~8 min", "15 min, 3,10 ₼", actions = listOf("15 min, 3,10 ₼", "Decline"))
        assertNull(one.approachMin); assertNull(one.deliveryMin)
        val equal = obs(bolt, "Decline", "Test Restoran", "Test 1", "~7 min", "~7 min", "14 min, 3,10 ₼", actions = listOf("14 min, 3,10 ₼", "Decline"))
        assertEquals(7, equal.approachMin); assertEquals(7, equal.deliveryMin)
    }

    @Test fun woltOfferKeepsItsStopsAndHasNoBoltLegs() {
        val o = obs(wolt, "Ready in 3 min", "Decline", "₼2.31", "2 stops (2.8 km) • 8–16 min", "Izmir Doner Tantuni No1", "9RQW+344 Baku, Baku, AZ1000",
            "Customer drop-off", "Test 2, Bakı", "Accept", actions = listOf("Accept", "Decline"))
        assertEquals(ScreenStage.OFFER, o.stage); assertEquals(2.31, o.gross!!, 1e-9); assertEquals(2.8, o.km!!, 1e-9)
        assertEquals(2, o.stops); assertEquals(8, o.minLo); assertEquals(16, o.minHi); assertEquals(3, o.readyIn)
        assertNull(o.approachMin); assertNull(o.deliveryMin)
    }

    // ------------------------------------------------------------------ P03 / G18b and performance pages
    @Test fun woltYourStatsEarningsAreReadButStayAPage() {
        val o = obs(wolt, "Your stats", "TODAY", "WEEK", "MONTH", "CUSTOM", "Deliveries completed", "4", "Earnings (estimate)", "AZN 16.79")
        assertEquals(ScreenStage.UNKNOWN, o.stage); assertNull(o.gross)
        assertEquals(16.79, o.statsEarningsAzn!!, 1e-9); assertEquals(4, o.statsTasksDone)
        assertNull("four period tabs: the selected one is not known", o.statsPeriodLabel)
        assertNull(o.bonusPct); assertTrue(o.multipliers.isEmpty())
    }

    @Test fun boltAcceptanceRateIsAStatNotABonus() {
        val o = obs(bolt, "Acceptance rate", "85%")
        assertEquals(ScreenStage.UNKNOWN, o.stage); assertNull(o.bonusPct); assertNull(o.boostPct)
        assertEquals(0.85, o.statsAcceptanceRate!!, 1e-12)
    }

    @Test fun boltPerformancePageFields() {
        // synthetic labels (Bolt performance page not yet captured from the phone)
        val o = obs(bolt, "Performance", "This week", "Acceptance rate 92%", "Completion rate", "98.5%", "Rating", "4.93", "Completed deliveries", "120")
        assertEquals(ScreenStage.UNKNOWN, o.stage)
        assertEquals(0.92, o.statsAcceptanceRate!!, 1e-12); assertEquals(0.985, o.statsCompletionRate!!, 1e-12)
        assertEquals(4.93, o.statsRating!!, 1e-12); assertEquals(120, o.statsTasksDone); assertEquals("This week", o.statsPeriodLabel)
        assertNull(o.bonusPct); assertNull(o.statsEarningsAzn)
    }

    @Test fun statsAreOnlyReadOnPerformancePages() {
        val waiting = obs(wolt, "Baku Central", "Delivery demand", "Low", "NOW", "09:00", "15:00", "21:00", "03:00", "EXPECTED DEMAND", "EARN EXTRA",
            "Starts in 4 hours", "Boost", "Hər sifariş üçün əlavə 20%", "18:00-20:00", "Quick links", "Previous task", "Scheduled offline", "Support", "Go offline",
            "Menu", "Notifications", "Stats")
        assertEquals(ScreenStage.IDLE, waiting.stage)
        assertNull(waiting.statsEarningsAzn); assertNull(waiting.statsTasksDone); assertNull(waiting.statsAcceptanceRate)
        val menu = obs(wolt, "Test Courier", "Online", "Delivery settings", "Insights", "Finances", "Stats", "Earn extra", "Benefits and insurance",
            "Referrals", "Settings", "Support", "Help center", "Version 2.201.5 (22010500)")
        assertEquals(ScreenStage.UNKNOWN, menu.stage); assertNull(menu.statsTasksDone); assertNull(menu.statsRating)
    }

    @Test fun scheduledOfflineQuickLinkIsNoPenalty() {
        val o = obs(wolt, "Baku Central", "Delivery demand", "Medium", "EXPECTED DEMAND", "Quick links", "Previous task", "Scheduled offline", "Support", "Go offline")
        assertEquals(ScreenStage.IDLE, o.stage); assertNull(o.offlineUntil); assertNotEquals("offline", o.availability)
        assertEquals(ScreenStage.IDLE, obs(wolt, *e1).stage)
    }

    // ------------------------------------------------------------------ P05 / R01 / R02 / VM3-G20: multipliers
    @Test fun earningsScheduleForecastIsNotALiveMultiplier() {
        val o = obs(bolt, "Earnings Schedule", "Microdistrict", "1.1–1.65x")
        assertEquals(ScreenStage.UNKNOWN, o.stage); assertTrue(o.multipliers.isEmpty())
        // The replay writes platform context for IDLE/OFFLINE or any multiplier: nothing for this page.
        assertFalse(o.stage == ScreenStage.IDLE || o.stage == ScreenStage.OFFLINE || o.multipliers.isNotEmpty())
    }

    @Test fun namesAndDishesAreNotMultipliers() {
        assertTrue(obs(wolt, "cook box 2 by merkez").multipliers.isEmpty())
        assertTrue(obs(wolt, "2x burger").multipliers.isEmpty())
    }

    @Test fun boltHomeMultipliersStay() {
        assertEquals(listOf(1.6, 1.7), obs(bolt, "1.6x", "1.7x", "Looking for orders", "Go offline").multipliers)
        val off = obs(bolt, "1.5x", "1.45x", "BADAMDAR", "Go online")
        assertEquals(ScreenStage.OFFLINE, off.stage); assertEquals(listOf(1.5, 1.45), off.multipliers)
    }

    // ------------------------------------------------------------------ R03 / FP10 / FP04: boosts
    @Test fun futureBoostIsNotTheActiveBonus() {
        val o = obs(wolt, *e1)
        assertEquals(ScreenStage.IDLE, o.stage); assertEquals("Low", o.demand)
        assertNull(o.bonusPct); assertNull(o.bonusFrom)
        assertEquals(20, o.boostPct); assertEquals("18:00-20:00", o.boostWindow); assertEquals(240, o.boostStartsInMin)
    }

    @Test fun activeBoostStaysTheBonus() {
        val o = obs(wolt, "Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 20% from 18:00")
        assertEquals(ScreenStage.IDLE, o.stage); assertEquals(20, o.bonusPct); assertEquals("18:00", o.bonusFrom); assertEquals("Medium", o.demand)
        assertEquals(20, o.boostPct); assertNull(o.boostWindow); assertNull(o.boostStartsInMin)
        val hot = obs(wolt, "Orders coming in hot!", "8 live hotspots", "Hər sifariş üçün əlavə 50% from 08:00")
        assertEquals(50, hot.bonusPct); assertEquals("08:00", hot.bonusFrom)
    }

    @Test fun boostStartTimes() {
        // synthetic wording variants
        assertEquals(30, obs(wolt, "Starts in 30 min", "Boost", "Extra 25%", "18:00–20:00").boostStartsInMin)
        assertEquals(90, obs(wolt, "Starts in 1 hour 30 min", "Boost", "+25%").boostStartsInMin)
        assertEquals("08:00-10:00", obs(wolt, "Boost", "+25%", "8:00 - 10:00").boostWindow)
        assertNull(obs(wolt, "Starts in 1 hour 30 min", "Boost", "+25%").bonusPct)
    }

    // ------------------------------------------------------------------ R04 / R05 / R06: amounts and decline
    @Test fun thousandsSeparatorIsNeverMisread() {
        val g = obs(wolt, "4.499 AZN").gross
        assertTrue("gross=$g", g == null || g == 4499.0)
        assertNull(CourierParser.amount("₼4.499"))
        assertEquals(2.31, CourierParser.amount("₼2.31")!!, 1e-9); assertEquals(4.49, CourierParser.amount("22 min, 4,49 ₼")!!, 1e-9)
    }

    @Test fun azerbaijaniDeclineLabelIsADecline() {
        assertTrue(CourierParser.isDecline("İmtina et")); assertTrue(CourierParser.isDecline("Decline"))
        assertFalse(CourierParser.isDecline("Accept"))
    }

    @Test fun cashToCollectIsNotTheFee() {
        val o = obs(wolt, "Cash to collect 20 AZN", "₼2.31", "Accept", "Decline", actions = listOf("Accept", "Decline"))
        assertEquals(ScreenStage.OFFER, o.stage); assertEquals(2.31, o.gross!!, 1e-9)
    }

    // ------------------------------------------------------------------ FP08 / F06: KuryePlus's own panel lines
    @Test fun ownBadgeLinesAreRemovedForEverySource() {
        for (source in listOf("ocr", "accessibility", "notification")) {
            val o = obs(wolt, *badge, *e5, source = source)
            assertEquals(source, ScreenStage.OFFER, o.stage)
            assertEquals(source, 3.94, o.gross!!, 1e-9); assertEquals(source, 4.2, o.km!!, 1e-9)
            assertEquals(2, o.stops); assertEquals(11, o.minLo); assertEquals(19, o.minHi); assertEquals(1, o.readyIn)
            assertTrue(source, o.raw.none { it in badge })
        }
        // One OCR block holding the badge and the card keeps the card's lines.
        val block = obs(wolt, badge.joinToString("\n") + "\nDecline\nReady in 1 min", *e5.drop(2).toTypedArray(), source = "ocr")
        assertEquals(3.94, block.gross!!, 1e-9); assertEquals(4.2, block.km!!, 1e-9); assertTrue("Decline" in block.raw)
    }

    @Test fun ocrVariantsOfPanelLinesAreRecognised() {
        for (l in listOf("Wolt • 2.21 AZN • Teklif", "Wolt - 2.21₼ - Teklif", "Wolt 2.21₼ Teklif", "Wolt·2.21₼·Teklif", "• 30,1 km", "·30.1km",
            "Bugun: 7 siparis", "Bu gün:7 sifariş", "BUGÜN: 7 SİPARİŞ", "Bolt Food • Oflayn", "Wolt · Test Restoran · Restorana gidiliyor",
            "Bolt Food • Sifariş • Nəticə təsdiqlənmədi", "Wolt · 3.94₼ · net 3.15₼ · Test Restoran", "Wolt · 2.21₼ · Teklif"))
            assertTrue(l, OwnLines.isOwn(l))
        assertEquals(listOf("Decline", "Ready in 1 min"), OwnLines.withoutOwn(listOf(badge.joinToString("\n") + "\nDecline", "Wolt · 2.21₼ · Teklif", "Ready in 1 min")))
    }

    @Test fun courierAppLinesAreNeverPanelLines() {
        val screens = listOf(e1, e5, e8, arrayOf(
            "Ready in 3 min", "Decline", "₼2.31", "2 stops (2.8 km) • 8–16 min", "Izmir Doner Tantuni No1", "9RQW+344 Baku, Baku, AZ1000", "Customer drop-off", "Test 2, Bakı", "Accept",
            "Cook Box by Merkez Lahmacun Nizami", "Mehemmed Naxcivani 15", "~8 min", "~14 min", "22 min, 4,49 ₼",
            "Your stats", "TODAY", "Deliveries completed", "4", "Earnings (estimate)", "AZN 16.79", "Earnings Schedule", "Microdistrict", "1.1–1.65x",
            "Unable to go online", "You've been set offline until 21:46 for declining too many deliveries.", "Go online", "Delivery demand Medium",
            "Hər sifariş üçün əlavə 40% from 12:00", "Expect a task soon", "1.6x", "Looking for orders", "Go offline", "Drop off in -1 min", "Test C. #AAAA1",
            "Items (1)", "Slide to confirm", "Keyfiyyəti qoruyaq!", "Termoçantadan istifadə qaydaları.", "Anladım!", "Orders coming in hot!", "8 live hotspots",
            "All Deliveries", "Kofteci Test", "10/04 18:22", "#UWG2Q", "3,21 ₼", "Order is being prepared", "Arrive in 11 min", "Orders to pick up:", "Pickup 3 min",
            "Sabail district, Uzeyir Hajibeyov str., 27, AZ1000 Baku", "cook box 2 by merkez", "2x burger", "Acceptance rate", "85%", "4.499 AZN", "İmtina et",
            "Cash to collect 20 AZN", "1 deliveries declined", "Quick reminder, declining too many can mean being set offline!", "Decline offer?",
            "You'll lose potential earnings and it might take some time to find another offer in your delivery area.", "Return to offer details",
            "Block B ( TEST evleri) (Domofon kodu: 68🔔)", "Online", "Version 2.201.5 (22010500)", "Dropoff to", "Order delivered!"))
        assertEquals(emptyList<String>(), screens.flatMap { it.toList() }.filter(OwnLines::isOwn))
    }
}
