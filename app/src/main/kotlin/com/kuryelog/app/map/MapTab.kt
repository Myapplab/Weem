package com.kuryelog.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.nav.NavState
import com.kuryelog.logic.map.ui.CameraMode

/** Harita sekmesinin görünümü (MAP.md 6.1–6.12): gün, navigasyon, sol göstergeler, hava; [arrivalFocus] varış odağı (hedefe ≤100 m). */
data class MapTabState(
    val day: DayView,
    val nav: NavState = NavState(),
    val metrics: MapMetricsState = MapMetricsState(),
    val weather: WeatherView = WeatherView(),
    val weatherStrip: Boolean = true,
    val arrivalFocus: Boolean = false,
    val cameraMode: CameraMode = CameraMode.FOLLOW_HEADING,
    val nowMs: Long = 0,
    /** Yalnız harita kipi (haritaya tek dokunuş açar/kapatır). */
    val mapOnly: Boolean = false,
    /** Klavye açık: sol göstergeler ve alt panel yok. */
    val keyboard: Boolean = false,
)

/** Harita sekmesinin eylemleri; ekranı kuran (`MapScreen`) bağlar. Dolmayan yuvalar (A6.6–A6.9) boş kalır. */
class MapTabActions(
    val onLocate: () -> Unit = {},
    val onRouteFab: () -> Unit = {},
    val onSearch: () -> Unit = {},
    val onShowPanels: () -> Unit = {},
    val onWeatherRefresh: () -> Unit = {},
)

/**
 * Harita sekmesi (YolGo `nav_screen.dart:57-153`; MAP.md 6.3): harita yüzeyi + HUD (arama çubuğu `map-search`, hava şeridi, sol göstergeler, gün eylemleri, konum düğmesi,
 * alt yer düğmesi); sekmede menü, ayar düğmesi ve kayıt noktası yok. Varış odağında yalnız harita, bildirim şeridi (`toast-banner`), hatırlatıcı yuvası (`reminder-slot`), görev
 * uyarıları (`task-alerts`), ters yön kartı ve alttaki `arrival-address` kartı. Yalnız harita kipinde sağ üstte "Panelleri göster" (`show-panels`) ve konum düğmesi.
 * Dışarı dokunuş açık gün kartını katlar. Yuvalar ([toast], [reminder], [taskAlerts], [wrongWay], [routePanel]) sahip görevlerde dolar; [metrics] sol göstergeler (A6.7),
 * [topCard] arama çubuğunun üstünde (vardiya kapalıyken `ShiftCard`).
 */
