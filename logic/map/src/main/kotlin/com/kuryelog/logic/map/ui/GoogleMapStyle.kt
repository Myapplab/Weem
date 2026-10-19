package com.kuryelog.logic.map.ui

/** Google harita JSON stili ve koyu harita kararı (MAP.md 6.4; `google_map_surface.dart`). İskelet (A6.2b): gövdeler A6.5'te. */
object GoogleMapStyle {
    /** Koyu stil geçerli JSON dizisi ve koyu zemin (`#1d2330`) içerir; açık ve POI gizli değilse null. */
    fun style(dark: Boolean, hidePois: Boolean): String? = TODO("A6.5")

    /** Haritanın kendisi koyu mu? `dark-ui` koyu arayüz + açık harita demektir. */
    fun isDarkMap(mapStyle: String, nightPalette: Boolean): Boolean = TODO("A6.5")
}
