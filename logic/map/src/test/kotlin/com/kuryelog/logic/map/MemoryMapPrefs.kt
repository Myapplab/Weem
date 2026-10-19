package com.kuryelog.logic.map

import com.kuryelog.logic.map.settings.MapPrefs

/** Bellek içi `map` tercih dosyası (`MAP.md` 16.1: sahte tercih uygulaması). */
class MemoryMapPrefs(initial: Map<String, Any> = emptyMap()) : MapPrefs {
    val values = LinkedHashMap<String, Any>(initial)

    override fun getString(key: String): String? = values[key] as? String
    override fun putString(key: String, value: String) { values[key] = value }

    @Suppress("UNCHECKED_CAST")
    override fun getStringList(key: String): List<String>? = values[key] as? List<String>
    override fun putStringList(key: String, value: List<String>) { values[key] = value.toList() }

    override fun getBool(key: String): Boolean? = values[key] as? Boolean
    override fun putBool(key: String, value: Boolean) { values[key] = value }
}