@Composable
fun MapTab(
    state: MapTabState,
    lang: Lang,
    modifier: Modifier = Modifier,
    mapSurface: @Composable () -> Unit = {},
    onHome: () -> Unit = {},
    onStats: () -> Unit = {},
    onEnd: () -> Unit = {},
    actions: MapTabActions = MapTabActions(),
    toast: @Composable () -> Unit = {},
    reminder: @Composable () -> Unit = {},
    taskAlerts: @Composable () -> Unit = {},
    wrongWay: @Composable () -> Unit = {},
    routePanel: @Composable () -> Unit = {},
    metrics: @Composable () -> Unit = { MapMetrics(state.metrics, lang) },
    topCard: @Composable () -> Unit = {},
) {
    val alerts = @Composable { Box(Modifier.fillMaxSize().testTag("task-alerts")) { taskAlerts() } }
    val toastSlot = @Composable { Box(Modifier.fillMaxWidth().testTag("toast-banner")) { toast() } }
    val reminderSlot = @Composable { Box(Modifier.fillMaxWidth().testTag("reminder-slot")) { reminder() } }
    val keyboard = state.keyboard
    if (state.arrivalFocus && !keyboard) {
        MapHudLayout(
            keyboard = false, map = mapSurface, modifier = modifier, alerts = alerts,
            header = { Column(Modifier.fillMaxWidth()) { toastSlot(); reminderSlot() } },
            footer = {
                Column(Modifier.fillMaxWidth()) {
                    wrongWay()
                    state.nav.stops.firstOrNull()?.let { ArrivalAddressCard(it.label, it.unitNote) }
                }
            },
        )
        return
    }
    if (state.mapOnly) {
        MapHudLayout(
            keyboard = keyboard, map = mapSurface, modifier = modifier, alerts = alerts,
            header = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                    FilledTonalButton(onClick = actions.onShowPanels, modifier = Modifier.testTag("show-panels")) { Text(tl(lang, "Panelleri göster", "Panelləri göstər")) }
                }
            },
            footer = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) { SmartLocateButton(state.cameraMode, actions.onLocate, lang = lang) }
            },
        )
        return
    }
    var collapseSignal by remember { mutableIntStateOf(0) }
    var shiftCollapsed by remember { mutableStateOf(true) }
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    val hasRoute = state.nav.hasRoute || state.nav.busy || state.nav.pending != null
    val bar = @Composable {
        MapShiftActions(
            state.day, hasRoute, lang, onHome, onStats, onEnd,
            modifier = Modifier.onGloballyPositioned { cardBounds = it.boundsInRoot() },
            collapseSignal = collapseSignal, onCollapsedChange = { shiftCollapsed = it },
        )
    }
    val locate = @Composable { SmartLocateButton(state.cameraMode, actions.onLocate, lang = lang) }
    Box(
        modifier.fillMaxSize().onGloballyPositioned { rootCoords = it }.pointerInput(Unit) {
            // Kartın dışına dokunuş (harita, başka düğme) açık gün kartını katlar; olay tüketilmez.
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.type != PointerEventType.Press) continue
                    val origin = rootCoords?.positionInRoot() ?: continue
                    val p = e.changes.first().position + origin
                    if (!shiftCollapsed && !cardBounds.contains(p)) collapseSignal++
                }
            }
        },
    ) {
        MapHudLayout(
            keyboard = keyboard, map = mapSurface, alerts = alerts,
            header = {
                Column(Modifier.fillMaxWidth()) {
                    topCard()
                    SearchBar(lang, actions.onSearch)
                    Spacer(Modifier.height(16.dp))
                    if (state.weatherStrip) WeatherChip(state.weather, lang, state.nowMs, onRefresh = actions.onWeatherRefresh)
                    Spacer(Modifier.height(6.dp))
                    if (!keyboard) metrics()
                    Spacer(Modifier.height(6.dp))
                    toastSlot()
                    reminderSlot()
                }
            },
            footer = {
                // 1.7.1: gün kartı katlıyken konum ve rota düğmeleri kartın yanına iner; açılınca yerlerine döner.
                if (shiftCollapsed) {
                    Column(Modifier.fillMaxWidth()) {
                        wrongWay()
                        routePanel()
                        Row(verticalAlignment = Alignment.Bottom) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.BottomStart) { bar() }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                locate()
                                RouteFab(actions.onRouteFab, lang = lang)
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        locate()
                        Spacer(Modifier.height(10.dp))
                        wrongWay()
                        routePanel()
                        RouteFab(actions.onRouteFab, lang = lang)
                        Spacer(Modifier.height(8.dp))
                        bar()
                    }
                }
            },
        )
    }
}

/** Arama çubuğu (`map-search`; MAP.md 8.8): ipucu "Adres veya mekân ara"; arama akışı A6.6. */
@Composable
private fun SearchBar(lang: Lang, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .95f),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("map-search"),
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text("⌕  " + tl(lang, "Adres veya mekân ara", "Ünvan və ya obyekt axtar"), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Varış odağında alttaki adres kartı (`arrival-address`; MAP.md 6.3, 7.10): adres ve kapı notu. */
@Composable
private fun ArrivalAddressCard(label: String, unitNote: String?) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .97f),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().testTag("arrival-address"),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (!unitNote.isNullOrBlank()) Text(unitNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
