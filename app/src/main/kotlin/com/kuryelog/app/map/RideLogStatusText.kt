package com.kuryelog.app.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.logs.RideLogStatus

/** Sürüş kayıtları ekranının durum yazısı (`ride-log-status`; MAP.md 12.10): `logs.RideLogStatus` metinleri, seçili dilde; kayıt sürerken kırmızı. */
@Composable
fun RideLogStatusText(logging: Boolean, dayActive: Boolean, lang: Lang, modifier: Modifier = Modifier) {
    val (tr, az) = RideLogStatus.texts(logging, dayActive)
    Text(
        if (lang == Lang.AZ) az else tr,
        modifier.testTag("ride-log-status"),
        style = MaterialTheme.typography.bodySmall,
        color = if (logging && dayActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
