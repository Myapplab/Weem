dependencies {
    compileOnly(libs.org.json)
    testImplementation(libs.org.json)
    // Saha kareleri ve ortak kare kurucular `logic/capture` testFixtures'ından (CAPTURE.md 8).
    testImplementation(testFixtures(project(":capture")))
}
