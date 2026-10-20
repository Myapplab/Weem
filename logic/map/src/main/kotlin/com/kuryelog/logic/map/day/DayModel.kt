package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.GeoPoint
import org.json.JSONArray
import org.json.JSONObject

/** Gün motorunun konum okuması (Dart `Fix`; MAP.md 11.17): [speedMps] negatifse bilinmiyor. */
data class DayFix(val atMs: Long, val lat: Double, val lon: Double, val speedMps: Double = 0.0, val accuracy: Double = 5.0)

/** Durma noktasının yolla ilişkisi (harita verisinden). */
enum class StopSurface { UNKNOWN, ROAD, SIDEWALK }

/**
 * Durma noktasının yol bilgisi (Dart `RoadInfo`, MAP.md 11.8): [known] true = trafik ışığının yanında, false = ışık değil, null = bilinmiyor;
 * [lightM] ışığa uzaklık (m); [maxLightM] ışık sayılması için en fazla uzaklık (ayar, varsayılan 5 m); [footM] yaya yoluna uzaklık.
 * İskelet (A6.2b): türetilenler A6.8'de.
 */
data class RoadInfo(
    val onRoad: Boolean = false,
    val junctionM: Double? = null,
    val known: Boolean? = null,
    val lightLat: Double? = null,
    val lightLon: Double? = null,
    val surface: StopSurface = StopSurface.UNKNOWN,
    val lightM: Double? = null,
    val maxLightM: Double = 5.0,
    val mall: Boolean = false,
    val roadside: Boolean = false,
    val manualSidewalk: Boolean = false,
    val footM: Double? = null,
) {
    /** Işığa ≤[maxLightM] ve ((yoldayım ve yaya yolunda değilim) ya da yaya yoluna ≤1 m); AVM ve elle kaldırım ışık değildir; ışık bilinmiyorsa değil. */
    val atLight: Boolean
        get() {
            if (mall || manualSidewalk || known != true) return false
            val d = lightM ?: return surface != StopSurface.SIDEWALK && !roadside
            return d <= maxLightM && (onRoad || (footM ?: Double.POSITIVE_INFINITY) <= 1.0)
        }

    /** AVM'de trafik ışığı olamaz. */
    val allowsTrafficLight: Boolean get() = onRoad && surface != StopSurface.SIDEWALK && !mall && !roadside
}

enum class OrderPhase(val jsonName: String) {
    NONE("none"), TO_RESTAURANT("toRestaurant"), AT_RESTAURANT("atRestaurant"), READY_FOR_CUSTOMER("readyForCustomer"),
    TO_CUSTOMER("toCustomer"), AT_CUSTOMER("atCustomer");

    companion object {
        /** Bilinmeyen ad `none` (Dart `byName` yedeği). */
        fun fromJsonName(n: String): OrderPhase = values().firstOrNull { it.jsonName == n } ?: NONE
    }
}

/**
 * Bir duruş kaydı (Dart `StopRec`): [kind] `light` | `stop` | `order` | `light?` | `mall` | `sidewalk`; [longWait] 2 dk üstü ışık duruşu
 * (`light?` + `long` alanı); [measured] canlı ölçerden. JSON biçimi `VERI_SOZLESMESI` 11 (`long` yalnız doğruysa yazılır).
 */
