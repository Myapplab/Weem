// Gölge mod fikstürü üreticisi (A3.9; ORDERS.md 14.3): saha karelerini Rawstats @ 6388c04'ün saf sınıflarından geçirip
// `logic/orders/src/test/resources/shadow/` fikstürlerini üretir. WEEM build'ine bağlı değil; yalnız fikstürü yeniden üretmek için:
//   git clone --depth 1 -b rawstats https://github.com/Myapplab/kurye_nav /tmp/ref-rawstats
//   ./gradlew -p tools/shadow-fixture run -Prawstats=/tmp/ref-rawstats \
//       --args="$PWD/logic/capture/src/testFixtures/resources/saha/saha_kareleri_2026-10-11_12.jsonl.gz /tmp/shadow-out"
//   (cd /tmp/shadow-out && zip -q -9 -r -X saha_rawstats_2026-10-11_12.zip 2026-* && gzip -9 stages.txt)
//   → saha_rawstats_2026-10-11_12.zip ve stages.txt.gz (= saha_rawstats_asamalar_2026-10-11_12.txt.gz)
plugins { kotlin("jvm") version "2.4.0"; application }
dependencies { implementation("org.json:json:20240303") }
kotlin { jvmToolchain(21) }

val rawstats = providers.gradleProperty("rawstats").map { file(it).resolve("app/src/main/java/com/kuryelog/app") }
// Rawstats'ın Android'siz dosyaları (Store/I18n'in saf kısmı src/Stubs.kt'de); Android'e ya da analiz dışa aktarmasına bağlı olanlar dışarıda.
val rawstatsSources by tasks.registering(Sync::class) {
    from(rawstats) {
        include("*.kt")
        exclude("AnalysisExport.kt", "BackupFiles.kt", "DailyRecords.kt", "LogFeedV3.kt")
        eachFile { if (file.readText().contains("import android")) exclude() }
    }
    into(layout.buildDirectory.dir("rawstats"))
}
sourceSets.main { kotlin.srcDir("src"); kotlin.srcDir(rawstatsSources) }
application { mainClass.set("com.kuryelog.app.GenKt"); applicationDefaultJvmArgs = listOf("-Duser.timezone=Asia/Baku") }
