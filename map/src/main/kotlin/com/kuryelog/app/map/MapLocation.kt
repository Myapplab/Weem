package com.kuryelog.app.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.ui.PuckAnimator
import com.kuryelog.logic.map.ui.PuckFix
import com.kuryelog.logic.map.ui.PuckMotion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/**
 * Harita ekranının konum işareti: çizim yeri, yön ([heading]: ok, koni ve yön-yukarı kamera; S38 gerçek zamanlı), [moving] (ok gösterilebilir), doğruluk,
 * son ham konum.
 */
data class PuckView(val point: GeoPoint, val heading: Double?, val moving: Boolean, val accuracyM: Double?, val raw: GeoPoint, val turning: Boolean)

/**
 * Harita görünürken GPS (1 sn; ilk konum için ağ) ve pusula ([CompassSensor], ~30 Hz) dinler; uygulama arka plandayken (Activity STARTED değil, ör. Wolt/Bolt önde)
 * ikisi de kapalı (A10.1) ve işaret akışını (`ui.PuckMotion`, MAP.md 6.4) uygular: [graph] varsa yola oturtma, ileri tahmin, doğrusal kayma. S38: kayma ve dönüş
 * tek döngüde, en çok ~30 fps ve yalnız değişiklik varken (`ui.PuckAnimator`); duran işaret kare istemez (pil). İzin yoksa null kalır (izni vardiya kartı ister).
 */
@Composable
fun rememberMapPuck(enabled: Boolean, graph: RoadGraph?): State<PuckView?> {
    val context = LocalContext.current
    val view = remember { mutableStateOf<PuckView?>(null) }
    val motion = remember(graph) {
        PuckMotion(graph?.let { g -> { lat, lon, d, h, tol -> g.nearestEdge(lat, lon, maxDist = d, heading = h, headingTol = tol) } })
    }
    val animator = remember { PuckAnimator() }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val listen = enabled && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    DisposableEffect(listen, motion) {
        val lm = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { loc ->
            val step = motion.onFix(loc.toPuckFix(), SystemClock.elapsedRealtime(), animator.displayed) ?: return@LocationListener
            animator.onStep(step, motion.accuracyM, motion.heading.value, GeoPoint(loc.latitude, loc.longitude), SystemClock.uptimeMillis())
            wake.trySend(Unit)
        }
        val compass = CompassSensor(context.applicationContext) { deg -> animator.onCompass(deg); wake.trySend(Unit) }
        val started = listen && start(context, lm, listener)
        val here = view.value?.raw
        val compassOn = listen && compass.start(here?.lat, here?.lon)
        onDispose {
            if (started) runCatching { lm?.removeUpdates(listener) }
            if (compassOn) { compass.stop(); animator.compassOff(); wake.trySend(Unit) }
        }
    }
    LaunchedEffect(animator) {
        while (true) {
            wake.receive()
            while (true) {
                val now = SystemClock.uptimeMillis()
                animator.frame(now)?.let { f -> view.value = PuckView(f.point, f.heading, f.moving, f.accuracyM, f.raw, f.turning) }
                if (!animator.busy(now)) break
                delay(PuckAnimator.FRAME_MS)
            }
        }
    }
    return view
}

@SuppressLint("MissingPermission")
private fun start(context: Context, lm: LocationManager?, listener: LocationListener): Boolean {
    if (lm == null || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
    return runCatching {
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let(listener::onLocationChanged)
        }
        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(listener::onLocationChanged)
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
        true
    }.getOrDefault(false)
}

private fun Location.toPuckFix() = PuckFix(
    lat = latitude, lon = longitude, accuracyM = if (hasAccuracy()) accuracy.toDouble() else Double.POSITIVE_INFINITY,
    speedMps = if (hasSpeed()) speed.toDouble() else -1.0, headingDeg = if (hasBearing()) bearing.toDouble() else -1.0,
    headingAccuracyDeg = if (hasBearingAccuracy()) bearingAccuracyDegrees.toDouble() else 0.0,
)
