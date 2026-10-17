// logic/ — saf Kotlin/JVM mantık (Android eklentisi yok). Her ortamda derlenir ve test edilir:
//   ./gradlew -p logic test
// Kök Android build bunu includeBuild("logic") ile bağlar (WEEM_PLANI.md 4.1).
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
rootProject.name = "logic"
include(":app", ":core", ":capture", ":orders", ":economics", ":scooter", ":map", ":link", ":remote")
