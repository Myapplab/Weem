import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

// Modül bağımlılıkları ve yön denetimi: gradle/module-graph.properties (iki build için tek kaynak).
apply(from = "../gradle/module-graph.gradle.kts")

val junit = libs.junit

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
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
