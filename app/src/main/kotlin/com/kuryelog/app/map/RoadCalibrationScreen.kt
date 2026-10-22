package com.kuryelog.app.map

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.EmptyState
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.SectionLabel
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.Skeleton
import com.kuryelog.app.ui.SubScreen
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WChip
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.calibration.CalibrationPhase
import com.kuryelog.logic.map.calibration.CalibrationSpeedMode
import com.kuryelog.logic.map.calibration.CalibrationText
import com.kuryelog.logic.map.calibration.RoadCalibrationRecord
import com.kuryelog.logic.map.calibration.RoadCalibrationReferences
import com.kuryelog.logic.map.calibration.RoadQuality
import com.kuryelog.logic.map.surface.SurfaceText

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
    val p = palette()
    SubScreen(
        CalibrationText.TITLE, Str.BACK.get(lang), onBack,
        actions = {
            WButton("JSON", {
                runCatching { val s = rt.calibrationRecords(); shareFile(ctx, "WEEM-yol-kalibrasyonu.json", java.io.File(RoadCalibrationService.store(ctx).export()).readBytes()); s }.onFailure { toast(CalibrationText.failed(it.message)) }
            }, kind = BtnKind.TEXT, icon = WeemIcons.Share, enabled = loaded)
        },
    ) {
        WCard {
            Text(CalibrationText.FINISHED, style = WeemType.titleM)
            Text(CalibrationText.profileLine(profile.name), style = WeemType.bodyS, color = p.muted.color())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                for (b in RoadCalibrationReferences.bands) WChip(CalibrationText.bandLabel(b), band == b, { band = b }, Modifier.testTag("band-$b"))
            }
            SurfaceText.missingLevelsPrompt(refs, band, lang)?.let { Banner(it, Modifier.testTag("missing-levels"), tone = WTone.WARN) }
            for (c in refs.conflicts(band)) Banner(SurfaceText.conflictLine(c, lang), tone = WTone.DANGER)
        }
        SettingsGroup(null, dividerInset = 52.dp) {
            for (q in RoadQuality.entries) {
                val row = CalibrationText.levelRow(q, refs, band)
                ListRow(
                    row.title, subtitle = row.subtitle,
                    leading = {
                        Icon(
                            if (row.complete) WeemIcons.CheckCircleFilled else WeemIcons.FiberManualRecord, contentDescription = null,
                            tint = (if (row.complete) p.positive else p.muted).color(), modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        }
        Text(CalibrationText.thresholds(refs, band), style = WeemType.bodyM)
        Text(CalibrationText.TRIM_HELP, style = WeemType.bodyS, color = p.muted.color())
        if (!loaded) Skeleton(height = 56.dp)
        if (loaded && records.isEmpty()) EmptyState(CalibrationText.EMPTY, icon = WeemIcons.Tune)
        for (r in records.filter { !it.isLegacy }) RecordCard(r, refs, profile.id, lang) { runCatching { RoadCalibrationService.store(ctx).delete(r.id) }; rev++ }
        if (records.any { it.isLegacy }) {
            SectionLabel(CalibrationText.LEGACY_TITLE)
            Text(CalibrationText.LEGACY_HELP, style = WeemType.bodyS, color = p.muted.color())
            for (r in records.filter { it.isLegacy }) RecordCard(r, refs, profile.id, lang) { runCatching { RoadCalibrationService.store(ctx).delete(r.id) }; rev++ }
        }
        SectionLabel(CalibrationText.ADD)
        WCard {
            Text(CalibrationText.HOW_TO, style = WeemType.bodyM)
            Text(CalibrationText.LEVELS_HELP, style = WeemType.bodyS, color = p.muted.color())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s), verticalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
                for (n in 1..10) WChip("$n/10", level == n, { level = n }, Modifier.testTag("road-calibration-level-$n"), enabled = !busy)
            }
            level?.let { Text(CalibrationText.levelLine(it), style = WeemType.titleS) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s), verticalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
                for (n in CalibrationText.PRESET_SECONDS) WChip(CalibrationText.secondsLabel(n), seconds == n, { seconds = n }, enabled = !busy)
                WChip(CalibrationText.customLabel(seconds), seconds != null && seconds !in CalibrationText.PRESET_SECONDS, { custom = true }, enabled = !busy, icon = WeemIcons.Edit)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s), verticalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
                for (m in CalibrationSpeedMode.entries) WChip(CalibrationText.modeLabel(m), mode == m, { mode = m }, enabled = !busy)
            }
        }
        if (state.phase.active || state.phase == CalibrationPhase.DONE) WCard(live = true) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
                Text(CalibrationText.phaseTitle(state), style = WeemType.titleM)
                if (state.phase == CalibrationPhase.RECORDING) Text(CalibrationText.remaining(state.remaining), style = WeemType.numM)
                if (state.phase == CalibrationPhase.DONE) Text(state.message, style = WeemType.bodyM)
                if (state.phase == CalibrationPhase.RECORDING || state.phase == CalibrationPhase.COUNTDOWN) {
                    WButton(CalibrationText.FINISH, { RoadCalibrationService.finish() }, kind = BtnKind.DANGER, icon = WeemIcons.Stop)
                }
            }
        }
        WButton(CalibrationText.START, { launch(true) }, Modifier.testTag("road-calibration-start"), enabled = !busy && level != null && seconds != null, icon = WeemIcons.PlayArrow, ride = true, fill = true)
        WButton(CalibrationText.SHOW_BUBBLE, { launch(false) }, kind = BtnKind.OUTLINE, enabled = !busy, fill = true)
        if (state.visible && !state.phase.active) WButton(CalibrationText.HIDE_BUBBLE, { RoadCalibrationService.hide() }, kind = BtnKind.TEXT, fill = true)
    }
    if (custom) CustomSecondsDialog(onPick = { seconds = it; custom = false }, onDismiss = { custom = false })
}

@Composable
private fun RecordCard(r: RoadCalibrationRecord, refs: RoadCalibrationReferences, profile: String, lang: Lang, onDelete: () -> Unit) {
    val card = remember(r, refs, profile, lang) { CalibrationText.recordCard(r, refs, profile, lang) }
    WCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(card.title, style = WeemType.titleS)
                for (l in card.lines) Text(l, style = WeemType.bodyS)
                card.score?.let { v -> for (l in v.lines) Text(l, style = WeemType.bodyS) }
            }
            WButton(CalibrationText.DELETE, onDelete, kind = BtnKind.DANGER, icon = WeemIcons.Delete)
        }
    }
}

@Composable
private fun CustomSecondsDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val value = text.toIntOrNull()
    ConfirmDialog(
        title = CalibrationText.CUSTOM, confirm = CalibrationText.PICK, dismiss = CalibrationText.CANCEL, confirmEnabled = value != null && value in 1..600,
        onConfirm = { onPick(value!!) }, onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            text, { text = it.filter(Char::isDigit) }, label = { Text(CalibrationText.CUSTOM_RANGE) }, singleLine = true, shape = RoundedCornerShape(WeemRadius.tile),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}
