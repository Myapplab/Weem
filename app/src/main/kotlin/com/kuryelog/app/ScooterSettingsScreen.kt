package com.kuryelog.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.app.core.Store
import com.kuryelog.app.scooter.FloatingPanel
import com.kuryelog.app.scooter.KeyVault
import com.kuryelog.app.scooter.LoggerPreferences
import com.kuryelog.app.scooter.PairAttention
import com.kuryelog.app.scooter.Scooter
import com.kuryelog.app.scooter.ScooterShiftLink
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.scooter.FloatRules
import com.kuryelog.logic.scooter.LogExport
import com.kuryelog.logic.scooter.NinebotMode
import com.kuryelog.logic.scooter.PanelField
import com.kuryelog.logic.scooter.ScooterSettings
import com.kuryelog.logic.scooter.ScooterUiText as T
import kotlinx.coroutines.delay

private sealed interface SettingsAsk {
    data class Message(val text: String) : SettingsAsk
    data object Overlay : SettingsAsk
    data object Fields : SettingsAsk
    data object Order : SettingsAsk
    data object Size : SettingsAsk
    data object Clean : SettingsAsk
    data object Info : SettingsAsk
    data object Key : SettingsAsk
}

/**
 * Scooter ayarları ve tanı (S7, S8; NB `MainActivity`, `InfoSheet`): durum paneli (açıkken `summaryWatched`), Panel (açılışta canlı panel,
 * vardiyada otomatik bağlantı, yüzen panel ve izni, değerler, sıra/top, boyut), Kayıt ve depolama, Sistem, Bağlantı (bilgi, harici
 * batarya taraması, gelişmiş anahtar), Tanı (ayrıntılı teşhis, sweep). Bağlantı açıkken anahtar, tarama ve sweep değişmez. Bölümler
 * ekrandan çıkınca kapanır. Tercihler `logger` dosyasında (VERI_SOZLESMESI 5.2); vardiya bağı `kuryelog`'da.
 */
