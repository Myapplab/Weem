plugins {
    `java-test-fixtures`
}

// Gölge mod raporu (A3.9; ORDERS.md 14.3, CAPTURE.md 6.5): Rawstats ve WEEM `.dev` ZIP'lerini karşılaştıran JVM aracı. Ayrı kaynak
// seti: uygulamaya (APK) girmez, `main`in `internal` üyelerine test gibi erişir. Çalıştırma: `./gradlew -p logic :orders:shadowReport --args="…"`.
val shadow: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[shadow.implementationConfigurationName].extendsFrom(configurations.implementation.get())
kotlin.target.compilations.getByName("shadow").associateWith(kotlin.target.compilations.getByName("main"))

dependencies {
    compileOnly(libs.org.json)
    testImplementation(libs.org.json)
    // Saha kareleri ve ortak kare kurucular `logic/capture` testFixtures'ından (CAPTURE.md 8).
    testImplementation(testFixtures(project(":capture")))
    // Araç JVM'de çalışır: org.json burada çalışma zamanında da gerekir (Android'de platformunki).
    "shadowImplementation"(libs.org.json)
    testImplementation(shadow.output)
}
kotlin.target.compilations.getByName("test").associateWith(kotlin.target.compilations.getByName("shadow"))

tasks.register<JavaExec>("shadowReport") {
    group = "verification"
    description = "Gölge mod raporu: Rawstats ZIP'i ile WEEM .dev ZIP'ini gün gün karşılaştırır (ORDERS.md 14.3)."
    classpath = shadow.runtimeClasspath
    mainClass.set("com.kuryelog.logic.orders.shadow.ShadowMainKt")
    // Göreli yollar depo köküne göre (komut kökten `./gradlew -p logic …` ile çalışır).
    workingDir = rootDir.parentFile
    // Türkçe rapor metni her terminalde UTF-8 (JDK 18+ `stdout.encoding`).
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dfile.encoding=UTF-8")
}
