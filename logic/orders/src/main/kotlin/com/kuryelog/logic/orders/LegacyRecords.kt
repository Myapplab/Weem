package com.kuryelog.logic.orders

import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats LegacyRecords.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/** Preserve previous deliveries without upgrading old guessed outcomes into fresh evidence. */
object LegacyRecords {
    fun delivered(j: JSONObject): TripRecord? {
        if (j.optString("type") != "delivered" || j.has("record")) return null
        val at=LogTime.parseTs(j.optString("t")); if (at<=0) return null
        val minutes=j.num("total_min")
        val accepted=if (minutes!=null && minutes>=0) at-(minutes*60000).toLong() else at
        val pkg=j.str("pkg") ?: "unknown"
        val first=LogTime.parseTs(j.optString("offer_at")).takeIf { it>0 } ?: accepted
        val observation=CourierObservation(pkg,first,ScreenStage.OFFER,true,"legacy_log",emptyList(),emptyList(),
            j.num("gross"),j.num("km"),j.num("min_lo")?.toInt(),j.num("min_hi")?.toInt(),j.num("stops")?.toInt(),
            restaurant=j.str("restaurant"),restaurantAddress=j.str("rest_addr"),customerAddress=j.str("cust_addr"))
        val id="legacy-${j.str("job") ?: "$pkg-$at"}"
        val t=TripRecord(id,pkg,OfferState("legacy-offer:$id",pkg,first,observation),accepted,TripPhase.FINISHED,
            completedAt=at,commissionPct=j.num("commission_pct") ?: 0.0)
        t.pickups.add(PickupRecord(j.str("restaurant"),j.str("rest_addr")))
        t.customers["customer-1"]=CustomerRecord("customer-1",DeliveryDetails(address=j.str("cust_addr"),note=j.str("note")),deliveredAt=at)
        t.currentCustomer="customer-1"
        return t
    }
}
