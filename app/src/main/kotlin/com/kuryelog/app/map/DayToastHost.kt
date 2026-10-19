package com.kuryelog.app.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.logic.map.day.DayToast
import com.kuryelog.logic.map.day.Haptic
import com.kuryelog.logic.map.ui.ToastTone
import kotlinx.coroutines.delay

/**
 * Harita sekmesinin bildirim şeridi (MAP.md 7.11, 11.14; YolGo `toastProvider`): gün motorunun toast'larını [DayRuntime.toasts]'tan toplar. Şerit yalnız Harita sekmesi
 * görünürken (bu bileşen composition'dayken) dinler: kaçan toast sonradan gösterilmez. Süre [DayToast.seconds]; titreşim şiddeti toast'tan. Navigasyonun "Geri al" toast'ı A6.6'da
 * aynı şeride eklenir.
 */
@Composable
fun DayToastHost(modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<DayToast?>(null) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        DayRuntime.toasts.collect { t ->
            current = t
            if (t.haptic != Haptic.NONE) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val shown = current
    LaunchedEffect(shown) {
        if (shown == null) return@LaunchedEffect
        delay(shown.seconds * 1000L)
        if (current === shown) current = null
    }
    if (shown != null) {
        Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("day-toast")) {
            Surface(shape = RoundedCornerShape(14.dp), color = toneColor(shown.tone), shadowElevation = 3.dp) {
                Text(shown.text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

private fun toneColor(t: ToastTone): Color = Color(
    when (t) {
        ToastTone.GREEN -> 0xFF137D65
        ToastTone.PURPLE -> 0xFF7B4DFF
        ToastTone.ORANGE -> 0xFFD45D00
        ToastTone.RED -> 0xFFD7002E
        ToastTone.BIKEWAY -> 0xFF00838F
        ToastTone.PINK -> 0xFFD81B74
        ToastTone.FOOD -> 0xFFC2570C
        ToastTone.BLUE -> 0xFF1765D1
    },
)
