package com.kuryelog.app.scooter

import android.content.Context
import android.content.SharedPreferences
import com.kuryelog.logic.scooter.NinebotMode

/**
 * Sürüş Paneli tercihleri `logger` (SCOOTER.md 11.1; NB `LoggerPreferences.java`): 0.2.9 öncesi `MainActivity` dosyasındaki
 * seçimler bir kez taşınır (`legacy_main_migrated`). Ad ve anahtarlar değişmez (VERI_SOZLESMESI 5.2).
 */
object LoggerPreferences {
    const val NAME = "logger"

    @Synchronized
    fun get(context: Context): SharedPreferences {
        val current = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        if (current.getBoolean("legacy_main_migrated", false)) return current
        val legacy = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
        val edit = current.edit()
        // Device identity is one group: never combine a newer selection with an old BLE name.
        if (!current.contains("device")) for (key in arrayOf("device", "device_name", "ble_name")) {
            if (!current.contains(key) && legacy.contains(key)) edit.putString(key, legacy.getString(key, ""))
        }
        if (!current.contains("sweep") && legacy.contains("sweep")) edit.putBoolean("sweep", legacy.getBoolean("sweep", false))
        if (!current.contains("crypto_mode") && legacy.contains("crypto_mode")) {
            edit.putString("crypto_mode", legacy.getString("crypto_mode", NinebotMode.GEN2_FW.setting))
        }
        edit.putBoolean("legacy_main_migrated", true).apply()
        return current
    }
}
