// Kabuğun saf mantığı. Tablo dışı (gradle/module-graph.properties): kabuk her modüle bağlanabilir; dil (`Lang`) :core'da.
dependencies {
    "api"(project(":core"))
}
