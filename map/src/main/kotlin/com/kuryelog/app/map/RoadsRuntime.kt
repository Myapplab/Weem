package com.kuryelog.app.map

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.AtomicFile
import com.kuryelog.logic.map.nav.MatchInfo
import com.kuryelog.logic.map.nav.NavController
import com.kuryelog.logic.map.nav.WrongWayEvent
import com.kuryelog.logic.map.roads.LearnFixes
import com.kuryelog.logic.map.roads.QualityPrompt
import com.kuryelog.logic.map.roads.RoadBook
import com.kuryelog.logic.map.roads.RoadFiles
import com.kuryelog.logic.map.roads.RoadHaptic
import com.kuryelog.logic.map.roads.VolumeDispatch
import com.kuryelog.logic.map.roads.VolumeEnv
import com.kuryelog.logic.map.roads.RoadsText
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.StreetList
import com.kuryelog.logic.map.route.StreetRatios
import com.kuryelog.logic.map.ui.ToastTone
import com.kuryelog.logic.map.route.StreetPrefsStore
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.surface.MarkStore
import com.kuryelog.logic.map.ui.MapToast
import com.kuryelog.logic.map.ui.Toasts
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** `files/map/roads/<ad>` ve `files/map/manual-marks.json` (MAP.md 9.10): `.tmp` + yeniden adlandırma. */
internal class RoadFilesAndroid(private val mapDir: File) : RoadFiles {
    private fun file(name: String) = if (name.startsWith(MarkStore.FILE)) File(mapDir, name) else File(File(mapDir, "roads"), name)

    override fun read(name: String): String? = file(name).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    override fun write(name: String, text: String) {
        val f = file(name)
        f.parentFile?.mkdirs()
        AtomicFile.write(f, text.toByteArray())
    }
}

/** Uzun basış menüsünün Y12 satırları için basılan yol bilgisi. */
data class PressedRoadInfo(val rateSubtitle: String, val badRatioTitle: String, val wwAllowed: Boolean, val blocked: Boolean)

/**
 * Yol kalitesi öğrenmenin (Y12) Android bağı (MAP.md 9.12 `RoadsRuntime`): [book] (`RoadBook`), kalite kartı [prompt], ses tuşları [onKeyEvent].
 * **S26:** `MapSettings.roadLearning` kapalıyken (varsayılan) öğrenme kapsamı açılmaz, ters yön sorusu çıkmaz, ses tuşları yakalanmaz; elle girilmiş ve
 * önceden öğrenilmiş yol bilgisi rotaya girmeye devam eder ([book] her zaman açık). Kapsam: vardiya açık + anahtar açık → `startDay("yolgo-<başlangıç ms>")`.
 * Bildirim kullanılmaz: geri bildirim uygulama içi şerit ([toast]) ve titreşim.
 */
