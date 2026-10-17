// WEEM kök build (Android). Saf Kotlin mantık ayrı build'de: logic/ (./gradlew -p logic test).
// Android SDK ve Google Maven (dl.google.com) gerekir; kapalı ortamda yalnız logic/ derlenir (AGENTS.md → Ortam).
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "WEEM"

// Android modülleri com.kuryelog.logic:<modül> ister; Gradle bunu logic/ projeleriyle değiştirir.
includeBuild("logic")

include(":app", ":core", ":capture", ":orders", ":economics", ":scooter", ":map", ":link", ":remote")
