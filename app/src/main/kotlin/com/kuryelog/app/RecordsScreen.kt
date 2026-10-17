package com.kuryelog.app

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kuryelog.app.core.LogImport
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.DailyRecords
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.orders.OrdersScreen
import com.kuryelog.logic.app.Text as Str
/**
 * Daha fazla → Kayıtlar (CORE.md 5.3, ORDERS.md 10.3): eski Rawstats ZIP yedeğini içe aktarma (A2.6) ve gün sayfaları (A3.8): her gün
 * için o günün siparişleri, kayıt dosyaları, arşivle/arşivden çıkar, sil. Üstteki düğme arşivle etkin günler arasında geçer.
 */
@Composable
fun RecordsScreen(lang: Lang, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var archive by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var platform by rememberSaveable { mutableStateOf("") }
    var trip by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ctx.safely("import") {
            status = Str.RECORDS_PREPARING.get(lang)
            LogImport.start(ctx, { ctx.contentResolver.openInputStream(uri) }) { outcome ->
                status = when (outcome) {
                    // Biten içe aktarma: akış varsa courier.db günlüklerden yeniden kurulur (A3.7 bağlayınca).
                    is LogImport.Outcome.Done -> { OrdersAccess.reload(); Str.RECORDS_IMPORT_DONE.get(lang) }
                    LogImport.Outcome.ShiftOn -> Str.RECORDS_IMPORT_STOP.get(lang)
                    LogImport.Outcome.Busy -> Str.RECORDS_PREPARING.get(lang)
                    is LogImport.Outcome.Failed -> Str.ERROR.get(lang) + outcome.message
                }
                status?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
                version++
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(Str.BACK.get(lang)) }
            Text(Str.RECORDS_TITLE.get(lang), style = MaterialTheme.typography.headlineMedium)
        }
        Button(
            onClick = {
                ctx.safely {
                    if (Store.shiftOn(ctx)) status = Str.RECORDS_IMPORT_STOP.get(lang)
                    else picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                }
            },
            modifier = Modifier.padding(top = 16.dp),
        ) { Text(Str.RECORDS_IMPORT.get(lang)) }
        status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }

        GuardedPage("records", lang, key = version, build = { OrdersAccess.source(ctx).let { it to OrdersScreen(it, lang).warm() } }) { (src, screen) ->
            val labels = screen.labels
            OutlinedButton(onClick = { archive = !archive; selected = null }, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(if (archive) labels.showActive else labels.showArchive)
            }
            Text(labels.dailyRecords, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            DayPages(screen, records = true, archive = archive, platform = platform.ifEmpty { null }, onPlatform = { platform = it ?: "" }, selected = selected,
                onSelected = { selected = it }, onDetail = {}, onTrip = { trip = it }) { date ->
                RecordCard(screen, src.today(), date, lang) { version++ }
            }
        }
    }
    trip?.let { id -> TripDetailDialog(ctx, lang, id) { trip = null } }
}

@Composable
private fun TripDetailDialog(ctx: android.content.Context, lang: Lang, id: String, onClose: () -> Unit) {
    val screen = remember(id) { OrdersScreen(OrdersAccess.source(ctx), lang) }
    TripDialog(screen, id, onClose)
}

/** Bir günün kayıt kartı (Rawstats `renderDailyRecords`): dosya sayısı ve boyut, kayıt dosyaları, arşiv, sil. Paylaşma ZIP'i A4.3'te. */
@Composable
private fun RecordCard(screen: OrdersScreen, today: String, date: String, lang: Lang, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val labels = screen.labels
    val records = remember(date) { DailyRecords(Store.logsRoot(ctx)) }
    val day = remember(date, screen) { records.days().firstOrNull { it.date == date } }
    var files by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val canDelete = OrdersAccess.flow != null

    /** Arşivle/çıkar ([archive] true/false) ya da sil (null); bugünün kaydı vardiya açıkken değişmez. */
    fun change(name: String?, archive: Boolean?) = ctx.safely {
        synchronized(Store) {
            check(!Store.importing) { Str.RECORDS_PREPARING.get(lang) }
            check(date != today || !Store.shiftOn(ctx)) { labels.recordStop }
            if (archive == null) records.delete(date, name) else records.archive(date, name, archive)
        }
        if (archive == null) OrdersAccess.reload()
        onChanged()
    }

    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (day == null) {
                Text(labels.noRecords, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(day.date + if (day.date == today) " · " + labels.today else "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    Str.RECORDS_DAY.get(lang).format(day.files.size, Formatter.formatShortFileSize(ctx, day.bytes)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (day.archived) Text(labels.archived, style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = { files = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(labels.recordFiles) }
                OutlinedButton(onClick = { change(null, !day.archived) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (day.archived) labels.unarchive else labels.archive) }
                OutlinedButton(onClick = { confirm = "" }, enabled = canDelete, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(labels.delete, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (files && day != null) AlertDialog(
        onDismissRequest = { files = false }, title = { Text(date) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (f in day.files) TextButton(onClick = { file = f.name; files = false }) {
                    Text(f.name + if (records.archived(date, f.name)) " · " + labels.archived else "")
                }
            }
        },
        confirmButton = { TextButton(onClick = { files = false }) { Text(labels.cancel) } },
    )
    file?.let { name ->
        val archived = records.archived(date, name)
        AlertDialog(
            onDismissRequest = { file = null }, title = { Text(name) },
            text = {
                Column {
                    TextButton(onClick = { change(name, !archived); file = null }) { Text(if (archived) labels.unarchive else labels.archive) }
                    TextButton(onClick = { confirm = name; file = null }, enabled = canDelete) { Text(labels.delete) }
                }
            },
            confirmButton = { TextButton(onClick = { file = null }) { Text(labels.cancel) } },
        )
    }
    confirm?.let { name ->
        val target = name.ifEmpty { null }
        AlertDialog(
            onDismissRequest = { confirm = null }, title = { Text(labels.delete) }, text = { Text("$date ${target.orEmpty()}\n" + labels.deleteConfirm) },
            confirmButton = { TextButton(onClick = { change(target, null); confirm = null }) { Text(labels.delete) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(labels.cancel) } },
        )
    }
}
