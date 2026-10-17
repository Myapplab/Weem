package com.kuryelog.app

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Rawstats `Store`'un saf kısmı (gölge mod fikstürü üreticisi için; Store.kt:49-51, 80-88). */
object Store {
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val tsFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    @Synchronized fun ts(t: Long = System.currentTimeMillis()): String = tsFmt.format(Date(t))
    @Synchronized fun day(t: Long = System.currentTimeMillis()): String = dayFmt.format(Date(t))
    @Synchronized fun parseTs(s: String): Long = try { tsFmt.parse(s)?.time ?: 0L } catch (e: Exception) { 0L }
    fun locJson(): JSONObject? = null
}
object I18n { fun tl(ctx: Any?, tr: String, az: String) = tr }
