package com.kuryelog.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.reminders.ActiveReminder

/**
 * Hatırlatıcı kartı (`reminder-card`; MAP.md 13.16): haritayı kapatmayan yarı boy, tek satır (≤44 dp), uzun metin taşmaz; ✓ (`reminder-done`) tamamlar; erteleme düğmesi yok;
 * [active] null ise görünmez. İskelet (A6.2b): gövde A6.9'da.
 */
@Composable
fun ReminderCard(active: ActiveReminder?, lang: Lang, onDone: () -> Unit, modifier: Modifier = Modifier) {
    TODO("A6.9")
}
