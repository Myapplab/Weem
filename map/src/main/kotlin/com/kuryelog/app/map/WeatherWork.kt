package com.kuryelog.app.map

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.core.NotifyPolicy
import com.kuryelog.logic.map.ui.RideText
import com.kuryelog.logic.map.weather.WeatherSchedule
import com.kuryelog.logic.map.weather.WeatherText
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Hava bildirimi (MAP.md 10.8; YolGo `WeatherWorker`): vardiya açıkken ve hava açıkken 2 saatlik periyodik iş `duzyol_weather_v1`; her çalışmada günde bir kez (vardiya başına) önümüzdeki 4 saatin
 * özeti bildirimi 7301 (kanal `duzyol_weather`, 30 sn). Vardiya kapanınca ya da hava kapanınca iş iptal edilir ve bildirim kalkar.
 */
class WeatherWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val day = inputData.getString(KEY_DAY)
        if (!WeatherFetch.accepts(day)) return Result.success()
        val prefs = MapPrefsStore(app)
        if (!(prefs.getBool(WeatherSchedule.KEY_ENABLED) ?: true)) return Result.success()
        val now = System.currentTimeMillis()
        // Hava bildirimi günde bir kez (eskiden 2 saatte bir); saat geri giderse yeniden.
        if (!NotifyPolicy.weatherDue(prefs.getString(KEY_NOTIFIED_DAY), day, prefs.getLong(KEY_NOTIFIED) ?: 0L, now)) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = app.getSystemService(NotificationManager::class.java) ?: return Result.success()
        if (!manager.areNotificationsEnabled()) return Result.success()
        return try {
            val lang = Store.lang(app)
            val text = WeatherText.notification(WeatherFetch.fetch(app, day, force = false), lang, now)
            // İndirme sürerken kullanıcı kapattıysa ya da gün bittiyse bildirim gönderme.
            if (!(prefs.getBool(WeatherSchedule.KEY_ENABLED) ?: true) || !WeatherFetch.accepts(day)) return Result.success()
            manager.createNotificationChannel(NotificationChannel(WeatherSchedule.CHANNEL, RideText.WEATHER_NOTIF_CHANNEL.get(lang), NotificationManager.IMPORTANCE_DEFAULT))
            val open = app.packageManager.getLaunchIntentForPackage(app.packageName)
                ?.let { PendingIntent.getActivity(app, NotificationIds.WEATHER, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
            manager.notify(
                NotificationIds.WEATHER,
                Notification.Builder(app, WeatherSchedule.CHANNEL).setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setContentTitle(text.title).setContentText(text.content).setStyle(Notification.BigTextStyle().bigText(text.bigText))
                    .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setTimeoutAfter(NotifyPolicy.TIMEOUT_MS).build(),
            )
            prefs.setLong(KEY_NOTIFIED, now)
            day?.let { prefs.setString(KEY_NOTIFIED_DAY, it) }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (!WeatherFetch.accepts(day)) Result.success() else if (runAttemptCount < WeatherSchedule.WORK_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        const val KEY_DAY = "day"
        const val KEY_NOTIFIED = "weather.notified"
        const val KEY_NOTIFIED_DAY = "weather.notifiedDay"

        /** `WeatherScheduler.configure`: gün açık değil ya da hava kapalı → iş ve bildirim iptal; değilse 2 saatlik periyodik iş (ağ gerekli), `UPDATE`. */
        fun configure(ctx: Context, active: Boolean, enabled: Boolean) {
            val wm = runCatching { WorkManager.getInstance(ctx) }.getOrNull() ?: return
            val day = ShiftHub.state.value.takeIf { it.active }?.startedAtMs?.toString()
            if (!active || !enabled || day == null) {
                wm.cancelUniqueWork(WeatherSchedule.WORK_NAME)
                runCatching { ctx.getSystemService(NotificationManager::class.java)?.cancel(NotificationIds.WEATHER) }
                return
            }
            val work = PeriodicWorkRequest.Builder(WeatherWork::class.java, WeatherSchedule.WORK_HOURS, TimeUnit.HOURS)
                .setInputData(Data.Builder().putString(KEY_DAY, day).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            wm.enqueueUniquePeriodicWork(WeatherSchedule.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, work)
        }
    }
}
