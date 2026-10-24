import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Google Maps anahtarı (MAP.md 6.10): CI gizli değişkeni GOOGLE_MAPS_API_KEY, yerelde local.properties. Yoksa Google altlığı kapalı (MapLibre çalışır).
// Değer hiçbir belgeye ya da günlüğe yazılmaz: günlükte yalnız son 4 karakter.
val googleMapsKey: String = (System.getenv("GOOGLE_MAPS_API_KEY")
    ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }.getProperty("GOOGLE_MAPS_API_KEY")
    } ?: "").trim().also { key ->
    if (key.isNotEmpty()) {
        require(Regex("AIza[0-9A-Za-z_-]{35}").matches(key)) { "Google Maps anahtarı biçimi geçersiz" }
        logger.lifecycle("Google Maps anahtarı: ••••${key.takeLast(4)}")
    }
}

android {
    namespace = "com.kuryelog.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kuryelog.app"
        minSdk = 29
        targetSdk = 36
        // Tek kaynak: gradle.properties (weem.versionCode, weem.versionName).
        versionCode = providers.gradleProperty("weem.versionCode").get().toInt()
            .also { require(it > 33) { "weem.versionCode Rawstats'ın 33'ünden büyük olmalı: $it" } }
        versionName = providers.gradleProperty("weem.versionName").get()
        manifestPlaceholders["appLabel"] = "WEEM"
        resValue("string", "google_maps_key", googleMapsKey)
        buildConfigField("boolean", "MAPS_ENABLED", googleMapsKey.isNotEmpty().toString())
        // Yalnız arm64 (kullanıcı kararı S30, A10.1): Pixel 10 Pro, Poco X3, S22 arm64; x86/x86_64/armeabi-v7a ≈62 MiB yer tutuyordu.
        ndk { abiFilters += "arm64-v8a" }
    }

    // S39: gömülü karolar zaten sıkıştırılmış (gzip MVT); APK'da yeniden sıkıştırılmaz, ilk açılışta kopyası hızlı.
    androidResources { noCompress += "pmtiles" }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    // Rawstats'ın anahtarı (kurye_nav rawstats app/build.gradle.kts): Pixel'de Rawstats'ın üstüne kurulur,
    // izinler, Ninebot eşleşmesi ve kayıtlar kalır. Test anahtarı; değiştirmek kullanıcı kararı (plan Bölüm 2).
    signingConfigs {
        create("kuryelog") {
            storeFile = rootProject.file("ci/kuryelog-test.keystore")
            storePassword = "android"
            keyAlias = "kuryelogtest"
            keyPassword = "android"
        }
    }
    buildTypes {
        // .dev eski uygulamaların yanına ayrı kurulur (gölge mod); aynı anahtar, her makinede aynı imza.
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            manifestPlaceholders["appLabel"] = "WEEM dev"
            signingConfig = signingConfigs.getByName("kuryelog")
        }
        // com.kuryelog.app: Rawstats'ın yerini alır; WEEM 1.0'dan önce telefona kurulmaz.
        release {
            // R8 + kaynak küçültme (S30, A10.1): dex ≈41 → ≈6 MiB. Kod yansıma kullanmaz; manifest servis adları R8'de korunur.
            // Telefonda sorun çıkarsa isMinifyEnabled/isShrinkResources = false ile kapatılır (.dev küçültmesiz).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("kuryelog")
        }
    }

    testOptions {
        // Compose ekranlarını JVM'de çizen denemeler (Robolectric; cihaz gerekmez).
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        lintConfig = rootProject.file("lint.xml")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Compose denemeleri debug'a bağlı (ui-test-manifest yalnız debug'da): release birim testi koşmaz.
tasks.matching { it.name == "testReleaseUnitTest" }.configureEach { enabled = false }
// A10.4d görsel tur (`ScreenshotTour`, Robolectric yerel grafik): yalnız WEEM_SHOTS=<klasör> verilince koşar; CI'da yüklenmez.
tasks.withType<Test>().configureEach { if (System.getenv("WEEM_SHOTS") == null) exclude("**/ScreenshotTour*") }

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Kabuğun saf mantığı (sekmeler, dil, tema, palet): logic/app.
    implementation("com.kuryelog.logic:app")
    // Siparişler sekmesinin içeriği (A3.8): logic/orders OrdersScreen.
    implementation("com.kuryelog.logic:orders")
    // Vardiya kartının scooter satırı ve düğme metinleri (A5.3): logic/scooter ScooterText.
    implementation("com.kuryelog.logic:scooter")
    // Siparişler → "Teklif puanı" (E4, A4.1): logic/economics OfferScorePage, OfferScoreBook.
    implementation("com.kuryelog.logic:economics")
    // Harita ekranlarının durum tipleri (A6.2b iskeleti, A6.5–A6.9 doldurur): logic/map gün, nav, ride, weather, logs, tasks, reminders.
    implementation("com.kuryelog.logic:map")
    // 2 telefon ekranı (A8.3): logic/link LinkText, LinkView, LinkRole, PhoneBridge.
    implementation("com.kuryelog.logic:link")
    // Uzaktan sekmesi (A9.1): logic/remote RemoteText, RemoteLines, HostView, ViewerView.
    implementation("com.kuryelog.logic:remote")
    implementation("com.kuryelog.logic:core")
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    // A8.3 eşleştirme ekranı (S24): QR okuma ve üretme.
    implementation(libs.play.services.code.scanner)
    implementation(libs.zxing.core)
    // Kabuk her modüle bağlanır (WEEM_PLANI.md 4.2).
    listOf("core", "capture", "orders", "economics", "scooter", "map", "link", "remote").forEach {
        implementation(project(":$it"))
    }
    testImplementation(libs.junit)
    testImplementation(platform(libs.compose.bom))
    debugImplementation(platform(libs.compose.bom))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
