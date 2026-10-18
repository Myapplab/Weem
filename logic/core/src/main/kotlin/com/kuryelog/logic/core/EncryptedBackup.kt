package com.kuryelog.logic.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * N3 otomatik şifreli yedek (yeni özellik; kullanıcı kararı S22, 18 Ekim, `WEEM_PLANI.md` Bölüm 8). Her vardiya sonu, vardiyanın değdiği
 * hafta(lar) için bir dosya: `İndirilenler/WEEM/yedek/WEEM_yedek_<Pazartesi>_<Pazar>.weem`; aynı haftanın dosyası yenilenir.
 *
 * İçerik (şifreli ZIP): haftanın gün kayıtları (`YYYY-MM-DD/<logNames>`, tam yedekle aynı), `restaurants.json`, [PREFS] (`kuryelog`
 * tercihleri; kullanıcının ev konumu `home.*`, vardiyanın o anki durumu ve cihaz kimliği HARİÇ, [excludedPref]), `courier/trips.jsonl`
 * ve `courier/offers.jsonl` (o haftanın `courier.db` satırları, müşteri telefonu dahil), [MANIFEST]. Gün sırları, Sürüş Paneli eşleşmesi ve
 * başka dosya girmez.
 *
 * Kap biçimi (`.weem`): [MAGIC] (8 bayt) · PBKDF2 tur sayısı (4 bayt, büyük uçlu) · tuz (16) · IV (12) · AES-256-GCM şifreli ZIP (sonunda
 * 16 baytlık etiket). Anahtar = PBKDF2WithHmacSHA256(parola, tuz, tur, 256 bit); ilk 40 bayt ek doğrulanan veri (başlık değişirse açılmaz).
 * Parola koda, dosyaya ya da kayda yazılmaz. Ağ kodu yok.
 */
object EncryptedBackup {
    const val FOLDER = BackupExport.FOLDER + "/yedek"
    const val EXTENSION = ".weem"
    const val MAGIC = "WEEMBAK1"
    const val SCHEMA = "weem-encrypted-backup/1"
    const val MANIFEST = "WEEM_YEDEK.json"
    const val PREFS = "kuryelog.json"
    const val TRIPS = "courier/trips.jsonl"
    const val OFFERS = "courier/offers.jsonl"
    /** OWASP (2023) PBKDF2-HMAC-SHA256 önerisi; yedek arka planda yazılır. */
    const val ITERATIONS = 600_000
    const val MIN_PASSWORD = 8
    private const val SALT = 16
    private const val IV = 12
    private const val HEADER = 8 + 4 + SALT + IV
    private const val TAG_BITS = 128
    /** Açılırken bellekte tutulan en büyük şifreli dosya (GCM etiketi doğrulanmadan çıktı verilmez); içe aktarma sınırıyla aynı. */
    const val MAX_BYTES = LogImporter.MAX_TOTAL_BYTES

    /** Bir haftanın Pazartesi'si ve Pazar'ı. */
    data class Week(val monday: LocalDate) {
        val sunday: LocalDate get() = monday.plusDays(6)
        val days: List<String> get() = (0L..6L).map { monday.plusDays(it).toString() }
        val fileName: String get() = "${BackupExport.APP_NAME}_yedek_${monday}_$sunday$EXTENSION"
        operator fun contains(day: String): Boolean = runCatching { LocalDate.parse(day) }.getOrNull()?.let { !it.isBefore(monday) && !it.isAfter(sunday) } == true
    }

