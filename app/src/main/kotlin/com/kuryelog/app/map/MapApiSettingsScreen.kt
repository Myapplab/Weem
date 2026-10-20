package com.kuryelog.app.map

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.app.R
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("api-settings")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("←") }
            Text(ApiText.TITLE.get(lang), style = MaterialTheme.typography.headlineMedium)
        }
        ApiCard("maps", ApiText.MAPS_TITLE.get(lang), ApiText.MAPS_INFO.get(lang), lang, status, checkedAt, busy) {
            check("maps") {
                val s = MapsKeyStatus.read(ctx, apkKey)
                ApiChecks.mapsSdk(s.hasKey, s.playServices, lang) { ApiChecks.probeGoogleKey(AndroidHttp, ApkGoogleConfig.credentials(ctx, ""), lang) }
            }
        }
        Text(ApiText.MAPS_KEY_APK.get(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        ApiCard("search", ApiText.PLACES_TITLE.get(lang), ApiText.PLACES_INFO.get(lang), lang, status, checkedAt, busy, keyValue = settings.googleKey,
            onEdit = { editing = "googleKey" to "Google Places / Geocoding" }) {
            check("search") {
                ApiChecks.placesVerdict(ApiChecks.probeGoogleKey(AndroidHttp, ApkGoogleConfig.credentials(ctx, settings.googleKey), lang), lang)
            }
        }
        ApiCard("ors", ApiText.ORS_TITLE.get(lang), ApiText.ORS_INFO.get(lang), lang, status, checkedAt, busy, keyValue = settings.orsKey,
            onEdit = { editing = "orsKey" to "OpenRouteService" }) {
            check("ors") { ApiChecks.probeOrs(AndroidHttp, settings.orsKey, lang) }
        }
        ApiCard("route", ApiText.ROUTE_TITLE.get(lang), ApiText.ROUTE_INFO.get(lang), lang, status, checkedAt, busy) {
            check("route") { ApiChecks.routeEngine(MapData.controller(ctx).state.value.graph?.edgeCount, lang) }
        }
        Text("${ctx.packageName} · ${ApiText.FOOTER.get(lang)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
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
    status: Map<String, String>, checkedAt: Map<String, Long>, busy: Map<String, Boolean>,
    keyValue: String? = null, onEdit: () -> Unit = {}, onCheck: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("api-$id")) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(info, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
            if (keyValue != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ApiChecks.maskedKey(keyValue, lang), modifier = Modifier.weight(1f))
                    TextButton(onClick = onEdit) { Text("✎ " + ApiText.EDIT_KEY.get(lang)) }
                }
            }
            status[id]?.let { s ->
                val at = checkedAt[id]?.let { SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(it)) } ?: "—"
                Text("$s\n${ApiChecks.lastChecked(at, lang)}", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).testTag("api-$id-status"))
            }
            OutlinedButton(onClick = onCheck, enabled = busy[id] != true, modifier = Modifier.padding(top = 8.dp)) { Text(ApiText.CHECK.get(lang)) }
        }
    }
}

@Composable
private fun KeyDialog(title: String, value: String, lang: Lang, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true, label = { Text(ApiText.KEY_LABEL.get(lang)) },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "◉" else "◎") } },
                modifier = Modifier.testTag("api-key-field"),
            )
        },
        confirmButton = { Button(onClick = { onSave(text.trim()) }) { Text(ApiText.SAVE.get(lang)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(ApiText.CANCEL.get(lang)) } },
    )
}
