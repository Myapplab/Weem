package com.kuryelog.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kuryelog.app.R

/**
 * Inter (A10.4d K2, S36): uygulamaya gömülü alt küme — Latin, Latin genişletilmiş, ə/Ə, ₼, oklar ve sık noktalama; 400–800 kalınlık
 * (`res/font`, her biri ≈ 60 KB). Lisans: SIL Open Font License 1.1 (yazı tipi dosyasının ad tablosunda; Inter Project Authors).
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

/** Yazı ölçeği (`docs/TASARIM.md` 5): hiçbir yazı 12 sp altında değil; değişen sayılar sabit genişlikli rakam (`tnum`). */
object WeemType {
    private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0, tabular: Boolean = false) = TextStyle(
        fontFamily = Inter, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em,
        fontFeatureSettings = if (tabular) "tnum" else null,
    )

    /** Scooter hızı. */
    val displayXL = style(88, 92, FontWeight.ExtraBold, -0.04, tabular = true)
    /** Net kazanç. */
    val displayM = style(40, 44, FontWeight.ExtraBold, -0.025, tabular = true)
    /** Sekme başlığı. */
    val headline = style(28, 34, FontWeight.Bold, -0.015)
    /** Alt ekran, sayfa ve pencere başlığı; kapı notu. */
    val titleL = style(22, 28, FontWeight.Bold, -0.01)
    /** Kart başlığı, durak adresi, aşama. */
    val titleM = style(18, 24, FontWeight.SemiBold, -0.005)
    /** Liste satırı başlığı. */
    val titleS = style(16, 22, FontWeight.SemiBold)
    val bodyL = style(16, 24, FontWeight.Normal)
    val bodyM = style(15, 22, FontWeight.Normal)
    val bodyS = style(13, 18, FontWeight.Normal)
    /** Düğme. */
    val labelL = style(15, 20, FontWeight.SemiBold)
    /** Çip, segment, durum hapı. */
    val labelM = style(13, 16, FontWeight.SemiBold)
    /** Alt menü etiketi, karo etiketi. */
    val labelS = style(12, 16, FontWeight.SemiBold)
    /** Karo ve gösterge değeri. */
    val numM = style(22, 26, FontWeight.Bold, -0.01, tabular = true)
    /** Satırdaki tutar ve süre. */
    val numS = style(16, 20, FontWeight.SemiBold, tabular = true)

    /** Material yuvaları: eski ekranlar da (`typography.headlineMedium` …) aynı ölçeği kullanır. */
    val typography = Typography(
        displayLarge = displayXL,
        displayMedium = style(56, 60, FontWeight.ExtraBold, -0.03, tabular = true),
        displaySmall = displayM,
        headlineLarge = style(32, 38, FontWeight.Bold, -0.02),
        headlineMedium = headline,
        headlineSmall = style(24, 30, FontWeight.Bold, -0.01),
        titleLarge = titleL,
        titleMedium = titleM,
        titleSmall = titleS,
        bodyLarge = bodyL,
        bodyMedium = bodyM,
        bodySmall = bodyS,
        labelLarge = labelL,
        labelMedium = labelM,
        labelSmall = labelS,
    )
}
