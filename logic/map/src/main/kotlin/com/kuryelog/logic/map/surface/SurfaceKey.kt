package com.kuryelog.logic.map.surface

import java.util.Locale

/**
 * Kesim anahtarı `way/çapa/yön/indeks` (YolGo `surface_controller._observation`): kesimler grafın değişebilir indekslerine değil kenarın
 * fiziksel başlangıç koordinatına çapalanır; harita yenilenip koordinat değişirse eski kesim yok sayılır.
 */
object SurfaceKey {
    /** `enlem,boylam` altı ondalık (Dart `toStringAsFixed(6)`). */
    fun anchor(lat: Double, lon: Double): String = String.format(Locale.US, "%.6f,%.6f", lat, lon)

    fun of(way: Long, anchor: String, direction: Int, index: Int): String = "$way/$anchor/$direction/$index"

    /** Anahtardaki çapa; biçim bozuksa null. */
    fun anchorOf(key: String): String? = key.split('/').let { if (it.size >= 4) it[1] else null }
}
