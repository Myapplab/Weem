package com.kuryelog.app

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kuryelog.app.core.Store
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.NoticeDialog
import com.kuryelog.app.scooter.Scooter
import com.kuryelog.logic.scooter.ScooterPanel
import com.kuryelog.logic.scooter.ScooterUiText
import com.kuryelog.app.ui.SectionLabel
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.StatusPill
import com.kuryelog.app.ui.SubScreen
import com.kuryelog.app.ui.SwitchRow
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WSheet
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.DailyPack
import com.kuryelog.logic.core.DailyRecords
import com.kuryelog.logic.core.EncryptedBackup
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.orders.OrdersScreen
import com.kuryelog.logic.orders.OrdersSource
/**
 * Daha fazla → Kayıtlar (CORE.md 5.3, ORDERS.md 10.3): yedek ve dışa aktarma (A4.3: günlük paket,
 * tüm kayıtları paylaş, anonim analiz) ve gün sayfaları (A3.8): her gün için o günün siparişleri, kayıt dosyaları, paylaş, arşivle/arşivden çıkar,
 * sil. Üstteki düğme arşivle etkin günler arasında geçer. S42: eski Rawstats ZIP'ini içe aktarma (A2.6) kalktı; geri yükleme şifreli yedekten.
 */
@Composable
fun RecordsScreen(lang: Lang, onBack: () -> Unit, source: (android.content.Context) -> OrdersSource = OrdersAccess::source) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var archive by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var platform by rememberSaveable { mutableStateOf("") }
    var trip by remember { mutableStateOf<String?>(null) }
    SubScreen(Str.RECORDS_TITLE.get(lang), Str.BACK.get(lang), onBack, spacing = 0.dp) {
        BackupTools(lang)
        ScooterRecords(lang)
        EncryptedBackupTools(lang) { version++ }

        GuardedPage("records", lang, key = version, build = { source(ctx).let { it to OrdersScreen(it, lang).warm() } }) { (src, screen) ->
            val labels = screen.labels
            SectionLabel(labels.dailyRecords)
            WButton(
                if (archive) labels.showActive else labels.showArchive, { archive = !archive; selected = null },
                kind = BtnKind.OUTLINE, icon = if (archive) WeemIcons.History else WeemIcons.Folder, fill = true,
            )
            DayPages(screen, records = true, archive = archive, platform = platform.ifEmpty { null }, onPlatform = { platform = it ?: "" }, selected = selected,
                onSelected = { selected = it }, onDetail = {}, onTrip = { trip = it }, modifier = Modifier.padding(top = WeemSpace.m)) { date ->
                RecordCard(screen, src.today(), date, lang, canDelete = src.canAct) { version++ }
            }
        }
    }
    trip?.let { id -> TripDetailDialog(ctx, lang, id, source) { trip = null } }
}

