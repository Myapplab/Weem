package com.kuryelog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kuryelog.app.map.MapDataStep
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.RadioRow
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.palette
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
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = WeemSpace.l, vertical = WeemSpace.l),
                verticalArrangement = Arrangement.spacedBy(WeemSpace.m),
            ) {
                StepBar(step.ordinal, SetupStep.entries.size)
                Text(
                    SetupText.STEP_COUNT.get(lang).format(step.ordinal + 1, SetupStep.entries.size),
                    style = WeemType.labelM, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(step.title.get(lang), style = WeemType.headline)
                GuardedPage("setup_${step.name.lowercase()}", lang, build = {}) {
                    Column(verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
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
            }
            // Başparmak bölgesi: ileri/geri yarı yarıya (uzun Azerice metin iki satıra iner), atla altta.
            Column(Modifier.fillMaxWidth().padding(horizontal = WeemSpace.l, vertical = WeemSpace.s), verticalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                    if (step.ordinal > 0) WButton(SetupText.PREV.get(lang), { stepIndex = step.prev().ordinal }, Modifier.weight(1f), kind = BtnKind.OUTLINE)
                    WButton(
                        (if (step.isLast) SetupText.FINISH else SetupText.NEXT).get(lang),
                        { if (step.isLast) onDone() else stepIndex = step.next().ordinal },
                        Modifier.weight(1f), kind = BtnKind.PRIMARY,
                    )
                }
                if (!step.isLast) WButton(SetupText.SKIP.get(lang), onDone, kind = BtnKind.TEXT, fill = true)
            }
        }
    }
}

/** Adım çubuğu: her adım bir parça, geçilen ve şimdiki adım vurgu renginde. */
@Composable
private fun StepBar(current: Int, count: Int) {
    val p = palette()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
        repeat(count) { i ->
            Box(Modifier.weight(1f).height(4.dp).background((if (i <= current) p.accent else p.high).color(), CircleShape))
        }
    }
}

@Composable
private fun LanguageStep(lang: Lang, langSetting: String, onLangSetting: (String) -> Unit) {
    Text(SetupText.WELCOME_TITLE.get(lang), style = WeemType.titleL)
    Body(SetupText.WELCOME_TEXT.get(lang))
    SettingsGroup(null, dividerInset = 52.dp, selectableGroup = true) {
        RadioRow(Str.LANG_SYSTEM.get(lang), langSetting.isEmpty(), { onLangSetting("") })
        RadioRow(Str.LANG_TR.get(lang), langSetting == Lang.TR.key, { onLangSetting(Lang.TR.key) })
        RadioRow(Str.LANG_AZ.get(lang), langSetting == Lang.AZ.key, { onLangSetting(Lang.AZ.key) })
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = WeemType.bodyM, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
