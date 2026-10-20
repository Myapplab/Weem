package com.kuryelog.logic.map

import com.kuryelog.logic.map.settings.MapPrefs

/** Bellek içi `map` tercih dosyası (Dart `SharedPreferences.setMockInitialValues` karşılığı). */
class MemoryMapPrefs(initial: Map<String, Any> = emptyMap()) : MapPrefs {
    private val values = HashMap<String, Any>(initial)

    @Suppress("UNCHECKED_CAST")
    override fun getStringList(key: String): List<String>? = values[key] as? List<String>
    override fun setStringList(key: String, value: List<String>) { values[key] = value }
    override fun getBool(key: String): Boolean? = values[key] as? Boolean
    override fun setBool(key: String, value: Boolean) { values[key] = value }
    override fun getString(key: String): String? = values[key] as? String
    override fun setString(key: String, value: String) { values[key] = value }
    override fun getDouble(key: String): Double? = (values[key] as? Number)?.toDouble()
    override fun setDouble(key: String, value: Double) { values[key] = value }
    override fun getLong(key: String): Long? = (values[key] as? Number)?.toLong()
    override fun setLong(key: String, value: Long) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}
