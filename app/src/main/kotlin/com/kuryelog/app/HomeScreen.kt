package com.kuryelog.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.kuryelog.app.core.Home
import com.kuryelog.app.core.HomeFixSampler
import com.kuryelog.app.map.HomePickMap
import com.kuryelog.app.map.HomePickOutcome
import com.kuryelog.app.map.HomePickScreen
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.IconBox
import com.kuryelog.app.ui.SubScreen
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.logic.app.HomeTexts
import com.kuryelog.logic.app.Palette
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.HomeDraft
import com.kuryelog.logic.core.HomePickResult
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.ui.MapStyles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    SubScreen(Str.HOME_TITLE.get(lang), Str.BACK.get(lang), onBack) {
        GuardedPage("home", lang, key = version, build = { Home.point(ctx) to Home.hasBackground(ctx) }) { (point, background) ->
            Column(verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
                if (point != null && !editing) {
                    WCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
                            IconBox(WeemIcons.HomeFilled, tone = WTone.ACCENT)
                            Column(Modifier.weight(1f)) {
                                Text(Str.HOME_SAVED.get(lang), style = WeemType.titleM)
                                Text(HomeTexts.coordinates(point.lat, point.lon), style = WeemType.numS)
                            }
                        }
                        val date = point.updatedAt?.let { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag(lang.key)).format(Date(it)) }
                        Text(Str.HOME_UPDATED.get(lang).format(date ?: Str.HOME_UNKNOWN.get(lang)), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                            WButton(Str.HOME_EDIT.get(lang), { editing = true }, Modifier.weight(1f), kind = BtnKind.TONAL, icon = WeemIcons.Edit)
                            WButton(Str.HOME_DELETE.get(lang), { confirmDelete = true }, Modifier.weight(1f), kind = BtnKind.DANGER, icon = WeemIcons.Delete)
                        }
                    }
                    if (!background) {
                        Banner(Str.HOME_BACKGROUND.get(lang), tone = WTone.DANGER)
                        WButton(Str.HOME_BACKGROUND_BUTTON.get(lang), { ctx.safely { askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) } }, kind = BtnKind.OUTLINE, fill = true)
                    }
                } else {
                    Text(if (point == null) Str.HOME_NONE.get(lang) else Str.HOME_PICK.get(lang), style = WeemType.titleM)
                    WButton(
                        Str.HOME_USE_CURRENT.get(lang), { ctx.safely { if (Home.hasFine(ctx)) measure() else askLocation.launch(locationPermissions()) } },
                        enabled = measuring == null, busy = measuring != null, icon = WeemIcons.MyLocation, fill = true, ride = true,
                    )
                    WButton(Str.HOME_FROM_MAP.get(lang), { sampler.cancel(); measuring = null; picking = true }, kind = BtnKind.OUTLINE, icon = WeemIcons.Map, fill = true)
                    measuring?.let { Text(it, style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    status?.let { Banner(it, tone = WTone.DANGER) }
                    draft?.let { d ->
                        WCard(live = true) {
                            Text(Str.HOME_DRAFT.get(lang), style = WeemType.titleM)
                            Text(HomeTexts.coordinates(d.lat, d.lon), style = WeemType.numS)
                            d.accuracyM?.let {
                                Text(HomeTexts.accuracy(lang, it), style = WeemType.bodyS, color = if (d.needsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (d.needsWarning) Banner(HomeTexts.warning(lang), tone = WTone.WARN)
                            Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                                WButton(Str.CANCEL.get(lang), { draft = null }, Modifier.weight(1f), kind = BtnKind.OUTLINE)
                                WButton(Str.SAVE.get(lang), { ctx.safely { Home.set(ctx, d.lat, d.lon); draft = null; editing = false; version++ } }, Modifier.weight(1f))
                            }
                        }
                    }
                    if (point != null) WButton(Str.CANCEL.get(lang), { sampler.cancel(); measuring = null; draft = null; editing = false }, kind = BtnKind.TEXT, fill = true)
                }
            }
        }
        Text(HomeTexts.rule(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (confirmDelete) ConfirmDialog(
        title = Str.HOME_DELETE_ASK.get(lang), confirm = Str.HOME_DELETE.get(lang), dismiss = Str.CANCEL.get(lang), danger = true,
        onConfirm = { ctx.safely { Home.clear(ctx); confirmDelete = false; version++ } }, onDismiss = { confirmDelete = false },
    )
}
