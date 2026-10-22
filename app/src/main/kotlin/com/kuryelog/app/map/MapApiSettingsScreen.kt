package com.kuryelog.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kuryelog.app.R
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.IconBox
import com.kuryelog.app.ui.SubScreen
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.input.ApiChecks
import com.kuryelog.logic.map.input.ApiText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Daha fazla → Harita ayarları → "API ve bağlantılar" (YolGo `api_settings.dart`; MAP.md 8.9): Google Maps SDK, Google Places (New), OpenRouteService, rota motoru kartları.
 * Denetim yalnız düğmeyle; aynı kart 5 dk içinde yeniden denetlenmez; 12 sn zaman aşımı ya da hata → genel metin; altında "Son kontrol HH:mm". Anahtar kaydedilince sonuçlar silinir.
 * CARTO ve "Google Maps uyumluluk modu" WEEM'de yok; Open-Meteo kartı hava ile (A6.7) eklenir. Anahtarlar yalnız `MapSettings`'te, hiçbir kayda yazılmaz.
 */
@Composable
fun MapApiSettingsScreen(lang: Lang, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by MapSettingsRepo.settings(ctx).collectAsState()
    val apkKey = stringResource(R.string.google_maps_key)
    val status = remember { mutableStateMapOf<String, String>() }
    val checkedAt = remember { mutableStateMapOf<String, Long>() }
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun check(id: String, run: suspend () -> String) {
        if (busy[id] == true || !ApiChecks.canCheck(checkedAt[id], System.currentTimeMillis())) return
        busy[id] = true
        scope.launch {
            val text = try {
                withTimeout(ApiChecks.CHECK_TIMEOUT_MS) { run() }
            } catch (e: CancellationException) {
                if (e is kotlinx.coroutines.TimeoutCancellationException) ApiChecks.checkFailed(lang) else throw e
            } catch (_: Exception) {
                ApiChecks.checkFailed(lang)
            }
            status[id] = text
            checkedAt[id] = System.currentTimeMillis()
            busy[id] = false
        }
    }

    SubScreen(ApiText.TITLE.get(lang), Str.BACK.get(lang), onBack, Modifier.testTag("api-settings")) {
        ApiCard("maps", ApiText.MAPS_TITLE.get(lang), ApiText.MAPS_INFO.get(lang), lang, status, checkedAt, busy, WeemIcons.Map) {
            check("maps") {
                val s = MapsKeyStatus.read(ctx, apkKey)
                ApiChecks.mapsSdk(s.hasKey, s.playServices, lang) { ApiChecks.probeGoogleKey(AndroidHttp, ApkGoogleConfig.credentials(ctx, ""), lang) }
            }
        }
        Text(ApiText.MAPS_KEY_APK.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = WeemSpace.xs))
        ApiCard("search", ApiText.PLACES_TITLE.get(lang), ApiText.PLACES_INFO.get(lang), lang, status, checkedAt, busy, WeemIcons.Search, keyValue = settings.googleKey,
            onEdit = { editing = "googleKey" to "Google Places / Geocoding" }) {
            check("search") {
                ApiChecks.placesVerdict(ApiChecks.probeGoogleKey(AndroidHttp, ApkGoogleConfig.credentials(ctx, settings.googleKey), lang), lang)
            }
        }
        ApiCard("ors", ApiText.ORS_TITLE.get(lang), ApiText.ORS_INFO.get(lang), lang, status, checkedAt, busy, WeemIcons.AltRoute, keyValue = settings.orsKey,
            onEdit = { editing = "orsKey" to "OpenRouteService" }) {
            check("ors") { ApiChecks.probeOrs(AndroidHttp, settings.orsKey, lang) }
        }
        ApiCard("route", ApiText.ROUTE_TITLE.get(lang), ApiText.ROUTE_INFO.get(lang), lang, status, checkedAt, busy, WeemIcons.Route) {
            check("route") { ApiChecks.routeEngine(MapData.controller(ctx).state.value.graph?.edgeCount, lang) }
        }
        Text("${ctx.packageName} · ${ApiText.FOOTER.get(lang)}", style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    editing?.let { (field, title) ->
        KeyDialog(title, if (field == "googleKey") settings.googleKey else settings.orsKey, lang, onDismiss = { editing = null }) { v ->
            editing = null
            MapSettingsRepo.update(ctx) { if (field == "googleKey") it.copy(googleKey = v) else it.copy(orsKey = v) }
            status.clear(); checkedAt.clear()
        }
    }
}

@Composable
private fun ApiCard(
    id: String, title: String, info: String, lang: Lang,
    status: Map<String, String>, checkedAt: Map<String, Long>, busy: Map<String, Boolean>, icon: ImageVector,
    keyValue: String? = null, onEdit: () -> Unit = {}, onCheck: () -> Unit,
) {
    WCard(Modifier.testTag("api-$id")) {
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
            IconBox(icon, tone = WTone.ACCENT)
            Text(title, style = WeemType.titleM, modifier = Modifier.weight(1f))
        }
        Text(info, style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (keyValue != null) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(WeemRadius.tile)).padding(start = WeemSpace.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(WeemIcons.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(ApiChecks.maskedKey(keyValue, lang), style = WeemType.numS, modifier = Modifier.weight(1f).padding(start = WeemSpace.s))
                WButton(ApiText.EDIT_KEY.get(lang), onEdit, kind = BtnKind.TEXT, icon = WeemIcons.Edit)
            }
        }
        status[id]?.let { s ->
            val at = checkedAt[id]?.let { SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(it)) } ?: "—"
            Text("$s\n${ApiChecks.lastChecked(at, lang)}", style = WeemType.bodyM, modifier = Modifier.testTag("api-$id-status"))
        }
        WButton(ApiText.CHECK.get(lang), onCheck, kind = BtnKind.OUTLINE, enabled = busy[id] != true, busy = busy[id] == true, icon = WeemIcons.Refresh)
    }
}

@Composable
private fun KeyDialog(title: String, value: String, lang: Lang, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    var visible by remember { mutableStateOf(false) }
    ConfirmDialog(title = title, confirm = ApiText.SAVE.get(lang), dismiss = ApiText.CANCEL.get(lang), onConfirm = { onSave(text.trim()) }, onDismiss = onDismiss) {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true, label = { Text(ApiText.KEY_LABEL.get(lang)) },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), shape = RoundedCornerShape(WeemRadius.tile),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) WeemIcons.VisibilityOff else WeemIcons.Key, contentDescription = ApiText.KEY_LABEL.get(lang))
                }
            },
            modifier = Modifier.testTag("api-key-field"),
        )
    }
}
