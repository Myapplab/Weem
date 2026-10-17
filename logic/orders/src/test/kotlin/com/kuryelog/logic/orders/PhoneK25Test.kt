package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/** K24/K25: arama düğmesinden gelen numara. Yapay numaralar; gerçek kayıt değil. */
class PhoneK25Test {
    private val wolt = "com.wolt.courierapp"
    private fun trip(): TripRecord {
        val t = TripRecord("t1", wolt, null, 1_000, TripPhase.TO_CUSTOMER)
        t.customers["name:test"] = CustomerRecord("name:test", DeliveryDetails(name = "Test Müştəri", address = "Test Street 1, Baku", phone = "+994 50 123 45 67"))
        return t
    }

    @Test fun numberStaysOnThePhoneButNotInLogsOrBackups() {
        val db = RecordJson.trip(trip(), withPhone = true)
        assertEquals("+994 50 123 45 67", RecordJson.trip(db).customers["name:test"]!!.details.phone)
        // orders.jsonl snapshot / ZIP: no number anywhere in the text.
        val portable = RecordJson.trip(trip()).toString()
        assertFalse(portable.contains("123 45 67")); assertFalse(portable.contains("1234567"))
        assertNull(RecordJson.trip(RecordJson.trip(trip())).customers["name:test"]!!.details.phone)
    }

    @Test fun dialerNumberWithDirectionMarksAndNoBreakSpacesIsRead() {
        assertEquals(listOf("+994 50 123 45 67"), NoteFields.phones("‪+994 50 123 45 67‬"))
        assertEquals(listOf("+994 50 123 45 67"), NoteFields.phones("+994 50 123 45 67"))
        assertEquals("[tel]", NoteFields.redact("‪+994 50 123 45 67‬"))
    }

    @Test fun boltCallLabelAndWoltIconLabelsAreCalls() {
        assertTrue(CourierParser.isCall("Call")); assertTrue(CourierParser.isCall("Phone"))
        assertFalse(CourierParser.isCall("Send a message")); assertFalse(CourierParser.isCall("Chat")); assertFalse(CourierParser.isCall("Translate"))
    }
}
