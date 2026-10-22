package com.kuryelog.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WChip
import com.kuryelog.app.ui.WSheet
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.DatasetText
import com.kuryelog.logic.orders.dataset.DatasetManual

/** Seçim sayfası + form (MAP.md 13.9): 8 ekran türü dosya seçiciyi ([onPick]) açar; haftalık ödeme ve vardiya notu formu [onSave] ile kaydedilir (hata dönerse satır içi gösterilir). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DatasetManualSheet(lang: Lang, onPick: (kind: String) -> Unit, onSave: (kind: String, input: Map<String, String?>) -> DatasetManual.Error?, onDismiss: () -> Unit) {
    var form by remember { mutableStateOf<String?>(null) }
    val kinds = DatasetManual.SCREENSHOT_KINDS + listOf(DatasetManual.PAYOUT, DatasetManual.NOTE)
    val field = RoundedCornerShape(WeemRadius.tile)
    if (form == null) {
        // K4: çok seçenekli pencere alttan açılan sayfa.
        WSheet(onDismiss = onDismiss, title = DatasetText.MANUAL_TITLE.get(lang)) {
            Text(DatasetText.MANUAL_SUB.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                SettingsGroup(null, dividerInset = 70.dp) {
                    for (k in kinds) {
                        val form1 = k == DatasetManual.PAYOUT || k == DatasetManual.NOTE
                        ListRow(
                            DatasetText.manualLabel(k, lang), Modifier.testTag("manual-kind-$k"),
                            icon = if (k == DatasetManual.PAYOUT) WeemIcons.Payments else if (k == DatasetManual.NOTE) WeemIcons.Edit else WeemIcons.Folder, chevron = true,
                            onClick = { if (form1) form = k else { onPick(k); onDismiss() } },
                        )
                    }
                }
            }
            WButton(DatasetText.CANCEL.get(lang), onDismiss, kind = BtnKind.TEXT, fill = true)
        }
        return
    }
    val kind = form!!
    val values = remember { mutableStateMapOf<String, String>() }
    var error by remember { mutableStateOf<DatasetManual.Error?>(null) }
    WSheet(onDismiss = onDismiss, title = DatasetText.manualLabel(kind, lang)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
            fun field(key: String, label: String) = @Composable {
                OutlinedTextField(values[key].orEmpty(), { values[key] = it; error = null }, Modifier.fillMaxWidth().testTag("manual-$key"), label = { Text(label) }, singleLine = true, shape = field)
            }
            @Composable
            fun choices(key: String, label: String, options: List<Pair<String, String>>) {
                Text(label, style = WeemType.labelM, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s), verticalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
                    WChip(DatasetText.UNKNOWN.get(lang), values[key].isNullOrEmpty(), { values[key] = "" })
                    for ((v, t) in options) WChip(t, values[key] == v, { values[key] = v }, Modifier.testTag("manual-$key-$v"))
                }
            }
            if (kind == DatasetManual.PAYOUT) {
                choices("platform", DatasetText.PLATFORM.get(lang), listOf("wolt" to "Wolt", "bolt" to "Bolt"))
                field("amountAzn", DatasetText.AMOUNT.get(lang))()
                field("periodStart", DatasetText.PERIOD_START.get(lang))()
                field("periodEnd", DatasetText.PERIOD_END.get(lang))()
            } else {
                field("breakMinutes", DatasetText.BREAK_MIN.get(lang))()
                choices(
                    "weather", DatasetText.WEATHER.get(lang),
                    listOf("clear" to DatasetText.W_CLEAR.get(lang), "rain" to DatasetText.W_RAIN.get(lang), "wind" to DatasetText.W_WIND.get(lang), "cold" to DatasetText.W_COLD.get(lang), "hot" to DatasetText.W_HOT.get(lang), "mixed" to DatasetText.W_MIXED.get(lang)),
                )
                choices("otherApp", DatasetText.OTHER_APP.get(lang), listOf("wolt" to "Wolt", "bolt" to "Bolt", "none" to DatasetText.A_NONE.get(lang)))
                choices(
                    "whileBusy", DatasetText.WHILE_BUSY.get(lang),
                    listOf("delivery" to DatasetText.B_DELIVERY.get(lang), "waiting" to DatasetText.B_WAITING.get(lang), "offline" to DatasetText.B_OFFLINE.get(lang), "break" to DatasetText.B_BREAK.get(lang), "none" to DatasetText.B_NONE.get(lang)),
                )
                field("declinePenaltyMinutes", DatasetText.PENALTY_MIN.get(lang))()
                field("declinePenaltyAzn", DatasetText.PENALTY_AZN.get(lang))()
            }
            error?.let { e ->
                Banner(
                    when (e) {
                        DatasetManual.Error.NUMBERS -> DatasetText.ERR_NUMBERS
                        DatasetManual.Error.DATE -> DatasetText.ERR_DATE
                        DatasetManual.Error.PAYOUT_REQUIRED -> DatasetText.ERR_PAYOUT
                        DatasetManual.Error.KIND -> DatasetText.ERR_NUMBERS
                    }.get(lang),
                    tone = WTone.DANGER,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            WButton(DatasetText.CANCEL.get(lang), onDismiss, Modifier.weight(1f), kind = BtnKind.OUTLINE)
            WButton(DatasetText.SAVE.get(lang), {
                val e = onSave(kind, values.toMap())
                if (e == null) onDismiss() else error = e
            }, Modifier.weight(1f).testTag("manual-save"))
        }
    }
}
