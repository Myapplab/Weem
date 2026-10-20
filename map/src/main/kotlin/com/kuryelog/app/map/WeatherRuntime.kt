package com.kuryelog.app.map

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.core.NotifyPolicy
import com.kuryelog.logic.core.ShiftState
import com.kuryelog.logic.core.WeatherNow
import com.kuryelog.logic.core.WeatherPort
import com.kuryelog.logic.map.nav.NavFix
import com.kuryelog.logic.map.net.UserAgent
import com.kuryelog.logic.map.weather.LiveWeather
import com.kuryelog.logic.map.weather.WeatherNotifier
import com.kuryelog.logic.map.weather.WeatherFetcher
import com.kuryelog.logic.map.weather.WeatherNotifyText
import com.kuryelog.logic.map.weather.WeatherPrefs
import com.kuryelog.logic.map.weather.WeatherState
import com.kuryelog.logic.map.weather.WeatherSummary
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Hava çalışma zamanı (MAP.md 10.7–10.10): `LiveWeather` durumu ([state]), ayarlar ([enabled], [strip]), 10 dk yenileme (gün açık + harita önde), konum güncellemesi (en çok 2 dk'da bir,
 * doğruluk 0–65 m, yaş −5…+30 sn), günde bir bildirim (kanal `duzyol_weather`, kimlik [NotificationIds.WEATHER], 30 sn sonra kalkar) ve [WeatherPort].
 * Bildirim eski WorkManager'ın (`duzyol_weather_v1`, 2 saat) yerine süreç içi döngüyle: gün açıkken vardiya ön plan servisi süreci canlı tutar; açılışta hemen, sonra 2 saatte bir denenir.
 */
