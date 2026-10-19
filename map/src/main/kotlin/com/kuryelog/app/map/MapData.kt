package com.kuryelog.app.map

import android.content.Context
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.graph.MapAssets
import com.kuryelog.logic.map.graph.MapDataController
import com.kuryelog.logic.map.graph.MapDownload
import com.kuryelog.logic.map.net.HttpPort
import com.kuryelog.logic.map.net.UrlHttp
import com.kuryelog.logic.map.net.UserAgent
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

/** Android `HttpPort` (MAP.md 8.12): `HttpURLConnection` (`UrlHttp`); izin `INTERNET` `:app` manifestinde. */
object AndroidHttp : HttpPort by UrlHttp()

/**
 * Harita verisi (MAP.md 2.9, 2.14): tek örnek [MapDataController] — `filesDir/mapdata`, `assets/map/`, `map` tercihleri. Durum
 * `controller(context).state` (`StateFlow`); açılışta ve bölge değişince `load(region)`, "İndir / güncelle" `download(region)`
 * (ekran açıkken süren coroutine; YolGo'daki gibi). Ekranlar A6.5/Bölüm 6.
 */
object MapData {
    @Volatile
    private var instance: MapDataController? = null

    fun controller(context: Context): MapDataController =
        instance ?: synchronized(this) { instance ?: create(context.applicationContext).also { instance = it } }

    private fun create(app: Context): MapDataController {
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "?"
        val assets = MapAssets { name ->
            try {
                app.assets.open("map/$name").use { it.readBytes() }
            } catch (_: FileNotFoundException) {
                null
            }
        }
        val core = app.getSharedPreferences("kuryelog", Context.MODE_PRIVATE)
        return MapDataController(
            dir = File(app.filesDir, "mapdata"),
            assets = assets,
            prefs = MapPrefsStore(app),
            download = MapDownload(AndroidHttp, UserAgent.of(version)),
            lang = { Lang.resolve(core.getString(Lang.PREF_KEY, null), Locale.getDefault().language) },
        )
    }
}
