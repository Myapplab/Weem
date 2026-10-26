package com.kuryelog.app.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kuryelog.app.core.Downloads
import com.kuryelog.app.ui.SubScreen
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.logs.RideLogEntry
import com.kuryelog.logic.map.stats.DayHistory
import com.kuryelog.logic.map.ui.ReminderText
import com.kuryelog.logic.map.ui.RideLogText
import com.kuryelog.logic.map.ui.StatsText
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Dosyayı İndirilenler/WEEM'e yazar (`Downloads`, izin gerekmez) ve adresini döner. */
private fun saveToDownloads(ctx: Context, file: File, name: String, mime: String): Uri =
    Downloads.save(ctx, "WEEM", name, mime = mime) { out -> file.inputStream().use { it.copyTo(out) } }

private fun share(ctx: Context, uri: Uri, mime: String, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun mimeOf(name: String) = if (name.endsWith(".jsonl")) "text/plain" else "application/json"

/** İstatistik (Y17) çalışma zamanına bağlı: geçmiş (süren gün canlı), ışık cevabı, gün silme, paylaşma. */
@Composable
fun StatsHost(lang: Lang, focusStartMs: Long?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val dayRt = remember { DayRuntime.get(ctx) }
    val writer = remember { DayReportWriter.get(ctx) }
    val live by dayRt.view.collectAsState()
    var rev by remember { mutableIntStateOf(0) }
    var files by remember { mutableStateOf<List<DayState>>(emptyList()) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(rev) { files = withContext(Dispatchers.IO) { writer.files() } }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowMs = System.currentTimeMillis() } }
    val days = remember(files, live) { DayHistory.merge(dayRt.activeDay(), files, dayRt.lastDay()) }
    BackHandler(onBack = onBack)
    val actions = remember(lang) {
        StatsActions(
            streetName = { lat, lon, maxM -> MapData.controller(ctx).state.value.graph?.let { g -> g.nearestEdge(lat, lon, maxM)?.let { g.nameOf(it.edge) } } },
            onReview = { start, isLight -> dayRt.reviewLight(start, isLight); scope.launch { delay(400); rev++ } },
            onDelete = { date, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { DayDeletionRuntime.get(ctx).delete(date) } }
                    onBack()
                }
            },
            onShare = { d ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            writer.write(d)
                            writer.waitIdle()
                            val f = writer.file(d)
                            share(ctx, saveToDownloads(ctx, f, f.name, "application/json"), "application/json", f.name, StatsText.SHARE_TEXT.get(lang).format(f.name))
                        }
                    }
                }
            },
            onRefresh = { rev++ },
        )
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        StatsScreen(days, lang, nowMs, ZoneId.systemDefault(), focusStartMs, actions, onBack = onBack)
    }
}

/** Sürüş kayıtları (Y18) çalışma zamanına bağlı. */
@Composable
fun RideLogsHost(lang: Lang, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rt = remember { RideLogRuntime.get(ctx) }
    val settings by MapSettingsRepo.settings(ctx).collectAsState()
    val day by DayRuntime.get(ctx).view.collectAsState()
    var rev by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<RideLogEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(rev) {
        try {
            entries = withContext(Dispatchers.IO) { rt.writer.records() }
            error = null
        } catch (_: Exception) {
            entries = null
            error = RideLogText.READ_ERROR.get(lang)
        }
    }
    BackHandler(onBack = onBack)
    fun run(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            message = null
            error = null
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (_: Exception) {
                error = RideLogText.ACTION_ERROR.get(lang)
            }
            busy = false
            rev++
        }
    }
    fun fileOf(entry: RideLogEntry?, dayKey: String?): File = entry?.file ?: rt.writer.export(File(ctx.cacheDir, "exports"), dayKey)
    val actions = RideLogActions(
        onLoggingChange = { on -> MapSettingsRepo.update(ctx) { it.copy(logging = on) } },
        onRefresh = { rev++ },
        onSave = { e, dayKey, _ ->
            run {
                val f = fileOf(e, dayKey)
                saveToDownloads(ctx, f, f.name, mimeOf(f.name))
                message = RideLogText.SAVED.get(lang)
            }
        },
        onShare = { e, dayKey, _ ->
            run {
                val f = fileOf(e, dayKey)
                val uri = saveToDownloads(ctx, f, f.name, mimeOf(f.name))
                withContext(Dispatchers.Main) { share(ctx, uri, mimeOf(f.name), RideLogText.SHARE.get(lang), RideLogText.SHARE_TEXT.get(lang)) }
            }
        },
        onDeleteOne = { e -> run { rt.writer.delete(e) } },
        onDeleteDay = { dayKey -> run { DayDeletionRuntime.get(ctx).delete(dayKey) } },
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        RideLogsScreen(
            entries, settings.logging, day.state.active, lang, ZoneId.systemDefault(), active = { rt.writer.isActive(it) }, actions = actions,
            busy = busy, error = error, message = message, onBack = onBack,
        )
    }
}

