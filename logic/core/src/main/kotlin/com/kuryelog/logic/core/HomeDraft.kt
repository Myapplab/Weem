package com.kuryelog.logic.core

/** Doğrulanmış, bellekteki ev seçimi (CORE.md 2.2). Oluşturmak ya da atmak tercihe yazmaz. */
class HomeDraft private constructor(val lat: Double, val lon: Double, val accuracyM: Double?) {
    val needsWarning get() = accuracyM != null && accuracyM > HomeFixes.WARN_ACCURACY_M

    companion object {
        fun create(lat: Double?, lon: Double?, accuracyM: Double? = null): HomeDraft? {
            if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() ||
                lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            if (accuracyM != null && (!accuracyM.isFinite() || accuracyM < 0)) return null
            return HomeDraft(lat, lon, accuracyM)
        }
    }
}

/** Haritadan seçme dönüşü (CORE.md 2.2): yalnız `Double` lat/lon, sonlu ve aralıkta; sonuç doğruluğu bilinmeyen taslaktır. */
object HomePickResult {
    fun parse(resultOk: Boolean, lat: Any?, lon: Any?): HomeDraft? {
        if (!resultOk || lat !is Double || lon !is Double) return null
        return HomeDraft.create(lat, lon)
    }
}
