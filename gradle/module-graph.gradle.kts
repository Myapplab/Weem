// Modül grafiği: gradle/module-graph.properties'teki izinli bağımlılıkları ekler ve denetler.
// Kök projeye `apply(from = ...)` ile uygulanır (logic/ ve kök Android build aynı dosyayı kullanır).
import java.util.Properties

val graphFile = rootDir.resolve("gradle/module-graph.properties").takeIf { it.exists() }
    ?: rootDir.resolve("../gradle/module-graph.properties")

val graphProps = Properties().apply { graphFile.reader(Charsets.UTF_8).use { load(it) } }
val graph: Map<String, List<String>> = graphProps.stringPropertyNames()
    .associateWith { key -> graphProps.getProperty(key).split(',').map { it.trim() }.filter { it.isNotEmpty() } }
    .toSortedMap()

fun graphErrors(): List<String> {
    val errors = mutableListOf<String>()
    if (graph["core"].orEmpty().isNotEmpty()) errors += "core hiçbir modüle bağlanamaz: ${graph["core"]}"
    graph.forEach { (m, deps) ->
        deps.filter { it !in graph }.forEach { errors += "$m → $it: tabloda olmayan modül" }
        if (m != "core" && "core" !in deps) errors += "$m → core eksik"
    }
    // Döngü: derinlik öncelikli arama.
    val state = mutableMapOf<String, Int>()
    fun visit(m: String, path: List<String>) {
        when (state[m]) {
            1 -> { errors += "döngü: ${(path + m).joinToString(" → ")}"; return }
            2 -> return
        }
        state[m] = 1
        graph[m].orEmpty().forEach { visit(it, path + m) }
        state[m] = 2
    }
    graph.keys.forEach { visit(it, emptyList()) }
    return errors
}

subprojects {
    val deps = graph[name] ?: return@subprojects
    val sub = this
    configurations.matching { it.name == "api" }.configureEach {
        deps.forEach { dependencies.add(sub.dependencies.project(mapOf("path" to ":$it"))) }
    }
}

val checkModuleGraph = tasks.register("checkModuleGraph") {
    group = "verification"
    description = "Modül bağımlılık yönünü denetler (WEEM_PLANI.md 4.2)."
    // Bildirilen proje bağımlılıkları yapılandırma sırasında toplanır.
    val declared: Provider<Map<String, Set<String>>> = provider {
        subprojects.associate { p ->
            p.name to p.configurations.flatMap { c ->
                c.dependencies.withType(ProjectDependency::class.java).map { it.path.removePrefix(":") }
            }.filter { it != p.name }.toSortedSet()
        }
    }
    val staticErrors = graphErrors()
    val allowed = graph
    doLast {
        val errors = staticErrors.toMutableList()
        declared.get().forEach { (m, used) ->
            val ok = allowed[m] ?: return@forEach // tablo dışı (:app) serbest
            used.filter { it !in ok }.forEach { errors += "$m → $it izinli değil (gradle/module-graph.properties)" }
        }
        if (errors.isNotEmpty()) throw GradleException("Modül grafiği hatalı:\n- " + errors.joinToString("\n- "))
        logger.lifecycle("Modül grafiği: ${allowed.size} modül, yön ve döngü denetimi geçti")
    }
}

subprojects {
    tasks.matching { it.name == "test" || it.name == "check" }.configureEach { dependsOn(checkModuleGraph) }
}
