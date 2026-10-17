import os
ROOT=os.environ.get('WEEM_ROOT','/home/user/Weem')
import re,sys
D=ROOT+'/logic/orders/src/test/kotlin/com/kuryelog/logic/orders/'
def edit(f,pairs,regex=()):
    p=D+f;s=open(p,encoding='utf-8').read()
    for a,b in pairs:
        n=s.count(a)
        if n==0: print('YOK',f,repr(a[:70])); continue
        s=s.replace(a,b)
    for a,b in regex:
        s2=re.sub(a,b,s,flags=re.S)
        if s2==s: print('REGEX YOK',f,a[:60])
        s=s2
    open(p,'w',encoding='utf-8').write(s)
# --- K34 kayıt testi: ekonomi kaydedicisiz kopya (ORDERS.md 15.1)
edit('CustomerStageK34RecordTest.kt',[
 (", val rows: List<EconomicsRecorder.Row>)",")"),
 ("val rec = EconomicsRecorder(); val rows = ArrayList<EconomicsRecorder.Row>(); val events","val events"),
 ("val before = c.pending.values.associateBy { it.id }; val e = c.observe(o); events += e\n            rows += rec.frame(o, e, before, c, emptyList(), OfferGeo.UNKNOWN) { \"unknown\" }","events += c.observe(o)"),
 ("val before = c.pending.values.associateBy { it.id }; val e = c.click(pkg, text, at); events += e\n            rows += rec.events(at, e, before, c, emptyList(), { \"unknown\" })","events += c.click(pkg, text, at)"),
 ('events.single { it.type == "delivered_inferred" }, rows)','events.single { it.type == "delivered_inferred" })'),
 (" * economics-legs row that is never a delivery (tripLegs v1 has no such outcome: \"unknown\", not calibrated on).\n"," * economics-legs row (that part is A4.1's `legsRowIsNeverADelivery`; here the order record only: ORDERS.md 15.1).\n"),
 (" * `KL_ROWS_OUT=<dir>` dumps the legs rows for `docs/ekonomi-v2/araclar/validate_rows.py`.\n",""),
],[(r"    private fun dump\(rows: List<JSONObject>\) \{.*?\n    \}\n","")])
# --- K34 geç teslim: ekonomi satırı ifadeleri A4.1'e
edit('CustomerStageK34LateDeliveryTest.kt',[
 ("    /** Core events go through the economics recorder like OrderTracker does; legs rows are collected. */\n    private class Run(val core: DeliveryCore, val rec: EconomicsRecorder = EconomicsRecorder()) {\n        val events = ArrayList<CoreEvent>(); val rows = ArrayList<EconomicsRecorder.Row>()",
  "    /** Core events are collected like OrderTracker does. WEEM: the economics recorder (legs rows) is `:economics`' (A4.1): its assertions are not here (ORDERS.md 15.1, 4.14). */\n    private class Run(val core: DeliveryCore) {\n        val events = ArrayList<CoreEvent>()"),
 ("val before = core.pending.values.associateBy { it.id }; val e = core.observe(o); events += e\n            rows += rec.frame(o, e, before, core, emptyList(), OfferGeo.UNKNOWN) { \"unknown\" }","events += core.observe(o)"),
 ("val before = core.pending.values.associateBy { it.id }; val e = core.click(pkg, text, at); events += e\n            rows += rec.events(at, e, before, core, emptyList(), { \"unknown\" })","events += core.click(pkg, text, at)"),
 ("        fun legs(tripId: String) = rows.filter { it.file == EconomicsLogs.LEGS && it.json.optString(\"tripId\") == tripId }\n",""),
 ("assertTrue(w.deliveredInferred); assertEquals(1, r.legs(w.id).size)","assertTrue(w.deliveredInferred)"),
 ("        // The legs row was written at the close; the real delivery adds none.\n        assertEquals(1, r.legs(w.id).size)\n",""),
 ("        assertEquals(\"unknown\", r.legs(w.id).single().json.getString(\"outcome\"))\n",""),
 ("assertEquals(listOf(w), atCustomer(r.core)); assertEquals(1, r.legs(b.id).size)","assertEquals(listOf(w), atCustomer(r.core))"),
 ("        assertTrue(r2.legs(w.id).isEmpty())\n",""),
 ("assertEquals(1_511_100L, w.completedAt); assertEquals(1, r.legs(w.id).size)","assertEquals(1_511_100L, w.completedAt)"),
 ("// Process death: trips come back from courier.db, a new recorder starts empty.","// Process death: trips come back from courier.db."),
])
# --- RecordJson: Store.ts -> LogTime.ts
edit('RecordJsonTest.kt',[("Store.ts(","LogTime.ts(")])

