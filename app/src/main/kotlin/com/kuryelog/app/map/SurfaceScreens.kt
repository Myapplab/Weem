package com.kuryelog.app.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuryelog.app.core.Downloads
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.EmptyState
import com.kuryelog.app.ui.IconBox
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.RadioRow
import com.kuryelog.app.ui.SectionLabel
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.SubScreen
import com.kuryelog.app.ui.SwitchRow
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WSheet
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.calibration.RoadCalibrationRecord
import com.kuryelog.logic.map.calibration.RoadCalibrationReferences
import com.kuryelog.logic.map.roads.ProfileReferences
import com.kuryelog.logic.map.roads.RoadsText
import com.kuryelog.logic.map.roads.Street
import com.kuryelog.logic.map.roads.StreetScores
import com.kuryelog.logic.map.surface.Passability
import com.kuryelog.logic.map.surface.SurfaceLabel
import com.kuryelog.logic.map.surface.SurfaceMark
import com.kuryelog.logic.map.surface.SurfaceProfile
import com.kuryelog.logic.map.surface.SurfaceSummary
import com.kuryelog.logic.map.surface.SurfaceText
import com.kuryelog.logic.map.surface.SurfaceUiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Y13/Y14 ekranları (A10.4b): Yollarım → Kalite sekmesinin "Yol puanları" bölümü, sokak parçaları, parça ayrıntısı, Ölçüm ve kalibrasyon.
// YolGo `surface_screen.dart` @ 870ab6e; metin ve sıra `logic/map` `SurfaceText`/`StreetScores`'ta (testli), burada yalnız çizim. Cihazda denenmedi.

/** Ölçülmüş parçalar, kalibrasyon kayıtları ve yol adları: ekranların ortak verisi (parçalar değişince yeniden okunur). */
internal class SurfaceData(val summaries: List<SurfaceSummary>, val records: List<RoadCalibrationRecord>, val names: Map<Long, String>) {
    val references = ProfileReferences(records)
    fun streets(): List<Street> = StreetScores.buildStreets(summaries, references::score) { names[it] }
    fun nameOf(way: Long, lang: Lang) = names[way]?.takeIf { it.isNotEmpty() } ?: SurfaceUiText.UNNAMED_ROAD.get(lang)
}

@Composable
internal fun rememberSurfaceData(): SurfaceData {
    val ctx = LocalContext.current
    val rt = remember { NavRuntime.get(ctx).surface }
    val pieces by rt.summaries.collectAsState()
    val road by NavRuntime.get(ctx).roads.book.states.collectAsState()
    val calibration by RoadCalibrationService.state.collectAsState()
    val records by produceState(emptyList<RoadCalibrationRecord>(), calibration.phase, pieces.size) { value = withContext(Dispatchers.IO) { rt.calibrationRecords() } }
    return remember(pieces, records, road.k.names.size) { SurfaceData(pieces, records, HashMap(road.k.names)) }
}

@Composable
private fun tone(q: Double) = RoadsText.tone(q).color()

/** Kalite sekmesi sokak satırı: ad, alt yazı, puan etiketi ve rota kalitesi (renk; null = "ölçüm az"). */
internal class StreetRowView(val key: String, val title: String, val subtitle: String, val score: String, val quality: Double?)

internal fun streetRows(data: SurfaceData, lang: Lang): List<StreetRowView> = data.streets().map { s ->
    StreetRowView(s.key, s.name ?: data.nameOf(s.ways.first(), lang), SurfaceText.streetSubtitle(s.scoredPieces, s.scoredMeters, lang), StreetScores.label(s.score, lang), s.score?.routeQuality)
}

/**
 * Kalite sekmesinin üstü: "Yol puanları" açıklaması, ölçüm durumu, "Ölçüm ve kalibrasyon", "Haritada yüzey renkleri" anahtarı ve sokak listesi
 * (kötüden iyiye, "ölçüm az" sonda). Sokağa dokununca [onStreet] (anahtar, ad). Çalışma zamanına bağlı hâli [SurfaceQualitySection].
 */
