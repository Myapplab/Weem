package com.kuryelog.app.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.DatasetText
import com.kuryelog.logic.orders.dataset.DatasetManual

/** Seçim sayfası + form (MAP.md 13.9): 8 ekran türü dosya seçiciyi ([onPick]) açar; haftalık ödeme ve vardiya notu formu [onSave] ile kaydedilir (hata dönerse satır içi gösterilir). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DatasetManualSheet(lang: Lang, onPick: (kind: String) -> Unit, onSave: (kind: String, input: Map<String, String?>) -> DatasetManual.Error?, onDismiss: () -> Unit) {
    var form by remember { mutableStateOf<String?>(null) }
    val kinds = DatasetManual.SCREENSHOT_KINDS + listOf(DatasetManual.PAYOUT, DatasetManual.NOTE)
    if (form == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(DatasetText.MANUAL_TITLE.get(lang)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(DatasetText.MANUAL_SUB.get(lang), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                    for (k in kinds) {
                        Text(
                            DatasetText.manualLabel(k, lang),
                            Modifier.fillMaxWidth().clickable { if (k == DatasetManual.PAYOUT || k == DatasetManual.NOTE) form = k else { onPick(k); onDismiss() } }.padding(vertical = 12.dp).testTag("manual-kind-$k"),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(DatasetText.CANCEL.get(lang)) } },
        )
        return
    }
    val kind = form!!
    val values = remember { mutableStateMapOf<String, String>() }
    var error by remember { mutableStateOf<DatasetManual.Error?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(DatasetText.manualLabel(kind, lang)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fun field(key: String, label: String) = @Composable {
                    OutlinedTextField(values[key].orEmpty(), { values[key] = it; error = null }, Modifier.fillMaxWidth().testTag("manual-$key"), label = { Text(label) }, singleLine = true)
                }
                @Composable
                fun choices(key: String, label: String, options: List<Pair<String, String>>) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = values[key].isNullOrEmpty(), onClick = { values[key] = "" }, label = { Text(DatasetText.UNKNOWN.get(lang)) })
                        for ((v, t) in options) FilterChip(selected = values[key] == v, onClick = { values[key] = v }, label = { Text(t) }, modifier = Modifier.testTag("manual-$key-$v"))
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
                    Text(
                        when (e) {
                            DatasetManual.Error.NUMBERS -> DatasetText.ERR_NUMBERS
                            DatasetManual.Error.DATE -> DatasetText.ERR_DATE
                            DatasetManual.Error.PAYOUT_REQUIRED -> DatasetText.ERR_PAYOUT
                            DatasetManual.Error.KIND -> DatasetText.ERR_NUMBERS
                        }.get(lang),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = onSave(kind, values.toMap())
                if (e == null) onDismiss() else error = e
            }, Modifier.testTag("manual-save")) { Text(DatasetText.SAVE.get(lang)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(DatasetText.CANCEL.get(lang)) } },
    )
}
