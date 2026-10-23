package com.kuryelog.app.map

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.ui.MapCamera
import kotlin.math.abs

/**
 * Pusula (MAP.md 7.6; YolGo `MainActivity.kt:340-374`; S38'de bağlandı): döndürme vektörü; telefon dikse (`|R[8]| < 0,7`, gidonda) arka kameranın baktığı yön,
 * yataysa ekranın üst kenarının yönü (ekran döndürmesi hesaba katılır); manyetik sapma eklenir (gerçek kuzey; GPS yönüyle aynı). Gerçek zamanlı: ~33 Hz,
 * ≥ [MIN_CHANGE_DEG] değişimde ve en sık [MIN_INTERVAL_MS]'de bir bildirir. Yalnız harita görünürken açılır (çağıran kapatır; pil).
 */
internal class CompassSensor(private val app: Context, private val onHeading: (Double) -> Unit) : SensorEventListener {
    private val sensors = app.getSystemService(SensorManager::class.java)
    private val rot = FloatArray(9)
    private val screen = FloatArray(9)
    private val upright = FloatArray(9)
    private val orient = FloatArray(3)
    private var declination = 0f
    private var rotation = Surface.ROTATION_0
    private var rotationAtMs = 0L
    private var lastDeg = Double.NaN
    private var lastAtMs = 0L
    private var on = false

    /** Sensör yoksa false (yön GPS'ten). [lat]/[lon] manyetik sapma için son konum (yoksa Bakü). */
    fun start(lat: Double? = null, lon: Double? = null): Boolean {
        if (on) return true
        val sm = sensors ?: return false
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) ?: return false
        declination = runCatching {
            GeomagneticField((lat ?: MapCamera.BAKU.lat).toFloat(), (lon ?: MapCamera.BAKU.lon).toFloat(), 0f, System.currentTimeMillis()).declination
        }.getOrDefault(0f)
        on = runCatching { sm.registerListener(this, sensor, SAMPLING_US) }.getOrDefault(false)
        return on
    }

    fun stop() {
        if (!on) return
        runCatching { sensors?.unregisterListener(this) }
        on = false
        lastDeg = Double.NaN
    }

    private fun displayRotation(now: Long): Int {
        if (now - rotationAtMs > ROTATION_REFRESH_MS) {
            rotationAtMs = now
            rotation = runCatching { app.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation }.getOrNull() ?: Surface.ROTATION_0
        }
        return rotation
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = SystemClock.uptimeMillis()
        if (now - lastAtMs < MIN_INTERVAL_MS) return
        SensorManager.getRotationMatrixFromVector(rot, event.values)
        // Ekran koordinatları: Y ekranın üstü (yatay kilitte cihazın yan kenarı).
        when (displayRotation(now)) {
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, screen)
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, screen)
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, screen)
            else -> System.arraycopy(rot, 0, screen, 0, 9)
        }
        // rot[8]: ekran normalinin dikey bileşeni. Dikse (gidon) arka kameranın yönü, yataysa üst kenarın yönü.
        if (abs(rot[8]) < UPRIGHT) {
            SensorManager.remapCoordinateSystem(screen, SensorManager.AXIS_X, SensorManager.AXIS_Z, upright)
            SensorManager.getOrientation(upright, orient)
        } else {
            SensorManager.getOrientation(screen, orient)
        }
        val deg = Geo.norm360(Math.toDegrees(orient[0].toDouble()) + declination)
        if (!lastDeg.isNaN() && Geo.angleDiff(deg, lastDeg) < MIN_CHANGE_DEG) return
        lastDeg = deg
        lastAtMs = now
        onHeading(deg)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        /** ~33 Hz (SENSOR_DELAY_UI ~16 Hz gerçek zamanlı hissettirmiyordu; GAME 50 Hz gereksiz). */
        const val SAMPLING_US = 30_000
        const val MIN_INTERVAL_MS = 25L
        const val MIN_CHANGE_DEG = 0.5
        const val UPRIGHT = 0.7f
        const val ROTATION_REFRESH_MS = 1_000L
    }
}