def replace_test(f,name,new):
    p=D+f;s=open(p,encoding='utf-8').read()
    i=s.index('@Test fun '+name+'(')
    # sonraki '    @Test' ya da sınıf sonu
    j=s.find('\n    @Test',i+5)
    if j<0: j=s.rindex('\n}')
    # j'den önceki boş satırları koru
    s=s[:i]+new.rstrip('\n')+'\n'+s[j:] if not s[j:].startswith('\n}') else s[:i]+new.rstrip('\n')+'\n'+s[j:]
    open(p,'w',encoding='utf-8').write(s)
edit('ParserEconomicsTest.kt',[('OverlayText::isOwn','OwnLines::isOwn')])
F='TripStagesE8Test.kt'
edit(F,[("    private fun stagesOf(j: JSONObject) = (0 until j.getJSONArray(\"events\").length()).map { j.getJSONArray(\"events\").getJSONObject(it) }\n",""),
 (" * E8 (Faz 3) kabul: \"sipariş aşamaları gerçek sırayla gelir\". Feed v2'nin yeni üst düzey `stages` nesnesi"," * E8 (Faz 3) kabul: \"sipariş aşamaları gerçek sırayla gelir\". WEEM: `OrderEvents.stages` (`logic/core` `TripStages` tipi; üretici `OrderStages`). Rawstats'ta feed v2'nin yeni üst düzey `stages` nesnesi")])
