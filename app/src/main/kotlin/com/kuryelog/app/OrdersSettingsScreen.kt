package com.kuryelog.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.economics.NetSettings
import com.kuryelog.logic.orders.CustomerCard
import com.kuryelog.logic.orders.OrderSettings
import com.kuryelog.logic.orders.RestaurantBubble
import com.kuryelog.logic.app.Text as Str

/**
 * Daha fazla → Siparişler ayarları (A4.2, S20 (b); RS `renderSettings` "Özel ayarlar"): filo komisyonu (platform başına), ekran ve kart
 * tercihleri, izlenen uygulamalar; ve yeni: kWh fiyatı, şarj verimi, günlük net hedef (N4, N8). Tercihler hemen yazılır ([Store.values]);
 * anahtarlar ve varsayılanlar `OrderSettings` / `NetSettings`'te. Sayı kutuları boş kaydedilirse değeri siler (komisyon hariç: 0).
 */
@Composable
fun OrdersSettingsScreen(lang: Lang, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    // `version` her yazıdan sonra artar; değerler Compose durumundan değil tercihlerden okunur.
    val values = remember(version) { Store.values(ctx) }
    fun write(map: Map<String, Any?>) { ctx.safely { Store.values(ctx).put(map); version++ } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(Str.BACK.get(lang)) }
            Text(Str.OSET_TITLE.get(lang), style = MaterialTheme.typography.headlineMedium)
        }
        // Sayı düzenleme ekranın içinde açılır (pencere değil): hem telefonda klavyeyle birlikte rahat görünür hem de Robolectric testinde çizilir.
        (dialog as? Dialog.Number)?.let { NumberEditor(lang, it) { dialog = null } }

        SettingsHeading(Str.OSET_COMMISSION.get(lang))
        val groups = OrderSettings.platformPackages(Store.targets(ctx))
        if (groups.isEmpty()) Text(Str.OSET_NO_PLATFORM.get(lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
        for ((name, pkgs) in groups) {
            SettingsRow(name, "%" + OrderSettings.pctText(OrderSettings.commissionPct(values, pkgs.first()))) {
                dialog = Dialog.Number(Str.OSET_COMMISSION_ASK.get(lang).format(name), OrderSettings.pctText(OrderSettings.commissionPct(values, pkgs.first())), 0.0, OrderSettings.MAX_COMMISSION, false) { v ->
                    write(OrderSettings.commissionWrite(pkgs, v ?: 0.0))
                }
            }
        }
        Text(Str.OSET_COMMISSION_NOTE.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SettingsHeading(Str.OSET_NET.get(lang))
        val prices = NetSettings.read(values)
        SettingsRow(Str.OSET_KWH.get(lang), prices.kwhPrice?.let { num(it) } ?: Str.OSET_UNSET.get(lang)) {
            dialog = Dialog.Number(Str.OSET_KWH.get(lang), prices.kwhPrice?.let { num(it) }.orEmpty(), 0.0, 10.0, true) { v -> write(mapOf(NetSettings.KWH_PRICE to v?.toFloat())) }
        }
        val effText = num(prices.chargeEfficiencyPct)
        val effSet = values.getFloat(NetSettings.CHARGE_EFFICIENCY, Float.NaN).isFinite()
        SettingsRow(Str.OSET_EFFICIENCY.get(lang), "%" + if (effSet) effText else Str.OSET_DEFAULT.get(lang).format(effText)) {
            dialog = Dialog.Number(Str.OSET_EFFICIENCY.get(lang), if (effSet) effText else "", NetSettings.MIN_EFFICIENCY_PCT, NetSettings.MAX_EFFICIENCY_PCT, true) { v -> write(mapOf(NetSettings.CHARGE_EFFICIENCY to v?.toFloat())) }
        }
        SettingsRow(Str.OSET_GOAL.get(lang), prices.dailyGoal?.let { num(it) } ?: Str.OSET_UNSET.get(lang)) {
            dialog = Dialog.Number(Str.OSET_GOAL.get(lang), prices.dailyGoal?.let { num(it) }.orEmpty(), 0.0, 10_000.0, true) { v -> write(mapOf(NetSettings.DAILY_GOAL to v?.takeIf { it > 0.0 }?.toFloat())) }
        }
        Text(Str.OSET_NET_NOTE.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SettingsHeading(Str.OSET_SCREEN.get(lang))
        SettingsSwitch(Str.OSET_OVERLAY.get(lang), values.getBoolean(OrderSettings.OVERLAY, true)) { write(mapOf(OrderSettings.OVERLAY to it)) }
        SettingsSwitch(Str.OSET_OCR.get(lang), values.getBoolean(OrderSettings.OCR, true)) { write(mapOf(OrderSettings.OCR to it)) }
        SettingsSwitch(Str.OSET_CARD.get(lang), values.getBoolean(OrderSettings.CUSTOMER_CARD, true)) { write(mapOf(OrderSettings.CUSTOMER_CARD to it)) }
        val bubble = RestaurantBubble.widthPct(values.getInt(RestaurantBubble.WIDTH_KEY, RestaurantBubble.DEFAULT_WIDTH_PCT))
        SettingsRow(Str.OSET_BUBBLE.get(lang), "%$bubble") {
            dialog = Dialog.Choice(Str.OSET_BUBBLE_ASK.get(lang), RestaurantBubble.WIDTH_CHOICES, bubble) { write(mapOf(RestaurantBubble.WIDTH_KEY to it)) }
        }
        val card = CustomerCard.sizePct(values.getInt(CustomerCard.SIZE_KEY, CustomerCard.DEFAULT_SIZE_PCT))
        SettingsRow(Str.OSET_CARD_SIZE.get(lang), "%$card") {
            dialog = Dialog.Choice(Str.OSET_CARD_SIZE_ASK.get(lang), CustomerCard.SIZE_CHOICES, card) { write(mapOf(CustomerCard.SIZE_KEY to it)) }
        }

        SettingsHeading(Str.OSET_APPS.get(lang))
        val targets = Store.targets(ctx)
        if (targets.isEmpty()) Text(Str.OSET_APPS_NONE.get(lang), color = MaterialTheme.colorScheme.error)
        targets.sorted().forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
        TextButton(onClick = { dialog = Dialog.Apps }) { Text(Str.OSET_APPS_PICK.get(lang)) }
    }

    when (val d = dialog) {
        null -> {}
        is Dialog.Number -> {}
        is Dialog.Choice -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(d.title) },
            text = { Column { d.choices.forEach { c -> ChoiceRow("%$c", c == d.current) { d.save(c); dialog = null } } } },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(Str.CANCEL.get(lang)) } },
        )
        Dialog.Apps -> AppsDialog(lang) { saved -> dialog = null; if (saved) version++ }
    }
}

