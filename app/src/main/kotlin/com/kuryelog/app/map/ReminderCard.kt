package com.kuryelog.app.map

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier.testTag("reminder-card").fillMaxWidth().heightIn(max = 44.dp).padding(top = 2.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent),
    ) {
        Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.emoji, fontSize = 14.sp)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Black, color = accent)) { append(item.title(lang)) }
                    append(" · ")
                    append(item.message(lang))
                },
                Modifier.weight(1f).padding(start = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.5.sp,
            )
            IconButton(onClick = onDone, Modifier.size(width = 36.dp, height = 32.dp).testTag("reminder-done").semantics { contentDescription = ReminderText.DONE.get(lang) }) {
                Text("✓", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
