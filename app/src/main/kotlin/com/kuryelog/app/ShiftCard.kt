package com.kuryelog.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kuryelog.app.capture.CaptureStatus
import com.kuryelog.app.core.Home
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.app.scooter.ScooterShiftLink
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.IconBox
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.floating
import com.kuryelog.app.ui.withoutGlyph
import com.kuryelog.logic.scooter.ScooterText
import com.kuryelog.logic.core.Lang
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kuryelog.logic.app.Text as Str

/**
 * Vardiya kartı (G1, CORE.md 1.2): durum (`ShiftPort.state`) ve tek düğme. A10.4d (`docs/TASARIM.md` 12): büyük kart, saat simgesi +
 * başlık, uyarılar banner; kesilmişken altında tonal tehlike "BİTİR". S37/S40: kapalıyken tek satır (küçük simge, durum, sağda "BAŞLAT");
 * [floating] haritada yüzer (`e1`, gölge).
 */
@Composable
fun ShiftCard(lang: Lang, modifier: Modifier = Modifier, floating: Boolean = false) {
    val ctx = LocalContext.current
    val shift by ShiftHub.state.collectAsState()
    var status by remember { mutableStateOf<String?>(null) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ctx.safely {
            if (Home.hasFine(ctx)) { status = null; ShiftHub.start() } else status = Str.SHIFT_NEEDS_LOCATION.get(lang)
        }
    }
    val start: () -> Unit = {
        ctx.safely {
            if (Home.hasFine(ctx)) ShiftHub.start()
            else askLocation.launch(locationPermissions())
        }
    }
    GuardedPage("shift_card", lang, key = shift, build = { Triple(shift.active, shift.startedAtMs ?: 0L, shift.interrupted) }) { (on, startedAt, interrupted) ->
        val title = if (on) Str.SHIFT_ON.get(lang).format(SimpleDateFormat("HH:mm", Locale.US).format(Date(startedAt))) else Str.SHIFT_OFF.get(lang)
        val shape = RoundedCornerShape(if (floating) WeemRadius.card else WeemRadius.panel)
        // S37 (23 Ekim): haritada yüzen kart tek satır (simge + durum + "BAŞLAT"). S40 (kullanıcı): harita verisi yokken de aynı tek satır
        // (büyük kart ve tam genişlik 56 dp "BAŞLAT" çok büyüktü); [floating] yalnız gölge.
        WCard(if (floating) modifier.floating(shape) else modifier, padding = WeemSpace.m, spacing = WeemSpace.m) {
            Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
                IconBox(WeemIcons.PunchClock)
                Text(title, style = WeemType.titleS, modifier = Modifier.weight(1f))
                if (!on || interrupted) WButton(
                    Str.SHIFT_START_SHORT.get(lang), start, Modifier.testTag("shift-start"),
                    icon = WeemIcons.PlayArrowFilled, haptic = HapticFeedbackType.Confirm,
                )
            }
            if (interrupted) Banner(Str.SHIFT_INTERRUPTED.get(lang), tone = WTone.DANGER, icon = WeemIcons.GpsOff)
            // E7 (CAPTURE.md 5.2): vardiya açık, ekran okuma çalışmıyor → uyarı, dokununca erişilebilirlik ayarı.
            // Rawstats ana sayfası her çizimde bakardı; burada 5 sn'de bir (uyarı denetimiyle aynı tempo).
            val screenReadingOffTitle by produceState<String?>(null, on, lang) {
                while (true) { value = runCatching { CaptureStatus.offWarning(ctx, lang, on) }.getOrNull(); delay(CaptureStatus.CHECK_MS) }
            }
            screenReadingOffTitle?.let {
                Banner(it, tone = WTone.DANGER, icon = WeemIcons.VisibilityOff, onClick = { ctx.safely { CaptureStatus.openAccessibilitySettings(ctx) } })
            }
            status?.let { Banner(it, tone = WTone.DANGER, icon = WeemIcons.GpsOff) }
            // Scooter satırı ve vardiya içinde sonradan bağlanma / Bluetooth izni (SCOOTER.md 5.3; RS `MainActivity` ana kart).
            if (on) ScooterRows(lang)
            if (on && !interrupted) {
                WButton(
                    Str.SHIFT_STOP.get(lang).withoutGlyph(), { ctx.safely { ShiftHub.stop() } },
                    kind = BtnKind.DANGER, icon = WeemIcons.StopFilled, ride = true, fill = true, haptic = HapticFeedbackType.Confirm,
                )
            } else {
                if (interrupted) WButton(
                    Str.SHIFT_STOP.get(lang).withoutGlyph(), { ctx.safely { ShiftHub.stop() } },
                    kind = BtnKind.DANGER, icon = WeemIcons.StopFilled, fill = true, haptic = HapticFeedbackType.Confirm,
                )
            }
        }
    }
}

/** Vardiya için ince/kaba konum ve (Android 13+) bildirim izni; arka plan konumu ayrıca istenir (Android 11+ kuralı). */
fun locationPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

/**
 * Vardiya kartının scooter satırları: simge + bağlantı durumu, altında tonal "Scooter'a şimdi bağlan" ve (Android 12+) "Bluetooth izni
 * ver" banner'ı. Hata satırı gizler, kartı bozmaz.
 */
@Composable
private fun ScooterRows(lang: Lang) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val state by produceState<Triple<String, Boolean, Boolean>?>(null, lang, refresh) {
        while (true) {
            value = runCatching { Triple(ScooterShiftLink.status(ctx), ScooterShiftLink.canConnectNow(ctx), ScooterShiftLink.needsBluetoothPermission(ctx)) }.getOrNull()
            delay(2_000)
        }
    }
    val (line, canConnect, needsPermission) = state ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
        IconBox(WeemIcons.ElectricScooter)
        Text(line, style = WeemType.titleS, modifier = Modifier.weight(1f))
    }
    // Azerice düğme metni durum satırının yanına sığmaz: düğme satırın altında, yazıyla aynı hizada.
    if (canConnect) WButton(
        ScooterText.CONNECT_NOW.get(lang), { ctx.safely { ScooterShiftLink.connectNow(ctx); refresh++ } },
        Modifier.padding(start = 52.dp), kind = BtnKind.TONAL, icon = WeemIcons.Bluetooth,
    )
    if (needsPermission) Banner(
        ScooterText.GIVE_PERMISSION.get(lang), tone = WTone.DANGER, icon = WeemIcons.Bluetooth,
        onClick = { ctx.safely { ask.launch(ScooterShiftLink.bluetoothPermissions) } },
    )
}