class WeatherRuntime private constructor(private val app: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = WeatherPrefs(MapPrefsStore(app))
    private val store = FileReportStore(app)
    private val core = app.getSharedPreferences("kuryelog", Context.MODE_PRIVATE)
    private fun lang(): Lang = Lang.resolve(core.getString(Lang.PREF_KEY, null), Locale.getDefault().language)

    private fun dayOf(s: ShiftState): String? = if (s.active) (s.startedAtMs ?: 0L).toString() else null
    private fun accepts(day: String): Boolean = dayOf(ShiftHub.state.value) == day

    private val fetcher = WeatherFetcher(AndroidHttp, prefs, store, UserAgent.of(BuildConfigVersion.name(app)), System::currentTimeMillis, ::accepts)
    private val live = LiveWeather(fetcher, System::currentTimeMillis)

    val state: StateFlow<WeatherState> get() = live.state

    private val enabledFlow = MutableStateFlow(prefs.enabled)
    private val stripFlow = MutableStateFlow(prefs.strip)
    val enabled: StateFlow<Boolean> get() = enabledFlow
    val strip: StateFlow<Boolean> get() = stripFlow

    /** Bildirim izni sorulmalı (gün açık, hava açık, hiç sorulmadı, API ≥ 33 ve izin yok): `:app` bir kez iste, sonra [permissionAsked]. */
    private val permissionFlow = MutableStateFlow(false)
    val needsPermission: StateFlow<Boolean> get() = permissionFlow

    @Volatile private var visible = false
    @Volatile private var lastLocationAt = 0L

    private val notifier = WeatherNotifier(fetcher, prefs, ::accepts, ::canNotify, ::postNotification)

    val port: WeatherPort = object : WeatherPort {
        override fun current(): WeatherNow? {
            val now = System.currentTimeMillis()
            return WeatherSummary.summary(store.read())?.takeIf { it.fresh(now) }
        }
    }

    init {
        scope.launch { live.refresh(cacheOnly = !ShiftHub.state.value.active) }
        scope.launch {
            ShiftHub.state.map { dayOf(it) }.distinctUntilChanged().collect { day ->
                live.scopeChanged(day != null)
                // Vardiya sonunda 7301'i kaldırmak `:core` vardiya servisinin işi (`NotificationIds.SHIFT_END_CANCEL`).
                if (day != null) {
                    updatePermissionFlag()
                    if (visible) live.refresh(dayId = day)
                }
            }
        }
        // Gün açık ve harita önde iken 10 dk'da bir yenileme.
        scope.launch {
            while (isActive) {
                delay(REFRESH_MS)
                val day = dayOf(ShiftHub.state.value)
                if (visible && day != null) live.refresh(dayId = day)
            }
        }
        // Günde bir bildirim: gün açılınca hemen, hata olursa 2 saatte bir (en çok 3 kez).
        scope.launch {
            ShiftHub.state.map { dayOf(it) }.distinctUntilChanged().collect { day ->
                if (day == null) return@collect
                var attempts = 0
                while (isActive && accepts(day)) {
                    val r = notifier.run(day, lang() == Lang.AZ, System.currentTimeMillis())
                    if (r == WeatherNotifier.Result.POSTED || r == WeatherNotifier.Result.SKIPPED && !NotifyPolicy.weatherDue(prefs.notifiedDay, day, prefs.notified, System.currentTimeMillis())) break
                    if (r == WeatherNotifier.Result.FAILED && ++attempts >= WeatherNotifier.MAX_ATTEMPTS) break
                    delay(WeatherNotifier.PERIOD_MS)
                }
            }
        }
    }

    fun setVisible(on: Boolean) {
        visible = on
        val day = dayOf(ShiftHub.state.value)
        if (on) scope.launch { live.refresh(cacheOnly = day == null, dayId = day) }
    }

    /** Konum: her taze fix'te en çok 2 dk'da bir `lat`/`lon` yazılır (`updateWeatherLocation`). */
    fun onFix(f: NavFix) {
        val now = System.currentTimeMillis()
        val age = now - f.atMs
        if (!f.accuracyM.isFinite() || f.accuracyM < 0 || f.accuracyM > 65 || age < -5_000 || age > 30_000) return
        if (now - lastLocationAt < LOCATION_EVERY_MS) return
        if (prefs.setLocation(f.lat, f.lon, now)) lastLocationAt = now
    }

    fun refresh(force: Boolean = false) {
        scope.launch { live.refresh(force = force, dayId = dayOf(ShiftHub.state.value)) }
    }

    /** Harita ayarları → "8 saatlik tahmini göster": ağdan zorla alır; [onDone] raporla ya da null (hata). */
    fun fetchNow(onDone: (com.kuryelog.logic.map.weather.WeatherReport?) -> Unit) {
        scope.launch {
            val report = runCatching { com.kuryelog.logic.map.weather.WeatherReport.parse(fetcher.fetch(true, null), System.currentTimeMillis()) }.getOrNull()
            kotlinx.coroutines.withContext(Dispatchers.Main) { onDone(report) }
        }
    }

    fun setEnabled(on: Boolean) {
        prefs.enabled = on
        enabledFlow.value = on
        updatePermissionFlag()
        if (!on) app.getSystemService(NotificationManager::class.java)?.cancel(NotificationIds.WEATHER)
    }

    fun setStrip(on: Boolean) {
        prefs.strip = on
        stripFlow.value = on
    }

    /** Anahtar değişince önbellek silinir. */
    fun setKey(key: String) {
        if (prefs.setKey(key)) store.clear()
    }

    fun permissionAsked() {
        prefs.permissionAsked = true
        permissionFlow.value = false
    }

    private fun updatePermissionFlag() {
        permissionFlow.value = Build.VERSION.SDK_INT >= 33 && prefs.enabled && ShiftHub.state.value.active && !prefs.permissionAsked &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return app.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
    }

    private fun postNotification(title: String, text: String, big: String) {
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        val az = lang() == Lang.AZ
        nm.createNotificationChannel(NotificationChannel(CHANNEL, WeatherNotifyText.channelName(az), NotificationManager.IMPORTANCE_DEFAULT))
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: Intent().setPackage(app.packageName)
        val open = PendingIntent.getActivity(app, NotificationIds.WEATHER, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            NotificationIds.WEATHER,
            Notification.Builder(app, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(big)).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
                .setTimeoutAfter(NotifyPolicy.TIMEOUT_MS).build(),
        )
    }

    companion object {
        const val CHANNEL = "duzyol_weather"
        private const val REFRESH_MS = 600_000L
        private const val LOCATION_EVERY_MS = 120_000L

        @Volatile private var instance: WeatherRuntime? = null

        fun get(ctx: Context): WeatherRuntime = instance ?: synchronized(this) { instance ?: WeatherRuntime(ctx.applicationContext).also { instance = it } }
    }
}
