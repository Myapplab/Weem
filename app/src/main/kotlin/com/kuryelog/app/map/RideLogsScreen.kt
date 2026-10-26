package com.kuryelog.app.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.EmptyState
import com.kuryelog.app.ui.IconBox
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.SwitchRow
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
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WeemTopBar(RideLogText.TITLE.get(lang), onBack, Str.BACK.get(lang), backTag = "ridelogs-back") {
            WIconButton(WeemIcons.Refresh, RideLogText.REFRESH.get(lang), actions.onRefresh, Modifier.testTag("ridelogs-refresh"))
            Box {
                WIconButton(WeemIcons.Download, RideLogText.SAVE_ALL_HINT.get(lang), { menu = true }, Modifier.testTag("ridelogs-menu"))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(RideLogText.SAVE_ALL.get(lang)) }, leadingIcon = { Icon(WeemIcons.Download, null) }, onClick = { menu = false; actions.onSave(null, null, true) })
                    DropdownMenuItem(text = { Text(RideLogText.SHARE_ALL.get(lang)) }, leadingIcon = { Icon(WeemIcons.Share, null) }, onClick = { menu = false; actions.onShare(null, null, true) })
                }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = WeemSpace.l, end = WeemSpace.l, bottom = WeemSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(WeemSpace.m),
        ) {
            RideLogStatusText(logging, dayActive, lang)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            message?.let { Banner(it, tone = WTone.NEUTRAL) }
            error?.let { Banner(it, tone = WTone.DANGER) }
            SettingsGroup(null, footer = RideLogText.INFO.get(lang)) {
                SwitchRow(
                    RideLogText.SWITCH_TITLE.get(lang), logging, { actions.onLoggingChange(!logging) }, subtitle = RideLogText.SWITCH_SUB.get(lang),
                    icon = WeemIcons.FiberManualRecord, tag = "ridelogs-switch",
                )
            }
            when {
                groups == null -> Unit
                groups.isEmpty() -> EmptyState(RideLogText.EMPTY.get(lang), Modifier.testTag("ridelogs-empty"), icon = WeemIcons.History)
                else -> for ((day, list) in groups) {
                    DayCard(day, list, lang, zone, day in open, active, actions, { if (day in open) open.remove(day) else open.add(day) }, { pendingDay = day }, { pendingOne = it })
                }
            }
        }
    }
    pendingDay?.let { day ->
        val dayActive2 = entries.orEmpty().any { it.day == day && active(it) }
        ConfirmDialog(
            title = RideLogText.DELETE_DAY_TITLE.fmt(lang, ddmmyyyy(day)),
            text = (if (dayActive2) RideLogText.DELETE_DAY_ACTIVE.get(lang) else "") + RideLogText.DELETE_DAY_BODY.get(lang),
            confirm = RideLogText.DELETE_DAY_CONFIRM.get(lang), dismiss = RideLogText.CANCEL.get(lang), danger = true, confirmTag = "ridelogs-delete-day-confirm",
            onConfirm = { pendingDay = null; actions.onDeleteDay(day) }, onDismiss = { pendingDay = null },
        )
    }
    pendingOne?.let { e ->
        ConfirmDialog(
            title = RideLogText.DELETE_ONE_TITLE.get(lang), text = RideLogText.DELETE_ONE_BODY.get(lang),
            confirm = RideLogText.DELETE_ONE_CONFIRM.get(lang), dismiss = RideLogText.CANCEL.get(lang), danger = true, confirmTag = "ridelogs-delete-one-confirm",
            onConfirm = { pendingOne = null; actions.onDeleteOne(e) }, onDismiss = { pendingOne = null },
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
    val p = palette()
    WCard(Modifier.testTag("ridelogs-day-$day"), padding = 0.dp, spacing = 0.dp) {
        Row(Modifier.padding(end = WeemSpace.xs), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(onClick = onToggle).testTag("ridelogs-day-header-$day").padding(start = WeemSpace.l, top = WeemSpace.m, bottom = WeemSpace.m),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBox(WeemIcons.History)
                Column(Modifier.weight(1f)) {
                    Text(ddmmyyyy(day), style = WeemType.titleS)
                    Text(RideLogText.DAY_SUBTITLE.fmt(lang, list.count { it.kind == "ride" }, list.size), style = WeemType.bodyS, color = p.muted.color())
                }
                Icon(if (expanded) WeemIcons.ExpandLess else WeemIcons.ExpandMore, contentDescription = null, tint = p.muted.color())
            }
            Box {
                WIconButton(WeemIcons.MoreVert, RideLogText.DAY_MENU_HINT.get(lang), { menu = true }, Modifier.testTag("ridelogs-day-menu-$day"))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(RideLogText.DAY_SAVE.get(lang)) }, leadingIcon = { Icon(WeemIcons.Download, null) }, onClick = { menu = false; actions.onSave(null, day, false) })
                    DropdownMenuItem(text = { Text(RideLogText.DAY_SHARE.get(lang)) }, leadingIcon = { Icon(WeemIcons.Share, null) }, onClick = { menu = false; actions.onShare(null, day, false) })
                    DropdownMenuItem(
                        text = { Text(RideLogText.DAY_DELETE.get(lang), color = p.danger.color()) }, leadingIcon = { Icon(WeemIcons.Delete, null, tint = p.danger.color()) },
                        onClick = { menu = false; onDeleteDay() }, modifier = Modifier.testTag("ridelogs-day-delete-$day"),
                    )
                }
            }
        }
        AnimatedVisibility(expanded) {
            Column {
                HorizontalDivider(thickness = 1.dp, color = p.outline.color())
                for (e in list) EntryRow(e, lang, zone, active(e), actions, onDeleteOne)
            }
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
    val p = palette()
    val icon = when (e.kind) {
        "route" -> WeemIcons.Route
        "ride" -> WeemIcons.TwoWheeler
        "report" -> WeemIcons.ReceiptLong
        else -> WeemIcons.Schedule
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = WeemSpace.l, end = WeemSpace.xs, top = WeemSpace.xs, bottom = WeemSpace.xs).testTag("ridelogs-entry-${e.name}"),
        horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = (if (isActive) p.danger else p.muted).color(), modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = WeemType.bodyM)
            Text(String.format(Locale.US, "%.2f MB", e.sizeBytes / 1_048_576.0) + if (isActive) RideLogText.ACTIVE_SUFFIX.get(lang) else "", style = WeemType.bodyS, color = p.muted.color())
        }
        Box {
            WIconButton(WeemIcons.MoreVert, Str.MORE_ACTIONS.get(lang), { menu = true }, Modifier.testTag("ridelogs-entry-menu-${e.name}"))
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(RideLogText.SAVE.get(lang)) }, leadingIcon = { Icon(WeemIcons.Download, null) }, onClick = { menu = false; actions.onSave(e, null, false) })
                DropdownMenuItem(text = { Text(RideLogText.SHARE.get(lang)) }, leadingIcon = { Icon(WeemIcons.Share, null) }, onClick = { menu = false; actions.onShare(e, null, false) })
                if (!isActive) DropdownMenuItem(
                    text = { Text(RideLogText.DELETE_ONE.get(lang), color = p.danger.color()) }, leadingIcon = { Icon(WeemIcons.Delete, null, tint = p.danger.color()) },
                    onClick = { menu = false; onDeleteOne(e) }, modifier = Modifier.testTag("ridelogs-entry-delete-${e.name}"),
                )
            }
        }
    }
}
