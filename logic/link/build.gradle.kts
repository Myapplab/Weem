dependencies {
    // A8.3 yükleri `:core` mesajlarının JSON'u; org.json Android'de platformunki (`:core` gibi).
    compileOnly(libs.org.json)
    // Testler Noise vektörlerini (JSON) okur (LINK.md 3.14).
    testImplementation(libs.org.json)
}
