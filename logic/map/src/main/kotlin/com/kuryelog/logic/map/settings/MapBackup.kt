package com.kuryelog.logic.map.settings

import com.kuryelog.logic.core.EncryptedBackup
import com.kuryelog.logic.map.surface.MarkStore
import com.kuryelog.logic.map.weather.WeatherPrefs
import org.json.JSONObject
import java.io.File

/**
 * Haritanın küçük verisi haftalık şifreli yedekte (A10.4h; S35, MAP.md 17.1 #15 b): `map` tercihleri (sırlar çıkarılmış, 14.10),
 * `files/map/roads/` (Y12), `files/map/manual-marks.json` (Ses−), haftanın gün raporları (`.gun.json`) ve rota kayıtları (`_route_<ms>.json`).
 * Sürüş günlükleri (`.jsonl`), hava önbelleği ve öteki dosyalar girmez. Kap ve şifre `:core` [EncryptedBackup]'ta.
 *
 * Geri yükleme iki adım: yedek açılınca harita kısmı [savePending] ile `files/` + [PENDING] klasörüne bırakılır; süreç yeniden başlarken,
 * harita çalışma zamanları kurulmadan önce [applyPending] yalnız telefonda olmayan dosyaları ve tercihleri yazar (bellekteki boş durum
 * geri yüklenen dosyayı ezmesin diye). Üzerine yazılmaz; sırlar geri yüklemede de süzülür.
 */
object MapBackup {
    /** Yedeğe girmeyen `settings.v1` alanları ve `map` anahtarları (MAP.md 14.10). */
    val SECRET_SETTINGS = listOf("googleKey", "orsKey", "cartoKey")
    val SECRET_PREFS = setOf(WeatherPrefs.KEY)
    const val PENDING = "map-restore"
    private const val PENDING_PREFS = "prefs.json"
    private const val PENDING_FILES = "files"

    private val ROADS = Regex("roads\\.[a-z0-9.]+\\.json")
    private val LOG = Regex("(\\d{4}-\\d{2}-\\d{2})(?:(?:_\\d{4})?\\.gun\\.json|_route_\\d+\\.json)")

    /** `map` tercihleri, sırlar çıkarılmış: [SECRET_PREFS] atlanır, `settings.v1`'den [SECRET_SETTINGS] silinir (okunamazsa hiç girmez). */
    fun strip(all: Map<String, Any?>): Map<String, Any?> = all.mapNotNull { (k, v) ->
        when {
            k in SECRET_PREFS -> null
            k == MapSettings.KEY -> (v as? String)?.let { s -> runCatching { JSONObject(s) }.getOrNull() }
                ?.let { j -> SECRET_SETTINGS.forEach(j::remove); k to j.toString() }
            else -> k to v
        }
    }.toMap()

    /** Yedeğe girecek `files/map/` dosyaları ([EncryptedBackup.MAP_PREFIX] adlarıyla); günlük dosyalar yalnız [days] günleri. */
    fun files(mapDir: File, days: Collection<String>): List<Pair<String, File>> {
        val out = ArrayList<Pair<String, File>>()
        File(mapDir, "roads").listFiles { f -> f.isFile && ROADS.matches(f.name) }.orEmpty().sortedBy { it.name }
            .forEach { out += "${EncryptedBackup.MAP_PREFIX}roads/${it.name}" to it }
        File(mapDir, MarkStore.FILE).takeIf { it.isFile }?.let { out += "${EncryptedBackup.MAP_PREFIX}${it.name}" to it }
        File(mapDir, "logs").listFiles { f -> f.isFile && LOG.matchEntire(f.name)?.groupValues?.get(1) in days }.orEmpty().sortedBy { it.name }
            .forEach { out += "${EncryptedBackup.MAP_PREFIX}logs/${it.name}" to it }
        return out
    }

    /** Geri yüklenebilir harita dosyası adı ([files]'ın ürettikleri); başka ad yok sayılır. */
    fun accepts(name: String): Boolean {
        if (!EncryptedBackup.mapEntry(name)) return false
        val rel = name.removePrefix(EncryptedBackup.MAP_PREFIX)
        return rel == MarkStore.FILE ||
            (rel.startsWith("roads/") && ROADS.matches(rel.removePrefix("roads/"))) ||
            (rel.startsWith("logs/") && LOG.matches(rel.removePrefix("logs/")))
    }

    /** Açılan yedeğin harita kısmını [filesDir]/[PENDING]'e bırakır (öncekini değiştirir). Bırakılan dosya sayısı; bırakacak bir şey yoksa 0. */
    fun savePending(filesDir: File, prefs: Map<String, Any>, files: List<Pair<String, ByteArray>>): Int {
        val keep = files.filter { accepts(it.first) }
        val cleanPrefs = strip(prefs).filterValues { it != null }
        if (keep.isEmpty() && cleanPrefs.isEmpty()) return 0
        val tmp = File(filesDir, "$PENDING.tmp").apply { deleteRecursively(); mkdirs() }
        File(tmp, PENDING_PREFS).writeText(EncryptedBackup.encodePrefs(cleanPrefs) { false }.toString())
        for ((name, bytes) in keep) File(tmp, "$PENDING_FILES/${name.removePrefix(EncryptedBackup.MAP_PREFIX)}").apply { parentFile?.mkdirs() }.writeBytes(bytes)
        val dir = File(filesDir, PENDING)
        dir.deleteRecursively()
        check(tmp.renameTo(dir)) { "pending rename" }
        return keep.size
    }

    fun hasPending(filesDir: File): Boolean = File(filesDir, PENDING).isDirectory

    /** [applyPending] sonucu: telefona yazılacak (eksik) `map` tercihleri ve kopyalanan dosya sayısı. */
    class Applied(val prefs: Map<String, Any>, val files: Int)

    /**
     * Bekleyen geri yüklemeyi uygular: [filesDir]/`map/` altında olmayan dosyalar kopyalanır (`.tmp` + yeniden adlandırma), [currentPrefs]'te
     * olmayan tercihler döner (çağıran yazar). Bekleyen klasör her durumda silinir; yoksa null.
     */
    fun applyPending(filesDir: File, currentPrefs: Set<String>): Applied? {
        val dir = File(filesDir, PENDING)
        if (!dir.isDirectory) return null
        try {
            val prefs = runCatching { EncryptedBackup.decodePrefs(JSONObject(File(dir, PENDING_PREFS).readText())) { false } }.getOrDefault(emptyMap())
            val missing = strip(prefs).filter { (k, v) -> k !in currentPrefs && v != null }.mapValues { it.value!! }
            val root = File(dir, PENDING_FILES)
            var copied = 0
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(root).invariantSeparatorsPath
                if (!accepts(EncryptedBackup.MAP_PREFIX + rel)) return@forEach
                val target = File(File(filesDir, "map"), rel)
                if (target.exists()) return@forEach
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, "${target.name}.restore.tmp")
                f.copyTo(tmp, overwrite = true)
                if (tmp.renameTo(target)) copied++ else tmp.delete()
            }
            return Applied(missing, copied)
        } finally { dir.deleteRecursively() }
    }
}
