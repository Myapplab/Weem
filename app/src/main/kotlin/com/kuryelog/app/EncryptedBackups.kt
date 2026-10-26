package com.kuryelog.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.kuryelog.app.core.CourierDb
import com.kuryelog.app.core.Downloads
import com.kuryelog.app.core.RestoreHook
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.DailyRecords
import com.kuryelog.logic.core.EncryptedBackup
import com.kuryelog.logic.core.LogTime
import com.kuryelog.logic.core.ShiftMachine
import com.kuryelog.logic.map.settings.MapBackup
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * N3 parolası telefonda (S22): her vardiyada sorulmasın diye AndroidKeyStore'daki [ALIAS] AES/GCM anahtarıyla şifreli, ayrı tercih dosyası
 * [FILE]'da (`kuryelog` değil: hiçbir yedeğe girmez; `allowBackup=false`). Dışa aktarılan haftaların işareti de burada. Cihazda doğrulanmadı.
 */
class BackupVault(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun hasPassword(): Boolean = prefs.contains(PASSWORD)

    fun setPassword(password: CharArray) {
        EncryptedBackup.checkPassword(password)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, master()) }
        val bytes = String(password).toByteArray(Charsets.UTF_8)
        val sealed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(bytes), Base64.NO_WRAP)
        bytes.fill(0)
        check(prefs.edit().putString(PASSWORD, sealed).commit())
    }

    /** Parola ya da null (yok / Keystore açamadı). Çağıran işi bitince diziyi sıfırlar. */
    fun password(): CharArray? {
        val sealed = prefs.getString(PASSWORD, null) ?: return null
        val (iv, ct) = sealed.split(':').takeIf { it.size == 2 } ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, master(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        val bytes = cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP))
        return String(bytes, Charsets.UTF_8).toCharArray().also { bytes.fill(0) }
    }

    fun exported(week: EncryptedBackup.Week): Boolean = prefs.contains(EXPORTED + week.monday)
    fun markExported(week: EncryptedBackup.Week) { prefs.edit().putLong(EXPORTED + week.monday, System.currentTimeMillis()).apply() }

    companion object {
        const val FILE = "weem-backup"
        const val ALIAS = "WEEM.Backup.v1"
        private const val PASSWORD = "password"
        private const val EXPORTED = "exported."

        @Synchronized private fun master(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!ks.containsAlias(ALIAS)) KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            }.generateKey()
            return ks.getKey(ALIAS, null) as SecretKey
        }
    }
}

/**
 * N3 otomatik şifreli yedek (S22): yazma, liste, dışa aktarma, haftayı silme, geri yükleme. Kural ve biçim `logic/core` [EncryptedBackup]'ta
 * (JVM'de test edilir); burada MediaStore, Keystore, `courier.db` ve iş parçacığı. Ağ kodu yok. Cihazda doğrulanmadı.
 */
object EncryptedBackups {
    private val main = Handler(Looper.getMainLooper())

    /** [week] dosyasını yazar (aynı haftanınki yenilenir); parola yoksa, içe aktarma sürüyorsa ya da haftanın kaydı yoksa false. Arka planda çağrılır. */
    fun write(ctx: Context, week: EncryptedBackup.Week): Boolean {
        val app = ctx.applicationContext
        val password = BackupVault(app).password() ?: return false
        val staging = File(app.cacheDir, "n3-${System.nanoTime()}")
        try {
            val files = Store.locked(app) {
                if (Store.importing) return false
                EncryptedBackup.stage(Store.logsRoot(app), app.filesDir, week, staging)
            } ?: return false
            val db = CourierDb(app)
            val (trips, offers) = try { week.days.flatMap { db.trips(it) } to week.days.flatMap { db.offers(it) } } finally { db.close() }
            val content = EncryptedBackup.Content(files, EncryptedBackup.encodePrefs(Store.prefs(app).all), trips, offers,
                mapPrefs = EncryptedBackup.encodePrefs(MapBackup.strip(mapPrefs(app).all)) { false }, mapFiles = stageMap(app, week, staging))
            Downloads.save(app, EncryptedBackup.FOLDER, week.fileName, replace = true, mime = "application/octet-stream") {
                EncryptedBackup.write(week, content, password, it, RecordExport.versionName(app), System.currentTimeMillis())
            }
            Store.event(app, EVENT, JSONObject().put("week", week.monday.toString()).put("files", files.size).put("trips", trips.size))
            return true
        } finally { password.fill(' '); staging.deleteRecursively() }
    }

