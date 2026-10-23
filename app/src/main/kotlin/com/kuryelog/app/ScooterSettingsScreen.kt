package com.kuryelog.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
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
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.CheckRow
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.DividedColumn
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.NoticeDialog
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.SwitchRow
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WSheet
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemTopBar
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.scooter.FloatRules
import com.kuryelog.logic.scooter.LogExport
import com.kuryelog.logic.scooter.NinebotMode
import com.kuryelog.logic.scooter.PanelField
import com.kuryelog.logic.scooter.ScooterSettings
import com.kuryelog.logic.scooter.ScooterStatusText
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

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WeemTopBar(T.SETTINGS_TITLE.get(lang), onBack, T.BACK.get(lang))
        if (remember(tick) { PairAttention.visible() }) {
            // Eşleştirme anı: büyük uyarı bandı (gidondan okunur).
            Text(
                T.PAIR_ATTENTION.get(lang), style = WeemType.titleL.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold),
                color = palette().warn.color(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = WeemSpace.l, vertical = WeemSpace.s)
                    .background(palette().warnBg.color(), RoundedCornerShape(WeemRadius.panel)).padding(WeemSpace.xl),
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = WeemSpace.l, end = WeemSpace.l, top = WeemSpace.xs, bottom = WeemSpace.x3),
            verticalArrangement = Arrangement.spacedBy(WeemSpace.m),
        ) {
            val name = remember(tick) { Scooter.deviceName(ctx) }
            Group(WeemIcons.ElectricScooter, T.STATUS_TITLE.get(lang), ScooterSettings.selectedTitle(name, lang), statusOpen, { statusOpen = it }) {
                Column(Modifier.padding(start = WeemSpace.l, end = WeemSpace.l, bottom = WeemSpace.l), verticalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                    Info(T.STATUS_VERSION.get(lang).format(LogExport.VERSION))
                    Info(T.SELECTED.get(lang).format(name.ifEmpty { T.NONE.get(lang) }))
                    Text(
                        remember(tick, lang) { ScooterStatusText.localize(Scooter.summary, lang) }, style = WeemType.bodyM,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(WeemRadius.tile)).padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                    Info(T.PROTOCOL.get(lang))
                }
            }
            Section(WeemIcons.Tune, T.SECTION_PANEL.get(lang)) {
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
            Section(WeemIcons.Folder, T.SECTION_STORAGE.get(lang)) {
                Nav(T.CLEAN.get(lang), T.CLEAN_SUB.get(lang)) {
                    ask = if (Scooter.recording || Scooter.maintenance) SettingsAsk.Message(T.EXPORT_BUSY.get(lang)) else SettingsAsk.Clean
                }
            }
            Section(WeemIcons.Settings, T.SECTION_SYSTEM.get(lang)) {
                // S37: pil, izin ve GPS ayarları burada yok (uygulama tek; Daha fazla → İzinler).
                PrefSwitch(T.KEEP_SCREEN.get(lang), null, prefs.getBoolean("keep_screen_on", true)) { prefs.edit().putBoolean("keep_screen_on", it).apply() }
            }
            Section(WeemIcons.Bluetooth, T.SECTION_LINK.get(lang)) {
                Nav(T.INFO.get(lang), T.INFO_SUB.get(lang)) { ask = SettingsAsk.Info }
                PrefSwitch(T.PROBE.get(lang), ScooterSettings.probeLabel(remember(tick) { Scooter.externalBattery(ctx) }, lang),
                    remember(tick) { Scooter.externalProbePending(ctx) }, enabled = idle) { prefs.edit().putBoolean("battery_probe", it).apply() }
                Nav(T.KEY.get(lang), T.KEY_SUB.get(lang), enabled = idle) {
                    ask = if (Scooter.device(ctx).isEmpty()) SettingsAsk.Message(T.KEY_PICK_FIRST.get(lang)) else SettingsAsk.Key
                }
            }
            Section(WeemIcons.Info, T.SECTION_DIAG.get(lang)) {
                PrefSwitch(T.DETAILED.get(lang), T.DETAILED_SUB.get(lang), prefs.getBoolean("detailed_diagnostics", false)) { prefs.edit().putBoolean("detailed_diagnostics", it).apply() }
                PrefSwitch(T.SWEEP.get(lang), T.SWEEP_SUB.get(lang) + if (idle) "" else " • " + T.WHEN_IDLE.get(lang), prefs.getBoolean("sweep", false), enabled = idle) {
                    prefs.edit().putBoolean("sweep", it).apply()
                }
                Info(T.DIAG_FULL_HINT.get(lang), Modifier.padding(horizontal = WeemSpace.l, vertical = WeemSpace.m))
            }
        }
    }

    when (val a = ask) {
        null -> {}
        is SettingsAsk.Message -> NoticeDialog(a.text, T.OK.get(lang), { ask = null })
        SettingsAsk.Overlay -> ConfirmDialog(
            title = T.OVERLAY_TITLE.get(lang), text = T.OVERLAY_TEXT.get(lang), confirm = T.OVERLAY_OPEN.get(lang), dismiss = T.LATER.get(lang),
            onConfirm = {
                ask = try {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
                    null
                } catch (e: RuntimeException) { SettingsAsk.Message(T.OVERLAY_FAILED.get(lang)) }
            },
            onDismiss = { ask = null },
        )
        SettingsAsk.Fields -> FieldsDialog(ctx, lang) { ask = null; tick++ }
        SettingsAsk.Order -> OrderDialog(ctx, lang) { ask = null; tick++ }
        SettingsAsk.Size -> SizeDialog(ctx, lang) { ask = null; tick++ }
        SettingsAsk.Clean -> ConfirmDialog(
            title = T.CLEAN_TITLE.get(lang), text = T.CLEAN_TEXT.get(lang), confirm = T.CLEAN_TITLE.get(lang), dismiss = T.CANCEL.get(lang), danger = true,
            onConfirm = {
                ask = SettingsAsk.Message(T.CLEANING.get(lang))
                ctx.safely("scooter_clean") { Scooter.cleanOld(ctx, lang) { ask = SettingsAsk.Message(it) } }
            },
            onDismiss = { ask = null },
        )
        SettingsAsk.Info -> WSheet(onDismiss = { ask = null }, title = T.INFO.get(lang)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
                for ((title, body) in T.INFO_SECTIONS) {
                    Text(title.get(lang), style = WeemType.titleM, modifier = Modifier.padding(top = WeemSpace.s))
                    Text(body.get(lang), style = WeemType.bodyM, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            WButton(T.CLOSE.get(lang), { ask = null }, kind = BtnKind.TONAL, fill = true)
        }
        SettingsAsk.Key -> KeyDialog(ctx, lang, { ask = null }) { ask = SettingsAsk.Message(it) }
    }
}

/** En çok [PanelField.MAX] değer, seçim sırasıyla; hız varsayılan kapalı (NB `chooseFields`). */
@Composable
private fun FieldsDialog(ctx: Context, lang: Lang, onClose: () -> Unit) {
    val current = remember { mutableStateListOf<PanelField>().also { it.addAll(FloatingPanel.fields(ctx)) } }
    var limit by remember { mutableStateOf(false) }
    WSheet(onDismiss = onClose, title = T.FIELDS_MAX.get(lang).format(PanelField.MAX.toString())) {
        if (limit) Banner(T.FIELDS_LIMIT.get(lang).format(PanelField.MAX.toString()), tone = WTone.WARN)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            SettingsGroup(null, dividerInset = 52.dp) {
                for (f in PanelField.entries) {
                    val on = f in current
                    CheckRow(f.title(lang) + " (" + f.unit(lang) + ")", on, { limit = !ScooterSettings.toggle(current, f, !on) })
                }
            }
        }
        SheetButtons(T.CANCEL.get(lang), T.SAVE.get(lang), onClose) {
            LoggerPreferences.get(ctx).edit().putString(FloatRules.PREF_FIELDS, ScooterSettings.fieldsToSave(current)).apply()
            onClose()
        }
    }
}

