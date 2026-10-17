import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// Modül bağımlılıkları ve yön denetimi: gradle/module-graph.properties (logic/ ile aynı kaynak).
apply(from = "gradle/module-graph.gradle.kts")

subprojects {
    // Ortak Android ayarları (Rawstats ile aynı: compile/target 36, min 29, Java/JVM 17).
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            namespace = "com.kuryelog.app.${project.name}"
            compileSdk = 36
            defaultConfig {
                minSdk = 29
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
        // İnce Android katmanı: kararı veren kod aynı adlı logic/ modülünde.
        dependencies.add("implementation", "com.kuryelog.logic:${project.name}")
    }
    // Lint hataları CI'yı kırar; uyarılar ve ayarlar kök lint.xml'de.
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            lint {
                abortOnError = true
                lintConfig = rootProject.file("lint.xml")
            }
        }
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
