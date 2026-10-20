package com.kuryelog.logic.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G4 uçtan uca (A7.2; CORE.md 3.1, 3.3): bütün modüllerin bildirim kodu tek tablonun kurallarına uyar. Kaynak taraması: kanal adları iki
 * dilli, YolGo kökenli bildirimler 30 sn'de kalkar ve uygulama öndeyken yalnız kart olur, Rawstats zaman aşımları aynı, ilk açılış
 * kurulumu ve izinler kartı bildirim atmaz (pil satırı yalnız bilgi).
 */
class NotificationAuditTest {
    private val sources = RepoSource.mainKotlin()
    private fun src(name: String) = sources.single { it.name == name }.readText()

    @Test fun everyChannelNameIsInBothLanguages() {
        val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        val local = Regex("[çğıöşüəÇĞİÖŞÜƏ]")
        val single = sources.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                if ("NotificationChannel(" !in line || line.trimStart().startsWith("*")) return@mapIndexedNotNull null
                val localized = literal.findAll(line).count { local.containsMatchIn(it.groupValues[1]) }
                if (localized == 1) "${f.name}:${i + 1}: ${line.trim()}" else null
            }
        }
        assertEquals(emptyList<String>(), single)
        // Adı düz metin olmayan kanallar iki dilli tablodan ya da dil seçimiyle gelir.
        val created = sources.filter { "NotificationChannel(" in it.readText() }.map { it.name }.toSet()
        assertTrue(created.toString(), created.containsAll(setOf("Notices.kt", "ScreenReadingWarning.kt", "WeatherRuntime.kt", "TaskRuntime.kt", "DayReportHook.kt")))
    }

    @Test fun yolgoNotificationsLeaveAfterThirtySecondsAndStayInAppWhileWeemIsOpen() {
        assertEquals(30_000L, NotifyPolicy.TIMEOUT_MS)
        for (name in listOf("ReminderRuntime.kt", "TaskRuntime.kt", "WeatherRuntime.kt")) {
            val s = src(name)
            assertTrue(name, s.contains("setTimeoutAfter(NotifyPolicy.TIMEOUT_MS)"))
        }
        for (name in listOf("ReminderRuntime.kt", "TaskRuntime.kt")) {
            val s = src(name)
            assertTrue(name, s.contains("NotifyPolicy.inAppOnly(AppForeground.on"))
            assertTrue(name, s.contains("setOnlyAlertOnce(true)") && s.contains("setAutoCancel(true)"))
        }
        // Hava özeti günde bir (NotifyPolicy.weatherDue → WeatherNotifier).
        assertTrue(sources.any { it.name == "WeatherNotifier.kt" && it.readText().contains("weatherDue(") })
    }

    @Test fun rawstatsTimeoutsAreKept() {
        assertTrue(src("CashTipPrompt.kt").contains("setTimeoutAfter(15_000L)"))
        assertTrue(src("PocketCashPrompt.kt").contains("setTimeoutAfter(10_000L)"))
        assertTrue(src("Notices.kt").contains("AUTO_START_TIMEOUT_MS = 15 * 60_000L"))
        assertTrue(src("ScooterShiftLink.kt").contains("RECEIVING_TIMEOUT_MS = 15_000L"))
        // 5103 (vardiya kesildi) zaman aşımsız: H4 bildirimi 0 ile atılır.
        assertTrue(src("Notices.kt").contains("if (timeoutMs > 0) b.setTimeoutAfter(timeoutMs)"))
    }

    @Test fun setupAndPermissionsCardPostNoNotification() {
        for (name in listOf("SetupScreen.kt", "PermissionsCard.kt", "Setup.kt")) {
            val s = src(name)
            for (call in listOf("Notification.Builder", "NotificationCompat", ".notify(", "NotificationChannel(")) assertTrue("$name: $call", call !in s)
        }
    }

    @Test fun shiftEndCancelsTheYolgoNotificationsAndTheDeliveryNote() {
        assertTrue(src("ShiftMachine.kt").contains("for (id in NotificationIds.SHIFT_END_CANCEL) safe { platform.cancel(id) }"))
    }
}
