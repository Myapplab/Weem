package com.kuryelog.logic.scooter

/**
 * G3 ekranındaki resmî navigasyon (S45, N10; 28 Kasım HCI, Segway 6.10.x): kart `0x23`, komut `0x71`, yanıt beklenmez
 * (yalnız başlatma boş yanıt alır). Görüntü komutu yok; ok, mesafe ve sokak adı. Alanların adı kayıttaki tek örnekten
 * çıkarıldı, G3 ekranında doğrulandı (build-21): `03` = LE u32 kalan m ("7.0km"), LE u32 kalan s ("28min"), LE u16 dönüş kodu, LE u16
 * sıradaki manevraya m ("30 m"), 6 bayt 0.
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

    fun guide(remainingM: Int, remainingS: Int, turn: Int, nextM: Int) =
        Frame(3, le(remainingM.toLong(), 4) + le(remainingS.toLong(), 4) + le(turn.toLong(), 2) + le(minOf(nextM, 0xffff).toLong(), 2) + ByteArray(6))

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

    /**
     * Dönüş kodları (28 Kasım "G3 ekran testi" videosu, build-21): 1 düz, 2 sol, 3 sağ, 4 hafif sol, 5 hafif sağ, 6 keskin sol,
     * 7 keskin sağ, 8 sola U, 11/17 dönel kavşak, 12/18 kavşaktan düz, 15 varış iğnesi, 16 tünel, 19 sağa U; 9, 10, 13, 14, 20, 23,
     * 27 ve 29+ düz; 21/25 sol, 22/26 sağ, 24 sola U, 28 sağa U (aynı oklar). 0 görülmedi.
     */
    const val STRAIGHT = 1
    const val LEFT = 2
    const val RIGHT = 3
    const val SLIGHT_LEFT = 4
    const val SLIGHT_RIGHT = 5
    const val SHARP_LEFT = 6
    const val SHARP_RIGHT = 7
    const val U_LEFT = 8
    const val ARRIVE = 15
    const val U_RIGHT = 19

    /** WEEM dönüşü → kod: [angle] + sağ, − sol (derece; `RouteTurn`); null = dönüş kalmadı. */
    fun turnCode(angle: Double?): Int {
        if (angle == null) return STRAIGHT
        val a = kotlin.math.abs(angle)
        val right = angle > 0
        return when {
            a < 60 -> if (right) SLIGHT_RIGHT else SLIGHT_LEFT
            a < 135 -> if (right) RIGHT else LEFT
            a < 165 -> if (right) SHARP_RIGHT else SHARP_LEFT
            else -> if (right) U_RIGHT else U_LEFT
        }
    }

    /** Ekrana giden yönlendirme: kalan yol (m, s), sıradaki manevraya kalan m, kod ve sokak adı. */
    data class Guide(val remainingM: Int, val remainingS: Int, val nextM: Int, val code: Int, val street: String)

    /**
     * Rota ölçümünden [Guide] (S45): dönüş kalmadıysa varışa kalan yol; varışa [ARRIVE_M] kalınca varış iğnesi.
     * [turnAngle]/[turnName] sıradaki dönüş (yoksa null/boş).
     */
    fun guide(remainingM: Double, remainingS: Double?, nextTurnM: Double?, turnAngle: Double?, turnName: String): Guide {
        val rem = maxOf(0, Math.round(remainingM).toInt())
        val code = if (nextTurnM == null) (if (remainingM <= ARRIVE_M) ARRIVE else STRAIGHT) else turnCode(turnAngle)
        val next = maxOf(0, Math.round(nextTurnM ?: remainingM).toInt())
        return Guide(rem, maxOf(0, Math.round(remainingS ?: 0.0).toInt()), next, code, turnName)
    }

    fun frames(g: Guide): List<Frame> = listOf(
        guide(g.remainingM, g.remainingS, g.code, g.nextM), street(STREET_NOW, g.street), street(STREET_NEXT, g.street), remaining(g.remainingM),
    )

    const val ARRIVE_M = 30.0

    const val PROBE_LAST = 40
    const val PROBE_STEP_MS = 4000L
    private const val SAMPLE_REMAINING = 7078
    private const val SAMPLE_SECOND = 1676
    private const val SAMPLE_FOURTH = 31

    private fun le(v: Long, n: Int) = ByteArray(n) { ((v ushr (8 * it)) and 0xff).toByte() }
}
