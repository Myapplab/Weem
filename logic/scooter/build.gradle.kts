dependencies {
    // org.json Android'de platformunki; burada yalnız derleme ve JVM testi için (ScooterSamples, LogExport, enerji bloğu şekli).
    compileOnly(libs.org.json)
    testImplementation(libs.org.json)
}