@Composable
fun ScooterSettingsScreen(lang: Lang, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { LoggerPreferences.get(ctx) }
    BackHandler(onBack = onBack)
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        // NB `onCreate`: protokol tek seçenek.
        prefs.edit().putString("crypto_mode", NinebotMode.GEN2_FW.setting).apply()
        while (true) { delay(1_000); tick++ }
    }
    var ask by remember { mutableStateOf<SettingsAsk?>(null) }
    var statusOpen by remember { mutableStateOf(false) }
    DisposableEffect(statusOpen) {
        Scooter.summaryWatched = statusOpen
        onDispose { Scooter.summaryWatched = false }
    }
    val idle = remember(tick) { !Scooter.active }
    val p = LocalWeemPalette.current

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = T.BACK.get(lang) }) { Text("←", fontSize = 22.sp) }
            Text(T.SETTINGS_TITLE.get(lang), fontSize = 23.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp).semantics { heading() })
        }
        if (remember(tick) { PairAttention.visible() }) {
            Text(
                T.PAIR_ATTENTION.get(lang), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(p.warn),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).background(Color(p.warnBg), RoundedCornerShape(24.dp)).padding(18.dp),
            )
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)) {
            val name = remember(tick) { Scooter.deviceName(ctx) }
            Group("🛵", T.STATUS_TITLE.get(lang), ScooterSettings.selectedTitle(name, lang), statusOpen, { statusOpen = it }) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
                    Info(T.STATUS_VERSION.get(lang).format(LogExport.VERSION))
                    Info(T.SELECTED.get(lang).format(name.ifEmpty { T.NONE.get(lang) }))
                    Text(
                        remember(tick) { Scooter.summary },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                    Info(T.PROTOCOL.get(lang))
                }
            }
            Section("▣", T.SECTION_PANEL.get(lang)) {
                PrefSwitch(T.AUTO_LIVE.get(lang), T.AUTO_LIVE_SUB.get(lang), prefs.getBoolean("auto_live", false)) { prefs.edit().putBoolean("auto_live", it).apply() }
                PrefSwitch(T.SHIFT_AUTO.get(lang), T.SHIFT_AUTO_SUB.get(lang), ScooterShiftLink.autoConnect(ctx)) {
                    Store.prefs(ctx).edit().putBoolean(ScooterShiftLink.PREF_AUTO, it).apply()
                }
                val permitted = remember(tick) { FloatingPanel.permitted(ctx) }
                PrefSwitch(T.FLOATING.get(lang), ScooterSettings.floatingLabel(permitted, lang), remember(tick) { FloatingPanel.enabled(ctx) }) {
                    prefs.edit().putBoolean(FloatRules.PREF_ON, it).apply()
                    if (it && !FloatingPanel.permitted(ctx)) ask = SettingsAsk.Overlay
                }
                val fields = remember(tick) { FloatingPanel.fields(ctx) }
                Nav(T.FIELDS.get(lang), ScooterSettings.fieldsLabel(fields, lang)) { ask = SettingsAsk.Fields }
                Nav(T.ORDER.get(lang), T.ORDER_BALL.get(lang).format(FloatRules.ballField(prefs.getString(FloatRules.PREF_BALL_FIELD, ""), fields).title(lang))) { ask = SettingsAsk.Order }
                Nav(T.SIZE.get(lang), ScooterSettings.sizeLabel(ScooterSettings.SIZES.map { it.clamp(prefs.getInt(it.key, it.default)) }, lang)) { ask = SettingsAsk.Size }
            }
            Section("▤", T.SECTION_STORAGE.get(lang)) {
                Nav(T.CLEAN.get(lang), T.CLEAN_SUB.get(lang)) {
                    ask = if (Scooter.recording || Scooter.maintenance) SettingsAsk.Message(T.EXPORT_BUSY.get(lang)) else SettingsAsk.Clean
                }
            }
            Section("◐", T.SECTION_SYSTEM.get(lang)) {
                PrefSwitch(T.KEEP_SCREEN.get(lang), null, prefs.getBoolean("keep_screen_on", true)) { prefs.edit().putBoolean("keep_screen_on", it).apply() }
                Nav(T.APP_SETTINGS.get(lang), T.APP_SETTINGS_SUB.get(lang)) {
                    ctx.safely { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))) }
                }
                Nav(T.GPS_SETTINGS.get(lang), T.GPS_SETTINGS_SUB.get(lang)) { ctx.safely { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }
            }
            Section("ⓘ", T.SECTION_LINK.get(lang)) {
                Nav(T.INFO.get(lang), T.INFO_SUB.get(lang)) { ask = SettingsAsk.Info }
                PrefSwitch(T.PROBE.get(lang), ScooterSettings.probeLabel(remember(tick) { Scooter.externalBattery(ctx) }, lang),
                    remember(tick) { Scooter.externalProbePending(ctx) }, enabled = idle) { prefs.edit().putBoolean("battery_probe", it).apply() }
                Nav(T.KEY.get(lang), T.KEY_SUB.get(lang), enabled = idle) {
                    ask = if (Scooter.device(ctx).isEmpty()) SettingsAsk.Message(T.KEY_PICK_FIRST.get(lang)) else SettingsAsk.Key
                }
            }
            Section("⌁", T.SECTION_DIAG.get(lang)) {
                PrefSwitch(T.DETAILED.get(lang), T.DETAILED_SUB.get(lang), prefs.getBoolean("detailed_diagnostics", false)) { prefs.edit().putBoolean("detailed_diagnostics", it).apply() }
                PrefSwitch(T.SWEEP.get(lang), T.SWEEP_SUB.get(lang) + if (idle) "" else " • " + T.WHEN_IDLE.get(lang), prefs.getBoolean("sweep", false), enabled = idle) {
                    prefs.edit().putBoolean("sweep", it).apply()
                }
                Info(T.DIAG_FULL_HINT.get(lang), Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }
    }

    when (val a = ask) {
        null -> {}
        is SettingsAsk.Message -> AlertDialog(onDismissRequest = { ask = null }, text = { Text(a.text) },
            confirmButton = { TextButton(onClick = { ask = null }) { Text(T.OK.get(lang)) } })
        SettingsAsk.Overlay -> AlertDialog(
            onDismissRequest = { ask = null }, title = { Text(T.OVERLAY_TITLE.get(lang)) }, text = { Text(T.OVERLAY_TEXT.get(lang)) },
            confirmButton = {
                TextButton(onClick = {
                    ask = try {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
                        null
                    } catch (e: RuntimeException) { SettingsAsk.Message(T.OVERLAY_FAILED.get(lang)) }
                }) { Text(T.OVERLAY_OPEN.get(lang)) }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(T.LATER.get(lang)) } },
        )
        SettingsAsk.Fields -> FieldsDialog(ctx, lang) { ask = null; tick++ }
        SettingsAsk.Order -> OrderDialog(ctx, lang) { ask = null; tick++ }
        SettingsAsk.Size -> SizeDialog(ctx, lang) { ask = null; tick++ }
        SettingsAsk.Clean -> AlertDialog(
            onDismissRequest = { ask = null }, title = { Text(T.CLEAN_TITLE.get(lang)) }, text = { Text(T.CLEAN_TEXT.get(lang)) },
            confirmButton = {
                TextButton(onClick = {
                    ask = SettingsAsk.Message(T.CLEANING.get(lang))
                    ctx.safely("scooter_clean") { Scooter.cleanOld(ctx, lang) { ask = SettingsAsk.Message(it) } }
                }) { Text(T.CLEAN_TITLE.get(lang)) }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(T.CANCEL.get(lang)) } },
        )
        SettingsAsk.Info -> AlertDialog(
            onDismissRequest = { ask = null }, title = { Text(T.INFO.get(lang)) },
            text = {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                    for ((title, body) in T.INFO_SECTIONS) {
                        Text(title.get(lang), fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp))
                        Text(body.get(lang), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ask = null }) { Text(T.CLOSE.get(lang)) } },
        )
        SettingsAsk.Key -> KeyDialog(ctx, lang, { ask = null }) { ask = SettingsAsk.Message(it) }
    }
}

