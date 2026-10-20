package com.kuryelog.app.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.reminders.ReminderItem
import com.kuryelog.logic.map.reminders.ReminderKind
import com.kuryelog.logic.map.reminders.ReminderSettings
import com.kuryelog.logic.map.reminders.fmtMinuteOfDay
import com.kuryelog.logic.map.ui.ReminderText
import com.kuryelog.logic.map.ui.fmt

/** Satır özeti (MAP.md 13.16): "Kapalı" / "saat SS:DD, …" ya da "saat seçilmedi" / "her N dk" + " · gün içinde". */
internal fun reminderSummary(it: ReminderItem, lang: Lang): String {
    if (!it.enabled) return ReminderText.OFF.get(lang)
    val base = if (it.useTimes) {
        if (it.times.isEmpty()) ReminderText.NO_TIMES.get(lang) else ReminderText.AT_TIMES.fmt(lang, it.times.joinToString(", ") { m -> fmtMinuteOfDay(m) })
    } else {
        ReminderText.EVERY.fmt(lang, it.intervalMin)
    }
    return base + if (it.onlyShift) ReminderText.DURING_SHIFT.get(lang) else ""
}

/**
 * Harita ayarları → "Hatırlatıcılar" (Y20; MAP.md 13.16): her tür bir satır (`reminder-tile-<tür>`): özet, açma anahtarı; açık satıra dokununca ayrıntı (aralık/saat, gün içinde,
 * metin, "Dene"); altta "Nasıl çalışır". Değişiklikler [onChange] ile (sayaç kuralları denetleyicide).
 */
@Composable
fun ReminderSettingsSection(settings: ReminderSettings, lang: Lang, onChange: (ReminderKind, (ReminderItem) -> ReminderItem) -> Unit, onPreview: (ReminderKind) -> Unit, modifier: Modifier = Modifier, onEnable: () -> Unit = {}) {
    var expanded by remember { mutableStateOf<ReminderKind?>(null) }
    var how by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(ReminderText.SECTION.get(lang), style = MaterialTheme.typography.titleMedium)
        for (item in settings.items) {
            Card(Modifier.fillMaxWidth().testTag("reminder-tile-${item.kind.key}")) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { if (item.enabled) expanded = if (expanded == item.kind) null else item.kind }, verticalAlignment = Alignment.CenterVertically) {
                        Text(item.emoji, Modifier.padding(end = 10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title(lang), fontWeight = if (item.enabled) FontWeight.Bold else FontWeight.Normal, color = if (item.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(reminderSummary(item, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = item.enabled, onCheckedChange = { on -> onChange(item.kind) { it.copy(enabled = on) }; if (on) onEnable() }, Modifier.testTag("reminder-switch-${item.kind.key}"))
                    }
                    if (item.enabled && expanded == item.kind) ReminderDetail(item, lang, onChange, onPreview)
                }
            }
        }
        TextButton(onClick = { how = !how }, Modifier.testTag("reminder-how")) { Text((if (how) "▾ " else "▸ ") + ReminderText.HOW.get(lang)) }
        if (how) Text(ReminderText.HOW_TEXT.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDetail(item: ReminderItem, lang: Lang, onChange: (ReminderKind, (ReminderItem) -> ReminderItem) -> Unit, onPreview: (ReminderKind) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !item.useTimes, onClick = { onChange(item.kind) { it.copy(useTimes = false) } }, label = { Text(ReminderText.MODE_INTERVAL.get(lang)) }, modifier = Modifier.testTag("reminder-mode-interval-${item.kind.key}"))
            FilterChip(selected = item.useTimes, onClick = { onChange(item.kind) { it.copy(useTimes = true) } }, label = { Text(ReminderText.MODE_TIMES.get(lang)) }, modifier = Modifier.testTag("reminder-mode-times-${item.kind.key}"))
        }
        if (!item.useTimes) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ReminderText.EVERY_LABEL.get(lang))
                OutlinedButton(onClick = { onChange(item.kind) { it.copy(intervalMin = (it.intervalMin - 5).coerceAtLeast(10)) } }, Modifier.testTag("reminder-minus-${item.kind.key}")) { Text("−") }
                Text(ReminderText.MINUTES.fmt(lang, item.intervalMin), fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { onChange(item.kind) { it.copy(intervalMin = (it.intervalMin + 5).coerceAtMost(240)) } }, Modifier.testTag("reminder-plus-${item.kind.key}")) { Text("+") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (m in item.times) FilterChip(selected = true, onClick = { onChange(item.kind) { it.copy(times = it.times - m) } }, label = { Text(fmtMinuteOfDay(m) + " ✕") })
            }
            OutlinedButton(onClick = { picking = true }, Modifier.testTag("reminder-add-time-${item.kind.key}")) { Text(ReminderText.ADD_TIME.get(lang)) }
        }
        Row(Modifier.fillMaxWidth().clickable { onChange(item.kind) { it.copy(onlyShift = !it.onlyShift) } }, verticalAlignment = Alignment.CenterVertically) {
            Text(ReminderText.ONLY_SHIFT.get(lang), Modifier.weight(1f))
            Switch(checked = item.onlyShift, onCheckedChange = null)
        }
        Text(item.message(lang), style = MaterialTheme.typography.bodySmall, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { editing = true }, Modifier.testTag("reminder-edit-${item.kind.key}")) { Text(ReminderText.EDIT_TEXT.get(lang)) }
            TextButton(onClick = { onPreview(item.kind) }, Modifier.testTag("reminder-try-${item.kind.key}")) { Text(ReminderText.TRY.get(lang)) }
        }
    }
    if (picking) {
        val state = rememberTimePickerState(15, 15, true)
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    val m = state.hour * 60 + state.minute
                    picking = false
                    onChange(item.kind) { if (m in it.times) it else it.copy(times = (it.times + m).sorted()) }
                }) { Text(ReminderText.SAVE.get(lang)) }
            },
            text = { TimePicker(state) },
        )
    }
    if (editing) {
        var text by remember { mutableStateOf(item.text) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(ReminderText.DIALOG_TITLE.fmt(lang, item.emoji, item.title(lang))) },
            text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().testTag("reminder-text-field"), placeholder = { Text(ReminderItem.defaultText(item.kind, lang)) }, maxLines = 3) },
            confirmButton = { TextButton(onClick = { editing = false; onChange(item.kind) { it.copy(text = text.trim()) } }, Modifier.testTag("reminder-text-save")) { Text(ReminderText.SAVE.get(lang)) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(ReminderText.CANCEL.get(lang)) } },
        )
    }
}
