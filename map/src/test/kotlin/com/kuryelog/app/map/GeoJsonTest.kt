package com.kuryelog.app.map

import com.kuryelog.logic.map.geo.GeoPoint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S38: hızlı GeoJSON yazıcı (String.format'sız) geçerli JSON üretir; özellikler ve kaçışlar doğru. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeoJsonTest {
    @Test
    fun yaziciGecerliGeoJsonUretir() {
        val b = GeoJson.Builder()
        b.line(listOf(GeoPoint(40.4, 49.85), GeoPoint(40.41, 49.86))) { prop("color", "#1A73E8"); prop("width", 6.0); prop("pattern", "solid") }
        b.point(GeoPoint(40.4, 49.85)) { prop("icon", "stop-\"1\"\\x"); prop("rot", Double.NaN) }
        b.polygon(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(1.0, 0.0), GeoPoint(0.0, 0.0))) {}
        val j = JSONObject(b.build())
        val f = j.getJSONArray("features")
        assertEquals(3, f.length())
        val line = f.getJSONObject(0)
        assertEquals("LineString", line.getJSONObject("geometry").getString("type"))
        // GeoJSON sırası (boylam, enlem).
        assertEquals(49.86, line.getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(1).getDouble(0), 0.0)
        assertEquals(6.0, line.getJSONObject("properties").getDouble("width"), 0.0)
        val point = f.getJSONObject(1).getJSONObject("properties")
        assertEquals("stop-\"1\"\\x", point.getString("icon"))
        assertEquals(true, point.isNull("rot"))
        assertEquals(0, f.getJSONObject(2).getJSONObject("properties").length())
        assertEquals("""{"type":"FeatureCollection","features":[]}""", GeoJson.Builder().build())
    }
}