/** En çok [PanelField.MAX] değer, seçim sırasıyla; hız varsayılan kapalı (NB `chooseFields`). */
@Composable
private fun FieldsDialog(ctx: Context, lang: Lang, onClose: () -> Unit) {
    val current = remember { mutableStateListOf<PanelField>().also { it.addAll(FloatingPanel.fields(ctx)) } }
    var limit by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text(T.FIELDS_MAX.get(lang).format(PanelField.MAX.toString())) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (limit) Text(T.FIELDS_LIMIT.get(lang).format(PanelField.MAX.toString()), color = MaterialTheme.colorScheme.error)
                for (f in PanelField.entries) {
                    val on = f in current
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { limit = !ScooterSettings.toggle(current, f, !on) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Text(f.title(lang) + " (" + f.unit(lang) + ")", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                LoggerPreferences.get(ctx).edit().putString(FloatRules.PREF_FIELDS, ScooterSettings.fieldsToSave(current)).apply()
                onClose()
            }) { Text(T.SAVE.get(lang)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(T.CANCEL.get(lang)) } },
    )
}

/** Bardaki sıra (oklar) ve topta görünen değer (daire) (NB `chooseOrder`). */
@Composable
private fun OrderDialog(ctx: Context, lang: Lang, onClose: () -> Unit) {
    val prefs = LoggerPreferences.get(ctx)
    val list = remember { mutableStateListOf<PanelField>().also { it.addAll(FloatingPanel.fields(ctx)) } }
    var ball by remember { mutableStateOf(FloatRules.ballField(prefs.getString(FloatRules.PREF_BALL_FIELD, ""), list)) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text(T.ORDER_TITLE.get(lang)) },
        text = {
            Column {
                list.forEachIndexed { i, f ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = f == ball, onClick = { ball = f }, modifier = Modifier.semantics { contentDescription = T.ORDER_BALL_DESC.get(lang).format(f.title(lang)) })
                        Text("${i + 1}. " + f.title(lang), modifier = Modifier.weight(1f))
                        TextButton(enabled = i > 0, onClick = { list.add(i - 1, list.removeAt(i)) },
                            modifier = Modifier.width(52.dp).semantics { contentDescription = T.ORDER_UP.get(lang).format(f.title(lang)) }) { Text("↑") }
                        TextButton(enabled = i < list.size - 1, onClick = { list.add(i + 1, list.removeAt(i)) },
                            modifier = Modifier.width(52.dp).semantics { contentDescription = T.ORDER_DOWN.get(lang).format(f.title(lang)) }) { Text("↓") }
                    }
                }
                Info(T.ORDER_HINT.get(lang))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.edit().putString(FloatRules.PREF_FIELDS, PanelField.join(list)).putString(FloatRules.PREF_BALL_FIELD, ball.key).apply()
                onClose()
            }) { Text(T.SAVE.get(lang)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(T.CANCEL.get(lang)) } },
    )
}

