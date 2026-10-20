package com.kuryelog.app.map

import android.content.Context
import com.kuryelog.logic.map.weather.ReportStore
import java.io.File

/** Hava raporu önbelleği `files/map/weather.json` (MAP.md 10.11): atomik yazım (geçici dosya + yeniden adlandırma). */
internal class FileReportStore(app: Context) : ReportStore {
    private val file = File(app.filesDir, "map/weather.json")

    @Synchronized
    override fun read(): String? = runCatching { if (file.isFile) file.readText() else null }.getOrNull()

    @Synchronized
    override fun write(raw: String) {
        val dir = file.parentFile ?: return
        dir.mkdirs()
        val tmp = File(dir, "weather.json.tmp")
        tmp.writeText(raw)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    @Synchronized
    override fun clear() {
        file.delete()
    }
}
