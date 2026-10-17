package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test

/** NT `FloatingTest.java` (S7; 22 çağrı, 22 çalışma). Floating ball: value choice (max 4, speed optional) and the bar sizes on a Pixel 10 Pro (1280 × 2856 px). */
class FloatingTest : NinebotSuite() {
    @Test fun floatingPanelAssertions() {
        check(!PanelField.DEFAULT.contains(PanelField.SPEED) && PanelField.DEFAULT.size == 4, "speed is opt-in, four defaults")
        check(PanelField.parse(null) == PanelField.DEFAULT && PanelField.parse("nonsense") == PanelField.DEFAULT, "unreadable → defaults")
        check(PanelField.parse("speed,power,speed,temp,range,voltage") == listOf(PanelField.SPEED, PanelField.POWER, PanelField.TEMP, PanelField.RANGE), "distinct, ordered, at most 4")
        check(PanelField.join(PanelField.parse("trip_wh,avg_speed")) == "trip_wh,avg_speed", "round trip")
        check(PanelField.entries.size == 16, "every panel/scooter value plus trip values selectable")
        check(NinebotDashboard.clock(725.0) == "12:05" && NinebotDashboard.clock(3725.0) == "1:02:05", "ride time clock")
        val none: NinebotDashboard.Snapshot? = null; check(PanelField.POWER.value(none, null) == NinebotDashboard.DASH && PanelField.TRIP_WH.value(none, null) == NinebotDashboard.DASH, "unknown → dash")
        val ball = 290; val m = 40
        // Landscape Pixel 10 Pro: four values take 80 % of the 2856 px width.
        check(FloatGeometry.length(4, true, 2856, 1280, ball, m) == 2285, "4 values horizontal = 80 % of landscape width")
        check(FloatGeometry.length(2, true, 2856, 1280, ball, m) == 1142, "2 values = 40 %")
        // Portrait: four values take 60 % of the 2856 px height; a horizontal bar is capped by the narrow screen.
        check(FloatGeometry.length(4, false, 1280, 2856, ball, m) == 1714, "4 values vertical = 60 % of portrait height")
        check(FloatGeometry.length(4, true, 1280, 2856, ball, m) == 1200, "horizontal bar never leaves a portrait screen")
        check(FloatGeometry.length(1, false, 1280, 2856, ball, m) == 428, "one value = 15 %")
        check(FloatGeometry.direction(120f, 10f, 24f) == FloatGeometry.Direction.RIGHT && FloatGeometry.direction(-120f, 30f, 24f) == FloatGeometry.Direction.LEFT, "horizontal swipes")
        check(FloatGeometry.direction(5f, 150f, 24f) == FloatGeometry.Direction.DOWN && FloatGeometry.direction(20f, -90f, 24f) == FloatGeometry.Direction.UP, "vertical swipes")
        check(FloatGeometry.direction(10f, -12f, 24f) == FloatGeometry.Direction.NONE, "short travel is a tap")
        check(FloatGeometry.start(100, ball, 1000, true, 1280, m) == 100 && FloatGeometry.start(800, ball, 800, false, 1280, m) == 290, "opens toward the swipe")
        check(FloatGeometry.start(900, ball, 1000, true, 1280, m) == 240, "pulled back inside the screen")
        // Thicker bar than the ball, centred on it and kept on screen.
        check(FloatGeometry.thickness(263) == 329, "bar 1.25 × ball")
        check(FloatGeometry.cross(500, 263, 329, 2856, 40) == 467 && FloatGeometry.cross(10, 263, 329, 1280, 40) == 40, "centred on the ball, inside the screen")
        // User-set sizes: shares and thickness follow the settings, defaults match the fixed rules above.
        check(FloatGeometry.length(4, true, 2856, 1280, ball, m, 0.5) == 1428 && FloatGeometry.length(4, false, 1280, 2856, ball, m, 0.9) == 2570, "custom length shares")
        check(FloatGeometry.thickness(263, FloatGeometry.THICK_PCT) == FloatGeometry.thickness(263) && FloatGeometry.thickness(200, 150) == 300 && FloatGeometry.thickness(200, 999) == 400, "custom thickness, clamped")
        check(FloatGeometry.H_PCT == 80 && FloatGeometry.V_PCT == 60 && FloatGeometry.clamp(10, FloatGeometry.BALL_MIN, FloatGeometry.BALL_MAX) == FloatGeometry.BALL_MIN, "defaults and ranges")
        assertEquals("floating panel assertions passed", 22, count)
    }
}
