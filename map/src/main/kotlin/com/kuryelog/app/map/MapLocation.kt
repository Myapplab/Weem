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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.ui.PuckFix
import com.kuryelog.logic.map.ui.PuckMotion
import com.kuryelog.logic.map.ui.PuckStep

/** Harita ekranının konum işareti: kaydırılmış nokta, ok/koni yönü, doğruluk, son ham konum. */
data class PuckView(val point: GeoPoint, val course: Double?, val beam: Double?, val accuracyM: Double?, val raw: GeoPoint, val turning: Boolean, val cameraHeading: Double?)

/**
 * Harita görünürken GPS (1 sn; ilk konum için ağ) dinler; uygulama arka plandayken (Activity STARTED değil, ör. Wolt/Bolt önde) dinlemez (A10.1) ve işaret akışını (`ui.PuckMotion`, MAP.md 6.4) uygular: [graph] varsa yola oturtma, ileri tahmin, doğrusal kayma
 * (her karede). İzin yoksa null kalır (izni vardiya kartı ister). Pusula yönü (koni) Bölüm 7.6, A6.6.
 */
@Composable
fun rememberMapPuck(enabled: Boolean, graph: RoadGraph?): State<PuckView?> {
    val context = LocalContext.current
    val view = remember { mutableStateOf<PuckView?>(null) }
    val motion = remember(graph) {
        PuckMotion(graph?.let { g -> { lat, lon, d, h, tol -> g.nearestEdge(lat, lon, maxDist = d, heading = h, headingTol = tol) } })
    }
    val step = remember { mutableStateOf<PuckStep?>(null) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val listen = enabled && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    DisposableEffect(listen, motion) {
        val lm = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { loc -> motion.onFix(loc.toPuckFix(), SystemClock.elapsedRealtime(), view.value?.point)?.let { step.value = it } }
        val started = listen && start(context, lm, listener)
        onDispose { if (started) runCatching { lm?.removeUpdates(listener) } }
    }
    val s = step.value
    LaunchedEffect(s) {
        if (s == null) return@LaunchedEffect
        val beam = motion.heading.value ?: s.course
        fun emit(p: GeoPoint) {
            view.value = PuckView(p, s.course, beam, motion.accuracyM, s.to, s.turning, s.cameraHeading)
        }
        if (s.jump || s.slideMs <= 0) { emit(s.to); return@LaunchedEffect }
        val start = withFrameNanos { it }
        while (true) {
            val t = withFrameNanos { it }
            val k = ((t - start) / 1e6 / s.slideMs).coerceIn(0.0, 1.0)
            emit(GeoPoint(s.from.lat + (s.to.lat - s.from.lat) * k, s.from.lon + (s.to.lon - s.from.lon) * k))
            if (k >= 1.0) break
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
