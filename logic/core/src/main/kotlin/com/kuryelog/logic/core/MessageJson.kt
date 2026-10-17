package com.kuryelog.logic.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Mesajların ortak JSON kuralları (plan 4.4): her mesaj `schemaVersion` taşır; okuyucu bilinmeyen alanı yok sayar; ölçülmeyen sayı
 * `null` (uydurulmaz; NaN/∞ da null); zamanlar gönderenin duvar saati (ms). Eşitlik ayrıştırılmış değerle ölçülür (alan sırası önemsiz).
 */
internal object Msg {
    fun obj(raw: String?): JSONObject? = if (raw.isNullOrEmpty()) null else try { JSONObject(raw) } catch (_: Exception) { null }
    fun version(j: JSONObject): Int? = (j.opt("schemaVersion") as? Number)?.toInt()

    fun num(j: JSONObject, k: String): Double? = (j.opt(k) as? Number)?.toDouble()?.takeIf { it.isFinite() }
    fun long(j: JSONObject, k: String): Long? = (j.opt(k) as? Number)?.let { n -> if (n is Double || n is Float) n.toDouble().takeIf { it.isFinite() }?.toLong() else n.toLong() }
    fun positive(j: JSONObject, k: String): Long? = long(j, k)?.takeIf { it > 0 }
    fun int(j: JSONObject, k: String): Int? = long(j, k)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
    fun str(j: JSONObject, k: String): String? = j.opt(k) as? String
    fun bool(j: JSONObject, k: String): Boolean? = j.opt(k) as? Boolean

    /** null ve sonsuz sayı → JSON null. */
    fun JSONObject.putN(k: String, v: Any?): JSONObject = put(k, when (v) {
        null -> JSONObject.NULL
        is Double -> if (v.isFinite()) v else JSONObject.NULL
        is Float -> if (v.isFinite()) v.toDouble() else JSONObject.NULL
        else -> v
    })

    fun array(j: JSONObject, k: String): List<JSONObject> {
        val a = j.optJSONArray(k) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
    fun <T> JSONObject.putList(k: String, items: List<T>, f: (T) -> JSONObject): JSONObject = put(k, JSONArray().apply { items.forEach { put(f(it)) } })
}

/** Ayrıştırılmış JSON değerlerinin eşitliği (sayılar değerce; nesneler anahtar kümesi ve değerleriyle). Android org.json'da `similar` yok. */
object JsonEq {
    fun same(a: Any?, b: Any?): Boolean = when {
        (a == null || a == JSONObject.NULL) && (b == null || b == JSONObject.NULL) -> true
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        a is JSONObject && b is JSONObject -> a.length() == b.length() && a.keys().asSequence().all { b.has(it) && same(a.opt(it), b.opt(it)) }
        a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { same(a.opt(it), b.opt(it)) }
        else -> a == b
    }
}

/** Kurye platformu (feed v2 `platform`). */
enum class CourierPlatform(val key: String, val pkg: String) {
    WOLT("wolt", "com.wolt.courierapp"),
    BOLT("bolt", "com.bolt.deliverycourier");

    companion object {
        fun of(key: String?): CourierPlatform? = entries.firstOrNull { it.key == key }
        fun ofPackage(pkg: String?): CourierPlatform? = entries.firstOrNull { it.pkg == pkg }
    }
}
