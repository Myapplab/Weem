package com.kuryelog.logic.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A10.2 geçiş öncesi güvenlik kontrolü (S27; `VERI_SOZLESMESI` 3 ve 9): Android katmanındaki gizlilik kuralları kaynak taramasıyla
 * korunur (bu katmanın birim testi yok).
 */
class PrivacyAuditTest {
    private fun src(path: String) = RepoSource.text(path)

    @Test fun receiverPhoneReadsNoScreenAndNoNotification() {
        val watch = src("capture/src/main/kotlin/com/kuryelog/app/WatchService.kt")
        // Olay girişi, ekran okuma ve gözlem kabulü: üçü de rolü sorar.
        assertEquals(3, Regex("""!Store\.canRecordScreen\(this\) \|\| CaptureState\.receiverRole\(\)""").findAll(watch).count())
        assertTrue(watch.contains("syncEventScope(Store.canRecordScreen(this@WatchService) && !CaptureState.receiverRole())"))
        val notif = src("capture/src/main/kotlin/com/kuryelog/app/NotifService.kt")
        assertEquals(2, Regex("""Store\.canRecordScreen\(this\) && !CaptureState\.receiverRole\(\)""").findAll(notif).count())
        assertTrue(src("app/src/main/kotlin/com/kuryelog/app/TwoPhone.kt").contains("CaptureState.receiverRole = { LinkHub.role.value == LinkRole.RECEIVER }"))
        // Scooter kaydı Receiver'da sürer: rol kapısı Store.canRecordScreen'e girmez.
        assertFalse(src("core/src/main/kotlin/com/kuryelog/app/core/Store.kt").contains("receiverRole"))
    }

    @Test fun watchServiceHearsOnlyTheWatchedAppsAndDialers() {
        val watch = src("capture/src/main/kotlin/com/kuryelog/app/WatchService.kt")
        assertTrue(watch.contains("Dialer.eventScope(Store.targets(this), defaultDialer, packageName)"))
        assertFalse(watch.contains("packageNames = if (all) null"))
        assertFalse(watch, Regex("""packageNames\s*=\s*null""").containsMatchIn(watch))
    }

    @Test fun customerCardNotificationHidesItsTextOnTheLockScreen() {
        val r = src("orders/src/main/kotlin/com/kuryelog/app/orders/DeliveryReminder.kt")
        assertTrue(r.contains("lockscreenVisibility = Notification.VISIBILITY_PRIVATE"))
        assertTrue(r.contains(".setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public)"))
        val public = RepoSource.body(r, "val public = ", ".build()")
        assertFalse(public, public.contains("r.title") || public.contains("r.details") || public.contains("r.body"))
    }

    @Test fun noUnusedSensitivePermission() {
        val modules = listOf("app", "core", "capture", "orders", "economics", "scooter", "map", "link", "remote")
        val manifests = modules.joinToString("\n") { src("$it/src/main/AndroidManifest.xml") }
        // ACTIVITY_RECOGNITION (A10.4c, SORU-Y13-1): yalnız Y13 adım dedektörü (`:map` SurfaceSensors) için ve yalnız `:map` bildirir.
        assertEquals(listOf("map"), modules.filter { src("$it/src/main/AndroidManifest.xml").contains("android.permission.ACTIVITY_RECOGNITION") })
        assertTrue(src("map/src/main/kotlin/com/kuryelog/app/map/SurfaceSensors.kt").contains("TYPE_STEP_DETECTOR"))
        assertFalse(manifests.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertFalse(manifests.contains("android.permission.READ_CONTACTS"))
        assertFalse(manifests.contains("android.permission.READ_PHONE_STATE"))
    }
}
