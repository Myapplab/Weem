package com.kuryelog.logic.map.ui

/** Google harita JSON stili ve koyu harita kararı (MAP.md 6.4; `google_map_surface.dart:23-52`). */
object GoogleMapStyle {
    private val DARK = listOf(
        """{"elementType":"geometry","stylers":[{"color":"#1d2330"}]}""",
        """{"elementType":"labels.text.fill","stylers":[{"color":"#8a96a8"}]}""",
        """{"elementType":"labels.text.stroke","stylers":[{"color":"#141922"}]}""",
        """{"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#3a4456"}]}""",
        """{"featureType":"landscape.man_made","elementType":"geometry","stylers":[{"color":"#222a38"}]}""",
        """{"featureType":"poi","elementType":"geometry","stylers":[{"color":"#232b39"}]}""",
        """{"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#1b2d2a"}]}""",
        """{"featureType":"road","elementType":"geometry","stylers":[{"color":"#2f3a4c"}]}""",
        """{"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#1a202b"}]}""",
        """{"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#3a465a"}]}""",
        """{"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#4a566c"}]}""",
        """{"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#222a36"}]}""",
        """{"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#9aa6b8"}]}""",
        """{"featureType":"transit","elementType":"geometry","stylers":[{"color":"#2a3240"}]}""",
        """{"featureType":"water","elementType":"geometry","stylers":[{"color":"#0e1626"}]}""",
        """{"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#4e6080"}]}""",
    )
    private val HIDE_POIS = listOf(
        """{"featureType":"poi.business","stylers":[{"visibility":"off"}]}""",
        """{"featureType":"transit","stylers":[{"visibility":"off"}]}""",
    )

    /** POI'ler (YolGo kendi yerlerini çizer) bu yakınlığın altında gizlenir. */
    const val HIDE_POIS_BELOW_ZOOM = 17.0

    /** Koyu stil geçerli JSON dizisi ve koyu zemin (`#1d2330`) içerir; açık ve POI gizli değilse null. */
    fun style(dark: Boolean, hidePois: Boolean): String? {
        val rules = (if (dark) DARK else emptyList()) + (if (hidePois) HIDE_POIS else emptyList())
        return if (rules.isEmpty()) null else rules.joinToString(",", "[", "]")
    }

    /** Haritanın kendisi koyu mu? `dark-ui` koyu arayüz + açık harita demektir. */
    fun isDarkMap(mapStyle: String, nightPalette: Boolean): Boolean = nightPalette && mapStyle != "dark-ui"
}
