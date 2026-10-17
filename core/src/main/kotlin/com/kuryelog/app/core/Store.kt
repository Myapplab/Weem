package com.kuryelog.app.core

import android.content.Context
import android.content.SharedPreferences
import com.kuryelog.logic.core.CoreText
import com.kuryelog.logic.core.FailureSink
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.LogStore
import com.kuryelog.logic.core.ManualSessionPolicy
import com.kuryelog.logic.core.attempt
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Bileşenlerin paylaştığı kayıt yeri (CORE.md 5; RS `Store.kt`): `files/logs/YYYY-MM-DD/` ve `kuryelog` tercihleri. Karar veren kod
 * `logic/core`'da ([LogStore], [KuryelogPrefs]); bu nesne yalnız Android bağlantısı.
 */
object Store {
    /** İçe aktarma sürüyor: vardiya başlamaz, ekran kaydı durur (CORE.md 1.1, 5.3). Tercih değil, süreç içi. */
    @Volatile var importing = false
    @Volatile private var log: LogStore? = null

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(KuryelogPrefs.FILE, Context.MODE_PRIVATE)
    fun values(ctx: Context): KuryelogPrefs.Values = PrefValues(prefs(ctx))
    fun lang(ctx: Context): Lang = Lang.resolve(prefs(ctx).getString(Lang.PREF_KEY, ""), Locale.getDefault().language)

    fun log(ctx: Context): LogStore = log ?: synchronized(this) {
        log ?: ctx.applicationContext.let { app ->
            LogStore(File(app.filesDir, "logs"), writeErrorPrefix = { CoreText.WRITE_FAILED.get(lang(app)) })
        }.also { log = it }
    }
    fun logsRoot(ctx: Context): File = log(ctx).logsRoot.apply { mkdirs() }

    fun append(ctx: Context, name: String, line: String, day: String? = null): Boolean = log(ctx).append(name, line, day)
    fun event(ctx: Context, type: String, extra: JSONObject? = null): Boolean = log(ctx).event(type, extra)
    fun failure(ctx: Context, source: String, e: Throwable) = log(ctx).failure(source, e)
    fun sink(ctx: Context): FailureSink = log(ctx)
    fun lastError(ctx: Context): String? = log(ctx).lastError

    /** Korumalı giriş (CORE.md 4.2): hata `errors.jsonl`'e yazılır, fırlatılmaz; `VirtualMachineError` hariç. */
    inline fun <T> guarded(ctx: Context, source: String, block: () -> T): T? = attempt(source, sink(ctx), block).getOrNull()

    fun shiftOn(ctx: Context): Boolean = KuryelogPrefs.shiftOn(values(ctx))
    /** Ekran ve bildirim kaydı izni (CAPTURE.md 0; RS `Store.canRecordScreen`): elle vardiya açık ve içe aktarma yok. */
    fun canRecordScreen(ctx: Context): Boolean = prefs(ctx).let {
        ManualSessionPolicy.canCapture(it.getBoolean(KuryelogPrefs.SHIFT, false), it.getBoolean(KuryelogPrefs.MANUAL_SESSION, false), importing)
    }
    /** İzlenen uygulamalar (tercih `targets`, CAPTURE.md 1.9). */
    fun targets(ctx: Context): Set<String> = prefs(ctx).getStringSet(TARGETS, null) ?: emptySet()
    /** Telefon pili 0–100, okunamazsa null (`screens.jsonl` `phone_battery_pct`). */
    fun phoneBattery(ctx: Context): Int? = ctx.getSystemService(android.os.BatteryManager::class.java)
        ?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
    const val TARGETS = "targets"
    @Synchronized fun ensureManualMode(ctx: Context): Boolean = KuryelogPrefs.ensureManualMode(values(ctx))

    private class PrefValues(private val p: SharedPreferences) : KuryelogPrefs.Values {
        override fun getBoolean(key: String, default: Boolean) = p.getBoolean(key, default)
        override fun getInt(key: String, default: Int) = p.getInt(key, default)
        override fun getLong(key: String, default: Long) = p.getLong(key, default)
        override fun getFloat(key: String, default: Float) = p.getFloat(key, default)
        override fun getString(key: String, default: String?) = p.getString(key, default)
        override fun put(values: Map<String, Any?>) {
            val e = p.edit()
            for ((k, v) in values) when (v) {
                null -> e.remove(k)
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is String -> e.putString(k, v)
                else -> throw IllegalArgumentException("$k: ${v.javaClass.simpleName}")
            }
            e.apply()
        }
    }
}
