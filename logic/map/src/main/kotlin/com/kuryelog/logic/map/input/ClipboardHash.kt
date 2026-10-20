package com.kuryelog.logic.map.input

/**
 * Pano tekrar önleme (`clipboard_sniffer.dart:74-81`; MAP.md 8.3): FNV-1a 32 bit, UTF-16 kod birimleri üstünde; değer `map` tercih dosyasında
 * [KEY] (`Long`, 0…2³²−1). Aynı metin ikinci kez işlenmez; önce hash yazılır, sonra metin işlenir.
 */
object ClipboardHash {
    const val KEY = "clipboard.lastHash"

    /** Pano metni ilk okumada boşsa (odak geç gelir) bir kez daha okunur. */
    const val RETRY_MS = 400L

    fun fnv1a(s: String): Long {
        var h = 0x811c9dc5L
        for (c in s) {
            h = h xor c.code.toLong()
            h = (h * 0x01000193L) and 0xFFFFFFFFL
        }
        return h
    }
}
