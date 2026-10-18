package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.scooter.ScooterUiText as T

/**
 * Yüzen panelin (S7) Android'siz kuralları (A5.4; SCOOTER.md 6.3; NB `FloatingPanel.java`): ne zaman görünür, çizim temposu, ilk konum,
 * kenara kayma, "×" hedefi, okların yeri. Pencere ve dokunma `:scooter` `FloatingPanel`'de; geometri [FloatGeometry]'de.
 */
object FloatRules {
    /** WEEM'in son ekranı durduktan sonra (ekranlar arası geçişte değil). */
    const val SHOW_DELAY_MS = 400L

    /** Kare her ölçümde gelir; top yalnız gösterilen değer değiştiyse ve en sık bu aralıkla çizilir (0.18.0: 250 → 80 ms). */
    const val MIN_FRAME_MS = 80L

    /** Bağlantı bitince top saniyelik tikte kalkar. */
    const val TICK_MS = 1_000L

    /** Oklar dokunulmazsa bu süre sonra kaybolur. */
    const val ARROWS_MS = 4_000L

    /** Bar dışına dokunuş okları biraz bekleyip kaldırır (oka dokunuş yine yerini bulur). */
    const val OUTSIDE_ARROWS_MS = 300L

    const val ANIM_MS = 260L
    const val MARGIN_DP = 12
    const val CLOSE_DP = 64
    const val CLOSE_BOTTOM_DP = 170
    const val ARROW_DP = 34
    const val ARROW_GAP_DP = 2

    /** Yalnız çok hızlı fırlatma barı o yöne açar (0.22.2); sıradan hareket sürükler. */
    const val FLING_DP_PER_S = 1800
    const val FLING_WINDOW_MS = 300L

    /** Yüzen panelin tercih anahtarları (`logger`, VERI_SOZLESMESI 5.2). */
    const val PREF_ON = "floating"
    const val PREF_FIELDS = "float_fields"
    const val PREF_BALL_FIELD = "float_ball_field"
    const val PREF_X = "float_x"
    const val PREF_Y = "float_y"

    /** Top görünür mü: WEEM ekranı yok, bağlantı açık, ayar açık ve "üstte göster" izni var. */
    fun show(startedScreens: Int, active: Boolean, enabled: Boolean, permitted: Boolean): Boolean =
        startedScreens == 0 && active && enabled && permitted

    /**
     * Resim içinde resim yalnız yedek: top ayarı açık ama izin yokken (NB `pipEnabled`). `auto` = bağlantı varken otomatik giriş.
     */
    fun pip(enabled: Boolean, permitted: Boolean, supported: Boolean): Boolean = enabled && !permitted && supported

    /** Bir sonraki çizime kalan süre; ≤ 0 = hemen. */
    fun redrawWait(nowMs: Long, drawnAtMs: Long): Long = MIN_FRAME_MS - (nowMs - drawnAtMs)

    /** Kaydedilmiş konum yoksa sağ kenar, ekranın 1/3'ü; her durumda ekranın içinde. */
    fun initialX(saved: Int?, screenW: Int, ball: Int, margin: Int): Int = Math.min(saved ?: (screenW - ball - margin), screenW - ball - margin)

    fun initialY(saved: Int?, screenH: Int, ball: Int, margin: Int): Int = Math.min(saved ?: (screenH / 3), screenH - ball - margin)

    /** Bırakınca en yakın yan kenar (Messenger gibi); y ekranın içinde kalır. */
    fun settleX(ballX: Int, ball: Int, screenW: Int, margin: Int): Int = if (ballX + ball / 2 < screenW / 2) margin else screenW - ball - margin

    fun settleY(ballY: Int, ball: Int, screenH: Int, margin: Int): Int = Math.max(margin, Math.min(ballY, screenH - ball - margin))

    /** Sürüklerken topun yeri ekranın içinde. */
    fun dragTo(start: Int, delta: Float, ball: Int, screen: Int): Int = Math.max(0, Math.min(Math.round(start + delta), screen - ball))

    /** "×" hedefi: alttan [CLOSE_BOTTOM_DP], yatay ortada; merkez yakınlığı `1,1 × boy`. */
    fun nearClose(ballCx: Int, ballCy: Int, targetCx: Int, targetCy: Int, size: Int): Boolean =
        Math.hypot((ballCx - targetCx).toDouble(), (ballCy - targetCy).toDouble()) < size * 1.1

    /** Okların yeri: top ekranın sağ yarısındaysa yatay ok solda, alt üçte birindeyse dikey ok üstte. */
    data class Arrows(
        val sideX: Int, val sideY: Int, val sideAngle: Float, val side: FloatGeometry.Direction,
        val crossX: Int, val crossY: Int, val crossAngle: Float, val cross: FloatGeometry.Direction,
    )

    fun arrows(ballX: Int, ballY: Int, ball: Int, size: Int, gap: Int, screenW: Int, screenH: Int): Arrows {
        val leftSide = ballX + ball / 2 > screenW / 2
        val upSide = ballY + ball / 2 > screenH * 2 / 3
        val rx = if (leftSide) ballX - gap - size else ballX + ball + gap
        val ry = ballY + (ball - size) / 2
        val dx = ballX + (ball - size) / 2
        val dy = if (upSide) ballY - gap - size else ballY + ball + gap
        return Arrows(
            clampTo(rx, size, screenW), clampTo(ry, size, screenH), if (leftSide) 180f else 0f,
            if (leftSide) FloatGeometry.Direction.LEFT else FloatGeometry.Direction.RIGHT,
            clampTo(dx, size, screenW), clampTo(dy, size, screenH), if (upSide) 270f else 90f,
            if (upSide) FloatGeometry.Direction.UP else FloatGeometry.Direction.DOWN,
        )
    }