/** S37: Kayıtlar → Scooter kayıtları — Sürüş Paneli ZIP seçenekleri (Scooter sekmesindeki ZIP ile aynı; GPS'li ve tam teşhis onaylı). */
@Composable
private fun ScooterRecords(lang: Lang) {
    val ctx = LocalContext.current
    var ask by remember { mutableStateOf<ScooterPanel.ExportChoice?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    fun run(c: ScooterPanel.ExportChoice) {
        message = ScooterUiText.EXPORT_PREPARING.get(lang)
        ctx.safely("scooter_export") { Scooter.export(ctx, c, lang) { message = it } }
    }
    SettingsGroup(Str.RECORDS_SCOOTER.get(lang), dividerInset = 70.dp) {
        for (c in ScooterPanel.ExportChoice.entries) ListRow(
            c.text.get(lang), Modifier.testTag("scooter-export-${c.name}"), icon = WeemIcons.ElectricScooter, chevron = true,
            onClick = { if (c.warning() != null) ask = c else run(c) },
        )
    }
    ask?.let { c ->
        val (title, text, go) = c.warning()!!
        ConfirmDialog(
            title = (title ?: c.text).get(lang), text = text.get(lang), confirm = go.get(lang), dismiss = ScooterUiText.CANCEL.get(lang),
            onConfirm = { ask = null; run(c) }, onDismiss = { ask = null },
        )
    }
    message?.let { NoticeDialog(it, ScooterUiText.OK.get(lang), { message = null }) }
}

/**
 * Yedek bölümü (R17, R18; RS `renderRecordTools` `:1243-1258`, aynı sıra): günlük paket anahtarı + açıklama, "Tüm kayıtları paylaş (.zip)"
 * (tam yedek), "Analiz için paylaş (anonim)" + açıklama. İçe aktarma düğmesi bunun üstünde (A2.6).
 */
@Composable
private fun BackupTools(lang: Lang) {
    val ctx = LocalContext.current
    var daily by remember { mutableStateOf(DailyPack.enabled(Store.values(ctx))) }
    SettingsGroup(Str.RECORDS_BACKUP.get(lang), dividerInset = 70.dp) {
        // S37: tüm kayıtlar (sipariş + scooter + sürüş) tek ZIP'te.
        ListRow(
            Str.RECORDS_EXPORT_EVERYTHING.get(lang), Modifier.testTag("export-everything"), subtitle = Str.RECORDS_EXPORT_EVERYTHING_INFO.get(lang),
            icon = WeemIcons.FolderZip, iconTone = WTone.ACCENT, chevron = true, onClick = { ctx.safely("export_all") { RecordExport.exportEverything(ctx, lang) } },
        )
        SwitchRow(
            Str.RECORDS_DAILY_PACK.get(lang), daily,
            { on -> ctx.safely { Store.prefs(ctx).edit().putBoolean(DailyPack.PREF, on).apply(); daily = on } },
            subtitle = Str.RECORDS_DAILY_PACK_INFO.get(lang), icon = WeemIcons.Schedule,
        )
        ListRow(Str.RECORDS_EXPORT_ALL.get(lang), icon = WeemIcons.Share, chevron = true, onClick = { ctx.safely("export") { RecordExport.exportLogs(ctx, lang) } })
        ListRow(
            Str.RECORDS_EXPORT_ANON.get(lang), subtitle = Str.RECORDS_EXPORT_ANON_INFO.get(lang), icon = WeemIcons.VisibilityOff, chevron = true,
            onClick = { ctx.safely("analysis") { RecordExport.exportAnalysis(ctx, lang) } },
        )
    }
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
    var askRestart by remember { mutableStateOf(false) }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }

    SectionLabel(Str.BACKUP_TITLE.get(lang))
    WCard {
        Text(Str.BACKUP_INFO.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatusPill(if (hasPassword) Str.BACKUP_ON.get(lang) else Str.BACKUP_NO_PASSWORD.get(lang), if (hasPassword) WTone.POSITIVE else WTone.WARN, icon = WeemIcons.Lock)
        WButton(
            if (hasPassword) Str.BACKUP_CHANGE_PASSWORD.get(lang) else Str.BACKUP_SET_PASSWORD.get(lang), { askPassword = true },
            kind = if (hasPassword) BtnKind.OUTLINE else BtnKind.TONAL, icon = WeemIcons.Lock, fill = true,
        )
        if (hasPassword) WButton(Str.BACKUP_NOW.get(lang), {
            ctx.safely("encrypted_backup") {
                toast(Str.RECORDS_PREPARING.get(lang))
                EncryptedBackups.writeNow(ctx) { r ->
                    toast(r.fold({ if (it) Str.BACKUP_SAVED.get(lang) else Str.RECORDS_NO_LOGS.get(lang) }, { Str.ERROR.get(lang) + it.message })); refresh++
                }
            }
        }, kind = BtnKind.TONAL, icon = WeemIcons.Backup, fill = true)
        if (backups.isEmpty()) Text(Str.BACKUP_NONE.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for ((week, item) in backups) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(WeemRadius.tile)).padding(WeemSpace.m)) {
                Text("${week.monday} – ${week.sunday}", style = WeemType.titleS)
                Text(item.name + " · " + Formatter.formatShortFileSize(ctx, item.bytes), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
                    WButton(Str.BACKUP_EXPORT.get(lang), { ctx.safely("encrypted_backup") { EncryptedBackups.export(ctx, week, item.uri, Str.BACKUP_EXPORT.get(lang)); refresh++ } },
                        kind = BtnKind.TEXT, icon = WeemIcons.Share)
                    WButton(Str.BACKUP_DELETE_WEEK.get(lang), { if (vault.exported(week)) deleteWeek = week else toast(Str.BACKUP_DELETE_AFTER_EXPORT.get(lang)) },
                        kind = if (vault.exported(week)) BtnKind.DANGER else BtnKind.TEXT, icon = WeemIcons.Delete)
                }
            }
        }
        WButton(Str.BACKUP_RESTORE.get(lang), {
            ctx.safely { if (Store.shiftOn(ctx)) toast(Str.RECORDS_IMPORT_STOP.get(lang)) else picker.launch(arrayOf("application/octet-stream", "*/*")) }
        }, kind = BtnKind.OUTLINE, icon = WeemIcons.History, fill = true)
    }

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
                    if (r is EncryptedBackups.Restored.Done && r.mapPending) askRestart = true
                    onChanged()
                }
            }
        }
    }
    // A10.4h: harita verisi yeni süreçte yüklenir.
    if (askRestart) ConfirmDialog(
        title = Str.BACKUP_RESTORE.get(lang), text = Str.BACKUP_MAP_RESTART.get(lang),
        confirm = Str.BACKUP_RESTART_NOW.get(lang), dismiss = Str.LATER.get(lang),
        onConfirm = { askRestart = false; ctx.safely("encrypted_restore") { EncryptedBackups.restart(ctx) } },
        onDismiss = { askRestart = false },
    )
    deleteWeek?.let { week ->
        ConfirmDialog(
            title = Str.BACKUP_DELETE_WEEK.get(lang), text = "${week.monday} – ${week.sunday}\n" + Str.BACKUP_DELETE_CONFIRM.get(lang),
            confirm = Str.BACKUP_DELETE_WEEK.get(lang), dismiss = Str.CANCEL.get(lang), danger = true,
            onConfirm = { ctx.safely("encrypted_backup") { EncryptedBackups.deleteWeek(ctx, week) }; deleteWeek = null; onChanged() },
            onDismiss = { deleteWeek = null },
        )
    }
}

