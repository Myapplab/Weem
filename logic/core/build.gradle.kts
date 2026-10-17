dependencies {
    // org.json Android'de platformunki; burada yalnız derleme ve JVM testi için (Rawstats'ın test bağımlılığı).
    compileOnly(libs.org.json)
    testImplementation(libs.org.json)
}
