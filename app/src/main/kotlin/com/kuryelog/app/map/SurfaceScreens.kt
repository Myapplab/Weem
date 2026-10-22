package com.kuryelog.app.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import com.kuryelog.app.core.Downloads
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.calibration.RoadCalibrationRecord
import com.kuryelog.logic.map.calibration.RoadCalibrationReferences
import com.kuryelog.logic.map.roads.ProfileReferences
import com.kuryelog.logic.map.roads.Street
import com.kuryelog.logic.map.roads.StreetScores
import com.kuryelog.logic.map.roads.RoadsText
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
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(SurfaceUiText.ROAD_SCORES.get(lang), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(SurfaceText.qualityIntro(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(measurement, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("surface-state"))
                TextButton(onClick = onSetup, modifier = Modifier.testTag("surface-setup")) { Text(SurfaceUiText.SETUP.get(lang)) }
            }
        }
        Row(Modifier.fillMaxWidth().clickable { onLayer(!layerOn) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(SurfaceUiText.LAYER.get(lang), Modifier.weight(1f))
            Switch(checked = layerOn, onCheckedChange = null)
        }
        if (streets.isEmpty()) Text(SurfaceUiText.NO_MEASUREMENT.get(lang), Modifier.padding(vertical = 12.dp))
        for (s in streets) {
            Row(Modifier.fillMaxWidth().clickable { onStreet(s.key, s.title) }.padding(vertical = 8.dp).testTag("street-${s.key}"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(s.score, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = s.quality?.let { tone(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
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
        for (l in v.lines) Text(l, fontSize = 13.sp)
        v.prompt?.let { p ->
            val band = v.promptBand
            if (onCalibrate != null && band != null) TextButton(onClick = { onCalibrate(band) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(p, fontSize = 13.sp) }
            else Text(p, fontSize = 13.sp)
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("←") }; Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (street == null) Text(SurfaceUiText.NO_MEASUREMENT.get(lang), Modifier.padding(20.dp))
        else Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(SurfaceUiText.STREET_SCORE.get(lang))
                Text(SurfaceText.streetSubtitle(street.scoredPieces, street.scoredMeters, lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(StreetScores.label(street.score, lang), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        for (s in pieces) {
            val score = street?.scores?.get(s.key)
            val dim = s.confidence < .3 || score == null
            Column(Modifier.fillMaxWidth().clickable { detail = s }.padding(vertical = 8.dp).testTag("road-score-${s.key}")) {
                Text(SurfaceText.pieceTitle(data.nameOf(s.way, lang), s), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else tone(score!!.level / 10))
                RoadScoreLines(data.references.of(s.profile), s.dominantRoadBand?.rms, s.dominantRoadBand?.let { it.speed * 3.6 }, lang, onCalibrate, s.confirmedShock)
                Text(SurfaceText.pieceSubtitle(s, lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(data.nameOf(s.way, lang), maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RoadScoreLines(refs, s.dominantRoadBand?.rms, s.dominantRoadBand?.let { it.speed * 3.6 }, lang, onCalibrate, s.confirmedShock)
                for (l in SurfaceText.detailLines(s, lang)) Text(l)
                if (events.isNotEmpty()) {
                    Text(SurfaceUiText.IMPACT_EVENTS.get(lang), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    for (e in events) {
                        val id = "event-${e.opt("id")}"
                        val previous = NavRuntime.get(ctx).roads.book.state.marks.firstOrNull { it.id == id }
                        Column(Modifier.fillMaxWidth().clickable { edit = Triple(s.way, e.optDouble("lat") to e.optDouble("lon"), previous ?: SurfaceMark(id, s.way, e.optDouble("lat"), e.optDouble("lon"), 0)) }.padding(vertical = 4.dp)) {
                            Text(SurfaceText.impactTitle(e.optDouble("peak"), lang))
                            Text(SurfaceText.impactSubtitle(e.optLong("utc"), e.optDouble("accuracy"), lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TextButton(onClick = { edit = Triple(s.way, s.lat to s.lon, null) }) { Text(SurfaceUiText.SAVE_MARK.get(lang)) }
                TextButton(onClick = { tech = !tech }) { Text(SurfaceUiText.TECHNICAL.get(lang)) }
                if (tech) Text(SurfaceText.technical(s), fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(RoadsText.cancel(lang)) } },
    )
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
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(SurfaceUiText.MARK_TITLE.get(lang)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (l in SurfaceLabel.entries) Row(Modifier.fillMaxWidth().clickable { label = l }, verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = label == l, onClick = { label = l }); Text(SurfaceText.surfaceLabel(l, lang)) }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                for (a in Passability.entries) Row(Modifier.fillMaxWidth().clickable { access = a }, verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = access == a, onClick = { access = a }); Text(SurfaceText.passability(a, lang)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val now = System.currentTimeMillis()
                NavRuntime.get(ctx).roads.book.saveMark(SurfaceMark(previous?.id ?: "user-$now", way, lat, lon, now, label, access))
                Toast.makeText(ctx, SurfaceUiText.MARK_SAVED.get(lang), Toast.LENGTH_SHORT).show()
                onDone()
            }) { Text(SurfaceUiText.SAVE.get(lang)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(RoadsText.cancel(lang)) } },
    )
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("←") }; Text(SurfaceUiText.SETUP_TITLE.get(lang), style = MaterialTheme.typography.titleLarge) }
        Text(SurfaceText.measurementLabel(view.decision.state, lang), style = MaterialTheme.typography.titleMedium)
        Text("${profile.name}\n${profile.id} · v${profile.version}")
        val sameVehicle = store.profiles.filter { it.vehicleId == vehicle }
        if (sameVehicle.size > 1) {
            Text(SurfaceUiText.SAVED_PROFILE.get(lang), fontWeight = FontWeight.SemiBold)
            for (p in sameVehicle) Row(Modifier.fillMaxWidth().clickable { runCatching { store.saveProfile(p) }; rev++ }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = p.id == profile.id, onClick = { runCatching { store.saveProfile(p) }; rev++ }); Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        OutlinedButton(onClick = { rt.pause(!rt.paused); rev++ }, modifier = Modifier.fillMaxWidth().testTag("surface-pause")) {
            Text((if (rt.paused) SurfaceUiText.RESUME else SurfaceUiText.PAUSE).get(lang))
        }
        Text(SurfaceUiText.RESUME_HELP.get(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { newProfile = true }) { Text(SurfaceUiText.NEW_PROFILE.get(lang)) }
        Text(SurfaceUiText.NEW_PROFILE_HELP.get(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = {
            if (rt.saveMount()) { rev++ } else toast(SurfaceUiText.MOUNT_NOT_READY.get(lang))
        }) { Text(SurfaceUiText.SAVE_MOUNT.get(lang)) }
        HorizontalDivider()
        Text(SurfaceUiText.CONTROLLED.get(lang), style = MaterialTheme.typography.titleMedium)
        Text(SurfaceUiText.CONTROLLED_HELP.get(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for ((key, text) in listOf<Pair<String?, String>>(
            null to SurfaceUiText.CALIBRATION_OFF.get(lang), "smooth" to SurfaceUiText.KNOWN_SMOOTH.get(lang), "rough" to SurfaceUiText.KNOWN_ROUGH.get(lang),
        )) Row(Modifier.fillMaxWidth().clickable { rt.setCalibrationLabel(key); rev++ }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = rt.calibrationLabel == key, onClick = { rt.setCalibrationLabel(key); rev++ }); Text(text)
        }
        for (bin in 0..3) {
            Column {
                Text(SurfaceText.SPEED_BINS[bin])
                Text(SurfaceText.bandStatus(profile.bands[bin], lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = { runCatching { store.calibrate() }; rev++ }, modifier = Modifier.fillMaxWidth()) { Text(SurfaceUiText.CALCULATE.get(lang)) }
        Button(onClick = onCalibration, modifier = Modifier.fillMaxWidth().testTag("surface-road-calibration")) { Text(SurfaceUiText.ROAD_CALIBRATION.get(lang)) }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(SurfaceUiText.MOTION_TITLE.get(lang))
                Text((if (motionGranted) SurfaceUiText.MOTION_ALLOWED else SurfaceUiText.MOTION_LIMITED).get(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!motionGranted) TextButton(onClick = { askMotion.launch(Manifest.permission.ACTIVITY_RECOGNITION) }, modifier = Modifier.testTag("surface-motion-grant")) { Text(SurfaceUiText.MOTION_GRANT.get(lang)) }
        }
        TextButton(onClick = { diagnostics = !diagnostics }) { Text(SurfaceUiText.DIAGNOSTICS.get(lang)) }
        if (diagnostics) {
            Row(Modifier.fillMaxWidth().clickable { rawOn = !rawOn; rt.rawEnabled = rawOn }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(SurfaceUiText.RAW_TITLE.get(lang))
                    Text(SurfaceUiText.RAW_HINT.get(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = rawOn, onCheckedChange = null, modifier = Modifier.testTag("surface-raw-switch"))
            }
            TextButton(onClick = {
                val file = rt.exportRaw()
                if (file == null) toast(SurfaceUiText.RAW_EMPTY.get(lang))
                else runCatching { shareFile(ctx, "WEEM-surface-raw-last-10-minutes.jsonl", file.readBytes(), "application/x-ndjson") }.onFailure { toast(it.message ?: SurfaceUiText.ERROR.get(lang)) }
            }) { Text(SurfaceUiText.RAW_EXPORT.get(lang)) }
            Text(SurfaceText.diagnostics(view, profile), fontSize = 12.sp)
            for (row in store.decisions.takeLast(10).reversed()) Text("${row.optString("state")} · ${row.optString("reason")}\n${row.optString("leg")}", fontSize = 12.sp)
            TextButton(onClick = {
                runCatching { shareFile(ctx, "WEEM-surface-diagnostics.json", java.io.File(store.export()).readBytes()) }.onFailure { toast(it.message ?: SurfaceUiText.ERROR.get(lang)) }
            }) { Text(SurfaceUiText.EXPORT_JSON.get(lang)) }
        }
    }
    if (newProfile) NewProfileDialog(lang, vehicle, onDone = { newProfile = false; rev++ })
}

@Composable
private fun NewProfileDialog(lang: Lang, vehicle: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val rt = remember { NavRuntime.get(ctx).surface }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(SurfaceUiText.NEW_PROFILE_TITLE.get(lang)) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text(SurfaceUiText.NEW_PROFILE_HINT.get(lang)) }) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                runCatching { rt.store.saveProfile(SurfaceProfile(id = "$vehicle-${System.currentTimeMillis()}", name = "$vehicle · ${name.trim()}", vehicleId = vehicle)) }
                onDone()
            }) { Text(SurfaceUiText.CREATE.get(lang)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(RoadsText.cancel(lang)) } },
    )
}

/** Dosyayı İndirilenler/WEEM'e yazar ve paylaşım seçicisini açar. */
internal fun shareFile(ctx: Context, name: String, bytes: ByteArray, mime: String = "application/json") {
    val uri = Downloads.save(ctx, "WEEM", name, replace = true, mime = mime) { it.write(bytes) }
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