private sealed interface Dialog {
    class Number(val title: String, val initial: String, val min: Double, val max: Double, val clearable: Boolean, val save: (Double?) -> Unit) : Dialog
    class Choice(val title: String, val choices: List<Int>, val current: Int, val save: (Int) -> Unit) : Dialog
    data object Apps : Dialog
}

/** 12.5 → "12.5", 90.0 → "90" (gereksiz sıfır yok). */
private fun num(v: Double): String = if (v == Math.rint(v)) v.toLong().toString() else v.toString()

@Composable
private fun NumberEditor(lang: Lang, d: Dialog.Number, close: () -> Unit) {
    var text by remember(d) { mutableStateOf(d.initial) }
    var error by remember(d) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(d.title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text, onValueChange = { text = it; error = false }, singleLine = true, isError = error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (d.clearable) Text(Str.OSET_EMPTY_CLEARS.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error) Text(Str.OSET_INVALID.get(lang).format(num(d.min), num(d.max)), color = MaterialTheme.colorScheme.error)
            Row {
                TextButton(onClick = {
                    if (text.isBlank() && d.clearable) { d.save(null); close(); return@TextButton }
                    val v = NetSettings.parse(text, d.min, d.max)
                    if (v == null) error = true else { d.save(v); close() }
                }) { Text(Str.SAVE.get(lang)) }
                TextButton(onClick = close) { Text(Str.CANCEL.get(lang)) }
            }
        }
    }
}

@Composable
private fun AppsDialog(lang: Lang, close: (saved: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { TargetPicker.pickList(ctx) }
    val checked = remember { mutableStateListOf<String>().apply { addAll(Store.targets(ctx)) } }
    AlertDialog(
        onDismissRequest = { close(false) },
        title = { Text(Str.OSET_APPS.get(lang)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for ((pkg, label) in apps) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { if (pkg in checked) checked.remove(pkg) else checked.add(pkg) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = pkg in checked, onCheckedChange = null)
                        Column(Modifier.padding(start = 12.dp)) { Text(label); Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { ctx.safely { TargetPicker.save(ctx, checked.toSet()) }; close(true) }) { Text(Str.SAVE.get(lang)) } },
        dismissButton = { TextButton(onClick = { close(false) }) { Text(Str.CANCEL.get(lang)) } },
    )
}

@Composable
private fun SettingsHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SettingsSwitch(label: String, on: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = on, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = null)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.RadioButton, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}
