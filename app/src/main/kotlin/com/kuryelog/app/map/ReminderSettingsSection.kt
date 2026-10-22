package com.kuryelog.app.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.IconBox
import com.kuryelog.app.ui.SwitchRow
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WChip
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.weemSwitchColors
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
/** Hatırlatıcı simgesi (emoji yerine; metin ve bildirim aynı kalır). */
private fun ReminderKind.icon() = when (this) {
    ReminderKind.WATER -> WeemIcons.WaterDrop
    ReminderKind.CHECK -> WeemIcons.Tune
    ReminderKind.FOOD -> WeemIcons.Restaurant
}

@Composable
fun ReminderSettingsSection(settings: ReminderSettings, lang: Lang, onChange: (ReminderKind, (ReminderItem) -> ReminderItem) -> Unit, onPreview: (ReminderKind) -> Unit, modifier: Modifier = Modifier, onEnable: () -> Unit = {}) {
    var expanded by remember { mutableStateOf<ReminderKind?>(null) }
    var how by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
        for (item in settings.items) {
            WCard(Modifier.testTag("reminder-tile-${item.kind.key}"), padding = 0.dp, spacing = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = item.enabled) { expanded = if (expanded == item.kind) null else item.kind }
                        .heightIn(min = 72.dp).padding(start = WeemSpace.l, end = WeemSpace.m, top = WeemSpace.s, bottom = WeemSpace.s),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBox(item.kind.icon(), tone = if (item.enabled) WTone.ACCENT else WTone.NEUTRAL)
                    Column(Modifier.weight(1f)) {
                        Text(item.title(lang), style = WeemType.titleS)
                        Text(reminderSummary(item, lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = item.enabled, onCheckedChange = { on -> onChange(item.kind) { it.copy(enabled = on) }; if (on) onEnable() },
                        Modifier.testTag("reminder-switch-${item.kind.key}"), colors = weemSwitchColors(),
                    )
                }
                AnimatedVisibility(item.enabled && expanded == item.kind) {
                    Column {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        ReminderDetail(item, lang, onChange, onPreview)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable { how = !how }.testTag("reminder-how").heightIn(min = 48.dp).padding(horizontal = WeemSpace.xs),
            horizontalArrangement = Arrangement.spacedBy(WeemSpace.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(WeemIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(ReminderText.HOW.get(lang), style = WeemType.labelL, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Icon(if (how) WeemIcons.ExpandLess else WeemIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        if (how) Text(ReminderText.HOW_TEXT.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDetail(item: ReminderItem, lang: Lang, onChange: (ReminderKind, (ReminderItem) -> ReminderItem) -> Unit, onPreview: (ReminderKind) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = WeemSpace.m), verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
        Row(Modifier.padding(horizontal = WeemSpace.l), horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            WChip(ReminderText.MODE_INTERVAL.get(lang), !item.useTimes, { onChange(item.kind) { it.copy(useTimes = false) } }, Modifier.testTag("reminder-mode-interval-${item.kind.key}"))
            WChip(ReminderText.MODE_TIMES.get(lang), item.useTimes, { onChange(item.kind) { it.copy(useTimes = true) } }, Modifier.testTag("reminder-mode-times-${item.kind.key}"))
        }
        if (!item.useTimes) {
            Row(Modifier.padding(horizontal = WeemSpace.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                Text(ReminderText.EVERY_LABEL.get(lang), style = WeemType.bodyM, modifier = Modifier.weight(1f))
                WIconButton(WeemIcons.Remove, "−5", { onChange(item.kind) { it.copy(intervalMin = (it.intervalMin - 5).coerceAtLeast(10)) } },
                    Modifier.testTag("reminder-minus-${item.kind.key}"), tinted = true)
                Text(ReminderText.MINUTES.fmt(lang, item.intervalMin), style = WeemType.numS)
                WIconButton(WeemIcons.Add, "+5", { onChange(item.kind) { it.copy(intervalMin = (it.intervalMin + 5).coerceAtMost(240)) } },
                    Modifier.testTag("reminder-plus-${item.kind.key}"), tinted = true)
            }
        } else {
            FlowRow(Modifier.padding(horizontal = WeemSpace.l), horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                for (m in item.times) WChip(fmtMinuteOfDay(m), false, { onChange(item.kind) { it.copy(times = it.times - m) } }, icon = WeemIcons.Close)
            }
            WButton(ReminderText.ADD_TIME.get(lang), { picking = true }, Modifier.padding(horizontal = WeemSpace.l).testTag("reminder-add-time-${item.kind.key}"),
                kind = BtnKind.TONAL, icon = WeemIcons.Add)
        }
        SwitchRow(ReminderText.ONLY_SHIFT.get(lang), item.onlyShift, { onChange(item.kind) { it.copy(onlyShift = !it.onlyShift) } })
        Text(
            item.message(lang), style = WeemType.bodyS, maxLines = 2,
            modifier = Modifier.padding(horizontal = WeemSpace.l).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(WeemRadius.tile)).padding(WeemSpace.m),
        )
        Row(Modifier.padding(horizontal = WeemSpace.s), horizontalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
            WButton(ReminderText.EDIT_TEXT.get(lang), { editing = true }, Modifier.testTag("reminder-edit-${item.kind.key}"), kind = BtnKind.TEXT, icon = WeemIcons.Edit)
            WButton(ReminderText.TRY.get(lang), { onPreview(item.kind) }, Modifier.testTag("reminder-try-${item.kind.key}"), kind = BtnKind.TEXT, icon = WeemIcons.Notifications)
        }
    }
    if (picking) {
        val state = rememberTimePickerState(15, 15, true)
        ConfirmDialog(
            title = ReminderText.ADD_TIME.get(lang), confirm = ReminderText.SAVE.get(lang), dismiss = ReminderText.CANCEL.get(lang), onDismiss = { picking = false },
            onConfirm = {
                val m = state.hour * 60 + state.minute
                picking = false
                onChange(item.kind) { if (m in it.times) it else it.copy(times = (it.times + m).sorted()) }
            },
        ) { TimePicker(state) }
    }
    if (editing) {
        var text by remember { mutableStateOf(item.text) }
        ConfirmDialog(
            title = ReminderText.DIALOG_TITLE.fmt(lang, item.emoji, item.title(lang)), confirm = ReminderText.SAVE.get(lang), dismiss = ReminderText.CANCEL.get(lang),
            confirmTag = "reminder-text-save", onDismiss = { editing = false },
            onConfirm = { editing = false; onChange(item.kind) { it.copy(text = text.trim()) } },
        ) {
            OutlinedTextField(
                text, { text = it }, Modifier.fillMaxWidth().testTag("reminder-text-field"), placeholder = { Text(ReminderItem.defaultText(item.kind, lang)) }, maxLines = 3,
                shape = RoundedCornerShape(WeemRadius.tile),
            )
        }
    }
}
