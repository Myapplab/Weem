package com.kuryelog.logic.economics

import org.json.JSONArray
import org.json.JSONObject

// Rawstats RecordJson.kt:6-10 @ 6388c04: JSON yardımcıları (`:orders`'takiler `internal`; burada aynısı).
internal fun JSONObject.str(k: String): String? = optString(k).takeIf { it.isNotBlank() && it != "null" }
internal fun JSONObject.num(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k).takeIf { it.isFinite() } else null
internal fun JSONObject.longOrNull(k: String): Long? = num(k)?.toLong()
internal fun JSONArray.strings() = (0 until length()).map { getString(it) }
internal fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
