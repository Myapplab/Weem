package com.kuryelog.app.map

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.EmptyState
import com.kuryelog.app.ui.StatusPill
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemTopBar
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.DatasetText
import com.kuryelog.logic.map.ui.fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch

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
    onBack: (() -> Unit)? = null,
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

    Column(modifier.fillMaxWidth()) {
        WeemTopBar(DatasetText.TITLE.get(lang), onBack, Str.BACK.get(lang)) {
            WIconButton(WeemIcons.Refresh, DatasetText.REFRESH.get(lang), { act(true) {} }, Modifier.testTag("dataset-refresh"))
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = WeemSpace.l, end = WeemSpace.l, bottom = WeemSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(WeemSpace.m),
        ) {
            Text(DatasetText.DESCRIPTION.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                WButton(DatasetText.COLLECT_TODAY.get(lang), { act(true) { runtime.collect(shiftDay ?: LocalDate.now().toString()) } }, Modifier.weight(1f).testTag("dataset-collect-shift"), icon = WeemIcons.Download)
                WButton(DatasetText.OTHER_DAY.get(lang), { picking = true }, Modifier.weight(1f).testTag("dataset-other-day"), kind = BtnKind.OUTLINE, icon = WeemIcons.Schedule)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            if (actionError) Banner(DatasetText.ERROR_ACTION.get(lang), tone = WTone.DANGER)
            if (listError) Banner(DatasetText.ERROR_LIST.get(lang), tone = WTone.DANGER)
            if (days.isEmpty() && !listError) EmptyState(DatasetText.NO_DAYS.get(lang), icon = WeemIcons.Analytics)
            for (d in days) DatasetDayCard(d, lang, d.day in open, { if (d.day in open) open.remove(d.day) else open.add(d.day) }, { act(false) { runtime.collect(d.day) } }, { onManual(d.day) }, { onShare(d.day) })
        }
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
    val p = palette()
    WCard(Modifier.testTag("dataset-day-${d.day}").animateContentSize(), padding = 0.dp, spacing = 0.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).testTag("dataset-day-header-${d.day}").heightIn(min = 56.dp).padding(horizontal = WeemSpace.l, vertical = WeemSpace.m),
            horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (d.collectionComplete == true) WeemIcons.CheckCircleFilled else WeemIcons.Analytics, contentDescription = null,
                tint = (if (d.collectionComplete == true) p.positive else p.muted).color(), modifier = Modifier.size(22.dp))
            Text(d.folder.replace("_", " · "), style = WeemType.titleS, modifier = Modifier.weight(1f))
            Icon(if (expanded) WeemIcons.ExpandLess else WeemIcons.ExpandMore, contentDescription = null, tint = p.muted.color())
        }
        if (expanded) {
            HorizontalDivider(thickness = 1.dp, color = p.outline.color())
            Column(Modifier.padding(WeemSpace.l), verticalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
                val total = d.matched + d.onlyDayLog + d.onlyLive
                Text(DatasetText.OFFERS.fmt(lang, d.wolt, d.bolt), style = WeemType.titleS)
                for (line in listOf(
                    DatasetText.MATCH.fmt(lang, pct(if (total == 0) null else d.matched.toDouble() / total, unknown), d.matched),
                    DatasetText.ONLY.fmt(lang, d.onlyDayLog, d.onlyLive),
                    DatasetText.ROUTE_RATIO.fmt(lang, pct(d.routeComputedRatio, unknown)),
                    DatasetText.EMPTY_RATIO.fmt(lang, pct(d.emptyFieldRatio, unknown)),
                    DatasetText.SCOOTER_COVERAGE.fmt(lang, pct(d.scooterCoverageRatio, unknown)),
                )) Text(line, style = WeemType.bodyM)
                StatusPill(
                    if (d.collectionComplete == true) DatasetText.COLLECTED.get(lang) else DatasetText.INCOMPLETE.get(lang),
                    if (d.collectionComplete == true) WTone.POSITIVE else WTone.WARN,
                )
                Text(
                    if (d.missingManual.isEmpty()) DatasetText.MANUAL_DONE.get(lang)
                    else DatasetText.MANUAL_MISSING.fmt(lang, d.missingManual.joinToString(", ") { DatasetText.manualLabel(it, lang) }),
                    style = WeemType.bodyS, color = p.muted.color(),
                )
                FlowRow(Modifier.padding(top = WeemSpace.s), horizontalArrangement = Arrangement.spacedBy(WeemSpace.s), verticalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                    WButton(DatasetText.COLLECT.get(lang), onCollect, Modifier.testTag("dataset-sync-${d.day}"), icon = WeemIcons.Download)
                    WButton(DatasetText.MANUAL.get(lang), onManual, Modifier.testTag("dataset-manual-${d.day}"), kind = BtnKind.OUTLINE, icon = WeemIcons.Edit)
                    WButton(DatasetText.SHARE.get(lang), onShare, Modifier.testTag("dataset-share-${d.day}"), kind = BtnKind.OUTLINE, icon = WeemIcons.Share)
                }
            }
        }
    }
}