/** Altı kaydırıcı: top, bar kalınlığı, yatay ve dikey uzunluk, iki yazı boyutu (NB `chooseSize`). */
@Composable
private fun SizeDialog(ctx: Context, lang: Lang, onClose: () -> Unit) {
    val prefs = LoggerPreferences.get(ctx)
    val chosen = remember { mutableStateListOf<Int>().also { l -> ScooterSettings.SIZES.forEach { l.add(it.clamp(prefs.getInt(it.key, it.default))) } } }
    AlertDialog(
        onDismissRequest = onClose, title = { Text(T.SIZE.get(lang)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                ScooterSettings.SIZES.forEachIndexed { i, s ->
                    Text(s.label(chosen[i], lang), modifier = Modifier.padding(top = 14.dp))
                    Slider(
                        value = chosen[i].toFloat(), onValueChange = { chosen[i] = Math.round(it) }, valueRange = s.min.toFloat()..s.max.toFloat(),
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = s.text.get(lang) },
                    )
                }
                Info(T.SIZE_HINT.get(lang))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = prefs.edit()
                ScooterSettings.SIZES.forEachIndexed { i, s -> e.putInt(s.key, chosen[i]) }
                e.apply()
                onClose()
            }) { Text(T.SAVE.get(lang)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { val e = prefs.edit(); ScooterSettings.SIZES.forEach { e.remove(it.key) }; e.apply(); onClose() }) { Text(T.DEFAULT.get(lang)) }
                TextButton(onClick = onClose) { Text(T.CANCEL.get(lang)) }
            }
        },
    )
}

/** Kendi scooter'ının 16 bayt anahtarı (NB `key`): telefonda Keystore ile şifreli; kayıt ZIP'ine girmez. */
@Composable
private fun KeyDialog(ctx: Context, lang: Lang, onClose: () -> Unit, onMessage: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val device = remember { Scooter.device(ctx) }
    val state = remember {
        try {
            val vault = KeyVault(ctx)
            val k = vault.get(device)
            k?.fill(0)
            ScooterSettings.keyState(k != null, vault.hasPending(device), vault.confirmed(device)).get(lang)
        } catch (e: RuntimeException) { T.KEY_BROKEN.get(lang) }
    }
    AlertDialog(
        onDismissRequest = onClose, title = { Text(T.KEY_TITLE.get(lang)) },
        text = {
            Column {
                Text(T.KEY_TEXT.get(lang) + "\n" + state)
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, singleLine = true, placeholder = { Text(T.KEY_HINT.get(lang)) },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(onClick = {
                    onMessage((if (runCatching { KeyVault(ctx).restore(device) }.getOrDefault(false)) T.KEY_RESTORED else T.KEY_NO_PREVIOUS).get(lang))
                }) { Text(T.KEY_RESTORE.get(lang)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val k = ScooterSettings.parseKey(input)
                input = ""
                onMessage(
                    if (k == null) T.KEY_BAD.get(lang) else try {
                        val vault = KeyVault(ctx)
                        vault.save(device, k, false)
                        vault.save(device, k, true)
                        T.KEY_SAVED.get(lang)
                    } catch (e: RuntimeException) { T.KEY_BAD.get(lang) } finally { k.fill(0) },
                )
            }) { Text(T.SAVE.get(lang)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(T.CANCEL.get(lang)) } },
    )
}

@Composable
private fun Info(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(top = 6.dp, bottom = 2.dp))
}

/** Açılır kart: başlığa dokununca açılır; ekrandan çıkınca kapanır (NB 0.22.2). */
@Composable
private fun Group(glyph: String, title: String, subtitle: String, open: Boolean, onOpen: (Boolean) -> Unit, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth().clickable { onOpen(!open) }.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(glyph, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (open) "⌃" else "⌄", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) content()
    }
}

@Composable
private fun Section(glyph: String, title: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Group(glyph, title, "", open, { open = it }) { Column(Modifier.padding(bottom = 8.dp)) { content() } }
}

/** Bütün satır tercihi çevirir; ekran okuyucu tek anahtar olarak okur. */
@Composable
private fun PrefSwitch(title: String, subtitle: String?, initial: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    var on by remember(initial) { mutableStateOf(initial) }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Switch) { on = !on; onChange(on) }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun Nav(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
