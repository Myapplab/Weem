package com.kuryelog.app.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.logs.RideLogEntry
import com.kuryelog.logic.map.logs.RideLogStore
import com.kuryelog.logic.map.stats.DurationText
import com.kuryelog.logic.map.ui.RideLogText
import com.kuryelog.logic.map.ui.fmt
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Sürüş kayıtları ekranının dış bağları (testte sahte): kayıt silme/kaydet/paylaş, YolGo içe aktarma (A7.1) ve ayar. */
class RideLogActions(
    val onLoggingChange: (Boolean) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onSave: (RideLogEntry?, day: String?, all: Boolean) -> Unit = { _, _, _ -> },
    val onShare: (RideLogEntry?, day: String?, all: Boolean) -> Unit = { _, _, _ -> },
    val onDeleteOne: (RideLogEntry) -> Unit = {},
    val onDeleteDay: (String) -> Unit = {},
    val onImport: () -> Unit = {},
)

/**
 * Sürüş kayıtları (Y18; MAP.md 12.10): durum yazısı, yenile, tümünü kaydet/paylaş; "Sürüş kaydı" anahtarı ve açıklama; günler (ilk gün açık) ve kayıt satırları.
 * [entries] listeleme sırasında (null = okunuyor ya da okunamadı; okunamadıysa çağıran [error]'a "Kayıtlar okunamadı" verir); [active] etkin kayıt mı (silinemez); [busy] iş sürerken ilerleme.
 */
@Composable
fun RideLogsScreen(
    entries: List<RideLogEntry>?,
    logging: Boolean,
    dayActive: Boolean,
    lang: Lang,
    zone: ZoneId,
    active: (RideLogEntry) -> Boolean,
    actions: RideLogActions,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    error: String? = null,
    message: String? = null,
    onBack: (() -> Unit)? = null,
) {
    val groups = entries?.let { RideLogStore.groupByDay(it) }
    val open = remember(groups?.keys?.firstOrNull()) { mutableStateListOf<String>().also { l -> groups?.keys?.firstOrNull()?.let { l.add(it) } } }
    var menu by remember { mutableStateOf(false) }
    var pendingDay by remember { mutableStateOf<String?>(null) }
    var pendingOne by remember { mutableStateOf<RideLogEntry?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextButton(onClick = onBack, Modifier.testTag("ridelogs-back")) { Text("←") }
            Column(Modifier.weight(1f)) {
                Text(RideLogText.TITLE.get(lang), style = MaterialTheme.typography.titleLarge)
                RideLogStatusText(logging, dayActive, lang)
            }
            IconButton(onClick = actions.onRefresh, Modifier.testTag("ridelogs-refresh").semantics { contentDescription = RideLogText.REFRESH.get(lang) }) { Text("↻") }
            IconButton(onClick = { menu = true }, Modifier.testTag("ridelogs-menu").semantics { contentDescription = RideLogText.SAVE_ALL_HINT.get(lang) }) { Text("⤓") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(RideLogText.SAVE_ALL.get(lang)) }, onClick = { menu = false; actions.onSave(null, null, true) })
                DropdownMenuItem(text = { Text(RideLogText.SHARE_ALL.get(lang)) }, onClick = { menu = false; actions.onShare(null, null, true) })
                DropdownMenuItem(text = { Text(RideLogText.IMPORT.get(lang)) }, onClick = { menu = false; actions.onImport() }, modifier = Modifier.testTag("ridelogs-import"))
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.fillMaxWidth().clickable { actions.onLoggingChange(!logging) }.testTag("ridelogs-switch"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(RideLogText.SWITCH_TITLE.get(lang), style = MaterialTheme.typography.bodyLarge)
                Text(RideLogText.SWITCH_SUB.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = logging, onCheckedChange = null)
        }
        Text(RideLogText.INFO.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            groups == null -> Unit
            groups.isEmpty() -> Text(RideLogText.EMPTY.get(lang), Modifier.testTag("ridelogs-empty"))
            else -> for ((day, list) in groups) {
                DayCard(day, list, lang, zone, day in open, active, actions, { if (day in open) open.remove(day) else open.add(day) }, { pendingDay = day }, { pendingOne = it })
            }
        }
    }
    pendingDay?.let { day ->
        val dayActive2 = entries.orEmpty().any { it.day == day && active(it) }
        AlertDialog(
            onDismissRequest = { pendingDay = null },
            title = { Text(RideLogText.DELETE_DAY_TITLE.fmt(lang, ddmmyyyy(day))) },
            text = { Text((if (dayActive2) RideLogText.DELETE_DAY_ACTIVE.get(lang) else "") + RideLogText.DELETE_DAY_BODY.get(lang)) },
            confirmButton = { TextButton(onClick = { pendingDay = null; actions.onDeleteDay(day) }, Modifier.testTag("ridelogs-delete-day-confirm")) { Text(RideLogText.DELETE_DAY_CONFIRM.get(lang), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDay = null }) { Text(RideLogText.CANCEL.get(lang)) } },
        )
    }
    pendingOne?.let { e ->
        AlertDialog(
            onDismissRequest = { pendingOne = null },
            title = { Text(RideLogText.DELETE_ONE_TITLE.get(lang)) },
            text = { Text(RideLogText.DELETE_ONE_BODY.get(lang)) },
            confirmButton = { TextButton(onClick = { pendingOne = null; actions.onDeleteOne(e) }, Modifier.testTag("ridelogs-delete-one-confirm")) { Text(RideLogText.DELETE_ONE_CONFIRM.get(lang), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingOne = null }) { Text(RideLogText.CANCEL.get(lang)) } },
        )
    }
}

