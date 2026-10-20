package com.kuryelog.app.map

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.core.NotifyPolicy
import com.kuryelog.logic.map.reminders.ActiveReminder
import com.kuryelog.logic.map.reminders.ReminderController
import com.kuryelog.logic.map.reminders.ReminderEvents
import com.kuryelog.logic.map.reminders.ReminderItem
import com.kuryelog.logic.map.reminders.ReminderKind
import com.kuryelog.logic.map.reminders.ReminderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Hatırlatıcıların Android çalışma zamanı (Y20; MAP.md 13.16, 13.19): [ReminderController] + gün sinyali (`DayRuntime`), gün açıkken 20 sn'de bir denetim, 30 sn sonra
 * gizleme, bildirim `7100 + tür` (kanal `hatirlatici`; WEEM öndeyken ve önizleme değilken yalnız kart), güçlü titreşim, sürüş günlüğü olayı `reminder`.
 */
class ReminderRuntime private constructor(private val app: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val core = app.getSharedPreferences("kuryelog", Context.MODE_PRIVATE)
    private val lang: () -> Lang = { Lang.resolve(core.getString(Lang.PREF_KEY, null), Locale.getDefault().language) }
    private val controller = ReminderController(MapPrefsStore(app), Clock.Jvm)

    private val activeFlow = MutableStateFlow<ActiveReminder?>(null)

    /** Ekrandaki kart; null = yok (uygulama kabuğu çizer). */
    val active: StateFlow<ActiveReminder?> get() = activeFlow

    private val settingsFlow = MutableStateFlow(controller.settings)
    val settings: StateFlow<ReminderSettings> get() = settingsFlow

    /** Gün yeni açıldı ve açık hatırlatıcı var: ekran bildirim iznini ister (Android 13+). */
    private val permissionFlow = MutableStateFlow(false)
    val wantsPermission: StateFlow<Boolean> get() = permissionFlow

    init {
        controller.events = object : ReminderEvents {
            override fun show(item: ReminderItem, dayId: String?, preview: Boolean) {
                buzz()
                if (!preview && dayId == null) return
                if (NotifyPolicy.inAppOnly(AppForeground.on, preview)) return
                post(item)
            }

            override fun cancel(kind: ReminderKind) {
                runCatching { app.getSystemService(NotificationManager::class.java)?.cancel(notificationId(kind)) }
            }

            override fun logEvent(name: String, data: Map<String, Any?>) = RideLogRuntime.get(app).writer.event(name, data)
        }
        val day = DayRuntime.get(app)
        day.view.value.state.let { s -> controller.activeDay(if (s.active) s.startedAtMs?.let { "yolgo-$it" } else null, s.startedAtMs) }
        var wasActive = day.view.value.state.active
        day.addStepListener { step ->
            val s = step.view.state
            val opened = s.active && !wasActive
            wasActive = s.active
            synchronized(lock) { controller.activeDay(if (s.active) s.startedAtMs?.let { "yolgo-$it" } else null, s.startedAtMs) }
            if (opened) permissionFlow.value = controller.wantsPermission
            publish()
        }
        scope.launch {
            var n = 0
            while (isActive) {
                delay(1_000)
                synchronized(lock) {
                    controller.hideIfExpired()
                    if (++n % CHECK_EVERY_S == 0) controller.check()
                }
                publish()
            }
        }
    }

    private fun publish() {
        synchronized(lock) {
            activeFlow.value = controller.active
            settingsFlow.value = controller.settings
        }
    }

    /** Ayarlar ekranı: bir hatırlatıcıyı değiştirir (sayaç kuralları denetleyicide). */
    fun updateItem(kind: ReminderKind, change: (ReminderItem) -> ReminderItem) {
        synchronized(lock) { controller.updateItem(kind, change) }
        publish()
    }

    /** ✓ ("Tamam"). */
    fun done() {
        synchronized(lock) { controller.done() }
        publish()
    }

    /** "Dene": zamanlamayı bozmadan hemen göster. */
    fun preview(kind: ReminderKind) {
        synchronized(lock) { controller.preview(kind) }
        publish()
    }

    fun permissionAsked() {
        permissionFlow.value = false
    }

    private fun notificationId(kind: ReminderKind) = NotificationIds.reminder(NotificationIds.ReminderKind.valueOf(kind.name))

    private fun post(item: ReminderItem) {
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        val l = lang()
        runCatching {
            nm.createNotificationChannel(Reminders.channel(l == Lang.AZ))
            val id = notificationId(item.kind)
            val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: Intent().setPackage(app.packageName)
            val tap = PendingIntent.getActivity(app, id, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            nm.notify(
                id,
                Notification.Builder(app, NotificationIds.CHANNEL_REMINDER).setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("${item.emoji} ${item.title(l)}").setContentText(item.message(l)).setCategory(Notification.CATEGORY_REMINDER)
                    .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true).setTimeoutAfter(NotifyPolicy.TIMEOUT_MS).build(),
            )
        }
    }

    private fun buzz() {
        runCatching {
            val v = if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 200, 350), -1))
        }
    }

    companion object {
        private const val CHECK_EVERY_S = 20

        @Volatile private var instance: ReminderRuntime? = null

        fun get(ctx: Context): ReminderRuntime = instance ?: synchronized(this) { instance ?: ReminderRuntime(ctx.applicationContext).also { instance = it } }
    }
}
