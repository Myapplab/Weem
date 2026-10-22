package com.kuryelog.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kuryelog.app.ui.StatusPill
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.withoutGlyph
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.logs.RideLogStatus

/** Sürüş kayıtları ekranının durum yazısı (`ride-log-status`; MAP.md 12.10): `logs.RideLogStatus` metinleri, seçili dilde; kayıt sürerken kırmızı. */
@Composable
fun RideLogStatusText(logging: Boolean, dayActive: Boolean, lang: Lang, modifier: Modifier = Modifier) {
    val (tr, az) = RideLogStatus.texts(logging, dayActive)
    // A10.4d: "● " öneki hapın noktasına döner; kayıt sürerken kırmızı nokta.
    StatusPill(
        (if (lang == Lang.AZ) az else tr).withoutGlyph(), if (logging && dayActive) WTone.DANGER else WTone.NEUTRAL,
        modifier.testTag("ride-log-status"),
    )
}