@Composable
internal fun SurfaceQualityPanel(lang: Lang, measurement: String, streets: List<StreetRowView>, layerOn: Boolean, onLayer: (Boolean) -> Unit, onStreet: (String, String) -> Unit, onSetup: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = WeemSpace.m), verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
        SettingsGroup(null, dividerInset = 70.dp) {
            Column(Modifier.padding(WeemSpace.l), verticalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
                Text(SurfaceUiText.ROAD_SCORES.get(lang), style = WeemType.titleM)
                Text(SurfaceText.qualityIntro(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(measurement, style = WeemType.labelL, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("surface-state"))
            }
            ListRow(SurfaceUiText.SETUP.get(lang), Modifier.testTag("surface-setup"), icon = WeemIcons.Vibration, chevron = true, onClick = onSetup)
            SwitchRow(SurfaceUiText.LAYER.get(lang), layerOn, onLayer, icon = WeemIcons.Layers)
        }
        SettingsGroup(null) {
            if (streets.isEmpty()) ListRow(SurfaceUiText.NO_MEASUREMENT.get(lang), titleStyle = WeemType.bodyM)
            for (s in streets) {
                ListRow(
                    s.title, Modifier.testTag("street-${s.key}"), subtitle = s.subtitle, onClick = { onStreet(s.key, s.title) },
                    trailing = { Text(s.score, style = WeemType.numS.copy(fontWeight = FontWeight.ExtraBold), color = s.quality?.let { tone(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        }
    }
}

@Composable
fun SurfaceQualitySection(lang: Lang, onStreet: (String, String) -> Unit, onSetup: () -> Unit) {
    val ctx = LocalContext.current
    val rt = remember { NavRuntime.get(ctx).surface }
    val data = rememberSurfaceData()
    val view by rt.view.collectAsState()
    val settings by MapSettingsRepo.settings(ctx).collectAsState()
    val rows = remember(data, lang) { streetRows(data, lang) }
    SurfaceQualityPanel(lang, SurfaceText.measurementLabel(view.decision.state, lang), rows, settings.showQualityLayer,
        { on -> MapSettingsRepo.update(ctx) { it.copy(showQualityLayer = on) } }, onStreet, onSetup)
}

/** Yol puanı satırları ([SurfaceText.roadScore]); eksik seviye satırına dokununca [onCalibrate] (bant). */
@Composable
internal fun RoadScoreLines(refs: RoadCalibrationReferences, rms: Double?, speedKmh: Double?, lang: Lang, onCalibrate: ((Int) -> Unit)?, confirmedShock: Boolean = false) {
    val v = remember(refs, rms, speedKmh, lang, confirmedShock) { SurfaceText.roadScore(refs, rms, speedKmh, lang, confirmedShock = confirmedShock) }
    Column {
        for (l in v.lines) Text(l, style = WeemType.bodyS)
        v.prompt?.let { p ->
            val band = v.promptBand
            if (onCalibrate != null && band != null) WButton(p, { onCalibrate(band) }, kind = BtnKind.TEXT, icon = WeemIcons.Tune)
            else Text(p, style = WeemType.bodyS)
        }
    }
}

/** Bir sokağın 50 m parçaları: E10 puanı, güven, geçiş, darbe; dokununca ayrıntı. */
@Composable
fun StreetPiecesScreen(lang: Lang, streetKey: String, title: String, onBack: () -> Unit, onCalibrate: (Int) -> Unit) {
    val data = rememberSurfaceData()
    var detail by remember { mutableStateOf<SurfaceSummary?>(null) }
    val street = remember(data, streetKey) {
        StreetScores.buildStreets(data.summaries.filter { StreetScores.streetKeyOf(it.way, data.names[it.way]) == streetKey }, data.references::score) { data.names[it] }.firstOrNull()
    }
    val pieces = remember(street) {
        street?.pieces?.sortedWith { a, b ->
            val av = street.scores[a.key]; val bv = street.scores[b.key]
            if (av == null || bv == null) (if (av == null) (if (bv == null) a.key.compareTo(b.key) else 1) else -1)
            else av.compareTo(bv).let { if (it == 0) a.key.compareTo(b.key) else it }
        }.orEmpty()
    }
    SubScreen(title, Str.BACK.get(lang), onBack) {
        if (street == null) EmptyState(SurfaceUiText.NO_MEASUREMENT.get(lang), icon = WeemIcons.Vibration)
        else WCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(SurfaceUiText.STREET_SCORE.get(lang), style = WeemType.titleS)
                    Text(SurfaceText.streetSubtitle(street.scoredPieces, street.scoredMeters, lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(StreetScores.label(street.score, lang), style = WeemType.numM)
            }
        }
        if (pieces.isNotEmpty()) SettingsGroup(null) {
            for (s in pieces) {
                val score = street?.scores?.get(s.key)
                val dim = s.confidence < .3 || score == null
                Column(Modifier.fillMaxWidth().clickable { detail = s }.padding(horizontal = WeemSpace.l, vertical = WeemSpace.m).testTag("road-score-${s.key}")) {
                    Text(
                        SurfaceText.pieceTitle(data.nameOf(s.way, lang), s), style = WeemType.titleS, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else tone(score!!.level / 10),
                    )
                    RoadScoreLines(data.references.of(s.profile), s.dominantRoadBand?.rms, s.dominantRoadBand?.let { it.speed * 3.6 }, lang, onCalibrate, s.confirmedShock)
                    Text(SurfaceText.pieceSubtitle(s, lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    detail?.let { SurfaceDetailDialog(lang, it, data, onCalibrate = { b -> detail = null; onCalibrate(b) }) { detail = null } }
}

/** Parça ayrıntısı: puanlar, mesafe, darbe olayları (türünü doğrula), teknik ölçümler; "Yüzey türü veya kapanış kaydet". */
@Composable
private fun SurfaceDetailDialog(lang: Lang, s: SurfaceSummary, data: SurfaceData, onCalibrate: (Int) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val rt = remember { NavRuntime.get(ctx) }
    val events by produceState(emptyList<JSONObject>(), s.key) { value = withContext(Dispatchers.IO) { runCatching { rt.surface.store.impactEvents(s.key) }.getOrDefault(emptyList()) } }
    var edit by remember { mutableStateOf<Triple<Long, Pair<Double, Double>, SurfaceMark?>?>(null) }
    var tech by remember { mutableStateOf(false) }
    val refs = remember(data, s.profile) { RoadCalibrationReferences(data.records.filter { it.profile == s.profile }) }
    // K4: uzun ayrıntı alttan açılan sayfada.
    WSheet(onDismiss = onClose, title = data.nameOf(s.way, lang)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
            RoadScoreLines(refs, s.dominantRoadBand?.rms, s.dominantRoadBand?.let { it.speed * 3.6 }, lang, onCalibrate, s.confirmedShock)
            for (l in SurfaceText.detailLines(s, lang)) Text(l, style = WeemType.bodyM)
            if (events.isNotEmpty()) {
                SectionLabel(SurfaceUiText.IMPACT_EVENTS.get(lang))
                SettingsGroup(null, dividerInset = 70.dp) {
                    for (e in events) {
                        val id = "event-${e.opt("id")}"
                        val previous = NavRuntime.get(ctx).roads.book.state.marks.firstOrNull { it.id == id }
                        ListRow(
                            SurfaceText.impactTitle(e.optDouble("peak"), lang), subtitle = SurfaceText.impactSubtitle(e.optLong("utc"), e.optDouble("accuracy"), lang),
                            icon = WeemIcons.Warning, iconTone = WTone.WARN, chevron = true,
                            onClick = { edit = Triple(s.way, e.optDouble("lat") to e.optDouble("lon"), previous ?: SurfaceMark(id, s.way, e.optDouble("lat"), e.optDouble("lon"), 0)) },
                        )
                    }
                }
            }
            WButton(SurfaceUiText.SAVE_MARK.get(lang), { edit = Triple(s.way, s.lat to s.lon, null) }, Modifier.padding(top = WeemSpace.s), kind = BtnKind.TONAL, icon = WeemIcons.Flag, fill = true)
            WButton(SurfaceUiText.TECHNICAL.get(lang), { tech = !tech }, kind = BtnKind.TEXT, icon = if (tech) WeemIcons.ExpandLess else WeemIcons.ExpandMore)
            if (tech) Text(SurfaceText.technical(s), style = WeemType.bodyS)
        }
        WButton(RoadsText.cancel(lang), onClose, kind = BtnKind.TEXT, fill = true)
    }
    edit?.let { (way, point, previous) ->
        EditSurfaceMarkDialog(lang, way, point.first, point.second, previous, onDone = { edit = null })
    }
}

/** Yüzey türü ve geçilebilirlik kaydı (elle işaret): "kapalı" işaret rotada o yolu hiç kullandırmaz. */
@Composable
private fun EditSurfaceMarkDialog(lang: Lang, way: Long, lat: Double, lon: Double, previous: SurfaceMark?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var label by remember { mutableStateOf(previous?.label ?: SurfaceLabel.UNKNOWN) }
    var access by remember { mutableStateOf(previous?.access ?: Passability.UNKNOWN) }
    WSheet(onDismiss = onDone, title = SurfaceUiText.MARK_TITLE.get(lang)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
            SettingsGroup(null, dividerInset = 52.dp, selectableGroup = true) {
                for (l in SurfaceLabel.entries) RadioRow(SurfaceText.surfaceLabel(l, lang), label == l, { label = l })
            }
            SettingsGroup(null, dividerInset = 52.dp, selectableGroup = true) {
                for (a in Passability.entries) RadioRow(SurfaceText.passability(a, lang), access == a, { access = a })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            WButton(RoadsText.cancel(lang), onDone, Modifier.weight(1f), kind = BtnKind.OUTLINE)
            WButton(SurfaceUiText.SAVE.get(lang), {
                val now = System.currentTimeMillis()
                NavRuntime.get(ctx).roads.book.saveMark(SurfaceMark(previous?.id ?: "user-$now", way, lat, lon, now, label, access))
                Toast.makeText(ctx, SurfaceUiText.MARK_SAVED.get(lang), Toast.LENGTH_SHORT).show()
                onDone()
            }, Modifier.weight(1f))
        }
    }
}

/** Ölçüm ve kalibrasyon: durum, montaj profili, duraklat, montaj yönü, kontrollü kalibrasyon etiketi, hız aralıkları, tanılama. */
@Composable
fun SurfaceSetupScreen(lang: Lang, onBack: () -> Unit, onCalibration: () -> Unit) {
    val ctx = LocalContext.current
    val rt = remember { NavRuntime.get(ctx).surface }
    val view by rt.view.collectAsState()
    val summaries by rt.summaries.collectAsState()
    var rev by remember { mutableStateOf(0) }
    val store = rt.store
    val profile = remember(rev, summaries) { store.profile }
    val vehicle = MapSettingsRepo.settings(ctx).collectAsState().value.vehicleId
    var newProfile by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(false) }
    var rawOn by remember { mutableStateOf(rt.rawEnabled) }
    // Etkinlik tanıma izni (Android 10+): verilince sensörler yeniden kaydedilir ki adım dedektörü açılsın.
    var motionGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 29 || ctx.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) }
    val askMotion = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> motionGranted = ok; rt.motionPermissionChanged(); rev++ }
    fun toast(text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
    SubScreen(SurfaceUiText.SETUP_TITLE.get(lang), Str.BACK.get(lang), onBack) {
        WCard {
            Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
                IconBox(WeemIcons.Vibration, tone = WTone.ACCENT)
                Text(SurfaceText.measurementLabel(view.decision.state, lang), style = WeemType.titleM, modifier = Modifier.weight(1f))
            }
            Text("${profile.name}\n${profile.id} · v${profile.version}", style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WButton(
                (if (rt.paused) SurfaceUiText.RESUME else SurfaceUiText.PAUSE).get(lang), { rt.pause(!rt.paused); rev++ }, Modifier.testTag("surface-pause"),
                kind = BtnKind.OUTLINE, icon = if (rt.paused) WeemIcons.PlayArrow else WeemIcons.Stop, fill = true,
            )
            Text(SurfaceUiText.RESUME_HELP.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val sameVehicle = store.profiles.filter { it.vehicleId == vehicle }
        if (sameVehicle.size > 1) SettingsGroup(SurfaceUiText.SAVED_PROFILE.get(lang), dividerInset = 52.dp, selectableGroup = true) {
            for (p in sameVehicle) RadioRow(p.name, p.id == profile.id, { runCatching { store.saveProfile(p) }; rev++ })
        }
        SettingsGroup(null, dividerInset = 70.dp) {
            ListRow(SurfaceUiText.NEW_PROFILE.get(lang), subtitle = SurfaceUiText.NEW_PROFILE_HELP.get(lang), icon = WeemIcons.Add, onClick = { newProfile = true })
            ListRow(SurfaceUiText.SAVE_MOUNT.get(lang), icon = WeemIcons.Check, onClick = { if (rt.saveMount()) { rev++ } else toast(SurfaceUiText.MOUNT_NOT_READY.get(lang)) })
        }
        SettingsGroup(SurfaceUiText.CONTROLLED.get(lang), footer = SurfaceUiText.CONTROLLED_HELP.get(lang), dividerInset = 52.dp, selectableGroup = true) {
            for ((key, text) in listOf<Pair<String?, String>>(
                null to SurfaceUiText.CALIBRATION_OFF.get(lang), "smooth" to SurfaceUiText.KNOWN_SMOOTH.get(lang), "rough" to SurfaceUiText.KNOWN_ROUGH.get(lang),
            )) RadioRow(text, rt.calibrationLabel == key, { rt.setCalibrationLabel(key); rev++ })
        }
        SettingsGroup(null) {
            for (bin in 0..3) ListRow(SurfaceText.SPEED_BINS[bin], subtitle = SurfaceText.bandStatus(profile.bands[bin], lang), icon = WeemIcons.Speed)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            WButton(SurfaceUiText.CALCULATE.get(lang), { runCatching { store.calibrate() }; rev++ }, Modifier.weight(1f), kind = BtnKind.OUTLINE)
            WButton(SurfaceUiText.ROAD_CALIBRATION.get(lang), onCalibration, Modifier.weight(1f).testTag("surface-road-calibration"), icon = WeemIcons.Tune)
        }
        SettingsGroup(null, dividerInset = 70.dp) {
            ListRow(
                SurfaceUiText.MOTION_TITLE.get(lang), subtitle = (if (motionGranted) SurfaceUiText.MOTION_ALLOWED else SurfaceUiText.MOTION_LIMITED).get(lang),
                icon = WeemIcons.Sensors, iconTone = if (motionGranted) WTone.POSITIVE else WTone.WARN,
                trailing = if (!motionGranted) ({
                    WButton(SurfaceUiText.MOTION_GRANT.get(lang), { askMotion.launch(Manifest.permission.ACTIVITY_RECOGNITION) }, Modifier.testTag("surface-motion-grant"), kind = BtnKind.TONAL)
                }) else null,
            )
            ListRow(
                SurfaceUiText.DIAGNOSTICS.get(lang), icon = WeemIcons.Info, onClick = { diagnostics = !diagnostics },
                trailing = { Icon(if (diagnostics) WeemIcons.ExpandLess else WeemIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        }
        if (diagnostics) SettingsGroup(null, dividerInset = 70.dp) {
            SwitchRow(SurfaceUiText.RAW_TITLE.get(lang), rawOn, { rawOn = it; rt.rawEnabled = it }, subtitle = SurfaceUiText.RAW_HINT.get(lang), tag = "surface-raw-switch")
            ListRow(SurfaceUiText.RAW_EXPORT.get(lang), icon = WeemIcons.Share, onClick = {
                val file = rt.exportRaw()
                if (file == null) toast(SurfaceUiText.RAW_EMPTY.get(lang))
                else runCatching { shareFile(ctx, "WEEM-surface-raw-last-10-minutes.jsonl", file.readBytes(), "application/x-ndjson") }.onFailure { toast(it.message ?: SurfaceUiText.ERROR.get(lang)) }
            })
            Column(Modifier.padding(WeemSpace.l), verticalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
                Text(SurfaceText.diagnostics(view, profile), style = WeemType.bodyS)
                for (row in store.decisions.takeLast(10).reversed()) Text("${row.optString("state")} · ${row.optString("reason")}\n${row.optString("leg")}", style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ListRow(SurfaceUiText.EXPORT_JSON.get(lang), icon = WeemIcons.Share, onClick = {
                runCatching { shareFile(ctx, "WEEM-surface-diagnostics.json", java.io.File(store.export()).readBytes()) }.onFailure { toast(it.message ?: SurfaceUiText.ERROR.get(lang)) }
            })
        }
    }
    if (newProfile) NewProfileDialog(lang, vehicle, onDone = { newProfile = false; rev++ })
}

@Composable
private fun NewProfileDialog(lang: Lang, vehicle: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val rt = remember { NavRuntime.get(ctx).surface }
    var name by remember { mutableStateOf("") }
    ConfirmDialog(
        title = SurfaceUiText.NEW_PROFILE_TITLE.get(lang), confirm = SurfaceUiText.CREATE.get(lang), dismiss = RoadsText.cancel(lang), confirmEnabled = name.isNotBlank(),
        onConfirm = {
            runCatching { rt.store.saveProfile(SurfaceProfile(id = "$vehicle-${System.currentTimeMillis()}", name = "$vehicle · ${name.trim()}", vehicleId = vehicle)) }
            onDone()
        },
        onDismiss = onDone,
    ) {
        OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text(SurfaceUiText.NEW_PROFILE_HINT.get(lang)) }, shape = RoundedCornerShape(WeemRadius.tile))
    }
}

/** Dosyayı İndirilenler/WEEM'e yazar ve paylaşım seçicisini açar. */
internal fun shareFile(ctx: Context, name: String, bytes: ByteArray, mime: String = "application/json") {
    val uri = Downloads.save(ctx, "WEEM", name, replace = true, mime = mime) { it.write(bytes) }
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
