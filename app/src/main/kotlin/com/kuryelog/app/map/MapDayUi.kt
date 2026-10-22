package com.kuryelog.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.RadioRow
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WSheet
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.floating
import com.kuryelog.app.ui.mark
import com.kuryelog.app.ui.palette
import com.kuryelog.app.ui.withoutGlyph
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayTone
import com.kuryelog.logic.map.geo.Baku
import com.kuryelog.logic.map.graph.Poi
import com.kuryelog.logic.map.ui.MapToast
import com.kuryelog.logic.map.ui.PlaceText
import com.kuryelog.logic.map.ui.PoiEditRules
import com.kuryelog.logic.map.ui.RegionInfo
import com.kuryelog.logic.map.ui.ToastTone

/** Gün motoru ve bisiklet bildirimi (MAP.md 7.11, 11.14): tek şerit, [id] değişince yeni bildirim; "Geri al" düğmesi varsa onunla. */
class DayBanner(val id: Long, val text: String, val tone: ToastTone, val seconds: Int, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

internal fun DayTone.toTone(): ToastTone = when (this) {
    DayTone.GREEN -> ToastTone.GREEN
    DayTone.PINK -> ToastTone.PURPLE
    DayTone.ORANGE -> ToastTone.ORANGE
}

internal fun MapToast.toBanner(id: Long) = DayBanner(id, text, tone, seconds, actionLabel, onAction)

@Composable
fun DayToastBanner(banner: DayBanner?, dark: Boolean) {
    var shown by remember { mutableStateOf<DayBanner?>(null) }
    LaunchedEffect(banner?.id) {
        shown = banner
        if (banner != null) {
            kotlinx.coroutines.delay(banner.seconds * 1000L)
            if (shown?.id == banner.id) shown = null
        }
    }
    val t = shown ?: return
    val p = palette()
    val (icon, tone) = when (t.tone) {
        ToastTone.GREEN -> WeemIcons.CheckCircleFilled to WTone.POSITIVE
        ToastTone.PURPLE -> WeemIcons.InfoFilled to WTone.CUSTOMER
        ToastTone.ORANGE -> WeemIcons.WarningFilled to WTone.WARN
        ToastTone.RED -> WeemIcons.ErrorFilled to WTone.DANGER
        ToastTone.BIKEWAY -> WeemIcons.DirectionsBikeFilled to WTone.ACCENT
    }
    val shape = RoundedCornerShape(WeemRadius.card)
    Surface(
        shape = shape,
        color = p.surface.color(),
        contentColor = p.text.color(),
        modifier = Modifier.fillMaxWidth().padding(top = WeemSpace.xs).floating(shape).testTag("day-toast"),
    ) {
        Row(Modifier.padding(start = WeemSpace.l, end = WeemSpace.xs, top = WeemSpace.xs, bottom = WeemSpace.xs).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = p.mark(tone).color(), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(WeemSpace.m))
            Text(t.text.withoutGlyph(), style = WeemType.titleS, modifier = Modifier.weight(1f).clickable { shown = null }.padding(vertical = WeemSpace.s))
            val action = t.onAction
            if (t.actionLabel != null && action != null) {
                WButton(t.actionLabel, { action(); shown = null }, kind = BtnKind.TEXT)
            }
        }
    }
}

/**
 * Yer düzeltme penceresi (MAP.md 5.3 `poi_editor`): ad (en çok 100, kelime başı büyük), tür seçici (Restoran, Fast food, Kafe, Mağaza, AVM); yeni işletme basılan noktaya,
 * düzeltme orijinalin konumuna yazılır; ad boşsa "Kaydet" pasif. [initial] null → ekleme.
 */
@Composable
fun PoiEditDialog(initial: Poi?, lang: Lang, onSave: (name: String, kind: Char) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var kind by remember { mutableStateOf(PoiEditRules.kindOrDefault(initial?.kind ?: 'r')) }
    ConfirmDialog(
        title = (if (initial == null) PlaceText.ADD_TITLE else PlaceText.EDIT_TITLE).get(lang),
        confirm = PlaceText.SAVE.get(lang), dismiss = PlaceText.CANCEL.get(lang), confirmEnabled = name.isNotBlank(),
        onConfirm = { onSave(PoiEditRules.normalizeName(name, lang), kind) }, onDismiss = onDismiss,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(PoiEditRules.MAX_NAME) },
                label = { Text(PlaceText.NAME.get(lang)) },
                singleLine = true,
                shape = RoundedCornerShape(WeemRadius.tile),
                modifier = Modifier.fillMaxWidth().testTag("poi-name"),
            )
            Column(Modifier.selectableGroup()) {
                for (k in PoiEditRules.KINDS) RadioRow(PoiEditRules.kindLabel(k).get(lang), kind == k, { kind = k })
            }
            Text(PlaceText.NOTE.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Bölge bilgisi sayfasının içeriği (MAP.md 5.4): iki çerçevenin boyutu, açıklaması ve OSM'de gösteren düğmeler. */
@Composable
fun RegionInfoContent(lang: Lang, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("region-info"), verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
        Text(PlaceText.REGION_TITLE.get(lang), style = WeemType.titleL)
        for ((title, text) in listOf(
            PlaceText.CENTRAL_TITLE.format(lang, Baku.centralBox.sizeKmLabel) to PlaceText.CENTRAL_TEXT.get(lang),
            PlaceText.WIDE_TITLE.format(lang, Baku.wideBox.sizeKmLabel) to PlaceText.WIDE_TEXT.get(lang),
        )) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(WeemRadius.card)).padding(WeemSpace.m)) {
                Text(title, style = WeemType.titleS)
                Text(text, style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            WButton(PlaceText.BTN_CENTRAL.get(lang), { onOpenUrl(RegionInfo.osmBoxUrl(Baku.centralBox)) }, Modifier.weight(1f), kind = BtnKind.OUTLINE, icon = WeemIcons.OpenInNew)
            WButton(PlaceText.BTN_WIDE.get(lang), { onOpenUrl(RegionInfo.osmBoxUrl(Baku.wideBox)) }, Modifier.weight(1f), kind = BtnKind.OUTLINE, icon = WeemIcons.OpenInNew)
        }
        Text(PlaceText.REGION_NOTE.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RegionInfoSheet(lang: Lang, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    WSheet(onDismiss = onDismiss) {
        RegionInfoContent(lang, onOpenUrl = { runCatching { uri.openUri(it) } }, modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))
    }
}
