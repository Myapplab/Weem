package com.kuryelog.app.map

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kuryelog.logic.map.graph.MapDataPhase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android harita verisi katmanı (MAP.md 2.14): `map` tercih dosyası ve `MapData` (yer tutucu gömülü manifest → kurulum ekranı). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MapDataTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun mapTercihDosyasiDegerleriKayipsizSaklar() {
        val p = MapPrefsStore(app)
        p.setStringList("streets.names", listOf("1|A", "2|B|C"))
        p.setDouble("x", 0.1 + 0.2)
        p.setString("map.bundled.wide", "2026-10-01T00:00")
        val again = MapPrefsStore(app)
        assertEquals(listOf("1|A", "2|B|C"), again.getStringList("streets.names"))
        assertEquals(0.1 + 0.2, again.getDouble("x")!!, 0.0)
        assertEquals("2026-10-01T00:00", again.getString("map.bundled.wide"))
        assertNull(again.getBool("yok"))
        assertNull(again.getLong("yok"))
    }

    /**
     * PR CI'ında `assets/map` yalnız yer tutucu manifest → harita yok. APK işinde (`ci/build_map.sh`) gerçek gömülü harita var → kurulur
     * (S40: AGP `.gz` uzantısını siler; önceden bu durumda da MISSING kalıyordu ve test yanlışlıkla geçiyordu).
     */
    @Test
    fun gomuluManifesteGoreHaritaKurulurYaDaYokTekOrnek() = runBlocking {
        val c = MapData.controller(app)
        assertSame(c, MapData.controller(app))
        val version = JSONObject(app.assets.open("map/manifest.json").use { String(it.readBytes()) }).optString("version")
        c.load("central")
        if (version.isEmpty()) {
            assertEquals(MapDataPhase.MISSING, c.state.value.phase)
        } else {
            assertEquals(MapDataPhase.READY, c.state.value.phase)
            assertEquals(version, c.bundledVersion)
            assertTrue(c.state.value.graph!!.edgeCount > 0)
        }
        assertNull(c.state.value.error)
        assertEquals(true, File(app.filesDir, "mapdata").isDirectory)
    }
}