/** Parola girişi (gizli); [twice] ise iki kez ve en az 8 karakter. */
@Composable
private fun PasswordDialog(lang: Lang, twice: Boolean, onDismiss: () -> Unit, onOk: (CharArray) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val field = RoundedCornerShape(WeemRadius.tile)
    ConfirmDialog(
        title = Str.BACKUP_TITLE.get(lang), confirm = Str.SAVE.get(lang), dismiss = Str.CANCEL.get(lang), onDismiss = onDismiss,
        onConfirm = { if (a.length < EncryptedBackup.MIN_PASSWORD || (twice && a != b)) error = true else onOk(a.toCharArray()) },
    ) {
        OutlinedTextField(a, { a = it }, label = { Text(Str.BACKUP_PASSWORD.get(lang)) }, singleLine = true, shape = field,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (twice) OutlinedTextField(b, { b = it }, label = { Text(Str.BACKUP_PASSWORD_AGAIN.get(lang)) }, singleLine = true, shape = field,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (error) Banner(Str.BACKUP_PASSWORD_MISMATCH.get(lang), tone = WTone.DANGER)
    }
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

    WCard(Modifier.padding(top = WeemSpace.m)) {
        if (day == null) {
            Text(labels.noRecords, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s), verticalAlignment = Alignment.CenterVertically) {
                Text(day.date + if (day.date == today) " · " + labels.today else "", style = WeemType.titleM, modifier = Modifier.weight(1f))
                if (day.archived) StatusPill(labels.archived, WTone.NEUTRAL, dot = false)
            }
            Text(
                Str.RECORDS_DAY.get(lang).format(day.files.size, Formatter.formatShortFileSize(ctx, day.bytes)),
                style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                WButton(labels.recordFiles, { files = true }, Modifier.weight(1f), kind = BtnKind.OUTLINE, icon = WeemIcons.Folder)
                WButton(Str.RECORDS_SHARE.get(lang), { ctx.safely("export") { RecordExport.exportLogs(ctx, lang, date) } }, Modifier.weight(1f), kind = BtnKind.OUTLINE, icon = WeemIcons.Share)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                WButton(if (day.archived) labels.unarchive else labels.archive, { change(null, !day.archived) }, Modifier.weight(1f), kind = BtnKind.OUTLINE, icon = WeemIcons.History)
                WButton(labels.delete, { confirm = "" }, Modifier.weight(1f), kind = BtnKind.DANGER, icon = WeemIcons.Delete, enabled = canDelete)
            }
        }
    }
    // K4: dosya listesi ve dosya menüsü alttan açılan sayfada.
    if (files && day != null) WSheet(onDismiss = { files = false }, title = date) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            SettingsGroup(null, dividerInset = 70.dp) {
                for (f in day.files) ListRow(
                    f.name, subtitle = if (records.archived(date, f.name)) labels.archived else null, icon = WeemIcons.Folder, chevron = true,
                    onClick = { file = f.name; files = false },
                )
            }
        }
        WButton(labels.cancel, { files = false }, kind = BtnKind.TEXT, fill = true)
    }
    file?.let { name ->
        val archived = records.archived(date, name)
        WSheet(onDismiss = { file = null }, title = name) {
            // RS dosya menüsü: paylaş, arşivle/çıkar, sil.
            SettingsGroup(null, dividerInset = 70.dp) {
                ListRow(Str.RECORDS_SHARE.get(lang), icon = WeemIcons.Share, onClick = { ctx.safely("export") { RecordExport.exportLogs(ctx, lang, date, name) }; file = null })
                ListRow(if (archived) labels.unarchive else labels.archive, icon = WeemIcons.History, onClick = { change(name, !archived); file = null })
                ListRow(labels.delete, icon = WeemIcons.Delete, iconTone = WTone.DANGER, titleTone = WTone.DANGER, enabled = canDelete, onClick = { confirm = name; file = null })
            }
            WButton(labels.cancel, { file = null }, kind = BtnKind.TEXT, fill = true)
        }
    }
    confirm?.let { name ->
        val target = name.ifEmpty { null }
        ConfirmDialog(
            title = labels.delete, text = "$date ${target.orEmpty()}\n" + labels.deleteConfirm, confirm = labels.delete, dismiss = labels.cancel, danger = true,
            onConfirm = { change(target, null); confirm = null }, onDismiss = { confirm = null },
        )
    }
}
