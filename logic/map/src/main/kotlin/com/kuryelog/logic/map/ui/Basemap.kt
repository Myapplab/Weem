package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.graph.AtomicFile
import com.kuryelog.logic.map.graph.MapAssets
import java.io.File
import org.json.JSONObject

/**
 * Gömülü vektör altlık (S39; MAP.md 6.19): APK'daki `assets/basemap/` (CI'ın ürettiği Geniş Bakü `baku.pmtiles` + repodaki stil şablonları ve
 * Noto Sans glifleri) uygulamanın dosyalarına bir kez kopyalanır; MapLibre karoları ve glifleri yerel dosyadan okur, ağ gerekmez. Karosuz APK'da
 * (PR CI'ı, yerel derleme) [install] null döner ve altlık eskisi gibi OpenFreeMap'ten gelir.
 *
 * [assets] `assets/basemap/` köküne göredir. Kopya sürümü `tiles.json`'daki sürüm + bayt + glif listesi; aynıysa dosyalara dokunulmaz.
 */
class BasemapInstaller(private val dir: File, private val assets: MapAssets) {
    /** Kurulu yerel altlık: MapLibre adresleri ([tilesUrl], [glyphsUrl]) ve sürüm (stil anahtarında). */
    data class Local(val version: String, val tilesUrl: String, val glyphsUrl: String)

    /** APK karo içeriyor mu (kopyalamadan; küçük `tiles.json` okunur). */
    fun embedded(): Boolean = tilesManifest() != null

    /**
     * Gerekirse kopyalar ve yerel altlığı döndürür; karosuz APK'da ya da bozuk dosyada null. IO iş parçacığında çağrılır (ilk açılışta karolar
     * ~onlarca MB akışla kopyalanır; sonraki açılışlarda yalnız işaret dosyası okunur).
     */
    fun install(): Local? {
        val tiles = tilesManifest() ?: return null
        val glyphs = runCatching { JSONObject(String(assets.read("glyphs.json")!!, Charsets.UTF_8)) }.getOrNull() ?: return null
        val font = glyphs.optString("font").ifEmpty { return null }
        val ranges = glyphs.optJSONArray("ranges")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        val version = "${tiles.optString("version")}|${tiles.optLong("bytes")}|$font|${ranges.joinToString(",")}"
        val tilesFile = File(dir, TILES)
        val glyphDir = File(dir, "glyphs/$font")
        val marker = File(dir, MARKER)
        val local = Local(version, "pmtiles://file://${tilesFile.absolutePath}", "file://${File(dir, "glyphs").absolutePath}/{fontstack}/{range}.pbf")
        if (marker.exists() && runCatching { marker.readText() }.getOrNull() == version && tilesFile.exists()) return local
        return try {
            dir.mkdirs()
            glyphDir.mkdirs()
            marker.delete()
            (assets.open(TILES) ?: return null).use { AtomicFile.write(tilesFile, it) }
            for (r in ranges) (assets.open("glyphs/$font/$r.pbf") ?: return null).use { AtomicFile.write(File(glyphDir, "$r.pbf"), it) }
            // Gömülü olmayan aralıklar boş glif dosyası: MapLibre eksik aralıkta karonun bütün yazılarını bekletir (ör. tek bir Arapça ad).
            for (start in 0 until 65536 step 256) {
                val r = "$start-${start + 255}"
                if (r !in ranges) File(glyphDir, "$r.pbf").writeBytes(emptyGlyphs(font, r))
            }
            AtomicFile.write(marker, version.toByteArray(Charsets.UTF_8))
            local
        } catch (_: Exception) {
            null
        }
    }

    /** Stil şablonu (`style-light.json` / `style-dark.json`) yerel adreslerle; şablon yoksa null. */
    fun style(local: Local, dark: Boolean): String? =
        assets.read(if (dark) "style-dark.json" else "style-light.json")?.let { localStyle(String(it, Charsets.UTF_8), local) }

    private fun tilesManifest(): JSONObject? =
        runCatching { JSONObject(String(assets.read("tiles.json")!!, Charsets.UTF_8)) }.getOrNull()?.takeIf { it.optLong("bytes") > 0 }

    companion object {
        const val TILES = "baku.pmtiles"
        const val MARKER = "installed.txt"

        /** Şablondaki `{{TILES}}` ve `{{GLYPHS}}` yer tutucuları → yerel MapLibre adresleri. */
        fun localStyle(template: String, local: Local): String =
            template.replace("\"{{TILES}}\"", JSONObject.quote(local.tilesUrl)).replace("\"{{GLYPHS}}\"", JSONObject.quote(local.glyphsUrl))

        /** Glifsiz geçerli glif dosyası (`glyphs { stacks { name, range } }`, protobuf). */
        fun emptyGlyphs(font: String, range: String): ByteArray {
            fun field(tag: Int, bytes: ByteArray) = byteArrayOf(tag.toByte(), bytes.size.toByte()) + bytes
            return field(0x0A, field(0x0A, font.toByteArray()) + field(0x12, range.toByteArray()))
        }
    }
}
