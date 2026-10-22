package com.kuryelog.app.map

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.floating
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.reminders.ActiveReminder
import com.kuryelog.logic.map.ui.ReminderText

/**
 * Hatırlatıcı kartı (`reminder-card`; MAP.md 13.16): haritayı kapatmayan yarı boy, tek satır (≤44 dp), uzun metin taşmaz; ✓ (`reminder-done`) tamamlar; erteleme düğmesi yok;
 * [active] null ise görünmez.
 */
@Composable
fun ReminderCard(active: ActiveReminder?, lang: Lang, onDone: () -> Unit, modifier: Modifier = Modifier) {
    if (active == null) return
    val item = active.item
    val p = palette()
    val accent = p.accentText.color()
    val shape = RoundedCornerShape(WeemRadius.tile)
    Surface(
        modifier.testTag("reminder-card").fillMaxWidth().heightIn(max = 44.dp).padding(top = 2.dp).floating(shape),
        shape = shape,
        color = p.surface.color(),
        contentColor = p.text.color(),
    ) {
        Row(Modifier.padding(start = WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(item.kind.icon(), contentDescription = null, tint = p.accent.color(), modifier = Modifier.size(18.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = accent)) { append(item.title(lang)) }
                    append(" · ")
                    append(item.message(lang))
                },
                Modifier.weight(1f).padding(start = WeemSpace.s),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = WeemType.bodyS,
            )
            WIconButton(WeemIcons.Check, ReminderText.DONE.get(lang), onDone, Modifier.testTag("reminder-done"), size = 40.dp, tone = WTone.ACCENT)
        }
    }
}