    fun week(day: String): Week = Week(LocalDate.parse(day).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    /** Vardiyanın değdiği haftalar ([DailyPack.days] günlerinden). */
    fun weeks(startMs: Long, endMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Week> = DailyPack.days(startMs, endMs, zone).map(::week).distinct()
    /** Dosya adından hafta (`WEEM_yedek_<Pazartesi>_<Pazar>.weem`); başka ad null. */
    fun weekOf(fileName: String): Week? = Regex("${BackupExport.APP_NAME}_yedek_([0-9]{4}-[0-9]{2}-[0-9]{2})_([0-9]{4}-[0-9]{2}-[0-9]{2})\\.weem")
        .matchEntire(fileName)?.let { m -> runCatching { week(m.groupValues[1]) }.getOrNull()?.takeIf { it.monday.toString() == m.groupValues[1] && it.sunday.toString() == m.groupValues[2] } }

    // ---- tercihler
    private val excluded = setOf(KuryelogPrefs.SHIFT, KuryelogPrefs.MANUAL_SESSION, KuryelogPrefs.AUTO_STARTED, KuryelogPrefs.SHIFT_START,
        KuryelogPrefs.SHIFT_START_REASON, KuryelogPrefs.SHIFT_ENDED_AT, KuryelogPrefs.SHIFT_END_REASON, KuryelogPrefs.SHIFT_INTERRUPTED_AT,
        KuryelogPrefs.DIST, KuryelogPrefs.DEVICE_ID)
    /** Yedeğe girmeyen tercih: kullanıcının evi (`home.` önekli her anahtar, S22), vardiyanın o anki durumu, cihaz kimliği. */
    fun excludedPref(key: String): Boolean = key.startsWith("home.") || key in excluded

    /** `SharedPreferences.getAll()` → [PREFS] JSON: `{anahtar: {"type": bool|int|long|float|string|set, "value": …}}`; [excludedPref] atlanır. */
    fun encodePrefs(all: Map<String, Any?>): JSONObject {
        val out = JSONObject()
        for ((k, v) in all.toSortedMap()) {
            if (excludedPref(k) || v == null) continue
            val (type, value) = when (v) {
                is Boolean -> "bool" to v
                is Int -> "int" to v
                is Long -> "long" to v
                is Float -> "float" to v.toDouble()
                is String -> "string" to v
                is Set<*> -> "set" to JSONArray(v.filterIsInstance<String>().sorted())
                else -> continue
            }
            out.put(k, JSONObject().put("type", type).put("value", value))
        }
        return out
    }

    /** [encodePrefs] tersi; bilinmeyen tip ya da [excludedPref] anahtarı atlanır (elle değiştirilmiş dosya evi geri yazamaz). */
    fun decodePrefs(j: JSONObject): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for (k in j.keys()) {
            if (excludedPref(k)) continue
            val e = j.optJSONObject(k) ?: continue
            val v: Any = when (e.optString("type")) {
                "bool" -> e.optBoolean("value")
                "int" -> e.optInt("value")
                "long" -> e.optLong("value")
                "float" -> e.optDouble("value").toFloat()
                "string" -> e.optString("value")
                "set" -> e.optJSONArray("value")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: continue
                else -> continue
            }
            out[k] = v
        }
        return out
    }

    /** Geri yüklemede telefona yazılacak tercihler: yalnız telefonda olmayan anahtarlar (üzerine yazmaz). */
    fun missingPrefs(backup: Map<String, Any>, current: Set<String>): Map<String, Any> = backup.filterKeys { it !in current && !excludedPref(it) }

    // ---- courier.db
    /** Telefondaki yolculuk satırı [current] ile yedekteki [backup]: kaydedilecek satır ya da null. Yoksa yedeğinki; varsa yalnız eksik müşteri telefonları eklenir. */
    fun mergeTrip(current: JSONObject?, backup: JSONObject): JSONObject? {
        if (current == null) return backup
        val phones = HashMap<String, String>()
        backup.optJSONArray("customers")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { c ->
            val p = c.opt("phone") as? String; val id = c.opt("id") as? String
            if (!p.isNullOrBlank() && id != null) phones[id] = p
        } }
        if (phones.isEmpty()) return null
        val out = JSONObject(current.toString())
        var changed = false
        out.optJSONArray("customers")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { c ->
            val id = c.opt("id") as? String
            if (id != null && (c.opt("phone") as? String).isNullOrBlank() && phones[id] != null) { c.put("phone", phones[id]); changed = true }
        } }
        return if (changed) out else null
    }

    // ---- yazma
    /** Kaynaklar: haftanın gün dosyaları ve `restaurants.json` [staging]'e kopyalanır (yazanın kilidi altında); tercihler ve `courier.db` satırları bellekte. */
    class Content(val files: List<Pair<String, File>>, val prefs: JSONObject, val trips: List<JSONObject>, val offers: List<Pair<JSONObject, String?>>)

    /** Haftanın kayıtlarını [staging]'e kopyalar; o haftanın gün dosyası yoksa null (yedek yazılmaz). */
    fun stage(logsRoot: File, filesDir: File, week: Week, staging: File): List<Pair<String, File>>? {
        val records = DailyRecords(logsRoot)
        val files = week.days.flatMap { records.files(it) }
        if (files.isEmpty()) return null
        for (f in files) { val copy = File(staging, f.relativeTo(logsRoot).invariantSeparatorsPath); copy.parentFile?.mkdirs(); f.copyTo(copy) }
        val rf = File(filesDir, BackupExport.RESTAURANTS); if (rf.isFile) rf.copyTo(File(staging, rf.name))
        return BackupExport.entries(staging)
    }

    /** Şifreli yedeği [out]'a yazar (sonunda kapatır). [iterations] yalnız testte düşürülür. */
    fun write(week: Week, content: Content, password: CharArray, out: OutputStream, versionName: String, createdAtMs: Long,
              iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()) {
        checkPassword(password)
        val salt = ByteArray(SALT).also(random::nextBytes); val iv = ByteArray(IV).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER).put(MAGIC.toByteArray(Charsets.US_ASCII)).putInt(iterations).put(salt).put(iv).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv)); updateAAD(header)
        }
        out.use { o ->
            o.write(header)
            val stream = javax.crypto.CipherOutputStream(NonClosing(o), cipher)
            ZipOutputStream(stream).use { z -> zip(z, week, content, versionName, createdAtMs) }
        }
    }

    private fun zip(z: ZipOutputStream, week: Week, content: Content, versionName: String, createdAtMs: Long) {
        val counts = JSONObject()
        fun text(name: String, s: String) { z.putNextEntry(ZipEntry(name)); z.write(s.toByteArray(Charsets.UTF_8)); z.closeEntry() }
        for ((name, f) in content.files) {
            require(name == BackupExport.RESTAURANTS || (BackupFiles.checkedPath(name) == name && name.substringBefore('/') in week)) { "Invalid backup entry" }
            z.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry(); counts.put(name, f.length())
        }
        text(PREFS, content.prefs.toString())
        val trips = content.trips.filter { (it.opt("day") as? String)?.let { d -> d in week } == true }
        val offers = content.offers.filter { (it.first.opt("day") as? String)?.let { d -> d in week } == true }
        text(TRIPS, trips.joinToString("") { it.toString() + "\n" })
        text(OFFERS, offers.joinToString("") { (j, r) -> JSONObject().put("payload", j).put("result", r ?: JSONObject.NULL).toString() + "\n" })
        text(MANIFEST, JSONObject().put("schema", SCHEMA).put("app", "com.kuryelog.app").put("versionName", versionName).put("createdAtMs", createdAtMs)
            .put("week", JSONObject().put("from", week.monday.toString()).put("to", week.sunday.toString()))
            .put("files", counts).put("prefs", content.prefs.length()).put("trips", trips.size).put("offers", offers.size).toString())
    }

    // ---- okuma
    class WrongPassword : IllegalArgumentException("wrong password or damaged file")

    /** Geri yüklenecekler: gün dosyaları için düz ZIP ([LogImporter.restore]'a verilir), tercihler, `courier.db` satırları, işaret. */
    class Opened(val logsZip: ByteArray, val prefs: Map<String, Any>, val trips: List<JSONObject>, val offers: List<Pair<JSONObject, String?>>, val manifest: JSONObject)

    /** Şifreyi çözer ve içeriği denetler; parola yanlış ya da dosya bozuksa [WrongPassword]. Kayıtlara dokunmaz. */
    fun open(input: InputStream, password: CharArray): Opened {
        val data = DataInputStream(input)
        val header = ByteArray(HEADER).also { data.readFully(it) }
        require(String(header, 0, 8, Charsets.US_ASCII) == MAGIC) { "Not a WEEM backup" }
        val buffer = ByteBuffer.wrap(header, 8, HEADER - 8)
        val iterations = buffer.int
        require(iterations in 10_000..10_000_000) { "Invalid backup header" }
        val salt = ByteArray(SALT).also { buffer.get(it) }; val iv = ByteArray(IV).also { buffer.get(it) }
        val body = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (true) { val n = input.read(chunk); if (n < 0) break; body.write(chunk, 0, n); require(body.size() <= MAX_BYTES) { "Backup is too large" } }
        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv)); updateAAD(header)
            }.doFinal(body.toByteArray())
        } catch (e: AEADBadTagException) { throw WrongPassword() }
        return read(plain)
    }

    /**
     * Geri yükleme (S22): [open], sonra gün dosyaları ve `restaurants.json` [LogImporter.restore] kurallarıyla (birleştirir, üzerine yazmaz;
     * biri geçersizse hiçbiri yazılmaz). Tercihler ([missingPrefs]) ve `courier.db` satırları ([mergeTrip]) Android'de uygulanır. Dönen:
     * açılan yedek ve içe aktarılan dosya adları. Vardiya kapalıyken, `importing` bayrağıyla çağrılır (içe aktarmayla aynı).
     */
    fun restore(input: InputStream, password: CharArray, filesDir: File, logsRoot: File, cacheDir: File): Pair<Opened, List<String>> {
        val opened = open(input, password)
        return opened to LogImporter.restore(ByteArrayInputStream(opened.logsZip), filesDir, logsRoot, cacheDir)
    }

    private fun read(plain: ByteArray): Opened {
        val logs = ByteArrayOutputStream()
        var prefs: Map<String, Any> = emptyMap(); val trips = ArrayList<JSONObject>(); val offers = ArrayList<Pair<JSONObject, String?>>()
        var manifest: JSONObject? = null
        ZipOutputStream(logs).use { out ->
            ZipInputStream(ByteArrayInputStream(plain)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val bytes = z.readBytes()
                    when (e.name) {
                        PREFS -> prefs = decodePrefs(JSONObject(String(bytes, Charsets.UTF_8)))
                        TRIPS -> lines(bytes).forEach { trips += JSONObject(it).also { j -> j.getString("id"); j.getString("pkg"); j.getString("day") } }
                        OFFERS -> lines(bytes).forEach { val o = JSONObject(it); val p = o.getJSONObject("payload"); p.getString("id"); p.getString("pkg"); p.getString("day")
                            offers += p to (o.opt("result") as? String) }
                        MANIFEST -> manifest = JSONObject(String(bytes, Charsets.UTF_8))
                        // Gün dosyaları ve restaurants.json: LogImporter kurallarıyla (checkedPath, JSON/CSV denetimi, birleştirme).
                        else -> { out.putNextEntry(ZipEntry(e.name)); out.write(bytes); out.closeEntry() }
                    }
                }
            }
        }
        val m = manifest ?: throw IllegalArgumentException("Backup has no manifest")
        require(m.optString("schema") == SCHEMA) { "Unknown backup schema" }
        return Opened(logs.toByteArray(), prefs, trips, offers, m)
    }

    private fun lines(bytes: ByteArray) = String(bytes, Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.toList()

    fun checkPassword(password: CharArray) = require(password.size >= MIN_PASSWORD) { "Password too short" }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }

    /** `CipherOutputStream.close` GCM etiketini yazar ve alttaki akışı kapatır; dış akışı [write] kapatır. */
    private class NonClosing(private val o: OutputStream) : java.io.FilterOutputStream(o) {
        override fun write(b: ByteArray, off: Int, len: Int) = o.write(b, off, len)
        override fun close() = o.flush()
    }
}
