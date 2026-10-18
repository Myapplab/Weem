import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
            isMinifyEnabled = false
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
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
