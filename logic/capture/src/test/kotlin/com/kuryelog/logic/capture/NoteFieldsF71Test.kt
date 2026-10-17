package com.kuryelog.logic.capture

import org.junit.Assert.*
import org.junit.Test

/**
 * F7.1 (Ekim planı madde 12): four Wolt drop-off screens of 16 Oct, names and streets removed, numbers changed (V1–V4).
 * Customers type the same facts into different places: "Address details", the structured Building/Entrance/Floor/
 * Apartment fields or the note. Existing NoteFields vectors (DeliveryRecognitionTest, PhoneK25Test) must not change.
 */
class NoteFieldsF71Test {
    @Test fun v1KodWithoutColon() {
        val d = DeliveryDetails(addressDetails = "blok A 7 этаж kod 35,menzil 4,rabociy lift").fillFromText()
        assertEquals("A", d.building); assertEquals("7", d.floor); assertEquals("4", d.apartment); assertEquals("35", d.doorCode)
    }

    @Test fun v2DomofonAndMertebeStillRead() {
        val d = DeliveryDetails(addressDetails = "Block C ( Test evleri) (Domofon kodu: 52 🔔)",
            note = "BLOCK C (Domofon kodu: 52🔔) Mertebe 3...").fillFromText()
        assertEquals("C", d.building); assertEquals("3", d.floor); assertEquals("52", d.doorCode)
        assertTrue(d.noteTruncated) // the full text is in Wolt's Notes; the warning stays
    }

    @Test fun v3ApartmentTypedIntoTheEntranceField() {
        val d = DeliveryDetails(building = "Test kucesi 5 c menzil41", entrance = "Menzil41", floor = "8", apartment = "41").fillFromText()
        assertNull(d.entrance) // "Menzil41" is the flat, not an entrance
        assertEquals("8", d.floor); assertEquals("41", d.apartment)
    }

    @Test fun v4EntranceFieldHoldsTheWholeDirection() {
        val d = DeliveryDetails(entrance = "A giriw , 3 cu mertebe , saga sonacan , 16 cu otag", floor = "3",
            note = "Test city 3 cu mertebe saga 16 cu otag").fillFromText()
        assertEquals("A", d.entrance); assertEquals("3", d.floor); assertEquals("Oda 16", d.apartment)
    }

    @Test fun newKeywords() {
        assertEquals("16", NoteFields.extract("ofis 16").room)
        assertEquals("16", NoteFields.extract("16-cı otaq").room)
        assertEquals("B", NoteFields.extract("B giriş, 2-ci mərtəbə").entrance)
        assertEquals("21", NoteFields.extract("Kod 21").doorCode)
        assertNull(NoteFields.extract("kod yoxdur").doorCode)
        // The old vectors.
        val f = NoteFields.extract("2-ci podyezd, 9-cu mərtəbə, mənzil 54, qapı kodu 54#1234")
        assertEquals("2", f.entrance); assertEquals("9", f.floor); assertEquals("54", f.apartment); assertEquals("54#1234", f.doorCode)
    }

    @Test fun platformFieldsWinOverText() {
        val d = DeliveryDetails(apartment = "12", addressDetails = "menzil 41, ofis 16").fillFromText()
        assertEquals("12", d.apartment)
    }
}
