package com.kuryelog.app.map

import android.content.Context
import android.content.SharedPreferences
import com.kuryelog.logic.map.settings.MapPrefs
import org.json.JSONArray

/** `map` tercih dosyası (MAP.md 4.7, 14.1): `SharedPreferences("map")`. StringList JSON dizisi olarak saklanır. */
class MapPrefsStore(context: Context) : MapPrefs {
    private val p: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun getStringList(key: String): List<String>? {
        val s = p.getString(key, null) ?: return null
        return runCatching { JSONArray(s).let { a -> List(a.length()) { a.getString(it) } } }.getOrNull()
    }

    override fun setStringList(key: String, value: List<String>) = p.edit().putString(key, JSONArray(value).toString()).apply()
    override fun getBool(key: String): Boolean? = if (p.contains(key)) runCatching { p.getBoolean(key, false) }.getOrNull() else null
    override fun setBool(key: String, value: Boolean) = p.edit().putBoolean(key, value).apply()
    override fun getString(key: String): String? = runCatching { p.getString(key, null) }.getOrNull()
    override fun setString(key: String, value: String) = p.edit().putString(key, value).apply()

    /** Ondalık kayıpsız: `Double` bit deseni `Long` olarak. */
    override fun getDouble(key: String): Double? =
        if (p.contains(key)) runCatching { Double.fromBits(p.getLong(key, 0)) }.getOrNull() else null

    override fun setDouble(key: String, value: Double) = p.edit().putLong(key, value.toRawBits()).apply()
    override fun getLong(key: String): Long? = if (p.contains(key)) runCatching { p.getLong(key, 0) }.getOrNull() else null
    override fun setLong(key: String, value: Long) = p.edit().putLong(key, value).apply()
    override fun remove(key: String) = p.edit().remove(key).apply()

    companion object {
        const val FILE = "map"
    }
}
