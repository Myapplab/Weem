package com.kuryelog.app.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayTone
import com.kuryelog.logic.map.geo.Baku
import com.kuryelog.logic.map.graph.Poi
import com.kuryelog.logic.map.ui.MapPalette
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
    val pal = MapPalette.of(dark)
    val color = Color(
        when (t.tone) {
            ToastTone.GREEN -> pal.green
            ToastTone.PURPLE -> pal.pink
            ToastTone.ORANGE -> pal.orange
            ToastTone.RED -> pal.red
            ToastTone.BIKEWAY -> if (dark) 0xFF3DDCE8.toInt() else 0xFF00838F.toInt()
        },
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .97f),
        border = BorderStroke(1.5.dp, color),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("day-toast"),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).clickable { shown = null })
            val action = t.onAction
            if (t.actionLabel != null && action != null) {
                TextButton(onClick = { action(); shown = null }) { Text(t.actionLabel, color = color) }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (initial == null) PlaceText.ADD_TITLE else PlaceText.EDIT_TITLE).get(lang)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(PoiEditRules.MAX_NAME) },
                    label = { Text(PlaceText.NAME.get(lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("poi-name"),
                )
                for (k in PoiEditRules.KINDS) {
                    Row(Modifier.fillMaxWidth().selectable(selected = kind == k, role = Role.RadioButton, onClick = { kind = k }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = kind == k, onClick = null)
                        Text(PoiEditRules.kindLabel(k).get(lang), Modifier.padding(start = 8.dp))
                    }
                }
                Text(PlaceText.NOTE.get(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            Button(onClick = { onSave(PoiEditRules.normalizeName(name, lang), kind) }, enabled = name.isNotBlank()) { Text(PlaceText.SAVE.get(lang)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(PlaceText.CANCEL.get(lang)) } },
    )
}

/** Bölge bilgisi sayfasının içeriği (MAP.md 5.4): iki çerçevenin boyutu, açıklaması ve OSM'de gösteren düğmeler. */
@Composable
fun RegionInfoContent(lang: Lang, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(20.dp).testTag("region-info"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(PlaceText.REGION_TITLE.get(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(PlaceText.CENTRAL_TITLE.format(lang, Baku.centralBox.sizeKmLabel), fontWeight = FontWeight.SemiBold)
        Text(PlaceText.CENTRAL_TEXT.get(lang), style = MaterialTheme.typography.bodySmall)
        Text(PlaceText.WIDE_TITLE.format(lang, Baku.wideBox.sizeKmLabel), fontWeight = FontWeight.SemiBold)
        Text(PlaceText.WIDE_TEXT.get(lang), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onOpenUrl(RegionInfo.osmBoxUrl(Baku.centralBox)) }) { Text(PlaceText.BTN_CENTRAL.get(lang)) }
            OutlinedButton(onClick = { onOpenUrl(RegionInfo.osmBoxUrl(Baku.wideBox)) }) { Text(PlaceText.BTN_WIDE.get(lang)) }
        }
        Text(PlaceText.REGION_NOTE.get(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionInfoSheet(lang: Lang, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        RegionInfoContent(lang, onOpenUrl = { runCatching { uri.openUri(it) } }, modifier = Modifier.verticalScroll(rememberScrollState()))
    }
}
