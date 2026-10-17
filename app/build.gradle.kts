import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Kabuk her modüle bağlanır (WEEM_PLANI.md 4.2).
    listOf("core", "capture", "orders", "economics", "scooter", "map", "link", "remote").forEach {
        implementation(project(":$it"))
    }
}
