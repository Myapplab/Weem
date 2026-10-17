plugins {
    `java-test-fixtures`
}

dependencies {
    // org.json Android'de platformunki; burada yalnız derleme ve JVM testi için (CaptureSink satırları, saha kareleri).
    compileOnly(libs.org.json)
    testImplementation(libs.org.json)
    // Ortak test yardımcıları (saha kareleri yükleyicisi) `logic/orders` testleriyle paylaşılır (CAPTURE.md 8).
    testFixturesCompileOnly(libs.org.json)
    testFixturesImplementation(libs.org.json)
    testFixturesImplementation(libs.junit)
}