/** `YYYY-MM-DD` → `GG.AA.YYYY`. */
internal fun ddmmyyyy(day: String): String = day.split('-').let { if (it.size == 3) "${it[2]}.${it[1]}.${it[0]}" else day }

@Composable
private fun DayCard(
    day: String, list: List<RideLogEntry>, lang: Lang, zone: ZoneId, expanded: Boolean, active: (RideLogEntry) -> Boolean, actions: RideLogActions,
    onToggle: () -> Unit, onDeleteDay: () -> Unit, onDeleteOne: (RideLogEntry) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("ridelogs-day-$day")) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = onToggle).testTag("ridelogs-day-header-$day")) {
                    Text(ddmmyyyy(day), fontWeight = FontWeight.SemiBold)
                    Text(RideLogText.DAY_SUBTITLE.fmt(lang, list.count { it.kind == "ride" }, list.size), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { menu = true }, Modifier.testTag("ridelogs-day-menu-$day").semantics { contentDescription = RideLogText.DAY_MENU_HINT.get(lang) }) { Text("⋮") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(RideLogText.DAY_SAVE.get(lang)) }, onClick = { menu = false; actions.onSave(null, day, false) })
                    DropdownMenuItem(text = { Text(RideLogText.DAY_SHARE.get(lang)) }, onClick = { menu = false; actions.onShare(null, day, false) })
                    DropdownMenuItem(text = { Text(RideLogText.DAY_DELETE.get(lang), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDeleteDay() }, modifier = Modifier.testTag("ridelogs-day-delete-$day"))
                }
            }
            if (expanded) for (e in list) EntryRow(e, lang, zone, active(e), actions, onDeleteOne)
        }
    }
}

@Composable
private fun EntryRow(e: RideLogEntry, lang: Lang, zone: ZoneId, isActive: Boolean, actions: RideLogActions, onDeleteOne: (RideLogEntry) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val clock = e.startedAtMs?.let { String.format(Locale.US, "%s:%02d", DurationText.clock(it, zone), Instant.ofEpochMilli(it).atZone(zone).second) }
    val title = when (e.kind) {
        "route" -> RideLogText.KIND_ROUTE.get(lang) + clock?.let { " · $it" }.orEmpty()
        "ride" -> RideLogText.KIND_RIDE.get(lang) + clock?.let { " · $it" }.orEmpty()
        "report" -> RideLogText.KIND_REPORT.get(lang) + clock?.let { " · $it" }.orEmpty()
        else -> RideLogText.KIND_DAY.get(lang)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("ridelogs-entry-${e.name}"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(String.format(Locale.US, "%.2f MB", e.sizeBytes / 1_048_576.0) + if (isActive) RideLogText.ACTIVE_SUFFIX.get(lang) else "", style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = { menu = true }, Modifier.testTag("ridelogs-entry-menu-${e.name}")) { Text("⋮") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(RideLogText.SAVE.get(lang)) }, onClick = { menu = false; actions.onSave(e, null, false) })
            DropdownMenuItem(text = { Text(RideLogText.SHARE.get(lang)) }, onClick = { menu = false; actions.onShare(e, null, false) })
            if (!isActive) DropdownMenuItem(text = { Text(RideLogText.DELETE_ONE.get(lang), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDeleteOne(e) }, modifier = Modifier.testTag("ridelogs-entry-delete-${e.name}"))
        }
    }
}
