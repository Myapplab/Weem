package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RecordJsonTest {
    @Test fun restartRetainsOfferPriceAndBundleCustomers() {
        val o=CourierParser.parse("com.bolt.food.courier",listOf("Decline","22 min, 4,49 ₼"),listOf("22 min, 4,49 ₼"),1000)
        val offer=OfferState("offer-1",o.pkg,1000,o,61)
        val t=TripRecord("trip-1",o.pkg,offer,2000,TripPhase.TO_CUSTOMER,expectedOrders=2)
        t.customers["#A1BCD"]=CustomerRecord("#A1BCD",DeliveryDetails(name="First",floor="3",note="Original note"))
        t.customers["#E2FGH"]=CustomerRecord("#E2FGH",DeliveryDetails(name="Second",doorCode="0123"))
        t.currentCustomer="#E2FGH"
        val restored=RecordJson.trip(JSONObject(RecordJson.trip(t).toString()))
        assertEquals(4.49,restored.gross!!,0.001); assertEquals(61,restored.offer!!.phoneBattery); assertEquals("3",restored.customers["#A1BCD"]!!.details.floor)
        assertEquals("0123",restored.customers["#E2FGH"]!!.details.doorCode); assertNull(restored.customers["#E2FGH"]!!.details.floor)
    }
    @Test fun deliveryOpenAcrossMidnightIsRestoredButFinishedOldTripsAreNot() {
        val yesterdayOpen=TripRecord("y-open","wolt",null,1000,TripPhase.TO_CUSTOMER)
        val yesterdayDone=TripRecord("y-done","wolt",null,900,TripPhase.FINISHED,completedAt=1500)
        val yesterdayUnconfirmed=TripRecord("y-unc","bolt",null,800,TripPhase.END_UNCONFIRMED)
        val today=TripRecord("t-done","wolt",null,90_000_000,TripPhase.FINISHED,completedAt=90_000_500)
        val restored=RecordJson.restorable(listOf(RecordJson.trip(today),JSONObject().put("id","bozuk")),
            listOf(yesterdayDone,yesterdayOpen,yesterdayUnconfirmed).map(RecordJson::trip))
        assertEquals(listOf("y-open","t-done"),restored.map { it.id })
        assertEquals(TripPhase.TO_CUSTOMER,restored.first().phase)
        // Vardiya bitince gece yarısını geçen sipariş de "doğrulanamadı" olarak kapanır.
        val core=DeliveryCore(); restored.forEach { core.trips[it.id]=it }
        assertTrue(core.stop(2000).any { it.tripId=="y-open" && it.type=="end_unconfirmed" })
    }
    @Test fun energyCoverageAndCommissionSurviveRestart() {
        val t=TripRecord("x","wolt",null,1000); t.usedWh=12.0; t.recoveredWh=2.0; t.energyCoveredMs=8000; t.energyShared=true; t.commissionPct=10.0; t.tip=1.0
        val restored=RecordJson.trip(RecordJson.trip(t)); assertEquals(8000,restored.energyCoveredMs); assertTrue(restored.energyShared); assertEquals(10.0,restored.commissionPct,0.001)
    }
    @Test fun unknownFieldsRemainNullInBackup() { val t=TripRecord("x","wolt",null,1000); val restored=RecordJson.trip(RecordJson.trip(t)); assertNull(restored.gross); assertNull(restored.completedAt); assertNull(restored.currentCustomer) }
    @Test fun deliveryDeadlinesStaySignedAfterBackup() {
        val o=CourierParser.parse("wolt",listOf("Dropoff -7 min","Dropoff to","Name","Address","CHECK ID"),emptyList(),1000)
        val restored=RecordJson.observation(RecordJson.observation(o)); assertEquals(-7,restored.dropoffClock); assertNull(restored.travelEta); assertTrue(restored.customer.checkId)
    }
    @Test fun oldDeliveredLogKeepsPriceAndDurationWithoutGuessingNewRejects() {
        val j=JSONObject().put("type","delivered").put("t",LogTime.ts(1700000600000)).put("pkg","wolt").put("job","old1")
            .put("gross",4.0).put("total_min",10.0).put("note","Original old note").put("commission_pct",10.0)
        val t=LegacyRecords.delivered(j)!!; assertEquals(10.0,t.actualMinutes!!,0.001); assertEquals(3.6,t.net!!,0.001)
        assertEquals("legacy_log",t.offer!!.data.source); assertEquals("Original old note",t.customers["customer-1"]!!.details.note)
        assertNull(LegacyRecords.delivered(JSONObject().put("type","offer_result").put("outcome","declined_or_expired")))
    }
}
