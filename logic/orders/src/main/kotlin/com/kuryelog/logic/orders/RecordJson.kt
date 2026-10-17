package com.kuryelog.logic.orders

import org.json.JSONArray
import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats RecordJson.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
internal fun JSONObject.str(k: String): String? = optString(k).takeIf { it.isNotBlank() && it != "null" }
internal fun JSONObject.num(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k).takeIf { it.isFinite() } else null
internal fun JSONObject.longOrNull(k: String): Long? = num(k)?.toLong()
internal fun JSONArray.strings() = (0 until length()).map { getString(it) }
internal fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
internal object RecordJson {
    /** Trips kept in memory after a restart: deliveries still open from yesterday
     *  (accepted before midnight) first, then today's. Damaged rows are skipped. */
    fun restorable(today: List<JSONObject>, yesterday: List<JSONObject>): List<TripRecord> =
        yesterday.mapNotNull { runCatching { trip(it) }.getOrNull() }.filter { it.active } +
            today.mapNotNull { runCatching { trip(it) }.getOrNull() }
    /** Phone numbers are never written: the phone field is skipped and numbers inside free text are masked. */
    private fun clean(s: String?) = NoteFields.redact(s)
    fun details(d: DeliveryDetails) = JSONObject().put("name", d.name).put("address", clean(d.address)).put("note", clean(d.note)).put("instructions", clean(d.instructions))
        .put("building", d.building).put("entrance", d.entrance).put("floor", d.floor).put("apartment", d.apartment).put("door_code", d.doorCode).put("check_id", d.checkId)
        .put("address_details", clean(d.addressDetails))
    fun details(j: JSONObject) = DeliveryDetails(j.str("name"), j.str("address"), j.str("note"), j.str("instructions"), j.str("building"), j.str("entrance"), j.str("floor"), j.str("apartment"), j.str("door_code"), j.optBoolean("check_id"), j.str("address_details"))
    fun observation(o: CourierObservation) = JSONObject().put("pkg", o.pkg).put("at", o.at).put("stage", o.stage.name).put("complete", o.complete).put("source", o.source)
        .put("raw", JSONArray(o.raw.map { clean(it) })).put("actions", JSONArray(o.actions.map { clean(it) })).put("gross", o.gross).put("km", o.km).put("min_lo", o.minLo).put("min_hi", o.minHi)
        .put("stops", o.stops).put("quantity", o.quantity).put("restaurant", o.restaurant).put("restaurant_address", o.restaurantAddress).put("customer_address", o.customerAddress)
        .put("ids", JSONArray(o.orderIds)).put("customer", details(o.customer)).put("ready_in", o.readyIn).put("pickup_clock", o.pickupClock).put("dropoff_clock", o.dropoffClock)
        .put("travel_eta", o.travelEta).put("arrived", o.arrived).put("ready", o.ready).put("delivered", o.delivered).put("deliver_control", o.deliverControl)
        // Offer-card values of the 2.1.0 logs (Bolt's minute split, demand, active boost), so a restored offer keeps them.
        .put("approach_min", o.approachMin).put("delivery_min", o.deliveryMin).put("demand", o.demand).put("bonus_pct", o.bonusPct)
        .put("multipliers", JSONArray(o.multipliers))
    fun observation(j: JSONObject) = CourierObservation(j.getString("pkg"), j.getLong("at"), ScreenStage.valueOf(j.getString("stage")), j.optBoolean("complete"), j.optString("source"),
        j.optJSONArray("raw")?.strings() ?: emptyList(), j.optJSONArray("actions")?.strings() ?: emptyList(), j.num("gross"), j.num("km"), j.num("min_lo")?.toInt(), j.num("min_hi")?.toInt(),
        j.num("stops")?.toInt(), j.num("quantity")?.toInt(), j.str("restaurant"), j.str("restaurant_address"), j.str("customer_address"), j.optJSONArray("ids")?.strings() ?: emptyList(),
        j.optJSONObject("customer")?.let(::details) ?: DeliveryDetails(), j.num("ready_in")?.toInt(), j.num("pickup_clock")?.toInt(), j.num("dropoff_clock")?.toInt(), j.num("travel_eta")?.toInt(),
        j.optBoolean("arrived"), j.optBoolean("ready"), j.optBoolean("delivered"), deliverControl = j.optBoolean("deliver_control"),
        approachMin = j.num("approach_min")?.toInt(), deliveryMin = j.num("delivery_min")?.toInt(), demand = j.str("demand"), bonusPct = j.num("bonus_pct")?.toInt(),
        multipliers = j.optJSONArray("multipliers")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optDouble(i).takeIf { it.isFinite() } } } ?: emptyList())
    fun offer(o: OfferState) = JSONObject().put("id", o.id).put("pkg", o.pkg).put("day", LogTime.day(o.firstAt)).put("at", o.firstAt).put("battery_pct", o.phoneBattery).put("data", observation(o.data))
        .put("lat",o.lat).put("lon",o.lon).put("gps_accuracy_m",o.gpsAccuracyM).put("gps_age_ms",o.gpsAgeMs)
        .put("scooter_battery_pct",o.scooterBatteryPct).put("scooter_observed_at",o.scooterObservedAt).put("scooter_source",o.scooterSource)
        .put("session_id", o.sessionId).put("last_seen_at", o.lastSeenAt).put("decline_click_at", o.declineClickAt)
        .put("optional", o.optional).put("field_source", JSONObject().also { f -> o.fieldSource.forEach { (k, v) -> f.put(k, v) } })
        // K27: written only once the offer had a boost lookup; older rows read back as null.
        .also { j -> o.weather?.into(j) }
        .also { j -> o.boostSource?.let { j.put("boost_value", o.boostValue ?: JSONObject.NULL).put("boost_unit", o.boostUnit ?: JSONObject.NULL).put("boost_source", it).put("boost_seen_at", o.boostSeenAt ?: JSONObject.NULL) } }
        // D6 read timing: written only when observed; older rows read back as null.
        .also { j -> o.feeAt?.let { j.put("fee_at", it).put("fee_gap_ms", o.feeGapMs ?: JSONObject.NULL).put("fee_ocr_ms", o.feeOcrMs ?: JSONObject.NULL) }; o.notifiedAt?.let { j.put("notified_at", it) } }
    fun offer(j: JSONObject) = OfferState(j.getString("id"), j.getString("pkg"), j.getLong("at"), observation(j.getJSONObject("data")), j.num("battery_pct")?.toInt(),
        j.num("lat"),j.num("lon"),j.num("gps_accuracy_m"),j.longOrNull("gps_age_ms"),j.num("scooter_battery_pct"),j.longOrNull("scooter_observed_at"),j.str("scooter_source")).also { o ->
        o.sessionId = j.str("session_id"); o.lastSeenAt = j.longOrNull("last_seen_at") ?: o.firstAt; o.declineClickAt = j.longOrNull("decline_click_at")
        o.optional = j.optBoolean("optional"); j.optJSONObject("field_source")?.let { f -> f.keys().forEach { k -> f.str(k)?.let { o.fieldSource[k] = it } } }
        o.weather = OfferWeather.fromRecord(j); o.boostValue = j.num("boost_value"); o.boostUnit = j.str("boost_unit"); o.boostSource = j.str("boost_source"); o.boostSeenAt = j.longOrNull("boost_seen_at")
        o.feeAt = j.longOrNull("fee_at"); o.feeGapMs = j.longOrNull("fee_gap_ms"); o.feeOcrMs = j.longOrNull("fee_ocr_ms"); o.notifiedAt = j.longOrNull("notified_at") }
    /** withPhone only for courier.db on the phone (K25); logs, snapshots and the ZIP backup never get the number. */
    fun trip(t: TripRecord, withPhone: Boolean = false): JSONObject = JSONObject().put("id", t.id).put("pkg", t.pkg).put("day", LogTime.day(t.acceptedAt)).put("accepted_at", t.acceptedAt)
        .put("phase", t.phase.name).put("offer", t.offer?.let(::offer)).put("completed_at", t.completedAt).put("expected_orders", t.expectedOrders).put("current_customer", t.currentCustomer)
        .put("distance_m", t.distanceM).put("used_wh", t.usedWh).put("recovered_wh", t.recoveredWh).put("energy_covered_ms", t.energyCoveredMs).put("energy_shared", t.energyShared)
        .put("tip", t.tip).put("commission_pct", t.commissionPct).put("price_is_net", t.priceIsNet).put("missed_offer", t.missedOffer)
        .put("pickup_intent_at", t.pickupIntentAt).put("finish_intent_at", t.finishIntentAt).put("latest", t.latest?.let(::observation))
        .put("deliver_seen_for", t.deliverSeenFor).put("deliver_seen_at", t.deliverSeenAt).put("session_id", t.sessionId)
        // K34 unverified delivery: written only on such trips, so other records keep their 0.15.0 shape.
        .also { o -> t.deliveredInferredAt?.let { o.put("delivered_inferred_at", it).put("delivered_inferred_by", t.deliveredInferredBy).put("delivered_inferred_reason", t.deliveredInferredReason) } }
        // K33 slider spinner: written only when seen; older rows lack it and read back as null.
        .also { o -> t.deliverSpinnerAt?.let { o.put("deliver_spinner_at", it).put("deliver_spinner_for", t.deliverSpinnerFor) } }
        // K31/K32 payment and tip source: written only when observed / set; older rows read back as null ("not_observed").
        .also { o -> t.paymentMethod?.let { o.put("payment_method", it).put("payment_source", t.paymentSource).put("cash_amount_azn", t.cashAmount ?: JSONObject.NULL).put("payment_at", t.paymentAt) } }
        .also { o -> t.cashNoChange?.let { o.put("cash_no_change", it) }; t.cashChangeAzn?.let { o.put("cash_change_azn", it) }; t.cashCheckedAt?.let { o.put("cash_checked_at", it) } }
        .also { o -> t.tipSource?.let { o.put("tip_source", it) } }
        // D3: platform list confirmation and Wolt Finances cash collection; written only when seen, older rows read back as null.
        .also { o -> t.verifiedBy?.let { o.put("verified_by", it).put("verified_at", t.verifiedAt) } }
        .also { o -> t.cashCollectedAt?.let { o.put("cash_collected_at", it).put("cash_collected_azn", t.cashCollectedAzn ?: JSONObject.NULL) } }
        // D15: the earnings list's extra waiting fee and in-app tip of the order; written only when read.
        .also { o -> if (t.extraWaitAzn > 0) o.put("extra_wait_azn", t.extraWaitAzn); t.platformTipAzn?.let { o.put("platform_tip_azn", it) } }
        // D10: time the external pack was known but not read (no Wh counted); written only when there was such time.
        .also { o -> if (t.energyPartialMs > 0) o.put("energy_partial_ms", t.energyPartialMs) }
        .put("pickups", JSONArray(t.pickups.map { p -> JSONObject().put("restaurant", p.restaurant).put("address", p.address).put("ready_at", p.readyAt).put("arrived_at", p.arrivedAt)
            .put("picked_at", p.pickedAt).put("arrival_source", p.arrivalSource).put("ready_observed_at", p.readyObservedAt)
            .put("note", clean(p.note)).put("code", p.code).put("items", JSONArray(p.items)).put("ready_hint_at", p.readyHintAt).put("preparing_at", p.preparingAt).put("reminder_at", p.reminderAt) }))
        .put("customers", JSONArray(t.customers.values.map { c -> JSONObject().put("id", c.id).put("details", details(c.details)).put("arrived_at", c.arrivedAt).put("delivered_at", c.deliveredAt)
            .put("lat", c.lat).put("lon", c.lon).put("coordinate_source", c.coordinateSource).put("reminder_at", c.reminderAt).put("details_at", c.detailsAt).put("deadline_clock", c.deadlineClock)
                .also { o -> c.deadlineAt?.let { o.put("deadline_at", it) }; c.arrivalSource?.let { o.put("arrival_source", it) } }
            .also { o -> if (withPhone) o.put("phone", c.details.phone) } }))
    fun trip(j: JSONObject): TripRecord {
        val t = TripRecord(j.getString("id"), j.getString("pkg"), j.optJSONObject("offer")?.let(::offer), j.getLong("accepted_at"), TripPhase.valueOf(j.getString("phase")))
        t.completedAt = j.longOrNull("completed_at"); t.expectedOrders = j.optInt("expected_orders", 1); t.currentCustomer = j.str("current_customer")
        t.distanceM = j.optDouble("distance_m", 0.0); t.usedWh = j.optDouble("used_wh", 0.0); t.recoveredWh = j.optDouble("recovered_wh", 0.0)
        t.energyCoveredMs = j.optLong("energy_covered_ms"); t.energyShared = j.optBoolean("energy_shared"); t.tip = j.optDouble("tip", 0.0)
        t.commissionPct = j.optDouble("commission_pct", 0.0); t.priceIsNet = j.optBoolean("price_is_net"); t.missedOffer = j.optBoolean("missed_offer")
        t.pickupIntentAt = j.longOrNull("pickup_intent_at"); t.finishIntentAt = j.longOrNull("finish_intent_at"); t.latest = j.optJSONObject("latest")?.let(::observation)
        t.deliverSeenFor = j.str("deliver_seen_for"); t.deliverSeenAt = j.longOrNull("deliver_seen_at"); t.sessionId = j.str("session_id")
        t.deliveredInferredAt = j.longOrNull("delivered_inferred_at"); t.deliveredInferredBy = j.str("delivered_inferred_by"); t.deliveredInferredReason = j.str("delivered_inferred_reason")
        t.deliverSpinnerAt = j.longOrNull("deliver_spinner_at"); t.deliverSpinnerFor = j.str("deliver_spinner_for")
        t.paymentMethod = j.str("payment_method"); t.paymentSource = j.str("payment_source"); t.cashAmount = j.num("cash_amount_azn"); t.paymentAt = j.longOrNull("payment_at")
        t.cashNoChange = if (j.has("cash_no_change")) j.optBoolean("cash_no_change") else null; t.cashChangeAzn = j.num("cash_change_azn"); t.cashCheckedAt = j.longOrNull("cash_checked_at")
        t.tipSource = j.str("tip_source")
        t.verifiedBy = j.str("verified_by"); t.verifiedAt = j.longOrNull("verified_at")
        t.cashCollectedAt = j.longOrNull("cash_collected_at"); t.cashCollectedAzn = j.num("cash_collected_azn")
        t.extraWaitAzn = j.num("extra_wait_azn") ?: 0.0; t.platformTipAzn = j.num("platform_tip_azn")
        t.energyPartialMs = j.optLong("energy_partial_ms")
        j.optJSONArray("pickups")?.objects()?.forEach { p -> t.pickups.add(PickupRecord(p.str("restaurant"), p.str("address"), p.longOrNull("ready_at"), p.longOrNull("arrived_at"), p.longOrNull("picked_at"), p.str("arrival_source"), p.longOrNull("ready_observed_at")).also { r ->
            r.note = p.str("note"); r.code = p.str("code"); r.items = p.optJSONArray("items")?.strings() ?: emptyList()
            r.readyHintAt = p.longOrNull("ready_hint_at"); r.preparingAt = p.longOrNull("preparing_at"); r.reminderAt = p.longOrNull("reminder_at") }) }
        j.optJSONArray("customers")?.objects()?.forEach { c -> t.customers[c.getString("id")] = CustomerRecord(c.getString("id"), details(c.getJSONObject("details")), c.longOrNull("arrived_at"), c.longOrNull("delivered_at"), c.num("lat"), c.num("lon"), c.str("coordinate_source"), c.longOrNull("reminder_at"), c.optLong("details_at"), c.num("deadline_clock")?.toInt()).also { r -> c.str("phone")?.let { r.details.phone = it }; r.deadlineAt = c.longOrNull("deadline_at"); r.arrivalSource = c.str("arrival_source") } }
        return t
    }
}
