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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.EmptyState
import com.kuryelog.app.ui.IconBox
import com.kuryelog.app.ui.StatusPill
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WSheet
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemTopBar
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.floating
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.tasks.DailyTask
import com.kuryelog.logic.map.ui.TaskText
import com.kuryelog.logic.map.ui.fmt
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch

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
    val p = palette()
    Box(modifier.fillMaxSize().background(p.bg.color())) {
        Column(Modifier.fillMaxSize()) {
            WeemTopBar(TaskText.TITLE.get(lang), onBack, Str.BACK.get(lang), backTag = "tasks-back")
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = WeemSpace.l, end = WeemSpace.l),
                verticalArrangement = Arrangement.spacedBy(WeemSpace.m),
            ) {
                Text(TaskText.INFO.get(lang), style = WeemType.bodyS, color = p.muted.color())
                if (tasks.isEmpty()) EmptyState(TaskText.EMPTY.get(lang), Modifier.testTag("tasks-empty"), icon = WeemIcons.TaskAlt)
                for (t in tasks) {
                    val done = t.completedAtMs != null
                    WCard(Modifier.testTag("task-${t.id}")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
                            IconBox(if (done) WeemIcons.CheckCircleFilled else WeemIcons.LocationOnFilled, tone = if (done) WTone.POSITIVE else WTone.ACCENT)
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = WeemType.titleS)
                                val at = Instant.ofEpochMilli(t.dueMs).atZone(zone)
                                Text(String.format(Locale.US, "%d.%d · %02d:%02d", at.dayOfMonth, at.monthValue, at.hour, at.minute), style = WeemType.numS)
                                Text(t.place, style = WeemType.bodyS, color = p.muted.color())
                            }
                            WIconButton(WeemIcons.Delete, TaskText.DELETE.get(lang), { deleting = t }, Modifier.testTag("task-delete-${t.id}"), tone = WTone.DANGER)
                        }
                        StatusPill(if (done) TaskText.DONE.get(lang) else TaskText.PENDING.get(lang), if (done) WTone.POSITIVE else WTone.ACCENT)
                    }
                }
                Box(Modifier.heightIn(min = 88.dp))
            }
        }
        WButton(
            TaskText.ADD.get(lang), { editing = true }, Modifier.align(Alignment.BottomEnd).padding(WeemSpace.l).floating(CircleShape).testTag("tasks-add"),
            icon = WeemIcons.Add, ride = true,
        )
    }
    deleting?.let { t ->
        ConfirmDialog(
            title = TaskText.DELETE_TITLE.get(lang), confirm = TaskText.DELETE.get(lang), dismiss = TaskText.CANCEL.get(lang), danger = true, confirmTag = "task-delete-confirm",
            onConfirm = { deleting = null; actions.onDelete(t.id) }, onDismiss = { deleting = null },
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
    val field = RoundedCornerShape(WeemRadius.tile)
    // K4: uzun düzenleyici alttan açılan sayfada; eylemler en altta.
    WSheet(onDismiss = onDismiss, title = TaskText.EDITOR_TITLE.get(lang)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
            OutlinedTextField(title, { title = it; invalid = false }, Modifier.fillMaxWidth().testTag("task-title"), label = { Text(TaskText.FIELD_TITLE.get(lang)) }, singleLine = true, shape = field)
            OutlinedTextField(
                placeText, { placeText = it; found = null; notFound = false; invalid = false }, Modifier.fillMaxWidth().testTag("task-place"),
                label = { Text(TaskText.FIELD_PLACE.get(lang)) }, maxLines = 2, shape = field,
            )
            WButton(
                TaskText.FIND.get(lang),
                {
                    busy = true
                    scope.launch {
                        val r = actions.resolve(placeText)
                        found = r
                        notFound = r == null
                        busy = false
                    }
                },
                Modifier.testTag("task-find"), kind = BtnKind.TONAL, icon = WeemIcons.Search, enabled = !busy && placeText.isNotBlank(), busy = busy,
            )
            found?.let {
                Banner(
                    "${it.label}\n${String.format(Locale.US, "%.5f, %.5f", it.point.lat, it.point.lon)}\n${TaskText.FOUND_NOTE.get(lang)}",
                    Modifier.testTag("task-found"), tone = WTone.POSITIVE, icon = WeemIcons.LocationOnFilled,
                )
            }
            if (notFound) Banner(TaskText.NOT_FOUND.get(lang), tone = WTone.DANGER)
            Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                WButton(date.toString(), { pickDate = true }, Modifier.weight(1f).testTag("task-date"), kind = BtnKind.OUTLINE, icon = WeemIcons.Schedule)
                WButton(String.format(Locale.US, "%02d:%02d", time.hour, time.minute), { pickTime = true }, Modifier.weight(1f).testTag("task-time"), kind = BtnKind.OUTLINE, icon = WeemIcons.Timer)
            }
            if (invalid) Banner(TaskText.NEED_TITLE_PLACE.get(lang), tone = WTone.DANGER)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            WButton(TaskText.CANCEL.get(lang), onDismiss, Modifier.weight(1f), kind = BtnKind.OUTLINE)
            WButton(
                TaskText.SAVE.get(lang),
                {
                    val f = found
                    if (title.isBlank() || f == null) {
                        invalid = true
                    } else {
                        val due = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                        actions.onAdd(DailyTask("${nowMs}${System.nanoTime() % 1000}", title.trim(), f.label.ifBlank { placeText.trim() }, f.point, due))
                        onDismiss()
                    }
                },
                Modifier.weight(1f).testTag("task-save"), icon = WeemIcons.Check,
            )
        }
    }
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
    val p = palette()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .6f)).testTag("task-alert"), contentAlignment = Alignment.Center) {
        WCard(Modifier.padding(WeemSpace.xxl).heightIn(max = 600.dp), large = true) {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
                Box(Modifier.size(72.dp).background(p.accentContainer.color(), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(WeemIcons.LocationOnFilled, contentDescription = null, tint = p.accent.color(), modifier = Modifier.size(40.dp))
                }
                Text(t.title, style = WeemType.headline.copy(fontWeight = FontWeight.ExtraBold), textAlign = TextAlign.Center)
                Text(t.place, style = WeemType.titleM, textAlign = TextAlign.Center)
                here?.let { Text(TaskText.ALERT_NEAR.fmt(lang, it.distanceTo(t.point).toInt()), style = WeemType.bodyM, color = p.muted.color()) }
                WButton(TaskText.ALERT_ROUTE.get(lang), { onRoute(t) }, Modifier.testTag("task-alert-route"), icon = WeemIcons.AltRoute, ride = true, fill = true)
                WButton(TaskText.ALERT_CLOSE.get(lang), { onDismiss(t) }, Modifier.testTag("task-alert-close"), kind = BtnKind.OUTLINE, ride = true, fill = true)
                Text(TaskText.ALERT_NOTE.get(lang), style = WeemType.bodyS, color = p.muted.color(), textAlign = TextAlign.Center)
            }
        }
    }
}
