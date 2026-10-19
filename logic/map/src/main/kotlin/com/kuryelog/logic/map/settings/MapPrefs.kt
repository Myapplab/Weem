package com.kuryelog.logic.map.settings

/**
 * `map` tercih dosyası arayüzü (`MAP.md` 14.1): Android `:map` `MapPrefsStore` = `SharedPreferences("map")`; testlerde bellek içi sahte.
 * YolGo adları `flutter.` öneki olmadan aynı ad ve biçimle.
 */
interface MapPrefs {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getStringList(key: String): List<String>?
    fun putStringList(key: String, value: List<String>)
    fun getBool(key: String): Boolean?
    fun putBool(key: String, value: Boolean)
}
