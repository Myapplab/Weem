package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Bilingual
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.BikewayMarks
import com.kuryelog.logic.map.route.BikewayMarksStore

/** Uzun basış menüsü satırı (MAP.md 5.1 `bikewayMenuAction`). */
enum class BikewayMenuAction { MARK, ALREADY_OSM, UNMARK }

/** Bisiklet yolu metinleri (MAP.md 5.1; uzun basış satırı, bildirimler). */
enum class BikewayText(override val tr: String, override val az: String) : Bilingual {
    MARK("Bisiklet yolu olarak işaretle", "Velosiped yolu kimi qeyd et"),
    UNMARK("Bisiklet yolu işaretini kaldır", "Velosiped yolu qeydini sil"),
    MARK_SUB("%s · bütün sokak; rotada tercih edilir", "%s · bütün küçə; marşrutda üstünlük verilir"),
    ALREADY_OSM_SUB("%s · OSM'de zaten bisiklet yolu", "%s · OSM-də artıq velosiped yoludur"),
    MARKED("Bisiklet yolu işaretlendi: %s", "Velosiped yolu qeyd olundu: %s"),
    UNMARKED("Bisiklet yolu işareti kaldırıldı: %s", "Velosiped yolu qeydi silindi: %s"),
    UNDO("Geri al", "Geri qaytar"),
    NO_NAME("isimsiz yol", "adsız yol"),
    ;

    fun format(lang: Lang, vararg args: Any?): String = String.format(get(lang), *args)
}

object BikewayMenu {
    private const val TOAST_SECONDS = 7

    /** Way elle işaretliyse kaldır; değilse kenar OSM bisiklet yoluysa pasif satır; değilse işaretle. */
    fun action(g: RoadGraph, edge: Int, marks: BikewayMarks): BikewayMenuAction = when {
        g.osmIdOf(edge) in marks.ways -> BikewayMenuAction.UNMARK
        g.isBikeway(edge) -> BikewayMenuAction.ALREADY_OSM
        else -> BikewayMenuAction.MARK
    }

    /** İşaretler/kaldırır, bildirim "Bisiklet yolu işaretlendi: $ad" / "… işareti kaldırıldı: $ad" ve "Geri al" (7 sn). */
    fun toggleWithUndo(store: BikewayMarksStore, toasts: Toasts, g: RoadGraph, edge: Int, name: String, lang: Lang = Lang.TR) {
        val way = g.osmIdOf(edge)
        val shown = name.ifBlank { BikewayText.NO_NAME.get(lang) }
        when (action(g, edge, store.marks)) {
            BikewayMenuAction.ALREADY_OSM -> Unit
            BikewayMenuAction.MARK -> {
                store.mark(way, name)
                toasts.show(MapToast(BikewayText.MARKED.format(lang, shown), ToastTone.BIKEWAY, TOAST_SECONDS, BikewayText.UNDO.get(lang)) { store.unmark(way) })
            }
            BikewayMenuAction.UNMARK -> {
                val before = store.marks.names[way] ?: name
                store.unmark(way)
                toasts.show(MapToast(BikewayText.UNMARKED.format(lang, shown), ToastTone.BIKEWAY, TOAST_SECONDS, BikewayText.UNDO.get(lang)) { store.mark(way, before) })
            }
        }
    }
}

/** Harita katmanındaki bir bisiklet yolu çizgisi (MAP.md 5.1 `bikewayLines`). */
class BikewayLine(val edge: Int, val dashed: Boolean, val rgb: Int, val alpha: Double, val width: Double)

object BikewayLayerRules {
    /** `showLayer` kapalıysa boş; OSM düz, elle işaretli kesikli; renk koyu `#3DDCE8`, açık `#00838F`, %85 opak. */
    fun lines(g: RoadGraph, marks: BikewayMarks, showLayer: Boolean, dark: Boolean): List<BikewayLine> {
        if (!showLayer) return emptyList()
        val rgb = if (dark) 0x3DDCE8 else 0x00838F
        val out = ArrayList<BikewayLine>()
        for (e in 0 until g.edgeCount) {
            if (g.isBikeway(e)) out += BikewayLine(e, dashed = false, rgb = rgb, alpha = 0.85, width = 4.0)
            else if (g.osmIdOf(e) in marks.ways) out += BikewayLine(e, dashed = true, rgb = rgb, alpha = 0.85, width = 4.0)
        }
        return out
    }
}
