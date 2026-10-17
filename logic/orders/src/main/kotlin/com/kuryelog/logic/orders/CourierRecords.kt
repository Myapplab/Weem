package com.kuryelog.logic.orders

import org.json.JSONObject

/**
 * `databases/courier.db` (RS `CourierDb.kt:19-44` @ 6388c04; şema `logic/core` `CourierSchema`). WEEM (ORDERS.md 4.14): `OrderFlow`
 * bunu görür; Android'de SQLite uygulaması (A3.7), testlerde [Memory]. Satır anahtarları Rawstats'la aynı: `trips(id, pkg, day,
 * payload)`, `offers(id, pkg, day, result, payload)`, `session(k = active, payload)`.
 */
interface CourierRecords {
    fun saveTrip(j: JSONObject)
    fun saveOffer(j: JSONObject, result: String? = null)
    /** [day] null: hepsi; sıra `id`. */
    fun trips(day: String? = null): List<JSONObject>
    fun offer(id: String): JSONObject?
    fun offerCount(day: String, result: String): Int
    fun session(): JSONObject?
    fun session(j: JSONObject)
    /** Tek işlem (Rawstats: bir günlük kaydı commit'i). */
    fun <T> batch(block: () -> T): T
    fun clear()

    /** Bellekte (testler ve saf araçlar); SQLite'ın `ORDER BY id` sırası. */
    class Memory : CourierRecords {
        val tripRows = java.util.TreeMap<String, JSONObject>()
        val offerRows = java.util.TreeMap<String, Pair<JSONObject, String?>>()
        var sessionRow: JSONObject? = null

        @Synchronized override fun saveTrip(j: JSONObject) { j.getString("pkg"); j.getString("day"); tripRows[j.getString("id")] = JSONObject(j.toString()) }
        @Synchronized override fun saveOffer(j: JSONObject, result: String?) { j.getString("pkg"); j.getString("day"); offerRows[j.getString("id")] = JSONObject(j.toString()) to result }
        @Synchronized override fun trips(day: String?) = tripRows.values.filter { day == null || it.getString("day") == day }.map { JSONObject(it.toString()) }
        @Synchronized override fun offer(id: String) = offerRows[id]?.first?.let { JSONObject(it.toString()) }
        @Synchronized override fun offerCount(day: String, result: String) = offerRows.values.count { it.first.getString("day") == day && it.second == result }
        @Synchronized override fun session() = sessionRow?.let { JSONObject(it.toString()) }
        @Synchronized override fun session(j: JSONObject) { sessionRow = JSONObject(j.toString()) }
        @Synchronized override fun <T> batch(block: () -> T): T = block()
        @Synchronized override fun clear() { tripRows.clear(); offerRows.clear(); sessionRow = null }
    }
}
