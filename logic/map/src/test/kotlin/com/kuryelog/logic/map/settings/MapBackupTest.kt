package com.kuryelog.logic.map.settings

import com.kuryelog.logic.core.EncryptedBackup
import com.kuryelog.logic.map.roads.RoadBook
import com.kuryelog.logic.map.surface.MarkStore
import com.kuryelog.logic.map.weather.WeatherPrefs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** A10.4h (S35, MAP.md 17.1 #15 b): haritanın küçük verisi haftalık şifreli yedekte; sırlar hiçbir yönde geçmez, geri yükleme üzerine yazmaz. */
class MapBackupTest {
    @get:Rule val temp = TemporaryFolder()
    private val week = EncryptedBackup.week("2026-10-14") // 12–18 Ekim
    private val password = "test-parola-1".toCharArray()

    private val settings = MapSettingsCodec.toJson(MapSettings(googleKey = "g-secret", orsKey = "o-secret", cartoKey = "c-secret")).toString()
    private val allPrefs: Map<String, Any?> = mapOf(
        MapSettings.KEY to settings, WeatherPrefs.KEY to "w-secret", "weather.enabled.v1" to false,
        "streets.blocked.v1" to "[\"1\",\"2\"]", "clipboard.lastHash" to 42L, "nullish" to null,
    )

    private fun mapDir(base: File) = File(base, "map").apply {
        File(this, "roads").mkdirs(); File(this, "logs").mkdirs()
        File(this, "roads/${RoadBook.WAYS}").writeText("{\"w\":1}")
        File(this, "roads/${RoadBook.DAYS}").writeText("{\"d\":1}")
        File(this, "roads/${RoadBook.WAYS}.damaged-5").writeText("x")
        File(this, "roads/x.tmp").writeText("x")
        File(this, MarkStore.FILE).writeText("[]")
        File(this, "weather.json").writeText("{}")
        File(this, "logs/2026-10-14_0930.gun.json").writeText("{\"r\":1}")
        File(this, "logs/2026-10-14_route_1792000000000.json").writeText("{\"route\":1}")
        File(this, "logs/2026-10-14.jsonl").writeText("ride log\n")
        File(this, "logs/2026-10-14_ride_1792000000000.jsonl").writeText("ride\n")
        File(this, "logs/2026-10-19_1000.gun.json").writeText("{\"next\":1}")
    }

    @Test fun secretsAreStrippedFromSettingsAndPrefs() {
        val out = MapBackup.strip(allPrefs)
        assertFalse(WeatherPrefs.KEY in out)
        val s = JSONObject(out[MapSettings.KEY] as String)
        for (k in MapBackup.SECRET_SETTINGS) assertFalse(k, s.has(k))
        assertEquals(MapSettings().vehicleId, s.getString("vehicleId"))
        assertEquals(42L, out["clipboard.lastHash"])
        // Okunamayan settings.v1 hiç girmez (sır sızmasın).
        assertFalse(MapSettings.KEY in MapBackup.strip(mapOf(MapSettings.KEY to "{googleKey:")))
        assertFalse(MapSettings.KEY in MapBackup.strip(mapOf(MapSettings.KEY to 5)))
    }

    @Test fun filesAreTheSmallMapDataOfTheWeek() {
        val dir = mapDir(temp.newFolder("a"))
        assertEquals(
            listOf("map/roads/${RoadBook.DAYS}", "map/roads/${RoadBook.WAYS}", "map/${MarkStore.FILE}",
                "map/logs/2026-10-14_0930.gun.json", "map/logs/2026-10-14_route_1792000000000.json"),
            MapBackup.files(dir, week.days).map { it.first },
        )
        assertTrue(MapBackup.files(File(temp.root, "none"), week.days).isEmpty())
    }

    @Test fun acceptsOnlyWhatFilesProduces() {
        for (ok in listOf("map/manual-marks.json", "map/roads/roads.ww.v1.json", "map/logs/2026-10-14.gun.json", "map/logs/2026-10-14_0930.gun.json",
            "map/logs/2026-10-14_route_5.json")) assertTrue(ok, MapBackup.accepts(ok))
        for (bad in listOf("map/../shared_prefs/kuryelog.xml", "map/roads/../../x.json", "map/logs/2026-10-14.jsonl", "map/weather.json",
            "map/roads/.hidden.json", "roads/roads.ww.v1.json", "map/logs/2026-10-14_ride_5.jsonl", "map/roads/sub/roads.ww.v1.json")) assertFalse(bad, MapBackup.accepts(bad))
        assertFalse(EncryptedBackup.mapEntry("map/../x")); assertFalse(EncryptedBackup.mapEntry("map/.x")); assertTrue(EncryptedBackup.mapEntry("map/a/b.json"))
    }

    private fun backup(dir: File): ByteArray {
        val content = EncryptedBackup.Content(
            listOf("2026-10-14/orders.jsonl" to File(temp.newFolder("day"), "orders.jsonl").apply { writeText("{\"id\":\"t\"}\n") }),
            JSONObject(), emptyList(), emptyList(),
            mapPrefs = EncryptedBackup.encodePrefs(MapBackup.strip(allPrefs)) { false }, mapFiles = MapBackup.files(dir, week.days),
        )
        return ByteArrayOutputStream().also { EncryptedBackup.write(week, content, password, it, "1.0", 1L, iterations = 10_000) }.toByteArray()
    }

    @Test fun encryptedRoundTripCarriesMapDataWithoutSecrets() {
        val bytes = backup(mapDir(temp.newFolder("a")))
        val plain = String(bytes, Charsets.ISO_8859_1)
        for (secret in listOf("g-secret", "o-secret", "c-secret", "w-secret", "ride log")) assertFalse(plain.contains(secret))
        val opened = EncryptedBackup.open(ByteArrayInputStream(bytes), password)
        assertEquals(5, opened.mapFiles.size)
        assertEquals("{\"w\":1}", String(opened.mapFiles.first { it.first.endsWith(RoadBook.WAYS) }.second))
        assertFalse(WeatherPrefs.KEY in opened.mapPrefs)
        assertFalse(JSONObject(opened.mapPrefs[MapSettings.KEY] as String).has("googleKey"))
        assertEquals(false, opened.mapPrefs["weather.enabled.v1"])
        assertEquals(42L, opened.mapPrefs["clipboard.lastHash"])
        assertEquals(5, opened.manifest.getInt("mapFiles"))
        // Gün dosyaları ayrı ZIP'te kalır; harita dosyaları oraya karışmaz.
        val logNames = java.util.zip.ZipInputStream(ByteArrayInputStream(opened.logsZip)).use { z -> generateSequence { z.nextEntry?.name }.toList() }
        assertEquals(listOf("2026-10-14/orders.jsonl"), logNames)
    }

    @Test fun pendingRestoreWritesOnlyWhatIsMissing() {
        val opened = EncryptedBackup.open(ByteArrayInputStream(backup(mapDir(temp.newFolder("old")))), password)
        val phone = temp.newFolder("new")
        File(phone, "map/roads").mkdirs()
        File(phone, "map/roads/${RoadBook.WAYS}").writeText("{\"phone\":1}") // telefondaki kalır
        assertEquals(5, MapBackup.savePending(phone, opened.mapPrefs, opened.mapFiles + ("map/../evil.json" to byteArrayOf(1))))
        assertTrue(MapBackup.hasPending(phone))
        val applied = MapBackup.applyPending(phone, setOf("clipboard.lastHash"))!!
        assertFalse(MapBackup.hasPending(phone))
        assertEquals(4, applied.files)
        assertEquals("{\"phone\":1}", File(phone, "map/roads/${RoadBook.WAYS}").readText())
        assertEquals("{\"d\":1}", File(phone, "map/roads/${RoadBook.DAYS}").readText())
        assertEquals("{\"route\":1}", File(phone, "map/logs/2026-10-14_route_1792000000000.json").readText())
        assertFalse(File(phone, "evil.json").exists())
        assertFalse(File(phone, "map/logs/2026-10-14.jsonl").exists())
        assertEquals(setOf(MapSettings.KEY, "weather.enabled.v1", "streets.blocked.v1"), applied.prefs.keys)
        assertNull(MapBackup.applyPending(phone, emptySet()))
    }

    @Test fun nothingToRestoreLeavesNoPending() {
        val phone = temp.newFolder("p")
        assertEquals(0, MapBackup.savePending(phone, emptyMap(), emptyList()))
        assertFalse(MapBackup.hasPending(phone))
        assertNull(MapBackup.applyPending(phone, emptySet()))
    }
}
