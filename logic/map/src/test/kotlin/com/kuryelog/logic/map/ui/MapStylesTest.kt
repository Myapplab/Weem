package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.settings.MapSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Y2 karakterizasyonu (eski test yok; MAP.md 6.4, 6.6, 6.14 "Yeni", SORU-N5 (a)): altlık seçimi, anahtarsız `google` düşüşü, atıflar, POI yakınlık eşikleri. */
class MapStylesTest {
    @Test
    fun `altlık seçimi, eski değerler vektöre, anahtarsız google vektöre düşer`() {
        assertEquals("vector", MapSettings().mapStyle)
        assertEquals(BaseMap.VECTOR, MapStyles.resolve("vector", mapsEnabled = true))
        assertEquals(BaseMap.OSM, MapStyles.resolve("osm", mapsEnabled = false))
        assertEquals(BaseMap.GOOGLE, MapStyles.resolve("google", mapsEnabled = true))
        assertEquals(BaseMap.VECTOR, MapStyles.resolve("google", mapsEnabled = false))
        for (old in listOf("minimal", "esri", "sat", "", "x")) assertEquals(old, BaseMap.VECTOR, MapStyles.resolve(old, mapsEnabled = true))
        assertEquals(listOf(BaseMap.VECTOR, BaseMap.OSM), MapStyles.choices(mapsEnabled = false))
        assertTrue(BaseMap.GOOGLE in MapStyles.choices(mapsEnabled = true))
        assertEquals("Vektor · WEEM", MapStyles.label(BaseMap.VECTOR).get(Lang.AZ))
    }

    @Test
    fun `atıf gerçek kaynağı söyler, OSM raster koyuda ters çevrilir`() {
        assertEquals("© OpenStreetMap contributors", MapStyles.attribution(BaseMap.OSM)!!.text)
        assertEquals("https://www.openstreetmap.org/copyright", MapStyles.attribution(BaseMap.OSM)!!.url)
        assertTrue(MapStyles.attribution(BaseMap.VECTOR)!!.text.contains("OpenStreetMap"))
        assertNull(MapStyles.attribution(BaseMap.GOOGLE))
        val light = JSONObject(MapStyles.osmRasterStyle(dark = false))
        assertEquals("https://tile.openstreetmap.org/{z}/{x}/{y}.png", light.getJSONObject("sources").getJSONObject("osm").getJSONArray("tiles").getString(0))
        assertFalse(light.getJSONArray("layers").getJSONObject(0).has("paint"))
        val paint = JSONObject(MapStyles.osmRasterStyle(dark = true)).getJSONArray("layers").getJSONObject(0).getJSONObject("paint")
        assertEquals(1.0, paint.getDouble("raster-brightness-min"), 0.0)
        assertEquals(0.0, paint.getDouble("raster-brightness-max"), 0.0)
        assertTrue(MapStyles.vectorStyleUrl(true) != MapStyles.vectorStyleUrl(false))
        assertEquals("WEEM/0.6.0 (personal courier navigation)", MapStyles.userAgent("0.6.0"))
    }

    @Test
    fun `kamera sınırları ve POI yakınlık eşikleri`() {
        assertEquals(16.2, MapCamera.STANDARD_ZOOM, 0.0)
        assertEquals(40.4093, MapCamera.BAKU.lat, 0.0)
        assertEquals(11.0 to 19.0, MapCamera.MAPLIBRE_MIN_ZOOM to MapCamera.MAPLIBRE_MAX_ZOOM)
        assertEquals(11.0 to 20.0, MapCamera.GOOGLE_MIN_ZOOM to MapCamera.GOOGLE_MAX_ZOOM)
        assertEquals(listOf(16, 120, 16, 240), MapCamera.GOOGLE_PADDING_DP)
        // Yorumdaki 12/13/15/16 değil, koddaki 13/15/16,5/16,5 (MAP.md 6.16).
        assertFalse(PoiZoomRules.markerVisible('s', 12.9)); assertTrue(PoiZoomRules.markerVisible('s', 13.0))
        assertFalse(PoiZoomRules.markerVisible('m', 14.9)); assertTrue(PoiZoomRules.markerVisible('m', 15.0))
        for (k in listOf('l', 'r', 'f', 'c', 't')) { assertFalse(PoiZoomRules.markerVisible(k, 16.4)); assertTrue(PoiZoomRules.markerVisible(k, 16.5)) }
        assertFalse(PoiZoomRules.labelVisible('s', 15.9)); assertTrue(PoiZoomRules.labelVisible('s', 16.0))
        assertFalse(PoiZoomRules.labelVisible('m', 16.9)); assertTrue(PoiZoomRules.labelVisible('m', 17.0))
        assertFalse(PoiZoomRules.labelVisible('r', 17.5)); assertTrue(PoiZoomRules.labelVisible('r', 17.6))
        assertFalse(PoiZoomRules.labelVisible('l', 19.0))
        assertEquals(listOf('l', 's', 'm', 'r'), listOf('r', 'm', 'l', 's').sortedBy { PoiZoomRules.priority(it) })
        assertEquals(180, PoiZoomRules.MAX_MARKERS); assertEquals(55, PoiZoomRules.MAX_LABELS)
        assertEquals(17.0, GoogleMapStyle.HIDE_POIS_BELOW_ZOOM, 0.0)
    }
}