class RoadsRuntime internal constructor(
    private val app: Context,
    private val nav: NavController,
    private val scope: CoroutineScope,
    private val settings: StateFlow<MapSettings>,
    private val lang: () -> Lang,
    private val streets: StreetPrefsStore,
    private val streetsChanged: () -> Unit,
) {
    private val clock = Clock.Jvm
    private val toasts = Toasts()
    private val toastFlow = MutableStateFlow<MapToast?>(null)

    /** Y12 bildirim şeridi (son gelen). */
    val toast: StateFlow<MapToast?> get() = toastFlow

    val book = RoadBook(RoadFilesAndroid(File(app.filesDir, "map")), streets, toasts, { settings.value }, clock, lang)
    val prompt = QualityPrompt(book, clock)

    private val env = object : VolumeEnv {
        override fun match(): MatchInfo? = nav.match
        override fun here(): GeoPoint? = nav.here()
        override fun suggestion(): WrongWayEvent? = nav.state.value.suggestion
        override fun clearSuggestion() = nav.clearSuggestion()
        // Yeniden hesap `routeRev` üzerinden (aşağıdaki girdiler akışı); burada ikinci kez istenmez.
        override fun replanIfRoute() = Unit
    }
    private val volume = VolumeDispatch(book, clock, prompt, env)
    private val main = Handler(Looper.getMainLooper())
    private val due = Runnable { volume.due() }
    private var downHeld = false
    private var downLongFired = false
    private val hold = Runnable {
        if (downHeld && capture()) {
            downLongFired = true
            volume.onDownHold()
        }
    }

    /** Harita sekmesi ya da alt ekranı önde mi (SORU-Q4 a). */
    @Volatile var mapInFront = false

    init {
        toasts.onChange = { toastFlow.value = it }
        book.haptic = ::vibrate
        RideLogRuntime.get(app).let { log -> book.log = { name, data -> log.writer.event(name, data) } }
        DayDeletionRuntime.get(app).deleteContributions = { book.deleteContributions(it) }
        nav.onMatchedFix = { fix, info ->
            if (book.learning) {
                val st = nav.state.value
                book.onFix(LearnFixes.of(fix, info, streets.current.approved, st.stops.map { it.point }, st.reached?.point))
            }
        }
        // Öğrenme kapsamı: vardiya açık ve anahtar açık (S26).
        scope.launch {
            combine(ShiftHub.state, settings.map { it.roadLearning }.distinctUntilChanged()) { s, on -> if (on && s.active) s.startedAtMs ?: 0L else null }
                .distinctUntilChanged()
                .collect { started -> if (started != null) book.startDay("yolgo-$started") else book.endDay() }
        }
        // Rota girdileri: harita ya da yol bilgisi değişince; elle değişiklik (routeRev) açık rotayı yeniden hesaplatır.
        scope.launch {
            var lastRoute = -1
            var lastApproved = book.state.approvedRev
            val graphs = MapData.controller(app).state.map { it.graph }.distinctUntilChanged()
            val threshold = settings.map { it.wwMaxProblemPct }.distinctUntilChanged()
            combine(graphs, book.states, threshold) { g, s, _ -> g to s }.collect { (g, s) ->
                // Önce sokak kararları (onay/kara liste) denetleyiciye, sonra girdiler: yeniden hesap güncel kararlarla yapılır.
                if (s.approvedRev != lastApproved) {
                    lastApproved = s.approvedRev
                    streetsChanged()
                }
                nav.setRoadInputs(book.forPlan(g), replan = lastRoute >= 0 && s.routeRev != lastRoute)
                lastRoute = s.routeRev
            }
        }
        scope.launch {
            var n = 0
            while (isActive) {
                delay(1_000)
                prompt.due()
                if (++n % 5 == 0) book.tick()
            }
        }
    }

    /** Ses tuşu yakalama koşulu (MAP.md 9.5.3, S26): anahtar + ses tuşu ayarı + Harita önde. */
    fun capture(): Boolean = settings.value.let { it.roadLearning && it.volumeKeys } && mapInFront

    /**
     * `MainActivity.dispatchKeyEvent` (MAP.md 9.5.1): yakalama açıkken ses tuşu olayları (basış, bırakış, tekrar) tüketilir. Ses+ basışta; Ses− kısa
     * bırakışta, 1 sn basılı tutulursa uzun basış.
     */
    fun onKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_VOLUME_UP && e.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (!capture()) {
            downHeld = false
            main.removeCallbacks(hold)
            return false
        }
        if (e.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                volume.onKey(up = true)
                armDue()
            }
            return true
        }
        when (e.action) {
            KeyEvent.ACTION_DOWN -> if (e.repeatCount == 0) {
                downHeld = true
                downLongFired = false
                main.removeCallbacks(hold)
                main.postDelayed(hold, VolumeDispatch.HOLD_MS)
            }
            KeyEvent.ACTION_UP -> {
                main.removeCallbacks(hold)
                if (downHeld && !e.isCanceled && !downLongFired) {
                    if (e.eventTime - e.downTime >= VolumeDispatch.HOLD_MS) {
                        volume.onDownHold()
                    } else {
                        volume.onKey(up = false)
                        armDue()
                    }
                }
                downHeld = false
            }
        }
        return true
    }

    private fun armDue() {
        main.removeCallbacks(due)
        if (volume.pending) main.postDelayed(due, VolumeDispatch.DOUBLE_MS)
    }

    // ---------------------------------------------------------------- Uzun basış (MAP.md 6.7, 9.4.2, 9.7, 9.8.5, 9.8.6)

    /** Basılan yolun Y12 satır verisi: elle puan alt yazısı, kötü kesim oranı başlığı, ww kesimi izni, kapalı mı. */
    fun pressed(g: RoadGraph, edge: Int): PressedRoadInfo {
        val way = g.osmIdOf(edge)
        val l = lang()
        return PressedRoadInfo(
            rateSubtitle = RoadsText.rateSubtitle(l, book.state.k.ways[way]),
            badRatioTitle = StreetRatios.text(StreetRatios.of(g, way, book.state.marks), l),
            wwAllowed = book.wwSegAllowed(g, edge),
            blocked = way in streets.current.blocked,
        )
    }

    /** 👍 (90) / 👎 (25): ad "isimsiz yol" ise yazılmaz; şerit; açık rota yeniden hesaplanır (`routeRev`). */
    fun rateThumb(g: RoadGraph, edge: Int, rating: Int) {
        val l = lang()
        val raw = g.nameOf(edge)
        book.rate(g.osmIdOf(edge), rating, raw)
        val name = raw.ifEmpty { RoadsText.unnamed(l) }
        toasts.show(MapToast(RoadsText.rated(l, name, rating), if (rating >= 50) ToastTone.GREEN else ToastTone.ORANGE, 4))
    }

    /** "Elle yüzey puanı": % kartı. */
    fun openRate(g: RoadGraph, edge: Int) = prompt.open(g.osmIdOf(edge), g.nameOf(edge).ifEmpty { RoadsText.unnamed(lang()) })

    /** "Burada yol yok" / "Bu sokağın tamamı yok" + "Geri al" (7 sn); rota kendiliğinden yeniden hesaplanır (hayalet kümesi değişti). */
    fun markGhost(g: RoadGraph, edge: Int, all: Boolean) {
        val l = lang()
        val mark = book.markGhost(g, edge, all)
        toasts.show(
            MapToast(RoadsText.ghostToast(l, mark.name, mark.all), ToastTone.ORANGE, 7, RoadsText.undo(l)) {
                book.removeGhost(mark)
                toasts.hide()
            },
        )
    }

    /** Ana cadde kesiminde ters yön izni ver / kaldır. */
    fun toggleWwSegment(g: RoadGraph, edge: Int) {
        val allow = !book.wwSegAllowed(g, edge)
        book.setWwSegment(g, edge, allow)
        toasts.show(MapToast(RoadsText.wwSegToast(lang(), g.nameOf(edge), allow), ToastTone.GREEN, 4))
    }

    /** "Bu yolu tamamen kapat" / "Yol kapatmayı kaldır" (Y8 kara liste). */
    fun toggleBlock(g: RoadGraph, edge: Int) {
        val way = g.osmIdOf(edge)
        if (way in streets.current.blocked) streets.remove(StreetList.BLOCKED, way) else streets.block(way, g.nameOf(edge).ifEmpty { RoadsText.unnamed(lang()) })
        book.streetsEdited()
    }

    /** Uygulama arka plana geçti: zorla kayıt. */
    fun onPause() = book.onPause()

    private fun vibrate(h: RoadHaptic) {
        runCatching {
            val ms = when (h) {
                RoadHaptic.SELECTION -> 15L
                RoadHaptic.MEDIUM -> 35L
                RoadHaptic.HEAVY -> 60L
                RoadHaptic.VIBRATE -> 400L
            }
            app.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
