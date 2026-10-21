package com.kuryelog.app.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.HandlerThread
import android.os.Process
import com.kuryelog.logic.map.surface.SurfaceFeed
import com.kuryelog.logic.map.surface.SurfaceFix
import kotlin.math.sqrt

/**
 * Y13 sensör bağı (YolGo `TelemetryService`'in Android kısmı): ivmeölçer 100 Hz (yerçekimsiz doğrusal ivme yoksa ham ivmeölçer), yönelim vektörü ya da
 * yerçekimi, jiroskop, barometre ve (izin verilmişse) adım dedektörü; GPS 500 ms. Hepsi tek arka plan iş parçacığında [SurfaceFeed]'e akar; hesap `logic/map`'te.
 * Sensörler yalnız gün açık ve etap uygunken kayıtlıdır ([setActive]); süreç önceliği vardiya ön plan servisinden gelir (`:core`). Cihazda denenmedi.
 * Durağan sıfır ofseti hazırken ≥2 sn duruşta ivmeölçer 40 Hz'e, jiroskop 12,5 Hz'e iner ([SurfaceFeed.slowSampling]).
 */
internal class SurfaceSensors(private val app: Context, private val feed: SurfaceFeed) : SensorEventListener, LocationListener {
    private val sensors = app.getSystemService(SensorManager::class.java)
    private val locations = app.getSystemService(LocationManager::class.java)
    private var thread: HandlerThread? = null
    private var handler: android.os.Handler? = null
    private var active = false
    private var slow = false
    private var acceleration: Sensor? = null
    private val rotation = FloatArray(9)

    /** Adım dedektörü kullanılabilir mi: donanım var ve (Android 10+) etkinlik tanıma izni verilmiş. */
    fun stepsAvailable(): Boolean = sensors?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null &&
        (Build.VERSION.SDK_INT < 29 || app.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)

    @SuppressLint("MissingPermission")
    @Synchronized
    fun setActive(on: Boolean) {
        if (on == active) return
        if (!on) { stop(); return }
        if (sensors == null || locations == null || app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val t = HandlerThread("weem-surface", Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
        thread = t
        val h = android.os.Handler(t.looper)
        handler = h
        active = true
        slow = false
        h.post { register(); startLocation(t) }
    }

    private fun register() {
        val h = handler ?: return
        val linearSensor = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensor = linearSensor ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        feed.linear = linearSensor != null
        feed.accelerationAvailable = sensor != null
        acceleration = sensor
        sensor?.let { sensors.registerListener(this, it, ACCEL_FAST_US, 0, h) }
        val rot = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        rot?.let { sensors.registerListener(this, it, 20_000, 0, h) }
        if (rot == null) sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sensors.registerListener(this, it, 20_000, 0, h) }
        sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.registerListener(this, it, GYRO_FAST_US, 0, h) }
        sensors.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { sensors.registerListener(this, it, 200_000, 0, h) }
        if (stepsAvailable()) sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, h) }
    }

    @SuppressLint("MissingPermission")
    private fun startLocation(t: HandlerThread) {
        runCatching { locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, this, t.looper) }
    }

    @Synchronized
    private fun stop() {
        active = false
        handler?.removeCallbacksAndMessages(null)
        runCatching { sensors?.unregisterListener(this) }
        runCatching { locations?.removeUpdates(this) }
        thread?.quitSafely()
        thread = null; handler = null; acceleration = null
        feed.reset()
    }

    /** Duruşta örnekleme hızını düşürür/yükseltir ([SurfaceFeed.slowSampling] değişince). */
    private fun adjustRate() {
        val want = feed.slowSampling
        if (want == slow) return
        slow = want
        val h = handler ?: return
        acceleration?.let { sensors.unregisterListener(this, it); sensors.registerListener(this, it, if (want) ACCEL_SLOW_US else ACCEL_FAST_US, 0, h) }
        sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.unregisterListener(this, it); sensors.registerListener(this, it, if (want) GYRO_SLOW_US else GYRO_FAST_US, 0, h) }
    }

    override fun onLocationChanged(loc: Location) {
        val speedAccuracy = if (Build.VERSION.SDK_INT >= 26 && loc.hasSpeedAccuracy()) loc.speedAccuracyMetersPerSecond.toDouble() else null
        feed.onLocation(SurfaceFix(loc.elapsedRealtimeNanos, loc.time, loc.latitude, loc.longitude, loc.altitude, loc.accuracy.toDouble(), loc.speed.toDouble(), loc.bearing.toDouble(), speedAccuracy))
        adjustRate()
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotation, e.values)
                feed.onRotation(doubleArrayOf(rotation[6].toDouble(), rotation[7].toDouble(), rotation[8].toDouble()), e.timestamp)
            }
            Sensor.TYPE_GRAVITY -> feed.onGravity(doubleArrayOf(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble()), e.timestamp)
            Sensor.TYPE_GYROSCOPE -> feed.onGyro(sqrt((0..2).sumOf { e.values[it].toDouble() * e.values[it].toDouble() }))
            Sensor.TYPE_PRESSURE -> feed.onPressure(e.values[0].toDouble())
            Sensor.TYPE_STEP_DETECTOR -> feed.onStep(e.timestamp)
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_LINEAR_ACCELERATION ->
                feed.onAcceleration(e.timestamp, doubleArrayOf(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble()))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Deprecated("Eski API'ler için gerekli boş gövde")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

    private companion object {
        const val ACCEL_FAST_US = 10_000
        const val ACCEL_SLOW_US = 25_000
        const val GYRO_FAST_US = 20_000
        const val GYRO_SLOW_US = 80_000
    }
}
