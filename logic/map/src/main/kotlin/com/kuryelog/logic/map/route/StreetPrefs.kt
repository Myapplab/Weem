package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.settings.MapPrefs

/**
 * Kullanıcının sokak kararları (OSM way id; `lib/routing/street_prefs.dart`, MAP.md 4.2): [approved] "iki yönlü onaylı" (Ses+),
 * [blocked] tamamen kapalı (uzun basış menüsü), [steep] aşırı eğimli (Ses− iki kez; yokuş yukarı kullanılmaz). Kümeler ekleme
 * sırasını korur (yazma sırası).
 */
data class StreetPrefs(
    val approved: Set<Long> = emptySet(),
    val blocked: Set<Long> = emptySet(),
    val steep: Set<Long> = emptySet(),
    /** Liste ekranı için sokak adları. */
    val names: Map<Long, String> = emptyMap(),
    val reverseHours: Map<Long, ReverseHours> = emptyMap(),
)

enum class StreetList { APPROVED, BLOCKED, STEEP }

/**
 * `streets.*` anahtarlarının kodeği (MAP.md 4.2 tablo; `map` dosyası, öneksiz). Okuma: id ondalık, bozuk öğe atlanır; ad öğesi
 * `"<id>|<ad>"` (ilk `|` konumu > 0, ad `|` içerebilir); saat öğesi tam 3 tamsayı alan ve geçerli aralık. YolGo'nun 1.3.1 göçü
 * (`streets.blocked.legacy.v1`, `streets.blocked.migrated.v2`) WEEM'de yok: YolGo verisi aktarılmaz (S1).
 */
object StreetPrefsCodec {
    const val APPROVED = "streets.approved"
    const val BLOCKED = "streets.blocked"
    const val STEEP = "streets.steep"
    const val NAMES = "streets.names"
    const val HOURS = "streets.hours.v1"

    fun load(prefs: MapPrefs): StreetPrefs {
        fun ids(key: String): Set<Long> = (prefs.getStringList(key) ?: emptyList()).mapNotNullTo(LinkedHashSet()) { it.toLongOrNull() }
        val names = LinkedHashMap<Long, String>()
        for (s in prefs.getStringList(NAMES) ?: emptyList()) {
            val i = s.indexOf('|')
            if (i <= 0) continue
            val id = s.substring(0, i).toLongOrNull() ?: continue
            names[id] = s.substring(i + 1)
        }
        val hours = LinkedHashMap<Long, ReverseHours>()
        for (s in prefs.getStringList(HOURS) ?: emptyList()) {
            val f = s.split('|')
            if (f.size != 3) continue
            val id = f[0].toLongOrNull() ?: continue
            val start = f[1].toIntOrNull() ?: continue
            val end = f[2].toIntOrNull() ?: continue
            val h = ReverseHours(start, end)
            if (h.valid) hours[id] = h
        }
        return StreetPrefs(ids(APPROVED), ids(BLOCKED), ids(STEEP), names, hours)
    }

    /** Beş anahtar birden (YolGo `_save`). */
    fun save(prefs: MapPrefs, s: StreetPrefs) {
        prefs.setStringList(APPROVED, s.approved.map { "$it" })
        prefs.setStringList(BLOCKED, s.blocked.map { "$it" })
        prefs.setStringList(STEEP, s.steep.map { "$it" })
        prefs.setStringList(NAMES, s.names.map { (id, name) -> "$id|$name" })
        prefs.setStringList(HOURS, s.reverseHours.map { (id, h) -> "$id|${h.startMinute}|${h.endMinute}" })
    }
}

/**
 * Sokak kararlarını işleyen ve her işlemde `streets.*` anahtarlarına yazan depo (YolGo `StreetPrefsController`; MAP.md 4.2 işlem
 * tablosu). "Geri al" çağıranda: [clearWay] + önceki listeler + [setHours] (MAP.md 4.4).
 */
class StreetPrefsStore(private val prefs: MapPrefs) {
    var current: StreetPrefs = StreetPrefsCodec.load(prefs)
        private set

    /** Onay kara listeden çıkarır (ikisi birden olamaz); eğim işareti ve saat kalır. */
    fun approve(way: Long, name: String) = save(
        current.copy(approved = current.approved + way, blocked = current.blocked - way, names = named(way, name)),
    )

    /** Tamamen kapat: onayı kaldırır; saat kalır (ancak onay kaldırılınca gider). */
    fun block(way: Long, name: String) = save(
        current.copy(approved = current.approved - way, blocked = current.blocked + way, names = named(way, name)),
    )

    /** Aşırı eğimli: onay / kara liste durumuna dokunmaz. */
    fun markSteep(way: Long, name: String) = save(current.copy(steep = current.steep + way, names = named(way, name)))

    /** Üç listeden ve saatten çıkarır; ad kalır. */
    fun clearWay(way: Long) = save(
        current.copy(
            approved = current.approved - way,
            blocked = current.blocked - way,
            steep = current.steep - way,
            reverseHours = current.reverseHours - way,
        ),
    )

    /** Bir listeden tek sokak çıkar (diğer listeler kalır); onaylılardan çıkınca saati de silinir. */
    fun remove(list: StreetList, way: Long) = save(
        when (list) {
            StreetList.APPROVED -> current.copy(approved = current.approved - way, reverseHours = current.reverseHours - way)
            StreetList.BLOCKED -> current.copy(blocked = current.blocked - way)
            StreetList.STEEP -> current.copy(steep = current.steep - way)
        },
    )

    /** Liste boşalır; onaylılar boşalınca bütün saatler de. */
    fun clearAll(list: StreetList) = save(
        when (list) {
            StreetList.APPROVED -> current.copy(approved = emptySet(), reverseHours = emptyMap())
            StreetList.BLOCKED -> current.copy(blocked = emptySet())
            StreetList.STEEP -> current.copy(steep = emptySet())
        },
    )

    /** Geçersiz aralık yok sayılır; `null` saati siler. */
    fun setHours(way: Long, hours: ReverseHours?) {
        if (hours != null && !hours.valid) return
        save(current.copy(reverseHours = if (hours == null) current.reverseHours - way else current.reverseHours + (way to hours)))
    }

    private fun named(way: Long, name: String): Map<Long, String> = if (name.isEmpty()) current.names else current.names + (way to name)

    private fun save(s: StreetPrefs) {
        current = s
        StreetPrefsCodec.save(prefs, s)
    }
}
