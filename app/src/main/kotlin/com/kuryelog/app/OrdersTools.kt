package com.kuryelog.app

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kuryelog.logic.orders.Block
import com.kuryelog.logic.orders.CancellationRow
import com.kuryelog.logic.orders.Card
import com.kuryelog.logic.orders.OrdersSessions
import com.kuryelog.logic.orders.OrdersTools
import com.kuryelog.logic.orders.cards

/**
 * Siparişler → Çevrimiçi oturumlar sayfası (A10.4a, S20): bugünün Wolt/Bolt çevrimiçi dönemleri ve her döneme bağlı siparişler. Metin ve sıra
 * `logic/orders` [OrdersSessions]'tan; burada yalnız çizim.
 */
@Composable
fun SessionsPageView(sessions: OrdersSessions, back: String, onBack: () -> Unit) {
    val page = remember(sessions) { sessions.page() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        TextButton(onClick = onBack) { Text(back) }
        Text(page.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 4.dp).semantics { heading() })
        page.cards().forEach { CardView(it) }
    }
}

/** Siparişler → Araçlar (yalnız vardiya açıkken): bölünmüş ekran ve platform başına iptal gözlemi (onaylı; platformda işlem yapmaz). */
@Composable
fun ToolsCard(tools: OrdersTools) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf<CancellationRow?>(null) }
    CardView(Card(listOf(Block.Sub(tools.title))), footer = {
        OutlinedButton(onClick = { SplitScreen.open(ctx, tools) }, Modifier.fillMaxWidth()) { Text(tools.splitLabel) }
        for (row in tools.cancellations()) OutlinedButton(onClick = { confirm = row }, Modifier.fillMaxWidth()) { Text(row.label) }
    })
    confirm?.let { row ->
        AlertDialog(
            onDismissRequest = { confirm = null }, text = { Text(tools.cancelMessage) },
            confirmButton = { TextButton(onClick = { confirm = null; Toast.makeText(ctx, tools.markCancellation(row.pkg), Toast.LENGTH_SHORT).show() }) { Text(tools.cancelSave) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tools.cancelDismiss) } },
        )
    }
}
