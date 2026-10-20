dependencies {
    // org.json Android'de platformunki; burada yalnız derleme ve JVM testi için (Overpass JSON, tercih biçimleri).
    compileOnly(libs.org.json)
    testImplementation(libs.org.json)
}

// CI gömülü harita aracı (MAP.md 2.11; ci/build_map.sh): `./gradlew -p logic :map:buildMap --args="<çıktı> roads.json pois.json"`.
val mapTool: Configuration by configurations.creating
dependencies { mapTool(libs.org.json) }
tasks.register<JavaExec>("buildMap") {
    group = "weem"
    description = "Bakü yol ağı + yükseklik + yerler → assets/map (yalnız APK işi)"
    classpath = sourceSets["main"].runtimeClasspath + mapTool
    mainClass.set("com.kuryelog.logic.map.graph.MapBuildKt")
    workingDir = rootDir.parentFile
    maxHeapSize = "3g"
}