    /** A10.4h: haritanın küçük verisi [staging]/`map/`'e kopyalanır (yazan kilidi yok; dosyalar `.tmp` + yeniden adlandırmayla yazılır). Kopyalanamayan atlanır. */
    private fun stageMap(app: Context, week: EncryptedBackup.Week, staging: File): List<Pair<String, File>> =
        MapBackup.files(File(app.filesDir, "map"), week.days).mapNotNull { (name, f) ->
            runCatching { name to f.copyTo(File(staging, name), overwrite = true) }.getOrNull()
        }

    private fun mapPrefs(app: Context) = app.getSharedPreferences(MAP_PREFS_FILE, Context.MODE_PRIVATE)
    /** `:map` `MapPrefsStore.FILE` (MAP.md 14.1). */
    private const val MAP_PREFS_FILE = "map"

    const val EVENT = "encrypted_backup"
    const val EVENT_FAILED = "encrypted_backup_failed"

    /** Şimdi (Kayıtlar düğmesi): bu haftanın yedeği; sonuç ana iş parçacığında. */
    fun writeNow(ctx: Context, then: (Result<Boolean>) -> Unit) {
        val app = ctx.applicationContext
        Thread({
            val r = runCatching { write(app, EncryptedBackup.week(LogTime.day(System.currentTimeMillis()))) }
            r.exceptionOrNull()?.let { runCatching { Store.failure(app, "encrypted_backup", it) } }
            main.post { then(r) }
        }, "weem-backup").start()
    }

    /** Haftalık yedek dosyalarımız (İndirilenler/WEEM/yedek), yeniden eskiye. */
    fun list(ctx: Context): List<Pair<EncryptedBackup.Week, Downloads.Item>> =
        Downloads.list(ctx, EncryptedBackup.FOLDER).mapNotNull { item -> EncryptedBackup.weekOf(item.name)?.let { it to item } }.sortedByDescending { it.first.monday }

    /** Paylaşım seçicisi; dışa aktarıldı işareti konur ("Uygulamadan sil" açılır). */
    fun export(ctx: Context, week: EncryptedBackup.Week, uri: Uri, title: String) {
        val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        BackupVault(ctx).markExported(week)
    }

    /** "Uygulamadan sil": dışa aktarılmış haftanın gün kayıtları (vardiya açıkken bugün hariç). Silinen gün sayısı. */
    fun deleteWeek(ctx: Context, week: EncryptedBackup.Week): Int {
        check(BackupVault(ctx).exported(week))
        val days = Store.locked(ctx) {
            check(!Store.importing)
            val records = DailyRecords(Store.logsRoot(ctx))
            val today = LogTime.day(System.currentTimeMillis())
            EncryptedBackup.deletableDays(week, records.days().map { it.date }, today, Store.shiftOn(ctx)).onEach { records.delete(it) }
        }
        if (days.isNotEmpty()) OrdersAccess.reload(ctx)
        return days.size
    }

    sealed interface Restored {
        /** [mapPending]: harita verisi bırakıldı, WEEM yeniden açılınca yüklenir (A10.4h). */
        data class Done(val files: Int, val mapPending: Boolean = false) : Restored
        data object ShiftOn : Restored
        data object Busy : Restored
        data object WrongPassword : Restored
        data class Failed(val message: String?) : Restored
    }

