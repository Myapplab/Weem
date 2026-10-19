package com.kuryelog.app.map

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * YolGo `map_hud_gesture_test.dart` (2; 1. tanım 320/390 × klavye: 4 çalışma; A6.2b, MAP.md 6.3, 6.14): ortadaki boş alanda yatay/dikey sürükleme ve sağa hizalı düğmenin
 * solundaki saydam alan haritaya gider; denetime dokunuş haritaya gitmez; klavye açıkken alt panel yok; iki parmak ölçek >1,2; uzun üst panel kaydırılır ve harita girdi almaz;
 * görev engeli varken harita girdi almaz. Harita yerine sayaçlı `pointerInput` kutusu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-port-xxhdpi")
class MapHudLayoutTest {
    @get:Rule val compose = createComposeRule()

    private var mapUpdates = 0
    private var controls = 0
    private var scale = 1f
    private var largestScale = 1f

    private fun px(dp: Float) = dp * compose.density.density

    @Test
    fun bosHudAlanlariKaydirmaVeIkiParmakOlcegiHaritayaGider() {
        var width by mutableStateOf(320)
        var keyboard by mutableStateOf(false)
        compose.setContent {
            Box(Modifier.size(width.dp, 720.dp)) {
                MapHudLayout(
                    keyboard = keyboard,
                    map = {
                        Box(
                            Modifier.fillMaxSize().background(Color.Gray).pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    mapUpdates++
                                    scale *= zoom
                                    if (scale > largestScale) largestScale = scale
                                }
                            },
                        )
                    },
                    header = { Box(Modifier.testTag("header-control").fillMaxWidth().height(90.dp).clickable { controls++ }) },
                    footer = {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                            Box(Modifier.testTag("pin-control").size(64.dp, 120.dp).clickable { controls++ })
                        }
                    },
                )
            }
        }
        for (w in listOf(320, 390)) for (kb in listOf(false, true)) {
            width = w; keyboard = kb
            mapUpdates = 0; controls = 0; scale = 1f; largestScale = 1f
            compose.waitForIdle()
            val h = px(720f)
            val x = px(w / 2f)
            // Ortadaki boşlukta iki yön de haritaya ulaşmalı.
            compose.onRoot().performTouchInput { swipe(Offset(x, px(330f)), Offset(x + px(70f), px(330f)), 200) }
            compose.onRoot().performTouchInput { swipe(Offset(x, px(330f)), Offset(x, px(330f) + px(75f)), 200) }
            assertTrue("w=$w kb=$kb", mapUpdates > 0)
            if (!kb) {
                val before = mapUpdates
                // Sağa hizalı iğnenin solundaki saydam alan panel değildir.
                compose.onRoot().performTouchInput { swipe(Offset(px(32f), h - px(55f)), Offset(px(32f), h - px(120f)), 200) }
                assertTrue(mapUpdates > before)
                compose.onNodeWithTag("pin-control").performTouchInput { click() }
            } else {
                compose.onAllNodes(hasTestTag("pin-control")).assertCountEquals(0)
            }
            val beforeControl = mapUpdates
            compose.onNodeWithTag("header-control").performTouchInput { click() }
            assertEquals(if (kb) 1 else 2, controls)
            assertEquals(beforeControl, mapUpdates)
            compose.onRoot().performTouchInput { pinch(Offset(x - px(30f), px(360f)), Offset(x - px(65f), px(360f)), Offset(x + px(30f), px(360f)), Offset(x + px(65f), px(360f)), 200) }
            assertTrue("ölçek $largestScale", largestScale > 1.2f)
        }
    }

    @Test
    fun acikHudKaydirilirVeAktifGorevEngeliGirdiyiSahiplenir() {
        var barrier by mutableStateOf(false)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                MapHudLayout(
                    keyboard = false,
                    map = { Box(Modifier.fillMaxSize().background(Color.Gray).pointerInput(Unit) { detectTransformGestures { _, _, _, _ -> mapUpdates++ } }) },
                    header = { Box(Modifier.fillMaxWidth().height(900.dp).background(Color.White)) },
                    footer = {},
                    alerts = {
                        if (barrier) {
                            // ModalBarrier karşılığı: bütün dokunuşları tüketen tam ekran katman
                            Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
                        }
                    },
                )
            }
        }
        // Üretim başlığının kaydırıcısı kullanılır, ayrı test yerleşimi değil.
        compose.onRoot().performTouchInput { swipe(Offset(px(100f), px(150f)), Offset(px(100f), px(30f)), 200) }
        compose.waitForIdle()
        val scroll = compose.onNodeWithTag("hud-header-scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(scroll.value() > 0f)
        assertEquals(0, mapUpdates)
        barrier = true
        compose.waitForIdle()
        compose.onRoot().performTouchInput { swipe(Offset(px(100f), px(500f)), Offset(px(140f), px(500f)), 200) }
        assertEquals(0, mapUpdates)
    }
}
