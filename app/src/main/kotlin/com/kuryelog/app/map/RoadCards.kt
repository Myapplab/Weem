package com.kuryelog.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.roads.QualityAsk
import com.kuryelog.logic.map.roads.QualityPrompt
import com.kuryelog.logic.map.roads.QualityTone
import com.kuryelog.logic.map.roads.RoadsText
import com.kuryelog.logic.map.roads.WrongWayCardText

internal val GOOD_GREEN = Color(0xFF2EBD59)
internal val FAIR_ORANGE = Color(0xFFFF9800)
internal val BAD_RED = Color(0xFFE53935)

internal fun QualityTone.color(): Color = when (this) {
    QualityTone.GOOD -> GOOD_GREEN
    QualityTone.FAIR -> FAIR_ORANGE
    QualityTone.BAD -> BAD_RED
}

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
    if (view.question) {
        Surface(modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("ww-card"), shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp, shadowElevation = 6.dp) {
            Column(Modifier.padding(14.dp)) {
                Text(WrongWayCardText.title(lang), color = FAIR_ORANGE, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(WrongWayCardText.subtitle(lang, view.name, view.incidents, view.leftS), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onAnswer(true) }, modifier = Modifier.weight(1f).height(52.dp).testTag("ww-yes"),
                        colors = ButtonDefaults.buttonColors(containerColor = BAD_RED, contentColor = Color.White),
                    ) { Text(WrongWayCardText.yes(lang), fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.weight(1f).height(52.dp).testTag("ww-no")) {
                        Text(WrongWayCardText.no(lang), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }
    val live = view.liveName ?: return
    Surface(modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("ww-live"), shape = RoundedCornerShape(14.dp), tonalElevation = 4.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(WrongWayCardText.live(lang, live, view.liveRatioPct), Modifier.weight(1f), color = GOOD_GREEN, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onReport, modifier = Modifier.testTag("ww-report")) { Text(WrongWayCardText.report(lang), color = BAD_RED) }
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
    Surface(modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("quality-card"), shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp, shadowElevation = 6.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(QualityPrompt.title(lang), color = c, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(ask.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("%${ask.pct}", color = c, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, modifier = Modifier.testTag("quality-pct"))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (p in QualityPrompt.CHIPS) FilterChip(selected = ask.pct == p, onClick = { onSet(p) }, label = { Text("%$p", fontSize = 12.sp) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text(QualityPrompt.cancelLabel(lang)) }
                Button(onClick = onSave, modifier = Modifier.testTag("quality-save")) { Text(QualityPrompt.saveLabel(lang)) }
            }
            Text(QualityPrompt.hint(lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
