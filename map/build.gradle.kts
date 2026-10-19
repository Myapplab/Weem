// İnce Android katmanı; ortak ayarlar ve bağımlılıklar kök build.gradle.kts ve gradle/module-graph.properties'te.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    // Harita yüzeyi üstündeki Compose parçaları (ev seçici, konum imleci; MAP.md 15.2): A6.2b iskeleti, A6.5 doldurur.
    alias(libs.plugins.kotlin.compose)
}

android {
    testOptions {
        // Compose parçalarını JVM'de çizen denemeler (Robolectric; cihaz gerekmez).
        unitTests.isIncludeAndroidResources = true
    }
}

// Compose denemeleri debug'a bağlı (ui-test-manifest yalnız debug'da): release birim testi koşmaz.
tasks.matching { it.name == "testReleaseUnitTest" }.configureEach { enabled = false }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    // Harita yüzeyleri (MAP.md 6.15): MapLibre (OSM raster, vektör) ve Google Maps SDK; Compose'a AndroidView ile (maps-compose eklenmez).
    implementation(libs.maplibre.android)
    implementation(libs.play.services.maps)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(platform(libs.compose.bom))
    debugImplementation(platform(libs.compose.bom))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
