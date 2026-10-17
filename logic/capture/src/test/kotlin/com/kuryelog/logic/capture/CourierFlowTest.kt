package com.kuryelog.logic.capture

import org.junit.Assert.*
import org.junit.Test

/** Transcribed visible facts from the courier's screenshots. Not an OCR-image accuracy claim. */
class CourierFlowTest {
    private val wolt = "com.wolt.partner"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList(), complete: Boolean = true) = CourierParser.parse(pkg, text.toList(), actions, at, complete=complete)
    private fun boltOffer(at: Long=1000) = obs(bolt,at,"Decline","Cook Box by Merkez Lahmacun Nizami","Mehemmed Naxcivani 15","~8 min","~14 min","22 min, 4,49 ₼",actions=listOf("22 min, 4,49 ₼","Decline"))
    private fun woltOffer(at: Long=1000) = obs(wolt,at,"Ready in 3 min","Decline","₼2.31","2 stops (2.8 km) • 8–16 min","Izmir Doner Tantuni No1","9RQW+344 Baku, Baku, AZ1000","Customer drop-off","Mirzəağa Əliyev 249, Bakı, 1014","Accept",actions=listOf("Accept","Decline"))
    private fun pickup(pkg: String=bolt, at: Long=2000) = obs(pkg,at,"Order is being prepared","Arrive in 11 min","Xengeland","Mehdi Hüseyn 99","Orders to pick up:","2","#Q3FYM","Items (0)","#8P6D2")
    private fun idle(pkg: String=bolt,at: Long=3000) = obs(pkg,at,"Waiting for orders","Online")
    private fun drop(id: String="#8P6D2", at: Long=3000) = obs(bolt,at,"Drop off in -1 min","Ziba M. $id","Items (1)","Address","Mehdi Huseyn 40, Baku","Instructions","Meet at my door","Floor","3","Arrived","Slide to confirm",actions=listOf("Arrived"))
    @Test fun pricedBoltButtonIsAccept() { val o=boltOffer(); assertEquals(ScreenStage.OFFER,o.stage); assertEquals(4.49,o.gross!!,0.001); assertEquals(22,o.minLo); assertEquals("Mehemmed Naxcivani 15",o.restaurantAddress) }
    @Test fun woltOfferHasDistinctStopAndOrderCounts() { val o=woltOffer(); assertEquals(ScreenStage.OFFER,o.stage); assertEquals(2,o.stops); assertNull(o.quantity); assertEquals(8,o.minLo); assertEquals(16,o.minHi); assertEquals(2.8,o.km!!,0.001); assertEquals("Izmir Doner Tantuni No1",o.restaurant) }
    @Test fun plusCodeIsRetainedAsRestaurantAddress() { assertEquals("9RQW+344 Baku, Baku, AZ1000",woltOffer().restaurantAddress) }
    @Test fun customerAddressInOfferIsCaptured() { assertEquals("Mirzəağa Əliyev 249, Bakı, 1014",woltOffer().customerAddress) }
    @Test fun readyTimeIsSeparateFromDeliveryEstimate() { assertEquals(3,woltOffer().readyIn); assertEquals(16,woltOffer().minHi) }
    @Test fun negativeClockIsNotTravelEta() { val o=drop(); assertEquals(-1,o.dropoffClock); assertNull(o.travelEta) }
    @Test fun woltCollectTranslationIsNotDelivery() { assertTrue(CourierParser.isCollect("Təhvilalmanı tamamla")); assertFalse(CourierParser.isDeliver("Təhvilalmanı tamamla")) }
    @Test fun customerDetailsCaptureSparseFieldsOnly() { val o=drop(); assertEquals("3",o.customer.floor); assertEquals("Meet at my door",o.customer.instructions); assertNull(o.customer.apartment); assertNull(o.customer.doorCode); assertFalse(o.customer.display().contains("Kapı kodu")) }
    @Test fun notesContainingPhoneStayOriginal() { val o=obs(bolt,5000,"Drop off in -7 min","Ramil S. #CCUV5","Address","Təbriz 52, Баку","Instructions","Meet at my door","Additional note","Zəng edin nömrəyə — phone in original note","Translate","Floor","3","Call","Chat"); assertEquals("Zəng edin nömrəyə — phone in original note",o.customer.note); assertEquals("3",o.customer.floor) }
    @Test fun longDirectionsStayComplete() { val note="salam. Yasamal mida evlərinə çatmamış olan kruqdan 3. yola girmək lazımdır. məhəlin girişində yol yoxdur yazılıb o küçədir. axıra kimi gəlib əlaqə saxlayarsız zəhmət olmasa."; val o=obs(bolt,5000,"Drop off in 1 min","Solmaz O. #A3ULX","Address","Rahib Tağıyev 26, Bakı","Instructions",note,"Translate","Arrived","Slide to confirm"); assertEquals(note,o.customer.instructions) }
    @Test fun checkIdRequirementCaptured() { val o=obs(wolt,5000,"Dropoff -7 min","Dropoff to","Murad Muradly","Litvin Babayev 81, Bakı","CHECK ID"); assertTrue(o.customer.checkId); assertEquals("Murad Muradly",o.customer.name); assertEquals(ScreenStage.DROPOFF,o.stage) }
    @Test fun sliderIsContextualArrivalNotCollection() { assertTrue(CourierParser.isArrive("Arrived Slide to confirm")); assertFalse(CourierParser.isCollect("Arrived")); val o=drop(); assertFalse(o.arrived) }
    @Test fun futureBonusIsRecordedWithStartTimeNotAnOffer() { val o=obs(wolt,1000,"Expect a task soon","Delivery demand Medium","Hər sifariş üçün əlavə 30% from 19:00"); assertEquals(ScreenStage.IDLE,o.stage); assertEquals(30,o.bonusPct); assertEquals("19:00",o.bonusFrom); assertNull(o.gross) }
    @Test fun woltBanIsOfflineNotAnOrder() { val o=obs(wolt,1000,"Unable to go online","You've been set offline until 21:46 for declining too many deliveries.","Go online","Delivery demand Medium","Hər sifariş üçün əlavə 40% from 12:00"); assertEquals(ScreenStage.OFFLINE,o.stage); assertEquals("21:46",o.offlineUntil) }
    @Test fun multiplierIsNotMoney() { val o=obs(bolt,1000,"Online","1.8x","x1.4"); assertEquals(listOf(1.8,1.4),o.multipliers); assertNull(o.gross); assertEquals(ScreenStage.IDLE,o.stage) }
}
