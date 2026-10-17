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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kuryelog.app.core.LogImport
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.DailyRecords
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.app.Text as Str

/**
 * Daha fazla → Kayıtlar (CORE.md 5.3): eski Rawstats ZIP yedeğini içe aktarma ve gün listesi (tarih, dosya sayısı, boyut, arşiv).
 * A2.6 bununla denenir; A3 (R12 gün sayfası) genişletir.
 */
@Composable
fun RecordsScreen(lang: Lang, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ctx.safely("import") {
            status = Str.RECORDS_PREPARING.get(lang)
            LogImport.start(ctx, { ctx.contentResolver.openInputStream(uri) }) { outcome ->
                status = when (outcome) {
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
        status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }

        Text(Str.RECORDS_DAILY.get(lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
        GuardedPage("records", lang, key = version, build = { DailyRecords(Store.logsRoot(ctx)).days() }) { days ->
            if (days.isEmpty()) Text(Str.RECORDS_EMPTY.get(lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (d in days) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.date, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            Str.RECORDS_DAY.get(lang).format(d.files.size, Formatter.formatShortFileSize(ctx, d.bytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (d.archived) Text(Str.RECORDS_ARCHIVED.get(lang), style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider()
            }
        }
    }
}