/** Bardaki sıra (oklar) ve topta görünen değer (daire) (NB `chooseOrder`). */
@Composable
private fun OrderDialog(ctx: Context, lang: Lang, onClose: () -> Unit) {
    val prefs = LoggerPreferences.get(ctx)
    val list = remember { mutableStateListOf<PanelField>().also { it.addAll(FloatingPanel.fields(ctx)) } }
    var ball by remember { mutableStateOf(FloatRules.ballField(prefs.getString(FloatRules.PREF_BALL_FIELD, ""), list)) }
    WSheet(onDismiss = onClose, title = T.ORDER_TITLE.get(lang)) {
        SettingsGroup(null, dividerInset = 52.dp) {
            list.forEachIndexed { i, f ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = WeemSpace.xs, end = WeemSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = f == ball, onClick = { ball = f }, modifier = Modifier.semantics { contentDescription = T.ORDER_BALL_DESC.get(lang).format(f.title(lang)) })
                    Text("${i + 1}. " + f.title(lang), style = WeemType.bodyL, modifier = Modifier.weight(1f))
                    WIconButton(WeemIcons.ExpandLess, T.ORDER_UP.get(lang).format(f.title(lang)), { list.add(i - 1, list.removeAt(i)) }, enabled = i > 0)
                    WIconButton(WeemIcons.ExpandMore, T.ORDER_DOWN.get(lang).format(f.title(lang)), { list.add(i + 1, list.removeAt(i)) }, enabled = i < list.size - 1)
                }
            }
        }
        Info(T.ORDER_HINT.get(lang))
        SheetButtons(T.CANCEL.get(lang), T.SAVE.get(lang), onClose) {
            prefs.edit().putString(FloatRules.PREF_FIELDS, PanelField.join(list)).putString(FloatRules.PREF_BALL_FIELD, ball.key).apply()
            onClose()
        }
    }
}

