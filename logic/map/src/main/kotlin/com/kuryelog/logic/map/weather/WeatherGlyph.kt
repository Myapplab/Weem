package com.kuryelog.logic.map.weather

/**
 * Hava simgesi (S42; kullanıcı: "hava durumu ikonları çok kötü"). Açıklama ([WeatherKind]) aynı kalır; simge daha ayrıntılıdır: açık gündüz/gece, parçalı
 * bulutlu gündüz/gece (WMO 2), kapalı (3 ya da kod yok), çisenti (51–57) ile yağmur ayrı. Seçim [WeatherIcons.glyph]; çizim `:app` `WeatherGlyphIcon`.
 */
enum class WeatherGlyph { SUN, MOON, PARTLY_DAY, PARTLY_NIGHT, CLOUD, FOG, DRIZZLE, RAIN, SNOW, THUNDER, WIND }

/**
 * Simge renkleri (ARGB; iki tema): güneş diski (merkez → kenar) ve ışınları, hilal (açık → koyu, kenar), yıldız, bulut (üst → alt, kenar), arkadaki ikinci
 * bulut, fırtına bulutu, yağmur, kar, şimşek, sis ve rüzgâr. Hap (`surface`) ve saat karosu (`raised`) zemininde okunur; açık temada açık renkli şekillerin
 * kenarı vardır.
 */
class WeatherInk private constructor(
    val sunCore: Int,
    val sunEdge: Int,
    val ray: Int,
    val moonLight: Int,
    val moonDark: Int,
    val moonEdge: Int,
    val star: Int,
    val cloudTop: Int,
    val cloudBottom: Int,
    val cloudEdge: Int,
    val backTop: Int,
    val backBottom: Int,
    val stormTop: Int,
    val stormBottom: Int,
    val stormEdge: Int,
    val rain: Int,
    val snow: Int,
    val bolt: Int,
    val boltEdge: Int,
    val fog: Int,
    val wind: Int,
) {
    companion object {
        val DAY = WeatherInk(
            sunCore = 0xFFFFE27A.toInt(), sunEdge = 0xFFFFA90A.toInt(), ray = 0xFFFFA90A.toInt(),
            moonLight = 0xFFFFF0B8.toInt(), moonDark = 0xFFF0B93A.toInt(), moonEdge = 0xFFC98F1E.toInt(), star = 0xFFE9A92A.toInt(),
            cloudTop = 0xFFFFFFFF.toInt(), cloudBottom = 0xFFD5DEE9.toInt(), cloudEdge = 0xFF8EA2B8.toInt(),
            backTop = 0xFFDCE4EE.toInt(), backBottom = 0xFFAEBDCE.toInt(),
            stormTop = 0xFFA6B2C1.toInt(), stormBottom = 0xFF6A7789.toInt(), stormEdge = 0xFF525F70.toInt(),
            rain = 0xFF2A78E4.toInt(), snow = 0xFF4E9BE0.toInt(), bolt = 0xFFFFC21A.toInt(), boltEdge = 0xFFD98A00.toInt(),
            fog = 0xFF8495AB.toInt(), wind = 0xFF3F8FC0.toInt(),
        )
        val NIGHT = WeatherInk(
            sunCore = 0xFFFFE58A.toInt(), sunEdge = 0xFFFFB42E.toInt(), ray = 0xFFFFC24D.toInt(),
            moonLight = 0xFFFFF4CC.toInt(), moonDark = 0xFFF2C452.toInt(), moonEdge = 0x00000000, star = 0xFFFFDC7A.toInt(),
            cloudTop = 0xFFF4F7FB.toInt(), cloudBottom = 0xFFB9C6D5.toInt(), cloudEdge = 0xFF4E5D70.toInt(),
            backTop = 0xFFB4C0CE.toInt(), backBottom = 0xFF8292A6.toInt(),
            stormTop = 0xFFBCC6D2.toInt(), stormBottom = 0xFF7D8A9C.toInt(), stormEdge = 0xFF46515F.toInt(),
            rain = 0xFF62ABFF.toInt(), snow = 0xFFE3EFFF.toInt(), bolt = 0xFFFFC94A.toInt(), boltEdge = 0xFFE09A12.toInt(),
            fog = 0xFFAAB8C9.toInt(), wind = 0xFF86C4EA.toInt(),
        )

        fun of(dark: Boolean): WeatherInk = if (dark) NIGHT else DAY
    }
}