class StopRec(
    val startMs: Long,
    val endMs: Long,
    val lat: Double,
    val lon: Double,
    val kind: String,
    val onRoad: Boolean,
    val junctionM: Double? = null,
    val inOrder: Boolean = false,
    var answer: String? = null,
    val lightLat: Double? = null,
    val lightLon: Double? = null,
    val measured: Boolean = false,
    val surface: StopSurface = StopSurface.UNKNOWN,
    val longWait: Boolean = false,
) {
    val durS: Int get() = ((endMs - startMs) / 1000).toInt()

    /** Işık sayılır mı (cevap "light", ya da `kind == "light"` ve cevap "not" değil). */
    val isLight: Boolean get() = answer == "light" || (answer == null && kind == "light")

    fun toJson(): JSONObject {
        val j = JSONObject()
            .put("start", startMs).put("end", endMs)
            .put("lat", round6(lat)).put("lon", round6(lon))
            .put("kind", kind).put("onRoad", onRoad)
        if (junctionM != null) j.put("jm", Math.round(junctionM))
        j.put("inOrder", inOrder)
        if (answer != null) j.put("answer", answer)
        if (lightLat != null) j.put("llat", round6(lightLat))
        if (lightLon != null) j.put("llon", round6(lightLon))
        if (measured) j.put("measured", true)
        j.put("surface", surface.name.lowercase())
        if (longWait) j.put("long", true)
        return j
    }

    companion object {
        fun fromJson(j: JSONObject): StopRec = StopRec(
            startMs = j.getLong("start"),
            endMs = j.getLong("end"),
            lat = j.getDouble("lat"),
            lon = j.getDouble("lon"),
            kind = j.getString("kind"),
            onRoad = j.optBoolean("onRoad", false),
            junctionM = if (j.has("jm") && !j.isNull("jm")) j.getDouble("jm") else null,
            inOrder = j.optBoolean("inOrder", false),
            answer = if (j.has("answer") && !j.isNull("answer")) j.getString("answer") else null,
            lightLat = if (j.has("llat") && !j.isNull("llat")) j.getDouble("llat") else null,
            lightLon = if (j.has("llon") && !j.isNull("llon")) j.getDouble("llon") else null,
            measured = j.optBoolean("measured", false),
            surface = when (j.optString("surface")) {
                "road" -> StopSurface.ROAD
                "sidewalk" -> StopSurface.SIDEWALK
                else -> StopSurface.UNKNOWN
            },
            longWait = j.optBoolean("long", false),
        )
    }
}

private fun round6(v: Double): Double = Math.round(v * 1e6) / 1e6