replace_test(F,'deliveredOrderStaysTenMinutesWithItsOutcomeAndTime','''    @Test fun deliveredOrderStaysTenMinutesWithItsOutcomeAndTime() {
        val c = DeliveryCore().also { it.start() }; val t = atCustomer(c)
        c.observe(waiting(ms("18:20"))); c.observe(waiting(ms("18:20") + 1_500)); c.observe(waiting(ms("18:20") + 4_000))
        assertEquals(TripPhase.FINISHED, t.phase)
        val done = t.completedAt!!
        val j = OrderStages.of(c.trips.values, { null }, done + 9 * 60_000L).trips[0]
        assertFalse(j.active); assertNull(j.stage)
        assertEquals(TripStages.Outcome.DELIVERED, j.outcome); assertEquals(done, j.endedAtMs)
        assertEquals(listOf(TripStages.Stage.TO_PICKUP, TripStages.Stage.AT_PICKUP, TripStages.Stage.TO_DROPOFF), j.events.map { it.stage })
        assertEquals(0, OrderStages.of(c.trips.values, { null }, done + 11 * 60_000L).trips.size)
    }
''')
replace_test(F,'cancelledBeforePickupAndUnverifiedDeliveryAreTold','''    @Test fun cancelledBeforePickupAndUnverifiedDeliveryAreTold() {
        // D7: accepted, then Wolt's waiting map before the pickup: not delivered (cancelled).
        val c = DeliveryCore().also { it.start() }
        c.observe(offer(ms("12:27"), "Soloha")); c.observe(offer(ms("12:27") + 1_500, "Soloha")); c.observe(pickup(ms("12:27") + 4_000, "Soloha"))
        val cancelled = c.active(wolt)!!
        val closed = (c.observe(waiting(ms("12:36"))) + c.observe(waiting(ms("12:36") + 3_500))).single { it.type == "end_unconfirmed" }.at
        val j = OrderStages.of(c.trips.values, { id -> closed.takeIf { id == cancelled.id } }, ms("12:37")).trips[0]
        assertEquals(TripStages.Outcome.CANCELLED, j.outcome); assertEquals(closed, j.endedAtMs)
        // Without the close time (a restart lost it) the order is not listed: its age is unknown.
        assertEquals(0, OrderStages.of(c.trips.values, { null }, ms("12:37")).trips.size)

        // D3(b): the next order's restaurant screen after the slider: an unverified delivery at that moment.
        val d = DeliveryCore().also { it.start() }; val first = atCustomer(d)
        d.observe(offer(ms("18:20"), "Hatay Usulü Dönər")); d.observe(pickup(ms("18:20") + 3_000, "Hatay Usulü Dönər"))
        assertTrue(first.deliveredInferred)
        val trips = OrderStages.of(d.trips.values, { null }, ms("18:21")).trips
        assertEquals(2, trips.size)
        val ended = trips.single { it.tripId == first.id }
        assertEquals(TripStages.Outcome.DELIVERED_UNVERIFIED, ended.outcome); assertEquals(ms("18:20") + 3_000, ended.endedAtMs)
        val next = trips.single { it.active }
        assertEquals(TripStages.Stage.TO_PICKUP, next.stage)
    }
''')
replace_test(F,'secondPickupOfOneOrderGoesBackToPickupInOrder','''    @Test fun secondPickupOfOneOrderGoesBackToPickupInOrder() {
        val c = DeliveryCore().also { it.start() }
        c.observe(offer(ms("13:00"), "Mix Point")); c.observe(offer(ms("13:00") + 1_500, "Mix Point"))
        c.observe(pickup(ms("13:00") + 4_000, "Mix Point"))
        val t = c.active(wolt)!!
        c.observe(cardNoSlider(ms("13:08")))               // first restaurant collected
        c.observe(pickup(ms("13:10"), "Bravo Market"))     // a second restaurant of the same order
        val e = OrderStages.trip(t, null).events
        assertEquals(listOf(TripStages.Stage.TO_PICKUP, TripStages.Stage.TO_PICKUP), e.map { it.stage })
        assertEquals(listOf(0, 1), e.map { it.pickup })
        assertEquals(listOf(ms("13:00") + 4_000, ms("13:08")), e.map { it.atMs })
        assertEquals(TripStages.Stage.TO_PICKUP, OrderStages.trip(t, null).stage)
    }
''')
replace_test(F,'stagesHoldNoCustomerData','''    @Test fun stagesHoldNoCustomerData() {
        val c = DeliveryCore().also { it.start() }; atCustomer(c)
        val text = OrderStages.of(c.trips.values, { null }, ms("18:13")).toJson().toString()
        for (secret in listOf("Test A.", "Test küçəsi", "#330", "Borani")) assertFalse(secret, text.contains(secret))
    }
''')
edit('CustomerStageK34RecordTest.kt',[("    @Test fun theCourierCanStillConfirmTheDeliveryByHand() {","""    /** Rawstats read this from the feed v2 `courierState.activeTrips`; WEEM: `OrderEvents.stages` (`OrderStages`, ORDERS.md 13.2, 15.1). */
    @Test fun feedV2ShowsOneOrderAtTheCustomer() {
        val d = day()
        val trips = OrderStages.of(d.core.trips.values, { null }, 1_200_000L).trips.filter { it.active }
        assertEquals(1, trips.size); assertEquals(d.bolt.id, trips[0].tripId)
        assertEquals(TripStages.Stage.TO_DROPOFF, trips[0].stage)
    }
    @Test fun theCourierCanStillConfirmTheDeliveryByHand() {""")])