/** Altı kaydırıcı: top, bar kalınlığı, yatay ve dikey uzunluk, iki yazı boyutu (NB `chooseSize`). */
@Composable
private fun SizeDialog(ctx: Context, lang: Lang, onClose: () -> Unit) {
    val prefs = LoggerPreferences.get(ctx)
    val chosen = remember { mutableStateListOf<Int>().also { l -> ScooterSettings.SIZES.forEach { l.add(it.clamp(prefs.getInt(it.key, it.default))) } } }
    WSheet(onDismiss = onClose, title = T.SIZE.get(lang)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            ScooterSettings.SIZES.forEachIndexed { i, s ->
                Text(s.label(chosen[i], lang), style = WeemType.bodyM, modifier = Modifier.padding(top = WeemSpace.m))
                Slider(
                    value = chosen[i].toFloat(), onValueChange = { chosen[i] = Math.round(it) }, valueRange = s.min.toFloat()..s.max.toFloat(),
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = s.text.get(lang) },
                )
            }
            Info(T.SIZE_HINT.get(lang))
        }
        WButton(T.DEFAULT.get(lang), { val e = prefs.edit(); ScooterSettings.SIZES.forEach { e.remove(it.key) }; e.apply(); onClose() }, kind = BtnKind.TEXT, fill = true)
        SheetButtons(T.CANCEL.get(lang), T.SAVE.get(lang), onClose) {
            val e = prefs.edit()
            ScooterSettings.SIZES.forEachIndexed { i, s -> e.putInt(s.key, chosen[i]) }
            e.apply()
            onClose()
        }
    }
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
    ConfirmDialog(
        title = T.KEY_TITLE.get(lang), confirm = T.SAVE.get(lang), dismiss = T.CANCEL.get(lang), onDismiss = onClose,
        onConfirm = {
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
        },
    ) {
        Text(T.KEY_TEXT.get(lang) + "\n" + state, style = WeemType.bodyM)
        OutlinedTextField(
            value = input, onValueChange = { input = it }, singleLine = true, placeholder = { Text(T.KEY_HINT.get(lang)) }, shape = RoundedCornerShape(WeemRadius.tile),
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        WButton(T.KEY_RESTORE.get(lang), {
            onMessage((if (runCatching { KeyVault(ctx).restore(device) }.getOrDefault(false)) T.KEY_RESTORED else T.KEY_NO_PREVIOUS).get(lang))
        }, kind = BtnKind.TEXT, icon = WeemIcons.History)
    }
}

@Composable
private fun Info(text: String, modifier: Modifier = Modifier) {
    Text(text, style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Alt sayfanın en altındaki iki düğme: vazgeç (çerçeveli) + kaydet (dolgu). */
@Composable
private fun SheetButtons(cancel: String, save: String, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
        WButton(cancel, onCancel, Modifier.weight(1f), kind = BtnKind.OUTLINE)
        WButton(save, onSave, Modifier.weight(1f))
    }
}

/** Açılır kart: başlığa dokununca açılır; ekrandan çıkınca kapanır (NB 0.22.2). */
@Composable
private fun Group(icon: ImageVector, title: String, subtitle: String, open: Boolean, onOpen: (Boolean) -> Unit, content: @Composable () -> Unit) {
    WCard(padding = 0.dp, spacing = 0.dp) {
        ListRow(
            title, subtitle = subtitle.ifEmpty { null }, icon = icon, iconTone = WTone.ACCENT, onClick = { onOpen(!open) },
            trailing = { Icon(if (open) WeemIcons.ExpandLess else WeemIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
        AnimatedVisibility(open) {
            Column {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                content()
            }
        }
    }
}

@Composable
private fun Section(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Group(icon, title, "", open, { open = it }) { DividedColumn(MaterialTheme.colorScheme.outlineVariant, inset = WeemSpace.l) { content() } }
}

/** Bütün satır tercihi çevirir; ekran okuyucu tek anahtar olarak okur. */
@Composable
private fun PrefSwitch(title: String, subtitle: String?, initial: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    var on by remember(initial) { mutableStateOf(initial) }
    SwitchRow(title, on, { on = it; onChange(it) }, subtitle = subtitle, enabled = enabled)
}

@Composable
private fun Nav(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    ListRow(title, subtitle = subtitle.ifEmpty { null }, enabled = enabled, chevron = true, onClick = onClick)
}
