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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.kuryelog.logic.core.EncryptedBackup
import com.kuryelog.app.core.LogImport
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.DailyPack
import com.kuryelog.logic.core.DailyRecords
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.orders.OrdersScreen
import com.kuryelog.logic.orders.OrdersSource
import com.kuryelog.logic.app.Text as Str
/**
 * Daha fazla → Kayıtlar (CORE.md 5.3, ORDERS.md 10.3): eski Rawstats ZIP yedeğini içe aktarma (A2.6), yedek ve dışa aktarma (A4.3: günlük paket,
 * tüm kayıtları paylaş, anonim analiz) ve gün sayfaları (A3.8): her gün için o günün siparişleri, kayıt dosyaları, paylaş, arşivle/arşivden çıkar,
 * sil. Üstteki düğme arşivle etkin günler arasında geçer.
 */
@Composable
fun RecordsScreen(lang: Lang, onBack: () -> Unit, source: (android.content.Context) -> OrdersSource = OrdersAccess::source) {
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
                    // Siparişler `LogImport.afterImport` ile (WeemApplication) yeniden kurulur.
                    is LogImport.Outcome.Done -> Str.RECORDS_IMPORT_DONE.get(lang)
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
        BackupTools(lang)
        EncryptedBackupTools(lang) { version++ }
        status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }

        GuardedPage("records", lang, key = version, build = { source(ctx).let { it to OrdersScreen(it, lang).warm() } }) { (src, screen) ->
            val labels = screen.labels
            OutlinedButton(onClick = { archive = !archive; selected = null }, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(if (archive) labels.showActive else labels.showArchive)
            }
            Text(labels.dailyRecords, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            DayPages(screen, records = true, archive = archive, platform = platform.ifEmpty { null }, onPlatform = { platform = it ?: "" }, selected = selected,
                onSelected = { selected = it }, onDetail = {}, onTrip = { trip = it }) { date ->
                RecordCard(screen, src.today(), date, lang, canDelete = src.canAct) { version++ }
            }
        }
    }
    trip?.let { id -> TripDetailDialog(ctx, lang, id, source) { trip = null } }
}

/**
 * Yedek bölümü (R17, R18; RS `renderRecordTools` `:1243-1258`, aynı sıra): günlük paket anahtarı + açıklama, "Tüm kayıtları paylaş (.zip)"
 * (tam yedek), "Analiz için paylaş (anonim)" + açıklama. İçe aktarma düğmesi bunun üstünde (A2.6).
 */
