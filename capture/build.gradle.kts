// İnce Android katmanı; ortak ayarlar ve bağımlılıklar kök build.gradle.kts ve gradle/module-graph.properties'te.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

dependencies {
    // OCR (CAPTURE.md 3.3). Telemetri arka ucu manifestte kapatılır (AndroidManifest.xml, CAPTURE.md 7.3).
    implementation(libs.mlkit.text.recognition)
}

// Ağ yasağı (CAPTURE.md 7.3): çalışma sınıf yolundaki her modül (grup:ad) `runtime-allowlist.txt`'te. Listede olmayan yeni bir
// bağımlılık derlemeyi durdurur; eklemeden önce ağ kullanıp kullanmadığı denetlenir ve listeye bilerek yazılır.
val runtimeModules = provider { configurations.getByName("releaseRuntimeClasspath") }.flatMap { it.incoming.resolutionResult.rootComponent }
val verifyRuntimeAllowlist by tasks.registering {
    val allowFile = file("runtime-allowlist.txt")
    inputs.file(allowFile)
    inputs.property("modules", runtimeModules.map { root ->
        val seen = LinkedHashSet<String>()
        fun walk(c: org.gradle.api.artifacts.result.ResolvedComponentResult) {
            for (d in c.dependencies) if (d is org.gradle.api.artifacts.result.ResolvedDependencyResult) {
                val id = d.selected.moduleVersion ?: continue
                if (seen.add("${id.group}:${id.name}")) walk(d.selected)
            }
        }
        walk(root)
        seen.sorted()
    })
    doLast {
        @Suppress("UNCHECKED_CAST")
        val modules = inputs.properties["modules"] as List<String>
        val allowed = allowFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        val extra = modules.filter { it !in allowed }
        check(extra.isEmpty()) { ":capture çalışma sınıf yolunda izin listesinde olmayan modül (CAPTURE.md 7.3): $extra" }
    }
}
tasks.named("preBuild") { dependsOn(verifyRuntimeAllowlist) }
