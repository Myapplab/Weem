package com.kuryelog.app.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import com.kuryelog.app.core.OwnOverlays
import com.kuryelog.app.core.Store
import com.kuryelog.logic.remote.GestureStroke
import com.kuryelog.logic.remote.GlobalAction
import com.kuryelog.logic.remote.GesturePart
import com.kuryelog.logic.remote.HostView
import com.kuryelog.logic.remote.RemoteLines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Uzaktan dokunuş servisi (REMOTE.md 2.3–2.4): okuma servisinden (`com.kuryelog.app.WatchService`) **ayrı** erişilebilirlik servisi;
 * yalnız dokunuş yetkisi var (`canPerformGestures`), ekran içeriği okumaz, olay dinlemez. Yalnız [RemoteHub]'ın verdiği, eşleşmiş
 * telefondaki kullanıcının dokunuşundan gelen parçaları oynatır (paylaşım açık ve izleyen doğrulanmışken); kendiliğinden hiçbir şeye
 * basmaz. Paylaşım açıkken ekranda oturum işareti: dokununca paylaşım biter. Cihazda doğrulanmadı.
 */
class TouchService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private val queue = ArrayDeque<GesturePart>()
    private var busy = false
    /** Basılı parmakların son çizgisi (`continueStroke` için). */
    private val held = HashMap<Int, GestureDescription.StrokeDescription>()
    private var marker: TextView? = null
    private var markerText: String? = null

    private val markerRect: () -> Rect? = {
        marker?.takeIf { it.isAttachedToWindow && it.width > 0 }?.let { v ->
            val at = IntArray(2)
            v.getLocationOnScreen(at)
            Rect(at[0], at[1], at[0] + v.width, at[1] + v.height)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = this
        OwnOverlays.remove(markerRect)
        OwnOverlays.add(markerRect)
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        s.launch { RemoteHub.hostView.collect(::showMarker) }
        RemoteHub.touchAvailable(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (current !== this) return
        current = null
        RemoteHub.touchAvailable(false)
        scope?.cancel()
        scope = null
        main.post {
            queue.clear()
            held.clear()
            removeMarker()
        }
        OwnOverlays.remove(markerRect)
    }

    // --- dokunuş ---

    /** [RemoteHub]'tan: parçayı sıraya koy; biri bitince öteki oynatılır (sürdürülen çizgi öncekinin bitmesini bekler). */
    internal fun play(part: GesturePart) = main.post {
        queue.addLast(part)
        next()
    }

    internal fun global(action: GlobalAction) = main.post {
        val a = when (action) {
            GlobalAction.BACK -> GLOBAL_ACTION_BACK
            GlobalAction.HOME -> GLOBAL_ACTION_HOME
            GlobalAction.RECENTS -> GLOBAL_ACTION_RECENTS
            GlobalAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
        }
        runCatching { performGlobalAction(a) }
    }

    private fun next() {
        if (busy) return
        while (true) {
            val part = queue.removeFirstOrNull() ?: return
            val g = build(part) ?: continue
            val ok = runCatching { dispatchGesture(g, callback, main) }.getOrElse { e ->
                Store.sink(this).failure("remote_gesture", e)
                false
            }
            if (ok) {
                busy = true
                return
            }
            held.clear()
        }
    }

    private val callback = object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
            busy = false
            next()
        }

        override fun onCancelled(gestureDescription: GestureDescription?) {
            // Sistem kesti (ör. kullanıcı bu telefona dokundu): basılı parmak kalmaz, hareketin geri kalanı sürdürülemez.
            busy = false
            held.clear()
            next()
        }
    }

    private fun build(part: GesturePart): GestureDescription? {
        val b = GestureDescription.Builder()
        var n = 0
        for (s in part.strokes) {
            val sd = runCatching { stroke(s) }.getOrNull() ?: continue
            b.addStroke(sd)
            n++
            if (s.willContinue) held[s.id] = sd else held.remove(s.id)
        }
        return if (n == 0) null else runCatching { b.build() }.getOrNull()
    }

    private fun stroke(s: GestureStroke): GestureDescription.StrokeDescription? {
        val path = Path()
        path.moveTo(s.points[0].x.toFloat(), s.points[0].y.toFloat())
        for (i in 1 until s.points.size) path.lineTo(s.points[i].x.toFloat(), s.points[i].y.toFloat())
        val start = s.startMs.toLong()
        val dur = s.durationMs.toLong()
        if (!s.continues) return GestureDescription.StrokeDescription(path, start, dur, s.willContinue)
        // Öncesi oynatılamadıysa (iptal) sürdürülecek çizgi yok: parça atlanır.
        val prev = held[s.id] ?: return null
        return prev.continueStroke(path, start, dur, s.willContinue)
    }

    // --- oturum işareti ---

    private fun showMarker(v: HostView) {
        val text = RemoteLines.marker(v, Store.lang(this))
        if (text == null) {
            removeMarker()
            return
        }
        val m = marker ?: addMarker() ?: return
        if (text != markerText) {
            m.text = text
            markerText = text
        }
    }

    private fun addMarker(): TextView? {
        val wm = getSystemService(WindowManager::class.java)
        val d = resources.displayMetrics.density
        val tv = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            setTextColor(MARKER_TEXT)
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply { cornerRadius = 14 * d; setColor(MARKER_BG) }
            elevation = 4 * d
            // Tek dokunuşla bitirme: paylaşım (ve izleme, dokunuş) kapanır.
            setOnClickListener { RemoteHub.stopShare() }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = (MARKER_TOP_DP * d).toInt()
        return runCatching { wm.addView(tv, lp); tv }
            .onSuccess { marker = it; markerText = null }
            .onFailure { Store.sink(this).failure("remote_marker", it) }
            .getOrNull()
    }

    private fun removeMarker() {
        val m = marker ?: return
        runCatching { getSystemService(WindowManager::class.java).removeView(m) }
        marker = null
        markerText = null
    }

    companion object {
        /** Durum çubuğunun altı (çoğu telefonda 24–40 dp). */
        private const val MARKER_TOP_DP = 40
        private const val MARKER_BG = 0xE6C62828.toInt()
        private const val MARKER_TEXT = 0xFFFFFFFF.toInt()

        @Volatile var current: TouchService? = null
            private set
    }
}
