package com.kuryelog.logic.scooter

/**
 * G3 ekranındaki resmî navigasyon (S45, N10; 28 Kasım HCI, Segway 6.10.x): kart `0x23`, komut `0x71`, yanıt beklenmez
 * (yalnız başlatma boş yanıt alır). Görüntü komutu yok; ok, mesafe ve sokak adı. Alanların adı kayıttaki tek örnekten
 * çıkarıldı (doğrulanmadı): `03` = LE u32 kalan m, LE u32 ikinci değer, LE u16 dönüş adayı, LE u16 dördüncü değer, 6 bayt 0.
 */
object G3Display {
    const val BOARD = 0x23
    const val CMD = 0x71
    const val STREET_NOW = 0x10
    const val STREET_NEXT = 0x24
    /** Sokak adı sınırı (bayt, NUL hariç); kayıtta en uzun örnek 12 bayt, sınır tahmindir. */
    const val STREET_MAX = 32

    class Frame(val index: Int, val data: ByteArray)

    fun start() = Frame(0, byteArrayOf(1))

    fun remaining(meters: Int) = Frame(1, le(meters.toLong(), 4))

    fun guide(remainingM: Int, second: Int, turn: Int, fourth: Int) =
        Frame(3, le(remainingM.toLong(), 4) + le(second.toLong(), 4) + le(turn.toLong(), 2) + le(fourth.toLong(), 2) + ByteArray(6))

    fun street(index: Int, name: String): Frame {
        val b = name.toByteArray(Charsets.UTF_8)
        var n = minOf(b.size, STREET_MAX)
        while (n > 0 && n < b.size && (b[n].toInt() and 0xC0) == 0x80) n-- // UTF-8 karakterini bölme
        return Frame(index, b.copyOf(n) + byteArrayOf(0))
    }

    /** "G3 ekran testi" adımı: [step] 0 başlatır, sonra dönüş adayı 0..[PROBE_LAST] her adımda bir; bitince null. */
    fun probe(step: Int): List<Frame>? = when {
        step == 0 -> listOf(start())
        step in 1..PROBE_LAST + 1 -> {
            val code = step - 1
            listOf(guide(SAMPLE_REMAINING, SAMPLE_SECOND, code, SAMPLE_FOURTH), street(STREET_NOW, "KOD $code"), street(STREET_NEXT, "KOD $code"),
                remaining(SAMPLE_REMAINING))
        }
        else -> null
    }

    const val PROBE_LAST = 40
    const val PROBE_STEP_MS = 4000L
    private const val SAMPLE_REMAINING = 7078
    private const val SAMPLE_SECOND = 1676
    private const val SAMPLE_FOURTH = 31

    private fun le(v: Long, n: Int) = ByteArray(n) { ((v ushr (8 * it)) and 0xff).toByte() }
}
