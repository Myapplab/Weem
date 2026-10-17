dependencies {
    // Modüller arası arayüzler StateFlow taşır (plan 4.3); Android'de Compose ile aynı sürüm.
    "api"(libs.kotlinx.coroutines.core)
    // org.json Android'de platformunki; burada yalnız derleme ve JVM testi için (Rawstats'ın test bağımlılığı).
    compileOnly(libs.org.json)
    testImplementation(libs.org.json)
}
