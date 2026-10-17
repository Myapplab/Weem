package com.kuryelog.logic.capture

import org.junit.Assert.*
import org.junit.Test

/** CAPTURE.md 1.9, 8: no Rawstats test; characterisation of `MainActivity.autoDetectTargets/pickApps` @ 6388c04. */
class TargetAppsTest {
    private val apps = listOf("com.whatsapp" to "WhatsApp", "com.wolt.courierapp" to "Wolt Courier", "com.wolt.android" to "Wolt",
        "ee.mtakso.driver" to "Bolt Driver", "ee.mtakso.client" to "Bolt", "com.bolt.food.courier" to "Bolt Food Courier",
        "az.kuryer.app" to "Kuryer", "com.wolt.partner" to "Wolt Partner")

    @Test fun autoDetectionTakesCourierAppsOnly() {
        assertEquals(setOf("com.wolt.courierapp", "ee.mtakso.driver", "com.bolt.food.courier", "com.wolt.partner"),
            apps.filter { TargetApps.autoDetected(it.first, it.second) }.map { it.first }.toSet())
        assertTrue(TargetApps.autoDetected("com.example", "Wolt Kuryer"))   // the label counts too
    }

    @Test fun autoDetectionOnlyAddsAndNeverOverridesAHandPick() {
        assertEquals(setOf("x", "com.wolt.courierapp", "ee.mtakso.driver", "com.bolt.food.courier", "com.wolt.partner"),
            TargetApps.autoAdded(setOf("x"), apps, userPicked = false))
        assertEquals(setOf("x"), TargetApps.autoAdded(setOf("x"), apps, userPicked = true))
    }

    @Test fun pickListPutsCourierLikeAppsFirst() {
        assertEquals(listOf("Bolt", "Bolt Driver", "Bolt Food Courier", "Kuryer", "Wolt", "Wolt Courier", "Wolt Partner", "WhatsApp"),
            TargetApps.pickOrder(apps).map { it.second })
    }
}