    private fun clampTo(v: Int, size: Int, screen: Int) = Math.max(0, Math.min(v, screen - size))

    /** Topta gösterilen değer (ayardan); yoksa ilk alan. */
    fun ballField(key: String?, fields: List<PanelField>): PanelField = PanelField.entries.firstOrNull { it.key == key } ?: fields[0]

    /**
     * WEEM ekranlarının sayacı (NB `FloatingPanel.activityStarted/Stopped`): kendi ekranlarımız topu gizler; top ancak sonuncusu da durunca
     * çıkar. Ana iş parçacığında.
     */
    class Screens {
        var started = 0
            private set

        fun started() { started++ }

        /** Sayaç asla eksiye düşmez; dönüş: kalan açık ekran. */
        fun stopped(): Int { started = Math.max(0, started - 1); return started }
    }
}

/** Scooter ayarları ekranının saf kısmı (NB `MainActivity`): boyut kaydırıcıları, alt satır metinleri, anahtar girişi. */
object ScooterSettings {
    /** Bir boyut kaydırıcısı: tercih anahtarı, başlık, birim (dp ya da %), varsayılan ve sınırlar ([FloatGeometry]). */
    class Size internal constructor(val key: String, val text: T, val dp: Boolean, val default: Int, val min: Int, val max: Int) {
        fun clamp(v: Int): Int = FloatGeometry.clamp(v, min, max)
        fun label(v: Int, lang: Lang): String = text.get(lang) + ": " + if (dp) "$v dp" else "%$v"
    }

    val SIZES: List<Size> = listOf(
        Size("float_ball_dp", T.SIZE_BALL, true, FloatGeometry.BALL_DP, FloatGeometry.BALL_MIN, FloatGeometry.BALL_MAX),
        Size("float_thick_pct", T.SIZE_THICK, false, FloatGeometry.THICK_PCT, FloatGeometry.THICK_MIN, FloatGeometry.THICK_MAX),
        Size("float_h_pct", T.SIZE_H, false, FloatGeometry.H_PCT, FloatGeometry.H_MIN, FloatGeometry.H_MAX),
        Size("float_v_pct", T.SIZE_V, false, FloatGeometry.V_PCT, FloatGeometry.V_MIN, FloatGeometry.V_MAX),
        Size("float_text_port_pct", T.SIZE_TEXT_PORT, false, FloatGeometry.TEXT_PCT, FloatGeometry.TEXT_MIN, FloatGeometry.TEXT_MAX),
        Size("float_text_land_pct", T.SIZE_TEXT_LAND, false, FloatGeometry.TEXT_PCT, FloatGeometry.TEXT_MIN, FloatGeometry.TEXT_MAX),
    )

    /** "Top 100 dp • kalınlık %125 • …"; [values] [SIZES] sırasıyla, sınırlanmış. */
    fun sizeLabel(values: List<Int>, lang: Lang): String = T.SIZE_LABEL.get(lang).format(*values.map { it.toString() }.toTypedArray())

    fun fieldsLabel(fields: List<PanelField>, lang: Lang): String = fields.joinToString(", ") { it.title(lang) }

    fun probeLabel(boards: String, lang: Lang): String = if (boards.isEmpty()) T.PROBE_ONCE.get(lang) else T.PROBE_FOUND.get(lang).format(boards)

    fun floatingLabel(permitted: Boolean, lang: Lang): String = (if (permitted) T.FLOATING_HOWTO else T.FLOATING_NEEDS).get(lang)

    /** Durum başlığının alt satırı: seçili cihazın adı (etiketin ilk parçası) ya da "seçilmedi". */
    fun selectedTitle(deviceName: String, lang: Lang): String = if (deviceName.isEmpty()) T.NOT_SELECTED.get(lang) else deviceName.split(" • ")[0]

    /** Bu cihazın anahtar durumu (NB `keyState`); `key` null = etkin anahtar yok. Açılamayan depo için [T.KEY_BROKEN]. */
    fun keyState(hasKey: Boolean, pending: Boolean, confirmed: Boolean): T = when {
        !hasKey -> if (pending) T.KEY_PENDING else T.KEY_NONE
        confirmed -> T.KEY_CONFIRMED
        else -> T.KEY_UNCONFIRMED
    }

    /** Elle girilen anahtar: boşluklar atılır, tam 16 bayt hex; değilse null. */
    fun parseKey(input: String): ByteArray? {
        val k = try { Timing.unhex(input.replace(Regex("\\s+"), "")) } catch (e: IllegalArgumentException) { return null }
        if (k.size == 16) return k
        k.fill(0)
        return null
    }

    /** Alan seçiminde en çok [PanelField.MAX]; seçilen eklenir (sona), kaldırılan çıkar. false = sınır doldu, değişmedi. */
    fun toggle(current: MutableList<PanelField>, field: PanelField, on: Boolean): Boolean {
        if (!on) { current.remove(field); return true }
        if (current.contains(field)) return true
        if (current.size >= PanelField.MAX) return false
        current.add(field)
        return true
    }

    /** Kaydederken boş seçim varsayılana döner. */
    fun fieldsToSave(current: List<PanelField>): String = PanelField.join(current.ifEmpty { PanelField.DEFAULT })
}
