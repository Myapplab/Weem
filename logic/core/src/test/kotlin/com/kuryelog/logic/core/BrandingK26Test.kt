package com.kuryelog.logic.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RS `BrandingK26Test` (G5, A7.2) — UYARLANDI: paket ve tercih dosyası Rawstats'la aynı (WEEM onun üstüne kurulur), başlatıcı adı "WEEM".
 * Simge kullanıcı kararı (bugün sistem varsayılanı; `@android:drawable/` yok). Üçüncü test (`homeHasNoTabBarAndSettingsOpenFromGear`)
 * çevrilmez: WEEM'de alt menü kabuğu ve "Daha fazla" sekmesi var (plan 4.1; `logic/app` `ShellTest`).
 */
class BrandingK26Test {
    @Test fun packageAndPrefsStayTheSame() {
        assertTrue(RepoSource.text("app/build.gradle.kts").contains("applicationId = \"com.kuryelog.app\""))
        assertEquals("kuryelog", KuryelogPrefs.FILE)
        assertTrue(RepoSource.text("core/src/main/kotlin/com/kuryelog/app/core/Store.kt").contains("getSharedPreferences(KuryelogPrefs.FILE, Context.MODE_PRIVATE)"))
        assertTrue(RepoSource.text("app/src/main/kotlin/com/kuryelog/app/ShellPrefs.kt").contains("const val FILE = \"kuryelog\""))
    }

    @Test fun launcherIsNamedWeem() {
        val gradle = RepoSource.text("app/build.gradle.kts")
        assertTrue(gradle.contains("manifestPlaceholders[\"appLabel\"] = \"WEEM\""))
        assertTrue(gradle.contains("manifestPlaceholders[\"appLabel\"] = \"WEEM dev\""))
        val manifest = RepoSource.text("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:label=\"\${appLabel}\""))
        assertFalse(manifest.contains("@android:drawable/"))
    }
}
