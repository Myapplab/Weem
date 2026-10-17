package com.kuryelog.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kuryelog.app.capture.CaptureStatus
import com.kuryelog.app.core.Home
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.logic.core.Lang
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kuryelog.logic.app.Text as Str

/** Vardiya kartı (G1, CORE.md 1.2): durum (`ShiftPort.state`) ve tek düğme. */
@Composable
fun ShiftCard(lang: Lang) {
    val ctx = LocalContext.current
    val shift by ShiftHub.state.collectAsState()
    var status by remember { mutableStateOf<String?>(null) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ctx.safely {
            if (Home.hasFine(ctx)) { status = null; ShiftHub.start() } else status = Str.SHIFT_NEEDS_LOCATION.get(lang)
        }
    }
    GuardedPage("shift_card", lang, key = shift, build = { Triple(shift.active, shift.startedAtMs ?: 0L, shift.interrupted) }) { (on, startedAt, interrupted) ->
        Card(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (on) Str.SHIFT_ON.get(lang).format(SimpleDateFormat("HH:mm", Locale.US).format(Date(startedAt))) else Str.SHIFT_OFF.get(lang),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (interrupted) Text(Str.SHIFT_INTERRUPTED.get(lang), color = MaterialTheme.colorScheme.error)
                // E7 (CAPTURE.md 5.2): vardiya açık, ekran okuma çalışmıyor → kırmızı satır, dokununca erişilebilirlik ayarı.
                // Rawstats ana sayfası her çizimde bakardı; burada 5 sn'de bir (uyarı denetimiyle aynı tempo).
                val screenReadingOffTitle by produceState<String?>(null, on, lang) {
                    while (true) { value = runCatching { CaptureStatus.offWarning(ctx, lang, on) }.getOrNull(); delay(CaptureStatus.CHECK_MS) }
                }
                if (screenReadingOffTitle != null) TextButton(onClick = { ctx.safely { CaptureStatus.openAccessibilitySettings(ctx) } }) {
                    Text("$screenReadingOffTitle  !", color = MaterialTheme.colorScheme.error)
                }
                status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (on && !interrupted) {
                    OutlinedButton(onClick = { ctx.safely { ShiftHub.stop() } }, modifier = Modifier.padding(top = 8.dp)) { Text(Str.SHIFT_STOP.get(lang)) }
                } else {
                    Button(onClick = {
                        ctx.safely {
                            if (Home.hasFine(ctx)) ShiftHub.start()
                            else askLocation.launch(locationPermissions())
                        }
                    }, modifier = Modifier.padding(top = 8.dp)) { Text(Str.SHIFT_START.get(lang)) }
                    if (interrupted) OutlinedButton(onClick = { ctx.safely { ShiftHub.stop() } }) { Text(Str.SHIFT_STOP.get(lang)) }
                }
            }
        }
    }
}

/** Vardiya için ince/kaba konum ve (Android 13+) bildirim izni; arka plan konumu ayrıca istenir (Android 11+ kuralı). */
fun locationPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()
