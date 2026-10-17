package com.kuryelog.logic.capture

import org.junit.Assert.*
import org.junit.Test

class RecordingPolicyTest {
    @Test fun restaurantOrHistoryFeeAndKmAreNotAnOffer() {
        val details = listOf("Restaurant", "4.50 AZN", "2.3 km")
        assertFalse(OfferRules.isOffer(emptyList(), details, true))
        assertFalse(OfferRules.isOffer(listOf("Order accepted"), details, true))
        assertFalse(OfferRules.isOffer(listOf("Accept"), details + "Order history", true))
        assertFalse(OfferRules.isOffer(listOf("Accept"), details, false))
        assertTrue(OfferRules.isOffer(listOf("Accept"), details, true))
    }

    @Test fun acceptedStatusAndSettingsToggleCannotActAsAcceptButton() {
        for (s in listOf("Accepted", "Kabul edildi", "Qəbul edildi", "Accept new orders", "Acceptance rate", "Not accepted"))
            assertFalse(s, OfferRules.isAccept(s))
        for (s in listOf("Accept", "Accept (25s)", "Accept [20]", "Qəbul et", "Kabul et", "Принять заказ"))
            assertTrue(s, OfferRules.isAccept(s))
    }

    @Test fun windowReorderCountdownAndEtaDoNotProduceExtraScreens() {
        val d = ScreenDeduper()
        assertTrue(d.shouldSave("wolt", listOf("4.50 AZN", "Cafe", "2.3 km", "20 sec", "12 min")))
        assertFalse(d.shouldSave("wolt", listOf("Cafe", "4.50 AZN", "2.4 km", "19 sec", "11 min")))
        assertTrue(d.shouldSave("wolt", listOf("Cafe", "5.50 AZN", "2.4 km", "18 sec", "11 min")))
        assertTrue(d.shouldSave("bolt", listOf("Cafe", "5.50 AZN", "2.4 km", "18 sec", "11 min")))
    }

    @Test fun emptySnapshotDoesNotEraseTheLastKnownScreen() {
        val d = ScreenDeduper()
        assertTrue(d.shouldSave("wolt", listOf("Cafe")))
        assertFalse(d.shouldSave("wolt", emptyList()))
        assertFalse(d.shouldSave("wolt", listOf("Cafe")))
    }

}
