// Kabuğun saf mantığı. Tablo dışı (gradle/module-graph.properties): kabuk her modüle bağlanabilir; dil (`Lang`) :core'da.
dependencies {
    "api"(project(":core"))
    // A8.3 S5 uçtan uca testi: sipariş akışının ürettiği mesaj → 2 telefon yükü (LINK.md 4.8).
    testImplementation(project(":orders"))
    testImplementation(project(":link"))
    testImplementation(libs.org.json)
}
