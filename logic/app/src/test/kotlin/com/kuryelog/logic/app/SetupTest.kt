package com.kuryelog.logic.app

import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** G5 (A7.2): ilk açılış adımları, izinler kartı (RS `MainActivity.kt:1188-1210` sayacı ve sırası) ve pil rehberi. */
class SetupTest {
    private val none = PermissionState(accessibility = false, notificationListener = false, location = false, backgroundLocation = false)
    private val all = PermissionState(accessibility = true, notificationListener = true, location = true, backgroundLocation = true, batteryUnrestricted = true)

    @Test fun stepsRunInOrderAndStopAtTheEnds() {
        assertEquals(listOf(SetupStep.LANGUAGE, SetupStep.PERMISSIONS, SetupStep.MAP, SetupStep.BATTERY), SetupStep.entries)
        assertEquals(SetupStep.PERMISSIONS, SetupStep.LANGUAGE.next())
        assertEquals(SetupStep.BATTERY, SetupStep.BATTERY.next())
        assertEquals(SetupStep.LANGUAGE, SetupStep.LANGUAGE.prev())
        assertTrue(SetupStep.BATTERY.isLast)
        assertFalse(SetupStep.MAP.isLast)
        assertEquals(SetupStep.LANGUAGE, SetupStep.fromOrdinal(9))
        assertEquals(SetupStep.MAP, SetupStep.fromOrdinal(2))
        assertEquals(com.kuryelog.logic.core.KuryelogPrefs.SETUP_DONE, SetupStep.PREF_KEY)
    }

    @Test fun rowsFollowRawstatsOrderAndBluetoothOnlyWhenAScooterIsChosen() {
        assertEquals(listOf(PermKind.ACCESSIBILITY, PermKind.NOTIFICATION_LISTENER, PermKind.LOCATION, PermKind.BACKGROUND_LOCATION),
            Permissions.rows(none).map { it.kind })
        val bt = none.copy(bluetoothNeeded = true, bluetooth = false)
        assertEquals(PermKind.BLUETOOTH, Permissions.rows(bt).last().kind)
        assertEquals("! 0/5", Permissions.badge(bt))
    }

    @Test fun badgeCountsGrantedAndBatteryIsNotCounted() {
        assertEquals("! 0/4", Permissions.badge(none))
        assertEquals("! 2/4", Permissions.badge(none.copy(accessibility = true, location = true)))
        assertEquals("✓", Permissions.badge(all.copy(batteryUnrestricted = false)))
        assertTrue(Permissions.allOk(all.copy(batteryUnrestricted = false)))
        // "her zaman" konumu da sayılır (Rawstats: isteğe bağlı yazar ama rozet onu sayar).
        assertEquals("! 3/4", Permissions.badge(all.copy(backgroundLocation = false)))
    }

    @Test fun cardOpensWhenSomethingIsMissingUnlessTheUserChose() {
        assertTrue(Permissions.startsOpen(none, null))
        assertFalse(Permissions.startsOpen(all, null))
        assertFalse(Permissions.startsOpen(none, false))
        assertTrue(Permissions.startsOpen(all, true))
    }

    @Test fun backgroundNoteOnlyWithAHomeAndNoAlwaysLocation() {
        assertTrue(Permissions.backgroundNote(none.copy(homeSet = true)))
        assertFalse(Permissions.backgroundNote(none))
        assertFalse(Permissions.backgroundNote(all.copy(homeSet = true)))
    }

    @Test fun batteryRowIsInformationOnly() {
        assertEquals(SetupText.BATTERY_FREE, Permissions.batteryText(all))
        assertNull(Permissions.batteryBadge(all))
        assertEquals(SetupText.BATTERY_RESTRICTED, Permissions.batteryText(none))
        assertEquals("isteğe bağlı", Permissions.batteryBadge(none)!!.get(Lang.TR))
    }

    @Test fun vendorFromManufacturerOrBrand() {
        assertEquals(Vendor.XIAOMI, Vendor.of("Xiaomi", "Redmi"))
        assertEquals(Vendor.XIAOMI, Vendor.of("Xiaomi", "POCO"))
        assertEquals(Vendor.XIAOMI, Vendor.of("unknown", "poco"))
        assertEquals(Vendor.PIXEL, Vendor.of("Google", "google"))
        assertEquals(Vendor.OTHER, Vendor.of("samsung", "samsung"))
        assertEquals(Vendor.OTHER, Vendor.of(null, null))
    }

    @Test fun eachVendorHasStepsAndAnAppInfoButton() {
        for (v in Vendor.entries) {
            assertTrue(v.name, BatteryGuide.steps(v).isNotEmpty())
            assertTrue(v.name, BatteryAction.APP_INFO in BatteryGuide.actions(v))
        }
        assertEquals(BatteryAction.AUTOSTART, BatteryGuide.actions(Vendor.XIAOMI).first())
        assertFalse(BatteryAction.AUTOSTART in BatteryGuide.actions(Vendor.PIXEL))
        assertTrue(BatteryGuide.steps(Vendor.XIAOMI).first().get(Lang.TR).contains("Otomatik başlatma"))
        assertTrue(BatteryGuide.steps(Vendor.PIXEL).first().get(Lang.AZ).contains("Məhdudiyyətsiz"))
    }

    @Test fun textsSayWeemNotTheOldAppNames() {
        for (t in SetupText.entries) for (l in Lang.entries) {
            val s = t.get(l)
            assertFalse("${t.name}/$l", s.contains("Rawstats") || s.contains("YolGo") || s.contains("KuryePlus"))
        }
    }
}
