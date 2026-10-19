package com.kuryelog.logic.map.route

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.graph.RoadGraph

/** Uzun basış menüsü bisiklet satırı eylemi (`MAP.md` 5.1). */
enum class BikewayMenuAction { MARK, ALREADY_OSM, UNMARK }

/** Harita bildirim şeridi iletisi (`MAP.md` 7.13): metin, 7 sn, isteğe bağlı "Geri al" eylemi. */
class MapToast(val text: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null, val seconds: Int = 7)

/** Bildirim şeridi; testlerde son iletiyi tutan sahte. */
interface MapToasts {
    fun show(toast: MapToast)
}

fun bikewayMenuAction(graph: RoadGraph, edge: Int, marks: BikewayMarks): BikewayMenuAction = TODO("A6.8")

/** İşaretler ya da kaldırır; toast "Geri al" ile son işlemi tersine çevirir. */
fun toggleBikewayWithUndo(
    store: BikewayMarksStore,
    toasts: MapToasts,
    graph: RoadGraph,
    edge: Int,
    name: String,
    lang: Lang = Lang.TR,
): Unit = TODO("A6.8")
