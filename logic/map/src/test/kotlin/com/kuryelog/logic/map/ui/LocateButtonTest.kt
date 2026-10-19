package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.ui.LocateButtonRules.cameraModeOf
import com.kuryelog.logic.map.ui.LocateButtonRules.locateActionFor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * YolGo `locate_button_test.dart` saf 2 test (3. test `:app` Robolectric `SmartLocateButtonTest`; A6.2b, MAP.md 6.14): pusula ve "konuma dön" tek akıllı düğme.
 */
class LocateButtonTest {
    @Test
    fun `kamera kipi takip ve yön tercihinden çıkar`() {
        assertEquals(CameraMode.FREE, cameraModeOf(follow = false, headingUp = true))
        assertEquals(CameraMode.FREE, cameraModeOf(follow = false, headingUp = false))
        assertEquals(CameraMode.FOLLOW_NORTH, cameraModeOf(follow = true, headingUp = false))
        assertEquals(CameraMode.FOLLOW_HEADING, cameraModeOf(follow = true, headingUp = true))
    }

    @Test
    fun `düğme serbestken konuma döner, takipte yön kuzey arasında geçer`() {
        assertEquals(LocateAction.RECENTER, locateActionFor(CameraMode.FREE))
        assertEquals(LocateAction.HEADING_UP, locateActionFor(CameraMode.FOLLOW_NORTH))
        assertEquals(LocateAction.NORTH_UP, locateActionFor(CameraMode.FOLLOW_HEADING))

        // Haritayı elle kaydır → bas (konuma dön, yön tercihi korunur) → bas → bas.
        var follow = false
        var headingUp = true
        fun press() {
            when (locateActionFor(cameraModeOf(follow, headingUp))) {
                LocateAction.RECENTER -> follow = true
                LocateAction.HEADING_UP -> { headingUp = true; follow = true }
                LocateAction.NORTH_UP -> headingUp = false
            }
        }
        press()
        assertEquals(CameraMode.FOLLOW_HEADING, cameraModeOf(follow, headingUp))
        press()
        assertEquals(CameraMode.FOLLOW_NORTH, cameraModeOf(follow, headingUp))
        press()
        assertEquals(CameraMode.FOLLOW_HEADING, cameraModeOf(follow, headingUp))
    }
}
