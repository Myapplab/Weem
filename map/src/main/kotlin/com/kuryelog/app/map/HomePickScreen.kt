package com.kuryelog.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint

/** Ev seçicinin sonucu (A2.2 `HomePickResult`): yalnız sonuç döner, ev kaydına yazmaz (`:core` doğrular). */
sealed interface HomePickOutcome {
    /** Sürüklenen nokta, `Double` olarak. */
    data class Saved(val lat: Double, val lon: Double) : HomePickOutcome
    data object Cancelled : HomePickOutcome
}

/** Harita yüzeyine verilen ev seçici durumu: [center] sabit iğnenin altındaki nokta; yüzey sürükleme [onCenterChange] ve uzun basış [onLongPress] ile bildirir. */
class HomePickSurfaceState(val center: GeoPoint, val radiusM: Double, val onCenterChange: (GeoPoint) -> Unit, val onLongPress: (GeoPoint) -> Unit)

/**
 * Ev konumu seçici (YolGo `home_picker.dart`; MAP.md 6.11): girdi merkez (`initial`), yoksa [locate] (GPS; saklı ev kullanılmaz); sabit iğne (`home-pin`) ve 50 m daire;
 * Kaydet sürüklenen noktayı [onResult]'a verir (tercih yazılmaz); konum hatasında Kaydet pasif, İptal çalışır; Geri ve İptal ("İmtina") [HomePickOutcome.Cancelled] döner.
 * Harita yüzeyi [surface] ile enjekte edilir (testte sahte; MapLibre/Google A6.5). [dark] tema kök etiketi `home-pick-dark`/`home-pick-light` (Compose'ta parlaklık okunamaz).
 * Başlık "Ev konumunu seç" / "Ev məkanını seç". İskelet (A6.2b): gövde A6.5'te.
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
    TODO("A6.5")
}
