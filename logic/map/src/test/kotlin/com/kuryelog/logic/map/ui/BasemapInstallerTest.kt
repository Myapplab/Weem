package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.graph.MapAssets
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S39 gömülü vektör altlık (MAP.md 6.19): APK'daki karolar ve glifler bir kez kopyalanır, stil yerel adreslerle. */
class BasemapInstallerTest {
    private val dir: File = Files.createTempDirectory("basemap").toFile().apply { deleteOnExit() }

    private class Assets(val files: MutableMap<String, ByteArray>) : MapAssets {
        val opened = ArrayList<String>()
        override fun read(name: String): ByteArray? = files[name]
        override fun open(name: String) = files[name]?.inputStream()?.also { opened += name }
    }

    private val tiles = ByteArray(5000) { (it % 251).toByte() }

    private fun assets(version: String = "20261020") = Assets(
        mutableMapOf(
            "tiles.json" to """{"version":"$version","maxzoom":15,"bytes":${tiles.size}}""".toByteArray(),
            "baku.pmtiles" to tiles,
            "glyphs.json" to """{"font":"NotoSans","ranges":["0-255","256-511"]}""".toByteArray(),
            "glyphs/NotoSans/0-255.pbf" to byteArrayOf(1, 2, 3),
            "glyphs/NotoSans/256-511.pbf" to byteArrayOf(4, 5),
            "style-light.json" to """{"version":8,"sources":{"protomaps":{"type":"vector","url":"{{TILES}}"}},"glyphs":"{{GLYPHS}}","layers":[]}""".toByteArray(),
            "style-dark.json" to """{"version":8,"name":"dark","sources":{"protomaps":{"type":"vector","url":"{{TILES}}"}},"glyphs":"{{GLYPHS}}","layers":[]}""".toByteArray(),
        ),
    )

    @Test
    fun `karosuz APK'da yerel altlık yok, eskisi gibi ağdan`() {
        val a = Assets(mutableMapOf("glyphs.json" to """{"font":"NotoSans","ranges":[]}""".toByteArray()))
        val b = BasemapInstaller(dir, a)
        assertFalse(b.embedded())
        assertNull(b.install())
        assertFalse(File(dir, BasemapInstaller.TILES).exists())
    }

    @Test
    fun `karolar ve glifler bir kez kopyalanır, stil yerel dosyalardan okur`() {
        val a = assets()
        val b = BasemapInstaller(dir, a)
        assertTrue(b.embedded())
        val local = b.install()!!
        assertArrayEquals(tiles, File(dir, "baku.pmtiles").readBytes())
        assertArrayEquals(byteArrayOf(4, 5), File(dir, "glyphs/NotoSans/256-511.pbf").readBytes())
        // Gömülü olmayan aralık boş ama geçerli dosya.
        assertArrayEquals(BasemapInstaller.emptyGlyphs("NotoSans", "1536-1791"), File(dir, "glyphs/NotoSans/1536-1791.pbf").readBytes())
        assertEquals("pmtiles://file://${File(dir, "baku.pmtiles").absolutePath}", local.tilesUrl)

        val style = JSONObject(b.style(local, dark = false)!!)
        assertEquals(local.tilesUrl, style.getJSONObject("sources").getJSONObject("protomaps").getString("url"))
        assertEquals("file://${File(dir, "glyphs").absolutePath}/{fontstack}/{range}.pbf", style.getString("glyphs"))
        assertEquals("dark", JSONObject(b.style(local, dark = true)!!).getString("name"))

        // İkinci açılış: dosyalara dokunulmaz.
        a.opened.clear()
        assertEquals(local, b.install())
        assertTrue(a.opened.isEmpty())
    }

    @Test
    fun `yeni APK'daki yeni karolar eskisinin üzerine kopyalanır`() {
        BasemapInstaller(dir, assets("20261020")).install()!!
        val a = assets("20261101")
        val local = BasemapInstaller(dir, a).install()
        assertNotNull(local)
        assertTrue(local!!.version.startsWith("20261101|"))
        assertTrue("baku.pmtiles" in a.opened)
    }

    @Test
    fun `eksik karo dosyası kurulumu yarıda bırakır, işaret yazılmaz`() {
        val a = assets().apply { files.remove("baku.pmtiles") }
        assertNull(BasemapInstaller(dir, a).install())
        assertFalse(File(dir, BasemapInstaller.MARKER).exists())
    }

    @Test
    fun `boş glif dosyası protobuf yığını, adı ve aralığı taşır`() {
        val b = BasemapInstaller.emptyGlyphs("NotoSans", "0-255")
        // glyphs.stacks (alan 1) → fontstack.name (1) "NotoSans", fontstack.range (2) "0-255"
        val inner = byteArrayOf(0x0A, 8) + "NotoSans".toByteArray() + byteArrayOf(0x12, 5) + "0-255".toByteArray()
        assertArrayEquals(byteArrayOf(0x0A, inner.size.toByte()) + inner, b)
    }
}
