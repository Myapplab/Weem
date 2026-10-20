package com.kuryelog.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.tasks.DailyTask
import com.kuryelog.logic.map.ui.TaskText
import com.kuryelog.logic.map.ui.fmt
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** Konumu bulunmuş yer: [label] etiket, [point] nokta. */
class FoundPlace(val label: String, val point: GeoPoint)

/** Günlük işler ekranının dış bağları (testte sahte). */
class TaskActions(
    val onAdd: (DailyTask) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val resolve: suspend (String) -> FoundPlace? = { null },
)

/**
 * "Günlük işler" (Y20; MAP.md 13.14): açıklama, görev kartları (bekliyor mavi / tamam yeşil, sil onayı), yüzen "İş ekle" ve düzenleyici ([initialPoint] uzun basıştan gelen nokta).
 */
@Composable
fun TasksScreen(
    tasks: List<DailyTask>,
    lang: Lang,
    zone: ZoneId,
    nowMs: Long,
    actions: TaskActions,
    modifier: Modifier = Modifier,
    initialPoint: FoundPlace? = null,
    onBack: (() -> Unit)? = null,
) {
    var editing by remember { mutableStateOf(initialPoint != null) }
    var deleting by remember { mutableStateOf<DailyTask?>(null) }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) TextButton(onClick = onBack, Modifier.testTag("tasks-back")) { Text("←") }
                Text(TaskText.TITLE.get(lang), style = MaterialTheme.typography.titleLarge)
            }
            Text(TaskText.INFO.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (tasks.isEmpty()) Text(TaskText.EMPTY.get(lang), Modifier.testTag("tasks-empty"))
            for (t in tasks) {
                val done = t.completedAtMs != null
                Card(Modifier.fillMaxWidth().testTag("task-${t.id}")) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (done) "✓" else "📍", Modifier.padding(end = 10.dp), color = if (done) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontWeight = FontWeight.Bold)
                            val at = Instant.ofEpochMilli(t.dueMs).atZone(zone)
                            Text(String.format(Locale.US, "%d.%d · %02d:%02d", at.dayOfMonth, at.monthValue, at.hour, at.minute), style = MaterialTheme.typography.bodySmall)
                            Text(t.place, style = MaterialTheme.typography.bodySmall)
                            Text(if (done) TaskText.DONE.get(lang) else TaskText.PENDING.get(lang), style = MaterialTheme.typography.bodySmall, color = if (done) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { deleting = t }, Modifier.testTag("task-delete-${t.id}")) { Text(TaskText.DELETE.get(lang), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            Box(Modifier.heightIn(min = 72.dp))
        }
        Button(onClick = { editing = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("tasks-add")) { Text("＋ " + TaskText.ADD.get(lang)) }
    }
    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(TaskText.DELETE_TITLE.get(lang)) },
            confirmButton = { TextButton(onClick = { deleting = null; actions.onDelete(t.id) }, Modifier.testTag("task-delete-confirm")) { Text(TaskText.DELETE.get(lang), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(TaskText.CANCEL.get(lang)) } },
        )
    }
    if (editing) TaskEditorSheet(lang, zone, nowMs, actions, initialPoint, onDismiss = { editing = false })
}

/** Görev düzenleyici (MAP.md 13.14): ad, yer (çözümlenir, değişince nokta silinir), tarih ve saat; "Konumu onayla ve kaydet". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(lang: Lang, zone: ZoneId, nowMs: Long, actions: TaskActions, initial: FoundPlace?, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var placeText by remember { mutableStateOf(initial?.label.orEmpty()) }
    var found by remember { mutableStateOf(initial) }
    var notFound by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    var date by remember { mutableStateOf(now.toLocalDate()) }
    var time by remember { mutableStateOf(LocalTime.of(now.hour, now.minute)) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TaskText.EDITOR_TITLE.get(lang)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it; invalid = false }, Modifier.fillMaxWidth().testTag("task-title"), label = { Text(TaskText.FIELD_TITLE.get(lang)) }, singleLine = true)
                OutlinedTextField(placeText, { placeText = it; found = null; notFound = false; invalid = false }, Modifier.fillMaxWidth().testTag("task-place"), label = { Text(TaskText.FIELD_PLACE.get(lang)) }, maxLines = 2)
                OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            val r = actions.resolve(placeText)
                            found = r
                            notFound = r == null
                            busy = false
                        }
                    },
                    enabled = !busy && placeText.isNotBlank(), modifier = Modifier.testTag("task-find"),
                ) { Text(TaskText.FIND.get(lang)) }
                found?.let { Text("${it.label}\n${String.format(Locale.US, "%.5f, %.5f", it.point.lat, it.point.lon)}\n${TaskText.FOUND_NOTE.get(lang)}", Modifier.testTag("task-found"), color = MaterialTheme.colorScheme.primary) }
                if (notFound) Text(TaskText.NOT_FOUND.get(lang), color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate = true }, Modifier.testTag("task-date")) { Text(date.toString()) }
                    OutlinedButton(onClick = { pickTime = true }, Modifier.testTag("task-time")) { Text(String.format(Locale.US, "%02d:%02d", time.hour, time.minute)) }
                }
                if (invalid) Text(TaskText.NEED_TITLE_PLACE.get(lang), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val f = found
                    if (title.isBlank() || f == null) {
                        invalid = true
                    } else {
                        val due = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                        actions.onAdd(DailyTask("${nowMs}${System.nanoTime() % 1000}", title.trim(), f.label.ifBlank { placeText.trim() }, f.point, due))
                        onDismiss()
                    }
                },
                Modifier.testTag("task-save"),
            ) { Text(TaskText.SAVE.get(lang)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(TaskText.CANCEL.get(lang)) } },
    )
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate() }; pickDate = false }) { Text(TaskText.OK.get(lang)) } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(time.hour, time.minute, true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton(onClick = { time = LocalTime.of(state.hour, state.minute); pickTime = false }) { Text(TaskText.OK.get(lang)) } },
            text = { TimePicker(state) },
        )
    }
}

/** Konum uyarısı (`TaskAlertOverlay`, MAP.md 13.14): ilk uyarı tam ekran yarı saydam engel + ortada kart; "Rota Oluştur" ve "Tamam / Kapat". Uygulama kabuğunda, her sekmenin üstünde. */
@Composable
fun TaskAlertCard(alerts: List<DailyTask>, here: GeoPoint?, lang: Lang, onRoute: (DailyTask) -> Unit, onDismiss: (DailyTask) -> Unit, modifier: Modifier = Modifier) {
    val t = alerts.firstOrNull() ?: return
    Box(modifier.fillMaxSize().background(Color(0x99000000)).testTag("task-alert"), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth().padding(24.dp).heightIn(max = 600.dp)) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📍", fontSize = 48.sp)
                Text(t.title, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(t.place, fontSize = 20.sp)
                here?.let { Text(TaskText.ALERT_NEAR.fmt(lang, it.distanceTo(t.point).toInt()), style = MaterialTheme.typography.bodyMedium) }
                Button(onClick = { onRoute(t) }, Modifier.fillMaxWidth().heightIn(min = 68.dp).testTag("task-alert-route")) { Text(TaskText.ALERT_ROUTE.get(lang), fontSize = 20.sp) }
                OutlinedButton(onClick = { onDismiss(t) }, Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("task-alert-close")) { Text(TaskText.ALERT_CLOSE.get(lang)) }
                Text(TaskText.ALERT_NOTE.get(lang), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
