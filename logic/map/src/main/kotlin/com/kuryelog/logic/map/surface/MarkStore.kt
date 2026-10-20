package com.kuryelog.logic.map.surface

import com.kuryelog.logic.map.roads.RoadFiles
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Elle kötü noktalar `files/map/manual-marks.json` (Y13 deposunun WEEM 1.0 alt kümesi, SORU-Q1; MAP.md 9.10): YolGo
 * `surface_models.dart:19-37` biçimi aynen. Silme = `removed: true` (geri alınabilir). Okunamayan dosya `damaged-<ms>` kopyası alınıp
 * boş başlar. Gün silme bu işaretlere dokunmaz.
 */
class MarkStore(private val files: RoadFiles, private val nowMs: () -> Long) {
    private val list = ArrayList<SurfaceMark>()

    init {
        val raw = files.read(FILE)
        if (!raw.isNullOrEmpty()) {
            val decoded = decode(raw)
            if (decoded == null) files.write("$FILE.damaged-${nowMs()}", raw) else list += decoded
        }
    }

    val marks: List<SurfaceMark> get() = list.toList()

    fun save(m: SurfaceMark) {
        val i = list.indexOfFirst { it.id == m.id }
        if (i >= 0) list[i] = m else list += m
        write()
    }

    fun setRemoved(id: String, removed: Boolean) {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        list[i] = list[i].copy(removed = removed)
        write()
    }

    private fun write() = files.write(FILE, encode(list))

    companion object {
        const val FILE = "manual-marks.json"

        private val LABELS = mapOf(
            SurfaceLabel.UNKNOWN to "unknown", SurfaceLabel.SMOOTH to "smooth", SurfaceLabel.ROUGH to "rough",
            SurfaceLabel.POTHOLE to "pothole", SurfaceLabel.PATCH to "patch", SurfaceLabel.RAISED_ASPHALT to "raisedAsphalt",
            SurfaceLabel.DIRT to "dirt", SurfaceLabel.GRAVEL to "gravel", SurfaceLabel.HUMP to "hump", SurfaceLabel.OBSTACLE to "obstacle",
        )
        private val ACCESS = mapOf(Passability.UNKNOWN to "unknown", Passability.PASSABLE to "passable", Passability.CLOSED to "closed")

        fun encode(marks: List<SurfaceMark>): String = JSONArray().also { a ->
            for (m in marks) {
                a.put(
                    JSONObject().put("id", m.id).put("way", m.way).put("lat", m.lat).put("lon", m.lon).put("at", m.at)
                        .put("label", LABELS.getValue(m.label)).put("access", ACCESS.getValue(m.access)).put("source", m.source)
                        .put("removed", m.removed),
                )
            }
        }.toString()

        /** Dizi değilse ya da bozuksa null; eksik/tanınmayan alanlı öğe atlanır ya da varsayılan alır. */
        fun decode(raw: String): List<SurfaceMark>? = try {
            val a = JSONTokener(raw).nextValue() as? JSONArray
            a?.let {
                (0 until it.length()).mapNotNull { i ->
                    val o = it.opt(i) as? JSONObject ?: return@mapNotNull null
                    val way = o.opt("way") as? Number ?: return@mapNotNull null
                    val lat = o.opt("lat") as? Number ?: return@mapNotNull null
                    val lon = o.opt("lon") as? Number ?: return@mapNotNull null
                    SurfaceMark(
                        id = o.optString("id"), way = way.toLong(), lat = lat.toDouble(), lon = lon.toDouble(),
                        at = (o.opt("at") as? Number)?.toLong() ?: 0L,
                        label = LABELS.entries.firstOrNull { e -> e.value == o.optString("label") }?.key ?: SurfaceLabel.UNKNOWN,
                        access = ACCESS.entries.firstOrNull { e -> e.value == o.optString("access") }?.key ?: Passability.UNKNOWN,
                        source = o.optString("source", "user"), removed = o.optBoolean("removed", false),
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
