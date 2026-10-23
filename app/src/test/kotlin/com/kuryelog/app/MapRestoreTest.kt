package com.kuryelog.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kuryelog.logic.map.settings.MapBackup
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.settings.MapSettingsCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** A10.4h: bekleyen harita geri yüklemesi açılışta `map` tercihlerine ve `files/map/`'e yalnız eksikleri yazar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MapRestoreTest {
    @Test fun pendingMapDataIsAppliedOnceWithoutOverwriting() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("map", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("weather.enabled.v1", true).commit()
        File(app.filesDir, "map").deleteRecursively()
        val settings = MapSettingsCodec.toJson(MapSettings(googleKey = "g-secret")).toString()
        val backup = mapOf<String, Any>(MapSettings.KEY to settings, "weather.enabled.v1" to false, "clipboard.lastHash" to 7L, "weather.key.v1" to "w-secret")
        MapBackup.savePending(app.filesDir, backup, listOf("map/manual-marks.json" to "[1]".toByteArray(), "map/roads/roads.ww.v1.json" to "{}".toByteArray()))

        EncryptedBackups.applyPendingMap(app)

        assertEquals(true, prefs.getBoolean("weather.enabled.v1", false)) // telefondaki kalır
        assertEquals(7L, prefs.getLong("clipboard.lastHash", 0))
        assertFalse(prefs.contains("weather.key.v1"))
        assertFalse(JSONObject(prefs.getString(MapSettings.KEY, null)!!).has("googleKey"))
        assertEquals("[1]", File(app.filesDir, "map/manual-marks.json").readText())
        assertEquals("{}", File(app.filesDir, "map/roads/roads.ww.v1.json").readText())
        assertFalse(MapBackup.hasPending(app.filesDir))
        EncryptedBackups.applyPendingMap(app) // ikinci açılışta iş yok
    }
}
