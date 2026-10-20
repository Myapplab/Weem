package com.kuryelog.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kuryelog.app.map.MapDataStep
import com.kuryelog.logic.app.SetupStep
import com.kuryelog.logic.app.SetupText
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang

/**
 * İlk açılış kurulumu (G5, A7.2): dil → izinler → harita verisi → pil rehberi. Hiçbir adım zorunlu değil; "Kurulumu atla" ya da son
 * adımdaki "WEEM'i aç" [onDone]'u çağırır (`weem.setupDone`). Dil seçimi anında uygulanır, sonraki adımlar seçilen dilde.
 */
@Composable
fun SetupScreen(lang: Lang, langSetting: String, onLangSetting: (String) -> Unit, onDone: () -> Unit) {
    var stepIndex by rememberSaveable { mutableStateOf(0) }
    val step = SetupStep.fromOrdinal(stepIndex)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(
                    SetupText.STEP_COUNT.get(lang).format(step.ordinal + 1, SetupStep.entries.size),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(step.title.get(lang), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 12.dp))
                GuardedPage("setup_${step.name.lowercase()}", lang, build = {}) {
                    when (step) {
                        SetupStep.LANGUAGE -> LanguageStep(lang, langSetting, onLangSetting)
                        SetupStep.PERMISSIONS -> {
                            Body(SetupText.PERMISSIONS_TEXT.get(lang))
                            PermissionsCard(lang, alwaysOpen = true)
                        }
                        SetupStep.MAP -> {
                            Body(SetupText.MAP_TEXT.get(lang))
                            MapDataStep(lang, SetupText.MAP_READY.get(lang))
                        }
                        SetupStep.BATTERY -> {
                            Body(SetupText.BATTERY_TEXT.get(lang))
                            BatteryGuideCard(lang)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step.ordinal > 0) OutlinedButton(onClick = { stepIndex = step.prev().ordinal }) { Text(SetupText.PREV.get(lang)) }
                if (!step.isLast) TextButton(onClick = onDone) { Text(SetupText.SKIP.get(lang)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (step.isLast) onDone() else stepIndex = step.next().ordinal }) {
                    Text((if (step.isLast) SetupText.FINISH else SetupText.NEXT).get(lang))
                }
            }
        }
    }
}

@Composable
private fun LanguageStep(lang: Lang, langSetting: String, onLangSetting: (String) -> Unit) {
    Text(SetupText.WELCOME_TITLE.get(lang), style = MaterialTheme.typography.titleLarge)
    Body(SetupText.WELCOME_TEXT.get(lang))
    Column(Modifier.selectableGroup()) {
        Choice(Str.LANG_SYSTEM.get(lang), langSetting.isEmpty()) { onLangSetting("") }
        Choice(Str.LANG_TR.get(lang), langSetting == Lang.TR.key) { onLangSetting(Lang.TR.key) }
        Choice(Str.LANG_AZ.get(lang), langSetting == Lang.AZ.key) { onLangSetting(Lang.AZ.key) }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
}
