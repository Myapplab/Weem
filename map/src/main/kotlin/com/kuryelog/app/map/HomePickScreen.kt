package com.kuryelog.app.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.ui.MapStyles
import java.util.Locale

/** Ev seçicinin sonucu (A2.2 `HomePickResult`): yalnız sonuç döner, ev kaydına yazmaz (`:core` doğrular). */
sealed interface HomePickOutcome {
    /** Sürüklenen nokta, `Double` olarak. */
    data class Saved(val lat: Double, val lon: Double) : HomePickOutcome
    data object Cancelled : HomePickOutcome
}

/**
 * Harita yüzeyine verilen ev seçici durumu: [center] sabit iğnenin altındaki nokta; yüzey sürükleme [onCenterChange] ve uzun basış [onLongPress] ile bildirir.
 * [zoom] açılış yakınlığı (17), [dark] koyu temada karolar ters çevrilir; [moveSeq] uzun basışta artar (yüzey kamerayı [center]'a taşır).
 */
class HomePickSurfaceState(
    val center: GeoPoint,
    val radiusM: Double,
    val onCenterChange: (GeoPoint) -> Unit,
    val onLongPress: (GeoPoint) -> Unit,
    val zoom: Double = HomePick.ZOOM,
    val dark: Boolean = false,
    val moveSeq: Int = 0,
)

object HomePick {
    const val ZOOM = 17.0
    const val RADIUS_M = 50.0
}

/**
 * Ev konumu seçici (YolGo `home_picker.dart`; MAP.md 6.11): girdi merkez (`initial`), yoksa [locate] (GPS; saklı ev kullanılmaz); sabit iğne (`home-pin`) ve 50 m daire;
 * Kaydet sürüklenen noktayı [onResult]'a verir (tercih yazılmaz); konum hatasında Kaydet pasif, İptal çalışır; Geri ve İptal ("İmtina") [HomePickOutcome.Cancelled] döner.
 * Harita yüzeyi [surface] ile enjekte edilir (testte sahte; uygulamada MapLibre OSM raster). [dark] tema kök etiketi `home-pick-dark`/`home-pick-light`.
 */
@Composable
fun HomePickScreen(
    initial: GeoPoint?,
    locate: suspend () -> GeoPoint,
    lang: Lang,
    dark: Boolean,
    onResult: (HomePickOutcome) -> Unit,
    modifier: Modifier = Modifier,
    surface: @Composable (HomePickSurfaceState) -> Unit,
) {
    fun t(tr: String, az: String) = if (lang == Lang.AZ) az else tr
    var point by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var moveSeq by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        if (point != null) return@LaunchedEffect
        error = false
        point = try { locate() } catch (e: Exception) { error = true; null }
    }
    fun finish(save: Boolean) {
        val p = point
        if (closing || (save && p == null)) return
        closing = true
        onResult(if (save) HomePickOutcome.Saved(p!!.lat, p.lon) else HomePickOutcome.Cancelled)
    }
    BackHandler { onResult(HomePickOutcome.Cancelled) }
    val uri = LocalUriHandler.current
    Surface(modifier.fillMaxSize().testTag(if (dark) "home-pick-dark" else "home-pick-light"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { finish(false) }) { Text("←", fontSize = 22.sp) }
                Text(t("Ev konumunu seç", "Ev məkanını seç"), style = MaterialTheme.typography.titleLarge)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val p = point
                if (p == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (error) TextButton(onClick = { attempt++ }) { Text(t("Konum alınamadı · Tekrar dene", "Məkan alınmadı · Yenidən yoxla")) }
                        else CircularProgressIndicator()
                    }
                } else {
                    surface(
                        HomePickSurfaceState(
                            center = p, radiusM = HomePick.RADIUS_M, dark = dark, moveSeq = moveSeq,
                            onCenterChange = { point = it },
                            onLongPress = { point = it; moveSeq++ },
                        ),
                    )
                    // Sabit iğne: ucu merkezde (20 dp yukarı), dokunuşları haritaya bırakır.
                    Text(
                        "📍", fontSize = 34.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center).offset(y = (-20).dp).size(40.dp).testTag("home-pin"),
                    )
                    Text(
                        "© OpenStreetMap contributors", fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(MaterialTheme.colorScheme.surface)
                            .clickable { runCatching { uri.openUri(MapStyles.OSM_COPYRIGHT) } },
                    )
                }
            }
            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t("Haritayı kaydır veya uzun bas", "Xəritəni sürüşdür və ya uzun bas"))
                val p = point
                Text(if (p == null) "—" else String.format(Locale.US, "%.6f, %.6f", p.lat, p.lon))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { finish(false) }, enabled = !closing, modifier = Modifier.weight(1f)) { Text(t("İptal", "İmtina")) }
                    Button(onClick = { finish(true) }, enabled = p != null && !closing, modifier = Modifier.weight(1f)) { Text(t("Kaydet", "Saxla")) }
                }
            }
        }
    }
}
