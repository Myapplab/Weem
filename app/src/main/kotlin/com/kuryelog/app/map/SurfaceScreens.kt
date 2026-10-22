package com.kuryelog.app.map

import android.content.Context
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
import com.kuryelog.logic.map.surface.BakuTime
import com.kuryelog.logic.map.surface.Passability
import com.kuryelog.logic.map.surface.SurfaceLabel
import com.kuryelog.logic.map.surface.SurfaceMark
import com.kuryelog.logic.map.surface.SurfaceProfile
import com.kuryelog.logic.map.surface.SurfaceSummary
import com.kuryelog.logic.map.surface.SurfaceText
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Y13/Y14 ekranları (A10.4b): Yollarım → Kalite sekmesinin "Yol puanları" bölümü, sokak parçaları, parça ayrıntısı, Ölçüm ve kalibrasyon.
// YolGo `surface_screen.dart` @ 870ab6e; metin ve sıra `logic/map` `SurfaceText`/`StreetScores`'ta (testli), burada yalnız çizim. Cihazda denenmedi.

private fun tl(lang: Lang, tr: String, az: String) = if (lang == Lang.AZ) az else tr

/** Ölçülmüş parçalar, kalibrasyon kayıtları ve yol adları: ekranların ortak verisi (parçalar değişince yeniden okunur). */
internal class SurfaceData(val summaries: List<SurfaceSummary>, val records: List<RoadCalibrationRecord>, val names: Map<Long, String>) {
    val references = ProfileReferences(records)
    fun streets(): List<Street> = StreetScores.buildStreets(summaries, references::score) { names[it] }
    fun nameOf(way: Long, lang: Lang) = names[way]?.takeIf { it.isNotEmpty() } ?: tl(lang, "isimsiz yol", "adsız yol")
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
                Text(tl(lang, "Yol puanları", "Yol balları"), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(SurfaceText.qualityIntro(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(measurement, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("surface-state"))
                TextButton(onClick = onSetup, modifier = Modifier.testTag("surface-setup")) { Text(tl(lang, "Ölçüm ve kalibrasyon", "Ölçmə və kalibrasiya")) }
            }
        }
        Row(Modifier.fillMaxWidth().clickable { onLayer(!layerOn) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tl(lang, "Haritada yüzey renkleri", "Xəritədə səth rəngləri"), Modifier.weight(1f))
            Switch(checked = layerOn, onCheckedChange = null)
        }
        if (streets.isEmpty()) Text(tl(lang, "Henüz fiziksel yol ölçümü yok", "Hələ fiziki yol ölçməsi yoxdur"), Modifier.padding(vertical = 12.dp))
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
        if (street == null) Text(tl(lang, "Henüz fiziksel yol ölçümü yok", "Hələ fiziki yol ölçməsi yoxdur"), Modifier.padding(20.dp))
        else Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(tl(lang, "Sokak puanı", "Küçə balı"))
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
                Text("${tl(lang, "Rota/harita puanı", "Marşrut/xəritə balı")}: ${Math.round(s.score)}/100")
                Text("${tl(lang, "güven", "inam")} ${Math.round(s.confidence * 100)}%")
                Text("${s.passes} ${tl(lang, "bağımsız geçiş", "müstəqil keçid")} · ${SurfaceText.km(s.meters)} ${tl(lang, "ölçüldü", "ölçüldü")}")
                Text("${tl(lang, "Bozuk ölçülen mesafe", "Nahamar ölçülən məsafə")}: ${Math.round(s.roughFraction * 100)}%")
                Text("${s.impacts} ${tl(lang, "tarafsız darbe olayı", "neytral zərbə hadisəsi")} · ${s.impactsPer100M?.let { String.format(Locale.US, "%.1f", it) } ?: "—"} /100 m")
                Text("${tl(lang, "Geçilebilirlik", "Keçid")}: ${tl(lang, "yüzeyden çıkarılmaz; elle kapanış ayrı kaydedilir", "səthdən çıxarılmır; bağlanma ayrı qeyd olunur")}")
                Text("${tl(lang, "Son ölçüm", "Son ölçmə")}: ${BakuTime.stamp(s.lastAt, seconds = false)}")
                if (events.isNotEmpty()) {
                    Text(tl(lang, "Darbe olayları · türünü sen doğrula", "Zərbə hadisələri · növünü sən təsdiqlə"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    for (e in events) {
                        val id = "event-${e.opt("id")}"
                        val previous = NavRuntime.get(ctx).roads.book.state.marks.firstOrNull { it.id == id }
                        Column(Modifier.fillMaxWidth().clickable { edit = Triple(s.way, e.optDouble("lat") to e.optDouble("lon"), previous ?: SurfaceMark(id, s.way, e.optDouble("lat"), e.optDouble("lon"), 0)) }.padding(vertical = 4.dp)) {
                            Text("${tl(lang, "Darbe", "Zərbə")} · ${String.format(Locale.US, "%.2f", e.optDouble("peak"))} m/s²")
                            Text("${BakuTime.stamp(e.optLong("utc"))}\n${tl(lang, "Otomatik · tür bilinmiyor · GPS", "Avtomatik · növ bilinmir · GPS")} ${String.format(Locale.US, "%.1f", e.optDouble("accuracy"))} m", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TextButton(onClick = { edit = Triple(s.way, s.lat to s.lon, null) }) { Text(tl(lang, "Yüzey türü veya kapanış kaydet", "Səth növü və ya bağlanma qeyd et")) }
                TextButton(onClick = { tech = !tech }) { Text(tl(lang, "Teknik ölçümler", "Texniki ölçmələr")) }
                if (tech) Text(
                    "RMS ${String.format(Locale.US, "%.3f", s.rms)} m/s² · jerk ${s.jerk?.let { String.format(Locale.US, "%.2f", it) } ?: "—"} m/s³\n" +
                        "Darbe max ${String.format(Locale.US, "%.2f", s.hardest)} · P95 ${s.p95?.let { String.format(Locale.US, "%.2f", it) } ?: "—"} m/s²\n" +
                        "Gyro ${s.gyro?.let { String.format(Locale.US, "%.3f", it) } ?: "—"} rad/s\nGPS ${String.format(Locale.US, "%.1f", s.accuracy)} m · ${Math.round(s.segmentLength)} m segment · ${Math.round(s.coverage * 100)}% ölçüm kapsamı\n" +
                        "Bantlar ${s.bands.joinToString(" / ") { String.format(Locale.US, "%.3f", it) }}",
                    fontSize = 12.sp,
                )
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
        title = { Text(tl(lang, "Yüzey kaydı", "Səth qeydi")) },
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
                Toast.makeText(ctx, tl(lang, "Yüzey kaydedildi", "Səth qeyd edildi"), Toast.LENGTH_SHORT).show()
                onDone()
            }) { Text(tl(lang, "Kaydet", "Yadda saxla")) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(tl(lang, "Vazgeç", "Ləğv et")) } },
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
    fun toast(text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("←") }; Text(tl(lang, "Yol ölçümü", "Yol ölçməsi"), style = MaterialTheme.typography.titleLarge) }
        Text(SurfaceText.measurementLabel(view.decision.state, lang), style = MaterialTheme.typography.titleMedium)
        Text("${profile.name}\n${profile.id} · v${profile.version}")
        val sameVehicle = store.profiles.filter { it.vehicleId == vehicle }
        if (sameVehicle.size > 1) {
            Text(tl(lang, "Kayıtlı montaj profili", "Qeyd edilmiş montaj profili"), fontWeight = FontWeight.SemiBold)
            for (p in sameVehicle) Row(Modifier.fillMaxWidth().clickable { runCatching { store.saveProfile(p) }; rev++ }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = p.id == profile.id, onClick = { runCatching { store.saveProfile(p) }; rev++ }); Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        OutlinedButton(onClick = { rt.pause(!rt.paused); rev++ }, modifier = Modifier.fillMaxWidth().testTag("surface-pause")) {
            Text(if (rt.paused) tl(lang, "Ölçüme devam et", "Ölçməyə davam et") else tl(lang, "Ölçümü duraklat", "Ölçməni dayandır"))
        }
        Text(tl(lang, "Devam etme komutu yalnızca uygun sipariş etabında doğrulanan scooter sürüşünü kabul eder.", "Davam əmri yalnız uyğun sifariş mərhələsində təsdiqlənən skuter sürüşünü qəbul edir."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { newProfile = true }) { Text(tl(lang, "Scooter / telefon / montaj profili: yeni", "Skuter / telefon / montaj profili: yeni")) }
        Text(tl(lang, "Düzen değiştiyse yeni profil oluştur; eski ölçümler korunur.", "Quruluş dəyişibsə yeni profil yarat; köhnə ölçmələr saxlanır."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = {
            if (rt.saveMount()) { rev++ } else toast(tl(lang, "Sabit montaj ve güvenilir konum henüz doğrulanmadı", "Sabit montaj və etibarlı məkan hələ təsdiqlənmədi"))
        }) { Text(tl(lang, "Montaj yönünü kaydet (telefon sabit gidonda, scooter duruyorken)", "Montaj istiqamətini qeyd et (telefon sükan üzərində sabit, skuter dayanarkən)")) }
        HorizontalDivider()
        Text(tl(lang, "Kontrollü kalibrasyon", "Nəzarətli kalibrasiya"), style = MaterialTheme.typography.titleMedium)
        Text(tl(lang, "Her hız aralığında bilinen düzgün ve bozuk yüzeyden en az üç ayrı sipariş etabında geç. Etiketi geçişten önce seç, sonra kapat. Normal sürüşler referansı değiştirmez.",
            "Hər sürət aralığında məlum hamar və nahamar səthdən ən az üç ayrı sifariş mərhələsində keç. Etiketi keçiddən əvvəl seç, sonra bağla. Adi sürüşlər istinadı dəyişmir."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for ((key, text) in listOf<Pair<String?, String>>(
            null to tl(lang, "Kalibrasyon kapalı", "Kalibrasiya bağlıdır"), "smooth" to tl(lang, "Bilinen düzgün yüzey", "Məlum hamar səth"), "rough" to tl(lang, "Bilinen bozuk yüzey", "Məlum nahamar səth"),
        )) Row(Modifier.fillMaxWidth().clickable { rt.setCalibrationLabel(key); rev++ }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = rt.calibrationLabel == key, onClick = { rt.setCalibrationLabel(key); rev++ }); Text(text)
        }
        for (bin in 0..3) {
            val b = profile.bands[bin]
            Column {
                Text(SurfaceText.SPEED_BINS[bin])
                Text(if (b?.validated == true) "${b.smoothPasses} / ${b.roughPasses} ${tl(lang, "geçiş · kalibre", "keçid · kalibrə olunub")}" else tl(lang, "Kalibrasyon bekliyor", "Kalibrasiya gözlənilir"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = { runCatching { store.calibrate() }; rev++ }, modifier = Modifier.fillMaxWidth()) { Text(tl(lang, "Kalibrasyonu hesapla", "Kalibrasiyanı hesabla")) }
        Button(onClick = onCalibration, modifier = Modifier.fillMaxWidth().testTag("surface-road-calibration")) { Text(tl(lang, "Yol Kalitesi Kalibrasyonu (1–10)", "Yol Keyfiyyəti Kalibrasiyası (1–10)")) }
        HorizontalDivider()
        Text(tl(lang, "Hareket izni: sınırlı çıkarım · sensör + GPS kullanılır (adım dedektörü için etkinlik izni verilmedi)", "Hərəkət icazəsi: məhdud nəticə · sensor + GPS işlənir"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { diagnostics = !diagnostics }) { Text(tl(lang, "Ölçüm tanılama", "Ölçmə diaqnostikası")) }
        if (diagnostics) {
            Text(
                "Durum: ${view.decision.state.name.lowercase()}\nNeden: ${view.decision.reason}\nBoot ms: ${view.decision.at}\nGüven: ${view.decision.confidence}\nBekleyen: ${view.pending} · kabul: ${view.accepted}\n" +
                    "Algoritma v2 · ${profile.segmentM.toInt()} m · ${profile.bufferMs / 1000.0} s tampon\nHP ${profile.highPassHz} Hz · LP ${profile.lowPassHz} Hz · darbe ${profile.impactThreshold} m/s²",
                fontSize = 12.sp,
            )
            for (row in store.decisions.takeLast(10).reversed()) Text("${row.optString("state")} · ${row.optString("reason")}\n${row.optString("leg")}", fontSize = 12.sp)
            TextButton(onClick = {
                runCatching { shareFile(ctx, "WEEM-surface-diagnostics.json", java.io.File(store.export()).readBytes()) }.onFailure { toast(it.message ?: "Hata") }
            }) { Text(tl(lang, "JSON dışa aktar", "JSON ixrac et")) }
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
        title = { Text(tl(lang, "Yeni ölçüm profili", "Yeni ölçmə profili")) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text(tl(lang, "Telefon modeli · montaj yeri", "Telefon modeli · montaj yeri")) }) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                runCatching { rt.store.saveProfile(SurfaceProfile(id = "$vehicle-${System.currentTimeMillis()}", name = "$vehicle · ${name.trim()}", vehicleId = vehicle)) }
                onDone()
            }) { Text(tl(lang, "Oluştur", "Yarat")) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(tl(lang, "Vazgeç", "Ləğv et")) } },
    )
}

/** Dosyayı İndirilenler/WEEM'e yazar ve paylaşım seçicisini açar. */
internal fun shareFile(ctx: Context, name: String, bytes: ByteArray, mime: String = "application/json") {
    val uri = Downloads.save(ctx, "WEEM", name, replace = true, mime = mime) { it.write(bytes) }
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
