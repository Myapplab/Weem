dependencies {
    // org.json Android'de platformunki; burada yalnız derleme ve JVM testi için (Overpass JSON, tercih biçimleri).
    compileOnly(libs.org.json)
    testImplementation(libs.org.json)
}
