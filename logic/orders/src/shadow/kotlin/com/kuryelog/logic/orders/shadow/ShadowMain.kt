package com.kuryelog.logic.orders.shadow

import java.io.File
import java.time.ZoneId
import kotlin.system.exitProcess

private const val USAGE = """Kullanım: ./gradlew -p logic :orders:shadowReport --args="<rawstats.zip> <weem.zip> [--json <rapor.json>] [--zone Asia/Baku] [--examples 5] [--strict]"
  Girdiler: Rawstats ve WEEM .dev dışa aktarma ZIP'i (ya da açılmış klasörü); göreli yollar depo köküne göre.
  Çıktı: metin özeti ekrana, makine okunur JSON --json yoluna (varsayılan: WEEM ZIP'inin yanında <ad>.shadow.json).
  --strict: sert fark varsa çıkış kodu 1."""

/** Gölge mod raporu komut satırı (A3.9). Çıkış kodu: 0 tamam, 1 sert fark (`--strict`), 2 kullanım ya da okuma hatası. */
fun main(args: Array<String>) {
    val files = ArrayList<String>(); var json: String? = null; var zone = "Asia/Baku"; var examples = 5; var strict = false
    var i = 0
    try {
        while (i < args.size) {
            when (val a = args[i++]) {
                "--json" -> json = args[i++]
                "--zone" -> zone = args[i++]
                "--examples" -> examples = args[i++].toInt()
                "--strict" -> strict = true
                "-h", "--help" -> { println(USAGE); return }
                else -> files += a
            }
        }
        require(files.size == 2) { "iki girdi gerekir" }
    } catch (e: Exception) { System.err.println("${e.message ?: e}\n$USAGE"); exitProcess(2) }
    val report = try {
        ShadowCompare(ShadowExport.read(File(files[0])), ShadowExport.read(File(files[1])), ZoneId.of(zone)).run()
    } catch (e: Exception) { System.err.println("Okunamadı: ${e.message ?: e}"); exitProcess(2) }
    print(report.text(examples))
    val out = File(json ?: File(files[1]).let { File(it.absoluteFile.parentFile, it.nameWithoutExtension + ".shadow.json").path })
    out.writeText(report.json().toString(1), Charsets.UTF_8)
    println("\nJSON: ${out.path}")
    if (strict && report.hard > 0) exitProcess(1)
}