/** Günlük işler (Y20) çalışma zamanına bağlı. */
@Composable
fun TasksHost(lang: Lang, initial: FoundPlace? = null, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rt = remember { TaskRuntime.get(ctx) }
    val view by rt.view.collectAsState()
    BackHandler(onBack = onBack)
    val actions = remember {
        TaskActions(
            onAdd = rt::add, onDelete = rt::delete,
            resolve = { text -> NavRuntime.get(ctx).resolvePlace(text)?.let { FoundPlace(it.label, it.point) } },
        )
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        TasksScreen(view.tasks, lang, ZoneId.systemDefault(), System.currentTimeMillis(), actions, initialPoint = initial, onBack = onBack)
    }
}

/** Hatırlatıcı ayarları (Y20) çalışma zamanına bağlı. */
@Composable
fun ReminderSettingsHost(lang: Lang, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rt = remember { ReminderRuntime.get(ctx) }
    val settings by rt.settings.collectAsState()
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { rt.permissionAsked() }
    SubScreen(ReminderText.SECTION.get(lang), Str.BACK.get(lang), onBack) {
        ReminderSettingsSection(
            settings, lang, onChange = rt::updateItem, onPreview = rt::preview,
            onEnable = { if (android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
        )
    }
}

/** Teklif veri seti (Y19) çalışma zamanına bağlı; "Elle ekle" `DatasetManualSheet` (sonraki adım). */
@Composable
fun DatasetHost(lang: Lang, shiftDay: String?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val runtime = remember { AppDatasetRuntime(ctx) }
    val scope = rememberCoroutineScope()
    var manualDay by remember { mutableStateOf<String?>(null) }
    var pickKind by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val day = manualDay
        val kind = pickKind
        manualDay = null
        pickKind = null
        if (uri != null && day != null && kind != null) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { runtime.copyScreenshot(day, kind, uri) } }
                    .onFailure { Toast.makeText(ctx, com.kuryelog.logic.map.ui.DatasetText.PICK_FAILED.get(lang), Toast.LENGTH_LONG).show() }
                refresh++
            }
        }
    }
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            androidx.compose.runtime.key(refresh) { DatasetScreen(
                runtime, lang, shiftDay, Modifier.fillMaxWidth(), onBack = onBack,
                onManual = { day -> manualDay = day },
                onShare = { day ->
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val zip = runtime.reseal(day)
                                val uri = saveToDownloads(ctx, zip, zip.name, "application/zip")
                                withContext(Dispatchers.Main) { share(ctx, uri, "application/zip", zip.name, com.kuryelog.logic.map.ui.DatasetText.SHARE_TEXT.get(lang).format(day)) }
                            }
                        }.onFailure { Toast.makeText(ctx, com.kuryelog.logic.map.ui.DatasetText.ERROR_ACTION.get(lang), Toast.LENGTH_LONG).show() }
                    }
                },
            ) }
        }
    }
    manualDay?.let { day ->
        if (pickKind == null) DatasetManualSheet(
            lang,
            onPick = { kind -> pickKind = kind; picker.launch(arrayOf("image/*")) },
            onSave = { kind, input ->
                val r = com.kuryelog.logic.orders.dataset.DatasetManual.save(kind, input, System.currentTimeMillis(), android.os.SystemClock.elapsedRealtimeNanos())
                r.row?.let { runtime.addManual(day, it); refresh++ }
                r.error
            },
            onDismiss = { if (pickKind == null) manualDay = null },
        )
    }
}
