package com.kuryelog.app.map

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.calibration.CalibrationPhase
import com.kuryelog.logic.map.calibration.CalibrationSpeedMode
import com.kuryelog.logic.map.calibration.CalibrationText
import com.kuryelog.logic.map.calibration.RoadCalibrationReferences
import com.kuryelog.logic.map.calibration.RoadCalibrationRecord
import com.kuryelog.logic.map.calibration.RoadQuality
import com.kuryelog.logic.map.surface.SurfaceText
import org.json.JSONObject

// Y14 Yol Kalitesi Kalibrasyonu ekranı (YolGo `road_calibration_screen.dart` @ 870ab6e): biten kalibrasyonlar, hız bandı başına seviye durumu,
// kalibrasyon ekle (1–10, süre, hız kipi) ve yüzen paneli açma. Metin ve sıra `logic/map` `CalibrationText`'te (testli); burada yalnız çizim.
// Hesap ve kayıt `RoadCalibrationService`'te. Cihazda denenmedi.

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoadCalibrationScreen(lang: Lang, initialBand: Int = 20, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rt = remember { NavRuntime.get(ctx).surface }
    val state by RoadCalibrationService.state.collectAsState()
    var records by remember { mutableStateOf<List<RoadCalibrationRecord>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var rev by remember { mutableIntStateOf(0) }
    var band by remember { mutableIntStateOf(if (initialBand in RoadCalibrationReferences.bands) initialBand else 20) }
    var level by remember { mutableStateOf<Int?>(null) }
    var seconds by remember { mutableStateOf<Int?>(null) }
    var mode by remember { mutableStateOf(CalibrationSpeedMode.FIXED) }
    var custom by remember { mutableStateOf(false) }
    val profile = remember(rev) { rt.store.profile }
    // Kayıtlar ekran açılınca, bir koşu bitince (faz değişince) ve silinince yeniden okunur.
    LaunchedEffect(rev, state.phase) {
        records = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { rt.calibrationRecords() }
        loaded = true
    }
    val refs = remember(records, profile.id) { RoadCalibrationReferences(records.filter { it.profile == profile.id }) }
    val busy = state.phase.active || state.phase == CalibrationPhase.DONE
    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()
    fun launch(start: Boolean) {
        if (!RoadCalibrationService.overlayPermitted(ctx)) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        }
        runCatching {
            RoadCalibrationService.launch(ctx, start, profile.id, level, seconds, mode.name.lowercase(), profile.toJson())
        }.onFailure { toast(CalibrationText.failed(it.message)) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("←") }
            Text(CalibrationText.TITLE, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(enabled = loaded, onClick = {
                runCatching { val s = rt.calibrationRecords(); shareFile(ctx, "WEEM-yol-kalibrasyonu.json", java.io.File(RoadCalibrationService.store(ctx).export()).readBytes()); s }.onFailure { toast(CalibrationText.failed(it.message)) }
            }) { Text("JSON") }
        }
        Text(CalibrationText.FINISHED, style = MaterialTheme.typography.titleMedium)
        Text(CalibrationText.profileLine(profile.name))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (b in RoadCalibrationReferences.bands) FilterChip(selected = band == b, onClick = { band = b }, label = { Text(CalibrationText.bandLabel(b)) }, modifier = Modifier.testTag("band-$b"))
        }
        SurfaceText.missingLevelsPrompt(refs, band, lang)?.let { Text(it, modifier = Modifier.testTag("missing-levels")) }
        for (c in refs.conflicts(band)) Text(SurfaceText.conflictLine(c, lang))
        for (q in RoadQuality.entries) {
            val row = CalibrationText.levelRow(q, refs, band)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (row.complete) "✔" else "○", color = if (row.complete) androidx.compose.ui.graphics.Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
                Column { Text(row.title); Text(row.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Text(CalibrationText.thresholds(refs, band))
        Text(CalibrationText.TRIM_HELP, fontSize = 12.sp)
        if (!loaded) Text("…")
        if (loaded && records.isEmpty()) Text(CalibrationText.EMPTY, Modifier.padding(vertical = 12.dp))
        for (r in records.filter { !it.isLegacy }) RecordCard(r, refs, profile.id, lang) { runCatching { RoadCalibrationService.store(ctx).delete(r.id) }; rev++ }
        if (records.any { it.isLegacy }) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(CalibrationText.LEGACY_TITLE, style = MaterialTheme.typography.titleMedium)
            Text(CalibrationText.LEGACY_HELP)
            for (r in records.filter { it.isLegacy }) RecordCard(r, refs, profile.id, lang) { runCatching { RoadCalibrationService.store(ctx).delete(r.id) }; rev++ }
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text(CalibrationText.ADD, style = MaterialTheme.typography.titleMedium)
        Text(CalibrationText.HOW_TO)
        Text(CalibrationText.LEVELS_HELP)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in 1..10) FilterChip(selected = level == n, enabled = !busy, onClick = { level = n }, label = { Text("$n/10") }, modifier = Modifier.testTag("road-calibration-level-$n"))
        }
        level?.let { Text(CalibrationText.levelLine(it)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in CalibrationText.PRESET_SECONDS) FilterChip(selected = seconds == n, enabled = !busy, onClick = { seconds = n }, label = { Text(CalibrationText.secondsLabel(n)) })
            FilterChip(selected = seconds != null && seconds !in CalibrationText.PRESET_SECONDS, enabled = !busy, onClick = { custom = true },
                label = { Text(CalibrationText.customLabel(seconds)) })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in CalibrationSpeedMode.entries) FilterChip(selected = mode == m, enabled = !busy, onClick = { mode = m }, label = { Text(CalibrationText.modeLabel(m)) })
        }
        if (state.phase.active || state.phase == CalibrationPhase.DONE) Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(CalibrationText.phaseTitle(state), fontWeight = FontWeight.Bold)
                if (state.phase == CalibrationPhase.RECORDING) Text(CalibrationText.remaining(state.remaining))
                if (state.phase == CalibrationPhase.DONE) Text(state.message)
                if (state.phase == CalibrationPhase.RECORDING || state.phase == CalibrationPhase.COUNTDOWN) TextButton(onClick = { RoadCalibrationService.finish() }) { Text(CalibrationText.FINISH) }
            }
        }
        Button(enabled = !busy && level != null && seconds != null, onClick = { launch(true) }, modifier = Modifier.fillMaxWidth().testTag("road-calibration-start")) { Text(CalibrationText.START) }
        OutlinedButton(enabled = !busy, onClick = { launch(false) }, modifier = Modifier.fillMaxWidth()) { Text(CalibrationText.SHOW_BUBBLE) }
        if (state.visible && !state.phase.active) TextButton(onClick = { RoadCalibrationService.hide() }) { Text(CalibrationText.HIDE_BUBBLE) }
    }
    if (custom) CustomSecondsDialog(onPick = { seconds = it; custom = false }, onDismiss = { custom = false })
}

@Composable
private fun RecordCard(r: RoadCalibrationRecord, refs: RoadCalibrationReferences, profile: String, lang: Lang, onDelete: () -> Unit) {
    val card = remember(r, refs, profile, lang) { CalibrationText.recordCard(r, refs, profile, lang) }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(card.title, fontWeight = FontWeight.SemiBold)
                for (l in card.lines) Text(l, fontSize = 13.sp)
                card.score?.let { v -> for (l in v.lines) Text(l, fontSize = 13.sp) }
            }
            TextButton(onClick = onDelete) { Text(CalibrationText.DELETE) }
        }
    }
}

@Composable
private fun CustomSecondsDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val value = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(CalibrationText.CUSTOM) },
        text = { OutlinedTextField(text, { text = it.filter(Char::isDigit) }, label = { Text(CalibrationText.CUSTOM_RANGE) }, singleLine = true) },
        confirmButton = { TextButton(enabled = value != null && value in 1..600, onClick = { onPick(value!!) }) { Text(CalibrationText.PICK) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(CalibrationText.CANCEL) } },
    )
}