@Composable
private fun BackupTools(lang: Lang) {
    val ctx = LocalContext.current
    var daily by remember { mutableStateOf(DailyPack.enabled(Store.values(ctx))) }
    Text(Str.RECORDS_BACKUP.get(lang), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Str.RECORDS_DAILY_PACK.get(lang), Modifier.weight(1f))
        Switch(checked = daily, onCheckedChange = { on -> ctx.safely { Store.prefs(ctx).edit().putBoolean(DailyPack.PREF, on).apply(); daily = on } })
    }
    Text(Str.RECORDS_DAILY_PACK_INFO.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = { ctx.safely("export") { RecordExport.exportLogs(ctx, lang) } }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(Str.RECORDS_EXPORT_ALL.get(lang))
    }
    OutlinedButton(onClick = { ctx.safely("analysis") { RecordExport.exportAnalysis(ctx, lang) } }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(Str.RECORDS_EXPORT_ANON.get(lang))
    }
    Text(Str.RECORDS_EXPORT_ANON_INFO.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * N3 şifreli yedek (S22): parola durumu ve belirle/değiştir, "bu haftayı şimdi yedekle", haftalık dosyalar (dışa aktar/paylaş; dışa
 * aktarıldıktan sonra "Uygulamadan sil" = o haftanın kayıtları, onaylı), "şifreli yedeği geri yükle" (dosya + parola).
 */
@Composable
private fun EncryptedBackupTools(lang: Lang, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val vault = remember { BackupVault(ctx) }
    val hasPassword = remember(refresh) { vault.hasPassword() }
    val backups = remember(refresh) { runCatching { EncryptedBackups.list(ctx) }.getOrDefault(emptyList()) }
    var askPassword by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var deleteWeek by remember { mutableStateOf<EncryptedBackup.Week?>(null) }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }

    Text(Str.BACKUP_TITLE.get(lang), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
    Text(Str.BACKUP_INFO.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(if (hasPassword) Str.BACKUP_ON.get(lang) else Str.BACKUP_NO_PASSWORD.get(lang), modifier = Modifier.padding(top = 4.dp))
    OutlinedButton(onClick = { askPassword = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(if (hasPassword) Str.BACKUP_CHANGE_PASSWORD.get(lang) else Str.BACKUP_SET_PASSWORD.get(lang))
    }
    if (hasPassword) OutlinedButton(onClick = {
        ctx.safely("encrypted_backup") {
            toast(Str.RECORDS_PREPARING.get(lang))
            EncryptedBackups.writeNow(ctx) { r ->
                toast(r.fold({ if (it) Str.BACKUP_SAVED.get(lang) else Str.RECORDS_NO_LOGS.get(lang) }, { Str.ERROR.get(lang) + it.message })); refresh++
            }
        }
    }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(Str.BACKUP_NOW.get(lang)) }
    if (backups.isEmpty()) Text(Str.BACKUP_NONE.get(lang), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    for ((week, item) in backups) Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("${week.monday} – ${week.sunday}", fontWeight = FontWeight.Bold)
            Text(item.name + " · " + Formatter.formatShortFileSize(ctx, item.bytes), style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { ctx.safely("encrypted_backup") { EncryptedBackups.export(ctx, week, item.uri, Str.BACKUP_EXPORT.get(lang)); refresh++ } }) {
                    Text(Str.BACKUP_EXPORT.get(lang))
                }
                TextButton(onClick = { if (vault.exported(week)) deleteWeek = week else toast(Str.BACKUP_DELETE_AFTER_EXPORT.get(lang)) }) {
                    Text(Str.BACKUP_DELETE_WEEK.get(lang), color = if (vault.exported(week)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    OutlinedButton(onClick = {
        ctx.safely { if (Store.shiftOn(ctx)) toast(Str.RECORDS_IMPORT_STOP.get(lang)) else picker.launch(arrayOf("application/octet-stream", "*/*")) }
    }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(Str.BACKUP_RESTORE.get(lang)) }

    if (askPassword) PasswordDialog(lang, twice = true, onDismiss = { askPassword = false }) { pw ->
        ctx.safely("encrypted_backup") { vault.setPassword(pw); pw.fill(' ') }
        askPassword = false; refresh++
    }
    restoreUri?.let { uri ->
        PasswordDialog(lang, twice = false, onDismiss = { restoreUri = null }) { pw ->
            restoreUri = null
            ctx.safely("encrypted_restore") {
                toast(Str.RECORDS_PREPARING.get(lang))
                EncryptedBackups.restore(ctx, { ctx.contentResolver.openInputStream(uri) }, pw) { r ->
                    toast(when (r) {
                        is EncryptedBackups.Restored.Done -> Str.BACKUP_RESTORE_DONE.get(lang)
                        EncryptedBackups.Restored.ShiftOn -> Str.RECORDS_IMPORT_STOP.get(lang)
                        EncryptedBackups.Restored.Busy -> Str.RECORDS_PREPARING.get(lang)
                        EncryptedBackups.Restored.WrongPassword -> Str.BACKUP_WRONG_PASSWORD.get(lang)
                        is EncryptedBackups.Restored.Failed -> Str.ERROR.get(lang) + r.message
                    })
                    onChanged()
                }
            }
        }
    }
    deleteWeek?.let { week ->
        AlertDialog(
            onDismissRequest = { deleteWeek = null }, title = { Text(Str.BACKUP_DELETE_WEEK.get(lang)) },
            text = { Text("${week.monday} – ${week.sunday}\n" + Str.BACKUP_DELETE_CONFIRM.get(lang)) },
            confirmButton = { TextButton(onClick = { ctx.safely("encrypted_backup") { EncryptedBackups.deleteWeek(ctx, week) }; deleteWeek = null; onChanged() }) { Text(Str.BACKUP_DELETE_WEEK.get(lang)) } },
            dismissButton = { TextButton(onClick = { deleteWeek = null }) { Text(Str.CANCEL.get(lang)) } },
        )
    }
}

/** Parola girişi (gizli); [twice] ise iki kez ve en az 8 karakter. */
@Composable
private fun PasswordDialog(lang: Lang, twice: Boolean, onDismiss: () -> Unit, onOk: (CharArray) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(Str.BACKUP_TITLE.get(lang)) },
        text = {
            Column {
                OutlinedTextField(a, { a = it }, label = { Text(Str.BACKUP_PASSWORD.get(lang)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (twice) OutlinedTextField(b, { b = it }, label = { Text(Str.BACKUP_PASSWORD_AGAIN.get(lang)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (error) Text(Str.BACKUP_PASSWORD_MISMATCH.get(lang), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (a.length < EncryptedBackup.MIN_PASSWORD || (twice && a != b)) error = true else onOk(a.toCharArray())
            }) { Text(Str.SAVE.get(lang)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Str.CANCEL.get(lang)) } },
    )
}

@Composable
private fun TripDetailDialog(ctx: android.content.Context, lang: Lang, id: String, source: (android.content.Context) -> OrdersSource, onClose: () -> Unit) {
    val screen = remember(id) { OrdersScreen(source(ctx), lang) }
    TripDialog(screen, id, onClose)
}

/** Bir günün kayıt kartı (Rawstats `renderDailyRecords`): dosya sayısı ve boyut, kayıt dosyaları, paylaş (günün ZIP'i, A4.3), arşiv, sil. */
@Composable
private fun RecordCard(screen: OrdersScreen, today: String, date: String, lang: Lang, canDelete: Boolean, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val labels = screen.labels
    val records = remember(date) { DailyRecords(Store.logsRoot(ctx)) }
    val day = remember(date, screen) { records.days().firstOrNull { it.date == date } }
    var files by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }

    /** Arşivle/çıkar ([archive] true/false) ya da sil (null); bugünün kaydı vardiya açıkken değişmez. */
    fun change(name: String?, archive: Boolean?) = ctx.safely {
        Store.locked(ctx) {
            check(!Store.importing) { Str.RECORDS_PREPARING.get(lang) }
            check(date != today || !Store.shiftOn(ctx)) { labels.recordStop }
            if (archive == null) records.delete(date, name) else records.archive(date, name, archive)
        }
        if (archive == null) OrdersAccess.reload(ctx)
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
                OutlinedButton(onClick = { ctx.safely("export") { RecordExport.exportLogs(ctx, lang, date) } }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(Str.RECORDS_SHARE.get(lang))
                }
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
                    // RS dosya menüsü: paylaş, arşivle/çıkar, sil.
                    TextButton(onClick = { ctx.safely("export") { RecordExport.exportLogs(ctx, lang, date, name) }; file = null }) { Text(Str.RECORDS_SHARE.get(lang)) }
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
