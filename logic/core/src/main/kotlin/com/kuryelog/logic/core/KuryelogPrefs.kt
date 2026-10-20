package com.kuryelog.logic.core

/**
 * `shared_prefs/kuryelog.xml` (CORE.md 1.1, 2.1, 4.1, 5.4; tam liste `VERI_SOZLESMESI.md` 5.1). Ad ve tipler Rawstats ile aynı:
 * Rawstats'ın üstüne kurulan WEEM aynı dosyayı okur. Burada `:core`'un anahtarları; diğer modüller kendi anahtarlarını kendi aşamasında
 * ekler.
 */
object KuryelogPrefs {
    const val FILE = "kuryelog"

    // Vardiya (CORE.md 1.1, 1.5). Vardiya açık ⇔ SHIFT ve MANUAL_SESSION.
    const val SHIFT = "shift"
    const val MANUAL_SESSION = "manualSession"
    const val AUTO_STARTED = "autoStarted"
    const val AUTO_SHIFT = "autoShift"
    /** Int göç işareti: < 1 ise eski otomatik oturumlar bir kez kapatılır ([ensureManualMode]). */
    const val MANUAL_POLICY = "manualPolicy"
    const val SHIFT_START = "shiftStart"
    const val SHIFT_START_REASON = "shiftStartReason"
    const val SHIFT_ENDED_AT = "shiftEndedAt"
    const val SHIFT_END_REASON = "shiftEndReason"
    const val SHIFT_INTERRUPTED_AT = "shiftInterruptedAt"
    /** Float metre, 15 sn'de bir. */
    const val DIST = "dist"

    // Ev (CORE.md 2.1, 1.6). lat/lon String.
    const val HOME_LAT = "home.lat"
    const val HOME_LON = "home.lon"
    const val HOME_UPDATED_AT = "home.updatedAt"
    const val HOME_START_BLOCKED = "home.startBlocked"
    const val HOME_END_ARMED = "home.endArmed"

    /** Long: gizlenen son çökmenin zamanı (CORE.md 4.1). */
    const val CRASH_SEEN_AT = CrashFile.SEEN_KEY
    /** String: WEEM'in yeni cihaz kimliği (CORE.md 1.10). */
    const val DEVICE_ID = "weem.deviceId"
    /** Bool (false): ilk açılış kurulumu bitti ya da atlandı (G5, A7.2; `logic/app` `SetupStep.PREF_KEY`). Yedeğe girmez. */
    const val SETUP_DONE = "weem.setupDone"
    /** Bool (true): vardiya bitince günün anonim analiz ZIP'i (R17, [DailyPack]; RS `DailyPack.PREF`). */
    const val DAILY_PACK = "dailyPack"

    /** `kuryelog` tercihleri; Android'de `SharedPreferences` bağdaştırıcısı, testlerde bellekte. */
    interface Values {
        fun getBoolean(key: String, default: Boolean): Boolean
        fun getInt(key: String, default: Int): Int
        fun getLong(key: String, default: Long): Long
        fun getFloat(key: String, default: Float): Float
        fun getString(key: String, default: String?): String?
        /** Hepsi tek seferde yazılır (`SharedPreferences.Editor.apply`); null değer anahtarı siler. */
        fun put(values: Map<String, Any?>)
    }

    /** Bellekte (testler ve saf araçlar). */
    class Memory(val map: MutableMap<String, Any> = mutableMapOf()) : Values {
        override fun getBoolean(key: String, default: Boolean) = map[key] as? Boolean ?: default
        override fun getInt(key: String, default: Int) = map[key] as? Int ?: default
        override fun getLong(key: String, default: Long) = map[key] as? Long ?: default
        override fun getFloat(key: String, default: Float) = map[key] as? Float ?: default
        override fun getString(key: String, default: String?) = map[key] as? String ?: default
        @Synchronized override fun put(values: Map<String, Any?>) {
            for ((k, v) in values) if (v == null) map.remove(k) else map[k] = v
        }
    }

    /**
     * RS `Store.kt:19-25`: elle kayda geçişten önceki otomatik oturumlar güncellemeden sonra sürmez. `manualPolicy < 1` ise bir
     * kez `manualPolicy = 1` ve vardiya bayrakları false; Rawstats'ın üstüne kurulunca işaret zaten 1'dir. Değişiklik yaptıysa true.
     */
    fun ensureManualMode(p: Values): Boolean {
        if (p.getInt(MANUAL_POLICY, 0) >= 1) return false
        p.put(mapOf(MANUAL_POLICY to 1, SHIFT to false, MANUAL_SESSION to false, AUTO_SHIFT to false, AUTO_STARTED to false))
        return true
    }

    fun shiftOn(p: Values): Boolean = p.getBoolean(SHIFT, false) && p.getBoolean(MANUAL_SESSION, false)
}