private fun JSONObject.optLongOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
private fun JSONObject.optDoubleOrNull(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
private fun JSONObject.optPoint(k: String): GeoPoint? = optJSONObject(k)?.let { GeoPoint(it.getDouble("lat"), it.getDouble("lon")) }

/** Bir sipariş (Dart `OrderRec`; MAP.md 11.5): zamanlar epoch ms, null = olmadı. */
class OrderRec(val n: Int, val startedAtMs: Long) {
    var restArrivedAtMs: Long? = null
    var restLeftAtMs: Long? = null
    var custArrivedAtMs: Long? = null
    var deliveredAtMs: Long? = null
    var endedAtMs: Long? = null
    var customerDepartedAtMs: Long? = null
    var restLat: Double? = null
    var restLon: Double? = null
    var custLat: Double? = null
    var custLon: Double? = null
    var restAuto = false
    var custAuto = false
    var cancelled = false
    var distM = 0.0
    var restTarget: GeoPoint? = null
    var customerTarget: GeoPoint? = null
    var restName = ""

    val phase: OrderPhase
        get() = when {
            custArrivedAtMs != null -> OrderPhase.AT_CUSTOMER
            restLeftAtMs != null -> if (customerDepartedAtMs == null) OrderPhase.READY_FOR_CUSTOMER else OrderPhase.TO_CUSTOMER
            restArrivedAtMs != null -> OrderPhase.AT_RESTAURANT
            else -> OrderPhase.TO_RESTAURANT
        }

    /** Bekleme süreleri (sn): varış ya da çıkış görülmediyse null (uydurulmaz). */
    val restWaitS: Int? get() = secs(restArrivedAtMs, restLeftAtMs)
    val custWaitS: Int? get() = secs(custArrivedAtMs, deliveredAtMs)
    val totalS: Int? get() = secs(startedAtMs, deliveredAtMs ?: endedAtMs)

    private fun secs(from: Long?, to: Long?): Int? = if (from == null || to == null) null else ((to - from) / 1000).toInt()

    fun toJson(): JSONObject {
        val j = JSONObject().put("n", n).put("start", startedAtMs)
        restArrivedAtMs?.let { j.put("restArrived", it) }
        restLeftAtMs?.let { j.put("restLeft", it) }
        custArrivedAtMs?.let { j.put("custArrived", it) }
        deliveredAtMs?.let { j.put("delivered", it) }
        endedAtMs?.let { j.put("ended", it) }
        restLat?.let { j.put("restLat", it) }
        restLon?.let { j.put("restLon", it) }
        custLat?.let { j.put("custLat", it) }
        custLon?.let { j.put("custLon", it) }
        j.put("restAuto", restAuto).put("custAuto", custAuto).put("cancelled", cancelled)
        j.put("m", Math.round(distM))
        restTarget?.let { j.put("restTarget", JSONObject().put("lat", it.lat).put("lon", it.lon)) }
        customerTarget?.let { j.put("customerTarget", JSONObject().put("lat", it.lat).put("lon", it.lon)) }
        if (restName.isNotEmpty()) j.put("restName", restName)
        j.put("pickupFlow", 2)
        customerDepartedAtMs?.let { j.put("customerDeparted", it) }
        return j
    }

    companion object {
        fun fromJson(j: JSONObject): OrderRec {
            val o = OrderRec(j.getInt("n"), j.getLong("start"))
            o.restArrivedAtMs = j.optLongOrNull("restArrived")
            o.restLeftAtMs = j.optLongOrNull("restLeft")
            o.custArrivedAtMs = j.optLongOrNull("custArrived")
            o.deliveredAtMs = j.optLongOrNull("delivered")
            o.endedAtMs = j.optLongOrNull("ended")
            o.restLat = j.optDoubleOrNull("restLat")
            o.restLon = j.optDoubleOrNull("restLon")
            o.custLat = j.optDoubleOrNull("custLat")
            o.custLon = j.optDoubleOrNull("custLon")
            o.restAuto = j.optBoolean("restAuto", false)
            o.custAuto = j.optBoolean("custAuto", false)
            o.cancelled = j.optBoolean("cancelled", false)
            o.distM = j.optDouble("m", 0.0)
            o.restTarget = j.optPoint("restTarget")
            o.customerTarget = j.optPoint("customerTarget")
            o.restName = j.optString("restName", "")
            o.customerDepartedAtMs = j.optLongOrNull("customerDeparted")
            // Eski kayıt (pickupFlow yok, customerDeparted yok): restoran çıkışı müşteriye yolculuk sayılır.
            if (o.customerDepartedAtMs == null && !j.has("pickupFlow")) o.customerDepartedAtMs = o.restLeftAtMs
            return o
        }
    }
}

/** Motorun bir olayı (Dart `ShiftEvent`): [type] `day_start`, `order_start`, `arrive_restaurant`, `stop`, `delivered`, `order_cancel`, `ask` … */
data class DayEvent(val type: String, val atMs: Long, val data: Map<String, Any?> = emptyMap())

/**
 * Gün durumu (`shift.v1`, `v: 2`; MAP.md 11.5): km, hareket süresi, duruşlar, siparişler, eve dönüş alanları (`homeReturnAt`, `homeStartM`, `homeDistM`, `homeTimeS`).
 * Eski kayıt eksik alanlarla okunur; eski `question`, `pickupGroup`, `secondOrderWindowUsed` okunur, yazılmaz.
 */
class DayState {
    var active = false
    var startedAtMs: Long? = null
    var endedAtMs: Long? = null
    var distM = 0.0
    var movingS = 0.0
    val stops = ArrayList<StopRec>()
    val orders = ArrayList<OrderRec>()
    /** 1.10.0 öncesi ikinci sipariş (MAP.md 11.5): motor üretmez, yalnız okur. */
    val queuedOrders = ArrayList<OrderRec>()
    var current: OrderRec? = null
    var phase = OrderPhase.NONE
    var delivered = 0
    var homeReturnAtMs: Long? = null
    var homeStartM = 0.0
    var homeDistM = 0.0
    var homeTimeS = 0.0
    var startReason: String? = null
    var endReason: String? = null

    /** Yeni gün için boş durum (Dart `startDay` yeni `ShiftState` kurar; burada nesne korunur). */
    fun reset() {
        active = false; startedAtMs = null; endedAtMs = null; distM = 0.0; movingS = 0.0
        stops.clear(); orders.clear(); queuedOrders.clear(); current = null; phase = OrderPhase.NONE; delivered = 0
        homeReturnAtMs = null; homeStartM = 0.0; homeDistM = 0.0; homeTimeS = 0.0; startReason = null; endReason = null
    }

    /** Gün sonunda sorulacak adaylar (`light?` ve cevapsız) ve ışık sayılanlar. */
    val lightCandidates: List<StopRec> get() = stops.filter { it.kind == "light?" && it.answer == null }
    val lights: List<StopRec> get() = stops.filter { it.isLight }

    fun toJson(): JSONObject {
        val j = JSONObject().put("v", 2).put("active", active)
        startedAtMs?.let { j.put("dayId", "yolgo-$it").put("start", it) }
        endedAtMs?.let { j.put("end", it) }
        j.put("m", Math.round(distM)).put("movingS", Math.round(movingS))
        j.put("stops", JSONArray().also { a -> stops.forEach { a.put(it.toJson()) } })
        j.put("orders", JSONArray().also { a -> orders.forEach { a.put(it.toJson()) } })
        j.put("queuedOrders", JSONArray().also { a -> queuedOrders.forEach { a.put(it.toJson()) } })
        current?.let { j.put("current", it.toJson()) }
        j.put("phase", phase.jsonName)
        homeReturnAtMs?.let { j.put("homeReturnAt", it) }
        j.put("homeStartM", homeStartM).put("homeDistM", homeDistM).put("homeTimeS", homeTimeS)
        startReason?.let { j.put("startReason", it) }
        endReason?.let { j.put("endReason", it) }
        return j
    }

    companion object {
        fun fromJson(j: JSONObject): DayState {
            val s = DayState()
            s.active = j.optBoolean("active", false)
            s.startedAtMs = j.optLongOrNull("start")
            s.endedAtMs = j.optLongOrNull("end")
            s.distM = j.optDouble("m", 0.0)
            s.movingS = j.optDouble("movingS", 0.0)
            j.optJSONArray("stops")?.let { a -> for (i in 0 until a.length()) s.stops += StopRec.fromJson(a.getJSONObject(i)) }
            j.optJSONArray("orders")?.let { a -> for (i in 0 until a.length()) s.orders += OrderRec.fromJson(a.getJSONObject(i)) }
            j.optJSONArray("queuedOrders")?.let { a -> for (i in 0 until a.length()) s.queuedOrders += OrderRec.fromJson(a.getJSONObject(i)) }
            s.current = j.optJSONObject("current")?.let { OrderRec.fromJson(it) }
            s.phase = OrderPhase.fromJsonName(j.optString("phase"))
            s.delivered = s.orders.count { it.deliveredAtMs != null }
            s.homeReturnAtMs = j.optLongOrNull("homeReturnAt")
            s.homeStartM = j.optDouble("homeStartM", 0.0)
            s.homeDistM = j.optDouble("homeDistM", 0.0)
            s.homeTimeS = j.optDouble("homeTimeS", 0.0)
            s.startReason = if (j.has("startReason") && !j.isNull("startReason")) j.getString("startReason") else null
            s.endReason = if (j.has("endReason") && !j.isNull("endReason")) j.getString("endReason") else null
            return s
        }
    }
}
