package com.kuryelog.app.map

import android.Manifest
import android.app.Application
import android.location.LocationManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A10.1 regresyonu: harita işaretinin 1 sn GPS'i yalnız uygulama görünürken (STARTED) açık; arka planda (Wolt/Bolt önde) bırakılır,
 * öne dönünce yeniden istenir. Önceden `enabled = true` ile Activity durunca da sürüyordu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MapPuckLifecycleTest {
    @get:Rule val compose = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun gpsYalnizUygulamaGorunurkenAcik() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val lm = shadowOf(app.getSystemService(LocationManager::class.java))
        val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { rememberMapPuck(enabled = true, graph = null) }
        }
        compose.waitForIdle()
        assertEquals(1, lm.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).size)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        assertEquals(0, lm.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).size)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.waitForIdle()
        assertEquals(1, lm.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).size)
    }
}
