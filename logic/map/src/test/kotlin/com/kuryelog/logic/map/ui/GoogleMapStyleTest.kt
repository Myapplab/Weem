package com.kuryelog.logic.map.ui

import org.json.JSONArray
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** YolGo `v150_features_test.dart` "koyu Google harita" (1; A6.2b, MAP.md 6.4, 7.15): koyu stil geçerli JSON ve koyu zemin içerir; `dark-ui` açık harita demektir. */
class GoogleMapStyleTest {
    @Test
    fun `koyu stil geçerli JSON ve koyu zemin içerir, dark-ui açık harita`() {
        val style = GoogleMapStyle.style(dark = true, hidePois = true)!!
        assertTrue(JSONArray(style).length() > 0)
        assertTrue(style.contains("#1d2330"))
        assertNull(GoogleMapStyle.style(dark = false, hidePois = false))
        assertTrue(GoogleMapStyle.isDarkMap("dark", nightPalette = true))
        assertFalse(GoogleMapStyle.isDarkMap("dark-ui", nightPalette = true))
        assertFalse(GoogleMapStyle.isDarkMap("light", nightPalette = false))
    }
}
