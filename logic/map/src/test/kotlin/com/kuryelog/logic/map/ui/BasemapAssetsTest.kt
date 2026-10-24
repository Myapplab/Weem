package com.kuryelog.logic.map.ui

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S39 gömülü altlığın repodaki dosyaları (`map/src/main/assets/basemap/`, `tools/basemap/make_style.cjs` üretir): şablonlar yerel adreslerle geçerli JSON,
 * sprite yok (APK'da sprite yok; eksik simge MapLibre'de katmanı boş bırakır), bütün yazılar gömülü tek yazı tipiyle, listedeki her glif aralığı dosyada.
 */
class BasemapAssetsTest {
    private val root: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "AGENTS.md").isFile && File(it, "logic").isDirectory }
    private val dir = File(root, "map/src/main/assets/basemap")
    private val local = BasemapInstaller.Local("t", "pmtiles://file:///data/x/baku.pmtiles", "file:///data/x/glyphs/{fontstack}/{range}.pbf")

    private fun style(name: String): JSONObject {
        val template = File(dir, name).readText()
        assertTrue(name, template.contains("\"{{TILES}}\"") && template.contains("\"{{GLYPHS}}\""))
        return JSONObject(BasemapInstaller.localStyle(template, local))
    }

    @Test
    fun `glif listesindeki her aralık dosyada ve protobuf`() {
        val g = JSONObject(File(dir, "glyphs.json").readText())
        assertEquals("NotoSans", g.getString("font"))
        val ranges = g.getJSONArray("ranges")
        assertTrue(ranges.length() >= 5)
        for (i in 0 until ranges.length()) {
            val f = File(dir, "glyphs/NotoSans/${ranges.getString(i)}.pbf")
            val bytes = f.readBytes()
            assertTrue(f.name, bytes.size > 1000 && bytes[0] == 0x0A.toByte())
        }
    }

    @Test
    fun `açık ve koyu stil yerel adreslerle, sprite'sız, tek yazı tipi, aynı katmanlar`() {
        val ids = ArrayList<List<String>>()
        for (name in listOf("style-light.json", "style-dark.json")) {
            val s = style(name)
            assertEquals(local.tilesUrl, s.getJSONObject("sources").getJSONObject("protomaps").getString("url"))
            assertEquals(local.glyphsUrl, s.getString("glyphs"))
            assertFalse(name, s.has("sprite"))
            val layers = s.getJSONArray("layers")
            assertTrue(name, layers.length() > 40)
            val list = ArrayList<String>()
            for (i in 0 until layers.length()) {
                val l = layers.getJSONObject(i)
                list += l.getString("id")
                val layout = l.optJSONObject("layout") ?: continue
                assertFalse("${l.getString("id")} icon-image", layout.has("icon-image"))
                if (layout.has("text-font")) {
                    val font = layout.get("text-font") as JSONArray
                    assertEquals(l.getString("id"), "[\"literal\",[\"NotoSans\"]]", font.toString())
                }
            }
            ids += list
        }
        assertEquals(ids[0], ids[1])
    }
}
