package com.kuryelog.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kuryelog.app.core.Home
import com.kuryelog.app.core.HomeFixSampler
import com.kuryelog.app.core.Store
import com.kuryelog.logic.app.HomeTexts
import com.kuryelog.app.map.HomePickMap
import com.kuryelog.app.map.HomePickOutcome
import com.kuryelog.app.map.HomePickScreen
import com.kuryelog.logic.app.Palette
import com.kuryelog.logic.core.HomeDraft
import com.kuryelog.logic.core.HomePickResult
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.ui.MapStyles
import com.kuryelog.logic.core.Lang
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kuryelog.logic.app.Text as Str

/**
 * Daha fazla → Ev konumu (G2, CORE.md 2.1–2.2; RS ev kartı): kayıtlı ev, GPS ile seçim (6 sn ölçüm, taslak, Kaydet/İptal), silme
 * (onaylı), arka plan konumu izni ve kuralın açıklaması; haritadan seçme (`:map` `HomePickScreen`, A6.5). Ekran durumu tercih değildir.
 */
@Composable
fun HomeScreen(lang: Lang, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<HomeDraft?>(null) }
    var measuring by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val sampler = remember { HomeFixSampler(ctx) }
    DisposableEffect(Unit) { onDispose { sampler.cancel() } }

    fun measure() {
        status = null; draft = null
        val started = sampler.start(
            onProgress = { s -> measuring = HomeTexts.measuring(lang) + " " + (s.accuracyM?.let { HomeTexts.accuracy(lang, it) } ?: "") },
            onFinish = { s ->
                measuring = null
                draft = s?.let { HomeDraft.create(it.lat, it.lon, it.accuracyM) }
                if (draft == null) status = Str.HOME_NO_FIX.get(lang)
            },
        )
        if (started) measuring = HomeTexts.measuring(lang) else status = Str.HOME_NO_PROVIDER.get(lang)
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ctx.safely { if (Home.hasFine(ctx)) measure() } }
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ctx.safely { version++ } }

    if (picking) {
        // Haritadan seçme (MAP.md 6.11): girdi taslak ya da kayıtlı ev; sonuç yalnız taslak olur (Kaydet yine burada, CORE.md 2).
        val initial = draft?.let { GeoPoint(it.lat, it.lon) } ?: Home.point(ctx)?.let { GeoPoint(it.lat, it.lon) }
        val dark = LocalWeemPalette.current == Palette.dark
        HomePickScreen(
            initial = initial,
            locate = { HomePickLocate.current(ctx) },
            lang = lang, dark = dark,
            onResult = { r ->
                picking = false
                if (r is HomePickOutcome.Saved) HomePickResult.parse(true, r.lat, r.lon)?.let { draft = it; status = null }
            },
        ) { s -> HomePickMap(s, MapStyles.userAgent(BuildInfo.versionName(ctx))) }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(Str.BACK.get(lang)) }
            Text(Str.HOME_TITLE.get(lang), style = MaterialTheme.typography.headlineMedium)
        }
        GuardedPage("home", lang, key = version, build = { Home.point(ctx) to Home.hasBackground(ctx) }) { (point, background) ->
            Column {
                if (point != null && !editing) {
                    Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(Str.HOME_SAVED.get(lang), style = MaterialTheme.typography.titleMedium)
                            Text(HomeTexts.coordinates(point.lat, point.lon))
                            val date = point.updatedAt?.let { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag(lang.key)).format(Date(it)) }
                            Text(Str.HOME_UPDATED.get(lang).format(date ?: Str.HOME_UNKNOWN.get(lang)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row {
                                TextButton(onClick = { editing = true }) { Text(Str.HOME_EDIT.get(lang)) }
                                TextButton(onClick = { confirmDelete = true }) { Text(Str.HOME_DELETE.get(lang)) }
                            }
                        }
                    }
                    if (!background) {
                        Text(Str.HOME_BACKGROUND.get(lang), color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { ctx.safely { askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) } }) {
                            Text(Str.HOME_BACKGROUND_BUTTON.get(lang))
                        }
                    }
                } else {
                    Text(if (point == null) Str.HOME_NONE.get(lang) else Str.HOME_PICK.get(lang), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 12.dp))
                    Button(onClick = { ctx.safely { if (Home.hasFine(ctx)) measure() else askLocation.launch(locationPermissions()) } }, enabled = measuring == null) {
                        Text(Str.HOME_USE_CURRENT.get(lang))
                    }
                    OutlinedButton(onClick = { sampler.cancel(); measuring = null; picking = true }) { Text(Str.HOME_FROM_MAP.get(lang)) }
                    measuring?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    draft?.let { d ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(Str.HOME_DRAFT.get(lang), style = MaterialTheme.typography.titleMedium)
                                Text(HomeTexts.coordinates(d.lat, d.lon))
                                d.accuracyM?.let { Text(HomeTexts.accuracy(lang, it), color = if (d.needsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                                if (d.needsWarning) Text(HomeTexts.warning(lang), color = MaterialTheme.colorScheme.error)
                                Row {
                                    Button(onClick = { ctx.safely { Home.set(ctx, d.lat, d.lon); draft = null; editing = false; version++ } }) { Text(Str.SAVE.get(lang)) }
                                    TextButton(onClick = { draft = null }) { Text(Str.CANCEL.get(lang)) }
                                }
                            }
                        }
                    }
                    if (point != null) TextButton(onClick = { sampler.cancel(); measuring = null; draft = null; editing = false }) { Text(Str.CANCEL.get(lang)) }
                }
            }
        }
        Text(HomeTexts.rule(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp))
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(Str.HOME_DELETE_ASK.get(lang)) },
        confirmButton = { TextButton(onClick = { ctx.safely { Home.clear(ctx); confirmDelete = false; version++ } }) { Text(Str.HOME_DELETE.get(lang)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(Str.CANCEL.get(lang)) } },
    )
}
