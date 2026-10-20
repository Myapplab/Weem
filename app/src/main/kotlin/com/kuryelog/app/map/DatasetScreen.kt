package com.kuryelog.app.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.DatasetText
import com.kuryelog.logic.map.ui.fmt
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Veri seti gün özeti (MAP.md 13.8; `DatasetPack.summary` alanları): platform sayıları, eşleşme, eksikler. Oranlar null = bilinmiyor. */
data class DatasetDayInfo(
    val day: String,
    val folder: String,
    val wolt: Int,
    val bolt: Int,
    val matched: Int = 0,
    val onlyDayLog: Int = 0,
    val onlyLive: Int = 0,
    val routeComputedRatio: Double? = null,
    val emptyFieldRatio: Double? = null,
    val scooterCoverageRatio: Double? = null,
    val collectionComplete: Boolean? = null,
    val missingManual: List<String> = emptyList(),
)

/** `:orders` `DatasetRuntime` çağrıları (kanal sahtesi yerine sahte uygulama testte); hata fırlatır. */
interface DatasetRuntime {
    suspend fun list(): List<DatasetDayInfo>
    suspend fun collect(day: String)
}

private fun pct(r: Double?, unknown: String) = if (r == null) unknown else String.format(Locale.US, "%%%.1f", r * 100)

/**
 * Teklif veri seti ekranı (MAP.md 13.8): üstte açıklama (M-13-2), yenile (`dataset-refresh`, içerik açıklaması "Yenile"), vardiya gününü topla (`dataset-collect-shift`);
 * günler kapalı başlar (`dataset-day-<gün>`; başlık dokununca açılır, bağımsız); açık gün "Teklifler: Wolt N · Bolt M", eksikler; gün başına topla (`dataset-sync-<gün>`);
 * hata metinleri "Gün listesi okunamadı. Tekrar dene." / "İşlem tamamlanamadı." (başarılı yenile okuma hatasını temizler, toplama hatası liste okumasında kaybolmaz).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetScreen(
    runtime: DatasetRuntime,
    lang: Lang,
    shiftDay: String?,
    modifier: Modifier = Modifier,
    onManual: (String) -> Unit = {},
    onShare: (String) -> Unit = {},
) {
    var days by remember { mutableStateOf<List<DatasetDayInfo>>(emptyList()) }
    var listError by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val open = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        try {
            days = runtime.list()
            listError = false
        } catch (_: Exception) {
            listError = true
        }
    }

    fun act(clearAction: Boolean, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            if (clearAction) actionError = false
            try {
                block()
            } catch (_: Exception) {
                actionError = true
            }
            reload()
            busy = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(DatasetText.TITLE.get(lang), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(
                onClick = { act(true) {} },
                Modifier.testTag("dataset-refresh").semantics { contentDescription = DatasetText.REFRESH.get(lang) },
            ) { Text("↻") }
        }
        Text(DatasetText.DESCRIPTION.get(lang), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { act(true) { runtime.collect(shiftDay ?: LocalDate.now().toString()) } }, Modifier.testTag("dataset-collect-shift")) {
                Text(DatasetText.COLLECT_TODAY.get(lang))
            }
            OutlinedButton(onClick = { picking = true }, Modifier.testTag("dataset-other-day")) { Text(DatasetText.OTHER_DAY.get(lang)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (actionError) Text(DatasetText.ERROR_ACTION.get(lang), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (listError) Text(DatasetText.ERROR_LIST.get(lang), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (days.isEmpty() && !listError) Text(DatasetText.NO_DAYS.get(lang), style = MaterialTheme.typography.bodyMedium)
        for (d in days) DatasetDayCard(d, lang, d.day in open, { if (d.day in open) open.remove(d.day) else open.add(d.day) }, { act(false) { runtime.collect(d.day) } }, { onManual(d.day) }, { onShare(d.day) })
    }

    if (picking) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    state.selectedDateMillis?.let { ms -> val day = Instant.ofEpochMilli(ms).atZone(ZoneId.of("UTC")).toLocalDate().toString(); act(true) { runtime.collect(day) } }
                }) { Text(DatasetText.COLLECT.get(lang)) }
            },
        ) { DatePicker(state) }
    }
}

@Composable
private fun DatasetDayCard(d: DatasetDayInfo, lang: Lang, expanded: Boolean, onToggle: () -> Unit, onCollect: () -> Unit, onManual: () -> Unit, onShare: () -> Unit) {
    val unknown = DatasetText.UNKNOWN.get(lang)
    Card(Modifier.fillMaxWidth().testTag("dataset-day-${d.day}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                d.folder.replace("_", " · "),
                Modifier.fillMaxWidth().clickable(onClick = onToggle).testTag("dataset-day-header-${d.day}"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (expanded) {
                val total = d.matched + d.onlyDayLog + d.onlyLive
                Text(DatasetText.OFFERS.fmt(lang, d.wolt, d.bolt))
                Text(DatasetText.MATCH.fmt(lang, pct(if (total == 0) null else d.matched.toDouble() / total, unknown), d.matched))
                Text(DatasetText.ONLY.fmt(lang, d.onlyDayLog, d.onlyLive))
                Text(DatasetText.ROUTE_RATIO.fmt(lang, pct(d.routeComputedRatio, unknown)))
                Text(DatasetText.EMPTY_RATIO.fmt(lang, pct(d.emptyFieldRatio, unknown)))
                Text(DatasetText.SCOOTER_COVERAGE.fmt(lang, pct(d.scooterCoverageRatio, unknown)))
                Text(if (d.collectionComplete == true) DatasetText.COLLECTED.get(lang) else DatasetText.INCOMPLETE.get(lang))
                Text(
                    if (d.missingManual.isEmpty()) DatasetText.MANUAL_DONE.get(lang)
                    else DatasetText.MANUAL_MISSING.fmt(lang, d.missingManual.joinToString(", ") { DatasetText.manualLabel(it, lang) }),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCollect, Modifier.testTag("dataset-sync-${d.day}")) { Text(DatasetText.COLLECT.get(lang)) }
                    OutlinedButton(onClick = onManual, Modifier.testTag("dataset-manual-${d.day}")) { Text(DatasetText.MANUAL.get(lang)) }
                    OutlinedButton(onClick = onShare, Modifier.testTag("dataset-share-${d.day}")) { Text(DatasetText.SHARE.get(lang)) }
                }
            }
        }
    }
}
