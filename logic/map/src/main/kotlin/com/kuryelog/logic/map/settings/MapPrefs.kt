package com.kuryelog.logic.map.settings

/**
 * `map` tercih dosyası arayüzü (MAP.md 4.7, 14.1): anahtar → StringList / Bool / String. Android `:map` `MapPrefsStore`
 * `SharedPreferences("map")` ile uygular; testlerde bellek içi.
 */
interface MapPrefs {
    fun getStringList(key: String): List<String>?
    fun setStringList(key: String, value: List<String>)
    fun getBool(key: String): Boolean?
    fun setBool(key: String, value: Boolean)
    fun getString(key: String): String?
    fun setString(key: String, value: String)
    fun getDouble(key: String): Double?
    fun setDouble(key: String, value: Double)
    fun getLong(key: String): Long?
    fun setLong(key: String, value: Long)
    fun remove(key: String)
}
