package com.kuryelog.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.IconBox
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WChip
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.floating
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.app.Palette
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.roads.QualityAsk
import com.kuryelog.logic.map.roads.QualityPrompt
import com.kuryelog.logic.map.roads.QualityTone
import com.kuryelog.logic.map.roads.RoadsText
import com.kuryelog.logic.map.roads.WrongWayCardText

/** Kalite tonu (iyi/orta/kötü) paletten: tamam, uyarı, tehlike (A10.4d; eskiden elle yazılmış yeşil/turuncu/kırmızı). */
internal fun QualityTone.ink(p: Palette): Long = when (this) {
    QualityTone.GOOD -> p.positive
    QualityTone.FAIR -> p.warn
    QualityTone.BAD -> p.danger
}

@Composable
@ReadOnlyComposable
internal fun QualityTone.color(): Color = Color(ink(palette()))

/** Ters yön kartının görünümü: açık soru ([question]) ya da süren onaylı geçiş şeridi ([liveName]). */
data class WrongWayView(
    val question: Boolean = false,
    val name: String = "",
    val incidents: Int = 0,
    val leftS: Int = 0,
    val liveName: String? = null,
    val liveRatioPct: Int? = null,
)

/**
 * "Ters yönde sorun yaşadın mı?" kartı (MAP.md 9.4.3; `WrongWayCard`): soru açıkken başlık (turuncu), alt satır, "Evet, sorun" (kırmızı, 52 dp) ve "Hayır";
 * soru yokken süren onaylı geçişte ince şerit + "Sorun yaşadım". Cevap ekrana bakmadan ses tuşuyla da verilir.
 */
@Composable
fun WrongWayCard(view: WrongWayView, lang: Lang, onAnswer: (Boolean) -> Unit, onReport: () -> Unit, modifier: Modifier = Modifier) {
    val p = palette()
    if (view.question) {
        val shape = RoundedCornerShape(WeemRadius.panel)
        Surface(modifier.fillMaxWidth().padding(vertical = WeemSpace.xs).floating(shape).testTag("ww-card"), shape = shape, color = p.surface.color(), contentColor = p.text.color()) {
            Column(Modifier.padding(WeemSpace.l), verticalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
                    IconBox(WeemIcons.Block, tone = WTone.WARN)
                    Column(Modifier.weight(1f)) {
                        Text(WrongWayCardText.title(lang), color = p.warn.color(), style = WeemType.titleM)
                        Text(WrongWayCardText.subtitle(lang, view.name, view.incidents, view.leftS), style = WeemType.bodyS, color = p.muted.color())
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = WeemSpace.xs), horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                    WButton(WrongWayCardText.yes(lang), { onAnswer(true) }, Modifier.weight(1f).testTag("ww-yes"), kind = BtnKind.DANGER_FILLED, ride = true)
                    WButton(WrongWayCardText.no(lang), { onAnswer(false) }, Modifier.weight(1f).testTag("ww-no"), kind = BtnKind.OUTLINE, ride = true)
                }
            }
        }
        return
    }
    val live = view.liveName ?: return
    val shape = RoundedCornerShape(WeemRadius.card)
    Surface(modifier.fillMaxWidth().padding(vertical = WeemSpace.xs).floating(shape).testTag("ww-live"), shape = shape, color = p.surface.color(), contentColor = p.text.color()) {
        Row(Modifier.padding(start = WeemSpace.m, end = WeemSpace.xs, top = WeemSpace.xs, bottom = WeemSpace.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(WeemIcons.CheckCircleFilled, contentDescription = null, tint = p.positive.color(), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(WeemSpace.s))
            Text(WrongWayCardText.live(lang, live, view.liveRatioPct), Modifier.weight(1f), color = p.positive.color(), style = WeemType.labelL)
            WButton(WrongWayCardText.report(lang), onReport, Modifier.testTag("ww-report"), kind = BtnKind.DANGER, icon = WeemIcons.Flag)
        }
    }
}

/**
 * "Bu yolun kalitesi?" kartı (MAP.md 9.4.1; `QualityPromptCard`): başlık (kalite rengi), sokak adı, büyük %, çipler %20…%100, "Kaydet" / "Vazgeç", ipucu.
 * Ses+ / Ses− ±10; 6 sn dokunulmazsa kendiliğinden kaydedilir (çalışma zamanı).
 */
@Composable
fun QualityPromptCard(ask: QualityAsk, lang: Lang, onSet: (Int) -> Unit, onSave: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val c = RoadsText.tone(ask.pct / 100.0).color()
    val p = palette()
    val shape = RoundedCornerShape(WeemRadius.panel)
    Surface(modifier.fillMaxWidth().padding(vertical = WeemSpace.xs).floating(shape).testTag("quality-card"), shape = shape, color = p.surface.color(), contentColor = p.text.color()) {
        Column(Modifier.padding(WeemSpace.l), verticalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(QualityPrompt.title(lang), color = c, style = WeemType.titleM)
                    Text(ask.name, style = WeemType.bodyS, color = p.muted.color())
                }
                Text("%${ask.pct}", color = c, style = WeemType.displayM, modifier = Modifier.testTag("quality-pct"))
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (v in QualityPrompt.CHIPS) WChip("%$v", ask.pct == v, { onSet(v) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WeemSpace.s, Alignment.End)) {
                WButton(QualityPrompt.cancelLabel(lang), onCancel, kind = BtnKind.TEXT)
                WButton(QualityPrompt.saveLabel(lang), onSave, Modifier.testTag("quality-save"), icon = WeemIcons.Check)
            }
            Text(QualityPrompt.hint(lang), style = WeemType.bodyS, color = p.muted.color())
        }
    }
}