    /**
     * Geri yükleme (S22): vardiya kapalıyken, `importing` bayrağıyla (içe aktarmayla aynı). Gün dosyaları içe aktarma kurallarıyla birleşir,
     * siparişler yeniden kurulur ([RestoreHook.afterRestore]); sonra yalnız telefonda olmayan ayarlar yazılır (ev konumu yedekte yok) ve
     * `courier.db`'ye eksik yolculuk/teklif satırları ile eksik müşteri telefonları eklenir.
     */
    fun restore(ctx: Context, open: () -> java.io.InputStream?, password: CharArray, then: (Restored) -> Unit) {
        val app = ctx.applicationContext
        if (Store.shiftOn(app)) { then(Restored.ShiftOn); return }
        if (Store.importing) { then(Restored.Busy); return }
        Store.importing = true
        Thread({
            val outcome: Pair<Restored, EncryptedBackup.Opened?> = try {
                val (opened, names) = (open() ?: error("open")).use { EncryptedBackup.restore(it, password, app.filesDir, Store.logsRoot(app), app.cacheDir) }
                // A10.4h: harita kısmı bekleyen klasöre; çalışan harita bellekteki durumla dosyaları ezmesin diye açılışta uygulanır.
                val map = runCatching { MapBackup.savePending(app.filesDir, opened.mapPrefs, opened.mapFiles) }
                    .onFailure { runCatching { Store.failure(app, "encrypted_restore_map", it) } }.getOrDefault(0)
                Restored.Done(names.size, map > 0 || MapBackup.hasPending(app.filesDir)) to opened
            } catch (e: EncryptedBackup.WrongPassword) { Restored.WrongPassword to null
            } catch (e: Throwable) { runCatching { Store.failure(app, "encrypted_restore", e) }; Restored.Failed(e.message) to null
            } finally { password.fill(' ') }
            main.post {
                Store.importing = false
                val opened = outcome.second
                if (opened != null) {
                    RestoreHook.afterRestore?.let { hook -> Store.guarded(app, "encrypted_restore") { hook(app) } }
                    Store.guarded(app, "encrypted_restore") { applyRest(app, opened) }
                }
                then(outcome.first)
            }
        }, "weem-restore").start()
    }

    /**
     * A10.4h: bekleyen harita geri yüklemesi; süreç açılışında, harita çalışma zamanları kurulmadan önce ([WeemApplication]). Yalnız telefonda
     * olmayan dosyalar ve `map` tercihleri yazılır.
     */
    fun applyPendingMap(ctx: Context) {
        val app = ctx.applicationContext
        val prefs = mapPrefs(app)
        val applied = MapBackup.applyPending(app.filesDir, prefs.all.keys) ?: return
        prefs.edit().apply { for ((k, v) in applied.prefs) putPref(k, v) }.commit()
        Store.event(app, "encrypted_restore_map", JSONObject().put("files", applied.files).put("prefs", applied.prefs.size))
    }

    /** Süreci yeniden başlatır (geri yüklemeden sonra; vardiya kapalı). Açılış ekranı yeni süreçte açılır. */
    fun restart(ctx: Context) {
        val app = ctx.applicationContext
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        app.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        Runtime.getRuntime().exit(0)
    }

    @Suppress("UNCHECKED_CAST")
    private fun android.content.SharedPreferences.Editor.putPref(k: String, v: Any) {
        when (v) {
            is Boolean -> putBoolean(k, v); is Int -> putInt(k, v); is Long -> putLong(k, v); is Float -> putFloat(k, v)
            is String -> putString(k, v); is Set<*> -> putStringSet(k, v as Set<String>)
        }
    }

    private fun applyRest(app: Context, opened: EncryptedBackup.Opened) {
        val prefs = Store.prefs(app)
        val missing = EncryptedBackup.missingPrefs(opened.prefs, prefs.all.keys)
        prefs.edit().apply { for ((k, v) in missing) putPref(k, v) }.apply()
        val db = CourierDb(app)
        try {
            db.batch {
                val current = HashMap<String, JSONObject>()
                for (d in opened.trips.mapNotNull { it.opt("day") as? String }.distinct()) for (t in db.trips(d)) current[t.getString("id")] = t
                for (t in opened.trips) EncryptedBackup.mergeTrip(current[t.getString("id")], t)?.let(db::saveTrip)
                for ((o, result) in opened.offers) if (db.offer(o.getString("id")) == null) db.saveOffer(o, result)
            }
        } finally { db.close() }
    }
}

/** Vardiya sonu (S22): parola varsa vardiyanın değdiği her hafta için şifreli yedek, kendi iş parçacığında; hata vardiyaya ulaşmaz. */
class EncryptedBackupHook(ctx: Context) : ShiftMachine.Hooks {
    private val app = ctx.applicationContext

    override fun onEnd(startMs: Long, nowMs: Long, reason: String) {
        if (!BackupVault(app).hasPassword()) return
        Thread({
            for (w in EncryptedBackup.weeks(startMs, nowMs)) runCatching { EncryptedBackups.write(app, w) }.onFailure {
                Store.event(app, EncryptedBackups.EVENT_FAILED, JSONObject().put("week", w.monday.toString()).put("error", it.javaClass.simpleName))
            }
        }, "weem-backup").start()
    }
}
