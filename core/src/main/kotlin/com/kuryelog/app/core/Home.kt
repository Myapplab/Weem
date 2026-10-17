package com.kuryelog.app.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.kuryelog.logic.core.HomeRepository
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.core.ShiftMachine

/**
 * G2 ev konumu (CORE.md 2.1; RS `HomeLocation`, `HomePreferences`): yalnız `kuryelog` `home.*` tercihlerinde; kayıtlara, dışa aktarmaya,
 * mesajlara yazılmaz. Kural ve kayıt mantığı `logic/core` [HomeRepository]'de.
 */
object Home {
    @Volatile private var repository: HomeRepository? = null

    fun repository(ctx: Context): HomeRepository = repository ?: synchronized(this) {
        repository ?: HomeRepository(Preferences(Store.prefs(ctx.applicationContext))).also { repository = it }
    }
    fun point(ctx: Context): HomeRepository.Point? = repository(ctx).point()

    fun set(ctx: Context, lat: Double, lon: Double) {
        repository(ctx).set(lat, lon, System.currentTimeMillis())
        changed(ctx)
    }
    fun clear(ctx: Context) { repository(ctx).clear(); changed(ctx) }
    private fun changed(ctx: Context) {
        Notices.cancel(ctx, NotificationIds.HOME_AUTO_START)
        Store.guarded(ctx, "home") { HomeWatch.homeChanged(ctx) }
    }

    /** Evden çıkışın vardiya isteği: kayıtlı evin revizyonuyla (eski ev için kuyruğa girmiş istek reddedilir). */
    fun startIntent(ctx: Context, revision: Long = point(ctx)?.updatedAt ?: 0L): Intent =
        Intent(ctx, ShiftService::class.java).setAction(ShiftMachine.ACTION_HOME_START).putExtra(ShiftMachine.EXTRA_HOME_REVISION, revision)

    fun hasFine(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasBackground(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Eski kurulumların güncelleme tarihi bilinmiyor: asla uydurulmaz (RS `HomePreferences`). */
    private class Preferences(private val prefs: SharedPreferences) : HomeRepository.Storage {
        override fun read() = HomeRepository.Saved(prefs.getString(KuryelogPrefs.HOME_LAT, null), prefs.getString(KuryelogPrefs.HOME_LON, null),
            if (prefs.contains(KuryelogPrefs.HOME_UPDATED_AT)) prefs.getLong(KuryelogPrefs.HOME_UPDATED_AT, 0L) else null,
            prefs.getBoolean(KuryelogPrefs.HOME_START_BLOCKED, false), prefs.getBoolean(KuryelogPrefs.HOME_END_ARMED, false))
        override fun write(value: HomeRepository.Saved) {
            prefs.edit().apply {
                if (value.lat == null || value.lon == null) {
                    listOf(KuryelogPrefs.HOME_LAT, KuryelogPrefs.HOME_LON, KuryelogPrefs.HOME_UPDATED_AT, KuryelogPrefs.HOME_START_BLOCKED,
                        KuryelogPrefs.HOME_END_ARMED).forEach(::remove)
                } else {
                    putString(KuryelogPrefs.HOME_LAT, value.lat); putString(KuryelogPrefs.HOME_LON, value.lon)
                    value.updatedAt.let { if (it == null) remove(KuryelogPrefs.HOME_UPDATED_AT) else putLong(KuryelogPrefs.HOME_UPDATED_AT, it) }
                    putBoolean(KuryelogPrefs.HOME_START_BLOCKED, value.startBlocked); putBoolean(KuryelogPrefs.HOME_END_ARMED, value.endArmed)
                }
            }.apply()
        }
    }
}
