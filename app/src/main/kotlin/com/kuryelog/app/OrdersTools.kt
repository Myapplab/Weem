package com.kuryelog.app

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.SubScreen
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.orders.CancellationRow
import com.kuryelog.logic.orders.OrdersSessions
import com.kuryelog.logic.orders.OrdersTools
import com.kuryelog.logic.orders.cards

/**
 * Siparişler → Çevrimiçi oturumlar sayfası (A10.4a, S20): bugünün Wolt/Bolt çevrimiçi dönemleri ve her döneme bağlı siparişler. Metin ve sıra
 * `logic/orders` [OrdersSessions]'tan; burada yalnız çizim. A10.4d: üst çubuklu alt ekran.
 */
@Composable
fun SessionsPageView(sessions: OrdersSessions, back: String, onBack: () -> Unit) {
    val page = remember(sessions) { sessions.page() }
    SubScreen(page.title, back, onBack) {
        page.cards().forEach { CardView(it) }
    }
}

/**
 * Siparişler → Araçlar (yalnız vardiya açıkken): bölünmüş ekran (tonal) ve platform başına iptal gözlemi (onaylı; platformda işlem
 * yapmaz).
 */
@Composable
fun ToolsCard(tools: OrdersTools) {
    val ctx = LocalContext.current
    val p = palette()
    var confirm by remember { mutableStateOf<CancellationRow?>(null) }
    WCard(spacing = 10.dp) {
        Row(Modifier.padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(WeemIcons.Tune, contentDescription = null, tint = p.accentText.color())
            Text(tools.title, style = WeemType.titleM, modifier = Modifier.semantics { heading() })
        }
        WButton(tools.splitLabel, { SplitScreen.open(ctx, tools) }, kind = BtnKind.TONAL, icon = WeemIcons.Splitscreen, fill = true)
        for (row in tools.cancellations()) WButton(row.label, { confirm = row }, kind = BtnKind.OUTLINE, fill = true)
    }
    confirm?.let { row ->
        ConfirmDialog(
            title = row.label, text = tools.cancelMessage, confirm = tools.cancelSave, dismiss = tools.cancelDismiss,
            onConfirm = { confirm = null; Toast.makeText(ctx, tools.markCancellation(row.pkg), Toast.LENGTH_SHORT).show() },
            onDismiss = { confirm = null },
        )
    }
}
