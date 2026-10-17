package com.kuryelog.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kuryelog.logic.app.Lang
import com.kuryelog.logic.app.Tab
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.app.ThemeMode
import com.kuryelog.logic.app.label

private val tabIcon = mapOf(
    Tab.MAP to "🗺", Tab.ORDERS to "📋", Tab.SCOOTER to "🛵", Tab.REMOTE to "📱", Tab.MORE to "⋯",
)

/** Alt menü kabuğu (A1.3). Her sekmenin kendi durumu (kaydırma, sayaç…) sekme değişince saklanır. */
@Composable
fun WeemApp(
    lang: Lang,
    langSetting: String,
    theme: ThemeMode,
    versionName: String,
    onLangSetting: (String) -> Unit,
    onTheme: (ThemeMode) -> Unit,
) {
    var selectedKey by rememberSaveable { mutableStateOf(Tab.start.key) }
    val selected = Tab.fromKey(selectedKey)
    val holder = rememberSaveableStateHolder()
    Scaffold(
        bottomBar = {
            NavigationBar {
                for (tab in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { selectedKey = tab.key },
                        icon = { Text(tabIcon.getValue(tab)) },
                        label = { Text(tab.label.get(lang)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            holder.SaveableStateProvider(selected.key) {
                if (selected == Tab.MORE) {
                    MoreScreen(lang, langSetting, theme, versionName, onLangSetting, onTheme)
                } else {
                    PlaceholderScreen(selected, lang)
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(tab: Tab, lang: Lang) {
    var taps by rememberSaveable { mutableStateOf(0) }
    val debuggable = BuildInfo.isDebuggable(LocalContext.current)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tab.label.get(lang), style = MaterialTheme.typography.headlineMedium)
        Text(Str.PLACEHOLDER_COMING.get(lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (debuggable) {
            // .dev denemesi (A1.5): sayaç sekme değişince korunmalı.
            Button(onClick = { taps++ }, modifier = Modifier.padding(top = 16.dp)) {
                Text(Str.PLACEHOLDER_COUNTER.get(lang).format(taps))
            }
        }
    }
}

@Composable
private fun MoreScreen(
    lang: Lang,
    langSetting: String,
    theme: ThemeMode,
    versionName: String,
    onLangSetting: (String) -> Unit,
    onTheme: (ThemeMode) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(Str.SETTINGS_TITLE.get(lang), style = MaterialTheme.typography.headlineMedium)

        Section(Str.SETTINGS_LANGUAGE.get(lang)) {
            Choice(Str.LANG_SYSTEM.get(lang), langSetting.isEmpty()) { onLangSetting("") }
            Choice(Str.LANG_TR.get(lang), langSetting == Lang.TR.key) { onLangSetting(Lang.TR.key) }
            Choice(Str.LANG_AZ.get(lang), langSetting == Lang.AZ.key) { onLangSetting(Lang.AZ.key) }
        }
        Section(Str.SETTINGS_THEME.get(lang)) {
            Choice(Str.THEME_SYSTEM.get(lang), theme == ThemeMode.SYSTEM) { onTheme(ThemeMode.SYSTEM) }
            Choice(Str.THEME_LIGHT.get(lang), theme == ThemeMode.LIGHT) { onTheme(ThemeMode.LIGHT) }
            Choice(Str.THEME_DARK.get(lang), theme == ThemeMode.DARK) { onTheme(ThemeMode.DARK) }
        }
        Text(
            Str.VERSION.get(lang).format(versionName),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
    Column(Modifier.selectableGroup()) { content() }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}
