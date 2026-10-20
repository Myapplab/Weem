package com.kuryelog.app.map

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.NavOrigin
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.core.NotifyPolicy
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.NavFix
import com.kuryelog.logic.map.nav.StopPurpose
import com.kuryelog.logic.map.tasks.DailyTask
import com.kuryelog.logic.map.tasks.TaskController
import com.kuryelog.logic.map.tasks.TaskEvents
import com.kuryelog.logic.map.ui.TaskText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/** Görev ekranı için anlık görünüm (MAP.md 13.13): görevler, konum uyarıları, "Rota Oluştur" sürüyor mu. */
data class TaskView(val tasks: List<DailyTask>, val alerts: List<DailyTask>, val opening: Boolean = false, val lastPoint: GeoPoint? = null)

/**
 * Görevlerin Android çalışma zamanı (Y20; MAP.md 13.13, 13.19): [TaskController] + konum akışı (`NavLocation`), gün sinyalleri (`DayRuntime`: gün kimliği, açık sipariş, teslim),
 * 30 sn'de bir süre denetimi, bildirim 7201 (WEEM öndeyken yalnız kart), sürüş günlüğü olayı. "Rota Oluştur" WEEM navigasyonunu açar (S9): görev hedefi siparişe bağlanmaz.
 */
class TaskRuntime private constructor(private val app: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val core = app.getSharedPreferences("kuryelog", Context.MODE_PRIVATE)
    private val lang: () -> Lang = { Lang.resolve(core.getString(Lang.PREF_KEY, null), Locale.getDefault().language) }

    @Volatile private var lastFix: NavFix? = null

    private val controller = TaskController(MapPrefsStore(app), Clock.Jvm) { task -> open(task) }
    private val viewFlow = MutableStateFlow(snapshot())
    val view: StateFlow<TaskView> get() = viewFlow

    init {
        controller.events = object : TaskEvents {
            override fun alert(tasks: List<DailyTask>, dayId: String?) {
                if (NotifyPolicy.inAppOnly(AppForeground.on, preview = false) || dayId == null) return
                post(tasks)
            }

            override fun cancelAlert() = cancelNotification()

            override fun logEvent(name: String) = RideLogRuntime.get(app).writer.event(name)
        }
        val day = DayRuntime.get(app)
        day.view.value.state.let { s -> controller.activeDay(if (s.active) s.startedAtMs?.let { "yolgo-$it" } else null); controller.activeOrder(s.current != null) }
        refresh()
        day.addStepListener { step ->
            val s = step.view.state
            synchronized(lock) {
                controller.activeDay(if (s.active) s.startedAtMs?.let { "yolgo-$it" } else null)
                controller.activeOrder(s.current != null)
                if (step.events.any { it.type == "delivered" }) lastFix?.let { controller.deliveryFinished(GeoPoint(it.lat, it.lon), it.accuracyM, it.atMs) }
            }
            refresh()
        }
        scope.launch {
            while (isActive) {
                delay(EXPIRE_MS)
                synchronized(lock) { controller.expire() }
                refresh()
            }
        }
    }

    private fun snapshot() = TaskView(controller.tasks, controller.alerts, controller.opening, lastFix?.let { GeoPoint(it.lat, it.lon) })

    private fun refresh() {
        viewFlow.value = synchronized(lock) { snapshot() }
    }

    /** Konum akışının her fix'i (`NavLocation`). */
    fun onFix(f: NavFix) {
        lastFix = f
        synchronized(lock) { controller.onFix(GeoPoint(f.lat, f.lon), f.accuracyM, f.atMs) }
        refresh()
    }

    fun add(task: DailyTask) {
        synchronized(lock) { controller.add(task) }
        refresh()
    }

    fun delete(id: String) {
        synchronized(lock) { controller.delete(id) }
        refresh()
    }

    /** "Tamam / Kapat": görev tamamlanmaz. */
    fun dismiss(id: String) {
        synchronized(lock) { controller.dismiss(id) }
        refresh()
    }

    /** "Rota Oluştur": hedef WEEM navigasyonunda açılırsa görev tamamlanır. */
    fun openRoute(task: DailyTask): Boolean {
        val ok = synchronized(lock) { controller.openRoute(task) }
        refresh()
        return ok
    }

    private fun open(task: DailyTask): Boolean {
        val nav = NavRuntime.get(app)
        nav.controller.goTo(task.point, "${task.title} · ${task.place}", null, NavOrigin.MAP, StopPurpose.TASK)
        nav.showMap()
        return true
    }

    private fun post(tasks: List<DailyTask>) {
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        val az = lang() == Lang.AZ
        runCatching {
            nm.createNotificationChannel(Reminders.channel(az))
            val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: Intent().setPackage(app.packageName)
            val tap = PendingIntent.getActivity(app, NotificationIds.TASK_NEARBY, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            nm.notify(
                NotificationIds.TASK_NEARBY,
                Notification.Builder(app, NotificationIds.CHANNEL_REMINDER).setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(TaskText.ALERT_TITLE.get(lang())).setContentText(tasks.joinToString("\n") { "${it.title} · ${it.place}" })
                    .setStyle(Notification.BigTextStyle().bigText(tasks.joinToString("\n") { "${it.title} · ${it.place}" }))
                    .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true).setTimeoutAfter(NotifyPolicy.TIMEOUT_MS).build(),
            )
        }
    }

    private fun cancelNotification() {
        runCatching { app.getSystemService(NotificationManager::class.java)?.cancel(NotificationIds.TASK_NEARBY) }
    }

    companion object {
        private const val EXPIRE_MS = 30_000L

        @Volatile private var instance: TaskRuntime? = null

        fun get(ctx: Context): TaskRuntime = instance ?: synchronized(this) { instance ?: TaskRuntime(ctx.applicationContext).also { instance = it } }
    }
}

/** Hatırlatıcı ve görev bildirimlerinin ortak kanalı `hatirlatici` ("Hatırlatıcılar", HIGH, titreşim 0-350-200-350). */
internal object Reminders {
    fun channel(az: Boolean) = NotificationChannel(NotificationIds.CHANNEL_REMINDER, if (az) "Xatırlatmalar" else "Hatırlatıcılar", NotificationManager.IMPORTANCE_HIGH).apply {
        description = if (az) "Su iç, yoxla, yemək ye" else "Su iç, kontrol et, yemek ye"
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 350, 200, 350)
    }
}
