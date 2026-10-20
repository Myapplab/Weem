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
    streets: StreetPrefsStore,
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
        override fun replanIfRoute() = nav.replanIfRoute()
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
        prompt.onSaved = { nav.replanIfRoute() }
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
            combine(MapData.controller(app).state.map { it.graph }.distinctUntilChanged(), book.states) { g, s -> g to s }.collect { (g, s) ->
                nav.setRoadInputs(book.forPlan(g), replan = lastRoute >= 0 && s.routeRev != lastRoute)
                lastRoute = s.routeRev
                if (s.approvedRev != lastApproved) {
                    lastApproved = s.approvedRev
                    streetsChanged()
                }
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
