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
        // A1.2: sürüm kodu tek kaynaktan, imza, .dev eki.
        versionCode = 34
        versionName = "0.1.0"
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
