package com.kuryelog.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Alttan açılan sayfa (`docs/TASARIM.md` 11; K4: çok seçenekli ve uzun pencereler): tutamak 32×4, üst köşeler 32, yanlar 24, başlık
 * `titleL`, eylemler en altta; örtüye ya da geri tuşuna basınca [onDismiss]. Aşağıdan kayarak gelir (animasyon kapalıysa hemen).
 */
@Composable
fun WSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = palette()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            // Örtü ayrı katman: tıklanabilir üst düğüm içeriği anlamsal olarak birleştirmesin.
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
            val shown = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(
                shown,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(WeemMotion.SHEET_IN, easing = WeemMotion.enter)) { it } + fadeIn(tween(WeemMotion.STATE)),
            ) {
                // Zemin kırpılmadan çizilir (yalnız üst köşeler yuvarlak): kırpılan bu şekil dokunuş denetiminde `Path.op` ister,
                // Robolectric onu desteklemez. Boş `pointerInput` dokunuşun alttaki örtüye geçmesini önler (Surface gibi).
                CompositionLocalProvider(LocalContentColor provides p.text.color()) {
                    Column(
                        modifier.fillMaxWidth()
                            .background(p.surface.color(), RoundedCornerShape(topStart = WeemRadius.sheet, topEnd = WeemRadius.sheet))
                            .pointerInput(Unit) {}
                            .navigationBarsPadding().imePadding()
                            .padding(start = if (padded) WeemSpace.xxl else 0.dp, end = if (padded) WeemSpace.xxl else 0.dp, top = WeemSpace.m, bottom = WeemSpace.xxl),
                        verticalArrangement = Arrangement.spacedBy(WeemSpace.l),
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(width = 32.dp, height = 4.dp).background(p.high.color(), CircleShape))
                        }
                        if (title != null) Text(
                            title, style = WeemType.titleL,
                            modifier = Modifier.padding(horizontal = if (padded) 0.dp else WeemSpace.xxl).semantics { heading() },
                        )
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Kısa onay penceresi: köşe 24, başlık `titleL`, metin `bodyM`, iki düğme; [danger] iken onay düğmesi tehlike dolgusu.
 */
@Composable
fun ConfirmDialog(
    title: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmTag: String? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val p = palette()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(WeemRadius.panel),
        containerColor = p.surface.color(),
        titleContentColor = p.text.color(),
        textContentColor = p.text.color(),
        title = { Text(title, style = WeemType.titleL) },
        text = if (text == null && body == null) null else ({
            Column(verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
                if (text != null) Text(text, style = WeemType.bodyM)
                body?.invoke(this)
            }
        }),
        confirmButton = {
            WButton(
                confirm, onConfirm, if (confirmTag != null) Modifier.testTag(confirmTag) else Modifier,
                kind = if (danger) BtnKind.DANGER_FILLED else BtnKind.PRIMARY, enabled = confirmEnabled,
            )
        },
        dismissButton = { WButton(dismiss, onDismiss, kind = BtnKind.TEXT) },
    )
}

/** Tek düğmeli bilgi penceresi (ileti, sonuç). */
@Composable
fun NoticeDialog(text: String, ok: String, onDismiss: () -> Unit, title: String? = null) {
    val p = palette()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(WeemRadius.panel),
        containerColor = p.surface.color(),
        titleContentColor = p.text.color(),
        textContentColor = p.text.color(),
        title = title?.let { { Text(it, style = WeemType.titleL) } },
        text = { Text(text, style = WeemType.bodyM) },
        confirmButton = { WButton(ok, onDismiss, kind = BtnKind.TEXT) },
    )
}
