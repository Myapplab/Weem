package com.kuryelog.app.scooter

import org.json.JSONException
import org.json.JSONObject

/** NB `LogStore.json`: anahtar/değer çiftlerinden olay yükü; null değer alanı `JSONObject.NULL` olarak yazar (kayıt sözleşmesi, SCOOTER.md 11.2). */
internal fun json(vararg kv: Any?): JSONObject {
    val j = JSONObject()
    try {
        var i = 0
        while (i < kv.size) {
            j.put(kv[i] as String, kv[i + 1] ?: JSONObject.NULL)
            i += 2
        }
    } catch (e: JSONException) {
        throw IllegalArgumentException(e)
    }
    return j
}
