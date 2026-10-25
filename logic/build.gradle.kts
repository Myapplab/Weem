import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

// Modül bağımlılıkları ve yön denetimi: gradle/module-graph.properties (iki build için tek kaynak).
apply(from = "../gradle/module-graph.gradle.kts")

val junit = libs.junit

// Kaynak taraması testleri (RepoSource; ör. PrivacyAuditTest, TextCoverageTest, BasemapAssetsTest) Android modüllerini,
// araçları ve öteki logic/ modüllerinin kaynağını okur. Girdi bildirilmezse yalnız bunlar değişince Gradle testi "güncel"
// sayar ve koşmaz (S39'da yerelde yeşil, CI'da kırmızı).
val repo = rootDir.parentFile
val modules = listOf("app", "core", "capture", "orders", "economics", "scooter", "map", "link", "remote") // Android <m>/ = logic/<m>/
val scannedSources = files(
    modules.flatMap { listOf(repo.resolve("$it/src"), repo.resolve("$it/build.gradle.kts"), file("$it/src/main")) } +
        listOf(repo.resolve("tools"), repo.resolve("gradle")),
).asFileTree.matching { exclude("**/build/**", "**/.gradle/**", "**/__pycache__/**", "**/node_modules/**") }

subprojects {
    // Kök Android build bu modülleri koordinatla ister: com.kuryelog.logic:<modül> (includeBuild ile yerine konur).
    group = "com.kuryelog.logic"
    version = "1.0"

    apply(plugin = "java-library")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    // Android katmanıyla aynı hedef (Rawstats: Java/JVM 17); derleyen JDK 21 olabilir.
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    dependencies {
        "testImplementation"(junit)
    }

    tasks.withType<Test>().configureEach {
        inputs.files(scannedSources).withPropertyName("repoSources").withPathSensitivity(PathSensitivity.RELATIVE)
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
